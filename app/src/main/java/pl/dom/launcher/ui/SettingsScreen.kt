package pl.dom.launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import android.content.Intent
import android.provider.Settings
import androidx.lifecycle.compose.LocalLifecycleOwner
import pl.dom.launcher.service.HomeOverrideService
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.dom.launcher.LauncherApp
import pl.dom.launcher.config.ConfigServer
import pl.dom.launcher.config.Net
import pl.dom.launcher.config.qrBitmap
import pl.dom.launcher.data.HouseMode
import pl.dom.launcher.data.LauncherSettings
import pl.dom.launcher.update.UpdateState

@Composable
fun SettingsScreen(settings: LauncherSettings?) {
    val context = LocalContext.current
    val app = remember { LauncherApp.from(context) }
    val scope = rememberCoroutineScope()

    // --- Strona konfiguracji dla telefonu: działa tylko, gdy ten ekran jest otwarty. ---
    val pin = remember { (100000..999999).random().toString() }
    val ip = remember { Net.localIpv4(context) }
    val serverRunning by produceState<Boolean?>(null, pin) {
        val server = ConfigServer(context.applicationContext, pin)
        value = withContext(Dispatchers.IO) {
            runCatching { server.start(ConfigServer.SOCKET_TIMEOUT, true) }.isSuccess
        }
        awaitDispose { server.stop() }
    }
    val url = ip?.let { "http://$it:${ConfigServer.PORT}/#pin=$pin" }
    val qr = remember(url) { url?.let { qrBitmap(it, 480) } }

    // Góra ustawień (kod QR i PIN) nie ma nic do zaznaczenia, więc sama by się nie przewinęła.
    // Gdy kursor wychodzi z ustawień w górę (albo nie ma już dokąd), wracamy na samą górę.
    val scroll = rememberScrollState()
    val scrollScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    Column(
        Modifier
            .fillMaxSize()
            .onFocusChanged { if (!it.hasFocus) scrollScope.launch { scroll.animateScrollTo(0) } }
            .onPreviewKeyEvent { event ->
                if (event.key == Key.DirectionUp && event.type == KeyEventType.KeyDown) {
                    if (!focusManager.moveFocus(FocusDirection.Up)) scrollScope.launch { scroll.animateScrollTo(0) }
                    true
                } else {
                    false
                }
            }
            .verticalScroll(scroll)
            .padding(top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Ustawienia", fontSize = 20.sp, fontWeight = FontWeight.Medium, color = Palette.Text)

        Section {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(150.dp).background(Color.White, RoundedCornerShape(8.dp)).padding(6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (qr != null) Image(qr, contentDescription = "Kod QR strony konfiguracji", modifier = Modifier.fillMaxSize())
                    else Text("Brak sieci", color = Color.Black, fontSize = 11.sp)
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Konfiguracja z telefonu", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Palette.Text)
                    Text(
                        "Zeskanuj kod telefonem podłączonym do tej samej sieci. Wpiszesz tam klucze Home Assistanta, Emby i TMDB, wybierzesz serwisy, obserwowane seriale i ułożysz aplikacje.",
                        fontSize = 10.sp, color = Palette.Soft,
                    )
                    Text(url ?: "Telewizor nie ma połączenia z siecią.", fontSize = 11.sp, color = Palette.Amber)
                    Text("PIN: ${pin.chunked(3).joinToString(" ")}", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = Palette.Text)
                    Text(
                        when (serverRunning) {
                            null -> "Uruchamiam stronę…"
                            true -> "Strona działa, dopóki ten ekran jest otwarty."
                            false -> "Nie udało się uruchomić strony (port ${ConfigServer.PORT} zajęty?)."
                        },
                        fontSize = 9.sp, color = Palette.Muted,
                    )
                }
            }
        }

        Section {
            Text("Dom jako ekran główny", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Palette.Text)
            val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
            val overrideOn = remember(lifecycleState) { HomeOverrideService.isEnabled(context) }
            Text(
                if (overrideOn) "Włączone ✓ Przycisk Home i włączenie telewizora otwierają Dom."
                else "Włącz usługę „Dom – ekran główny” w Ułatwieniach dostępu. Wtedy Home i włączenie telewizora zawsze otworzą Dom (jak „Override” w Projectivy).",
                fontSize = 10.sp, color = if (overrideOn) Palette.Ok else Palette.Soft,
            )
            Button(onClick = {
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }) { Text("Otwórz Ułatwienia dostępu", fontSize = 11.sp) }
            Text(
                "Jeśli Play Protect zablokuje instalację, wyłącz w Sklepie Play: profil → Play Protect → zębatka → „Skanuj aplikacje”. Przy włączonej usłudze usuń w Button Mapperze przypisanie Home.",
                fontSize = 9.sp, color = Palette.Muted,
            )
        }

        Section {
            Text("Domek w tle", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Palette.Text)
            val mode = settings?.houseMode ?: HouseMode.FULL
            Text(
                when (mode) {
                    HouseMode.FULL -> "Pełna jakość: wszystkie punkty, 30 klatek na sekundę."
                    HouseMode.ECO -> "Oszczędna: połowa punktów, 20 klatek na sekundę."
                    HouseMode.OFF -> "Wyłączony: czarne tło, zero obciążenia."
                },
                fontSize = 10.sp, color = Palette.Soft,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(HouseMode.FULL to "Pełna", HouseMode.ECO to "Oszczędna", HouseMode.OFF to "Wyłączony").forEach { (m, label) ->
                    Button(onClick = { scope.launch { app.settings.setHouseMode(m) } }) {
                        Text(if (m == mode) "✓ $label" else label, fontSize = 11.sp)
                    }
                }
            }
        }

        Section {
            Text("Ochrona ekranu OLED", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Palette.Text)
            val minutes = settings?.idleMinutes ?: 5
            Text(
                if (minutes == 0) "Tryb spoczynku wyłączony." else "Tryb spoczynku po $minutes min bez pilota.",
                fontSize = 10.sp, color = Palette.Soft,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(2, 5, 10, 0).forEach { m ->
                    Button(onClick = { scope.launch { app.settings.setIdleMinutes(m) } }) {
                        Text((if (m == minutes) "✓ " else "") + (if (m == 0) "Wyłącz" else "$m min"), fontSize = 11.sp)
                    }
                }
            }
            Text(
                "W trybie spoczynku: domek na środku, godzina, temperatury i osoby w domu. Wszystko powoli wędruje po ekranie, " +
                    "a po 2 godzinach dodatkowo przygasa. Wygaszacz telewizora nie włącza się, gdy na ekranie jest Dom.",
                fontSize = 9.sp, color = Palette.Muted,
            )
            val blankHours = settings?.blankHours ?: 3
            Text(
                if (blankHours == 0) "Czarny ekran: nigdy." else "Czarny ekran po $blankHours h trybu spoczynku.",
                fontSize = 10.sp, color = Palette.Soft,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(1, 3, 6, 0).forEach { h ->
                    Button(onClick = { scope.launch { app.settings.setBlankHours(h) } }) {
                        Text((if (h == blankHours) "✓ " else "") + (if (h == 0) "Nigdy" else "$h h"), fontSize = 11.sp)
                    }
                }
            }
            val away = settings?.blankWhenAway ?: true
            Button(onClick = { scope.launch { app.settings.setBlankWhenAway(!away) } }) {
                Text((if (away) "✓ " else "") + "Czarny ekran, gdy nikogo nie ma w domu (Home Assistant)", fontSize = 11.sp)
            }
        }
        Section {
            Text("Aktualizacje", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Palette.Text)
            val update by app.updater.state.collectAsStateWithLifecycle()
            Text(
                "Zainstalowana wersja: ${app.updater.currentVersionName}. " + when (val u = update) {
                    UpdateState.Idle -> ""
                    UpdateState.Checking -> "Sprawdzam…"
                    UpdateState.UpToDate -> "Masz najnowszą wersję."
                    is UpdateState.Available -> "Dostępna: ${u.info.versionName}."
                    is UpdateState.Downloading -> "Pobieram… ${(u.progress * 100).toInt()}%"
                    UpdateState.Installing -> "Instaluję – potwierdź na ekranie."
                    is UpdateState.Failed -> u.message
                },
                fontSize = 10.sp, color = Palette.Soft,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { scope.launch { app.updater.check() } }) { Text("Sprawdź teraz", fontSize = 11.sp) }
                val available = update as? UpdateState.Available
                if (available != null) {
                    Button(onClick = {
                        if (app.updater.canInstall()) scope.launch { app.updater.downloadAndInstall(available.info) }
                        else app.updater.openInstallPermissionSettings()
                    }) { Text("Zainstaluj ${available.info.versionName}", fontSize = 11.sp) }
                }
            }
            Text(
                "Przy pierwszej aktualizacji system poprosi o zgodę na instalowanie aplikacji przez „Dom”. Zgadzasz się raz.",
                fontSize = 9.sp, color = Palette.Muted,
            )
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun Section(content: @Composable () -> Unit) {
    Column(
        Modifier
            .width(620.dp)
            .background(Palette.Card.copy(alpha = 0.85f), RoundedCornerShape(10.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) { content() }
}

