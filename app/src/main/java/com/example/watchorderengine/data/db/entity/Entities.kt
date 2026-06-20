package com.example.watchorderengine.data.db.entity

import androidx.room.*

@Entity(tableName = "media")
data class MediaEntity(
    @PrimaryKey val id: String,          // "tmdb_{tmdbId}"
    val tmdbId: Int,
    val anilistId: Int?,
    val title: String,
    val originalTitle: String,
    val overview: String,
    val tagline: String,
    val status: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val mediaCategory: String,           // "MOVIE" | "TV_SHOW" | "ANIME"
    val genres: List<String>,            // Stored via Converters
    val ageRating: String,
    val voteAverage: Float,
    val voteCount: Int,
    val runtime: Int?,
    val numberOfSeasons: Int?,
    val numberOfEpisodes: Int?,
    val releaseDate: String?,
    val releaseYear: String = "",
    val trailerKey: String?,
    val castJson: String,                // JSON string of List<CastMember>
    val recommendationsJson: String,
    val arcsJson: String,
    val lastUpdated: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "seasons",
    foreignKeys = [ForeignKey(
        entity = MediaEntity::class,
        parentColumns = ["id"],
        childColumns = ["mediaId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("mediaId")]
)
data class SeasonEntity(
    @PrimaryKey val id: String,          // "{mediaId}_s{seasonNumber}"
    val mediaId: String,
    val seasonNumber: Int,
    val name: String,
    val overview: String,
    val posterUrl: String?,
    val airDate: String?,
    val episodeCount: Int,
    val lastUpdated: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "episodes",
    foreignKeys = [
        ForeignKey(entity = MediaEntity::class,  parentColumns = ["id"], childColumns = ["mediaId"],  onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = SeasonEntity::class, parentColumns = ["id"], childColumns = ["seasonId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("mediaId"), Index("seasonId"), Index("absoluteEpisodeNumber")]
)
data class EpisodeEntity(
    @PrimaryKey val id: String,           // "{mediaId}_s{sN}e{eN}"
    val seasonId: String,
    val mediaId: String,
    val episodeNumber: Int,
    val seasonNumber: Int,
    val absoluteEpisodeNumber: Int,
    val title: String,
    val overview: String,
    val airDate: String?,
    val runtime: Int?,
    val stillUrl: String?,
    val voteAverage: Float,
    val episodeType: String,              // "CANON" | "FILLER" | "MIXED"
    val arcName: String?,
    val lastUpdated: Long = System.currentTimeMillis()
)

@Entity(tableName = "user_progress", indices = [Index("trackingState")])
data class UserProgressEntity(
    @PrimaryKey val mediaId: String,
    val trackingState: String,            // TrackingState.name
    val currentSeasonNumber: Int = 0,
    val currentEpisodeNumber: Int = 0,
    val userRating: Float? = null,
    val startedDate: Long? = null,
    val completedDate: Long? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    val userNotes: String = "",
    val priorityTag: String = "NONE"      // PriorityTag.name
)

@Entity(
    tableName = "episode_watched",
    primaryKeys = ["episodeId"],
    indices = [Index("mediaId")]
)
data class EpisodeWatchedEntity(
    val episodeId: String,
    val mediaId: String,
    val watchedAt: Long = System.currentTimeMillis()
)
