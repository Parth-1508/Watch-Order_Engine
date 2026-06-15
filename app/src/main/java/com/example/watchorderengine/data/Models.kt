package com.example.watchorderengine.data

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * A NODE in our Directed Acyclic Graph. Represents a single piece of
 * watchable media (film, episode, OVA, special, comic, etc.).
 *
 * Firestore path: /universes/{universeId}/nodes/{nodeId}
 *
 * DESIGN NOTE: We use @PropertyName to map Kotlin camelCase to Firestore
 * snake_case. The no-arg constructor requirement for Firestore deserialization
 * is satisfied automatically by Kotlin's default parameter values.
 */
@IgnoreExtraProperties
data class MediaNode(

    /** Mirrors the Firestore document ID. Populated automatically by Firestore. */
    @DocumentId
    val id: String = "",

    /**
     * The TMDB numeric ID for this media entry.
     */
    @get:PropertyName("tmdb_id") @set:PropertyName("tmdb_id")
    var tmdbId: Int = 0,

    /**
     * The TMDB media type — either "movie" or "tv".
     */
    @get:PropertyName("tmdb_media_type") @set:PropertyName("tmdb_media_type")
    var tmdbMediaType: String = "movie",

    /**
     * Local content type for UI icon selection.
     */
    @get:PropertyName("content_type") @set:PropertyName("content_type")
    var contentType: String = "MOVIE",

    /** Full display title. Redacted by the Spoiler Shield when blurred. */
    val title: String = "",

    /**
     * Media type drives the icon shown in the UI.
     * Stored as the enum name string in Firestore, e.g., "MOVIE" or "SERIES".
     */
    @get:PropertyName("type") @set:PropertyName("type")
    var typeStr: String = MediaType.MOVIE.name,

    /** Year of first release/air date. Used for release-order sorting hints. */
    @get:PropertyName("release_year") @set:PropertyName("release_year")
    var releaseYear: Int = 0,

    /** Runtime in minutes. 0 for SERIES type. */
    @get:PropertyName("duration_min") @set:PropertyName("duration_min")
    var durationMin: Int = 0,

    /** Episode count for SERIES type; ignored for others. */
    @get:PropertyName("episode_count") @set:PropertyName("episode_count")
    var episodeCount: Int = 0,

    /** Poster/cover art URL (Firestore Storage or external CDN). */
    @get:PropertyName("cover_url") @set:PropertyName("cover_url")
    var coverUrl: String = "",

    /**
     * The canonical tag set for this node — used by the Route Filter engine.
     *
     * Convention (universe editors should follow these):
     *   "CANON"       → Officially part of the main story.
     *   "ESSENTIAL"   → Must-watch for the core experience.
     *   "OPTIONAL"    → Side content; not required to understand the main plot.
     *   "NON_CANON"   → Filler, alternate timelines, officially non-canonical.
     *   "PHASE_1", "PHASE_2" ... → Arc/phase grouping for the UI.
     *
     * A node typically has multiple tags, e.g., ["CANON", "ESSENTIAL", "PHASE_1"].
     */
    val tags: List<String> = emptyList(),

    /**
     * In-universe chronological position hint (float allows inserting new
     * entries at 2.5 between 2 and 3 without renumbering the entire list).
     */
    @get:PropertyName("chrono_order") @set:PropertyName("chrono_order")
    var chronoOrder: Float = 0f,

    /**
     * Real-world release order hint. Used when the user picks the
     * RELEASE_ORDER route filter, which overrides graph traversal order.
     */
    @get:PropertyName("release_order") @set:PropertyName("release_order")
    var releaseOrder: Float = 0f,

    /** One-paragraph synopsis. NOT shown in list view (spoiler risk). */
    val synopsis: String = "",

    /**
     * Phase/arc label for visual grouping in the UI (e.g., "Fate/Zero Arc",
     * "Phase 1 – The Avengers Initiative"). Displayed as a section divider.
     */
    val phase: String = "",

    /** True if this node has multiple outgoing edges (a "split point"). */
    @get:PropertyName("is_branch_point") @set:PropertyName("is_branch_point")
    var isBranchPoint: Boolean = false,

    /** True if this node has multiple incoming edges (a "merge point"). */
    @get:PropertyName("is_merge_point") @set:PropertyName("is_merge_point")
    var isMergePoint: Boolean = false,
) {
    // Derived property — safe to call on deserialized objects from Firestore.
    @get:Exclude
    val type: MediaType
        get() = MediaType.entries.find { it.name == typeStr } ?: MediaType.MOVIE
}

enum class MediaType {
    MOVIE,    // Feature film
    SERIES,   // Multi-episode show
    EPISODE,  // Single episode node
    SHORT,    // Short film / ONA
    SPECIAL,  // TV special / OVA
    COMIC,    // Manga / comic tie-in
    NOVEL,    // Light novel / tie-in novel
    GAME      // Video game entry
}

/**
 * A DIRECTED EDGE in the DAG. Edge (A → B) means:
 * "To fully understand B, you should have watched A first."
 *
 * Firestore path: /universes/{universeId}/edges/{edgeId}
 *
 * The engine only uses REQUIRED edges for topological ordering.
 * RECOMMENDED and OPTIONAL edges are shown visually but do not
 * constrain the watch order in basic routes.
 */
