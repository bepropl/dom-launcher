package pl.dom.launcher.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import pl.dom.launcher.MainActivity
import pl.dom.launcher.LauncherApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Usługa ułatwień dostępu (jak „Override” w Projectivy), a przy okazji dymki z Home Assistanta nad innymi aplikacjami:
 *  - przycisk Home na pilocie otwiera Dom,
 *  - gdy na ekran wchodzi launcher Google TV (np. po włączeniu telewizora), od razu przykrywa go Dom.
 * Nie czyta zawartości ekranu – dostaje tylko informację, która aplikacja wyszła na wierzch,
 * i to wyłącznie dla launcherów Google (packageNames w accessibility_service_config.xml).
 */
class HomeOverrideService : AccessibilityService() {

    private var lastOpenAt = 0L
    private var scope: CoroutineScope? = null
    private var overlay: NotificationOverlay? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        val popup = NotificationOverlay(this)
        overlay = popup
        // Powiadomienia z Home Assistanta nad innymi aplikacjami (w launcherze pokazuje je sam launcher).
        scope = MainScope().also { scope ->
            scope.launch {
                LauncherApp.from(this@HomeOverrideService).ha.notifications.collect { n ->
                    if (!AppVisibility.launcherVisible) popup.show(n)
                }
            }
        }
    }

    override fun onDestroy() {
        scope?.cancel()
        overlay?.remove()
        super.onDestroy()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_HOME) return false
        if (event.action == KeyEvent.ACTION_UP) openLauncher()
        return true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (event.packageName?.toString() in STOCK_LAUNCHERS) openLauncher()
    }

    private fun openLauncher() {
        val now = SystemClock.uptimeMillis()
        if (now - lastOpenAt < 700) return
        lastOpenAt = now
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainActivity.EXTRA_GO_HOME, true)
        runCatching { startActivity(intent) }
    }

    override fun onInterrupt() = Unit

    companion object {
        val STOCK_LAUNCHERS = setOf(
            "com.google.android.apps.tv.launcherx",
            "com.google.android.tvlauncher",
            "com.google.android.leanbacklauncher",
        )

        /** Czy usługa jest włączona w Ustawieniach → Ułatwienia dostępu. */
        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ).orEmpty()
            return enabled.split(':').any { it.startsWith("${context.packageName}/") }
        }
    }
}
