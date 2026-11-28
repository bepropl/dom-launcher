package pl.dom.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import pl.dom.launcher.BuildConfig
import pl.dom.launcher.ha.HaNotification
import pl.dom.launcher.update.UpdateState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PanelItem(val title: String, val subtitle: String?, val icon: ImageVector, val onClick: () -> Unit)

/**
 * Panel wysuwany z prawej strony (jak w Google TV). Wstecz albo strzałka w lewo zamyka go,
 * a kursor od razu wraca na przycisk, który go otworzył ([returnFocus]) – bez przeskakiwania.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SidePanel(
    visible: Boolean,
    title: String,
    returnFocus: FocusRequester?,
    onClose: () -> Unit,
    content: @Composable ColumnScope.(first: FocusRequester, close: () -> Unit) -> Unit,
) {
    val first = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    // Kolejność ma znaczenie: najpierw zwalniamy kursor i stawiamy go na przycisku, potem chowamy panel.
    val close: () -> Unit = {
        focusManager.clearFocus(force = true)
        runCatching { returnFocus?.requestFocus() }
        onClose()
    }
    BackHandler(enabled = visible, onBack = close)
    LaunchedEffect(visible) {
        if (visible) {
            delay(120)
            runCatching { first.requestFocus() }
        }
    }
    AnimatedVisibility(visible, enter = fadeIn(tween(200)), exit = fadeOut(tween(200))) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
        AnimatedVisibility(
            visible,
            enter = slideInHorizontally(tween(220)) { it } + fadeIn(tween(220)),
            exit = slideOutHorizontally(tween(200)) { it } + fadeOut(tween(200)),
        ) {
            Column(
                Modifier
                    .fillMaxHeight()
                    .width(320.dp)
                    .background(Palette.Card)
                    .focusProperties {
                        canFocus = visible
                        exit = { if (visible) FocusRequester.Cancel else FocusRequester.Default }
                    }
                    .focusGroup()
                    // Panel sam obsługuje Wstecz i strzałkę w lewo, zanim zrobi to system kursora.
                    .onPreviewKeyEvent { e ->
                        val isClose = e.key == Key.Back || e.key == Key.Escape || e.key == Key.DirectionLeft
                        when {
                            !isClose -> false
                            e.type == KeyEventType.KeyDown -> true
                            e.type == KeyEventType.KeyUp -> { close(); true }
                            else -> true
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 28.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = Palette.Text, modifier = Modifier.padding(start = 10.dp, bottom = 14.dp))
                content(first, close)
            }
        }
    }
}

/** Jeden wiersz panelu (ikona, tytuł, podpis). */
@Composable
fun PanelRow(
    title: String,
    subtitle: String?,
    icon: ImageVector?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().focusProperties { canFocus = visible },
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = Palette.Text,
            focusedContainerColor = Palette.Text,
            focusedContentColor = Palette.Bg,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(14.dp))
            }
            Column {
                Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                subtitle?.let { Text(it, fontSize = 9.sp) }
            }
        }
    }
}

/** Pozycje panelu zębatki. */
fun settingsPanelItems(
    onLauncher: () -> Unit,
    onAndroid: () -> Unit,
    update: UpdateState,
    onUpdate: () -> Unit,
    guestSsid: String? = null,
    onGuest: () -> Unit = {},
) = listOfNotNull(
    PanelItem("Ustawienia launchera", "Dom: kod QR, domek, wygaszacz", Icons.Filled.Home, onLauncher),
    PanelItem("Ustawienia Androida", "Sieć, obraz, dźwięk, aplikacje, system", Icons.Filled.Settings, onAndroid),
    guestSsid?.let { PanelItem("Wi-Fi dla gości", "Kod QR do sieci „$it”", Icons.Filled.Lock, onGuest) },
    PanelItem(
        if (update is UpdateState.Available) "Zainstaluj aktualizację" else "Aktualizacja Dom",
        when (update) {
            is UpdateState.Available -> "Dostępna ${update.info.versionName} (masz ${BuildConfig.VERSION_NAME}) – OK instaluje"
            is UpdateState.Downloading -> "Pobieram… ${(update.progress * 100).toInt()}%"
            UpdateState.Installing -> "Instaluję – potwierdź na ekranie"
            UpdateState.Checking -> "Sprawdzam…"
            UpdateState.UpToDate -> "Masz najnowszą wersję (${BuildConfig.VERSION_NAME})"
            is UpdateState.Failed -> update.message
            else -> "Wersja ${BuildConfig.VERSION_NAME} – OK sprawdza"
        },
        Icons.Filled.Refresh,
        onUpdate,
    ),
)

