package com.example.watchorderengine.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName
import kotlinx.serialization.Serializable

// ─── Enums ────────────────────────────────────────────────────────────────────

/** The 5-state tracking lifecycle for any media item. */
@Serializable
enum class TrackingState(val displayName: String) {
    PLANNED("Planned"),
    WATCHING("Watching"),
    COMPLETED("Completed"),
    PAUSED("Paused"),
    DROPPED("Dropped")
}

/** Priority tags for advanced personalisation. */
@Serializable
enum class PriorityTag(val label: String) {
    HIGH("High Priority"),
    BACKLOG("Backlog"),
    ON_HOLD("On Hold"),
    NONE("None")
}

/** Sort types for home/watchlist screens. */
@Serializable
enum class SortType {
    DATE_ADDED,
    USER_RATING,
    GLOBAL_SCORE,
    ALPHABETICAL
}

/** Episode content classification set by Gemini analysis. */
@Serializable
enum class EpisodeType(val label: String) {
    CANON("CANON"),
    FILLER("FILLER"),
    MIXED("MIXED")
}

/** Top-level media category, used for TMDB routing and UI template selection. */
@Serializable
enum class MediaCategory { MOVIE, TV_SHOW, ANIME, EPISODE, SHORT, SPECIAL, COMIC, NOVEL, GAME }

// ─── Watch Providers ──────────────────────────────────────────────────────────

/**
 * One streaming / rental / purchase option shown in the "Where to Watch" card.
 *
 * Built by [MediaRepository.resolveWatchProviders] from the TMDB
 * `watch/providers` append module.  The repository resolves the best available
 * country (IN → US → GB → …) and flattens all offer types into a single list,
 * so the UI can group by [offerType] without knowing the country.
 */
@Serializable
data class WatchProviderItem(
    val providerId: Int,

    /** Short display name — may be overridden by [TmdbConfig.PROVIDER_SHORT_NAMES]. */
    val providerName: String,

    /** Full TMDB logo image URL (already resolved, ready for Coil/Glide). */
    val logoUrl: String?,

    /**
     * "stream" | "rent" | "buy" | "free"
     *
     * Controls badge colour and section grouping in the UI:
     *   stream / free → green "STREAM" badge
     *   rent          → orange "RENT" badge
     *   buy           → blue "BUY" badge
     */
    val offerType: String,

    /**
     * JustWatch deep-link for the title in the resolved country.
     * Tapping opens Chrome → JustWatch → the provider's app.
     * May be null if the TMDB country entry has no `link` field.
     */
    val justWatchUrl: String?,
)

// ─── Media ────────────────────────────────────────────────────────────────────

/** Lightweight list-item used in grids, search results, and watchlists. */
@Serializable
data class MediaSummary(
    @DocumentId val id: String = "",
    val tmdbId: Int = 0,
    val title: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val mediaCategory: MediaCategory,
    val voteAverage: Float,
    val releaseYear: String,
    val trackingState: TrackingState?,   // null = not in user's list
    val ageRating: String,
    val priorityTag: PriorityTag = PriorityTag.NONE,
    val genres: List<String> = emptyList(),
    val releaseDate: String? = null
)

/**
 * Full rich detail shown on the Detail Screen.
 *
 * ERROR #5 additions:
 *  - [trailerKey] was already present but never rendered.  The UI now shows a
 *    YouTube thumbnail with in-app WebView playback and an "open in app" button.
 *  - [watchProviders] is new — resolved from the TMDB `watch/providers` append
 *    module at cache time by the repository.  Empty list = no providers found.
 */
@Serializable
data class MediaDetail(
    @DocumentId val id: String = "",
    val tmdbId: Int = 0,
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
    val runtime: Int?,
    val numberOfSeasons: Int?,
    val numberOfEpisodes: Int?,
    val releaseDate: String?,
    val releaseYear: String,
    val trailerKey: String?,

    /**
     * Streaming / rental / purchase options for this title.
     * Resolved once at cache time; empty list hides the "Where to Watch" card.
     */
    val watchProviders: List<WatchProviderItem> = emptyList(),

    val cast: List<CastMember>,
    val recommendations: List<MediaSummary>,
    val seasons: List<SeasonSummary>,
    val arcs: List<StoryArc>,
    val userProgress: UserProgress?
)

// ─── Cast ─────────────────────────────────────────────────────────────────────

@Serializable
data class CastMember(
    val tmdbId: Int,
    val name: String,
    val character: String,
    val profileUrl: String?,
    val order: Int,
    val biography: String? = null
)

