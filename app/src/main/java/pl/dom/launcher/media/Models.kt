package pl.dom.launcher.media

enum class MediaType { MOVIE, TV }

/** Odcinek: sezon, numer i (opcjonalnie) data premiery w formacie RRRR-MM-DD. */
data class Episode(val season: Int, val number: Int, val airDate: String? = null) : Comparable<Episode> {
    override fun compareTo(other: Episode): Int =
        if (season != other.season) season.compareTo(other.season) else number.compareTo(other.number)

    val code: String get() = "S%02dE%02d".format(season, number)
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
)

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
)

object TmdbImages {
    fun poster(path: String?) = path?.let { "https://image.tmdb.org/t/p/w342$it" }
    fun backdrop(path: String?) = path?.let { "https://image.tmdb.org/t/p/w780$it" }
    /** Tło na cały ekran (podgląd tytułu). */
    fun backdropLarge(path: String?) = path?.let { "https://image.tmdb.org/t/p/w1280$it" }
    fun still(path: String?) = path?.let { "https://image.tmdb.org/t/p/w300$it" }
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
    /** Klucz filmu na YouTube ze zwiastunem. */
    val trailerKey: String?,
    val recommendations: List<MediaItem>,
    val cast: List<String>,
)

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
