package pl.dom.launcher.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import pl.dom.launcher.LauncherApp
import pl.dom.launcher.MainActivity
import pl.dom.launcher.data.LauncherSettings
import pl.dom.launcher.data.RemapAction

/**
 * Usługa ułatwień dostępu, która:
 *  - przejmuje przycisk Home i otwiera nasz launcher zamiast ekranu Google TV,
 *  - wykonuje akcję przypisaną do wybranego przycisku pilota (np. Crunchyroll),
 *  - w trybie nauki zapamiętuje następny wciśnięty przycisk.
 * Nie czyta zawartości ekranu (canRetrieveWindowContent = false).
 */
class HomeButtonService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile
    private var settings: LauncherSettings? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        scope.launch {
            LauncherApp.from(this@HomeButtonService).settings.flow.collect { settings = it }
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        val isUp = event.action == KeyEvent.ACTION_UP

        if (isUp) KeyBus.lastKey.value = KeyPress(code, SystemClock.uptimeMillis())

        if (code == KeyEvent.KEYCODE_HOME) {
            if (isUp) openLauncher()
            return true
        }

        if (KeyBus.learning.value && KeyBus.isAssignable(code)) {
            // Łapiemy przycisk dla ekranu ustawień, żeby np. nie otworzył się Crunchyroll.
            return true
        }

        val current = settings
        if (current != null && current.remapAction != RemapAction.NONE && current.remapKeyCode == code) {
            if (isUp) runRemap(current)
            return true
        }
        return false
    }

    private fun openLauncher() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainActivity.EXTRA_GO_HOME, true)
        startActivity(intent)
    }

    private fun runRemap(s: LauncherSettings) {
        when (s.remapAction) {
            RemapAction.OPEN_LAUNCHER -> openLauncher()
            RemapAction.OPEN_APP -> LauncherApp.from(this).apps.launch(s.remapPackage)
            RemapAction.NONE -> Unit
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        /** Czy użytkownik włączył naszą usługę w Ustawieniach → Ułatwienia dostępu. */
        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ).orEmpty()
            return enabled.split(':').any { it.startsWith("${context.packageName}/") }
        }
    }
}
