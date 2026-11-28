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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import pl.dom.launcher.update.UpdateState
import kotlin.random.Random
import pl.dom.launcher.ha.HaSummary
import pl.dom.launcher.TrailerActivity
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.key
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import android.os.SystemClock
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
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
import pl.dom.launcher.ha.summarize
import pl.dom.launcher.data.TvApp
import pl.dom.launcher.service.Interaction

private val Tabs = listOf("Start", "Do obejrzenia", "Filmy", "Seriale")
private const val TAB_START = 0
private const val TAB_WATCHLIST = 1
private const val TAB_MOVIES = 2
private const val TAB_SHOWS = 3

@Composable
fun LauncherScreen(goHomeSignal: StateFlow<Int>) {
    val context = LocalContext.current
    val app = remember { LauncherApp.from(context) }
    val scope = rememberCoroutineScope()

    val settings by app.settings.flow.collectAsStateWithLifecycle(initialValue = null)
    val haStatus by app.ha.status.collectAsStateWithLifecycle()
    val haRecent by app.ha.recent.collectAsStateWithLifecycle()
    val haSummary by app.ha.summary.collectAsStateWithLifecycle()
    var seenNotifications by remember { mutableStateOf(0L) }
    var showNotifications by remember { mutableStateOf(false) }
    val apps by app.apps.apps.collectAsStateWithLifecycle()
    val updateState by app.updater.state.collectAsStateWithLifecycle()
    val library by app.media.library.state.collectAsStateWithLifecycle()
    val feeds by app.media.feeds.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableIntStateOf(TAB_START) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    // Wszystkie aplikacje: osobny ekran otwierany kafelkiem „Wszystkie” na Starcie.
    var showAllApps by rememberSaveable { mutableStateOf(false) }
    // Panel zębatki (z prawej, jak w Google TV).
    var showPanel by remember { mutableStateOf(false) }
    val gearFocus = remember { FocusRequester() }
    val bellFocus = remember { FocusRequester() }
    val allAppsTile = remember { FocusRequester() }
    var returnToAllTile by remember { mutableStateOf(false) }

    // Przycisk Home zawsze wraca na Start.
    // Fokus na pierwszą aplikację tylko po starcie, po Home i po Wstecz – nie przy każdym
    // przejściu przez zakładkę Start w menu (to „szarpało” menu).
    var focusStartApps by remember { mutableStateOf(true) }
    // Wyszukiwanie w Filmach/Serialach czyści się po zmianie zakładki i po Home.
    var searchReset by rememberSaveable { mutableStateOf(0) }
    val goHome by goHomeSignal.collectAsStateWithLifecycle()
    LaunchedEffect(goHome) {
        tab = TAB_START
        showSettings = false
        showAllApps = false
        showPanel = false
        focusStartApps = true
        searchReset++
    }
    LaunchedEffect(tab) { searchReset++ }
    LaunchedEffect(showAllApps, returnToAllTile) {
        if (!showAllApps && returnToAllTile) {
            delay(120)
            runCatching { allAppsTile.requestFocus() }
            returnToAllTile = false
        }
    }

    // Wstecz: z ustawień i innych zakładek wraca na Start; na Starcie nic nie robi (to ekran główny).
    BackHandler(enabled = true) {
        when {
            showSettings -> showSettings = false
            showAllApps -> {
                showAllApps = false
                returnToAllTile = true
            }
            tab != TAB_START -> {
                tab = TAB_START
                focusStartApps = true
            }
        }
    }

    // --- Ochrona OLED ---
    // Uwaga: czas ostatniego naciśnięcia pilota czytamy w efekcie, a nie w samym ekranie –
    // inaczej każde naciśnięcie przebudowywałoby cały launcher (stąd brały się przycięcia menu).
    val idle by Interaction.idle.collectAsStateWithLifecycle()
    val idleMinutes = settings?.idleMinutes ?: 5
    LaunchedEffect(idleMinutes) {
        Interaction.lastAt.collectLatest {
            Interaction.idle.value = false
            if (idleMinutes > 0) {
                delay(idleMinutes * 60_000L)
                Interaction.idle.value = true
            }
        }
    }
    // Etapy wygaszacza: po 2 h przygaszenie, po N h (albo gdy nikogo nie ma w domu) czarny ekran.
    var deepIdle by remember { mutableStateOf(false) }
    var blankByTime by remember { mutableStateOf(false) }
    val blankHours = settings?.blankHours ?: 3
    LaunchedEffect(idle, blankHours) {
        deepIdle = false
        blankByTime = false
        if (!idle) return@LaunchedEffect
        delay(2 * 3600_000L)
        deepIdle = true
        if (blankHours > 0) {
            delay((blankHours * 3600_000L - 2 * 3600_000L).coerceAtLeast(0))
            blankByTime = true
        }
    }
    val nobodyHome = haSummary?.peopleHome == 0 && (haSummary?.peopleTotal ?: 0) > 0
    val blank = idle && (blankByTime || (settings?.blankWhenAway != false && nobodyHome))
    // Domek w wygaszaczu co minutę przesuwa się odrobinę (ochrona przed wypaleniem).
    val drift by produceState(Offset.Zero, idle) {
        value = Offset.Zero
        while (idle) {
            delay(60_000)
            value = Offset(Random.nextFloat() * 0.2f - 0.1f, Random.nextFloat() * 0.14f - 0.07f)
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
    val tabsState = rememberSaveableStateHolder()

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
    // Podgląd tytułu (jak w Netflixie). Stos: z „Podobne” można wchodzić głębiej, Wstecz wraca.
    var detailStack by remember { mutableStateOf(listOf<DetailRequest>()) }
    var returnFocus by remember { mutableStateOf<FocusRequester?>(null) }
    val actions = remember {
        MediaActions(app, scope, toast = { toast = it }, menu = { menu = it }, openDetail = { request ->
            if (detailStack.isEmpty()) returnFocus = FocusMemory.last
            detailStack = detailStack + request
        })
    }
    // Home zamyka też podgląd tytułu.
    LaunchedEffect(goHome) {
        detailStack = emptyList()
        returnFocus = null
    }
    LaunchedEffect(detailStack.isEmpty()) {
        if (detailStack.isEmpty()) {
            val target = returnFocus ?: return@LaunchedEffect
            delay(80)
            runCatching { target.requestFocus() }
            returnFocus = null
        }
    }

    // Kafelek „Wszystkie” przenosi fokus na zakładkę Aplikacje. Gdyby fokus po prostu zniknął
    // razem z kafelkiem, trafiłby na pierwszą zakładkę (Start) i ekran od razu by wrócił.
    val tabFocus = remember { List(Tabs.size) { FocusRequester() } }
    var focusAppsGrid by remember { mutableStateOf(false) }

    // Bez tła: pod spodem rysuje się domek (OpenGL), który sam czyści ekran na czarno.
    // Zabezpieczenie: gdy kursor gdzieś zniknie (nic nie jest zaznaczone), po chwili wraca na bieżącą zakładkę.
    var anyFocus by remember { mutableStateOf(true) }
    LaunchedEffect(anyFocus, menu, showNotifications, showPanel) {
        if (anyFocus || menu != null || showNotifications || showPanel) return@LaunchedEffect
        delay(500)
        if (!anyFocus) {
            // Otwarty podgląd tytułu: kursor wraca na „Oglądaj”, a nie na schowane menu.
            val detail = DetailFocus.main
            if (detailStack.isNotEmpty() && detail != null && runCatching { detail.requestFocus() }.isSuccess) return@LaunchedEffect
            val ok = runCatching { tabFocus[tab].requestFocus() }.isSuccess
            if (!ok) runCatching { gearFocus.requestFocus() }
        }
    }

    Box(Modifier.fillMaxSize().onFocusChanged { anyFocus = it.hasFocus }) {
        // Domek nie rysuje się pod podglądem tytułu ani na czarnym ekranie (oszczędność i zwiastuny).
        if (detailStack.isEmpty() && !blank) {
            HouseWallpaper(
                Modifier.fillMaxSize(),
                dim = when {
                    deepIdle -> 0.5f
                    idle -> 0.75f
                    else -> 1f
                },
                brightnessLevel = settings?.houseBrightness ?: 3,
                mode = settings?.houseMode ?: HouseMode.FULL,
                showHouse = idle || (tab == TAB_START && !showSettings && !showAllApps),
                layout = if (idle) Triple(drift.x, 0.14f + drift.y, 2.1f) else Triple(0.5f, 0.12f, 2.0f),
            )
        }

        Column(
            Modifier
                .fillMaxSize()
                .offset { pixelShift }
                .alpha(uiAlpha)
                .padding(vertical = 22.dp),
        ) {
            TopBar(
                modifier = Modifier.padding(horizontal = EdgePadding),
                onOpenSettings = { showPanel = true },
                settingsFocus = gearFocus,
                notificationsFocus = bellFocus,
                ha = haSummary,
                haStatus = haStatus,
                unread = haRecent.count { it.at > seenNotifications },
                onOpenNotifications = {
                    showNotifications = true
                    seenNotifications = System.currentTimeMillis()
                },
            )
            Spacer(Modifier.height(14.dp))

            if (showSettings) {
                Box(Modifier.padding(horizontal = EdgePadding)) { SettingsScreen(settings = settings) }
            } else if (showAllApps) {
                AppsScreen(
                    apps = apps,
                    favoritePackages = favorites.map { it.packageName }.toSet(),
                    onLaunch = { app.apps.launch(it.packageName) },
                    onToggleFavorite = ::toggleFavorite,
                    focusFirstApp = focusAppsGrid,
                    onFirstAppFocused = { focusAppsGrid = false },
                )
            } else {
                Box(Modifier.padding(horizontal = EdgePadding)) {
                    MainTabs(tabs = Tabs, selected = tab, onSelect = { tab = it }, focusRequesters = tabFocus)
                }
                tabsState.SaveableStateProvider(tab) { when (tab) {
                    TAB_START -> StartScreen(
                        favorites = favorites,
                        autoFocus = focusStartApps,
                        onAutoFocused = { focusStartApps = false },
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
                            showAllApps = true
                        },
                        allAppsFocus = allAppsTile,
                        updateState = updateState,
                    )
                    TAB_WATCHLIST -> WatchlistScreen(library, feeds, actions)
                    TAB_MOVIES -> MoviesScreen(feeds, actions, searchReset)
                    TAB_SHOWS -> ShowsScreen(library, feeds, actions, searchReset)
                } }
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

        detailStack.lastOrNull()?.let { request ->
            key(request) {
                DetailScreen(
                    request = request,
                    app = app,
                    library = library,
                    actions = actions,
                    onOpen = { next -> detailStack = detailStack + next },
                    onClose = { detailStack = detailStack.dropLast(1) },
                    onTrailer = { key, title -> TrailerActivity.open(context, key, title) },
                )
            }
        }

        // „Obejrzeliście…?” po powrocie z serialu otwartego z Dom.
        val askWatched by WatchPrompt.ask.collectAsStateWithLifecycle()
        val lifecycleState by androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
        LaunchedEffect(lifecycleState) {
            if (lifecycleState == androidx.lifecycle.Lifecycle.State.RESUMED) WatchPrompt.onLauncherResumed()
        }
        askWatched?.let { p ->
            if (!idle) {
                WatchPromptCard(
                    p,
                    onYes = { actions.answerWatchPrompt(p, true) },
                    onNo = { actions.answerWatchPrompt(p, false) },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 120.dp),
                )
            }
        }

        menu?.let { request -> ActionMenu(request, onDismiss = { menu = null }) }
        SidePanel(
            visible = showNotifications,
            title = "Powiadomienia",
            returnFocus = bellFocus,
            onClose = { showNotifications = false },
        ) { first, _ ->
            NotificationsPanelContent(haRecent, first, showNotifications, onClear = { app.ha.clearRecent() })
        }

        SidePanel(
            visible = showPanel,
            title = "Ustawienia",
            returnFocus = gearFocus,
            onClose = { showPanel = false },
        ) { first, _ ->
            val items = settingsPanelItems(
                onLauncher = {
                    showPanel = false
                    showSettings = true
                },
                onAndroid = {
                    showPanel = false
                    runCatching {
                        context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                },
                update = updateState,
                onUpdate = {
                    val available = updateState as? UpdateState.Available
                    when {
                        available == null -> scope.launch { app.updater.check() }
                        app.updater.canInstall() -> scope.launch { app.updater.downloadAndInstall(available.info) }
                        else -> app.updater.openInstallPermissionSettings()
                    }
                },
            )
            items.forEachIndexed { index, item ->
                PanelRow(
                    item.title, item.subtitle, item.icon, item.onClick,
                    modifier = if (index == 0) Modifier.focusRequester(first) else Modifier,
                    visible = showPanel,
                )
            }
        }

        NotificationPopup(
            app.ha.notifications,
            Modifier.align(Alignment.TopEnd).padding(top = 70.dp, end = 40.dp),
        )

        if (idle && !blank) IdleScreen(haSummary, deepIdle)
    }

}

@Composable
fun ComingSoon(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, fontSize = 14.sp, color = Palette.Muted)
    }
}

/**
 * Wygaszacz: domek na środku (rysuje go tło), pod nim godzina, temperatury i osoby w domu.
 * Tekst jest przygaszony i co minutę zmienia miejsce, więc nic nie stoi długo w jednym punkcie (OLED).
 */
@Composable
private fun IdleScreen(ha: HaSummary?, deep: Boolean) {
    val now by rememberNow()
    val shift by produceState(IntOffset.Zero) {
        while (true) {
            value = IntOffset((-60..60).random(), (-24..24).random())
            delay(60_000)
        }
    }
    val alpha = if (deep) 0.35f else 0.6f
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier.padding(bottom = 70.dp).offset { shift },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(now.format(TimeFormat), fontSize = 34.sp, fontWeight = FontWeight.Light, color = Palette.Soft.copy(alpha = alpha))
            val parts = buildList {
                ha?.outside?.let { add(listOfNotNull(ha.weatherIcon, it).joinToString(" ") + " na zewnątrz") }
                ha?.inside?.let { add("$it w domu") }
                ha?.peopleHome?.let { add(if (it == 0) "nikogo w domu" else "w domu: " + ha.namesHome.joinToString(", ").ifBlank { "$it" }) }
            }
            if (parts.isNotEmpty()) {
                Text(parts.joinToString("   ·   "), fontSize = 12.sp, color = Palette.Muted.copy(alpha = alpha))
            }
        }
    }
}
