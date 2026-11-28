package pl.dom.launcher.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import pl.dom.launcher.BuildConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(val versionCode: Int, val versionName: String, val apkUrl: String, val notes: String)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState
    data object Installing : UpdateState
    data class Failed(val message: String) : UpdateState
}

/**
 * Aktualizacje „OTA”: GitHub Actions publikuje każde zbudowanie jako wydanie
 * z tagiem build-<numer> i plikiem dom-launcher.apk. Launcher porównuje numer
 * z własnym versionCode, pobiera APK i prosi system o instalację.
 */
class Updater(private val context: Context) {

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    @Volatile
    private var lastCheckAt = 0L

    val currentVersionName: String get() = BuildConfig.VERSION_NAME

    /** Sprawdza najwyżej co kilka godzin (wywoływane przy powrocie na ekran główny). */
    suspend fun checkIfDue() {
        if (System.currentTimeMillis() - lastCheckAt > CHECK_INTERVAL_MS) check()
    }

    suspend fun check() {
        val current = _state.value
        if (current is UpdateState.Downloading || current is UpdateState.Installing) return
        _state.value = UpdateState.Checking
        _state.value = withContext(Dispatchers.IO) {
            runCatching {
                lastCheckAt = System.currentTimeMillis()
                val info = latestRelease()
                if (info != null && info.versionCode > BuildConfig.VERSION_CODE) UpdateState.Available(info)
                else UpdateState.UpToDate
            }.getOrElse {
                pl.dom.launcher.util.ErrorLog.add("Aktualizacja", "Sprawdzanie: ${it.message}")
                UpdateState.Failed("Nie udało się sprawdzić aktualizacji: ${it.message}")
            }
        }
    }

    /** Czy system pozwala launcherowi instalować aplikacje (jednorazowa zgoda użytkownika). */
    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Otwiera ekran systemu, na którym zezwala się launcherowi na instalowanie aktualizacji. */
    fun openInstallPermissionSettings() {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }.onFailure {
            runCatching {
                context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }

    suspend fun downloadAndInstall(info: UpdateInfo) {
        _state.value = UpdateState.Downloading(info, 0f)
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val apk = download(info)
                _state.value = UpdateState.Installing
                install(apk)
            }
        }
        result.onFailure {
            pl.dom.launcher.util.ErrorLog.add("Aktualizacja", "Pobieranie/instalacja: ${it.message}")
            _state.value = UpdateState.Failed("Aktualizacja nie powiodła się: ${it.message}")
        }
    }

    internal fun onInstallResult(success: Boolean, message: String?) {
        _state.value = if (success) UpdateState.UpToDate else UpdateState.Failed(message ?: "Instalacja przerwana")
    }

    private fun latestRelease(): UpdateInfo? {
        val conn = (URL("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest").openConnection() as HttpURLConnection).apply {
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "DomLauncher")
            connectTimeout = 10_000
            readTimeout = 10_000
        }
        try {
            if (conn.responseCode == 404) return null
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val tag = json.optString("tag_name")
            val code = tag.substringAfter("build-", "").toIntOrNull() ?: return null
            val assets = json.optJSONArray("assets") ?: return null
            val apkUrl = (0 until assets.length())
                .map { assets.getJSONObject(it) }
                .firstOrNull { it.optString("name").endsWith(".apk") }
                ?.optString("browser_download_url") ?: return null
            return UpdateInfo(code, json.optString("name", tag), apkUrl, json.optString("body"))
        } finally {
            conn.disconnect()
        }
    }

    private fun download(info: UpdateInfo): File {
        val target = File(context.cacheDir, "update.apk")
        val conn = (URL(info.apkUrl).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "DomLauncher")
            connectTimeout = 15_000
            readTimeout = 30_000
        }
        try {
            val total = conn.contentLengthLong.takeIf { it > 0 }
            conn.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        if (total != null) _state.value = UpdateState.Downloading(info, done.toFloat() / total)
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        return target
    }

    private fun install(apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            .apply { setAppPackageName(context.packageName) }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("dom-launcher.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val intent = Intent(context, UpdateReceiver::class.java)
            val pending = PendingIntent.getBroadcast(
                context, sessionId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(pending.intentSender)
        }
    }

    companion object {
        private const val CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L
    }
}
