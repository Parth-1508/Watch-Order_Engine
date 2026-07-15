package com.example.watchorderengine.data.model

import com.google.firebase.firestore.DocumentId
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Firestore-backed model for a single shared watch-order timeline post in the
 * global Community Feed.
 *
 * Collection path: `global_feed/{postId}`
 *
 * ── Firestore mapping notes ───────────────────────────────────────────────────
 * - [postId] is annotated @DocumentId — the Firestore SDK populates it
 *   automatically on read (`doc.toObject<CommunityPost>()`) and SKIPS it
 *   entirely on write (`firestore.collection(...).add(post)`), so it's never
 *   duplicated as a regular field inside the document body. This mirrors the
 *   exact convention already used by [MediaNode] and [ReviewDocument] in this
 *   codebase.
 * - Every property has a default value. This is REQUIRED for Firestore's
 *   reflection-based POJO mapper — it constructs the object via a no-arg
 *   constructor, then sets fields one by one. A class without defaults throws
 *   at deserialization time.
 * - [nodesJson] stores the watch-order graph as a raw JSON string rather than
 *   native Firestore nested objects/arrays. This keeps the schema stable even
 *   as the graph shape evolves, and lets the client encode/decode with
 *   kotlinx.serialization independent of Firestore's own mapper. See
 *   [SharedTimelinePayload] below for the exact envelope shape to encode.
 */
data class CommunityPost(
    @DocumentId
    var postId: String = "",

    var userId: String = "",
    var authorName: String = "",
    var authorAvatarUrl: String? = null,

    var universeTitle: String = "",
    var universeDescription: String = "",

    /**
     * kotlinx.serialization-encoded JSON of the shared graph, shaped as
     * [SharedTimelinePayload]: `{ "nodes": [...], "edges": [...] }`.
     * Decode with [SharedTimelinePayload.decode] before rendering.
     */
    var nodesJson: String = "",

    var likesCount: Long = 0L,

    /**
     * UIDs of every user who has liked this post. Checking
     * `currentUserId in likedByUsers` client-side drives the filled/outline
     * heart icon state based on whether the current user's UID is in likedByUsers.
     */
    var likedByUsers: List<String> = emptyList(),

    /** System.currentTimeMillis() at creation. Used for feed sort order. */
    var timestamp: Long = 0L,

    var tags: List<String> = emptyList(),
    var isOfficial: Boolean = false,
)

// ─── Shared graph payload envelope ─────────────────────────────────────────────

/**
 * The decoded shape of [CommunityPost.nodesJson].
 *
 * Mirrors exactly what [com.example.watchorderengine.data.WatchOrderRepository
 * .publishSortedUniverse] already builds for a user's private universe
 * (`List<MediaNode>` + `List<Edge>`), so a "Share Timeline" action on
 * TimelineScreen only needs to encode the SAME two lists it already has in
 * memory — no reshaping required.
 *
 * [MediaNode] and [Edge] are already `@Serializable`, so this envelope
 * round-trips cleanly through kotlinx.serialization.
 */
@Serializable
data class SharedTimelinePayload(
    val nodes: List<MediaNode> = emptyList(),
    val edges: List<Edge> = emptyList(),
)

/**
 * Encode/decode helpers for [SharedTimelinePayload], used on both sides of
 * the share flow:
 *   - Producer (TimelineScreen "Share" button): [encode] the current
 *     universe's nodes+edges, pass the resulting string as
 *     `CommunityRepository.shareTimeline(title, description, nodesJson)`.
 *   - Consumer (CommunityPostCard graph preview, or a future "Import shared
 *     timeline" action): [decode] a [CommunityPost.nodesJson] string back
 *     into typed nodes/edges.
 *
 * `ignoreUnknownKeys = true` future-proofs older shared posts against later
 * additions to [MediaNode]'s field set.
 */
object SharedTimelineCodec {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(nodes: List<MediaNode>, edges: List<Edge>): String =
        json.encodeToString(SharedTimelinePayload(nodes, edges))

    /** Returns null (never throws) if the JSON is malformed or from an incompatible schema. */
    fun decode(nodesJson: String): SharedTimelinePayload? = try {
        json.decodeFromString<SharedTimelinePayload>(nodesJson)
    } catch (e: Exception) {
        null
    }
}
