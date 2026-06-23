package com.example.watchorderengine.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * Retrofit interface for Wikipedia's REST "page summary" endpoint — a free,
 * no-auth-required source for character/person lore that fills the gap TMDB
 * leaves open (TMDB has zero fictional-character backstory fields) for
 * titles AniList doesn't cover (live-action, Western shows).
 *
 * Endpoint: GET https://en.wikipedia.org/api/rest_v1/page/summary/{title}
 * Docs: https://www.mediawiki.org/wiki/Wikimedia_REST_API
 *
 * IMPORTANT: {title} is a URL PATH segment, not a query parameter — spaces
 * must become underscores (Retrofit's @Path already percent-encodes the
 * rest). [CharacterRepository] handles the space→underscore conversion
 * before calling this.
 */
interface WikipediaApiService {
    @GET("page/summary/{title}")
    suspend fun getPageSummary(
        @Path("title", encoded = false) title: String
    ): Response<WikipediaSummaryResponse>
}

/**
 * Subset of Wikipedia's page-summary response we actually need. `type` is
 * critical: Wikipedia returns HTTP 200 (not 404) for disambiguation pages
 * ("Tony Stark" could plausibly clash with a real person of the same name),
 * with `type = "disambiguation"` and a useless or absent `extract`. Callers
 * MUST check `type == "standard"` before trusting `extract` as real lore —
 * see [CharacterRepository.getCharacterLore].
 */
@JsonClass(generateAdapter = true)
data class WikipediaSummaryResponse(
    @Json(name = "type") val type: String? = null,
    @Json(name = "title") val title: String? = null,
    @Json(name = "extract") val extract: String? = null,
    @Json(name = "description") val description: String? = null,
    @Json(name = "originalimage") val originalImage: WikipediaImage? = null,
    @Json(name = "thumbnail") val thumbnail: WikipediaImage? = null,
    @Json(name = "content_urls") val contentUrls: WikipediaContentUrls? = null
)

@JsonClass(generateAdapter = true)
data class WikipediaImage(
    @Json(name = "source") val source: String? = null,
    @Json(name = "width") val width: Int? = null,
    @Json(name = "height") val height: Int? = null
)

@JsonClass(generateAdapter = true)
data class WikipediaContentUrls(
    @Json(name = "desktop") val desktop: WikipediaUrl? = null
)

@JsonClass(generateAdapter = true)
data class WikipediaUrl(
    @Json(name = "page") val page: String? = null
)
