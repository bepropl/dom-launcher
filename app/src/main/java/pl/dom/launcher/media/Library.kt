package pl.dom.launcher.media

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Obserwowany serial i miejsce, do którego go obejrzeliście. */
data class FollowedShow(
    val tmdbId: Int,
    val name: String,
    val posterPath: String?,
    val backdropPath: String?,
    val provider: String?,
    /** Ostatni obejrzany odcinek (sezon 0 = jeszcze nic). */
    val watched: Episode,
    /** Ostatnio pobrane z TMDB szczegóły (do liczenia „Dalej” bez internetu). */
    val seasons: Map<Int, Int> = emptyMap(),
    val lastAired: Episode? = null,
    val nextAir: Episode? = null,
    val ended: Boolean = false,
    val updatedAt: Long = 0,
    val firstAirDate: String? = null,
    /** Czy jest w jednym z Waszych serwisów (sprawdzane przy starcie i co 24 h). */
    val mine: Boolean = provider != null,
    /** Kiedy pojawił się w Waszym serwisie (znaczek „Nowe na …” przez kilka dni). */
    val newSince: Long = 0,
    val checkedAt: Long = 0,
    /** Kiedy ostatnio coś oznaczyliście jako obejrzane (znaczek „Nowy” przy odcinkach wydanych później). */
    val watchedAt: Long = 0,
    /** Informacje o następnym odcinku do obejrzenia (kadr i tytuł na karcie „Dalej”). */
    val nextFor: Episode? = null,
    val nextName: String? = null,
    val nextStill: String? = null,
    /** Ile minut zajmie nadrobienie wszystkich wydanych, nieobejrzanych odcinków. */
    val catchUpMinutes: Int? = null,
    /** Kanał/serwis premiery (do „Premiera … · Netflix” w „Czekacie”). */
    val network: String? = null,
) {
    /** Dane o następnym odcinku, jeśli dotyczą właśnie tego odcinka. */
    val nextInfoValid: Boolean get() = nextFor != null && nextFor == nextToWatch

    /** Czy zaczęliście oglądać (niezaczęte są na liście „Do obejrzenia”, a nie w „Dalej”). */
    val started: Boolean get() = watched.season > 0 || watched.number > 0

    /** Następny odcinek do obejrzenia (null = nie ma kolejnego znanego odcinka). */
    val nextToWatch: Episode?
        get() {
            val count = seasons[watched.season] ?: 0
            if (watched.season > 0 && watched.number < count) return Episode(watched.season, watched.number + 1)
            val nextSeason = seasons.keys.filter { it > watched.season }.minOrNull() ?: return null
            return Episode(nextSeason, 1)
        }

    /** Czy następny odcinek już wyszedł. */
    val hasNewEpisode: Boolean
        get() {
            val next = nextToWatch ?: return false
            val aired = lastAired ?: return false
            return next <= aired
        }

    /** Ile wyszłych odcinków czeka na obejrzenie. */
    val unwatchedCount: Int
        get() {
            val aired = lastAired ?: return 0
            var count = 0
            var ep = nextToWatch
            while (ep != null && ep <= aired && count < 999) {
                count++
                ep = copy(watched = ep).nextToWatch
            }
            return count
        }

    /** Gdy następny do obejrzenia jest właśnie najnowszym odcinkiem – jego data premiery. */
    val newEpisodeDate: String? get() = if (nextToWatch == lastAired?.copy(airDate = null)) lastAired?.airDate else null
}

/** Film albo serial dodany do listy „do obejrzenia”. */
data class WatchItem(
    val tmdbId: Int,
    val type: MediaType,
    val title: String,
    val posterPath: String?,
    val backdropPath: String?,
    val provider: String?,
    val addedAt: Long,
    val releaseDate: String? = null,
    val mine: Boolean = provider != null,
    val newSince: Long = 0,
    val checkedAt: Long = 0,
    /** Film: premiera w kinach / na VOD (sprawdzane razem z dostępnością). */
    val release: ReleaseInfo? = null,
    /** Serial: kanał/serwis premiery. */
    val network: String? = null,
)

/**
 * Pozycja na liście „Do obejrzenia”: film lub serial dodany do listy albo obserwowany,
 * ale jeszcze niezaczęty serial.
 */
data class ToWatch(
    val tmdbId: Int,
    val type: MediaType,
    val title: String,
    val posterPath: String?,
    val backdropPath: String?,
    val provider: String?,
    val mine: Boolean,
    val releaseDate: String?,
    val newSince: Long,
    val followed: Boolean,
    val addedAt: Long,
    val release: ReleaseInfo? = null,
    val network: String? = null,
) {
    /** Wyszedł (albo nie znamy daty). */
    val released: Boolean get() = releaseDate == null || releaseDate <= java.time.LocalDate.now().toString()
    /** Gotowe do oglądania: jest w Waszym serwisie i już wyszło. */
    val ready: Boolean get() = mine && released
    /** „Nowe na Netflix” przez 4 dni od pojawienia się. */
    val isNew: Boolean get() = newSince > 0 && System.currentTimeMillis() - newSince < 4 * 24 * 3600_000L
}

