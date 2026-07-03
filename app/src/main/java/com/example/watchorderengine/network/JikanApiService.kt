package com.example.watchorderengine.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

// ─── Jikan v4 Response Models ─────────────────────────────────────────────────

/**
 * Top-level response from GET /v4/anime?q={title}&limit=1
 *
 * [data] holds the ranked search results. We only ever read [data][0]
 * (the top hit), so a limit=1 request is sufficient.
 */
@JsonClass(generateAdapter = true)
data class JikanSearchResponse(
    @Json(name = "data") val data: List<JikanAnime>
)

/**
 * Minimal anime entry from a Jikan search result.
 *
 * Only [malId] is needed to drive the subsequent episode fetch.
 * Additional fields (title, year, etc.) are intentionally omitted
 * to keep the adapter surface small.
 */
@JsonClass(generateAdapter = true)
data class JikanAnime(
    @Json(name = "mal_id") val malId: Int
)

/**
 * Top-level response from GET /v4/anime/{id}/episodes
 *
 * Jikan paginates at 25 episodes per page.  [pagination] carries the
 * [has_next_page][JikanPagination.hasNextPage] flag callers must use to
 * loop through all pages for long-running series (Naruto, Bleach, etc.).
 */
@JsonClass(generateAdapter = true)
data class JikanEpisodesResponse(
    @Json(name = "data")       val data:       List<JikanEpisode>,
    @Json(name = "pagination") val pagination: JikanPagination? = null
)

/**
 * Pagination metadata embedded in every paginated Jikan response.
 *
 * [hasNextPage] is the only field the episode-fetching loop needs.
 */
@JsonClass(generateAdapter = true)
data class JikanPagination(
    @Json(name = "has_next_page") val hasNextPage: Boolean
)

/**
 * Single episode entry from the Jikan episode list.
 *
 * IMPORTANT — [malId] here is the **absolute episode number** (1-indexed),
 * not a database surrogate key.  Jikan's episode listings use `mal_id` as
 * the episode number field (matching the MAL episode identifier), so it
 * maps directly to [EpisodeItem.absoluteEpisodeNumber] when merging.
 *
 * [filler] is set to true by the MyAnimeList community for episodes that
 * do not advance the main story arc (i.e., anime-original filler).
 */
@JsonClass(generateAdapter = true)
data class JikanEpisode(
    @Json(name = "mal_id") val malId:  Int,
    @Json(name = "episode_id") val epId:  Int? = null,
    @Json(name = "filler") val filler: Boolean
)

// ─── Jikan v4 Retrofit Interface ──────────────────────────────────────────────

/**
 * Retrofit service for the Jikan REST v4 API.
 *
 * BASE URL  : https://api.jikan.moe/v4/
 * AUTH      : None — Jikan is a fully public API.
 * RATE LIMIT: 3 requests/second, 60 requests/minute.
 *             Keep calls to a minimum: one [searchAnime] + N [getEpisodes]
 *             page calls per show.  Do NOT call these in a tight loop.
 *
 * Docs: https://docs.api.jikan.moe/
 */
interface JikanApiService {

    /**
     * Searches MyAnimeList for an anime by title and returns the best match.
     *
     * Endpoint: GET /v4/anime?q={title}&limit=1
     * Docs    : https://docs.api.jikan.moe/#tag/anime/operation/getAnimeSearch
     *
     * Passing the TMDB English title works well for most shows.  The caller
     * should gracefully handle an empty [JikanSearchResponse.data] list (title
     * not found on MAL) rather than crashing on index access.
     *
     * @param title  Show title to search for (e.g. "Naruto", "Attack on Titan").
     * @param limit  Result cap — 1 is always sufficient; we only use the top hit.
     */
    @GET("anime")
    suspend fun searchAnime(
        @Query("q")     title: String,
        @Query("limit") limit: Int = 1
    ): Response<JikanSearchResponse>

    /**
     * Fetches a single page of episode data for a given anime, including
     * filler and recap flags.
     *
     * Endpoint: GET /v4/anime/{id}/episodes?page={page}
     * Docs    : https://docs.api.jikan.moe/#tag/anime/operation/getAnimeEpisodes
     *
     * PAGINATION: Jikan returns 25 episodes per page.  Callers must loop
     * through pages until [JikanEpisodesResponse.pagination.hasNextPage]
     * is false.  See [MediaDetailViewModel.fetchAllJikanEpisodes].
     *
     * @param malId  MyAnimeList anime ID obtained from [searchAnime].
     * @param page   1-indexed page number.
     */
    @GET("anime/{id}/episodes")
    suspend fun getEpisodes(
        @Path("id")    malId: Int,
        @Query("page") page:  Int = 1
    ): Response<JikanEpisodesResponse>
}
