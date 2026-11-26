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
import pl.dom.launcher.data.TvApp
import pl.dom.launcher.service.Interaction

private val Tabs = listOf("Start", "Do obejrzenia", "Filmy", "Seriale", "Aplikacje")
private const val TAB_START = 0
private const val TAB_APPS = 4

@Composable
fun LauncherScreen(goHomeSignal: StateFlow<Int>) {
    val context = LocalContext.current
    val app = remember { LauncherApp.from(context) }
    val scope = rememberCoroutineScope()

    val settings by app.settings.flow.collectAsStateWithLifecycle(initialValue = null)
    val apps by app.apps.apps.collectAsStateWithLifecycle()
    val updateState by app.updater.state.collectAsStateWithLifecycle()

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

    fun toggleFavorite(target: TvApp) {
        val current = favorites.map { it.packageName }
        val updated = if (target.packageName in current) current - target.packageName else current + target.packageName
        scope.launch { app.settings.setFavorites(updated) }
    }

    Box(Modifier.fillMaxSize().background(Palette.Bg)) {
        HouseWallpaper(Modifier.fillMaxSize(), dim = if (idle) 0.55f else 1f)

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
                MainTabs(tabs = Tabs, selected = tab, onSelect = { tab = it })
                when (tab) {
                    TAB_START -> StartScreen(
                        favorites = favorites,
                        onLaunch = { app.apps.launch(it.packageName) },
                        onOpenAllApps = { tab = TAB_APPS },
                        updateState = updateState,
                    )
                    TAB_APPS -> AppsScreen(
                        apps = apps,
                        favoritePackages = favorites.map { it.packageName }.toSet(),
                        onLaunch = { app.apps.launch(it.packageName) },
                        onToggleFavorite = ::toggleFavorite,
                    )
                    else -> ComingSoon("Ta zakładka pojawi się w etapie 4 (Trakt, TMDB i Emby).")
                }
            }
        }

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
