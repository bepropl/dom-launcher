package pl.dom.launcher.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import pl.dom.launcher.MainActivity

/**
 * Usługa ułatwień dostępu (jak „Override” w Projectivy):
 *  - przycisk Home na pilocie otwiera Dom,
 *  - gdy na ekran wchodzi launcher Google TV (np. po włączeniu telewizora), od razu przykrywa go Dom.
 * Nie czyta zawartości ekranu – dostaje tylko informację, która aplikacja wyszła na wierzch,
 * i to wyłącznie dla launcherów Google (packageNames w accessibility_service_config.xml).
 */
class HomeOverrideService : AccessibilityService() {

    private var lastOpenAt = 0L

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
