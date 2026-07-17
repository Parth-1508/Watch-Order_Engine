package com.example.watchorderengine.network

import com.example.watchorderengine.network.model.TmdbDetailResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit service interface for the TMDB v3 API.
 *
 * AUTHENTICATION: All requests are authenticated via the
 * [TmdbAuthInterceptor] injected into OkHttp — no per-call
 * API key parameters are needed here. The interceptor adds:
 *   Authorization: Bearer {TMDB_READ_ACCESS_TOKEN}
 *   Accept: application/json
 *
 * BASE URL: https://api.themoviedb.org/3/
 * (Set in [NetworkModule] — endpoint paths must NOT start with '/')
 */
interface TmdbApiService {

    /**
     * Fetches full metadata for a single movie.
     *
     * Endpoint: GET /3/movie/{movie_id}
     * Docs: https://developer.themoviedb.org/reference/movie-details
     *
     * We use [Response] wrapper (not a bare [TmdbMovieResponse]) so the repository
     * can inspect the HTTP status code and distinguish "not found" (404)
     * from "rate limited" (429) from "server error" (5xx).
     *
     * @param movieId The TMDB numeric movie ID (e.g., 299536 for Infinity War).
     * @param language BCP 47 language tag. Defaults to English.
     * @param appendToResponse Comma-separated extra data modules to embed in the
     *                         response, reducing round-trips. We fetch release_dates
     *                         to display content ratings (PG, PG-13, etc.).
     */
    @GET("movie/{movieId}")
    suspend fun getMovie(
        @Path("movieId")          movieId: Int,
        @Query("language")        language: String = "en-US",
        @Query("append_to_response") appendToResponse: String = TmdbConfig.APPEND_TO_RESPONSE_MOVIE
    ): Response<TmdbDetailResponse>

    /**
     * Fetches full metadata for a single TV show.
     * Uses aggregate_credits for TV (better for multi-season shows than credits).
     */
    @GET("tv/{tvId}")
    suspend fun getTvShow(
        @Path("tvId")             tvId: Int,
        @Query("language")        language: String = "en-US",
        @Query("append_to_response") appendToResponse: String = TmdbConfig.APPEND_TO_RESPONSE_TV
    ): Response<TmdbDetailResponse>

    /**
     * Fetches full season details including all episodes.
     * Called per-season to populate the episodes tab in Detail screen.
     *
     * Endpoint: GET /3/tv/{series_id}/season/{season_number}
     * Docs: https://developer.themoviedb.org/reference/tv-season-details
     */
    @GET("tv/{tvId}/season/{seasonNumber}")
    suspend fun getTvSeason(
        @Path("tvId")           tvId: Int,
        @Path("seasonNumber")   seasonNumber: Int,
        @Query("language")      language: String = "en-US"
    ): Response<com.example.watchorderengine.network.model.TmdbSeasonDetail>

    /**
     * Fetches trending media.
     */
    @GET("trending/all/day")
    suspend fun getTrending(
        @Query("language") language: String = "en-US",
        @Query("page") page: Int = 1
    ): Response<com.example.watchorderengine.network.model.TmdbPagedResults<com.example.watchorderengine.network.model.TmdbMediaResult>>

    /**
     * Searches for movies and TV shows.
     */
    @GET("search/multi")
    suspend fun searchMulti(
        @Query("query") query: String,
        @Query("language") language: String = "en-US",
        @Query("page") page: Int = 1,
        @Query("include_adult") includeAdult: Boolean = false
    ): Response<com.example.watchorderengine.network.model.TmdbPagedResults<com.example.watchorderengine.network.model.TmdbMediaResult>>

    /**
     * Fetches full person details (biography, filmography, images).
     * Used by Character screen.
     *
     * Endpoint: GET /3/person/{person_id}
     * Docs: https://developer.themoviedb.org/reference/person-details
     */
    @GET("person/{personId}")
    suspend fun getPersonDetail(
        @Path("personId") personId: Int,
        @Query("language") language: String = "en-US",
        @Query("append_to_response") appendToResponse: String = "combined_credits,images,external_ids"
    ): Response<com.example.watchorderengine.network.model.TmdbPersonDetail>

