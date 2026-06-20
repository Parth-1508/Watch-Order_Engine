package com.example.watchorderengine.network.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Exhaustive TMDB detail response for a SINGLE API call using append_to_response.
 */
@JsonClass(generateAdapter = true)
data class TmdbDetailResponse(
    @Json(name = "id")                   val id: Int,
    @Json(name = "title")                val title: String?,           // Movies
    @Json(name = "name")                 val name: String?,             // TV
    @Json(name = "original_title")       val originalTitle: String?,
    @Json(name = "original_name")        val originalName: String?,
    @Json(name = "overview")             val overview: String?,
    @Json(name = "tagline")              val tagline: String?,
    @Json(name = "status")               val status: String?,
    @Json(name = "poster_path")          val posterPath: String?,
    @Json(name = "backdrop_path")        val backdropPath: String?,
    @Json(name = "release_date")         val releaseDate: String?,      // Movies
    @Json(name = "first_air_date")       val firstAirDate: String?,     // TV
    @Json(name = "runtime")              val runtime: Int?,             // Movies
    @Json(name = "episode_run_time")     val episodeRunTime: List<Int>?, // TV
    @Json(name = "vote_average")         val voteAverage: Double,
    @Json(name = "vote_count")           val voteCount: Int,
    @Json(name = "popularity")           val popularity: Double,
    @Json(name = "number_of_seasons")    val numberOfSeasons: Int?,
    @Json(name = "number_of_episodes")   val numberOfEpisodes: Int?,
    @Json(name = "origin_country")       val originCountry: List<String>?,
    @Json(name = "original_language")    val originalLanguage: String?,
    @Json(name = "genres")               val genres: List<TmdbGenre>?,
    @Json(name = "seasons")              val seasons: List<TmdbSeasonSummary>?,
    // appended modules:
    @Json(name = "credits")              val credits: TmdbCredits?,
    @Json(name = "aggregate_credits")    val aggregateCredits: TmdbAggregateCredits?, // TV only
    @Json(name = "videos")               val videos: TmdbVideoResults?,
    @Json(name = "recommendations")      val recommendations: TmdbPagedResults<TmdbMediaResult>?,
    @Json(name = "external_ids")         val externalIds: TmdbExternalIds?,
    @Json(name = "content_ratings")      val contentRatings: TmdbContentRatings?,    // TV
    @Json(name = "release_dates")        val releaseDates: TmdbReleaseDates?,        // Movies
    @Json(name = "keywords")             val keywords: TmdbKeywords?,
) {
    fun toDomainModel(): TmdbMediaDetail = TmdbMediaDetail(
        tmdbId = id,
        title = title ?: name ?: "",
        overview = overview ?: "",
        posterUrl = com.example.watchorderengine.network.TmdbConfig.buildImageUrl(posterPath),
        backdropUrl = com.example.watchorderengine.network.TmdbConfig.buildImageUrl(backdropPath, com.example.watchorderengine.network.TmdbConfig.PosterSize.HD),
        releaseDate = releaseDate ?: firstAirDate ?: "",
        runtimeMinutes = runtime ?: episodeRunTime?.firstOrNull() ?: 0,
        episodeCount = numberOfEpisodes ?: 0,
        seasonCount = numberOfSeasons ?: 0,
        voteAverage = voteAverage.toFloat(),
        voteCount = voteCount,
        genres = genres?.map { it.name } ?: emptyList(),
        status = status ?: "",
        tagline = tagline ?: "",
        imdbId = externalIds?.imdbId,
        mediaType = if (title != null) TmdbMediaType.MOVIE else TmdbMediaType.TV
    )
}

@JsonClass(generateAdapter = true)
data class TmdbGenre(
    @Json(name = "id")   val id: Int,
    @Json(name = "name") val name: String
)

