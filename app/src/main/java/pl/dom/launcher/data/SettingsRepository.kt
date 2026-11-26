package pl.dom.launcher.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "launcher")

/** Co ma się stać po wciśnięciu przypisanego przycisku pilota (np. Crunchyroll). */
enum class RemapAction { NONE, OPEN_LAUNCHER, OPEN_APP }

/** Zwykłe (niesekretne) ustawienia launchera. Klucze i tokeny trzyma [SecretStore]. */
data class LauncherSettings(
    /** Pakiety aplikacji w dolnym rzędzie, w kolejności. Pusta lista = domyślny zestaw. */
    val favorites: List<String>,
    /** Po ilu minutach bez pilota włącza się tryb spoczynku (0 = nigdy). */
    val idleMinutes: Int,
    val remapKeyCode: Int,
    val remapAction: RemapAction,
    val remapPackage: String,
    val haUrl: String,
    val embyUrl: String,
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val favorites = stringPreferencesKey("favorites")
        val idleMinutes = intPreferencesKey("idle_minutes")
        val remapKeyCode = intPreferencesKey("remap_key_code")
        val remapAction = stringPreferencesKey("remap_action")
        val remapPackage = stringPreferencesKey("remap_package")
        val haUrl = stringPreferencesKey("ha_url")
        val embyUrl = stringPreferencesKey("emby_url")
    }

    val flow: Flow<LauncherSettings> = context.dataStore.data.map { it.toSettings() }

    suspend fun current(): LauncherSettings = flow.first()

    suspend fun setFavorites(packages: List<String>) = context.dataStore.edit {
        it[Keys.favorites] = packages.distinct().joinToString(",")
    }

    suspend fun setIdleMinutes(minutes: Int) = context.dataStore.edit {
        it[Keys.idleMinutes] = minutes.coerceIn(0, 240)
    }

    suspend fun setRemap(keyCode: Int, action: RemapAction, packageName: String) = context.dataStore.edit {
        it[Keys.remapKeyCode] = keyCode
        it[Keys.remapAction] = action.name
        it[Keys.remapPackage] = packageName
    }

    suspend fun setHaUrl(url: String) = context.dataStore.edit { it[Keys.haUrl] = url.trim().trimEnd('/') }

    suspend fun setEmbyUrl(url: String) = context.dataStore.edit { it[Keys.embyUrl] = url.trim().trimEnd('/') }

    private fun Preferences.toSettings() = LauncherSettings(
        favorites = this[Keys.favorites].orEmpty().split(',').filter { it.isNotBlank() },
        idleMinutes = this[Keys.idleMinutes] ?: 5,
        remapKeyCode = this[Keys.remapKeyCode] ?: -1,
        remapAction = runCatching { RemapAction.valueOf(this[Keys.remapAction] ?: "NONE") }
            .getOrDefault(RemapAction.NONE),
        remapPackage = this[Keys.remapPackage].orEmpty(),
        haUrl = this[Keys.haUrl].orEmpty(),
        embyUrl = this[Keys.embyUrl].orEmpty(),
    )
}