// ─── Seasons & Episodes ───────────────────────────────────────────────────────

@Serializable
data class SeasonSummary(
    @DocumentId val id: String = "",
    val mediaId: String = "",
    val seasonNumber: Int,
    val name: String,
    val overview: String,
    val posterUrl: String?,
    val airDate: String?,
    val episodeCount: Int
)

@Serializable
data class EpisodeItem(
    @DocumentId val id: String = "",
    val seasonId: String = "",
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
    val isWatched: Boolean,
    val isSpoilerBlurred: Boolean = false
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
    val mediaId: String = "",
    val trackingState: TrackingState = TrackingState.PLANNED,
    val currentSeasonNumber: Int = 1,
    val currentEpisodeNumber: Int = 1,
    val totalEpisodesWatched: Int = 0,
    val userRating: Float? = null,
    val startedDate: Long? = null,
    val completedDate: Long? = null,
    val updatedAt: Long = 0,
    val userNotes: String = "",
    val priorityTag: PriorityTag = PriorityTag.NONE,
    @get:PropertyName("user_id") @set:PropertyName("user_id")
    @kotlinx.serialization.Transient var userId: String? = null,
    @get:PropertyName("universe_id") @set:PropertyName("universe_id")
    @kotlinx.serialization.Transient var universeId: String? = null,
    @get:PropertyName("completed_node_ids") @set:PropertyName("completed_node_ids")
    @kotlinx.serialization.Transient var completed_node_ids: List<String> = emptyList(),
    @get:PropertyName("active_route") @set:PropertyName("active_route")
    @kotlinx.serialization.Transient var active_route: String? = null,
    @get:PropertyName("spoiler_shield_enabled") @set:PropertyName("spoiler_shield_enabled")
    @kotlinx.serialization.Transient var spoiler_shield_enabled: Boolean = false,
    @get:PropertyName("last_updated") @set:PropertyName("last_updated")
    @kotlinx.serialization.Transient var lastUpdatedFirebase: com.google.firebase.Timestamp? = null
)

// ─── Universe / Graph ─────────────────────────────────────────────────────────

@Serializable
data class Universe(
    @DocumentId val id: String = "",
    val name: String = "",
    val description: String = "",
    val posterUrl: String? = null,
    val bannerUrl: String? = null,
    val tmdbId: Int? = null,
    val mediaType: String? = null,
    @get:PropertyName("available_routes") @set:PropertyName("available_routes")
    @kotlinx.serialization.Transient var available_routes: List<String> = emptyList(),
    @get:PropertyName("total_nodes") @set:PropertyName("total_nodes")
    @kotlinx.serialization.Transient var total_nodes: Int = 0
)

@Serializable
data class MediaNode(
    @DocumentId val id: String = "",
    val title: String = "",
    val content_type: String = "",   // MOVIE, SERIES, OVA
    val type: MediaCategory = MediaCategory.TV_SHOW,
    val tmdb_id: Int = 0,
    val tmdb_media_type: String = "",
    val chrono_order: Float = 0f,
    val release_order: Float = 0f,
    val phase: String = "",
    val tags: List<String> = emptyList(),
    val releaseYear: Int = 0,
    val episodeCount: Int = 0,
    val durationMin: Int = 0
)

@Serializable
data class Edge(
    val from_node_id: String = "",
    val to_node_id: String = "",
    val type: String = "REQUIRED"    // REQUIRED | OPTIONAL
)

@Serializable
data class ContextTag(
    @DocumentId val tagId: String = "",
    val label: String = "",
    val color: String = "#FFFFFF",
    val order: Int = 0
)

// ─── Profile Stats ────────────────────────────────────────────────────────────

/**
 * Aggregate watch statistics shown on the Profile screen.
 *
 * ERROR #4 additions:
 *  - [recentlyWatched] — 6 most-recently-updated tracked shows, shown as a
 *    horizontal poster scroll so the user can quickly jump back in.
 *  - [favoriteGenre] — most-watched genre string, derived from tracked media genres.
 *  - [streakDays] — consecutive calendar days with ≥1 episode marked watched,
 *    computed from episode_watched.watchedAt timestamps by the repository.
 *
 * All new fields default to safe values so existing callers don't need changes.
 */
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
    val averageRating: Float?,
    // ERROR #4 new fields
    val recentlyWatched: List<MediaSummary> = emptyList(),
    val favoriteGenre: String? = null,
    val streakDays: Int = 0,
    val profileScore: Int = 0
)
