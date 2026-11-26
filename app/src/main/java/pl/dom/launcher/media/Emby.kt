package pl.dom.launcher.media

import org.json.JSONObject

/**
 * Klient serwera Emby na NAS-ie (klucz API z panelu administracyjnego Emby).
 * Wszystkie metody blokują – wywoływać na Dispatchers.IO.
 */
class Emby(baseUrl: String, private val apiKey: String) {

    private val base = baseUrl.trim().trimEnd('/').removeSuffix("/emby")

    @Volatile
    private var userId: String? = null

    private fun url(path: String, params: Map<String, String> = emptyMap()): String {
        val query = (params + ("api_key" to apiKey)).entries.joinToString("&") { "${it.key}=${it.value.urlEncode()}" }
        return "$base/emby$path?$query"
    }

    /** Pierwszy aktywny użytkownik serwera (domowy NAS zwykle ma jednego). */
    private fun user(): String = userId ?: httpGetJsonArray(url("/Users"))
        .mapObjects { u ->
            val disabled = u.optJSONObject("Policy")?.optBoolean("IsDisabled") == true
            if (disabled) null else u.optString("Id")
        }
        .firstOrNull()
        ?.also { userId = it }
        ?: throw IllegalStateException("Emby: brak aktywnego użytkownika")

    /** Rozpoczęte filmy i odcinki („Kontynuuj”). */
    fun resume(): List<EmbyItem> =
        httpGetJson(url("/Users/${user()}/Items/Resume", mapOf("Limit" to "12", "Recursive" to "true", "MediaTypes" to "Video")))
            .optJSONArray("Items").mapObjects(::parse)

    /** Ostatnio dodane do biblioteki: filmy albo seriale. */
    fun latest(movies: Boolean): List<EmbyItem> =
        httpGetJsonArray(url("/Users/${user()}/Items/Latest", mapOf("Limit" to "16", "IncludeItemTypes" to if (movies) "Movie" else "Episode")))
            .mapObjects(::parse)

    private fun parse(o: JSONObject): EmbyItem {
        val id = o.optString("Id")
        val type = o.optString("Type")
        val isEpisode = type == "Episode"
        val title = if (isEpisode) o.optString("SeriesName").ifBlank { o.optString("Name") } else o.optString("Name")
        val subtitle = when {
            isEpisode -> "S%02dE%02d".format(o.optInt("ParentIndexNumber"), o.optInt("IndexNumber"))
            o.optInt("ProductionYear") > 0 -> o.optInt("ProductionYear").toString()
            else -> ""
        }
        val played = o.optJSONObject("UserData")?.optDouble("PlayedPercentage")?.takeIf { !it.isNaN() && it > 0 }
        // Dla odcinków kadr z odcinka, dla filmów i seriali – tło (16:9), a w ostateczności plakat.
        val hasBackdrop = (o.optJSONArray("BackdropImageTags")?.length() ?: 0) > 0
        val image = when {
            isEpisode -> url("/Items/$id/Images/Primary", mapOf("maxWidth" to "640"))
            hasBackdrop -> url("/Items/$id/Images/Backdrop", mapOf("maxWidth" to "780"))
            else -> url("/Items/$id/Images/Primary", mapOf("maxHeight" to "480"))
        }
        return EmbyItem(id, title, subtitle, image, played?.let { (it / 100.0).toFloat() }, isMovie = type == "Movie")
    }
}
