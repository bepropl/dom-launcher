package pl.dom.launcher.media

import org.json.JSONObject
import pl.dom.launcher.data.StreamingProvider

class MissingKeyException : Exception("Brak klucza TMDB – wpisz go na stronie konfiguracji w telefonie.")

/**
 * Klient TMDB (themoviedb.org). Przyjmuje klucz API v3 albo token v4 („Read Access Token”).
 * Wszystkie metody blokują – wywoływać na Dispatchers.IO.
 */
class Tmdb(private val key: () -> String?) {

    private fun get(path: String, params: Map<String, String> = emptyMap()): JSONObject {
        val k = key()?.trim().takeUnless { it.isNullOrEmpty() } ?: throw MissingKeyException()
        val bearer = k.length > 40
        val query = (mapOf("language" to "pl-PL") + params + (if (bearer) emptyMap() else mapOf("api_key" to k)))
            .entries.joinToString("&") { "${it.key}=${it.value.urlEncode()}" }
        val headers = if (bearer) mapOf("Authorization" to "Bearer $k") else emptyMap()
        return httpGetJson("https://api.themoviedb.org/3$path?$query", headers)
    }

    fun search(text: String): List<MediaItem> =
        get("/search/multi", mapOf("query" to text, "include_adult" to "false"))
            .optJSONArray("results")
            .mapObjects { o ->
                when (o.optString("media_type")) {
                    "movie" -> parseItem(o, MediaType.MOVIE)
                    "tv" -> parseItem(o, MediaType.TV)
                    else -> null
                }
            }

    fun tvDetails(id: Int): TvDetails {
        val o = get("/tv/$id")
        val seasons = o.optJSONArray("seasons").mapObjects { s ->
            val number = s.optInt("season_number")
            val count = s.optInt("episode_count")
            if (number > 0 && count > 0) number to count else null
        }.toMap()
        return TvDetails(
            tmdbId = id,
            name = o.optString("name"),
            posterPath = o.optStringOrNull("poster_path"),
            backdropPath = o.optStringOrNull("backdrop_path"),
            seasons = seasons,
            lastAired = o.optJSONObject("last_episode_to_air")?.let(::parseEpisode),
            nextAir = o.optJSONObject("next_episode_to_air")?.let(::parseEpisode),
            ended = o.optString("status") in setOf("Ended", "Canceled"),
        )
    }

    fun item(type: MediaType, id: Int): MediaItem =
        parseItem(get(if (type == MediaType.MOVIE) "/movie/$id" else "/tv/$id"), type)

    /** Popularne filmy/seriale dostępne w danym serwisie w Polsce. */
    fun discover(type: MediaType, provider: StreamingProvider, page: Int = 1): List<MediaItem> =
        get(
            if (type == MediaType.MOVIE) "/discover/movie" else "/discover/tv",
            mapOf(
                "with_watch_providers" to provider.id.toString(),
                "watch_region" to REGION,
                "with_watch_monetization_types" to "flatrate",
                "sort_by" to "popularity.desc",
                "page" to page.toString(),
            ),
        ).optJSONArray("results").mapObjects { parseItem(it, type).copy(provider = provider.name) }

    /** W którym z Waszych serwisów jest dany tytuł (abonament, Polska). */
    fun providerFor(type: MediaType, id: Int, mine: List<StreamingProvider>): String? {
        val path = if (type == MediaType.MOVIE) "/movie/$id/watch/providers" else "/tv/$id/watch/providers"
        val flatrate = get(path).optJSONObject("results")?.optJSONObject(REGION)?.optJSONArray("flatrate")
        val available = flatrate.mapObjects { it.optInt("provider_id") to it.optString("provider_name") }
        val mineIds = mine.map { it.id }.toSet()
        return (available.firstOrNull { it.first in mineIds } ?: available.firstOrNull())?.second
    }

    /** Lista serwisów streamingowych dostępnych w Polsce (do wyboru na stronie w telefonie). */
    fun providers(): List<StreamingProvider> =
        get("/watch/providers/movie", mapOf("watch_region" to REGION))
            .optJSONArray("results")
            .mapObjects { o ->
                val priority = o.optJSONObject("display_priorities")?.optInt(REGION, 999) ?: o.optInt("display_priority", 999)
                Triple(o.optInt("provider_id"), o.optString("provider_name"), priority)
            }
            .sortedBy { it.third }
            .map { StreamingProvider(it.first, it.second) }

    private fun parseItem(o: JSONObject, type: MediaType) = MediaItem(
        tmdbId = o.optInt("id"),
        type = type,
        title = if (type == MediaType.MOVIE) o.optString("title") else o.optString("name"),
        posterPath = o.optStringOrNull("poster_path"),
        backdropPath = o.optStringOrNull("backdrop_path"),
        year = (if (type == MediaType.MOVIE) o.optString("release_date") else o.optString("first_air_date")).take(4).ifBlank { null },
        overview = o.optString("overview"),
        rating = o.optDouble("vote_average").takeIf { !it.isNaN() && it > 0 },
    )

    private fun parseEpisode(o: JSONObject) =
        Episode(o.optInt("season_number"), o.optInt("episode_number"), o.optStringOrNull("air_date"))

    companion object {
        const val REGION = "PL"
    }
}
