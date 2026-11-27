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
import pl.dom.launcher.ha.HaConfig

private val Context.dataStore by preferencesDataStore(name = "launcher")

/** Jakość domku na ekranie głównym. */
enum class HouseMode { FULL, ECO, OFF }

/** Serwis streamingowy z TMDB (np. Netflix = 8), wybrany na stronie w telefonie. */
data class StreamingProvider(val id: Int, val name: String)

/** Zwykłe (niesekretne) ustawienia launchera. Klucze i tokeny trzyma [SecretStore]. */
data class LauncherSettings(
    /** Pakiety aplikacji w dolnym rzędzie, w kolejności. Pusta lista = domyślny zestaw. */
    val favorites: List<String>,
    /** Po ilu minutach bez pilota włącza się tryb spoczynku (0 = nigdy). */
    val idleMinutes: Int,
    val haUrl: String,
    val embyUrl: String,
    val houseMode: HouseMode,
    /** Serwisy, które macie – Filmy i Seriale pokazują tylko to, co jest w nich dostępne. */
    val providers: List<StreamingProvider>,
    /** Wybrane encje Home Assistanta i zaufany certyfikat. */
    val ha: HaConfig,
    /** Po ilu godzinach wygaszacza ekran robi się całkiem czarny (0 = nigdy). */
    val blankHours: Int = 3,
    /** Czarny ekran od razu w trybie spoczynku, gdy nikogo nie ma w domu (z Home Assistanta). */
    val blankWhenAway: Boolean = true,
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val favorites = stringPreferencesKey("favorites")
        val idleMinutes = intPreferencesKey("idle_minutes")
        val haUrl = stringPreferencesKey("ha_url")
        val embyUrl = stringPreferencesKey("emby_url")
        val houseMode = stringPreferencesKey("house_mode")
        val providers = stringPreferencesKey("providers")
        val haConfig = stringPreferencesKey("ha_config")
        val blankHours = intPreferencesKey("blank_hours")
        val blankWhenAway = intPreferencesKey("blank_when_away")
    }

    val flow: Flow<LauncherSettings> = context.dataStore.data.map { it.toSettings() }

    suspend fun current(): LauncherSettings = flow.first()

    suspend fun setFavorites(packages: List<String>) = context.dataStore.edit {
        it[Keys.favorites] = packages.distinct().joinToString(",")
    }

    suspend fun setIdleMinutes(minutes: Int) = context.dataStore.edit {
        it[Keys.idleMinutes] = minutes.coerceIn(0, 240)
    }

    suspend fun setHaUrl(url: String) = context.dataStore.edit { it[Keys.haUrl] = url.trim().trimEnd('/') }

    suspend fun setEmbyUrl(url: String) = context.dataStore.edit { it[Keys.embyUrl] = url.trim().trimEnd('/') }

    suspend fun setHouseMode(mode: HouseMode) = context.dataStore.edit { it[Keys.houseMode] = mode.name }

    suspend fun setBlankHours(hours: Int) = context.dataStore.edit { it[Keys.blankHours] = hours.coerceIn(0, 24) }

    suspend fun setBlankWhenAway(on: Boolean) = context.dataStore.edit { it[Keys.blankWhenAway] = if (on) 1 else 0 }

    suspend fun setHaConfig(config: HaConfig) = context.dataStore.edit { it[Keys.haConfig] = config.toJson() }

    suspend fun updateHaConfig(change: (HaConfig) -> HaConfig) = context.dataStore.edit { prefs ->
        prefs[Keys.haConfig] = change(HaConfig.fromJson(prefs[Keys.haConfig])).toJson()
    }

    /** Zapis jako „8:Netflix|337:Disney Plus”. */
    suspend fun setProviders(providers: List<StreamingProvider>) = context.dataStore.edit { prefs ->
        prefs[Keys.providers] = providers.joinToString("|") { "${it.id}:${it.name.replace("|", " ")}" }
    }

    private fun Preferences.toSettings() = LauncherSettings(
        favorites = this[Keys.favorites].orEmpty().split(',').filter { it.isNotBlank() },
        idleMinutes = this[Keys.idleMinutes] ?: 5,
        haUrl = this[Keys.haUrl].orEmpty(),
        embyUrl = this[Keys.embyUrl].orEmpty(),
        houseMode = runCatching { HouseMode.valueOf(this[Keys.houseMode] ?: "FULL") }.getOrDefault(HouseMode.FULL),
        providers = this[Keys.providers].orEmpty().split('|').mapNotNull { entry ->
            val id = entry.substringBefore(':').toIntOrNull() ?: return@mapNotNull null
            StreamingProvider(id, entry.substringAfter(':'))
        },
        ha = HaConfig.fromJson(this[Keys.haConfig]),
        blankHours = this[Keys.blankHours] ?: 3,
        blankWhenAway = (this[Keys.blankWhenAway] ?: 1) == 1,
    )
}