    /**
     * Fetches person details (for character biographies).
     */
    @GET("person/{personId}")
    suspend fun getPerson(
        @Path("personId") personId: Int,
        @Query("language") language: String = "en-US"
    ): Response<com.example.watchorderengine.network.model.TmdbPersonDetail>

    /**
     * Discovers movies matching a genre, sorted by popularity. Used by the
     * Discovery screen's category chips (Horror, Comedy, etc.) — list/search/
     * trending endpoints don't support genre filtering server-side, only
     * `/discover` does.
     *
     * Endpoint: GET /3/discover/movie
     * Docs: https://developer.themoviedb.org/reference/discover-movie
     */
    @GET("discover/movie")
    suspend fun discoverMovies(
        @Query("with_genres") genreId: String? = null,
        @Query("with_watch_providers") providerIds: String? = null,
        @Query("watch_region") watchRegion: String? = "IN",
        @Query("language") language: String = "en-US",
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("page") page: Int = 1,
        @Query("primary_release_date.gte") releaseDateGte: String? = null,
        @Query("primary_release_date.lte") releaseDateLte: String? = null,
        @Query("include_adult") includeAdult: Boolean = false
    ): Response<com.example.watchorderengine.network.model.TmdbPagedResults<com.example.watchorderengine.network.model.TmdbMediaResult>>

    /**
     * Discovers TV shows matching a genre, sorted by popularity.
     *
     * Endpoint: GET /3/discover/tv
     * Docs: https://developer.themoviedb.org/reference/discover-tv
     */
    @GET("discover/tv")
    suspend fun discoverTvShows(
        @Query("with_genres") genreId: String? = null,
        @Query("with_watch_providers") providerIds: String? = null,
        @Query("watch_region") watchRegion: String? = "IN",
        @Query("language") language: String = "en-US",
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("page") page: Int = 1,
        @Query("first_air_date.gte") airDateGte: String? = null,
        @Query("first_air_date.lte") airDateLte: String? = null
    ): Response<com.example.watchorderengine.network.model.TmdbPagedResults<com.example.watchorderengine.network.model.TmdbMediaResult>>

    /**
     * Fetches watch providers for a movie.
     */
    @GET("movie/{movieId}/watch/providers")
    suspend fun getMovieWatchProviders(@Path("movieId") movieId: Int): Response<com.example.watchorderengine.network.model.TmdbWatchProvidersResponse>

    /**
     * Fetches watch providers for a TV show.
     */
    @GET("tv/{tvId}/watch/providers")
    suspend fun getTvWatchProviders(@Path("tvId") tvId: Int): Response<com.example.watchorderengine.network.model.TmdbWatchProvidersResponse>

    /**
     * Fetches all parts of a TMDB movie collection (franchise).
     *
     * Endpoint: GET /3/collection/{collection_id}
     * Docs: https://developer.themoviedb.org/reference/collection-details
     *
     * Used by [MediaRepository.generateWatchOrder] to expand a single movie
     * into its full franchise tree before passing the list to Gemini.
     * e.g. movie 862 ("Toy Story") → collection 10194 → 4 movies returned.
     */
    @GET("collection/{collectionId}")
    suspend fun getMovieCollection(
        @Path("collectionId") collectionId: Int,
        @Query("language")    language: String = "en-US"
    ): Response<com.example.watchorderengine.network.model.TmdbCollectionResponse>

    /**
     * Fetches user reviews for a movie.
     */
    @GET("movie/{movieId}/reviews")
    suspend fun getMovieReviews(
        @Path("movieId")  movieId: Int,
        @Query("language") language: String = "en-US",
        @Query("page")     page: Int = 1
    ): Response<com.example.watchorderengine.network.model.TmdbPagedResults<com.example.watchorderengine.network.model.TmdbReview>>

