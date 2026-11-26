package pl.dom.launcher.data

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** Aplikacja zainstalowana na telewizorze, z oficjalnym banerem (logo), jeśli go ma. */
data class TvApp(
    val packageName: String,
    val label: String,
    val banner: ImageBitmap?,
    val icon: ImageBitmap?,
)

class AppsRepository(private val context: Context) {

    private val pm = context.packageManager
    private val _apps = MutableStateFlow<List<TvApp>>(emptyList())
    val apps: StateFlow<List<TvApp>> = _apps.asStateFlow()

    /** Wczytuje aplikacje widoczne na Google TV (kategoria LEANBACK_LAUNCHER). */
    suspend fun refresh() = withContext(Dispatchers.IO) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        _apps.value = pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != context.packageName }
            .distinctBy { it.activityInfo.packageName }
            .map { info ->
                val activity = info.activityInfo
                val banner = activity.loadBanner(pm) ?: activity.applicationInfo.loadBanner(pm)
                TvApp(
                    packageName = activity.packageName,
                    label = info.loadLabel(pm).toString(),
                    banner = runCatching { banner?.toBitmap(320, 180)?.asImageBitmap() }.getOrNull(),
                    icon = runCatching { activity.loadIcon(pm).toBitmap(128, 128).asImageBitmap() }.getOrNull(),
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    /** Szuka zainstalowanej aplikacji po nazwie serwisu, np. „Disney Plus” → „Disney+”. */
    fun findByName(name: String): TvApp? {
        fun norm(t: String) = t.lowercase().replace("plus", "").filter { it.isLetterOrDigit() }
        val wanted = norm(name)
        if (wanted.isEmpty()) return null
        return _apps.value.firstOrNull { norm(it.label) == wanted }
            ?: _apps.value.firstOrNull { app ->
                val label = norm(app.label)
                label.length >= 3 && (wanted.contains(label) || label.contains(wanted))
            }
    }

    fun launch(packageName: String): Boolean {
        val intent = pm.getLeanbackLaunchIntentForPackage(packageName)
            ?: pm.getLaunchIntentForPackage(packageName)
            ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    companion object {
        /** Domyślny dolny rząd, dopóki nie ustawisz własnego (tylko aplikacje, które są zainstalowane). */
        val PREFERRED = listOf(
            "com.netflix.ninja",
            "com.google.android.youtube.tv",
            "com.disney.disneyplus",
            "com.amazon.amazonvideo.livingroom",
            "tv.emby.embyatv",
        )

        fun resolveFavorites(stored: List<String>, all: List<TvApp>): List<TvApp> {
            val byPackage = all.associateBy { it.packageName }
            if (stored.isNotEmpty()) return stored.mapNotNull { byPackage[it] }
            val preferred = PREFERRED.mapNotNull { byPackage[it] }
            val rest = all.filter { it.packageName !in PREFERRED }
            return (preferred + rest).take(6)
        }
    }
}
