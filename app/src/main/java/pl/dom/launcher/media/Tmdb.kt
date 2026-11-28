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
            firstAirDate = o.optStringOrNull("first_air_date"),
        )
    }

    fun details(type: MediaType, id: Int): MediaDetails {
        val o = get(
            if (type == MediaType.MOVIE) "/movie/$id" else "/tv/$id",
            mapOf("append_to_response" to "videos,recommendations,credits,watch/providers", "include_video_language" to "pl,en,null"),
        )
        val videos = o.optJSONObject("videos")?.optJSONArray("results").mapObjects { v ->
            if (v.optString("site") == "YouTube") v else null
        }
        fun rank(v: JSONObject): Int {
            val typeRank = when (v.optString("type")) { "Trailer" -> 0; "Teaser" -> 2; else -> 4 }
            val langRank = when (v.optString("iso_639_1")) { "pl" -> 0; "en" -> 1; else -> 2 }
            return typeRank * 3 + langRank
        }
        val trailer = videos.filter { it.optString("type") in setOf("Trailer", "Teaser") }.minByOrNull(::rank)
            ?: videos.firstOrNull()
        // Serial bez polskiego zwiastuna: sprawdź jeszcze najnowszy (już wyemitowany) sezon.
        val seasonPl = if (type == MediaType.TV && trailer?.optString("iso_639_1") != "pl") {
            val today = java.time.LocalDate.now().toString()
            val latest = o.optJSONArray("seasons").mapObjects { s ->
                val n = s.optInt("season_number")
                val date = s.optStringOrNull("air_date")
                if (n > 0 && date != null && date <= today) n else null
            }.maxOrNull()
            latest?.let { runCatching { seasonTrailerPl(id, it) }.getOrNull() }
        } else null
        return MediaDetails(
            item = parseItem(o, type),
            tagline = o.optString("tagline").takeIf { it.isNotBlank() },
            genres = o.optJSONArray("genres").mapObjects { it.optString("name").takeIf { n -> n.isNotBlank() } },
            runtime = o.optInt("runtime", 0).takeIf { it > 0 },
            seasons = if (type == MediaType.TV) o.optJSONArray("seasons").mapObjects { s ->
                val number = s.optInt("season_number")
                val count = s.optInt("episode_count")
                if (number > 0 && count > 0) number to count else null
            }.toMap() else emptyMap(),
            seasonYears = if (type == MediaType.TV) o.optJSONArray("seasons").mapObjects { s ->
                val number = s.optInt("season_number")
                if (number > 0) number to s.optStringOrNull("air_date") else null
            }.toMap() else emptyMap(),
            trailerKey = seasonPl ?: trailer?.optString("key")?.takeIf { it.isNotBlank() },
            recommendations = o.optJSONObject("recommendations")?.optJSONArray("results")
                .mapObjects { r ->
                    when (r.optString("media_type")) {
                        "movie" -> parseItem(r, MediaType.MOVIE)
                        "tv" -> parseItem(r, MediaType.TV)
                        else -> parseItem(r, type)
                    }
                }.take(20),
            cast = o.optJSONObject("credits")?.optJSONArray("cast").mapObjects { it.optString("name").takeIf { n -> n.isNotBlank() } }.take(6),
            collectionId = o.optJSONObject("belongs_to_collection")?.optInt("id")?.takeIf { it > 0 },
            collectionName = o.optJSONObject("belongs_to_collection")?.optString("name")?.takeIf { it.isNotBlank() },
            offersFlatrate = offers(o, "flatrate").map { it.first to it.second } + offers(o, "free") + offers(o, "ads"),
            offersRent = offers(o, "rent").map { it.second },
            offersBuy = offers(o, "buy").map { it.second },
        )
    }

    private fun offers(o: JSONObject, kind: String): List<Pair<Int, String>> =
        o.optJSONObject("watch/providers")?.optJSONObject("results")?.optJSONObject(REGION)?.optJSONArray(kind)
            .mapObjects { p -> p.optInt("provider_id") to p.optString("provider_name") }
            .filter { it.second.isNotBlank() }

    /** Wszystkie filmy serii, po kolei (od najstarszego). */
    fun collection(id: Int): Pair<String, List<MediaItem>> {
        val o = get("/collection/$id")
        val parts = o.optJSONArray("parts").mapObjects { parseItem(it, MediaType.MOVIE) }
            .sortedBy { it.releaseDate ?: "9999" }
        return o.optString("name") to parts
    }

    /** Propozycje TMDB „podobne do” danego tytułu. */
    fun recommendations(type: MediaType, id: Int): List<MediaItem> =
        get(if (type == MediaType.MOVIE) "/movie/$id/recommendations" else "/tv/$id/recommendations")
            .optJSONArray("results").mapObjects { parseItem(it, type) }

    /** Najwyżej oceniona polska okładka z TMDB (null = brak polskiej). */
    fun polishPoster(type: MediaType, id: Int): String? {
        val path = if (type == MediaType.MOVIE) "/movie/$id/images" else "/tv/$id/images"
        val posters = get(path, mapOf("include_image_language" to "pl")).optJSONArray("posters")
            .mapObjects { p -> if (p.optString("iso_639_1") == "pl") p else null }
        return posters.maxByOrNull { it.optDouble("vote_average", 0.0) * 1000 + it.optInt("vote_count") }
            ?.optString("file_path")?.takeIf { it.isNotBlank() }
    }

    /** Polski zwiastun najnowszego sezonu serialu (TMDB często trzyma je przy sezonie, nie przy serialu). */
    fun seasonTrailerPl(id: Int, season: Int): String? =
        get("/tv/$id/season/$season/videos", mapOf("include_video_language" to "pl")).optJSONArray("results")
            .mapObjects { v -> if (v.optString("site") == "YouTube" && v.optString("iso_639_1") == "pl") v else null }
            .sortedBy { when (it.optString("type")) { "Trailer" -> 0; "Teaser" -> 1; else -> 2 } }
            .firstOrNull()?.optString("key")?.takeIf { it.isNotBlank() }

    fun season(id: Int, season: Int): List<SeasonEpisode> =
        get("/tv/$id/season/$season").optJSONArray("episodes").mapObjects { e ->
            SeasonEpisode(
                season = season,
                number = e.optInt("episode_number"),
                name = e.optString("name"),
                airDate = e.optStringOrNull("air_date"),
                overview = e.optString("overview"),
                stillPath = e.optStringOrNull("still_path"),
                runtime = e.optInt("runtime", 0).takeIf { it > 0 },
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
                // Bez starych tytułów: filmy od 2015, seriale z odcinkami wyemitowanymi od 2015
                // (stary serial z nowym sezonem się pokaże).
                (if (type == MediaType.MOVIE) "primary_release_date.gte" else "air_date.gte") to MIN_DATE,
                "page" to page.toString(),
            ),
        ).optJSONArray("results").mapObjects { parseItem(it, type).copy(provider = provider.name) }

    /** W którym z Waszych serwisów jest dany tytuł (abonament, Polska). */
    fun providerFor(type: MediaType, id: Int, mine: List<StreamingProvider>): String? = availability(type, id, mine).provider

    fun availability(type: MediaType, id: Int, mine: List<StreamingProvider>): Availability {
        val path = if (type == MediaType.MOVIE) "/movie/$id/watch/providers" else "/tv/$id/watch/providers"
        val flatrate = get(path).optJSONObject("results")?.optJSONObject(REGION)?.optJSONArray("flatrate")
        val available = flatrate.mapObjects { it.optInt("provider_id") to it.optString("provider_name") }
        val mineIds = mine.map { it.id }.toSet()
        val ours = available.firstOrNull { it.first in mineIds }
        return if (ours != null) Availability(ours.second, true) else Availability(available.firstOrNull()?.second, false)
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
        releaseDate = (if (type == MediaType.MOVIE) o.optString("release_date") else o.optString("first_air_date")).ifBlank { null },
    )

    private fun parseEpisode(o: JSONObject) =
        Episode(o.optInt("season_number"), o.optInt("episode_number"), o.optStringOrNull("air_date"))

    companion object {
        const val REGION = "PL"
        const val MIN_DATE = "2015-01-01"
    }
}
