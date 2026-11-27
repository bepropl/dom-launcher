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
import pl.dom.launcher.media.ToWatch

/** Seriale z nowymi odcinkami – najświeższe na początku. */
fun Library.showsWithNewEpisodes(): List<FollowedShow> =
    shows.filter { it.started && it.hasNewEpisode }.sortedByDescending { it.lastAired?.airDate ?: "" }

/** Seriale, z którymi jesteście na bieżąco – najbliższa premiera na początku. */
fun Library.showsWaiting(): List<FollowedShow> =
    shows.filter { it.started && !it.hasNewEpisode }.sortedBy { it.nextAir?.airDate ?: "9999" }

fun FollowedShow.cardSubtitle(): String {
    val next = nextToWatch
    val date = friendlyDate(newEpisodeDate)
    return listOfNotNull(provider, next?.code, date).joinToString(" · ")
}

fun FollowedShow.cardBadge(): String =
    if (newEpisodeDate != null && unwatchedCount <= 1) "Nowy odcinek" else "$unwatchedCount do obejrzenia"

fun FollowedShow.waitingText(): String {
    val next = nextAir
    return when {
        next != null && next.number == 1 -> "nowy sezon · ${friendlyDate(next.airDate) ?: "data nieznana"}"
        next != null -> "${next.code} · ${friendlyDate(next.airDate) ?: "data nieznana"}"
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
                    title = show.name, subtitle = show.cardSubtitle(), imageUrl = TmdbImages.backdrop(show.backdropPath),
                    onClick = { actions.openProvider(show.provider, show.name) }, onLongClick = { actions.detail(MediaType.TV, show.tmdbId) },
                    badge = show.cardBadge(),
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

/** Zakładka Filmy: wyróżniony film, polecane w Waszych serwisach i biblioteka z Emby. */
@Composable
fun MoviesScreen(feeds: Feeds, actions: MediaActions) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Problems(feeds)
        feeds.movies.firstOrNull()?.let { Hero(it, actions) }
        if (feeds.movies.size > 1) {
            MediaGrid("Polecane w Waszych serwisach", rows = 2, cellHeight = BigPosterHeight) {
                gridItems(feeds.movies.drop(1), key = { "m-${it.tmdbId}" }) { item ->
                    TmdbPoster(item, actions, BigPosterWidth, BigPosterHeight)
                }
            }
        } else if (feeds.loading) {
            EmptyNote("Wczytuję…")
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
fun ShowsScreen(library: Library, feeds: Feeds, actions: MediaActions) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Problems(feeds)
        if (library.shows.isNotEmpty()) {
            MediaRow("Obserwowane", "OK – szczegóły i odcinki") {
                items(library.shows, key = { "f-${it.tmdbId}" }) { show ->
                    PosterCard(
                        title = show.name,
                        label = if (show.hasNewEpisode) "${show.unwatchedCount} nowe" else show.provider,
                        imageUrl = TmdbImages.poster(show.posterPath),
                        onClick = { actions.detail(MediaType.TV, show.tmdbId) },
                        onLongClick = { actions.menuForShow(show) },
                    )
                }
            }
        }
        if (feeds.shows.isNotEmpty()) {
            MediaGrid("Popularne w Waszych serwisach", "OK – szczegóły i zwiastun", rows = 3, cellHeight = PosterHeight) {
                gridItems(feeds.shows, key = { "s-${it.tmdbId}" }) { item -> TmdbPoster(item, actions) }
            }
        } else if (feeds.loading) {
            EmptyNote("Wczytuję…")
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
    val (label, strong) = when {
        entry.ready && entry.isNew -> "Nowe na ${entry.provider}" to true
        entry.ready -> listOfNotNull(kind, entry.provider).joinToString(" · ") to false
        !entry.released -> "Premiera ${friendlyDate(entry.releaseDate) ?: ""}".trim() to false
        entry.provider != null -> "Nie w Waszych serwisach · jest na ${entry.provider}" to false
        else -> "Jeszcze nie w Waszych serwisach" to false
    }
    PosterCard(
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
    PosterCard(
        width = width,
        height = height,
        title = item.title,
        label = item.provider,
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
