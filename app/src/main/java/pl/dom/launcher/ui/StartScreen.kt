package pl.dom.launcher.ui

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.activity.compose.BackHandler
import android.os.SystemClock
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import pl.dom.launcher.data.TvApp
import pl.dom.launcher.update.UpdateState
import pl.dom.launcher.media.Feeds
import pl.dom.launcher.media.Library
import pl.dom.launcher.media.TmdbImages
import androidx.compose.foundation.lazy.items

private val AppTileWidth = 110.dp
private val AppTileHeight = 62.dp

@Composable
fun StartScreen(
    favorites: List<TvApp>,
    onLaunch: (TvApp) -> Unit,
    onOpenAllApps: () -> Unit,
    onReorder: (List<String>) -> Unit,
    library: Library,
    feeds: Feeds,
    actions: MediaActions,
    updateState: UpdateState = UpdateState.Idle,
) {
    val firstApp = remember { FocusRequester() }
    // Po starcie fokus ląduje na pierwszej aplikacji, a nie w menu.
    LaunchedEffect(favorites.isNotEmpty()) {
        if (favorites.isNotEmpty()) {
            delay(150)
            runCatching { firstApp.requestFocus() }
        }
    }

    // Sortowanie: przytrzymanie OK na aplikacji → strzałki ◀ ▶ przesuwają ją, OK / Wstecz zapisuje.
    var moving by remember { mutableStateOf<String?>(null) }
    var movingSince by remember { mutableLongStateOf(0L) }
    var order by remember { mutableStateOf<List<TvApp>?>(null) }
    LaunchedEffect(favorites) { if (moving == null) order = null }
    val shown = order ?: favorites

    fun finishMove() {
        val current = order
        moving = null
        if (current != null && current.map { it.packageName } != favorites.map { it.packageName }) {
            onReorder(current.map { it.packageName })
        }
    }

    fun moveKey(event: KeyEvent): Boolean {
        val pkg = moving ?: return false
        val list = order ?: return false
        val delta = when (event.key) {
            Key.DirectionLeft -> -1
            Key.DirectionRight -> 1
            Key.DirectionUp, Key.DirectionDown -> {
                if (event.type == KeyEventType.KeyDown) finishMove()
                return true
            }
            else -> return false
        }
        if (event.type == KeyEventType.KeyDown) {
            val i = list.indexOfFirst { it.packageName == pkg }
            val j = i + delta
            if (i >= 0 && j in list.indices) {
                order = list.toMutableList().apply { add(j, removeAt(i)) }
            }
        }
        return true
    }

    BackHandler(enabled = moving != null) { finishMove() }

    Column(Modifier.fillMaxSize().padding(top = 12.dp)) {
        val shows = library.showsWithNewEpisodes()
        MediaRow("Nowe odcinki Waszych seriali", if (shows.isNotEmpty()) "${shows.size} do obejrzenia" else null) {
            items(shows, key = { "show-${it.tmdbId}" }) { show ->
                WideMediaCard(
                    title = show.name, subtitle = show.cardSubtitle(), imageUrl = TmdbImages.backdrop(show.backdropPath),
                    onClick = { actions.openProvider(show.provider, show.name) }, onLongClick = { actions.menuForShow(show) },
                    badge = show.cardBadge(),
                )
            }
            items(feeds.embyResume, key = { "emby-${it.id}" }) { item ->
                WideMediaCard(
                    title = item.title,
                    subtitle = listOf("Emby", item.subtitle).filter { it.isNotBlank() }.joinToString(" · "),
                    imageUrl = item.imageUrl,
                    onClick = { actions.openEmby() },
                    onLongClick = { actions.menuForEmby(item) },
                    badge = "Kontynuuj", badgeStrong = false, progress = item.progress,
                )
            }
        }
        if (shows.isEmpty() && feeds.embyResume.isEmpty()) {
            EmptyNote(
                feeds.problems.firstOrNull()
                    ?: "Brak nowych odcinków. Seriale obserwujesz w zakładce Seriale albo na stronie w telefonie.",
            )
        }

        // Środek ekranu zostaje pusty – tu widać domek.
        Spacer(Modifier.weight(1f))

        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (updateState is UpdateState.Available) {
                Text(
                    "Dostępna aktualizacja ${updateState.info.versionName} – zainstalujesz ją w Ustawieniach",
                    fontSize = 10.sp,
                    color = Palette.Amber,
                )
            } else if (moving != null) {
                Text("Przesuwasz aplikację: ◀ ▶ zmienia miejsce, OK albo Wstecz zapisuje", fontSize = 10.sp, color = Palette.Amber)
            } else {
                Text("Home Assistant: jeszcze niepodłączony (etap 3)", fontSize = 10.sp, color = Palette.Muted)
            }
        }
        Spacer(Modifier.height(10.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(13.dp),
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
        ) {
            itemsIndexed(shown, key = { _, app -> app.packageName }) { index, app ->
                val isMoving = app.packageName == moving
                AppCard(
                    app = app,
                    moving = isMoving,
                    modifier = Modifier
                        .size(AppTileWidth, AppTileHeight)
                        .then(if (index == 0) Modifier.focusRequester(firstApp) else Modifier)
                        .then(if (isMoving) Modifier.onPreviewKeyEvent(::moveKey) else Modifier),
                    onClick = {
                        when {
                            moving == null -> onLaunch(app)
                            SystemClock.uptimeMillis() - movingSince > 600 -> finishMove()
                        }
                    },
                    onLongClick = {
                        if (moving == null) {
                            order = shown
                            moving = app.packageName
                            movingSince = SystemClock.uptimeMillis()
                        }
                    },
                )
            }
            item(key = "all") {
                ActionCard(modifier = Modifier.size(AppTileWidth, AppTileHeight), onClick = onOpenAllApps) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Wszystkie", fontSize = 11.sp, color = Palette.Text)
                    }
                }
            }
        }
    }
}
