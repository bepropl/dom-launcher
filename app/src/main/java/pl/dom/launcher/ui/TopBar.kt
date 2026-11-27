package pl.dom.launcher.ui

import android.net.ConnectivityManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import pl.dom.launcher.ha.HaStatus
import pl.dom.launcher.ha.HaSummary

@Composable
fun TopBar(
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit,
    ha: HaSummary? = null,
    haStatus: HaStatus = HaStatus.Off,
    unread: Int = 0,
    onOpenNotifications: () -> Unit = {},
) {
    val now by rememberNow()
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(now.format(TimeFormat), fontSize = 32.sp, fontWeight = FontWeight.Light, color = Palette.Text)
        Spacer(Modifier.width(11.dp))
        Text(now.polishDate(), fontSize = 12.sp, color = Palette.Muted)

        Spacer(Modifier.weight(1f))

        // Dane z Home Assistanta (encje wybierasz na stronie w telefonie).
        if (ha?.weatherLabel != null) {
            HaInfo(value = listOfNotNull(ha.weatherIcon, ha.outside).joinToString(" "), label = ha.weatherLabel)
            Spacer(Modifier.width(18.dp))
        }
        if (ha?.weatherLabel == null || ha.outside == null) {
            HaInfo(value = ha?.outside ?: "—°", label = "Na zewnątrz")
            Spacer(Modifier.width(18.dp))
        }
        HaInfo(value = ha?.inside ?: "—°", label = "W domu")
        Spacer(Modifier.width(18.dp))
        HaInfo(
            value = ha?.peopleHome?.let { "$it/${ha.peopleTotal}" } ?: "—",
            label = when {
                ha == null || ha.peopleHome == null -> "Osoby w domu"
                ha.namesHome.isEmpty() -> "Nikogo w domu"
                ha.namesHome.size <= 2 -> ha.namesHome.joinToString(", ")
                else -> "Osoby w domu"
            },
        )
        Spacer(Modifier.width(18.dp))
        if (haStatus !is HaStatus.Connected && haStatus !is HaStatus.Off) {
            Text(
                if (haStatus is HaStatus.Connecting) "Łączę z HA…" else "Brak połączenia z HA",
                fontSize = 8.sp, color = Palette.Muted,
            )
            Spacer(Modifier.width(12.dp))
        }
        Box(Modifier.width(1.dp).height(24.dp).background(Palette.Amber.copy(alpha = 0.25f)))
        Spacer(Modifier.width(14.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                RoundIconButton(Icons.Filled.Notifications, "Powiadomienia", onOpenNotifications)
                if (unread > 0) {
                    Box(
                        Modifier.align(Alignment.TopEnd).size(8.dp).background(Palette.Amber, CircleShape),
                    )
                }
            }
            WifiIndicator()
            RoundIconButton(Icons.Filled.Settings, "Ustawienia launchera", onOpenSettings)
        }
    }
}

@Composable
private fun HaInfo(value: String, label: String) {
    Column {
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Palette.Text)
        Text(label, fontSize = 8.sp, color = Palette.Muted)
    }
}

@Composable
fun RoundIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(28.dp),
        shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Palette.Card.copy(alpha = 0.85f),
            contentColor = Palette.Text,
            focusedContainerColor = Palette.Amber,
            focusedContentColor = Palette.OnAmber,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.12f),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description, modifier = Modifier.size(14.dp))
        }
    }
}

/** Ikona Wi-Fi: jasna, gdy telewizor ma sieć, przygaszona, gdy nie ma. */
@Composable
private fun WifiIndicator() {
    val context = LocalContext.current
    val online by produceState(true) {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        while (true) {
            value = cm?.activeNetwork != null
            delay(30_000)
        }
    }
    val color = if (online) Palette.Text else Palette.Muted.copy(alpha = 0.4f)
    Box(
        Modifier
            .size(28.dp)
            .semantics { contentDescription = if (online) "Sieć: połączono" else "Brak sieci" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(16.dp)) {
            val stroke = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
            val c = Offset(size.width / 2, size.height * 0.9f)
            listOf(0.95f, 0.65f, 0.35f).forEach { r ->
                val radius = size.width * r * 0.6f
                drawArc(
                    color = color, startAngle = 225f, sweepAngle = 90f, useCenter = false,
                    topLeft = Offset(c.x - radius, c.y - radius), size = Size(radius * 2, radius * 2), style = stroke,
                )
            }
            drawCircle(color, radius = 1.4.dp.toPx(), center = c)
        }
    }
}
