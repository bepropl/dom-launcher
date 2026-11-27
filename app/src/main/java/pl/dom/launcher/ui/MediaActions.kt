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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import android.os.SystemClock
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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
import pl.dom.launcher.media.Episode
import pl.dom.launcher.media.MediaDetails
import pl.dom.launcher.media.SeasonEpisode
import pl.dom.launcher.media.MediaItem
import pl.dom.launcher.media.MediaType
import pl.dom.launcher.media.WatchItem
import pl.dom.launcher.media.ToWatch
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
    private val openDetail: (DetailRequest) -> Unit = {},
) {
    private val media get() = app.media
    @Volatile private var menuOpenedAt = 0L

    /** Po przytrzymaniu OK karta nie może się jeszcze „kliknąć” przy puszczeniu przycisku. */
    private fun justOpenedMenu() = SystemClock.uptimeMillis() - menuOpenedAt < 1200

    private fun showMenu(request: MenuRequest) {
        menuOpenedAt = SystemClock.uptimeMillis()
        menu(request)
    }

    /** OK na karcie. */
    fun openProvider(provider: String?, title: String) {
        if (!justOpenedMenu()) launchProvider(provider, title)
    }

    /** OK na karcie z Emby. */
    fun openEmby() {
        if (!justOpenedMenu()) launchEmby()
    }

    /** OK na plakacie – podgląd tytułu na cały ekran. */
    fun detail(type: MediaType, tmdbId: Int) {
        if (!justOpenedMenu()) openDetail(DetailRequest(type, tmdbId))
    }

    /** Przycisk „Oglądaj” w podglądzie: aplikacja serwisu, jeśli się da – od razu z wyszukanym tytułem. */
    fun watch(provider: String?, title: String) {
        val target = provider?.let { app.apps.findByName(it) }
        if (target != null && app.apps.launchSearch(target.packageName, title)) return
        launchProvider(provider, title)
    }

    fun follow(tmdbId: Int, title: String, caughtUp: Boolean) =
        run("Obserwujecie „$title”") { media.follow(tmdbId, caughtUp = caughtUp) }

    fun unfollow(tmdbId: Int, title: String) =
        run("Usunięto „$title” z obserwowanych") { media.unfollow(tmdbId) }

    fun toggleWatchlist(type: MediaType, tmdbId: Int, title: String, onList: Boolean) =
        if (onList) run("Usunięto „$title” z listy") { media.removeFromWatchlist(type, tmdbId) }
        else run("Dodano „$title” do listy") { media.addToWatchlist(type, tmdbId) }

    /** OK na odcinku w podglądzie serialu. */
    fun menuForEpisode(d: MediaDetails, ep: SeasonEpisode) {
        val code = Episode(ep.season, ep.number).code
        val followed = media.library.state.value.isFollowed(d.item.tmdbId)
        showMenu(
            MenuRequest(
                "${d.item.title} · $code ${ep.name}",
                buildList {
                    add(MenuAction("Obejrzane do $code (włącznie)") {
                        run("Zapisano: obejrzane do $code") {
                            if (!followed) media.follow(d.item.tmdbId, caughtUp = false)
                            media.setWatched(d.item.tmdbId, ep.season, ep.number)
                        }
                    })
                    if (followed) {
                        val prev = if (ep.number > 1) Episode(ep.season, ep.number - 1) else null
                        add(MenuAction(prev?.let { "Obejrzane do ${it.code} (bez tego odcinka)" } ?: "Ten sezon jeszcze nieobejrzany") {
                            run("Zapisano") {
                                if (prev != null) media.setWatched(d.item.tmdbId, prev.season, prev.number)
                                else media.setWatched(d.item.tmdbId, ep.season - 1, if (ep.season > 1) d.seasons[ep.season - 1] ?: 0 else 0)
                            }
                        })
                    }
                    add(MenuAction(d.item.provider?.let { "Oglądaj w $it" } ?: "Oglądaj") { watch(d.item.provider, d.item.title) })
                },
            ),
        )
    }

    private fun launchProvider(provider: String?, title: String) {
        val target = provider?.let { app.apps.findByName(it) }
        when {
            target != null -> app.apps.launch(target.packageName)
            provider != null -> toast("Nie znalazłem aplikacji „$provider” na telewizorze")
            else -> toast("Nie wiem, gdzie jest „$title” – sprawdź serwisy w ustawieniach")
        }
    }

    private fun launchEmby() {
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
        showMenu(
            MenuRequest(
                show.name,
                buildList {
                    add(MenuAction(show.provider?.let { "Oglądaj w $it" } ?: "Oglądaj") { launchProvider(show.provider, show.name) })
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

    /** Przytrzymanie OK na pozycji z listy „Do obejrzenia”. */
    fun menuForToWatch(entry: ToWatch) {
        showMenu(
            MenuRequest(
                entry.title,
                buildList {
                    if (entry.ready) add(MenuAction("Oglądaj w ${entry.provider}") { launchProvider(entry.provider, entry.title) })
                    add(MenuAction("Szczegóły i zwiastun") { openDetail(DetailRequest(entry.type, entry.tmdbId)) })
                    if (entry.type == MediaType.TV) {
                        add(MenuAction("Zaczęliśmy – obejrzane S01E01") {
                            run("„${entry.title}” przechodzi do „Dalej”") {
                                if (!entry.followed) media.follow(entry.tmdbId, caughtUp = false)
                                media.setWatched(entry.tmdbId, 1, 1)
                                media.removeFromWatchlist(MediaType.TV, entry.tmdbId)
                            }
                        })
                    } else {
                        add(MenuAction("Obejrzany – usuń z listy") {
                            run("Usunięto „${entry.title}” z listy") { media.removeFromWatchlist(entry.type, entry.tmdbId) }
                        })
                    }
                    add(MenuAction(if (entry.followed) "Usuń z listy (przestań obserwować)" else "Usuń z listy") {
                        run("Usunięto „${entry.title}” z listy") {
                            if (entry.followed) media.unfollow(entry.tmdbId)
                            media.removeFromWatchlist(entry.type, entry.tmdbId)
                        }
                    })
                },
            ),
        )
    }

    fun menuForWatchItem(item: WatchItem) {
        showMenu(
            MenuRequest(
                item.title,
                buildList {
                    add(MenuAction(item.provider?.let { "Oglądaj w $it" } ?: "Oglądaj") { launchProvider(item.provider, item.title) })
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
        showMenu(
            MenuRequest(
                item.title,
                buildList {
                    add(MenuAction(item.provider?.let { "Oglądaj w $it" } ?: "Oglądaj") { launchProvider(item.provider, item.title) })
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
        showMenu(MenuRequest(item.title, listOf(MenuAction("Otwórz Emby") { launchEmby() })))
    }
}

/** Okienko z opcjami (po przytrzymaniu OK na karcie). */
@Composable
fun ActionMenu(request: MenuRequest, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    // Menu otwiera się, gdy OK jest jeszcze wciśnięte. Puszczenie tego OK nie może
    // „kliknąć” pierwszej opcji – przyjmujemy wybór dopiero po nowym naciśnięciu OK.
    var armed by remember(request) { mutableStateOf(false) }
    val openedAt = remember(request) { SystemClock.uptimeMillis() }
    LaunchedEffect(request) {
        delay(50)
        runCatching { first.requestFocus() }
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .onPreviewKeyEvent { event ->
                    val ok = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
                    if (!ok) return@onPreviewKeyEvent false
                    if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0 && SystemClock.uptimeMillis() - openedAt > 150) {
                        armed = true
                    }
                    !armed
                }
                .width(280.dp)
                .background(Palette.Card, RoundedCornerShape(12.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(request.title, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Palette.Muted, modifier = Modifier.padding(bottom = 4.dp))
            request.actions.forEachIndexed { index, action ->
                Button(
                    onClick = {
                        if (armed) {
                            onDismiss()
                            action.action()
                        }
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
