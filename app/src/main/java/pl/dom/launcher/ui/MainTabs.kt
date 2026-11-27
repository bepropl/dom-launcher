package pl.dom.launcher.ui

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.delay

/**
 * Menu: Start / Do obejrzenia / Filmy / Seriale / Aplikacje.
 * Lekki, własny pasek (bez animowanego wskaźnika z biblioteki). Zakładka przełącza się
 * po najechaniu – z krótkim opóźnieniem, żeby szybkie przelecenie po menu nic nie przełączało.
 * Wejście do menu z góry albo z dołu zawsze trafia na bieżącą zakładkę.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MainTabs(
    tabs: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    focusRequesters: List<FocusRequester>,
) {
    val currentSelected by rememberUpdatedState(selected)
    val select by rememberUpdatedState(onSelect)
    var pending by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(pending) {
        val target = pending ?: return@LaunchedEffect
        delay(130)
        if (target != currentSelected) select(target)
    }

    Row(
        modifier = Modifier
            .focusProperties { enter = { focusRequesters[currentSelected] } }
            .focusGroup(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        tabs.forEachIndexed { index, title ->
            val isSelected = index == selected
            Surface(
                onClick = { select(index) },
                modifier = Modifier
                    .focusRequester(focusRequesters[index])
                    .onFocusChanged {
                        if (it.isFocused) pending = index
                        else if (pending == index) pending = null
                    },
                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(50)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = if (isSelected) Palette.Card.copy(alpha = 0.9f) else Color.Transparent,
                    contentColor = if (isSelected) Palette.Text else Palette.Soft,
                    focusedContainerColor = Palette.Amber,
                    focusedContentColor = Palette.OnAmber,
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            ) {
                Text(
                    text = title,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }
        }
    }
}
