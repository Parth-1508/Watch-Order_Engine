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
    val data: AnilistData?
)

@JsonClass(generateAdapter = true)
data class AnilistData(
    @Json(name = "Media") val media: AnilistMedia?,
    @Json(name = "Page") val page: AnilistPagedMedia?
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
    val characters: AnilistCharacters?
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
