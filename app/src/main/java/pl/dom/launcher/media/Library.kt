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
) {
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
)

data class Library(val shows: List<FollowedShow> = emptyList(), val watchlist: List<WatchItem> = emptyList()) {
    fun isFollowed(id: Int) = shows.any { it.tmdbId == id }
    fun isOnWatchlist(type: MediaType, id: Int) = watchlist.any { it.tmdbId == id && it.type == type }
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
        .put("version", 1)
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
        }))
        .put("watchlist", JSONArray(lib.watchlist.map { w ->
            JSONObject()
                .put("tmdbId", w.tmdbId).put("type", w.type.name).put("title", w.title)
                .put("poster", w.posterPath ?: JSONObject.NULL).put("backdrop", w.backdropPath ?: JSONObject.NULL)
                .put("provider", w.provider ?: JSONObject.NULL).put("addedAt", w.addedAt)
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
            )
        },
    )

    private fun episodeJson(e: Episode) = JSONObject().put("s", e.season).put("e", e.number).put("date", e.airDate ?: JSONObject.NULL)

    private fun episodeFrom(o: JSONObject) = Episode(o.optInt("s"), o.optInt("e"), o.optStringOrNull("date"))
}
