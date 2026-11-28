package pl.dom.launcher.util

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dziennik problemów (awarie, błędy połączeń z HA/TMDB/Emby, aktualizacje, zwiastuny).
 * Ostatnie 50 wpisów na dysku – widać je na stronie w telefonie (Więcej → Ostatnie problemy).
 * Ten sam komunikat z tego samego źródła zapisujemy najwyżej raz na 10 minut.
 */
object ErrorLog {
    data class Entry(val at: Long, val source: String, val message: String)

    private const val MAX = 50
    private var file: File? = null
    private val entries = ArrayDeque<Entry>()
    private val lastSeen = HashMap<String, Long>()

    @Synchronized
    fun init(context: Context) {
        if (file != null) return
        file = File(context.filesDir, "errors.json")
        runCatching {
            val arr = JSONArray(file!!.readText())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                entries.addLast(Entry(o.getLong("at"), o.getString("source"), o.getString("message")))
            }
        }
    }

    @Synchronized
    fun add(source: String, message: String?) {
        val text = (message ?: "nieznany błąd").take(2000)
        val key = "$source|$text"
        val now = System.currentTimeMillis()
        if (now - (lastSeen[key] ?: 0) < 10 * 60_000L) return
        lastSeen[key] = now
        entries.addFirst(Entry(now, source, text))
        while (entries.size > MAX) entries.removeLast()
        save()
    }

    fun add(source: String, error: Throwable) =
        add(source, (error::class.java.simpleName + ": " + (error.message ?: "")).trim())

    @Synchronized
    fun all(): List<Entry> = entries.toList()

    @Synchronized
    fun clear() {
        entries.clear()
        lastSeen.clear()
        save()
    }

    private fun save() {
        val f = file ?: return
        runCatching {
            f.writeText(JSONArray(entries.map { JSONObject().put("at", it.at).put("source", it.source).put("message", it.message) }).toString())
        }
    }

    /** Krótki zapis awarii: typ, komunikat i kilka pierwszych linii stosu z kodu launchera. */
    fun crashText(e: Throwable): String {
        val lines = e.stackTrace.filter { it.className.startsWith("pl.dom") }.take(6).joinToString("\n") { "  at ${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" }
        val cause = e.cause?.let { "\nPrzyczyna: ${it::class.java.simpleName}: ${it.message}" } ?: ""
        return "${e::class.java.simpleName}: ${e.message}\n$lines$cause"
    }

    private val time = SimpleDateFormat("d.MM HH:mm", Locale.forLanguageTag("pl-PL"))
    fun format(at: Long): String = time.format(Date(at))
}
