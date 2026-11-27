package pl.dom.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import android.os.SystemClock
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import pl.dom.launcher.LauncherApp
import pl.dom.launcher.data.AppsRepository
import pl.dom.launcher.data.HouseMode
import pl.dom.launcher.data.TvApp
import pl.dom.launcher.service.Interaction

private val Tabs = listOf("Start", "Do obejrzenia", "Filmy", "Seriale", "Aplikacje")
private const val TAB_START = 0
private const val TAB_WATCHLIST = 1
private const val TAB_MOVIES = 2
private const val TAB_SHOWS = 3
private const val TAB_APPS = 4

@Composable
fun LauncherScreen(goHomeSignal: StateFlow<Int>) {
    val context = LocalContext.current
    val app = remember { LauncherApp.from(context) }
    val scope = rememberCoroutineScope()

    val settings by app.settings.flow.collectAsStateWithLifecycle(initialValue = null)
    val apps by app.apps.apps.collectAsStateWithLifecycle()
    val updateState by app.updater.state.collectAsStateWithLifecycle()
    val library by app.media.library.state.collectAsStateWithLifecycle()
    val feeds by app.media.feeds.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableIntStateOf(TAB_START) }
    var showSettings by rememberSaveable { mutableStateOf(false) }

    // Przycisk Home zawsze wraca na Start.
    val goHome by goHomeSignal.collectAsStateWithLifecycle()
    LaunchedEffect(goHome) {
        tab = TAB_START
        showSettings = false
    }

    // Wstecz: z ustawień i innych zakładek wraca na Start; na Starcie nic nie robi (to ekran główny).
    BackHandler(enabled = true) {
        when {
            showSettings -> showSettings = false
            tab != TAB_START -> tab = TAB_START
        }
    }

    // --- Ochrona OLED ---
    val lastAt by Interaction.lastAt.collectAsStateWithLifecycle()
    val idle by Interaction.idle.collectAsStateWithLifecycle()
    val idleMinutes = settings?.idleMinutes ?: 5
    LaunchedEffect(lastAt, idleMinutes) {
        Interaction.idle.value = false
        if (idleMinutes > 0) {
            delay(idleMinutes * 60_000L)
            Interaction.idle.value = true
        }
    }
    // Co kilka minut cały interfejs przesuwa się o kilka pikseli, żeby nic nie stało w miejscu.
    val pixelShift by produceState(IntOffset.Zero) {
        while (true) {
            delay(180_000)
            value = IntOffset((-6..6).random(), (-4..4).random())
        }
    }
    val uiAlpha by animateFloatAsState(if (idle) 0f else 1f, tween(1500), label = "uiAlpha")

    val favoritePackages = settings?.favorites.orEmpty()
    val favorites: List<TvApp> = remember(favoritePackages, apps) {
        AppsRepository.resolveFavorites(favoritePackages, apps)
    }

    // Krótki komunikat na dole ekranu (np. „Dodano Netflix do ekranu Start”).
    var toast by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(2500)
            toast = null
        }
    }

    // Przytrzymanie OK potrafi wywołać akcję więcej niż raz, więc ta sama aplikacja
    // może zmienić stan najwyżej raz na sekundę. Liczymy zawsze od tego, co jest zapisane.
    val lastToggle = remember { mutableMapOf<String, Long>() }
    fun toggleFavorite(target: TvApp) {
        val now = SystemClock.uptimeMillis()
        if (now - (lastToggle[target.packageName] ?: 0L) < 1000) return
        lastToggle[target.packageName] = now
        scope.launch {
            val installed = app.apps.apps.value
            val stored = app.settings.current().favorites
            val base = if (stored.isEmpty()) {
                AppsRepository.resolveFavorites(emptyList(), installed).map { it.packageName }
            } else {
                stored.filter { pkg -> installed.any { it.packageName == pkg } }
            }
            val adding = target.packageName !in base
            app.settings.setFavorites(if (adding) base + target.packageName else base - target.packageName)
            toast = if (adding) "Dodano „${target.label}” do ekranu Start" else "Usunięto „${target.label}” z ekranu Start"
        }
    }

    var menu by remember { mutableStateOf<MenuRequest?>(null) }
    val actions = remember { MediaActions(app, scope, toast = { toast = it }, menu = { menu = it }) }

    // Kafelek „Wszystkie” przenosi fokus na zakładkę Aplikacje. Gdyby fokus po prostu zniknął
    // razem z kafelkiem, trafiłby na pierwszą zakładkę (Start) i ekran od razu by wrócił.
    val tabFocus = remember { List(Tabs.size) { FocusRequester() } }
    var focusAppsGrid by remember { mutableStateOf(false) }

    // Bez tła: pod spodem rysuje się domek (OpenGL), który sam czyści ekran na czarno.
    Box(Modifier.fillMaxSize()) {
        HouseWallpaper(
            Modifier.fillMaxSize(),
            dim = if (idle) 0.55f else 1f,
            mode = settings?.houseMode ?: HouseMode.FULL,
        )

        Column(
            Modifier
                .fillMaxSize()
                .offset { pixelShift }
                .alpha(uiAlpha)
                .padding(horizontal = 40.dp, vertical = 22.dp),
        ) {
            TopBar(onOpenSettings = { showSettings = true })
            Spacer(Modifier.height(14.dp))

            if (showSettings) {
                SettingsScreen(settings = settings)
            } else {
                MainTabs(tabs = Tabs, selected = tab, onSelect = { tab = it }, focusRequesters = tabFocus)
                when (tab) {
                    TAB_START -> StartScreen(
                        favorites = favorites,
                        onLaunch = { app.apps.launch(it.packageName) },
                        onReorder = { packages ->
                            scope.launch {
                                app.settings.setFavorites(packages)
                                toast = "Zapisano kolejność aplikacji"
                            }
                        },
                        library = library,
                        feeds = feeds,
                        actions = actions,
                        onOpenAllApps = {
                            focusAppsGrid = true
                            if (runCatching { tabFocus[TAB_APPS].requestFocus() }.isFailure) tab = TAB_APPS
                        },
                        updateState = updateState,
                    )
                    TAB_APPS -> AppsScreen(
                        apps = apps,
                        favoritePackages = favorites.map { it.packageName }.toSet(),
                        onLaunch = { app.apps.launch(it.packageName) },
                        onToggleFavorite = ::toggleFavorite,
                        focusFirstApp = focusAppsGrid,
                        onFirstAppFocused = { focusAppsGrid = false },
                    )
                    TAB_WATCHLIST -> WatchlistScreen(library, feeds, actions)
                    TAB_MOVIES -> MoviesScreen(feeds, actions)
                    TAB_SHOWS -> ShowsScreen(library, feeds, actions)
                }
            }
        }

        toast?.let { message ->
            Box(Modifier.fillMaxSize().padding(bottom = 30.dp), contentAlignment = Alignment.BottomCenter) {
                Text(
                    message,
                    modifier = Modifier
                        .background(Color(0xEE14100C), RoundedCornerShape(10.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    fontSize = 12.sp,
                    color = Palette.Text,
                )
            }
        }

        menu?.let { request -> ActionMenu(request, onDismiss = { menu = null }) }

        if (idle) IdleClock()
    }
}

@Composable
fun ComingSoon(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, fontSize = 14.sp, color = Palette.Muted)
    }
}

/** W trybie spoczynku zostaje tylko mały zegar, który co minutę lekko się przesuwa. */
@Composable
private fun IdleClock() {
    val now by rememberNow()
    val shift by produceState(IntOffset.Zero) {
        while (true) {
            value = IntOffset((-20..20).random(), (-12..12).random())
            delay(60_000)
        }
    }
    Box(Modifier.fillMaxSize()) {
        Text(
            text = now.format(TimeFormat),
            modifier = Modifier.padding(start = 60.dp, top = 45.dp).offset { shift },
            fontSize = 28.sp,
            color = Palette.Muted.copy(alpha = 0.7f),
        )
    }
}
