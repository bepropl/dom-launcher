package pl.dom.launcher.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import pl.dom.launcher.ha.HaNotification
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dymek z powiadomieniem z Home Assistanta, w prawym górnym rogu launchera.
 * Nie zabiera fokusu – pilot dalej steruje tym, co było na ekranie.
 */
@Composable
fun NotificationPopup(notifications: SharedFlow<HaNotification>, modifier: Modifier = Modifier) {
    var current by remember { mutableStateOf<HaNotification?>(null) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(notifications) {
        notifications.collect { n ->
            current = n
            visible = true
        }
    }
    LaunchedEffect(current) {
        val n = current ?: return@LaunchedEffect
        delay(n.seconds * 1000L)
        visible = false
    }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn() + slideInHorizontally { it / 3 },
        exit = fadeOut() + slideOutHorizontally { it / 3 },
    ) {
        current?.let { NotificationCard(it) }
    }
}

@Composable
fun NotificationCard(n: HaNotification) {
    Column(
        Modifier
            .width(if (n.image != null) 340.dp else 280.dp)
            .background(Palette.Card.copy(alpha = 0.96f), RoundedCornerShape(12.dp))
            .border(1.dp, Palette.Amber.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        n.image?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(8.dp)),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).background(Palette.Amber, RoundedCornerShape(4.dp)))
            Spacer(Modifier.width(7.dp))
            Text(n.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Palette.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (n.message.isNotBlank()) Text(n.message, fontSize = 11.sp, color = Palette.Soft, maxLines = 4, overflow = TextOverflow.Ellipsis)
    }
}

private val TimeOnly = SimpleDateFormat("HH:mm", Locale.forLanguageTag("pl-PL"))

/** Lista ostatnich powiadomień (dzwonek w pasku u góry). */
@Composable
fun NotificationsDialog(recent: List<HaNotification>, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .width(380.dp)
                .heightIn(max = 420.dp)
                .background(Palette.Card, RoundedCornerShape(12.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Powiadomienia", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Palette.Text)
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (recent.isEmpty()) {
                    Text("Brak powiadomień od włączenia telewizora.", fontSize = 11.sp, color = Palette.Muted)
                }
                recent.forEach { n ->
                    Row {
                        Text(TimeOnly.format(Date(n.at)), fontSize = 10.sp, color = Palette.Amber, modifier = Modifier.width(44.dp))
                        Column {
                            Text(n.title, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Palette.Text)
                            if (n.message.isNotBlank()) Text(n.message, fontSize = 10.sp, color = Palette.Soft)
                        }
                    }
                }
            }
            Button(onClick = onDismiss) { Text("Zamknij", fontSize = 11.sp) }
        }
    }
}