/** Obejrzany film (historia „Obejrzane”, zasila „Bo oglądaliście…”). */
data class WatchedItem(val tmdbId: Int, val type: MediaType, val title: String, val posterPath: String?, val watchedAt: Long)

/** Tytuł ukryty z polecanych („Nie interesuje mnie”). */
data class HiddenItem(val tmdbId: Int, val type: MediaType, val title: String)

data class Library(
    val shows: List<FollowedShow> = emptyList(),
    val watchlist: List<WatchItem> = emptyList(),
    val history: List<WatchedItem> = emptyList(),
    val hidden: List<HiddenItem> = emptyList(),
) {
    fun isWatched(type: MediaType, id: Int) = history.any { it.tmdbId == id && it.type == type }
    fun isHidden(type: MediaType, id: Int) = hidden.any { it.tmdbId == id && it.type == type }
    fun isFollowed(id: Int) = shows.any { it.tmdbId == id }
    fun isOnWatchlist(type: MediaType, id: Int) = watchlist.any { it.tmdbId == id && it.type == type }

    /** Cała lista „Do obejrzenia” (lista + niezaczęte obserwowane seriale), bez powtórzeń. */
    fun toWatch(): List<ToWatch> {
        val fromShows = shows.filterNot { it.started }.map { s ->
            ToWatch(s.tmdbId, MediaType.TV, s.name, s.posterPath, s.backdropPath, s.provider, s.mine, s.firstAirDate, s.newSince, true, s.updatedAt, network = s.network)
        }
        val showIds = fromShows.map { it.tmdbId }.toSet()
        val fromList = watchlist.filterNot { it.type == MediaType.TV && it.tmdbId in showIds }.map { w ->
            ToWatch(w.tmdbId, w.type, w.title, w.posterPath, w.backdropPath, w.provider, w.mine, w.releaseDate, w.newSince, false, w.addedAt, w.release, w.network)
        }
        return fromList + fromShows
    }
}

/** Lista seriali i filmów zapisana w pliku library.json na telewizorze. */
class LibraryStore(context: Context) {

    private val file = File(context.filesDir, "library.json")
    private val mutex = Mutex()
    private val _state = MutableStateFlow(load())
    val state: StateFlow<Library> = _state.asStateFlow()

    suspend fun update(transform: (Library) -> Library) = mutex.withLock {
        val updated = transform(_state.value)
        _state.value = updated
        withContext(Dispatchers.IO) { file.writeText(toJson(updated).toString()) }
    }

    fun export(): String = toJson(_state.value).toString(2)

    suspend fun import(json: String) {
        val parsed = fromJson(JSONObject(json))
        update { parsed }
    }

    private fun load(): Library = runCatching { fromJson(JSONObject(file.readText())) }.getOrDefault(Library())

    private fun toJson(lib: Library) = JSONObject()
        .put("version", 2)
        .put("shows", JSONArray(lib.shows.map { s ->
            JSONObject()
                .put("tmdbId", s.tmdbId).put("name", s.name)
                .put("poster", s.posterPath ?: JSONObject.NULL).put("backdrop", s.backdropPath ?: JSONObject.NULL)
                .put("provider", s.provider ?: JSONObject.NULL)
                .put("watched", episodeJson(s.watched))
                .put("seasons", JSONObject().apply { s.seasons.forEach { (k, v) -> put(k.toString(), v) } })
                .put("lastAired", s.lastAired?.let(::episodeJson) ?: JSONObject.NULL)
                .put("nextAir", s.nextAir?.let(::episodeJson) ?: JSONObject.NULL)
                .put("ended", s.ended).put("updatedAt", s.updatedAt)
                .put("firstAirDate", s.firstAirDate ?: JSONObject.NULL)
                .put("mine", s.mine).put("newSince", s.newSince).put("checkedAt", s.checkedAt)
                .put("watchedAt", s.watchedAt)
                .put("nextFor", s.nextFor?.let(::episodeJson) ?: JSONObject.NULL)
                .put("nextName", s.nextName ?: JSONObject.NULL)
                .put("nextStill", s.nextStill ?: JSONObject.NULL)
                .put("catchUpMinutes", s.catchUpMinutes ?: JSONObject.NULL)
                .put("network", s.network ?: JSONObject.NULL)
        }))
        .put("watchlist", JSONArray(lib.watchlist.map { w ->
            JSONObject()
                .put("tmdbId", w.tmdbId).put("type", w.type.name).put("title", w.title)
                .put("poster", w.posterPath ?: JSONObject.NULL).put("backdrop", w.backdropPath ?: JSONObject.NULL)
                .put("provider", w.provider ?: JSONObject.NULL).put("addedAt", w.addedAt)
                .put("releaseDate", w.releaseDate ?: JSONObject.NULL)
                .put("mine", w.mine).put("newSince", w.newSince).put("checkedAt", w.checkedAt)
                .put("network", w.network ?: JSONObject.NULL)
                .put("release", w.release?.let { r ->
                    JSONObject().put("cinema", r.cinema ?: JSONObject.NULL).put("cinemaCountry", r.cinemaCountry ?: JSONObject.NULL)
                        .put("digital", r.digital ?: JSONObject.NULL).put("digitalCountry", r.digitalCountry ?: JSONObject.NULL)
                } ?: JSONObject.NULL)
        }))
        .put("history", JSONArray(lib.history.map { h ->
            JSONObject().put("tmdbId", h.tmdbId).put("type", h.type.name).put("title", h.title)
                .put("poster", h.posterPath ?: JSONObject.NULL).put("watchedAt", h.watchedAt)
        }))
        .put("hidden", JSONArray(lib.hidden.map { h ->
            JSONObject().put("tmdbId", h.tmdbId).put("type", h.type.name).put("title", h.title)
        }))

