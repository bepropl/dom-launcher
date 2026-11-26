package pl.dom.launcher.media

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class HttpException(val code: Int, message: String) : Exception(message)

/** Proste zapytanie GET zwracające tekst. Wywoływać poza wątkiem UI. */
internal fun httpGet(url: String, headers: Map<String, String> = emptyMap()): String {
    val conn = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 10_000
        readTimeout = 15_000
        setRequestProperty("Accept", "application/json")
        setRequestProperty("User-Agent", "DomLauncher")
        headers.forEach { (k, v) -> setRequestProperty(k, v) }
    }
    try {
        val code = conn.responseCode
        if (code !in 200..299) throw HttpException(code, "HTTP $code")
        return conn.inputStream.bufferedReader().use { it.readText() }
    } finally {
        conn.disconnect()
    }
}

internal fun httpGetJson(url: String, headers: Map<String, String> = emptyMap()) = JSONObject(httpGet(url, headers))

internal fun httpGetJsonArray(url: String, headers: Map<String, String> = emptyMap()) = JSONArray(httpGet(url, headers))

internal fun String.urlEncode(): String = URLEncoder.encode(this, "UTF-8")

internal fun JSONObject.optStringOrNull(name: String): String? =
    if (isNull(name)) null else optString(name).takeIf { it.isNotBlank() }

internal inline fun <T> JSONArray?.mapObjects(transform: (JSONObject) -> T?): List<T> {
    if (this == null) return emptyList()
    val out = ArrayList<T>(length())
    for (i in 0 until length()) optJSONObject(i)?.let(transform)?.let(out::add)
    return out
}
