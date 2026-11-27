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

    /** Wiadomość na telewizor, gdy coś z listy pojawi się w Waszym serwisie (podpina LauncherApp). */
    @Volatile var onNews: ((title: String, message: String) -> Unit)? = null
    @Volatile private var availabilityCheckedThisRun = false

    private val refreshLock = Mutex()
    @Volatile private var lastRefreshAt = 0L
    @Volatile private var lastShowsAt = 0L
    private val polish by lazy { PolishPosters(context, tmdb) }

    val hasTmdbKey: Boolean get() = secrets.has(SecretStore.TMDB_KEY)

    /**
     * Wołane przy każdym powrocie do launchera:
     *  - polecane, Emby i dostępność listy – przy starcie i potem co 24 h (pełne odświeżenie),
     *  - daty odcinków obserwowanych seriali – co 6 h,
     *  - „Kontynuuj” z Emby – za każdym razem (zmienia się po każdym oglądaniu na NAS).
     */
    suspend fun refreshIfDue() {
        val now = System.currentTimeMillis()
        when {
            now - lastRefreshAt > FULL_REFRESH_MS -> refresh()
            now - lastShowsAt > SHOWS_REFRESH_MS -> {
                refreshShows()
                refreshEmbyResume()
            }
            else -> refreshEmbyResume()
        }
    }

    /** Tylko obserwowane seriale (nowe odcinki, daty premier). */
    private suspend fun refreshShows() = refreshLock.withLock {
        if (!hasTmdbKey) return@withLock
        withContext(Dispatchers.IO) {
            library.state.value.shows.forEach { show ->
                runCatching { tmdb.tvDetails(show.tmdbId) }.onSuccess { d -> updateShowDetails(d) }
            }
        }
        lastShowsAt = System.currentTimeMillis()
    }

    /** Tylko „Kontynuuj” z Emby – jedno szybkie zapytanie w sieci domowej. */
    private suspend fun refreshEmbyResume() {
        if (refreshLock.isLocked) return
        val resume = withContext(Dispatchers.IO) {
            emby(settings.current().embyUrl)?.let { runCatching { it.resume() }.getOrNull() }
        } ?: return
        _feeds.value = _feeds.value.copy(embyResume = resume)
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
                // Lista „Do obejrzenia”: przy starcie launchera i potem raz na 24 h.
                runCatching { checkAvailability(s.providers) }
                runCatching { updateLibraryPosters() }
                if (s.providers.isEmpty()) {
                    problems += "Zaznacz swoje serwisy streamingowe na stronie konfiguracji w telefonie."
                } else {
                    movies = roundRobin(s.providers.map { p -> runCatching { tmdb.discover(MediaType.MOVIE, p) }.getOrDefault(emptyList()) })
                    shows = roundRobin(s.providers.map { p -> runCatching { tmdb.discover(MediaType.TV, p) }.getOrDefault(emptyList()) })
                    // Polskie okładki tam, gdzie TMDB je ma.
                    movies = runCatching { polish.apply(movies) }.getOrDefault(movies)
                    shows = runCatching { polish.apply(shows) }.getOrDefault(shows)
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
        lastShowsAt = lastRefreshAt
        _feeds.value = result
    }

    suspend fun search(text: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val found = tmdb.search(text)
        runCatching { polish.apply(found) }.getOrDefault(found)
    }

    /** Test połączenia z TMDB (null = działa, inaczej opis problemu). */
    suspend fun testTmdb(): String? = withContext(Dispatchers.IO) {
        if (!hasTmdbKey) return@withContext "Brak klucza TMDB"
        runCatching { tmdb.providers() }.exceptionOrNull()?.let { "TMDB: " + (it.message ?: "brak odpowiedzi") }
    }

    /** Test połączenia z Emby (null = działa). */
    suspend fun testEmby(): String? = withContext(Dispatchers.IO) {
        val client = emby(settings.current().embyUrl) ?: return@withContext "Brak adresu albo klucza Emby"
        runCatching { client.resume() }.exceptionOrNull()?.let { "Emby: " + (it.message ?: "brak odpowiedzi") }
    }

    /** Polskie okładki dla obserwowanych seriali i listy „Do obejrzenia”. */
    private suspend fun updateLibraryPosters() {
        val lib = library.state.value
        val shows = lib.shows.associate { it.tmdbId to polish.posterFor(MediaType.TV, it.tmdbId) }
        val list = lib.watchlist.associate { (it.type to it.tmdbId) to polish.posterFor(it.type, it.tmdbId) }
        library.update { current ->
            current.copy(
                shows = current.shows.map { s -> shows[s.tmdbId]?.let { s.copy(posterPath = it) } ?: s },
                watchlist = current.watchlist.map { w -> list[w.type to w.tmdbId]?.let { w.copy(posterPath = it) } ?: w },
            )
        }
    }

    /** Szczegóły do ekranu podglądu, z serwisem, w którym tytuł jest dostępny. */
    suspend fun details(type: MediaType, tmdbId: Int): MediaDetails = withContext(Dispatchers.IO) {
        val details = tmdb.details(type, tmdbId)
        val mine = settings.current().providers
        val provider = runCatching { tmdb.providerFor(type, tmdbId, mine) }.getOrNull()
        details.copy(item = details.item.copy(provider = provider))
    }

    suspend fun season(tmdbId: Int, season: Int): List<SeasonEpisode> = withContext(Dispatchers.IO) { tmdb.season(tmdbId, season) }

    suspend fun providers(): List<StreamingProvider> = withContext(Dispatchers.IO) { tmdb.providers() }

    /** Dodaje serial do obserwowanych. caughtUp = jesteście na bieżąco (obejrzane do ostatniego odcinka). */
    suspend fun follow(tmdbId: Int, caughtUp: Boolean = true) {
        val (details, availability) = withContext(Dispatchers.IO) {
            tmdb.tvDetails(tmdbId) to (runCatching { tmdb.availability(MediaType.TV, tmdbId, settings.current().providers) }.getOrNull() ?: Availability(null, false))
        }
        val provider = availability.provider
        val plPoster = runCatching { polish.posterFor(MediaType.TV, tmdbId) }.getOrNull()
        library.update { lib ->
            if (lib.isFollowed(tmdbId)) return@update lib
            val show = FollowedShow(
                tmdbId = tmdbId,
                name = details.name,
                posterPath = plPoster ?: details.posterPath,
                backdropPath = details.backdropPath,
                provider = provider,
                watched = if (caughtUp) details.lastAired?.copy(airDate = null) ?: Episode(0, 0) else Episode(0, 0),
                seasons = details.seasons,
                lastAired = details.lastAired,
                nextAir = details.nextAir,
                ended = details.ended,
                updatedAt = System.currentTimeMillis(),
                firstAirDate = details.firstAirDate,
                mine = availability.mine,
                checkedAt = System.currentTimeMillis(),
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
        val (item, availability) = withContext(Dispatchers.IO) {
            tmdb.item(type, tmdbId) to (runCatching { tmdb.availability(type, tmdbId, settings.current().providers) }.getOrNull() ?: Availability(null, false))
        }
        val now = System.currentTimeMillis()
        val plPoster = runCatching { polish.posterFor(type, tmdbId) }.getOrNull()
        library.update { lib ->
            if (lib.isOnWatchlist(type, tmdbId)) lib
            else lib.copy(
                watchlist = lib.watchlist + WatchItem(
                    tmdbId, type, item.title, plPoster ?: item.posterPath, item.backdropPath, availability.provider, now,
                    releaseDate = item.releaseDate, mine = availability.mine, checkedAt = now,
                ),
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
                name = d.name, posterPath = s.posterPath ?: d.posterPath, backdropPath = d.backdropPath ?: s.backdropPath,
                seasons = d.seasons, lastAired = d.lastAired, nextAir = d.nextAir, ended = d.ended,
                updatedAt = System.currentTimeMillis(), firstAirDate = d.firstAirDate ?: s.firstAirDate,
            )
        })
    }

    /**
     * Sprawdza w TMDB, czy pozycje z listy „Do obejrzenia” są już w Waszych serwisach.
     * Gdy coś stało się gotowe do oglądania (pojawiło się w serwisie albo nadszedł dzień premiery),
     * dostaje znaczek „Nowe na …” i wysyłamy dymek na telewizor.
     */
    private suspend fun checkAvailability(providers: List<StreamingProvider>) {
        if (providers.isEmpty()) return
        val now = System.currentTimeMillis()
        val day = 24 * 3600_000L
        val due = library.state.value.toWatch().filter { !availabilityCheckedThisRun || now - checkedAtOf(it) > day }
        availabilityCheckedThisRun = true
        val today = java.time.LocalDate.now().toString()
        fun released(date: String?, at: Long): Boolean {
            if (date == null) return true
            val then = if (at > 0) java.time.Instant.ofEpochMilli(at).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString() else today
            return date <= then
        }
        for (entry in due) {
            val av = runCatching { tmdb.availability(entry.type, entry.tmdbId, providers) }.getOrNull() ?: continue
            val checkedAt = checkedAtOf(entry)
            val wasReady = entry.mine && released(entry.releaseDate, checkedAt)
            val isReady = av.mine && released(entry.releaseDate, now)
            val becameReady = isReady && !wasReady
            library.update { lib ->
                if (entry.followed) {
                    lib.copy(shows = lib.shows.map { s ->
                        if (s.tmdbId != entry.tmdbId) s
                        else s.copy(provider = av.provider, mine = av.mine, checkedAt = now, newSince = if (becameReady) now else s.newSince)
                    })
                } else {
                    lib.copy(watchlist = lib.watchlist.map { w ->
                        if (w.tmdbId != entry.tmdbId || w.type != entry.type) w
                        else w.copy(provider = av.provider, mine = av.mine, checkedAt = now, newSince = if (becameReady) now else w.newSince)
                    })
                }
            }
            if (becameReady) {
                val what = if (entry.type == MediaType.MOVIE) "Film" else "Serial"
                onNews?.invoke("Nowe na ${av.provider}", "$what „${entry.title}” jest już do obejrzenia na ${av.provider}.")
            }
        }
    }

    private fun checkedAtOf(entry: ToWatch): Long {
        val lib = library.state.value
        return if (entry.followed) lib.shows.firstOrNull { it.tmdbId == entry.tmdbId }?.checkedAt ?: 0
        else lib.watchlist.firstOrNull { it.tmdbId == entry.tmdbId && it.type == entry.type }?.checkedAt ?: 0
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
        return out.values.take(48)
    }

    companion object {
        const val FULL_REFRESH_MS = 24 * 3600_000L
        const val SHOWS_REFRESH_MS = 6 * 3600_000L
    }
}
