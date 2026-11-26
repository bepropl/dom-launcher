package pl.dom.launcher

import android.app.Application
import android.content.Context
import pl.dom.launcher.data.AppsRepository
import pl.dom.launcher.data.SecretStore
import pl.dom.launcher.data.SettingsRepository
import pl.dom.launcher.update.Updater

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

    override fun onCreate() {
        super.onCreate()
        settings = SettingsRepository(this)
        secrets = SecretStore(this)
        apps = AppsRepository(this)
        updater = Updater(this)
    }

    companion object {
        fun from(context: Context): LauncherApp = context.applicationContext as LauncherApp
    }
}
