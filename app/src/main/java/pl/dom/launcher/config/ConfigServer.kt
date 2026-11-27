package pl.dom.launcher.config

import android.content.Context
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import pl.dom.launcher.LauncherApp
import pl.dom.launcher.data.AppsRepository
import pl.dom.launcher.data.SecretStore
import pl.dom.launcher.data.StreamingProvider
import pl.dom.launcher.media.Episode
import pl.dom.launcher.ha.HaNotification
import pl.dom.launcher.ha.HaState
import pl.dom.launcher.ha.HaStatus
import pl.dom.launcher.house.HouseRooms
import pl.dom.launcher.media.MediaType
import pl.dom.launcher.media.MissingKeyException
import pl.dom.launcher.media.TmdbImages

/**
 * Mała strona konfiguracji w sieci domowej. Działa tylko, gdy na telewizorze otwarty jest
 * ekran Ustawień, i wymaga PIN-u pokazanego na ekranie. Sekretów nigdy nie odsyła z powrotem.
 */
class ConfigServer(context: Context, private val pin: String) : NanoHTTPD(PORT) {

    private val app = LauncherApp.from(context)

    @Volatile
    private var failedAttempts = 0

    override fun serve(session: IHTTPSession): Response {
        return try {
            when {
                session.method == Method.GET && session.uri == "/" ->
                    newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", CONFIG_PAGE)
                session.uri.startsWith("/api/") -> api(session)
                else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Nie ma takiej strony")
            }
        } catch (e: Exception) {
            json(Response.Status.INTERNAL_ERROR, JSONObject().put("error", "Błąd: ${e.message ?: e.javaClass.simpleName}"))
        }
    }

