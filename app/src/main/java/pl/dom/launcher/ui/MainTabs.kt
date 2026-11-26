package pl.dom.launcher.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Tab
import androidx.tv.material3.TabRow
import androidx.tv.material3.Text

/** Menu: Start / Do obejrzenia / Filmy / Seriale / Aplikacje. Zakładka zmienia się po najechaniu. */
@Composable
fun MainTabs(tabs: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    TabRow(selectedTabIndex = selected) {
        tabs.forEachIndexed { index, title ->
            Tab(selected = index == selected, onFocus = { onSelect(index) }) {
                Text(
                    text = title,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }
        }
    }
}
