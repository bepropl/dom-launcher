package pl.dom.launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import pl.dom.launcher.data.TvApp

/** Wszystkie aplikacje. OK uruchamia, przytrzymanie OK dodaje/usuwa z dolnego rzędu na Starcie. */
@Composable
fun AppsScreen(
    apps: List<TvApp>,
    favoritePackages: Set<String>,
    onLaunch: (TvApp) -> Unit,
    onToggleFavorite: (TvApp) -> Unit,
    focusFirstApp: Boolean = false,
    onFirstAppFocused: () -> Unit = {},
) {
    val firstApp = remember { FocusRequester() }
    // Po wejściu kafelkiem „Wszystkie” fokus przechodzi od razu na pierwszą aplikację.
    LaunchedEffect(focusFirstApp, apps.isNotEmpty()) {
        if (focusFirstApp && apps.isNotEmpty()) {
            delay(100)
            runCatching { firstApp.requestFocus() }
            onFirstAppFocused()
        }
    }

    Column(Modifier.fillMaxSize().padding(top = 12.dp, start = EdgePadding, end = EdgePadding)) {
        Text(
            "OK – uruchom · przytrzymaj OK – dodaj do ekranu Start lub usuń · kolejność ustawisz z telefonu",
            fontSize = 10.sp,
            color = Palette.Muted,
        )
        Spacer(Modifier.height(10.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(6),
            horizontalArrangement = Arrangement.spacedBy(13.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp),
            contentPadding = PaddingValues(6.dp),
        ) {
            items(apps, key = { it.packageName }) { app ->
                AppCard(
                    app = app,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .then(if (app.packageName == apps.firstOrNull()?.packageName) Modifier.focusRequester(firstApp) else Modifier),
                    isFavorite = app.packageName in favoritePackages,
                    onClick = { onLaunch(app) },
                    onLongClick = { onToggleFavorite(app) },
                )
            }
        }
    }
}
