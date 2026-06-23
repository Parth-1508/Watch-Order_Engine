package com.example.watchorderengine.network

/**
 * Central configuration for all TMDB API and image constants.
 * One place to update if TMDB changes its CDN or API version.
 */
object TmdbConfig {

    /** Retrofit base URL — MUST end with a trailing slash. */
    const val BASE_URL = "https://api.themoviedb.org/3/"

    /**
     * TMDB image CDN base URL. Append a [PosterSize] and then the raw
     * `poster_path` value from the API (which already starts with '/').
     * Example: "https://image.tmdb.org/t/p/w342/7WsyChQLEftFiDOVTGkv3hFpyyt.jpg"
     */
    private const val IMAGE_BASE_URL = "https://image.tmdb.org/t/p/"

    /**
     * Standardized poster image sizes from the TMDB configuration API.
     * Use [CARD] for node cards in the timeline (good quality, reasonable size).
     * Use [HD] for the detail screen backdrop.
     */
    enum class PosterSize(val key: String) {
        THUMBNAIL("w92"),
        SMALL("w185"),
        CARD("w342"),
        LARGE("w500"),
        HD("w780"),
        ORIGINAL("original")
    }

    /**
     * Constructs a fully-qualified TMDB image URL from a raw path.
     *
     * @param rawPath  The `poster_path` or `backdrop_path` from the TMDB response,
     *                 e.g., "/7WsyChQLEftFiDOVTGkv3hFpyyt.jpg". May be null if
     *                 TMDB has no image for this entry.
     * @param size     The desired image resolution. Defaults to [PosterSize.CARD].
     * @return A complete URL string, or null if [rawPath] was null or blank.
     */
    fun buildImageUrl(rawPath: String?, size: PosterSize = PosterSize.CARD): String? {
        if (rawPath.isNullOrBlank()) return null
        // rawPath already starts with '/', so no separator is needed.
        return "$IMAGE_BASE_URL${size.key}$rawPath"
    }

    /**
     * The TMDB media type string used to select the correct API endpoint.
     * These are constants so we don't scatter magic strings across the codebase.
     */
    const val MEDIA_TYPE_MOVIE = "movie"
    const val MEDIA_TYPE_TV    = "tv"

    /** Comma-separated `append_to_response` modules added to each API call. */
    const val APPEND_TO_RESPONSE_MOVIE = "release_dates,credits,videos,recommendations,external_ids"
    const val APPEND_TO_RESPONSE_TV    = "content_ratings,aggregate_credits,videos,recommendations,external_ids"

    /**
     * Official TMDB genre ID → name maps. List endpoints (search, trending,
     * discover) only return numeric `genre_ids`, not names — only the detail
     * endpoints return full {id, name} objects. These are TMDB's published,
     * stable genre lists (https://developer.themoviedb.org/reference/genre-movie-list
     * and .../genre-tv-list), so hardcoding them here avoids an extra network
     * round-trip just to label a discovery card.
     */
    val MOVIE_GENRES: Map<Int, String> = mapOf(
        28 to "Action", 12 to "Adventure", 16 to "Animation", 35 to "Comedy",
        80 to "Crime", 99 to "Documentary", 18 to "Drama", 10751 to "Family",
        14 to "Fantasy", 36 to "History", 27 to "Horror", 10402 to "Music",
        9648 to "Mystery", 10749 to "Romance", 878 to "Science Fiction",
        10770 to "TV Movie", 53 to "Thriller", 10752 to "War", 37 to "Western"
    )

    val TV_GENRES: Map<Int, String> = mapOf(
        10759 to "Action & Adventure", 16 to "Animation", 35 to "Comedy",
        80 to "Crime", 99 to "Documentary", 18 to "Drama", 10751 to "Family",
        10762 to "Kids", 9648 to "Mystery", 10763 to "News", 10764 to "Reality",
        10765 to "Sci-Fi & Fantasy", 10766 to "Soap", 10767 to "Talk",
        10768 to "War & Politics", 37 to "Western"
    )

    /** Resolves genre IDs to display names for either movies or TV, deduplicating overlapping names. */
    fun genreNamesFor(genreIds: List<Int>?, isMovie: Boolean): List<String> {
        if (genreIds.isNullOrEmpty()) return emptyList()
        val map = if (isMovie) MOVIE_GENRES else TV_GENRES
        return genreIds.mapNotNull { map[it] }
    }

    /**
     * Curated set of categories shown as filter chips on the Discovery
     * screen. Maps a user-facing label to the TMDB genre ID used with the
     * `/discover` endpoint's `with_genres` parameter. Movie and TV share IDs
     * for most of these (Action/Adventure differs — TMDB's TV catalog only
     * has the combined "Action & Adventure" id 10759, while movies have
     * separate Action=28/Adventure=12), so each entry carries both ids.
     */
    data class DiscoveryCategory(val label: String, val movieGenreId: Int, val tvGenreId: Int)

    val DISCOVERY_CATEGORIES: List<DiscoveryCategory> = listOf(
        DiscoveryCategory("Action", 28, 10759),
        DiscoveryCategory("Comedy", 35, 35),
        DiscoveryCategory("Horror", 27, 9648),   // TV has no Horror genre; Mystery is the closest analogue
        DiscoveryCategory("Drama", 18, 18),
        DiscoveryCategory("Romance", 10749, 10749),
        DiscoveryCategory("Sci-Fi", 878, 10765),
        DiscoveryCategory("Animation", 16, 16),
        DiscoveryCategory("Fantasy", 14, 10765),
        DiscoveryCategory("Thriller", 53, 9648)
    )
}
