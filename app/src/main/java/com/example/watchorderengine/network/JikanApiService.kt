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
 * [malId] in the /anime/{id}/episodes endpoint is the **sequential episode
 * number** within the series (1-based), NOT a global database surrogate key.
 * This is confirmed by the Jikan v4 docs and all known wrappers — e.g.
 * `number: episode.mal_id` in jikan-api.js. It maps 1:1 to TMDB's
 * [absoluteEpisodeNumber] for series where Jikan and TMDB agree on airing order.
 *
 * NOTE: There is no `episode_id` field in the Jikan v4 episodes response.
 * Earlier versions of this model included one as a fallback; it has been
 * removed to avoid confusion and silent null-coalescing.
 *
 * [filler] defaults to false — Jikan may omit this field for episodes that
 * have not yet been community-classified on MAL. A missing field must not
 * crash the Moshi adapter or incorrectly mark an episode as filler.
 */
@JsonClass(generateAdapter = true)
data class JikanEpisode(
    @Json(name = "mal_id") val malId:  Int,
    @Json(name = "filler") val filler: Boolean = false
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
     * Searches MyAnimeList for a TV anime by title and returns the best match.
     *
     * Endpoint: GET /v4/anime?q={title}&type=tv&limit=1
     * Docs    : https://docs.api.jikan.moe/#tag/anime/operation/getAnimeSearch
     *
     * WHY type=tv:
     *   Without this filter, a search for "One Piece" returns OVAs, movies,
     *   or recap specials ahead of the main TV series. For example, searching
     *   just "One Piece" can return "One Piece: Defeat Him! The Pirate Ganzack"
     *   (a 30-minute OVA, MAL ID 136) instead of the main series (MAL ID 21).
     *   Restricting to type=tv ensures we always land on the correct long-running
     *   series entry that carries the full filler classification data.
     *
     * WHY sfw=true:
     *   Prevents adult titles from surfacing as false-positive top matches for
     *   mainstream anime titles that share common words.
     *
     * @param title  Show title (e.g. "Naruto", "One Piece", "Bleach").
     * @param type   MAL media type filter. "tv" covers all serialised TV anime.
     * @param limit  Result cap — 1 is always sufficient; we only use the top hit.
     * @param sfw    Filter out adult content.
     */
    @GET("anime")
    suspend fun searchAnime(
        @Query("q")     title: String,
        @Query("type")  type:  String  = "tv",
        @Query("limit") limit: Int     = 1,
        @Query("sfw")   sfw:   Boolean = true
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
