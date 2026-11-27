package pl.dom.launcher.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
    TabRow(
        selectedTabIndex = selected,
        modifier = Modifier
            .focusProperties { enter = { focusRequesters[selected] } }
            .focusGroup(),
    ) {
        tabs.forEachIndexed { index, title ->
            Tab(
                selected = index == selected,
                onFocus = { onSelect(index) },
                modifier = Modifier.focusRequester(focusRequesters[index]),
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
