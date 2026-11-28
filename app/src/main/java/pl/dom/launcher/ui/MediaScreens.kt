package pl.dom.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import pl.dom.launcher.media.EmbyItem
import pl.dom.launcher.media.Feeds
import pl.dom.launcher.media.FollowedShow
import pl.dom.launcher.media.Library
import pl.dom.launcher.media.MediaItem
import pl.dom.launcher.media.MediaType
import pl.dom.launcher.media.TmdbImages
import pl.dom.launcher.media.episodesLabel
import pl.dom.launcher.media.durationLabel
import pl.dom.launcher.media.ToWatch

/** Seriale z nowymi odcinkami – najświeższe na początku. */
fun Library.showsWithNewEpisodes(): List<FollowedShow> =
    shows.filter { it.started && it.hasNewEpisode }.sortedByDescending { it.lastAired?.airDate ?: "" }

/** Seriale, z którymi jesteście na bieżąco – najbliższa premiera na początku. */
fun Library.showsWaiting(): List<FollowedShow> =
    // Tylko ze znaną datą następnego odcinka – reszta jest w „Obserwowanych”.
    shows.filter { it.started && !it.hasNewEpisode && it.nextAir?.airDate != null }.sortedBy { it.nextAir?.airDate ?: "9999" }

fun FollowedShow.cardSubtitle(): String {
    val next = nextToWatch ?: return provider.orEmpty()
    // „s. 2, odc. 5 · Tytuł odcinka” albo z dostawcą, gdy tytułu nie znamy.
    val title = nextName?.takeIf { nextInfoValid }
    return listOfNotNull(next.short, title ?: provider, friendlyDate(newEpisodeDate)).joinToString(" · ")
}

/** Kadr następnego odcinka (gdy znany), inaczej tło serialu. */
fun FollowedShow.cardImage(): String? =
    (if (nextInfoValid) TmdbImages.stillLarge(nextStill) else null) ?: TmdbImages.backdrop(backdropPath)

fun FollowedShow.cardBadge(): String {
    val count = unwatchedCount
    val time = catchUpMinutes?.takeIf { nextInfoValid }?.let { durationLabel(it) }
    return when {
        count <= 1 && newEpisodeDate != null -> "Nowy odcinek"
        time != null -> "${episodesLabel(count)} · $time"
        else -> "${episodesLabel(count)} do obejrzenia"
    }
}

fun FollowedShow.waitingText(): String {
    val next = nextAir
    return when {
        next != null && next.number == 1 -> "nowy sezon · ${friendlyDate(next.airDate) ?: "data nieznana"}"
        next != null -> "${next.short} · ${friendlyDate(next.airDate) ?: "data nieznana"}"
        ended -> "serial zakończony"
        else -> "data nieznana"
    }
}

@Composable
private fun Problems(feeds: Feeds) {
    feeds.problems.forEach { EmptyNote(it) }
}

@Composable
private fun EmbyCard(item: EmbyItem, actions: MediaActions, badge: String? = null) {
    WideMediaCard(
        serviceApp = rememberServiceApp("Emby"),
        title = item.title,
        subtitle = listOf("Emby", item.subtitle).filter { it.isNotBlank() }.joinToString(" · "),
        imageUrl = item.imageUrl,
        onClick = { actions.openEmby() },
        onLongClick = { actions.menuForEmby(item) },
        badge = badge,
        badgeStrong = false,
        progress = item.progress,
    )
}

