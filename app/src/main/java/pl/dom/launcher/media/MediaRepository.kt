package pl.dom.launcher.media

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import pl.dom.launcher.data.SecretStore
import pl.dom.launcher.data.SettingsRepository
import pl.dom.launcher.data.StreamingProvider

/** Dane do zakładek Filmy i Seriale oraz rzędów z Emby. */
data class Feeds(
    val movies: List<MediaItem> = emptyList(),
    val shows: List<MediaItem> = emptyList(),
    val embyResume: List<EmbyItem> = emptyList(),
    val embyMovies: List<EmbyItem> = emptyList(),
    val embyShows: List<EmbyItem> = emptyList(),
    /** Komunikaty o brakach w konfiguracji albo błędach połączenia. */
    val problems: List<String> = emptyList(),
    val loading: Boolean = false,
)

/**
 * Łączy lokalną listę (seriale, „do obejrzenia”), TMDB i Emby.
 * Seriale i filmy nie wymagają żadnego konta – tylko klucza TMDB.
 */
class MediaRepository(
    context: Context,
    private val settings: SettingsRepository,
    private val secrets: SecretStore,
) {
    val library = LibraryStore(context)
    private val tmdb = Tmdb { secrets.get(SecretStore.TMDB_KEY) }

    private val _feeds = MutableStateFlow(Feeds())
    val feeds: StateFlow<Feeds> = _feeds.asStateFlow()

    private val refreshLock = Mutex()
    @Volatile private var lastRefreshAt = 0L

    val hasTmdbKey: Boolean get() = secrets.has(SecretStore.TMDB_KEY)

    suspend fun refreshIfDue() {
        if (System.currentTimeMillis() - lastRefreshAt > REFRESH_INTERVAL_MS) refresh()
    }

    suspend fun refresh() = refreshLock.withLock {
        _feeds.value = _feeds.value.copy(loading = true)
        val problems = mutableListOf<String>()
        val result = withContext(Dispatchers.IO) {
            val s = settings.current()
            var movies = emptyList<MediaItem>()
            var shows = emptyList<MediaItem>()
            if (!hasTmdbKey) {
                problems += "Wpisz klucz TMDB na stronie konfiguracji w telefonie (zębatka → kod QR)."
            } else {
                // Aktualizacja obserwowanych seriali (nowe odcinki, daty premier).
                library.state.value.shows.forEach { show ->
                    runCatching { tmdb.tvDetails(show.tmdbId) }.onSuccess { d -> updateShowDetails(d) }
                }
                if (s.providers.isEmpty()) {
                    problems += "Zaznacz swoje serwisy streamingowe na stronie konfiguracji w telefonie."
                } else {
                    movies = roundRobin(s.providers.map { p -> runCatching { tmdb.discover(MediaType.MOVIE, p) }.getOrDefault(emptyList()) })
                    shows = roundRobin(s.providers.map { p -> runCatching { tmdb.discover(MediaType.TV, p) }.getOrDefault(emptyList()) })
                    if (movies.isEmpty() && shows.isEmpty()) problems += "TMDB nie odpowiada – sprawdź klucz i połączenie."
                }
            }
            var resume = emptyList<EmbyItem>()
            var embyMovies = emptyList<EmbyItem>()
            var embyShows = emptyList<EmbyItem>()
            emby(s.embyUrl)?.let { emby ->
                runCatching {
                    resume = emby.resume()
                    embyMovies = emby.latest(movies = true)
                    embyShows = emby.latest(movies = false)
                }.onFailure { problems += "Nie mogę połączyć się z Emby: ${it.message}" }
            }
            Feeds(movies, shows, resume, embyMovies, embyShows, problems, loading = false)
        }
        lastRefreshAt = System.currentTimeMillis()
        _feeds.value = result
    }

    suspend fun search(text: String): List<MediaItem> = withContext(Dispatchers.IO) { tmdb.search(text) }

    suspend fun providers(): List<StreamingProvider> = withContext(Dispatchers.IO) { tmdb.providers() }

    /** Dodaje serial do obserwowanych. caughtUp = jesteście na bieżąco (obejrzane do ostatniego odcinka). */
    suspend fun follow(tmdbId: Int, caughtUp: Boolean = true) {
        val (details, provider) = withContext(Dispatchers.IO) {
            tmdb.tvDetails(tmdbId) to runCatching { tmdb.providerFor(MediaType.TV, tmdbId, settings.current().providers) }.getOrNull()
        }
        library.update { lib ->
            if (lib.isFollowed(tmdbId)) return@update lib
            val show = FollowedShow(
                tmdbId = tmdbId,
                name = details.name,
                posterPath = details.posterPath,
                backdropPath = details.backdropPath,
                provider = provider,
                watched = if (caughtUp) details.lastAired?.copy(airDate = null) ?: Episode(0, 0) else Episode(0, 0),
                seasons = details.seasons,
                lastAired = details.lastAired,
                nextAir = details.nextAir,
                ended = details.ended,
                updatedAt = System.currentTimeMillis(),
            )
            lib.copy(shows = lib.shows + show, watchlist = lib.watchlist.filterNot { it.type == MediaType.TV && it.tmdbId == tmdbId })
        }
    }

    suspend fun unfollow(tmdbId: Int) = library.update { lib -> lib.copy(shows = lib.shows.filterNot { it.tmdbId == tmdbId }) }

    /** „Oznacz jako obejrzany” – przesuwa serial o jeden odcinek dalej. */
    suspend fun markNextWatched(tmdbId: Int) = library.update { lib ->
        lib.copy(shows = lib.shows.map { s -> if (s.tmdbId == tmdbId) s.nextToWatch?.let { s.copy(watched = it) } ?: s else s })
    }

    suspend fun setWatched(tmdbId: Int, season: Int, episode: Int) = library.update { lib ->
        lib.copy(shows = lib.shows.map { s -> if (s.tmdbId == tmdbId) s.copy(watched = Episode(season.coerceAtLeast(0), episode.coerceAtLeast(0))) else s })
    }

    suspend fun addToWatchlist(type: MediaType, tmdbId: Int) {
        if (library.state.value.isOnWatchlist(type, tmdbId)) return
        val (item, provider) = withContext(Dispatchers.IO) {
            tmdb.item(type, tmdbId) to runCatching { tmdb.providerFor(type, tmdbId, settings.current().providers) }.getOrNull()
        }
        library.update { lib ->
            if (lib.isOnWatchlist(type, tmdbId)) lib
            else lib.copy(
                watchlist = lib.watchlist + WatchItem(tmdbId, type, item.title, item.posterPath, item.backdropPath, provider, System.currentTimeMillis()),
            )
        }
    }

    suspend fun removeFromWatchlist(type: MediaType, tmdbId: Int) = library.update { lib ->
        lib.copy(watchlist = lib.watchlist.filterNot { it.type == type && it.tmdbId == tmdbId })
    }

    private suspend fun updateShowDetails(d: TvDetails) = library.update { lib ->
        lib.copy(shows = lib.shows.map { s ->
            if (s.tmdbId != d.tmdbId) s
            else s.copy(
                name = d.name, posterPath = d.posterPath ?: s.posterPath, backdropPath = d.backdropPath ?: s.backdropPath,
                seasons = d.seasons, lastAired = d.lastAired, nextAir = d.nextAir, ended = d.ended,
                updatedAt = System.currentTimeMillis(),
            )
        })
    }

    @Volatile private var embyClient: Pair<String, Emby>? = null

    private fun emby(url: String): Emby? {
        val key = secrets.get(SecretStore.EMBY_KEY)
        if (url.isBlank() || key.isNullOrBlank()) return null
        val id = "$url|$key"
        embyClient?.let { (cachedId, client) -> if (cachedId == id) return client }
        return Emby(url, key).also { embyClient = id to it }
    }

    /** Przeplata listy z kilku serwisów, żeby żaden nie zdominował rzędu. */
    private fun roundRobin(lists: List<List<MediaItem>>): List<MediaItem> {
        val out = LinkedHashMap<Int, MediaItem>()
        val max = lists.maxOfOrNull { it.size } ?: 0
        for (i in 0 until max) for (list in lists) list.getOrNull(i)?.let { out.putIfAbsent(it.tmdbId, it) }
        return out.values.take(24)
    }

    companion object {
        private const val REFRESH_INTERVAL_MS = 3 * 60 * 60 * 1000L
    }
}
