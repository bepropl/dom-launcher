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
import pl.dom.launcher.media.MediaItem
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
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
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import pl.dom.launcher.LauncherApp
import pl.dom.launcher.media.Episode
import pl.dom.launcher.media.durationLabel
import pl.dom.launcher.media.episodesLabel
import pl.dom.launcher.media.FollowedShow
import androidx.compose.foundation.lazy.rememberLazyListState
import pl.dom.launcher.media.Library
import pl.dom.launcher.media.MediaDetails
import pl.dom.launcher.media.MediaType
import pl.dom.launcher.media.SeasonEpisode
import pl.dom.launcher.media.TmdbImages

data class DetailRequest(val type: MediaType, val tmdbId: Int)

/** Przycisk „Oglądaj” otwartego podglądu – tu wraca zgubiony kursor. */
object DetailFocus {
    var main: FocusRequester? = null
}

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
    onTrailer: (key: String, title: String) -> Unit,
) {
    var reload by remember { mutableIntStateOf(0) }
    val details by produceState<Result<MediaDetails>?>(null, request, reload) {
        value = runCatching { app.media.details(request.type, request.tmdbId) }
    }
    val mainButton = remember(request) { FocusRequester() }

    BackHandler(onBack = onClose)

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
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Palette.Bg, 0.55f to Palette.Bg.copy(alpha = 0.88f), 0.85f to Color.Transparent)))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Palette.Bg)))

        when {
            details == null -> Text("Wczytuję…", fontSize = 12.sp, color = Palette.Muted, modifier = Modifier.align(Alignment.Center))
            d == null -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(details?.exceptionOrNull()?.message ?: "Nie udało się wczytać", fontSize = 12.sp, color = Palette.Soft)
                Spacer(Modifier.height(10.dp))
                Button(onClick = { reload++ }, modifier = Modifier.focusRequester(mainButton)) { Text("Spróbuj ponownie", fontSize = 11.sp) }
                LaunchedEffect(Unit) { delay(50); runCatching { mainButton.requestFocus() } }
            }
            else -> DetailContent(d, library, actions, mainButton, onTrailer = { d.trailerKey?.let { onTrailer(it, d.item.title) } }, onOpen = onOpen)
        }
    }

    // Zabezpieczenie kursora w launcherze wie, dokąd go wrócić, gdy podgląd jest otwarty.
    DisposableEffect(mainButton) {
        DetailFocus.main = mainButton
        onDispose { if (DetailFocus.main === mainButton) DetailFocus.main = null }
    }

    // Po wczytaniu kursor staje na „Oglądaj”.
    LaunchedEffect(details) {
        if (details?.getOrNull() != null) {
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
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 48.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Kolejność jak w Netflixie: tytuł i informacje, od razu pod nimi przyciski, niżej pełny opis.
        // Dzięki temu kursor na „Oglądaj” nie przewija ekranu i tytuł zawsze jest widoczny.
        Column(Modifier.padding(start = EdgePadding + 8.dp).fillMaxWidth(0.62f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
            // Film: o której się skończy, gdyby zacząć teraz (odświeża się co minutę).
            if (item.type == MediaType.MOVIE && d.runtime != null) {
                val now by rememberNow()
                val end = now.plusMinutes(d.runtime.toLong())
                Text("Skończy się o ${end.format(TimeFormat)}", fontSize = 10.sp, color = Palette.Amber)
            }
            d.release?.detailLine()?.let { Text(it, fontSize = 9.sp, color = Palette.Soft) }
            d.networks.takeIf { it.isNotEmpty() }?.let { Text("Kanał / serwis premiery: ${it.take(2).joinToString(", ")}", fontSize = 9.sp, color = Palette.Soft) }
            d.alsoAvailable?.let { Text("Dostępne też: $it", fontSize = 9.sp, color = Palette.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            followed?.let { show ->
                val next = show.nextToWatch
                Text(
                    when {
                        show.hasNewEpisode && next != null -> "Następny: ${next.label}" +
                            " · do nadrobienia ${episodesLabel(show.unwatchedCount)}" +
                            (show.catchUpMinutes?.takeIf { show.nextInfoValid }?.let { ", " + durationLabel(it) } ?: "")
                        !show.started -> "Jeszcze nie zaczęte – jest na liście „Do obejrzenia”"
                        else -> "Obejrzane do ${show.watched.labelUpTo}"
                    },
                    fontSize = 10.sp, color = Palette.Amber,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 6.dp, start = EdgePadding + 8.dp)) {
            Button(onClick = { actions.watch(item.provider, item.title, followed) }, modifier = Modifier.focusRequester(mainButton)) {
                Text(item.provider?.let { "▶  Oglądaj w $it" } ?: "▶  Oglądaj", fontSize = 11.sp)
            }
            if (d.trailerKey != null) OutlinedButton(onClick = onTrailer) { Text("Zwiastun", fontSize = 11.sp) }
            // Przyciski zawsze w tym samym miejscu – zmienia się tylko napis, więc kursor nie ginie.
            if (item.type == MediaType.TV) {
                OutlinedButton(onClick = { actions.menuForFollow(item.tmdbId, item.title, followed != null) }) {
                    Text(if (followed != null) "Obserwujecie ✓" else "Obserwuj…", fontSize = 11.sp)
                }
            }
            if (item.type == MediaType.MOVIE) {
                val watched = library.isWatched(MediaType.MOVIE, item.tmdbId)
                OutlinedButton(onClick = { actions.toggleMovieWatched(item, watched) }) {
                    Text(if (watched) "Obejrzany ✓" else "Obejrzany", fontSize = 11.sp)
                }
            }
            OutlinedButton(onClick = {
                if (followed != null) actions.explainFollowedOnList(followed.started)
                else actions.toggleWatchlist(item.type, item.tmdbId, item.title, onList)
            }) {
                Text(
                    when {
                        followed != null && !followed.started -> "Na liście ✓"
                        followed != null -> "W „Dalej”"
                        onList -> "Na liście ✓"
                        else -> "+ Do obejrzenia"
                    },
                    fontSize = 11.sp,
                )
            }
        }

        Column(
            Modifier.padding(start = EdgePadding + 8.dp, top = 4.dp).fillMaxWidth(0.62f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            d.tagline?.let { Text(it, fontSize = 11.sp, fontStyle = FontStyle.Italic, color = Palette.Soft) }
            if (item.overview.isNotBlank()) {
                Text(item.overview, fontSize = 11.sp, lineHeight = 16.sp, color = Palette.Text.copy(alpha = 0.92f))
            }
            if (d.cast.isNotEmpty()) Text("Obsada: " + d.cast.joinToString(", "), fontSize = 9.sp, color = Palette.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }

        if (item.type == MediaType.TV && d.seasons.isNotEmpty()) {
            Seasons(d, followed, actions)
        }

        // Seria filmów po kolei, z zaznaczeniem obejrzanych.
        d.collectionId?.let { id -> Collection(id, d.collectionName, item.tmdbId, library, onOpen) }

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
private fun Seasons(d: MediaDetails, followed: FollowedShow?, actions: MediaActions) {
    val app = LauncherApp.from(androidx.compose.ui.platform.LocalContext.current)
    val numbers = d.seasons.keys.sorted()
    val watched = followed?.watched
    val next = followed?.nextToWatch
    // „Nowy” = wydany po tym, jak ostatnio coś oznaczyliście jako obejrzane.
    val watchedDay = followed?.watchedAt?.takeIf { it > 0 }?.let {
        java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()
    }
    // Domyślnie sezon z następnym odcinkiem do obejrzenia (albo ten, w którym jesteście, albo pierwszy).
    var season by remember(d.item.tmdbId) {
        mutableIntStateOf(
            next?.season?.takeIf { it in numbers }
                ?: watched?.season?.takeIf { it in numbers }
                ?: numbers.first(),
        )
    }
    val episodesState = rememberLazyListState()
    // Poprzednia lista zostaje widoczna, dopóki nie wczyta się nowy sezon.
    val episodes by produceState<List<SeasonEpisode>?>(null, d.item.tmdbId, season) {
        value = runCatching { app.media.season(d.item.tmdbId, season) }.getOrDefault(emptyList())
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
        Box(Modifier.padding(start = EdgePadding + 4.dp)) { SectionTitle("Sezony i odcinki", "OK na odcinku – oznacz, że obejrzane do tego miejsca") }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = EdgePadding + 4.dp, vertical = 4.dp)) {
            items(numbers, key = { "season-$it" }) { n ->
                val selected = n == season
                // Zawsze ten sam rodzaj przycisku – po wybraniu sezonu kursor zostaje na miejscu.
                Button(
                    onClick = { season = n },
                    colors = ButtonDefaults.colors(
                        containerColor = if (selected) Palette.Amber.copy(alpha = 0.35f) else Palette.Card.copy(alpha = 0.7f),
                        contentColor = Palette.Text,
                        focusedContainerColor = Palette.Amber,
                        focusedContentColor = Palette.OnAmber,
                    ),
                ) {
                    val date = d.seasonYears[n]
                    val year = when {
                        date == null -> null
                        date > java.time.LocalDate.now().toString() -> "wkrótce"
                        else -> date.take(4)
                    }
                    Text((if (selected) "✓ " else "") + "Sezon $n" + (year?.let { " · $it" } ?: ""), fontSize = 10.sp)
                }
            }
        }
        val list = episodes
        // Po wczytaniu sezonu lista ustawia się na pierwszym nieobejrzanym odcinku.
        LaunchedEffect(list) {
            val l = list ?: return@LaunchedEffect
            val first = l.indexOfFirst { watched == null || Episode(it.season, it.number) > watched }
            if (first > 0) runCatching { episodesState.scrollToItem(first) }
        }
        when {
            list == null -> Text("Wczytuję odcinki…", fontSize = 10.sp, color = Palette.Muted, modifier = Modifier.padding(start = EdgePadding + 4.dp))
            list.isEmpty() -> Text("Brak listy odcinków.", fontSize = 10.sp, color = Palette.Muted, modifier = Modifier.padding(start = EdgePadding + 4.dp))
            else -> LazyRow(
                state = episodesState,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = EdgePadding + 4.dp, vertical = 6.dp),
            ) {
                items(list, key = { "ep-${it.season}-${it.number}" }) { ep ->
                    val seen = watched != null && Episode(ep.season, ep.number) <= watched
                    val aired = ep.airDate?.let { it <= java.time.LocalDate.now().toString() } ?: false
                    val isNew = !seen && aired && watchedDay != null && ep.airDate != null && ep.airDate > watchedDay
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
                            isNew -> "Nowy"
                            !aired -> "Wkrótce"
                            else -> null
                        },
                        badgeStrong = isNew,
                        width = 190.dp,
                        height = 107.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun Collection(id: Int, name: String?, currentId: Int, library: Library, onOpen: (DetailRequest) -> Unit) {
    val app = LauncherApp.from(androidx.compose.ui.platform.LocalContext.current)
    val parts by produceState<List<MediaItem>?>(null, id) {
        value = runCatching { app.media.collection(id).second }.getOrNull()
    }
    val list = parts?.takeIf { it.size > 1 } ?: return
    MediaRow("Seria: ${name ?: "filmy"}", "${list.size} części, po kolei") {
        itemsIndexed(list, key = { _, it -> "col-${it.tmdbId}" }) { index, part ->
            PosterCard(
                title = part.title,
                label = when {
                    part.tmdbId == currentId -> "Ten film"
                    library.isWatched(MediaType.MOVIE, part.tmdbId) -> "${index + 1}. · obejrzany ✓"
                    else -> listOfNotNull("${index + 1}.", part.year).joinToString(" · ")
                },
                labelStrong = part.tmdbId == currentId,
                imageUrl = TmdbImages.poster(part.posterPath),
                onClick = { if (part.tmdbId != currentId) onOpen(DetailRequest(MediaType.MOVIE, part.tmdbId)) },
            )
        }
    }
}

private fun seasonsLabel(n: Int): String = when {
    n == 1 -> "1 sezon"
    n % 10 in 2..4 && n % 100 !in 12..14 -> "$n sezony"
    else -> "$n sezonów"
}
