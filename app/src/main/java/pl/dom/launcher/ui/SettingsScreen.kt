package pl.dom.launcher.ui

import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import pl.dom.launcher.LauncherApp
import pl.dom.launcher.config.ConfigServer
import pl.dom.launcher.config.Net
import pl.dom.launcher.config.qrBitmap
import pl.dom.launcher.data.LauncherSettings
import pl.dom.launcher.data.RemapAction
import pl.dom.launcher.service.HomeButtonService
import pl.dom.launcher.service.KeyBus
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

    // Status usługi Home sprawdzamy przy każdym powrocie na ekran (np. z ustawień systemu).
    var a11yEnabled by remember { mutableStateOf(HomeButtonService.isEnabled(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) a11yEnabled = HomeButtonService.isEnabled(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // --- Nauka przycisku pilota ---
    var learning by remember { mutableStateOf(false) }
    LaunchedEffect(learning) {
        if (!learning) return@LaunchedEffect
        KeyBus.learning.value = true
        val since = SystemClock.uptimeMillis()
        val press = withTimeoutOrNull(15_000) {
            KeyBus.lastKey.filterNotNull().first { it.atMillis > since && KeyBus.isAssignable(it.keyCode) }
        }
        KeyBus.learning.value = false
        learning = false
        if (press != null) app.settings.setRemap(press.keyCode, RemapAction.OPEN_LAUNCHER, "")
    }
    DisposableEffect(Unit) { onDispose { KeyBus.learning.value = false } }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
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
                        "Zeskanuj kod telefonem podłączonym do tej samej sieci. Wpiszesz tam klucze Home Assistanta, Emby, TMDB i Traktu oraz ułożysz aplikacje.",
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
            Text("Przycisk Home na pilocie", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Palette.Text)
            Text(
                if (a11yEnabled) "Włączone – Home otwiera ten launcher."
                else "Wyłączone – włącz usługę „Dom – przycisk Home” w ułatwieniach dostępu.",
                fontSize = 10.sp, color = if (a11yEnabled) Palette.Ok else Palette.Amber,
            )
            Button(onClick = {
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }) { Text("Otwórz ułatwienia dostępu", fontSize = 11.sp) }
        }

        Section {
            Text("Przycisk do przypisania (np. Crunchyroll)", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Palette.Text)
            val code = settings?.remapKeyCode ?: -1
            val action = settings?.remapAction ?: RemapAction.NONE
            Text(
                if (code < 0 || action == RemapAction.NONE) "Brak przypisania."
                else "${KeyEvent.keyCodeToString(code)} → ${action.label(settings?.remapPackage)}",
                fontSize = 10.sp, color = Palette.Soft,
            )
            if (learning) {
                Text("Naciśnij teraz przycisk na pilocie… (15 s)", fontSize = 12.sp, color = Palette.Amber)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { learning = true }, enabled = a11yEnabled && !learning) {
                    Text("Przypisz przycisk", fontSize = 11.sp)
                }
                Button(onClick = { scope.launch { app.settings.setRemap(-1, RemapAction.NONE, "") } }) {
                    Text("Usuń przypisanie", fontSize = 11.sp)
                }
            }
            Text(
                "Domyślnie przycisk otwiera launcher. Inną aplikację wybierzesz na stronie w telefonie.",
                fontSize = 9.sp, color = Palette.Muted,
            )
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
                        Text(if (m == 0) "Wyłącz" else "$m min", fontSize = 11.sp)
                    }
                }
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

private fun RemapAction.label(packageName: String?): String = when (this) {
    RemapAction.NONE -> "nic"
    RemapAction.OPEN_LAUNCHER -> "otwiera launcher"
    RemapAction.OPEN_APP -> "otwiera ${packageName.orEmpty()}"
}
