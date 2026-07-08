package com.example.watchorderengine.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface AnilistApiService {
    @POST("/")
    suspend fun query(@Body body: AnilistRequest): Response<AnilistResponse>
}

@JsonClass(generateAdapter = true)
data class AnilistRequest(
    val query: String,
    val variables: Map<String, Any?> = emptyMap()
)

@JsonClass(generateAdapter = true)
data class AnilistResponse(
    val data: AnilistData?,
    // AniList returns HTTP 200 even for a query that fails GraphQL validation
    // (e.g. an invalid enum value) — `data` is null and the real reason lives
    // here. Surfaced via Log.w in CharacterRepository so a bad query shows up
    // in Logcat instead of silently rendering blank images.
    val errors: List<AnilistError>? = null
)

@JsonClass(generateAdapter = true)
data class AnilistError(
    val message: String?
)

@JsonClass(generateAdapter = true)
data class AnilistData(
    @Json(name = "Media") val media: AnilistMedia?,
    @Json(name = "Page") val page: AnilistPagedMedia?,
    @Json(name = "MediaListCollection") val mediaListCollection: AnilistMediaListCollection? = null
)

@JsonClass(generateAdapter = true)
data class AnilistMediaListCollection(
    val lists: List<AnilistMediaList>?
)

@JsonClass(generateAdapter = true)
data class AnilistMediaList(
    val name: String?,
    val status: String?,
    val entries: List<AnilistListEntry>?
)

@JsonClass(generateAdapter = true)
data class AnilistListEntry(
    val score: Float?,
    val status: String?,
    val progress: Int?,
    val media: AnilistMedia?
)

@JsonClass(generateAdapter = true)
data class AnilistPagedMedia(
    @Json(name = "media") val media: List<AnilistMedia>?
)

@JsonClass(generateAdapter = true)
data class AnilistMedia(
    val id: Int,
    val idMal: Int?,
    val title: AnilistTitle?,
    val description: String?,
    val bannerImage: String?,
    val coverImage: AnilistCoverImage?,
    val episodes: Int?,
    val averageScore: Int?,
    val genres: List<String>?,
    val tags: List<AnilistTag>?,
    val relations: AnilistRelations?,
    val characters: AnilistCharacters?,
    val reviews: AnilistReviews? = null,
    // "TV" | "TV_SHORT" | "MOVIE" | "SPECIAL" | "OVA" | "ONA" | "MUSIC" — used to
    // pick out franchise movies/specials from a media's relations list.
    val format: String? = null,
    val startDate: AnilistFuzzyDate? = null
)

@JsonClass(generateAdapter = true)
data class AnilistFuzzyDate(
    val year: Int?,
    val month: Int? = null,
    val day: Int? = null
)

@JsonClass(generateAdapter = true)
data class AnilistTitle(
    val english: String?,
    val romaji: String?,
    val native: String?
)

@JsonClass(generateAdapter = true)
data class AnilistCoverImage(
    val extraLarge: String?,
    val large: String?,
    val medium: String?
)

@JsonClass(generateAdapter = true)
data class AnilistTag(
    val name: String,
    val isMediaSpoiler: Boolean
)

@JsonClass(generateAdapter = true)
data class AnilistRelations(
    val edges: List<AnilistRelationEdge>?
)

@JsonClass(generateAdapter = true)
data class AnilistRelationEdge(
    val relationType: String,
    val node: AnilistMedia?
)

@JsonClass(generateAdapter = true)
data class AnilistCharacters(
    val edges: List<AnilistCharacterEdge>?
)

@JsonClass(generateAdapter = true)
data class AnilistCharacterEdge(
    val role: String,
    val node: AnilistCharacterNode?,
    val voiceActors: List<AnilistVoiceActor>? = null
)

@JsonClass(generateAdapter = true)
data class AnilistCharacterNode(
    val id: Int,
    val name: AnilistCharacterName?,
    val image: AnilistCharacterImage?,
    val description: String? = null,
    val gender: String? = null,
    val age: String? = null
)

@JsonClass(generateAdapter = true)
data class AnilistCharacterName(
    val full: String?,
    val native: String? = null
)

@JsonClass(generateAdapter = true)
data class AnilistCharacterImage(
    val large: String?
)

@JsonClass(generateAdapter = true)
data class AnilistVoiceActor(
    val id: Int,
    val name: AnilistCharacterName?,
    val language: String?,
    val image: AnilistCharacterImage?
)

// ─── Reviews ──────────────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class AnilistReviews(
    val nodes: List<AnilistReviewNode>?
)

@JsonClass(generateAdapter = true)
data class AnilistReviewNode(
    val id: Int,
    val summary: String?,
    val body: String?,
    val score: Int?,
    val createdAt: Int?,
    val user: AnilistUser?
)

@JsonClass(generateAdapter = true)
data class AnilistUser(
    val name: String,
    val avatar: AnilistAvatar?
)

@JsonClass(generateAdapter = true)
data class AnilistAvatar(
    val large: String?
)
