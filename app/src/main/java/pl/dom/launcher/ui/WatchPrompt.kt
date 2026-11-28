package pl.dom.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.tv.material3.Button
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import pl.dom.launcher.media.Episode

/**
 * „Obejrzeliście 5. odcinek 2. sezonu?” – gdy serial został otwarty z Dom, a po ponad 20 minutach
 * wracacie do launchera. Jedno OK zapisuje postęp.
 */
object WatchPrompt {
    data class Pending(val tmdbId: Int, val name: String, val episode: Episode, val startedAt: Long)

    private const val MIN_MS = 20 * 60_000L
    private const val MAX_MS = 8 * 3600_000L

    val pending = MutableStateFlow<Pending?>(null)
    val ask = MutableStateFlow<Pending?>(null)

    fun started(tmdbId: Int, name: String, episode: Episode) {
        pending.value = Pending(tmdbId, name, episode, System.currentTimeMillis())
    }

    /** Wołane, gdy launcher znów jest na ekranie. */
    fun onLauncherResumed() {
        val p = pending.value ?: return
        val elapsed = System.currentTimeMillis() - p.startedAt
        when {
            elapsed > MAX_MS -> pending.value = null
            elapsed >= MIN_MS -> {
                pending.value = null
                ask.value = p
            }
        }
    }
}

@Composable
fun WatchPromptCard(p: WatchPrompt.Pending, onYes: () -> Unit, onNo: () -> Unit, modifier: Modifier = Modifier) {
    val yes = remember(p) { FocusRequester() }
    LaunchedEffect(p) {
        delay(150)
        runCatching { yes.requestFocus() }
        // Bez reakcji karta sama znika.
        delay(25_000)
        onNo()
    }
    Column(
        modifier
            .widthIn(max = 460.dp)
            .background(Palette.Card.copy(alpha = 0.97f), RoundedCornerShape(14.dp))
            .border(1.dp, Palette.Amber.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(p.name, fontSize = 11.sp, color = Palette.Muted)
        Text("Obejrzeliście ${p.episode.number}. odcinek ${p.episode.season}. sezonu?", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Palette.Text)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onYes, modifier = Modifier.focusRequester(yes)) { Text("Tak, obejrzany", fontSize = 11.sp) }
            OutlinedButton(onClick = onNo) { Text("Nie", fontSize = 11.sp) }
        }
    }
}