@JsonClass(generateAdapter = true)
data class TmdbSeasonSummary(
    @Json(name = "id")             val id: Int,
    @Json(name = "season_number")  val seasonNumber: Int,
    @Json(name = "name")           val name: String,
    @Json(name = "overview")       val overview: String?,
    @Json(name = "poster_path")    val posterPath: String?,
    @Json(name = "air_date")       val airDate: String?,
    @Json(name = "episode_count")  val episodeCount: Int,
)

@JsonClass(generateAdapter = true)
data class TmdbSeasonDetail(
    @Json(name = "id")            val id: Int,
    @Json(name = "season_number") val seasonNumber: Int,
    @Json(name = "name")          val name: String,
    @Json(name = "overview")      val overview: String?,
    @Json(name = "poster_path")   val posterPath: String?,
    @Json(name = "air_date")      val airDate: String?,
    @Json(name = "episodes")      val episodes: List<TmdbEpisode>?,
)

@JsonClass(generateAdapter = true)
data class TmdbEpisode(
    @Json(name = "id")              val id: Int,
    @Json(name = "episode_number")  val episodeNumber: Int,
    @Json(name = "season_number")   val seasonNumber: Int,
    @Json(name = "name")            val name: String?,
    @Json(name = "overview")        val overview: String?,
    @Json(name = "runtime")         val runtime: Int?,
    @Json(name = "air_date")        val airDate: String?,
    @Json(name = "still_path")      val stillPath: String?,
    @Json(name = "vote_average")    val voteAverage: Double?,
    @Json(name = "vote_count")      val voteCount: Int?,
    @Json(name = "crew")            val crew: List<TmdbEpisodeCrew>?,
    @Json(name = "guest_stars")     val guestStars: List<TmdbCastMember>?,
)

@JsonClass(generateAdapter = true)
data class TmdbEpisodeCrew(
    @Json(name = "id")           val id: Int,
    @Json(name = "name")         val name: String,
    @Json(name = "job")          val job: String,
    @Json(name = "department")   val department: String,
    @Json(name = "profile_path") val profilePath: String?,
)

@JsonClass(generateAdapter = true)
data class TmdbCredits(
    @Json(name = "cast") val cast: List<TmdbCastMember>?,
    @Json(name = "crew") val crew: List<TmdbCrewMember>?,
)

@JsonClass(generateAdapter = true)
data class TmdbAggregateCredits(
    @Json(name = "cast") val cast: List<TmdbAggregateCastMember>?,
)

@JsonClass(generateAdapter = true)
data class TmdbCastMember(
    @Json(name = "id")           val id: Int,
    @Json(name = "name")         val name: String,
    @Json(name = "character")    val character: String?,
    @Json(name = "profile_path") val profilePath: String?,
    @Json(name = "order")        val order: Int?,
    @Json(name = "known_for_department") val department: String?,
)

@JsonClass(generateAdapter = true)
data class TmdbAggregateCastMember(
    @Json(name = "id")           val id: Int,
    @Json(name = "name")         val name: String,
    @Json(name = "profile_path") val profilePath: String?,
    @Json(name = "roles")        val roles: List<TmdbRole>?,
    @Json(name = "order")        val order: Int?,
)

@JsonClass(generateAdapter = true)
data class TmdbRole(
    @Json(name = "character")      val character: String?,
    @Json(name = "episode_count")  val episodeCount: Int,
)

@JsonClass(generateAdapter = true)
data class TmdbCrewMember(
    @Json(name = "id")           val id: Int,
    @Json(name = "name")         val name: String,
    @Json(name = "job")          val job: String,
    @Json(name = "department")   val department: String,
    @Json(name = "profile_path") val profilePath: String?,
)

@JsonClass(generateAdapter = true)
data class TmdbVideoResults(
    @Json(name = "results") val results: List<TmdbVideo>?,
)

