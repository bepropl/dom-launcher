package pl.dom.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import pl.dom.launcher.LauncherApp
import pl.dom.launcher.media.Episode
import pl.dom.launcher.media.Library
import pl.dom.launcher.media.MediaDetails
import pl.dom.launcher.media.MediaType
import pl.dom.launcher.media.SeasonEpisode
import pl.dom.launcher.media.TmdbImages

data class DetailRequest(val type: MediaType, val tmdbId: Int)

/**
 * Podgląd tytułu na cały ekran (jak w Netflixie): tło z kadru, opis, przyciski
 * (oglądaj, zwiastun, obserwuj / do obejrzenia), sezony i odcinki, podobne tytuły.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DetailScreen(
    request: DetailRequest,
    app: LauncherApp,
    library: Library,
    actions: MediaActions,
    onOpen: (DetailRequest) -> Unit,
    onClose: () -> Unit,
) {
    var reload by remember { mutableIntStateOf(0) }
    val details by produceState<Result<MediaDetails>?>(null, request, reload) {
        value = runCatching { app.media.details(request.type, request.tmdbId) }
    }
    var trailer by remember(request) { mutableStateOf(false) }
    val mainButton = remember(request) { FocusRequester() }

    BackHandler(enabled = !trailer, onBack = onClose)

    Box(
        Modifier
            .fillMaxSize()
            .background(Palette.Bg)
            // Fokus nie ucieka do ekranu pod spodem.
            .focusProperties { exit = { FocusRequester.Cancel } }
            .focusGroup(),
    ) {
        val d = details?.getOrNull()
        AsyncImage(
            model = TmdbImages.backdropLarge(d?.item?.backdropPath),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Palette.Bg, 0.45f to Palette.Bg.copy(alpha = 0.85f), 0.8f to Color.Transparent)))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Palette.Bg)))

        when {
            details == null -> Text("Wczytuję…", fontSize = 12.sp, color = Palette.Muted, modifier = Modifier.align(Alignment.Center))
            d == null -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(details?.exceptionOrNull()?.message ?: "Nie udało się wczytać", fontSize = 12.sp, color = Palette.Soft)
                Spacer(Modifier.height(10.dp))
                Button(onClick = { reload++ }, modifier = Modifier.focusRequester(mainButton)) { Text("Spróbuj ponownie", fontSize = 11.sp) }
                LaunchedEffect(Unit) { delay(50); runCatching { mainButton.requestFocus() } }
            }
            else -> DetailContent(d, library, actions, mainButton, onTrailer = { trailer = true }, onOpen = onOpen)
        }

        if (trailer && d?.trailerKey != null) {
            TrailerPlayer(d.trailerKey, d.item.title, onClose = {
                trailer = false
            })
        }
    }

    // Po zamknięciu zwiastuna wracamy na przyciski.
    LaunchedEffect(trailer, details) {
        if (!trailer && details?.getOrNull() != null) {
            delay(80)
            runCatching { mainButton.requestFocus() }
        }
    }
}

@Composable
private fun DetailContent(
    d: MediaDetails,
    library: Library,
    actions: MediaActions,
    mainButton: FocusRequester,
    onTrailer: () -> Unit,
    onOpen: (DetailRequest) -> Unit,
) {
    val item = d.item
    val followed = library.shows.firstOrNull { it.tmdbId == item.tmdbId }
    val onList = library.isOnWatchlist(item.type, item.tmdbId)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 64.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.padding(start = EdgePadding + 8.dp).width(520.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            item.provider?.let { Text("W serwisie $it", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Palette.Amber) }
            Text(item.title, fontSize = 30.sp, fontWeight = FontWeight.Medium, color = Palette.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val meta = listOfNotNull(
                item.year,
                d.genres.take(3).joinToString(", ").ifBlank { null },
                d.runtime?.let { "${it / 60} h ${it % 60} min".removePrefix("0 h ") },
                if (item.type == MediaType.TV && d.seasons.isNotEmpty()) seasonsLabel(d.seasons.size) else null,
                item.rating?.let { "TMDB %.1f".format(it) },
            )
            Text(meta.joinToString("  ·  "), fontSize = 10.sp, color = Palette.Soft)
            d.tagline?.let { Text(it, fontSize = 11.sp, fontStyle = FontStyle.Italic, color = Palette.Soft) }
            if (item.overview.isNotBlank()) {
                Text(item.overview, fontSize = 11.sp, color = Palette.Text.copy(alpha = 0.9f), maxLines = 5, overflow = TextOverflow.Ellipsis)
            }
            if (d.cast.isNotEmpty()) Text("Obsada: " + d.cast.joinToString(", "), fontSize = 9.sp, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            followed?.let { show ->
                val next = show.nextToWatch
                Text(
                    when {
                        show.hasNewEpisode && next != null -> "Następny do obejrzenia: ${next.code} (${show.unwatchedCount} czeka)"
                        !show.started -> "Jeszcze nie zaczęte – jest na liście „Do obejrzenia”"
                        else -> "Obejrzane do ${show.watched.code}"
                    },
                    fontSize = 10.sp, color = Palette.Amber,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 6.dp, start = EdgePadding + 8.dp)) {
            Button(onClick = { actions.watch(item.provider, item.title) }, modifier = Modifier.focusRequester(mainButton)) {
                Text(item.provider?.let { "▶  Oglądaj w $it" } ?: "▶  Oglądaj", fontSize = 11.sp)
            }
            if (d.trailerKey != null) OutlinedButton(onClick = onTrailer) { Text("Zwiastun", fontSize = 11.sp) }
            if (item.type == MediaType.TV) {
                if (followed != null) {
                    OutlinedButton(onClick = { actions.unfollow(item.tmdbId, item.title) }) { Text("Przestań obserwować", fontSize = 11.sp) }
                } else {
                    OutlinedButton(onClick = { actions.follow(item.tmdbId, item.title, caughtUp = false) }) { Text("Obserwuj – od początku", fontSize = 11.sp) }
                    OutlinedButton(onClick = { actions.follow(item.tmdbId, item.title, caughtUp = true) }) { Text("Obserwuj – na bieżąco", fontSize = 11.sp) }
                }
            }
            if (followed == null) {
                OutlinedButton(onClick = { actions.toggleWatchlist(item.type, item.tmdbId, item.title, onList) }) {
                    Text(if (onList) "Usuń z „Do obejrzenia”" else "+ Do obejrzenia", fontSize = 11.sp)
                }
            }
        }

        if (item.type == MediaType.TV && d.seasons.isNotEmpty()) {
            Seasons(d, followed?.watched, actions)
        }

        if (d.recommendations.isNotEmpty()) {
            MediaRow("Podobne") {
                items(d.recommendations, key = { "rec-${it.type}-${it.tmdbId}" }) { rec ->
                    PosterCard(
                        title = rec.title,
                        label = if (rec.type == MediaType.MOVIE) "Film" else "Serial",
                        imageUrl = TmdbImages.poster(rec.posterPath),
                        onClick = { onOpen(DetailRequest(rec.type, rec.tmdbId)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun Seasons(d: MediaDetails, watched: Episode?, actions: MediaActions) {
    val app = LauncherApp.from(androidx.compose.ui.platform.LocalContext.current)
    val numbers = d.seasons.keys.sorted()
    // Domyślnie sezon, w którym jesteście (albo pierwszy).
    var season by remember(d.item.tmdbId) {
        mutableIntStateOf(watched?.let { w -> if (w.season in numbers) w.season else null } ?: numbers.first())
    }
    val episodes by produceState<List<SeasonEpisode>?>(null, d.item.tmdbId, season) {
        value = null
        value = runCatching { app.media.season(d.item.tmdbId, season) }.getOrDefault(emptyList())
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
        Box(Modifier.padding(start = EdgePadding + 4.dp)) { SectionTitle("Sezony i odcinki", "OK na odcinku – oznacz, że obejrzane do tego miejsca") }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = EdgePadding + 4.dp, vertical = 4.dp)) {
            items(numbers, key = { "season-$it" }) { n ->
                val selected = n == season
                if (selected) {
                    Button(onClick = { season = n }) { Text("Sezon $n", fontSize = 10.sp) }
                } else {
                    OutlinedButton(onClick = { season = n }) { Text("Sezon $n", fontSize = 10.sp) }
                }
            }
        }
        val list = episodes
        when {
            list == null -> Text("Wczytuję odcinki…", fontSize = 10.sp, color = Palette.Muted, modifier = Modifier.padding(start = EdgePadding + 4.dp))
            list.isEmpty() -> Text("Brak listy odcinków.", fontSize = 10.sp, color = Palette.Muted, modifier = Modifier.padding(start = EdgePadding + 4.dp))
            else -> LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(horizontal = EdgePadding + 4.dp, vertical = 6.dp)) {
                items(list, key = { "ep-${it.season}-${it.number}" }) { ep ->
                    val seen = watched != null && Episode(ep.season, ep.number) <= watched
                    val aired = ep.airDate?.let { it <= java.time.LocalDate.now().toString() } ?: false
                    WideMediaCard(
                        title = "${ep.number}. ${ep.name}",
                        subtitle = listOfNotNull(
                            friendlyDate(ep.airDate)?.let { if (aired) it else "premiera $it" },
                            ep.runtime?.let { "$it min" },
                        ).joinToString(" · "),
                        imageUrl = TmdbImages.still(ep.stillPath),
                        onClick = { actions.menuForEpisode(d, ep) },
                        badge = when {
                            seen -> "Obejrzany ✓"
                            !aired -> "Wkrótce"
                            else -> null
                        },
                        badgeStrong = !seen && aired,
                        width = 190.dp,
                        height = 107.dp,
                    )
                }
            }
        }
    }
}

private fun seasonsLabel(n: Int): String = when {
    n == 1 -> "1 sezon"
    n % 10 in 2..4 && n % 100 !in 12..14 -> "$n sezony"
    else -> "$n sezonów"
}
