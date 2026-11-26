package pl.dom.launcher

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import pl.dom.launcher.service.Interaction
import pl.dom.launcher.ui.LauncherScreen
import pl.dom.launcher.ui.LauncherTheme

class MainActivity : ComponentActivity() {

    /** Zwiększany przy każdym „Home”, żeby ekran wrócił na zakładkę Start. */
    private val goHome = MutableStateFlow(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LauncherTheme {
                LauncherScreen(goHomeSignal = goHome)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Home, ikona aplikacji albo Button Mapper: zawsze wracamy na ekran Start.
        if (intent.action == Intent.ACTION_MAIN || intent.getBooleanExtra(EXTRA_GO_HOME, false)) {
            goHome.value++
        }
    }

    override fun onResume() {
        super.onResume()
        Interaction.touch()
        // Odświeżamy listę aplikacji przy każdym powrocie (np. po instalacji nowej).
        val app = LauncherApp.from(this)
        lifecycleScope.launch { app.apps.refresh() }
        lifecycleScope.launch { app.updater.checkIfDue() }
        lifecycleScope.launch { app.media.refreshIfDue() }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (Interaction.idle.value) {
            // Pierwsze wciśnięcie tylko budzi ekran z trybu spoczynku.
            if (event.action == KeyEvent.ACTION_UP) Interaction.touch()
            return true
        }
        Interaction.touch()
        return super.dispatchKeyEvent(event)
    }

    companion object {
        const val EXTRA_GO_HOME = "pl.dom.launcher.GO_HOME"
    }
}