@IgnoreExtraProperties
data class Edge(
    /** Mirrors the Firestore document ID. Populated automatically by Firestore. */
    @DocumentId
    val id: String = "",

    /** Source node ID — watch this first. */
    @get:PropertyName("from_node_id") @set:PropertyName("from_node_id")
    var fromNodeId: String = "",

    /** Destination node ID — this unlocks after the source. */
    @get:PropertyName("to_node_id") @set:PropertyName("to_node_id")
    var toNodeId: String = "",

    @get:PropertyName("type") @set:PropertyName("type")
    var typeStr: String = EdgeType.REQUIRED.name,

    /**
     * Optional human-readable context label shown on the connector line,
     * e.g., "Post-credits scene leads here" or "3 years later".
     */
    val label: String = "",
) {
    @get:Exclude
    val type: EdgeType
        get() = EdgeType.entries.find { it.name == typeStr } ?: EdgeType.REQUIRED
}

enum class EdgeType {
    /** Hard prerequisite. The graph traversal enforces this ordering. */
    REQUIRED,

    /** Strong suggestion. Shown in the UI but not enforced by the sort. */
    RECOMMENDED,

    /** Bonus context link. Only shown in "ALL" routes. */
    OPTIONAL
}

// ─────────────────────────────────────────────────────────────────────────────
// Universe
// Firestore path: /universes/{universeId}
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Top-level metadata for an entertainment universe.
 * This document is small — only metadata, no subcollection data.
 */
@IgnoreExtraProperties
data class Universe(
    @DocumentId
    val id: String = "",
    val name: String = "",
    val description: String = "",

    @get:PropertyName("cover_url") @set:PropertyName("cover_url")
    var coverUrl: String = "",

    /**
     * Denormalized node count for displaying progress ("42 of 130 completed")
     * without fetching the entire nodes subcollection just to count.
     * Keep in sync via Cloud Functions when nodes are added/removed.
     */
    @get:PropertyName("total_nodes") @set:PropertyName("total_nodes")
    var totalNodes: Int = 0,

    /**
     * The list of route tag keys that editors have configured for this universe.
     * Used to populate the route filter chip row in the UI.
     * e.g., ["ALL", "CANON", "ESSENTIAL", "CHRONOLOGICAL", "RELEASE_ORDER"]
     */
    @get:PropertyName("available_routes") @set:PropertyName("available_routes")
    var availableRoutes: List<String> = listOf("ALL"),
)

// ─────────────────────────────────────────────────────────────────────────────
// UserProgress
// Firestore path: /users/{userId}/progress/{universeId}
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Tracks a specific user's state within a universe.
 *
 * SCALE NOTE: completedNodeIds is stored as a List<String>. For universes
 * with < 500 nodes (all known entertainment universes qualify), Firestore
 * array operations (arrayUnion / arrayRemove) are efficient and safe.
 * For > 500 nodes, switch to Map<String, Boolean> for field-level writes.
 */
@IgnoreExtraProperties
data class UserProgress(
    @get:PropertyName("user_id") @set:PropertyName("user_id")
    var userId: String = "",

    @get:PropertyName("universe_id") @set:PropertyName("universe_id")
    var universeId: String = "",

    /**
     * The set of node IDs the user has checked off.
     * Stored as a List in Firestore for arrayUnion/arrayRemove support.
     * Converted to Set<String> in the ViewModel for O(1) lookup.
     */
    @get:PropertyName("completed_node_ids") @set:PropertyName("completed_node_ids")
    var completedNodeIds: List<String> = emptyList(),

    /**
     * The active route filter key, matching a ContextTag.id.
     * e.g., "CANON", "ALL", "ESSENTIAL". Defaults to "ALL".
     */
    @get:PropertyName("active_route") @set:PropertyName("active_route")
    var activeRoute: String = "ALL",

    /** Whether the spoiler shield is currently enabled for this user + universe. */
    @get:PropertyName("spoiler_shield_enabled") @set:PropertyName("spoiler_shield_enabled")
    var spoilerShieldEnabled: Boolean = true,

    /** Server timestamp of last progress update (used for sync conflict detection). */
    @get:PropertyName("last_updated") @set:PropertyName("last_updated")
    @ServerTimestamp
    var lastUpdated: Date? = null,
)

// ─────────────────────────────────────────────────────────────────────────────
// ContextTag (Route Filter definition)
// Firestore path: /universes/{universeId}/tags/{tagId}
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Defines a user-selectable route/filter for a universe.
 * The `id` field is the canonical tag key matched against MediaNode.tags.
 *
 * Examples:
 *   id="CANON", label="Canon Only", color="#FFD700"
 *   id="ESSENTIAL", label="Essential", color="#4FACFE"
 *   id="ALL", label="All Content", color="#888899"
 */
@IgnoreExtraProperties
data class ContextTag(
    /** The canonical key, must match values used in MediaNode.tags. */
    @DocumentId
    val id: String = "",
    val label: String = "",
    val description: String = "",

    /** Hex color string for the filter chip UI, e.g., "#FFD700". */
    val color: String = "#808080",

    /** Display order in the filter picker. Lower = shown first. */
    val order: Int = 0,
)
