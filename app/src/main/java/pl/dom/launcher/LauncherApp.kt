package pl.dom.launcher

import android.app.Application
import android.content.Context
import pl.dom.launcher.data.AppsRepository
import pl.dom.launcher.data.SecretStore
import pl.dom.launcher.data.SettingsRepository
import pl.dom.launcher.update.Updater
import pl.dom.launcher.media.MediaRepository
import pl.dom.launcher.ha.HomeAssistant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Wspólne obiekty dla całej aplikacji: ustawienia, zaszyfrowane klucze i lista aplikacji. */
class LauncherApp : Application() {
    lateinit var settings: SettingsRepository
        private set
    lateinit var secrets: SecretStore
        private set
    lateinit var apps: AppsRepository
        private set
    lateinit var updater: Updater
        private set
    lateinit var media: MediaRepository
        private set
    lateinit var ha: HomeAssistant
        private set

    /** Zadania w tle niezależne od ekranu (np. odświeżenie po zmianach z telefonu). */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        settings = SettingsRepository(this)
        secrets = SecretStore(this)
        apps = AppsRepository(this)
        updater = Updater(this)
        media = MediaRepository(this, settings, secrets)
        ha = HomeAssistant(settings, secrets, scope).also { it.start() }
    }

    companion object {
        fun from(context: Context): LauncherApp = context.applicationContext as LauncherApp
    }
}
