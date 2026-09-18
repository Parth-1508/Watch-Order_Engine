package com.example.watchorderengine.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface OmdbApiService {

    @GET("/")
    suspend fun getByImdbId(
        @Query("i") imdbId: String,
        @Query("tomatoes") tomatoes: Boolean = true,
        @Query("apikey") apiKey: String = "trilogy"
    ): Response<OmdbDetailResponse>

    @GET("/")
    suspend fun getByTitle(
        @Query("t") title: String,
        @Query("tomatoes") tomatoes: Boolean = true,
        @Query("apikey") apiKey: String = "trilogy"
    ): Response<OmdbDetailResponse>
}

@JsonClass(generateAdapter = true)
data class OmdbDetailResponse(
    @Json(name = "Title")      val title: String?,
    @Json(name = "Year")       val year: String?,
    @Json(name = "Rated")      val rated: String?,
    @Json(name = "Released")   val released: String?,
    @Json(name = "Runtime")    val runtime: String?,
    @Json(name = "Genre")      val genre: String?,
    @Json(name = "Director")   val director: String?,
    @Json(name = "Writer")     val writer: String?,
    @Json(name = "Actors")     val actors: String?,
    @Json(name = "Plot")       val plot: String?,
    @Json(name = "Awards")     val awards: String?,
    @Json(name = "Poster")     val poster: String?,
    @Json(name = "Ratings")    val ratings: List<OmdbRatingItem>?,
    @Json(name = "Metascore")  val metascore: String?,
    @Json(name = "imdbRating") val imdbRating: String?,
    @Json(name = "imdbVotes")  val imdbVotes: String?,
    @Json(name = "imdbID")     val imdbId: String?,
    @Json(name = "Type")       val type: String?,
    @Json(name = "Response")   val response: String?
)

@JsonClass(generateAdapter = true)
data class OmdbRatingItem(
    @Json(name = "Source") val source: String?,
    @Json(name = "Value")  val value: String?
)
