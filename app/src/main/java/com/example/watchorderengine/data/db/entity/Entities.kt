package com.example.watchorderengine.data.db.entity

import androidx.room.*

/**
 * Primary cache entity for a movie or TV show.
 *
 * Schema version 5 adds [watchProvidersJson] — a JSON-serialised
 * `List<WatchProviderItem>` written by the repository when it fetches
 * the TMDB "watch/providers" append module.  The column is TEXT NOT NULL
 * with a default of `'[]'` so the v4→v5 migration is a single ALTER TABLE
 * with no data loss.
 */
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
    val genres: List<String>,            // stored via Converters (||| separator)
    val ageRating: String,
    val voteAverage: Float,
    val voteCount: Int,
    val runtime: Int?,
    val numberOfSeasons: Int?,
    val numberOfEpisodes: Int?,
    val releaseDate: String?,
    val releaseYear: String = "",
    val trailerKey: String?,
    val originalLanguage: String? = null,

    /**
     * JSON-serialised `List<WatchProviderItem>`.
     *
     * Written by [MediaRepository.resolveWatchProviders] at cache time.
     * Read by [MediaRepository.buildMediaDetail] and exposed via [MediaDetail.watchProviders].
     *
     * Default `"[]"` so old rows (pre-v5) and skeleton entries created during
     * watch-order generation show an empty provider list rather than crashing.
     */
    val watchProvidersJson: String = "[]",

    val castJson: String,                // JSON string of List<CastMember>
    val recommendationsJson: String,
    val arcsJson: String,

    /**
     * Set to true once [MediaRepository.enrichEpisodesWithJikanFiller] has
     * successfully completed for this show.  Prevents re-running the 44-second
     * One Piece job on every app launch or ViewModel recreation.
     */
    val jikanFillerSynced: Boolean = false,

    val lastUpdated: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "seasons",
    foreignKeys = [ForeignKey(
        entity        = MediaEntity::class,
        parentColumns = ["id"],
        childColumns  = ["mediaId"],
        onDelete      = ForeignKey.CASCADE
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
    @PrimaryKey val id: String,          // "{mediaId}_s{sN}e{eN}"
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
    val episodeType: String,             // "CANON" | "FILLER" | "MIXED"
    val arcName: String?,
    val lastUpdated: Long = System.currentTimeMillis()
)

@Entity(tableName = "user_progress", indices = [Index("trackingState")])
data class UserProgressEntity(
    @PrimaryKey val mediaId: String = "",
    val trackingState: String = "PLANNED",           // TrackingState.name
    val currentSeasonNumber: Int = 0,
    val currentEpisodeNumber: Int = 0,
    val userRating: Float? = null,
    val startedDate: Long? = null,
    val completedDate: Long? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    val userNotes: String = "",
    val priorityTag: String = "NONE",    // PriorityTag.name
    
    // Graph/Universe progress fields
    val completedNodeIds: List<String> = emptyList(),
    val activeRoute: String? = null,
    val spoilerShieldEnabled: Boolean = false
)

data class JoinedProgressMedia(
    @Embedded val progress: UserProgressEntity,
    @Relation(
        parentColumn = "mediaId",
        entityColumn = "id"
    )
    val media: MediaEntity?
)

@Entity(
    tableName = "episode_watched",
    primaryKeys = ["episodeId"],
    indices    = [Index("mediaId")]
)
data class EpisodeWatchedEntity(
    val episodeId: String,
    val mediaId: String,
    val watchedAt: Long = System.currentTimeMillis()
)

/**
 * Tracks shows the user swiped LEFT ("skip") on the Discovery deck.
 * Distinct from [TrackingState.DROPPED]: skipped items resurface after a deck
 * reset; dropped items are permanently excluded.
 */
@Entity(tableName = "discovery_skipped")
data class DiscoverySkippedEntity(
    @PrimaryKey val mediaId: String,
    val skippedAt: Long = System.currentTimeMillis()
)
