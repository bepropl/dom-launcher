package pl.dom.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

data class PanelItem(val title: String, val subtitle: String?, val icon: ImageVector, val onClick: () -> Unit)

/** Panel wysuwany z prawej strony (jak ustawienia w Google TV). Wstecz albo strzałka w lewo zamyka. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SidePanel(visible: Boolean, title: String, items: List<PanelItem>, onClose: () -> Unit) {
    val first = remember { FocusRequester() }
    BackHandler(enabled = visible, onBack = onClose)
    LaunchedEffect(visible) {
        if (visible) {
            delay(120)
            runCatching { first.requestFocus() }
        }
    }
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
        AnimatedVisibility(
            visible,
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut(),
        ) {
            Column(
                Modifier
                    .fillMaxHeight()
                    .width(300.dp)
                    .background(Palette.Card)
                    .focusProperties {
                        exit = { FocusRequester.Cancel }
                    }
                    .focusGroup()
                    .onPreviewKeyEvent { e ->
                        if (e.key == Key.DirectionLeft && e.type == KeyEventType.KeyDown) { onClose(); true } else false
                    }
                    .padding(horizontal = 16.dp, vertical = 28.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = Palette.Text, modifier = Modifier.padding(start = 10.dp, bottom = 14.dp))
                items.forEachIndexed { index, item ->
                    Surface(
                        onClick = { item.onClick() },
                        modifier = Modifier.fillMaxWidth().then(if (index == 0) Modifier.focusRequester(first) else Modifier),
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
                            Icon(item.icon, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(item.title, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                item.subtitle?.let { Text(it, fontSize = 9.sp) }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/** Pozycje panelu zębatki. */
fun settingsPanelItems(onLauncher: () -> Unit, onAndroid: () -> Unit) = listOf(
    PanelItem("Ustawienia launchera", "Dom: kod QR, domek, wygaszacz, aktualizacje", Icons.Filled.Home, onLauncher),
    PanelItem("Ustawienia Androida", "Sieć, obraz, dźwięk, aplikacje, system", Icons.Filled.Settings, onAndroid),
)