/** Tekst kodu QR sieci Wi-Fi (format WIFI:, rozumiany przez aparat w iPhonie i Androidzie). */
fun guestWifiQrText(ssid: String, password: String?, security: String, hidden: Boolean): String {
    fun esc(v: String) = v.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace(":", "\\:").replace("\"", "\\\"")
    val t = if (security == "nopass" || password.isNullOrEmpty()) "nopass" else security
    val p = if (t == "nopass") "" else "P:${esc(password!!)};"
    return "WIFI:T:$t;S:${esc(ssid)};$p${if (hidden) "H:true;" else ""};"
}

/** Panel z dużym kodem QR sieci dla gości – samo SSID, bez hasła na ekranie. */
@Composable
fun ColumnScope.GuestWifiContent(ssid: String, qrText: String, first: FocusRequester, visible: Boolean, close: () -> Unit) {
    val qr = remember(qrText) { pl.dom.launcher.config.qrBitmap(qrText, 512) }
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Image(qr, contentDescription = "Kod QR sieci $ssid", modifier = Modifier.size(220.dp).clip(RoundedCornerShape(8.dp)))
    }
    Text(ssid, fontSize = 16.sp, color = Palette.Text, modifier = Modifier.align(Alignment.CenterHorizontally))
    Text(
        "Zeskanujcie aparatem w telefonie – połączy się samo.",
        fontSize = 10.sp, color = Palette.Soft, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp, bottom = 10.dp),
    )
    PanelRow("Zamknij", null, null, close, modifier = Modifier.focusRequester(first), visible = visible)
}

private val PanelTime = SimpleDateFormat("HH:mm", Locale.forLanguageTag("pl-PL"))

/** Zawartość panelu powiadomień: „Wyczyść” i lista (najnowsze na górze). */
@Composable
fun ColumnScope.NotificationsPanelContent(
    recent: List<HaNotification>,
    first: FocusRequester,
    visible: Boolean,
    onClear: () -> Unit,
) {
    PanelRow(
        title = if (recent.isEmpty()) "Brak powiadomień" else "Wyczyść wszystkie",
        subtitle = if (recent.isEmpty()) "Pojawią się tu powiadomienia z Home Assistanta" else "${recent.size} na liście",
        icon = null,
        onClick = onClear,
        modifier = Modifier.focusRequester(first),
        visible = visible,
    )
    Spacer(Modifier.height(4.dp))
    Column(
        Modifier.weight(1f).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        recent.forEach { n ->
            Surface(
                onClick = {},
                modifier = Modifier.fillMaxWidth().focusProperties { canFocus = visible },
                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Palette.Bg.copy(alpha = 0.35f),
                    contentColor = Palette.Text,
                    focusedContainerColor = Palette.Bg.copy(alpha = 0.8f),
                    focusedContentColor = Palette.Text,
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    n.image?.let { bitmap ->
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(8.dp)),
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(PanelTime.format(Date(n.at)), fontSize = 10.sp, color = Palette.Amber)
                        Spacer(Modifier.width(8.dp))
                        Text(n.title, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                    if (n.message.isNotBlank()) Text(n.message, fontSize = 10.sp, color = Palette.Soft)
                }
            }
        }
    }
}
