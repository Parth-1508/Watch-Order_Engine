package com.example.watchorderengine.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface TvmazeApiService {

    @GET("schedule")
    suspend fun getSchedule(
        @Query("country") country: String = "US",
        @Query("date") dateIso: String? = null
    ): Response<List<TvmazeScheduleEntry>>

    @GET("schedule/full")
    suspend fun getFullSchedule(): Response<List<TvmazeScheduleEntry>>

    @GET("shows/{showId}")
    suspend fun getShowDetail(
        @Path("showId") showId: Int,
        @Query("embed") embed: String = "episodes"
    ): Response<TvmazeShowDetail>
}

@JsonClass(generateAdapter = true)
data class TvmazeScheduleEntry(
    @Json(name = "id")            val id: Long,
    @Json(name = "name")          val episodeName: String?,
    @Json(name = "season")        val seasonNumber: Int?,
    @Json(name = "number")        val episodeNumber: Int?,
    @Json(name = "airdate")       val airdate: String?,
    @Json(name = "airtime")       val airtime: String?,
    @Json(name = "summary")       val summary: String?,
    @Json(name = "show")          val show: TvmazeShowSummary?
)

@JsonClass(generateAdapter = true)
data class TvmazeShowSummary(
    @Json(name = "id")        val id: Int,
    @Json(name = "name")      val name: String,
    @Json(name = "type")      val type: String?,
    @Json(name = "language")  val language: String?,
    @Json(name = "summary")   val summary: String?,
    @Json(name = "image")     val image: TvmazeImage?,
    @Json(name = "network")   val network: TvmazeNetwork?,
    @Json(name = "webChannel") val webChannel: TvmazeNetwork?,
    @Json(name = "externals") val externals: TvmazeExternals?
)

@JsonClass(generateAdapter = true)
data class TvmazeShowDetail(
    @Json(name = "id")        val id: Int,
    @Json(name = "name")      val name: String,
    @Json(name = "summary")   val summary: String?,
    @Json(name = "network")   val network: TvmazeNetwork?,
    @Json(name = "webChannel") val webChannel: TvmazeNetwork?,
    @Json(name = "externals") val externals: TvmazeExternals?
)

@JsonClass(generateAdapter = true)
data class TvmazeNetwork(
    @Json(name = "id")      val id: Int?,
    @Json(name = "name")    val name: String?,
    @Json(name = "country") val country: TvmazeCountry?
)

@JsonClass(generateAdapter = true)
data class TvmazeCountry(
    @Json(name = "name") val name: String?,
    @Json(name = "code") val code: String?
)

@JsonClass(generateAdapter = true)
data class TvmazeImage(
    @Json(name = "medium") val medium: String?,
    @Json(name = "original") val original: String?
)

@JsonClass(generateAdapter = true)
data class TvmazeExternals(
    @Json(name = "tvrage")  val tvrage: Long?,
    @Json(name = "thetvdb") val thetvdb: Long?,
    @Json(name = "imdb")    val imdb: String?
)