    private fun fromJson(o: JSONObject) = Library(
        shows = o.optJSONArray("shows").mapObjects { s ->
            FollowedShow(
                tmdbId = s.optInt("tmdbId"),
                name = s.optString("name"),
                posterPath = s.optStringOrNull("poster"),
                backdropPath = s.optStringOrNull("backdrop"),
                provider = s.optStringOrNull("provider"),
                watched = s.optJSONObject("watched")?.let(::episodeFrom) ?: Episode(0, 0),
                seasons = s.optJSONObject("seasons")?.let { js ->
                    js.keys().asSequence().mapNotNull { k -> k.toIntOrNull()?.let { it to js.optInt(k) } }.toMap()
                } ?: emptyMap(),
                lastAired = s.optJSONObject("lastAired")?.let(::episodeFrom),
                nextAir = s.optJSONObject("nextAir")?.let(::episodeFrom),
                ended = s.optBoolean("ended"),
                updatedAt = s.optLong("updatedAt"),
                firstAirDate = s.optStringOrNull("firstAirDate"),
                mine = if (s.has("mine")) s.optBoolean("mine") else s.optStringOrNull("provider") != null,
                newSince = s.optLong("newSince"),
                checkedAt = s.optLong("checkedAt"),
                watchedAt = s.optLong("watchedAt"),
                nextFor = s.optJSONObject("nextFor")?.let(::episodeFrom),
                nextName = s.optStringOrNull("nextName"),
                nextStill = s.optStringOrNull("nextStill"),
                catchUpMinutes = if (s.has("catchUpMinutes") && !s.isNull("catchUpMinutes")) s.optInt("catchUpMinutes") else null,
                network = s.optStringOrNull("network"),
            )
        },
        watchlist = o.optJSONArray("watchlist").mapObjects { w ->
            WatchItem(
                tmdbId = w.optInt("tmdbId"),
                type = runCatching { MediaType.valueOf(w.optString("type")) }.getOrDefault(MediaType.MOVIE),
                title = w.optString("title"),
                posterPath = w.optStringOrNull("poster"),
                backdropPath = w.optStringOrNull("backdrop"),
                provider = w.optStringOrNull("provider"),
                addedAt = w.optLong("addedAt"),
                releaseDate = w.optStringOrNull("releaseDate"),
                mine = if (w.has("mine")) w.optBoolean("mine") else w.optStringOrNull("provider") != null,
                newSince = w.optLong("newSince"),
                checkedAt = w.optLong("checkedAt"),
                network = w.optStringOrNull("network"),
                release = w.optJSONObject("release")?.let { r ->
                    ReleaseInfo(r.optStringOrNull("cinema"), r.optStringOrNull("cinemaCountry"), r.optStringOrNull("digital"), r.optStringOrNull("digitalCountry"))
                },
            )
        },
        history = o.optJSONArray("history").mapObjects { h ->
            WatchedItem(
                h.optInt("tmdbId"),
                runCatching { MediaType.valueOf(h.optString("type")) }.getOrDefault(MediaType.MOVIE),
                h.optString("title"),
                h.optStringOrNull("poster"),
                h.optLong("watchedAt"),
            )
        },
        hidden = o.optJSONArray("hidden").mapObjects { h ->
            HiddenItem(h.optInt("tmdbId"), runCatching { MediaType.valueOf(h.optString("type")) }.getOrDefault(MediaType.MOVIE), h.optString("title"))
        }
    )

    private fun episodeJson(e: Episode) = JSONObject().put("s", e.season).put("e", e.number).put("date", e.airDate ?: JSONObject.NULL)

    private fun episodeFrom(o: JSONObject) = Episode(o.optInt("s"), o.optInt("e"), o.optStringOrNull("date"))
}