/** Zakładka „Do obejrzenia”: Dalej, Lista do obejrzenia, Czekacie na nowe odcinki. */
@Composable
fun WatchlistScreen(library: Library, feeds: Feeds, actions: MediaActions) {
    val next = library.showsWithNewEpisodes()
    val waiting = library.showsWaiting()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Problems(feeds)
        MediaRow("Dalej", "OK – oglądaj, przytrzymaj OK – szczegóły") {
            items(next, key = { "show-${it.tmdbId}" }) { show ->
                WideMediaCard(
                    title = show.name, subtitle = show.cardSubtitle(), imageUrl = show.cardImage(),
                    onClick = { actions.watchShow(show) }, onLongClick = { actions.detail(MediaType.TV, show.tmdbId) },
                    badge = show.cardBadge(),
                    serviceApp = rememberServiceApp(show.provider),
                )
            }
            items(feeds.embyResume, key = { "emby-${it.id}" }) { EmbyCard(it, actions, "Kontynuuj") }
        }
        if (next.isEmpty() && feeds.embyResume.isEmpty()) EmptyNote("Nic nie czeka. Obserwuj seriale w zakładce Seriale albo na stronie w telefonie.")

        val toWatch = library.toWatch()
        val ready = toWatch.filter { it.ready }.sortedWith(compareByDescending<ToWatch> { it.isNew }.thenByDescending { it.newSince }.thenByDescending { it.addedAt })
        val upcoming = toWatch.filterNot { it.ready }.sortedWith(
            compareBy<ToWatch> { it.released }.thenBy { it.releaseDate ?: "9999" }.thenBy { it.title.lowercase() },
        )
        if (ready.isNotEmpty()) {
            MediaRow("Gotowe do oglądania", "Niezaczęte, już w Waszych serwisach · OK – szczegóły") {
                items(ready, key = { "r-${it.type}-${it.tmdbId}" }) { entry -> ToWatchPoster(entry, actions) }
            }
        }
        if (upcoming.isNotEmpty()) {
            MediaRow("Czekamy", "Premiera albo jeszcze nie ma w Waszych serwisach – sprawdzam codziennie") {
                items(upcoming, key = { "u-${it.type}-${it.tmdbId}" }) { entry -> ToWatchPoster(entry, actions) }
            }
        }
        if (toWatch.isEmpty()) EmptyNote("Lista jest pusta. Dodasz do niej filmy i seriale w podglądzie tytułu („+ Do obejrzenia”) albo na stronie w telefonie.")

        if (waiting.isNotEmpty()) {
            MediaRow("Czekacie na nowe odcinki", "Jesteście na bieżąco") {
                items(waiting, key = { "wait-${it.tmdbId}" }) { show ->
                    SmallMediaCard(
                        title = show.name,
                        subtitle = listOfNotNull(show.provider, show.waitingText()).joinToString(" · "),
                        imageUrl = TmdbImages.poster(show.posterPath),
                        onClick = { actions.detail(MediaType.TV, show.tmdbId) },
                        onLongClick = { actions.menuForShow(show) },
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

/**
 * Wyszukiwarka na górze zakładek Filmy i Seriale: duże pole na środku.
 * OK na polu → piszesz od razu w nim (klawiatura telewizora, działa też mikrofon).
 * Wyniki pojawiają się same, 2 s po ostatniej literze; strzałka w dół przenosi na wyniki.
 */
@Composable
private fun SearchSection(type: MediaType, actions: MediaActions, reset: Int) {
    // reset zmienia się przy zmianie zakładki i po Home – wtedy pole startuje puste.
    // Podgląd tytułu nie zmienia zakładki, więc po powrocie wyszukiwanie zostaje.
    var query by rememberSaveable(type, reset) { mutableStateOf("") }
    var searched by rememberSaveable(type, reset) { mutableStateOf("") }
    LaunchedEffect(query) {
        if (query.isNotBlank()) delay(2000)
        searched = query.trim()
    }
    val results by produceState<Result<List<MediaItem>>?>(null, searched) {
        value = null
        value = if (searched.isBlank()) null else runCatching { actions.search(searched, type) }
    }
    var editing by remember { mutableStateOf(false) }
    val field = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(editing) {
        if (editing) {
            delay(60)
            runCatching { field.requestFocus() }
            keyboard?.show()
        }
    }
    val what = if (type == MediaType.MOVIE) "filmu" else "serialu"

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Surface(
                onClick = { editing = true },
                modifier = Modifier.fillMaxWidth(0.6f).height(46.dp),
                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(23.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Palette.Card.copy(alpha = 0.92f),
                    contentColor = Palette.Text,
                    focusedContainerColor = Palette.Card,
                    focusedContentColor = Palette.Text,
                ),
                border = ClickableSurfaceDefaults.border(
                    focusedBorder = Border(BorderStroke(2.dp, Palette.Amber), shape = RoundedCornerShape(23.dp)),
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
            ) {
                Row(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("⌕", fontSize = 18.sp, color = Palette.Amber)
                    Spacer(Modifier.width(12.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text(
                                if (editing) "Wpisz tytuł…" else "Szukaj $what – naciśnij OK",
                                fontSize = 13.sp, color = Palette.Muted,
                            )
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 14.sp, color = Palette.Text),
                            cursorBrush = SolidColor(Palette.Amber),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                searched = query.trim()
                                keyboard?.hide()
                            }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(field)
                                .focusProperties { canFocus = editing }
                                .onFocusChanged { if (!it.isFocused && editing) editing = false },
                        )
                    }
                    if (query.isNotEmpty()) {
                        Spacer(Modifier.width(10.dp))
                        Text(if (results == null && searched.isNotBlank()) "szukam…" else "", fontSize = 10.sp, color = Palette.Muted)
                    }
                }
            }
        }
        if (searched.isNotBlank()) {
            val r = results
            when {
                r == null -> EmptyNote("Szukam „$searched”…")
                r.isFailure -> EmptyNote(r.exceptionOrNull()?.message ?: "Nie udało się wyszukać")
                r.getOrThrow().isEmpty() -> EmptyNote("Nic nie znalazłem dla „$searched”.")
                else -> MediaRow("Wyniki: „$searched”", "OK – szczegóły, obserwuj, + Do obejrzenia") {
                    items(r.getOrThrow(), key = { "find-${it.tmdbId}" }) { item -> TmdbPoster(item, actions) }
                }
            }
        }
    }
}

/** Zakładka Filmy: wyszukiwarka, wyróżniony film, polecane w Waszych serwisach i biblioteka z Emby. */
@Composable
fun MoviesScreen(feeds: Feeds, actions: MediaActions, searchReset: Int = 0, library: Library = Library()) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SearchSection(MediaType.MOVIE, actions, searchReset)
        Problems(feeds)
        val movies = feeds.movies.filterNot { library.isHidden(MediaType.MOVIE, it.tmdbId) || library.isWatched(MediaType.MOVIE, it.tmdbId) }
        if (movies.isNotEmpty()) {
            MediaGrid("Polecane w Waszych serwisach", "OK – szczegóły, przytrzymaj OK – „Nie interesuje mnie”", rows = 2, cellHeight = BigPosterHeight) {
                gridItems(movies, key = { "m-${it.tmdbId}" }) { item ->
                    TmdbPoster(item, actions, BigPosterWidth, BigPosterHeight)
                }
            }
        } else if (feeds.loading) {
            EmptyNote("Wczytuję…")
        }
        feeds.becauseMovies?.let { b ->
            val items = b.items.filterNot { library.isHidden(MediaType.MOVIE, it.tmdbId) || library.isWatched(MediaType.MOVIE, it.tmdbId) }
            if (items.isNotEmpty()) {
                MediaRow("Bo oglądaliście „${b.seed}”") {
                    items(items, key = { "bm-${it.tmdbId}" }) { item -> TmdbPoster(item, actions) }
                }
            }
        }
        if (feeds.embyMovies.isNotEmpty()) {
            MediaRow("Z Waszej biblioteki na NAS (Emby)") {
                items(feeds.embyMovies, key = { "em-${it.id}" }) { EmbyCard(it, actions) }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

/** Zakładka Seriale: obserwowane, popularne w Waszych serwisach i biblioteka z Emby. */
@Composable
fun ShowsScreen(library: Library, feeds: Feeds, actions: MediaActions, searchReset: Int = 0) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SearchSection(MediaType.TV, actions, searchReset)
        Problems(feeds)
        if (feeds.shows.isNotEmpty()) {
            MediaGrid("Popularne w Waszych serwisach", "OK – szczegóły, przytrzymaj OK – „Nie interesuje mnie”", rows = 3, cellHeight = PosterHeight) {
                gridItems(feeds.shows.filterNot { library.isHidden(MediaType.TV, it.tmdbId) }, key = { "s-${it.tmdbId}" }) { item -> TmdbPoster(item, actions) }
            }
        } else if (feeds.loading) {
            EmptyNote("Wczytuję…")
        }
        feeds.becauseShows?.let { b ->
            val items = b.items.filterNot { library.isHidden(MediaType.TV, it.tmdbId) || library.isFollowed(it.tmdbId) }
            if (items.isNotEmpty()) {
                MediaRow("Bo oglądaliście „${b.seed}”") {
                    items(items, key = { "bs-${it.tmdbId}" }) { item -> TmdbPoster(item, actions) }
                }
            }
        }
        if (library.shows.isNotEmpty()) {
            MediaRow("Obserwowane", "OK – szczegóły i odcinki") {
                items(library.shows, key = { "f-${it.tmdbId}" }) { show ->
                    val service = rememberServiceApp(show.provider)
                    PosterCard(
                        serviceApp = service,
                        title = show.name,
                        label = if (show.hasNewEpisode) "${show.unwatchedCount} nowe" else show.provider.takeIf { service == null },
                        imageUrl = TmdbImages.poster(show.posterPath),
                        onClick = { actions.detail(MediaType.TV, show.tmdbId) },
                        onLongClick = { actions.menuForShow(show) },
                    )
                }
            }
        }
        if (feeds.embyShows.isNotEmpty()) {
            MediaRow("Z Waszej biblioteki na NAS (Emby)") {
                items(feeds.embyShows, key = { "es-${it.id}" }) { EmbyCard(it, actions) }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun ToWatchPoster(entry: ToWatch, actions: MediaActions) {
    val kind = if (entry.type == MediaType.MOVIE) "Film" else "Serial"
    // W Waszym serwisie: ikona aplikacji w rogu zamiast nazwy serwisu.
    val service = if (entry.ready) rememberServiceApp(entry.provider) else null
    val (label, strong) = when {
        entry.ready && entry.isNew -> (if (service != null) "Nowe" else "Nowe na ${entry.provider}") to true
        entry.ready -> (if (service != null) kind else listOfNotNull(kind, entry.provider).joinToString(" · ")) to false
        !entry.released -> (
            entry.release?.cardLabel()
                ?: listOfNotNull("Premiera ${friendlyDate(entry.releaseDate) ?: ""}".trim(), entry.network).joinToString(" · ")
            ) to false
        entry.provider != null -> "Nie w Waszych serwisach · jest na ${entry.provider}" to false
        else -> (entry.release?.cardLabel() ?: "Jeszcze nie w Waszych serwisach") to false
    }
    PosterCard(
        serviceApp = service,
        title = entry.title,
        label = label,
        labelStrong = strong,
        imageUrl = TmdbImages.poster(entry.posterPath),
        onClick = { actions.detail(entry.type, entry.tmdbId) },
        onLongClick = { actions.menuForToWatch(entry) },
    )
}

@Composable
private fun TmdbPoster(item: MediaItem, actions: MediaActions, width: Dp = PosterWidth, height: Dp = PosterHeight) {
    val service = rememberServiceApp(item.provider)
    PosterCard(
        width = width,
        height = height,
        title = item.title,
        label = item.provider.takeIf { service == null },
        serviceApp = service,
        imageUrl = TmdbImages.poster(item.posterPath),
        onClick = { actions.detail(item.type, item.tmdbId) },
        onLongClick = { actions.menuForItem(item) },
    )
}

@Composable
private fun Hero(item: MediaItem, actions: MediaActions) {
    Box(
        Modifier
            .padding(horizontal = EdgePadding)
            .fillMaxWidth()
            .height(150.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Palette.Card),
    ) {
        AsyncImage(
            model = TmdbImages.backdrop(item.backdropPath),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(0.25f to Palette.Bg.copy(alpha = 0.96f), 0.7f to Color.Transparent),
            ),
        )
        Column(
            Modifier.align(Alignment.CenterStart).width(360.dp).padding(start = 22.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            item.provider?.let { Text("W serwisie $it", fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = Palette.Amber) }
            Text(item.title, fontSize = 22.sp, fontWeight = FontWeight.Medium, color = Palette.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(item.year, item.rating?.let { "TMDB %.1f".format(it) }).joinToString(" · "),
                fontSize = 9.sp, color = Palette.Muted,
            )
            Text(item.overview, fontSize = 9.sp, color = Palette.Soft, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { actions.openProvider(item.provider, item.title) }) {
                    Text(item.provider?.let { "Oglądaj w $it" } ?: "Oglądaj", fontSize = 10.sp)
                }
                OutlinedButton(onClick = { actions.detail(item.type, item.tmdbId) }) { Text("Więcej", fontSize = 10.sp) }
            }
        }
    }
}