@JsonClass(generateAdapter = true)
data class TmdbVideo(
    @Json(name = "id")        val id: String,
    @Json(name = "key")       val key: String,
    @Json(name = "name")      val name: String,
    @Json(name = "site")      val site: String,
    @Json(name = "type")      val type: String,
    @Json(name = "official")  val official: Boolean,
    @Json(name = "published_at") val publishedAt: String?,
)

@JsonClass(generateAdapter = true)
data class TmdbPagedResults<T>(
    @Json(name = "results")       val results: List<T>?,
    @Json(name = "total_results") val totalResults: Int,
    @Json(name = "total_pages")   val totalPages: Int,
)

@JsonClass(generateAdapter = true)
data class TmdbMediaResult(
    @Json(name = "id")           val id: Int,
    @Json(name = "title")        val title: String?,
    @Json(name = "name")         val name: String?,
    @Json(name = "poster_path")  val posterPath: String?,
    @Json(name = "backdrop_path") val backdropPath: String?,
    @Json(name = "media_type")   val mediaType: String?,
    @Json(name = "vote_average") val voteAverage: Double?,
    @Json(name = "genre_ids")    val genreIds: List<Int>?
)

@JsonClass(generateAdapter = true)
data class TmdbContentRatings(
    @Json(name = "results") val results: List<TmdbContentRating>?,
)

@JsonClass(generateAdapter = true)
data class TmdbContentRating(
    @Json(name = "iso_3166_1") val countryCode: String,
    @Json(name = "rating")     val rating: String,
)

@JsonClass(generateAdapter = true)
data class TmdbReleaseDates(
    @Json(name = "results") val results: List<TmdbReleaseDateCountry>?,
)

@JsonClass(generateAdapter = true)
data class TmdbReleaseDateCountry(
    @Json(name = "iso_3166_1")    val countryCode: String,
    @Json(name = "release_dates") val releaseDates: List<TmdbReleaseDate>?,
)

@JsonClass(generateAdapter = true)
data class TmdbReleaseDate(
    @Json(name = "certification") val certification: String?,
    @Json(name = "release_date")  val releaseDate: String?,
    @Json(name = "type")          val type: Int,
)

@JsonClass(generateAdapter = true)
data class TmdbExternalIds(
    @Json(name = "imdb_id")      val imdbId: String?,
    @Json(name = "tvdb_id")      val tvdbId: Int?,
    @Json(name = "facebook_id")  val facebookId: String?,
    @Json(name = "instagram_id") val instagramId: String?,
    @Json(name = "twitter_id")   val twitterId: String?,
)

@JsonClass(generateAdapter = true)
data class TmdbKeywords(
    @Json(name = "results")  val results: List<TmdbKeyword>?,
    @Json(name = "keywords") val keywords: List<TmdbKeyword>?,
)

@JsonClass(generateAdapter = true)
data class TmdbKeyword(
    @Json(name = "id")   val id: Int,
    @Json(name = "name") val name: String,
)

@JsonClass(generateAdapter = true)
data class TmdbPersonResponse(
    @Json(name = "id")           val id: Int,
    @Json(name = "name")         val name: String,
    @Json(name = "biography")    val biography: String?,
    @Json(name = "birthday")     val birthday: String?,
    @Json(name = "place_of_birth") val placeOfBirth: String?,
    @Json(name = "profile_path") val profilePath: String?
)

data class TmdbMediaDetail(
    val tmdbId: Int,
    val title: String,
    val overview: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val releaseDate: String,
    val runtimeMinutes: Int,
    val episodeCount: Int,
    val seasonCount: Int,
    val voteAverage: Float,
    val voteCount: Int,
    val genres: List<String>,
    val status: String,
    val tagline: String,
    val imdbId: String?,
    val mediaType: TmdbMediaType
) {
    val releaseYear: String get() = releaseDate.take(4)
    val runtimeDisplay: String get() = if (mediaType == TmdbMediaType.TV) "$episodeCount episodes" else "${runtimeMinutes}min"
}

enum class TmdbMediaType { MOVIE, TV }
