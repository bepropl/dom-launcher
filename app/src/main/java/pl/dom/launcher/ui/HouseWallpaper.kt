package pl.dom.launcher.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text

/**
 * Miejsce na domek z cząsteczek. W etapie 2 zastąpi go renderer OpenGL ES
 * z modelem z makiety (świecące okna sterowane z Home Assistanta).
 */
@Composable
fun HouseWallpaper(modifier: Modifier = Modifier, dim: Float = 1f) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Text("[ domek z cząsteczek – etap 2 ]", fontSize = 12.sp, color = Palette.Muted.copy(alpha = 0.25f * dim))
    }
}
