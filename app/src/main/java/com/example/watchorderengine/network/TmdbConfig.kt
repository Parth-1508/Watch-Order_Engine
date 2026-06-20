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
}
