package pl.dom.launcher.ui

import androidx.compose.ui.unit.dp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Kolory z makiety: bursztyn na czerni (OLED). */
/** Margines od krawędzi ekranu. Rzędy plakatów przewijają się aż do krawędzi (jak w Netflixie). */
val EdgePadding = 40.dp

object Palette {
    val Bg = Color(0xFF070504)
    val Card = Color(0xFF14100C)
    val Amber = Color(0xFFF2A65A)
    val OnAmber = Color(0xFF1A1009)
    val Text = Color(0xFFF4EBDD)
    val Soft = Color(0xFFD9C7AE)
    val Muted = Color(0xFFB9A68C)
    val Ok = Color(0xFF5DCAA5)
}

@Composable
fun LauncherTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Palette.Amber,
            onPrimary = Palette.OnAmber,
            background = Palette.Bg,
            onBackground = Palette.Text,
            surface = Palette.Card,
            onSurface = Palette.Text,
            surfaceVariant = Palette.Card,
            onSurfaceVariant = Palette.Muted,
            border = Palette.Amber,
        ),
        content = content,
    )
}

private val polish: Locale = Locale.forLanguageTag("pl-PL")
val TimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", polish)
val DateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM", polish)

fun LocalDateTime.polishDate(): String =
    format(DateFormat).replaceFirstChar { it.titlecase(polish) }

/** Aktualny czas, odświeżany dokładnie na początku każdej minuty. */
@Composable
fun rememberNow(): State<LocalDateTime> = produceState(LocalDateTime.now()) {
    while (true) {
        value = LocalDateTime.now()
        delay((60 - value.second) * 1000L + 50)
    }
}

/** Pomocnicze: uruchamia blok raz, gdy warunek staje się prawdziwy. */
@Composable
fun OnTrue(condition: Boolean, block: suspend () -> Unit) {
    LaunchedEffect(condition) { if (condition) block() }
}
