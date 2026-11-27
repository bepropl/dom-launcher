package pl.dom.launcher.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Tab
import androidx.tv.material3.TabRow
import androidx.tv.material3.Text

/**
 * Menu: Start / Do obejrzenia / Filmy / Seriale / Aplikacje. Zakładka zmienia się po najechaniu.
 * Wejście do menu z góry albo z dołu zawsze trafia na bieżącą zakładkę (na ekranie Start – na „Start”),
 * a nie na tę, która akurat leży najbliżej kursora.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MainTabs(
    tabs: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    focusRequesters: List<FocusRequester>,
) {
    // Zakładka przełącza się dopiero, gdy kursor zatrzyma się na niej na chwilę –
    // przelatywanie przez menu (albo „szarpnięcia” fokusu przy starcie) niczego nie przełącza.
    var pending by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(pending) {
        val target = pending ?: return@LaunchedEffect
        delay(220)
        if (target != selected) onSelect(target)
        pending = null
    }
    TabRow(
        selectedTabIndex = selected,
        modifier = Modifier
            .focusProperties { enter = { focusRequesters[selected] } }
            .focusGroup(),
    ) {
        tabs.forEachIndexed { index, title ->
            Tab(
                selected = index == selected,
                onFocus = { pending = index },
                modifier = Modifier
                    .focusRequester(focusRequesters[index])
                    .onFocusChanged { if (!it.isFocused && pending == index) pending = null },
            ) {
                Text(
                    text = title,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }
        }
    }
}
