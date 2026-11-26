package pl.dom.launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import pl.dom.launcher.data.TvApp
import pl.dom.launcher.update.UpdateState

private val AppTileWidth = 110.dp
private val AppTileHeight = 62.dp

@Composable
fun StartScreen(
    favorites: List<TvApp>,
    onLaunch: (TvApp) -> Unit,
    onOpenAllApps: () -> Unit,
    updateState: UpdateState = UpdateState.Idle,
) {
    val firstApp = remember { FocusRequester() }
    // Po starcie fokus ląduje na pierwszej aplikacji, a nie w menu.
    LaunchedEffect(favorites.isNotEmpty()) {
        if (favorites.isNotEmpty()) {
            delay(150)
            runCatching { firstApp.requestFocus() }
        }
    }

    Column(Modifier.fillMaxSize().padding(top = 12.dp)) {
        Text("Nowe odcinki Waszych seriali", fontSize = 11.sp, color = Palette.Soft)
        Spacer(Modifier.height(8.dp))
        Surface(
            onClick = { },
            modifier = Modifier.width(300.dp).height(70.dp),
            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Palette.Card.copy(alpha = 0.85f),
                focusedContainerColor = Palette.Card,
            ),
        ) {
            Column(Modifier.padding(12.dp)) {
                Text("Połącz konto Trakt", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Palette.Text)
                Spacer(Modifier.height(3.dp))
                Text(
                    "Nowe odcinki pojawią się tu w etapie 4. Klucze wpiszesz z telefonu w Ustawieniach.",
                    fontSize = 9.sp,
                    color = Palette.Muted,
                )
            }
        }

        // Środek ekranu zostaje pusty – tu widać domek.
        Spacer(Modifier.weight(1f))

        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (updateState is UpdateState.Available) {
                Text(
                    "Dostępna aktualizacja ${updateState.info.versionName} – zainstalujesz ją w Ustawieniach",
                    fontSize = 10.sp,
                    color = Palette.Amber,
                )
            } else {
                Text("Home Assistant: jeszcze niepodłączony (etap 3)", fontSize = 10.sp, color = Palette.Muted)
            }
        }
        Spacer(Modifier.height(10.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(13.dp),
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
        ) {
            itemsIndexed(favorites, key = { _, app -> app.packageName }) { index, app ->
                AppCard(
                    app = app,
                    modifier = Modifier
                        .size(AppTileWidth, AppTileHeight)
                        .then(if (index == 0) Modifier.focusRequester(firstApp) else Modifier),
                    onClick = { onLaunch(app) },
                )
            }
            item(key = "all") {
                ActionCard(modifier = Modifier.size(AppTileWidth, AppTileHeight), onClick = onOpenAllApps) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Wszystkie", fontSize = 11.sp, color = Palette.Text)
                    }
                }
            }
        }
    }
}
