package pl.dom.launcher.media

enum class MediaType { MOVIE, TV }

/** Odcinek: sezon, numer i (opcjonalnie) data premiery w formacie RRRR-MM-DD. */
data class Episode(val season: Int, val number: Int, val airDate: String? = null) : Comparable<Episode> {
    override fun compareTo(other: Episode): Int =
        if (season != other.season) season.compareTo(other.season) else number.compareTo(other.number)

    val code: String get() = "S%02dE%02d".format(season, number)

    /** Po ludzku: „2. sezon, 5. odcinek”. */
    val label: String get() = "$season. sezon, $number. odcinek"

    /** Po „do”: „5. odcinka 2. sezonu”. */
    val labelUpTo: String get() = "$number. odcinka $season. sezonu"

    /** Krótko, gdy mało miejsca: „s. 2, odc. 5”. */
    val short: String get() = "s. $season, odc. $number"
}

/** „1 odcinek”, „3 odcinki”, „5 odcinków”. */
fun episodesLabel(n: Int): String = when {
    n == 1 -> "1 odcinek"
    n % 10 in 2..4 && n % 100 !in 12..14 -> "$n odcinki"
    else -> "$n odcinków"
}

/** „ok. 2 h 40 min”, „ok. 45 min”. */
fun durationLabel(minutes: Int): String {
    val m = ((minutes + 4) / 5) * 5
    return if (m >= 60) "ok. ${m / 60} h" + (if (m % 60 > 0) " ${m % 60} min" else "") else "ok. $m min"
}

/** Film albo serial z TMDB. */
data class MediaItem(
    val tmdbId: Int,
    val type: MediaType,
    val title: String,
    val posterPath: String?,
    val backdropPath: String?,
    val year: String?,
    val overview: String,
    val rating: Double?,
    /** Serwis, w którym jest dostępny (jeśli wiemy). */
    val provider: String? = null,
    /** Data premiery RRRR-MM-DD (film: kinowa, serial: pierwszy odcinek). */
    val releaseDate: String? = null,
)

/** Gdzie można obejrzeć tytuł: serwis (Wasz, a jak nie – jakikolwiek) i czy to jeden z Waszych. */
data class Availability(val provider: String?, val mine: Boolean)

/** Szczegóły serialu potrzebne do liczenia, co oglądać dalej. */
data class TvDetails(
    val tmdbId: Int,
    val name: String,
    val posterPath: String?,
    val backdropPath: String?,
    /** Liczba odcinków w każdym sezonie (bez sezonu 0 – odcinków specjalnych). */
    val seasons: Map<Int, Int>,
    val lastAired: Episode?,
    val nextAir: Episode?,
    val ended: Boolean,
    val firstAirDate: String? = null,
)

object TmdbImages {
    fun poster(path: String?) = path?.let { "https://image.tmdb.org/t/p/w342$it" }
    fun backdrop(path: String?) = path?.let { "https://image.tmdb.org/t/p/w780$it" }
    /** Tło na cały ekran (podgląd tytułu). */
    fun backdropLarge(path: String?) = path?.let { "https://image.tmdb.org/t/p/w1280$it" }
    fun still(path: String?) = path?.let { "https://image.tmdb.org/t/p/w300$it" }
    fun stillLarge(path: String?) = path?.let { "https://image.tmdb.org/t/p/w780$it" }
}

/** Wszystko do ekranu podglądu (jak w Netflixie). */
data class MediaDetails(
    val item: MediaItem,
    val tagline: String?,
    val genres: List<String>,
    /** Film: długość w minutach. */
    val runtime: Int?,
    /** Serial: numery sezonów z liczbą odcinków (bez odcinków specjalnych). */
    val seasons: Map<Int, Int>,
    /** Rok premiery każdego sezonu (null = data nieznana). */
    val seasonYears: Map<Int, String?> = emptyMap(),
    /** Klucz filmu na YouTube ze zwiastunem. */
    val trailerKey: String?,
    val recommendations: List<MediaItem>,
    val cast: List<String>,
    /** Seria filmów (TMDB „collection”), do której należy film. */
    val collectionId: Int? = null,
    val collectionName: String? = null,
    /** Gdzie jeszcze można obejrzeć (Polska): abonament, wypożyczenie, kupno – (id serwisu, nazwa). */
    val offersFlatrate: List<Pair<Int, String>> = emptyList(),
    val offersRent: List<String> = emptyList(),
    val offersBuy: List<String> = emptyList(),
    /** „Dostępne też: …” – wyliczone dla Waszych serwisów. */
    val alsoAvailable: String? = null,
)

/** „Bo oglądaliście X” – propozycje na podstawie tytułu, który już znacie. */
data class Because(val seed: String, val items: List<MediaItem>)

/** Odcinek z listy sezonu. */
data class SeasonEpisode(
    val season: Int,
    val number: Int,
    val name: String,
    val airDate: String?,
    val overview: String,
    val stillPath: String?,
    val runtime: Int?,
)

/** Pozycja z biblioteki Emby (NAS). */
data class EmbyItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val imageUrl: String?,
    val progress: Float?,
    val isMovie: Boolean,
)
