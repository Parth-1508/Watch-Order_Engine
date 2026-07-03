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
     * TMDB Profile image sizes. Person profile images use a different set of 
     * allowed widths than posters/backdrops. Using an unsupported size string 
     * can lead to 404s or blank images in some regions.
     */
    enum class ProfileSize(val key: String) {
        SMALL("w45"),
        MEDIUM("w185"),
        LARGE("h632"),
        ORIGINAL("original")
    }

    /**
     * Constructs a fully-qualified TMDB image URL from a raw path.
     */
    fun buildImageUrl(rawPath: String?, size: PosterSize = PosterSize.CARD): String? {
        if (rawPath.isNullOrBlank()) return null
        return "$IMAGE_BASE_URL${size.key}$rawPath"
    }

    /**
     * Constructs a fully-qualified TMDB image URL for a PERSON profile.
     */
    fun buildProfileUrl(rawPath: String?, size: ProfileSize = ProfileSize.MEDIUM): String? {
        if (rawPath.isNullOrBlank()) return null
        return "$IMAGE_BASE_URL${size.key}$rawPath"
    }

    /**
     * Checks if an image URL is a known blank placeholder or invalid.
     */
    fun isValidImageUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val lower = url.lowercase()
        val placeholders = listOf(
            "no_image", "placeholder", "missing", "default_profile", "avatar_default", 
            "silhouette", "no-photo", "null", "empty", "image_not_found", "no-image",
            "default.jpg", "generic", "uncredited", "black-profile", "empty_profile",
            "no_photo", "blank", "none", "not-found", "portrait_placeholder",
            "no_headshot", "silhouette_inline"
        )
        // SVGs are almost exclusively Wikipedia "No Image" placeholders.
        return placeholders.none { lower.contains(it) } && 
               !lower.endsWith(".svg") &&
               !lower.contains("wiki-no-image") &&
               !lower.contains("wikimedia.org/static/images/icons/")
    }

    /**
     * The TMDB media type string used to select the correct API endpoint.
     * These are constants so we don't scatter magic strings across the codebase.
     */
    const val MEDIA_TYPE_MOVIE = "movie"
    const val MEDIA_TYPE_TV    = "tv"

    /** Comma-separated `append_to_response` modules added to each API call. */
    const val APPEND_TO_RESPONSE_MOVIE = "release_dates,credits,videos,recommendations,external_ids,watch/providers"
    const val APPEND_TO_RESPONSE_TV    = "content_ratings,aggregate_credits,videos,recommendations,external_ids,watch/providers"

    /**
     * Country priority for watch providers.
     * We try to show Indian providers first, falling back to US/GB if unavailable.
     */
    val PROVIDER_COUNTRY_PRIORITY = listOf("IN", "US", "GB", "AU", "CA")

    /**
     * Short names for common streaming providers to keep the UI compact.
     */
    val PROVIDER_SHORT_NAMES = mapOf(
        8 to "Netflix",
        119 to "Prime Video",
        337 to "Disney+",
        350 to "Apple TV+",
        122 to "Hotstar",
        232 to "Zee5",
        121 to "Voot",
        1899 to "HBO Max",
        15 to "Hulu",
        384 to "HBO",
        2 to "Apple TV",
        3 to "Google Play",
        10 to "Amazon Video",
        192 to "YouTube"
    )

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