    /**
     * Fetches user reviews for a TV show.
     */
    @GET("tv/{tvId}/reviews")
    suspend fun getTvReviews(
        @Path("tvId")     tvId: Int,
        @Query("language") language: String = "en-US",
        @Query("page")     page: Int = 1
    ): Response<com.example.watchorderengine.network.model.TmdbPagedResults<com.example.watchorderengine.network.model.TmdbReview>>

    /**
     * Searches for TV shows by keyword.
     *
     * Used by generateWatchOrder's TV branch to resolve title variants
     * (e.g., "Naruto" → surfaces "Naruto" AND "Naruto: Shippuden" as separate
     * TV entries) before passing the combined season list to Gemini.
     *
     * Endpoint: GET /3/search/tv
     * Docs: https://developer.themoviedb.org/reference/search-tv
     *
     * @param query  Stripped base title ("Naruto", "Fate", "Dragon Ball")
     * @param page   Page 1 returns up to 20 results; we only need the top 5.
     */
    @GET("search/tv")
    suspend fun searchTv(
        @Query("query")          query: String,
        @Query("language")       language: String = "en-US",
        @Query("page")           page: Int = 1,
        @Query("include_adult")  includeAdult: Boolean = false
    ): Response<com.example.watchorderengine.network.model.TmdbPagedResults<com.example.watchorderengine.network.model.TmdbMediaResult>>

    /**
     * Searches for movies by keyword.
     */
    @GET("search/movie")
    suspend fun searchMovie(
        @Query("query")          query: String,
        @Query("language")       language: String = "en-US",
        @Query("page")           page: Int = 1,
        @Query("include_adult")  includeAdult: Boolean = false
    ): Response<com.example.watchorderengine.network.model.TmdbPagedResults<com.example.watchorderengine.network.model.TmdbMediaResult>>

    /**
     * Fetches TMDB collections that contain a specific keyword in their name.
     *
     * Used by the franchise-anchor reverse lookup to find parent collection IDs
     * for universe discovery (e.g., "Marvel Cinematic Universe Collection").
     *
     * Endpoint: GET /3/search/collection
     * Docs: https://developer.themoviedb.org/reference/search-collection
     */
    @GET("search/collection")
    suspend fun searchCollection(
        @Query("query")    query: String,
        @Query("language") language: String = "en-US",
        @Query("page")     page: Int = 1
    ): Response<com.example.watchorderengine.network.model.TmdbPagedResults<com.example.watchorderengine.network.model.TmdbCollectionPart>>
}

// ─── OkHttp Auth Interceptor ──────────────────────────────────────────────────

/**
 * OkHttp interceptor that attaches the TMDB Bearer token to every request.
 *
 * WHY AN INTERCEPTOR (not @Query params):
 *   - One less parameter on every API method.
 *   - The token is added at the network layer — even calls made by Retrofit's
 *     cache-refresh mechanism will be authenticated.
 *   - Trivially swappable: in tests, inject a [TmdbAuthInterceptor] with a
 *     test token without rewriting the service interface.
 *
 * SECURITY NOTE: [readAccessToken] comes from BuildConfig, which reads from
 * local.properties. This file is in .gitignore and never committed.
 * The token is in memory only — it is NOT written to disk by this class.
 */
class TmdbAuthInterceptor(private val readAccessToken: String) : okhttp3.Interceptor {

    override fun intercept(chain: okhttp3.Interceptor.Chain): okhttp3.Response {
        val originalRequest = chain.request()

        // Don't authenticate requests that aren't going to TMDB
        // (future-proofing in case OkHttpClient is reused for other APIs)
        if (!originalRequest.url.host.contains("themoviedb.org")) {
            return chain.proceed(originalRequest)
        }

        val authenticatedRequest = originalRequest.newBuilder()
            .header("Authorization", "Bearer $readAccessToken")
            .header("Accept", "application/json")
            .build()

        return chain.proceed(authenticatedRequest)
    }
}
