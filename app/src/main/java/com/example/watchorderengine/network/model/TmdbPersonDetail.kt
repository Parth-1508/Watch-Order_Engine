package com.example.watchorderengine.network.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class TmdbPersonDetail(
    @Json(name = "id")                  val id: Int,
    @Json(name = "name")                val name: String,
    @Json(name = "also_known_as")       val alsoKnownAs: List<String>?,
    @Json(name = "biography")           val biography: String?,
    @Json(name = "birthday")            val birthday: String?,
    @Json(name = "deathday")            val deathday: String?,
    @Json(name = "place_of_birth")      val placeOfBirth: String?,
    @Json(name = "profile_path")        val profilePath: String?,
    @Json(name = "gender")              val gender: Int?,          // 0=Not set, 1=Female, 2=Male, 3=Non-binary
    @Json(name = "popularity")          val popularity: Double?,
    @Json(name = "known_for_department") val knownForDepartment: String?,
    @Json(name = "homepage")            val homepage: String?,
    @Json(name = "combined_credits")    val combinedCredits: TmdbPersonCredits?,
    @Json(name = "images")              val images: TmdbPersonImages?,
    @Json(name = "external_ids")        val externalIds: TmdbExternalIds?
)

@JsonClass(generateAdapter = true)
data class TmdbPersonCredits(
    @Json(name = "cast") val cast: List<TmdbPersonCastCredit>?,
    @Json(name = "crew") val crew: List<TmdbPersonCrewCredit>?
)

@JsonClass(generateAdapter = true)
data class TmdbPersonCastCredit(
    @Json(name = "id")            val id: Int,
    @Json(name = "credit_id")     val creditId: String,
    @Json(name = "title")         val title: String?,          // movie
    @Json(name = "name")          val name: String?,           // tv
    @Json(name = "character")     val character: String?,
    @Json(name = "media_type")    val mediaType: String?,      // "movie" | "tv"
    @Json(name = "poster_path")   val posterPath: String?,
    @Json(name = "release_date")  val releaseDate: String?,    // movie
    @Json(name = "first_air_date") val firstAirDate: String?,  // tv
    @Json(name = "vote_average")  val voteAverage: Double?,
    @Json(name = "episode_count") val episodeCount: Int?,      // tv only
    @Json(name = "popularity")    val popularity: Double?
)

@JsonClass(generateAdapter = true)
data class TmdbPersonCrewCredit(
    @Json(name = "id")           val id: Int,
    @Json(name = "title")        val title: String?,
    @Json(name = "name")         val name: String?,
    @Json(name = "job")          val job: String?,
    @Json(name = "department")   val department: String?,
    @Json(name = "media_type")   val mediaType: String?,
    @Json(name = "poster_path")  val posterPath: String?,
    @Json(name = "release_date") val releaseDate: String?,
    @Json(name = "first_air_date") val firstAirDate: String?
)

@JsonClass(generateAdapter = true)
data class TmdbPersonImages(
    @Json(name = "profiles") val profiles: List<TmdbProfileImage>?
)

@JsonClass(generateAdapter = true)
data class TmdbProfileImage(
    @Json(name = "file_path")    val filePath: String,
    @Json(name = "width")        val width: Int,
    @Json(name = "height")       val height: Int,
    @Json(name = "vote_average") val voteAverage: Double?
)
