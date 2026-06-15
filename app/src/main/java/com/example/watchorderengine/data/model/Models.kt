package com.example.watchorderengine.data.model

import kotlinx.serialization.Serializable

/** The 5-state tracking lifecycle for any media item. */
@Serializable
enum class TrackingState(val displayName: String) {
    PLANNED("Planned"),
    WATCHING("Watching"),
    COMPLETED("Completed"),
    PAUSED("Paused"),
    DROPPED("Dropped")
}

/** Priority tags for advanced personalization. */
@Serializable
enum class PriorityTag(val label: String) {
    HIGH("High Priority"),
    BACKLOG("Backlog"),
    ON_HOLD("On Hold"),
    NONE("None")
}

/** Sort types for home/watchlist. */
@Serializable
enum class SortType {
    DATE_ADDED,
    USER_RATING,
    GLOBAL_SCORE,
    ALPHABETICAL
}

/** Episode content classification — set by the ETL pipeline via Gemini analysis. */
@Serializable
enum class EpisodeType(val label: String) {
    CANON("CANON"),
    FILLER("FILLER"),
    MIXED("MIXED")
}

/** Top-level media category for routing to the correct TMDB endpoint and UI template. */
@Serializable
enum class MediaCategory { MOVIE, TV_SHOW, ANIME }

// ─── Media ────────────────────────────────────────────────────────────────────

/** Lightweight list-item representation for grids and search results. */
@Serializable
data class MediaSummary(
    val id: String,
    val tmdbId: Int,
    val title: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val mediaCategory: MediaCategory,
    val voteAverage: Float,
    val releaseYear: String,
    val trackingState: TrackingState?,  // null if not in user's list
    val ageRating: String,
    val priorityTag: PriorityTag = PriorityTag.NONE
)

/** Full rich detail for the Detail Screen. */
@Serializable
data class MediaDetail(
    val id: String,
    val tmdbId: Int,
    val anilistId: Int?,
    val title: String,
    val originalTitle: String,
    val overview: String,
    val tagline: String,
    val status: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val mediaCategory: MediaCategory,
    val genres: List<String>,
    val ageRating: String,
    val voteAverage: Float,
    val voteCount: Int,
    val runtime: Int?,             // minutes for movies; avg episode runtime for TV
    val numberOfSeasons: Int?,
    val numberOfEpisodes: Int?,
    val releaseDate: String?,
    val releaseYear: String,
    val trailerKey: String?,
    val cast: List<CastMember>,
    val recommendations: List<MediaSummary>,
    val seasons: List<SeasonSummary>,
    val arcs: List<StoryArc>,
    // User-specific (merged from Room)
    val userProgress: UserProgress?
)

// ─── Cast ─────────────────────────────────────────────────────────────────────

@Serializable
data class CastMember(
    val tmdbId: Int,
    val name: String,
    val character: String,
    val profileUrl: String?,
    val order: Int
)

// ─── Seasons & Episodes ───────────────────────────────────────────────────────

@Serializable
data class SeasonSummary(
    val id: String,
    val mediaId: String,
    val seasonNumber: Int,
    val name: String,
    val overview: String,
    val posterUrl: String?,
    val airDate: String?,
    val episodeCount: Int
)

@Serializable
data class EpisodeItem(
    val id: String,
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
    val episodeType: EpisodeType,
    val arcName: String?,
    val isWatched: Boolean   // merged from Room
)

@Serializable
data class StoryArc(
    val name: String,
    val startAbsoluteEpisode: Int?,
    val endAbsoluteEpisode: Int?,
    val startSeason: Int,
    val startEpisode: Int,
    val endSeason: Int,
    val endEpisode: Int,
    val synopsis: String
)

// ─── User Progress ────────────────────────────────────────────────────────────

@Serializable
data class UserProgress(
    val mediaId: String,
    val trackingState: TrackingState,
    val currentSeasonNumber: Int,
    val currentEpisodeNumber: Int,
    val totalEpisodesWatched: Int,
    val userRating: Float?,
    val startedDate: Long?,
    val completedDate: Long?,
    val updatedAt: Long,
    val userNotes: String = "",
    val priorityTag: PriorityTag = PriorityTag.NONE
)

// ─── Profile Stats ────────────────────────────────────────────────────────────

@Serializable
data class UserStats(
    val totalMinutesWatched: Long,
    val totalEpisodesWatched: Int,
    val totalMoviesWatched: Int,
    val showsCompleted: Int,
    val showsDropped: Int,
    val showsWatching: Int,
    val showsPlanned: Int,
    val showsPaused: Int,
    val topGenres: List<String>,
    val averageRating: Float?
)
