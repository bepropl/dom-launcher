package pl.dom.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pl.dom.launcher.LauncherApp
import pl.dom.launcher.media.EmbyItem
import pl.dom.launcher.media.FollowedShow
import pl.dom.launcher.media.MediaItem
import pl.dom.launcher.media.MediaType
import pl.dom.launcher.media.WatchItem
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class MenuAction(val label: String, val action: () -> Unit)
data class MenuRequest(val title: String, val actions: List<MenuAction>)

/**
 * Co się dzieje po wybraniu karty: OK otwiera aplikację serwisu (albo Emby),
 * przytrzymanie OK pokazuje menu z opcjami listy.
 */
class MediaActions(
    private val app: LauncherApp,
    private val scope: CoroutineScope,
    private val toast: (String) -> Unit,
    private val menu: (MenuRequest) -> Unit,
) {
    private val media get() = app.media

    fun openProvider(provider: String?, title: String) {
        val target = provider?.let { app.apps.findByName(it) }
        when {
            target != null -> app.apps.launch(target.packageName)
            provider != null -> toast("Nie znalazłem aplikacji „$provider” na telewizorze")
            else -> toast("Nie wiem, gdzie jest „$title” – sprawdź serwisy w ustawieniach")
        }
    }

    fun openEmby() {
        val emby = app.apps.findByName("Emby")
        if (emby != null) app.apps.launch(emby.packageName) else toast("Nie znalazłem aplikacji Emby na telewizorze")
    }

    private fun run(done: String, block: suspend () -> Unit) {
        scope.launch {
            runCatching { block() }
                .onSuccess { toast(done) }
                .onFailure { toast(it.message ?: "Coś poszło nie tak") }
        }
    }

    fun menuForShow(show: FollowedShow) {
        val next = show.nextToWatch
        menu(
            MenuRequest(
                show.name,
                buildList {
                    add(MenuAction(show.provider?.let { "Oglądaj w $it" } ?: "Oglądaj") { openProvider(show.provider, show.name) })
                    if (next != null && show.hasNewEpisode) {
                        add(MenuAction("Oznacz ${next.code} jako obejrzany") {
                            run("Oznaczono ${next.code} jako obejrzany") { media.markNextWatched(show.tmdbId) }
                        })
                    }
                    show.lastAired?.let { last ->
                        if (show.hasNewEpisode) {
                            add(MenuAction("Jesteśmy na bieżąco (do ${last.code})") {
                                run("Zapisano") { media.setWatched(show.tmdbId, last.season, last.number) }
                            })
                        }
                    }
                    add(MenuAction("Przestań obserwować") { run("Usunięto „${show.name}” z obserwowanych") { media.unfollow(show.tmdbId) } })
                },
            ),
        )
    }

    fun menuForWatchItem(item: WatchItem) {
        menu(
            MenuRequest(
                item.title,
                buildList {
                    add(MenuAction(item.provider?.let { "Oglądaj w $it" } ?: "Oglądaj") { openProvider(item.provider, item.title) })
                    if (item.type == MediaType.TV) {
                        add(MenuAction("Obserwuj serial (od początku)") {
                            run("Obserwujecie „${item.title}”") { media.follow(item.tmdbId, caughtUp = false) }
                        })
                    }
                    add(MenuAction(if (item.type == MediaType.MOVIE) "Obejrzany – usuń z listy" else "Usuń z listy") {
                        run("Usunięto „${item.title}” z listy") { media.removeFromWatchlist(item.type, item.tmdbId) }
                    })
                },
            ),
        )
    }

    fun menuForItem(item: MediaItem) {
        val lib = media.library.state.value
        menu(
            MenuRequest(
                item.title,
                buildList {
                    add(MenuAction(item.provider?.let { "Oglądaj w $it" } ?: "Oglądaj") { openProvider(item.provider, item.title) })
                    if (lib.isOnWatchlist(item.type, item.tmdbId)) {
                        add(MenuAction("Usuń z „Do obejrzenia”") { run("Usunięto z listy") { media.removeFromWatchlist(item.type, item.tmdbId) } })
                    } else {
                        add(MenuAction("Dodaj do „Do obejrzenia”") { run("Dodano „${item.title}” do listy") { media.addToWatchlist(item.type, item.tmdbId) } })
                    }
                    if (item.type == MediaType.TV) {
                        if (lib.isFollowed(item.tmdbId)) {
                            add(MenuAction("Przestań obserwować") { run("Usunięto z obserwowanych") { media.unfollow(item.tmdbId) } })
                        } else {
                            add(MenuAction("Obserwuj – jesteśmy na bieżąco") { run("Obserwujecie „${item.title}”") { media.follow(item.tmdbId, caughtUp = true) } })
                            add(MenuAction("Obserwuj – oglądamy od początku") { run("Obserwujecie „${item.title}”") { media.follow(item.tmdbId, caughtUp = false) } })
                        }
                    }
                },
            ),
        )
    }

    fun menuForEmby(item: EmbyItem) {
        menu(MenuRequest(item.title, listOf(MenuAction("Otwórz Emby") { openEmby() })))
    }
}

/** Okienko z opcjami (po przytrzymaniu OK na karcie). */
@Composable
fun ActionMenu(request: MenuRequest, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(request) {
        delay(50)
        runCatching { first.requestFocus() }
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .width(280.dp)
                .background(Palette.Card, RoundedCornerShape(12.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(request.title, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Palette.Muted, modifier = Modifier.padding(bottom = 4.dp))
            request.actions.forEachIndexed { index, action ->
                Button(
                    onClick = {
                        onDismiss()
                        action.action()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (index == 0) Modifier.focusRequester(first) else Modifier),
                ) { Text(action.label, fontSize = 11.sp) }
            }
        }
    }
}

/** „dziś”, „wczoraj”, „za 3 dni”, „26 listopada” – dla dat RRRR-MM-DD z TMDB. */
fun friendlyDate(iso: String?): String? {
    val date = iso?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
    val days = ChronoUnit.DAYS.between(LocalDate.now(), date)
    return when {
        days == 0L -> "dziś"
        days == 1L -> "jutro"
        days == -1L -> "wczoraj"
        days in 2..6 -> "za $days dni"
        else -> date.format(java.time.format.DateTimeFormatter.ofPattern("d MMMM", java.util.Locale.forLanguageTag("pl-PL")))
    }
}
