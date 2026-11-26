package pl.dom.launcher.config

import android.content.Context
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import pl.dom.launcher.LauncherApp
import pl.dom.launcher.data.AppsRepository
import pl.dom.launcher.data.RemapAction
import pl.dom.launcher.data.SecretStore

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
            json(Response.Status.INTERNAL_ERROR, JSONObject().put("error", "Błąd: ${e.message}"))
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
        return when {
            session.method == Method.GET && session.uri == "/api/state" -> json(Response.Status.OK, state())
            session.method == Method.POST && session.uri == "/api/save" -> {
                val files = HashMap<String, String>()
                session.parseBody(files)
                save(JSONObject(files["postData"].orEmpty()))
                json(Response.Status.OK, JSONObject().put("ok", true))
            }
            else -> json(Response.Status.NOT_FOUND, JSONObject().put("error", "Nieznane polecenie"))
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
            .put(
                "remap",
                JSONObject()
                    .put("keyCode", s.remapKeyCode)
                    .put("keyName", if (s.remapKeyCode >= 0) android.view.KeyEvent.keyCodeToString(s.remapKeyCode) else "")
                    .put("action", s.remapAction.name)
                    .put("pkg", s.remapPackage),
            )
            .put("secrets", JSONObject().apply { SecretStore.ALL.forEach { put(it, app.secrets.has(it)) } })
    }

    private fun save(body: JSONObject) = runBlocking {
        val s = app.settings
        if (body.has("idleMinutes")) s.setIdleMinutes(body.getInt("idleMinutes"))
        if (body.has("haUrl")) s.setHaUrl(body.getString("haUrl"))
        if (body.has("embyUrl")) s.setEmbyUrl(body.getString("embyUrl"))
        body.optJSONArray("favorites")?.let { arr ->
            s.setFavorites((0 until arr.length()).map { arr.getString(it) })
        }
        body.optJSONObject("remap")?.let { r ->
            val current = s.current()
            val action = runCatching { RemapAction.valueOf(r.getString("action")) }.getOrDefault(RemapAction.NONE)
            s.setRemap(current.remapKeyCode, action, r.optString("pkg"))
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