    private fun api(session: IHTTPSession): Response {
        if (failedAttempts >= MAX_ATTEMPTS) {
            return json(Response.Status.FORBIDDEN, JSONObject().put("error", "Za dużo błędnych PIN-ów. Otwórz Ustawienia na telewizorze ponownie."))
        }
        if (session.headers["x-pin"] != pin) {
            failedAttempts++
            return json(Response.Status.UNAUTHORIZED, JSONObject().put("error", "Zły PIN"))
        }
        failedAttempts = 0
        val get = session.method == Method.GET
        val post = session.method == Method.POST
        return when {
            get && session.uri == "/api/state" -> json(Response.Status.OK, state())
            post && session.uri == "/api/save" -> {
                save(body(session))
                refreshMedia()
                app.ha.reconnect()
                ok()
            }
            get && session.uri == "/api/ha" -> json(Response.Status.OK, haInfo())
            post && session.uri == "/api/ha/config" -> {
                val b = body(session)
                fun pick(name: String) = b.optString(name, "").takeIf { it.isNotBlank() && it != "null" }
                val persons = b.optJSONArray("persons")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
                val roomsJson = b.optJSONObject("rooms") ?: JSONObject()
                val rooms = roomsJson.keys().asSequence().mapNotNull { key ->
                    val entity = roomsJson.optString(key).takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    key.toIntOrNull()?.let { it to entity }
                }.toMap()
                runBlocking {
                    app.settings.updateHaConfig {
                        it.copy(weather = pick("weather"), outsideTemp = pick("outsideTemp"), insideTemp = pick("insideTemp"), persons = persons, rooms = rooms)
                    }
                }
                ok()
            }
            post && session.uri == "/api/ha/trust" -> {
                val fingerprint = body(session).optString("fingerprint").takeIf { it.isNotBlank() }
                runBlocking { app.settings.updateHaConfig { it.copy(certSha256 = fingerprint) } }
                app.ha.reconnect()
                ok()
            }
            post && session.uri == "/api/ha/test" -> {
                val camera = body(session).optString("camera").takeIf { it.isNotBlank() }
                app.scope.launch {
                    val image = camera?.let { app.ha.fetchImage("/api/camera_proxy/$it") }
                    app.ha.show(
                        HaNotification(
                            title = "Test powiadomienia",
                            message = if (camera != null && image == null) "Nie udało się pobrać obrazu z $camera" else "Tak będą wyglądać powiadomienia z Home Assistanta.",
                            image = image,
                            seconds = 12,
                        ),
                    )
                }
                ok()
            }
            get && session.uri == "/api/library" -> json(Response.Status.OK, library())
            get && session.uri == "/api/providers" -> json(Response.Status.OK, providers())
            post && session.uri == "/api/providers" -> {
                val arr = body(session).optJSONArray("providers") ?: JSONArray()
                val list = (0 until arr.length()).map { arr.getJSONObject(it) }
                    .map { StreamingProvider(it.getInt("id"), it.getString("name")) }
                runBlocking { app.settings.setProviders(list) }
                refreshMedia()
                ok()
            }
            post && session.uri == "/api/search" -> json(Response.Status.OK, search(body(session).optString("q")))
            post && session.uri == "/api/library" -> {
                libraryAction(body(session))
                refreshMedia()
                json(Response.Status.OK, library())
            }
            get && session.uri == "/api/export" ->
                newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", app.media.library.export()).apply {
                    addHeader("Cache-Control", "no-store")
                }
            post && session.uri == "/api/import" -> {
                runBlocking { app.media.library.import(body(session).getString("json")) }
                refreshMedia()
                json(Response.Status.OK, library())
            }
            else -> json(Response.Status.NOT_FOUND, JSONObject().put("error", "Nieznane polecenie"))
        }
    }

    private fun body(session: IHTTPSession): JSONObject {
        val files = HashMap<String, String>()
        session.parseBody(files)
        return JSONObject(files["postData"].orEmpty().ifBlank { "{}" })
    }

    private fun ok() = json(Response.Status.OK, JSONObject().put("ok", true))

    /** Po zmianach odświeża rzędy na telewizorze w tle. */
    private fun refreshMedia() {
        app.scope.launch { runCatching { app.media.refresh() } }
    }

    private fun haStatusJson(): JSONObject {
        val o = JSONObject()
        when (val st = app.ha.status.value) {
            HaStatus.Off -> o.put("status", "off").put("message", "Wpisz adres i token, potem Zapisz.")
            HaStatus.Connecting -> o.put("status", "connecting").put("message", "Łączę…")
            HaStatus.Connected -> o.put("status", "connected").put("message", "Połączono ✓")
            is HaStatus.Error -> o.put("status", "error").put("message", "Błąd: " + st.message)
            is HaStatus.Untrusted -> o.put("status", "untrusted").put("fingerprint", st.fingerprint)
                .put("message", "Home Assistant ma własny certyfikat. Sprawdź, że to Twój serwer, i kliknij „Ufaj”.")
        }
        return o
    }

    private fun haInfo(): JSONObject = runBlocking {
        val config = app.settings.current().ha
        val states = app.ha.allStates().sortedBy { it.name.lowercase() }
        fun list(filter: (HaState) -> Boolean) = JSONArray(states.filter(filter).map {
            JSONObject().put("id", it.entityId).put("name", it.name).put("state", it.state + (it.unit?.let { u -> " $u" } ?: ""))
        })
        JSONObject()
            .put("status", haStatusJson())
            .put("weather", list { it.domain == "weather" })
            .put("temps", list { it.domain == "sensor" && (it.attributes.optString("device_class") == "temperature" || it.unit == "°C") })
            .put("persons", list { it.domain == "person" })
            .put("lights", list { it.domain == "light" || it.domain == "switch" })
            .put("cameras", list { it.domain == "camera" })
            .put("rooms", JSONArray(HouseRooms.names.toSortedMap().map { (id, name) -> JSONObject().put("id", id).put("name", name) }))
            .put("config", JSONObject(config.toJson()))
    }

    private fun episode(e: Episode?): Any = e?.let {
        JSONObject().put("s", it.season).put("e", it.number).put("code", it.code).put("date", it.airDate ?: JSONObject.NULL)
    } ?: JSONObject.NULL

    private fun library(): JSONObject {
        val lib = app.media.library.state.value
        return JSONObject()
            .put("shows", JSONArray(lib.shows.sortedBy { it.name.lowercase() }.map { show ->
                JSONObject()
                    .put("id", show.tmdbId)
                    .put("name", show.name)
                    .put("poster", TmdbImages.poster(show.posterPath) ?: JSONObject.NULL)
                    .put("provider", show.provider ?: JSONObject.NULL)
                    .put("watched", episode(show.watched))
                    .put("next", episode(show.nextToWatch))
                    .put("lastAired", episode(show.lastAired))
                    .put("nextAir", episode(show.nextAir))
                    .put("unwatched", show.unwatchedCount)
                    .put("ended", show.ended)
                    .put("seasons", JSONObject().apply { show.seasons.toSortedMap().forEach { (k, v) -> put(k.toString(), v) } })
            }))
            .put("watchlist", JSONArray(lib.watchlist.map { item ->
                JSONObject()
                    .put("id", item.tmdbId)
                    .put("type", item.type.name)
                    .put("title", item.title)
                    .put("poster", TmdbImages.poster(item.posterPath) ?: JSONObject.NULL)
                    .put("provider", item.provider ?: JSONObject.NULL)
            }))
    }

    private fun providers(): JSONObject = runBlocking {
        val selected = app.settings.current().providers
        val all = runCatching { app.media.providers() }
        JSONObject()
            .put("selected", JSONArray(selected.map { it.id }))
            .put("all", JSONArray((all.getOrNull() ?: selected).map { JSONObject().put("id", it.id).put("name", it.name) }))
            .put("error", all.exceptionOrNull()?.let { problemText(it) } ?: JSONObject.NULL)
    }

    private fun search(q: String): JSONObject = runBlocking {
        if (q.isBlank()) return@runBlocking JSONObject().put("results", JSONArray())
        val lib = app.media.library.state.value
        val found = runCatching { app.media.search(q) }
        found.exceptionOrNull()?.let { return@runBlocking JSONObject().put("error", problemText(it)).put("results", JSONArray()) }
        JSONObject().put("results", JSONArray(found.getOrThrow().map { item ->
            JSONObject()
                .put("id", item.tmdbId)
                .put("type", item.type.name)
                .put("title", item.title)
                .put("year", item.year ?: JSONObject.NULL)
                .put("poster", TmdbImages.poster(item.posterPath) ?: JSONObject.NULL)
                .put("followed", item.type == MediaType.TV && lib.isFollowed(item.tmdbId))
                .put("onList", lib.isOnWatchlist(item.type, item.tmdbId))
        }))
    }

    private fun problemText(e: Throwable): String = when (e) {
        is MissingKeyException -> "Najpierw wpisz i zapisz klucz TMDB."
        else -> "TMDB: ${e.message ?: "brak odpowiedzi"}"
    }

    private fun libraryAction(b: JSONObject) = runBlocking {
        val media = app.media
        val id = b.getInt("id")
        val type = runCatching { MediaType.valueOf(b.optString("type", "TV")) }.getOrDefault(MediaType.TV)
        when (b.getString("action")) {
            "follow" -> media.follow(id, caughtUp = b.optBoolean("caughtUp", true))
            "unfollow" -> media.unfollow(id)
            "watched" -> media.setWatched(id, b.getInt("s"), b.getInt("e"))
            "next" -> media.markNextWatched(id)
            "list" -> media.addToWatchlist(type, id)
            "unlist" -> media.removeFromWatchlist(type, id)
            else -> throw IllegalArgumentException("Nieznana akcja")
        }
    }

    private fun state(): JSONObject = runBlocking {
        val s = app.settings.current()
        val all = app.apps.apps.value
        val favorites = AppsRepository.resolveFavorites(s.favorites, all).map { it.packageName }
        JSONObject()
            .put("idleMinutes", s.idleMinutes)
            .put("haUrl", s.haUrl)
            .put("embyUrl", s.embyUrl)
            .put("favorites", JSONArray(favorites))
            .put("apps", JSONArray(all.map { JSONObject().put("pkg", it.packageName).put("label", it.label) }))
            .put("secrets", JSONObject().apply { SecretStore.ALL.forEach { put(it, app.secrets.has(it)) } })
            .put("ha", haStatusJson())
    }

    private fun save(body: JSONObject) = runBlocking {
        val s = app.settings
        if (body.has("idleMinutes")) s.setIdleMinutes(body.getInt("idleMinutes"))
        if (body.has("haUrl")) s.setHaUrl(body.getString("haUrl"))
        if (body.has("embyUrl")) s.setEmbyUrl(body.getString("embyUrl"))
        body.optJSONArray("favorites")?.let { arr ->
            s.setFavorites((0 until arr.length()).map { arr.getString(it) })
        }
        // Sekrety: pusty tekst = bez zmian, "-" = usuń.
        body.optJSONObject("secrets")?.let { sec ->
            SecretStore.ALL.forEach { name ->
                val value = sec.optString(name, "")
                when {
                    value == "-" -> app.secrets.put(name, null)
                    value.isNotBlank() -> app.secrets.put(name, value)
                }
            }
        }
    }

    private fun json(status: Response.Status, body: JSONObject): Response =
        newFixedLengthResponse(status, "application/json; charset=utf-8", body.toString()).apply {
            addHeader("Cache-Control", "no-store")
        }

    companion object {
        const val PORT = 8765
        const val SOCKET_TIMEOUT = 5_000
        private const val MAX_ATTEMPTS = 10
    }
}
