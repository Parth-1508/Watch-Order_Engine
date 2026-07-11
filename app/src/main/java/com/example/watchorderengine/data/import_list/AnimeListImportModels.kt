package com.example.watchorderengine.data.import_list

import com.example.watchorderengine.data.model.MediaCategory
import com.example.watchorderengine.data.model.TrackingState
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class JikanUserListResponse(
    @Json(name = "data")       val data: List<JikanUserListEntry>,
    @Json(name = "pagination") val pagination: JikanUserListPagination?
)

@JsonClass(generateAdapter = true)
data class JikanUserListPagination(
    @Json(name = "has_next_page") val hasNextPage: Boolean,
    @Json(name = "current_page")  val currentPage: Int
)

@JsonClass(generateAdapter = true)
data class JikanUserListEntry(
    @Json(name = "node")             val node: JikanAnimeNode,
    /** 0–10 score; 0 = unscored. */
    @Json(name = "list_status")      val listStatus: JikanListStatus
)

@JsonClass(generateAdapter = true)
data class JikanAnimeNode(
    @Json(name = "mal_id") val malId: Int,
    @Json(name = "title")  val title: String,
    @Json(name = "images") val images: JikanImages?,
    @Json(name = "type")   val type: String?
)

@JsonClass(generateAdapter = true)
data class JikanListStatus(
    /** 1=Watching 2=Completed 3=On-Hold 4=Dropped 6=Plan to Watch */
    @Json(name = "status") val status: String,
    /** MAL score 0–10 (0 = unrated). */
    @Json(name = "score")  val score: Int,
    /** Number of episodes watched. */
    @Json(name = "num_episodes_watched") val numEpisodesWatched: Int
)

@JsonClass(generateAdapter = true)
data class JikanImages(
    @Json(name = "jpg") val jpg: JikanImageUrls?
)

@JsonClass(generateAdapter = true)
data class JikanImageUrls(
    @Json(name = "image_url")       val imageUrl: String?,
    @Json(name = "large_image_url") val largeImageUrl: String?
)

/** Domain model shared by both the AniList and MAL/Jikan import paths. */
data class ImportedAnimeEntry(
    val malId: Int?,
    val anilistId: Int?,
    val title: String,
    val coverImageUrl: String?,
    val trackingState: TrackingState,
    /** User's 1.0–10.0 rating (null if unrated). */
    val userRating: Float?,
    val progress: Int,
    val totalEpisodes: Int?,
    val source: Source,
    val mediaCategory: MediaCategory
) {
    enum class Source { ANILIST, MAL }
}
