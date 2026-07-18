package com.example.watchorderengine.data.repository

import android.util.Log
import com.example.watchorderengine.data.model.CommunityPost
import com.example.watchorderengine.data.model.PredefinedTimelines
import com.example.watchorderengine.data.model.SharedTimelineCodec
import com.example.watchorderengine.data.prefs.UserPreferencesRepository
import com.example.watchorderengine.viewmodel.TimelineViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.toObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "CommunityRepository"
private const val COLLECTION_GLOBAL_FEED = "global_feed"
private const val FEED_LIMIT = 50L

/**
 * Data layer for the global Community Feed.
 *
 * Firestore collection: `global_feed/{postId}` — a single flat, public
 * collection (not nested under `users/{uid}/...` like reviews or progress)
 * since every post is meant to be globally readable.
 */
@Singleton
class CommunityRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val userPrefs: UserPreferencesRepository,
) {

    /**
     * Live feed of the 50 most recent community posts, ordered newest-first.
     */
    fun fetchGlobalFeed(): Flow<Result<List<CommunityPost>>> = callbackFlow<Result<List<CommunityPost>>> {
        val predefined = PredefinedTimelines.masterTimelines
        
        // Base query for the feed
        val baseQuery = firestore.collection(COLLECTION_GLOBAL_FEED)
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(FEED_LIMIT)

        val registration = baseQuery.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "Global feed listener error: ${error.message}")
                trySend(Result.failure(error))
                return@addSnapshotListener
            }

            val combined = snapshot?.documents?.let { docs ->
                val fetchedPosts = docs.mapNotNull { doc ->
                    try {
                        doc.toObject<CommunityPost>()?.apply { postId = doc.id }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse post ${doc.id}: ${e.message}")
                        null
                    }
                }

                // Merge logic: Predefined always at top.
                // If a predefined post is found in the Firestore results (fetchedPosts),
                // use the Firestore version as the source of truth for likes/state.
                // Otherwise, use the local definition (starts at 0 likes).
                val predefinedIds = predefined.map { it.postId }.toSet()
                predefined.map { p ->
                    fetchedPosts.find { it.postId == p.postId } ?: p
                } + fetchedPosts.filter { it.postId !in predefinedIds }
            } ?: predefined

            trySend(Result.success(combined))
        }

        awaitClose {
            Log.d(TAG, "Removing global feed listener")
            registration.remove()
        }
    }.flowOn(Dispatchers.IO)

    private fun autoCategorize(title: String, description: String, nodesJson: String): List<String> {
        val tags = mutableSetOf<String>()
        val content = (title + " " + description).lowercase()
        
        // 1. Check title/description for keywords
        if (content.contains("spider") || content.contains("avengers") || content.contains("marvel") || content.contains("iron man") || content.contains("captain america") || content.contains("thor") || content.contains("black panther") || content.contains("mcu") || content.contains("eternals") || content.contains("black widow") || content.contains("guardians of the galaxy") || content.contains("ant-man")) {
            tags.add("Marvel")
        }
        if (content.contains("star wars") || content.contains("jedi") || content.contains("mandalorian") || content.contains("kenobi") || content.contains("andor") || content.contains("skywalker") || content.contains("solo:")) {
            tags.add("Star Wars")
        }
        if (content.contains("batman") || content.contains("superman") || content.contains("wonder woman") || content.contains("justice league") || content.contains("shazam") || content.contains("aquaman") || content.contains("dceu") || content.contains("dc universe") || content.contains("suicide squad") || content.contains("peacemaker")) {
            tags.add("DC Universe")
        }
        if (content.contains("naruto") || content.contains("shippuden") || content.contains("boruto") || content.contains("fate/") || content.contains("one piece") || content.contains("dragon ball") || content.contains("anime") || content.contains("bleach") || content.contains("jujutsu") || content.contains("demon slayer") || content.contains("attack on titan") || content.contains("fullmetal")) {
            tags.add("Anime")
        }
        if (content.contains("conjuring") || content.contains("annabelle") || content.contains("nun") || content.contains("horror") || content.contains("insidious") || content.contains("scream") || content.contains("scary") || content.contains("halloween") || content.contains(" it ") || content.contains("saw ")) {
            tags.add("Horror")
        }
        if (content.contains("star trek") || content.contains("sci-fi") || content.contains("science fiction") || content.contains("interstellar") || content.contains("dune") || content.contains("alien") || content.contains("blade runner") || content.contains("matrix")) {
            tags.add("Sci-Fi")
        }
        
        // 2. Check individual node titles as fallback
        if (tags.size < 2) {
            val payload = SharedTimelineCodec.decode(nodesJson)
            val nodeTitles = payload?.nodes?.map { it.title.lowercase() } ?: emptyList()
            
            if (nodeTitles.any { it.contains("spider") || it.contains("avengers") || it.contains("iron man") || it.contains("marvel") || it.contains("mcu") }) {
                tags.add("Marvel")
            }
            if (nodeTitles.any { it.contains("batman") || it.contains("superman") || it.contains("justice league") || it.contains("wonder woman") }) {
                tags.add("DC Universe")
            }
            if (nodeTitles.any { it.contains("star wars") || it.contains("jedi") || it.contains("clone wars") }) {
                tags.add("Star Wars")
            }
            if (nodeTitles.any { it.contains("naruto") || it.contains("shippuden") || it.contains("fate/") || it.contains("anime") }) {
                tags.add("Anime")
            }
        }
        
        return tags.toList()
    }

    /**
     * Publishes the current user's timeline to the global feed.
     */
    suspend fun shareTimeline(
        title: String,
        description: String,
        nodesJson: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val uid = auth.currentUser?.uid
                ?: throw IllegalStateException("Not authenticated — cannot share a timeline.")

            val usernameFromPrefs = userPrefs.username.first()
            val firebaseUser = auth.currentUser
            
            // Priority: Prefs (if not default) > Firebase Display Name > Default
            val username = when {
                usernameFromPrefs != "Player One" && usernameFromPrefs != "Guest" && usernameFromPrefs.isNotBlank() -> usernameFromPrefs
                !firebaseUser?.displayName.isNullOrBlank() -> firebaseUser?.displayName ?: "Explorer"
                else -> "Explorer"
            }
            
            val avatarUrl = userPrefs.avatarUrl.first() ?: firebaseUser?.photoUrl?.toString()
            val autoTags = autoCategorize(title, description, nodesJson)
            
            val payload = SharedTimelineCodec.decode(nodesJson)
            val bannerUrl = payload?.nodes?.firstOrNull { !it.posterUrl.isNullOrBlank() }?.posterUrl

            val post = CommunityPost(
                userId              = uid,
                authorName          = username,
                authorAvatarUrl     = avatarUrl,
                universeTitle       = title,
                universeDescription = description,
                bannerPosterUrl     = bannerUrl,
                nodesJson           = nodesJson,
                likesCount          = 0L,
                likedByUsers        = emptyList(),
                timestamp           = System.currentTimeMillis(),
                tags                = autoTags,
                isOfficial          = false
            )

            firestore.collection(COLLECTION_GLOBAL_FEED).add(post).await()
            Log.d(TAG, "Shared timeline '$title' to global feed with tags: $autoTags")
            Unit
        }.onFailure { e ->
            Log.w(TAG, "shareTimeline failed: ${e.message}")
        }
    }

    /**
     * Atomically toggles the current user's like on a post.
     */
    suspend fun toggleLikePost(
        postId: String,
        currentUserId: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val postRef = firestore.collection(COLLECTION_GLOBAL_FEED).document(postId)
            var authorId: String? = null
            var universeTitle: String = ""

            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(postRef)
                
                if (!snapshot.exists()) {
                    val predefinedBase = PredefinedTimelines.masterTimelines.find { it.postId == postId }
                    if (predefinedBase != null) {
                        authorId = predefinedBase.userId
                        universeTitle = predefinedBase.universeTitle
                        val skeleton = predefinedBase.copy(
                            postId = postId,
                            userId = currentUserId,
                            likedByUsers = listOf(currentUserId),
                            likesCount = 1L,
                            timestamp = System.currentTimeMillis()
                        )
                        transaction.set(postRef, skeleton)
                    }
                    return@runTransaction null
                }

                authorId = snapshot.getString("userId")
                universeTitle = snapshot.getString("universeTitle") ?: "your timeline"

                @Suppress("UNCHECKED_CAST")
                val likedBy = snapshot.get("likedByUsers") as? List<String> ?: emptyList()
                val alreadyLiked = currentUserId in likedBy

                if (alreadyLiked) {
                    transaction.update(
                        postRef,
                        mapOf(
                            "likedByUsers" to FieldValue.arrayRemove(currentUserId),
                            "likesCount"   to FieldValue.increment(-1L)
                        )
                    )
                } else {
                    transaction.update(
                        postRef,
                        mapOf(
                            "likedByUsers" to FieldValue.arrayUnion(currentUserId),
                            "likesCount"   to FieldValue.increment(1L)
                        )
                    )
                }
                null
            }.await()

            // ─── Smart Notification: Notify the author of the like ────────────────
            val finalAuthorId = authorId
            if (finalAuthorId != null && currentUserId != finalAuthorId) {
                runCatching {
                    val notif = com.example.watchorderengine.data.model.Notification(
                        userId = finalAuthorId,
                        type = com.example.watchorderengine.data.model.NotificationType.LIKE,
                        title = "Someone liked your timeline!",
                        message = "Your watch order for '$universeTitle' is getting some love.",
                        senderId = currentUserId,
                        senderName = userPrefs.username.first(),
                        senderAvatarUrl = userPrefs.avatarUrl.first(),
                        targetId = postId
                    )
                    firestore.collection("notifications").add(notif).await()
                }
            }

            Unit
        }.onFailure { e ->
            Log.w(TAG, "toggleLikePost failed for $postId: ${e.message}")
        }
    }

    /**
     * Deletes a post the current user authored.
     */
    suspend fun deletePost(postId: String, authorUserId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val uid = auth.currentUser?.uid ?: throw IllegalStateException("Not authenticated")
            if (uid != authorUserId) throw IllegalStateException("You are not the author of this post.")
            
            firestore.collection(COLLECTION_GLOBAL_FEED).document(postId).delete().await()
            Log.d(TAG, "Deleted post $postId")
            Unit
        }.onFailure { e ->
            Log.w(TAG, "deletePost failed for $postId: ${e.message}")
        }
    }

    /**
     * Imports a shared timeline into the user's private collection.
     *
     * Creates a new 'Universe' document and copies over all nodes/edges
     * from the shared post.
     */
    suspend fun importTimeline(post: CommunityPost): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val uid = auth.currentUser?.uid ?: throw IllegalStateException("Not authenticated")
            
            // Generate a new unique ID for the imported universe
            val newUniverseId = "imported_${post.postId}_${System.currentTimeMillis()}"
            
            val payload = SharedTimelineCodec.decode(post.nodesJson)
                ?: throw IllegalArgumentException("Malformed timeline data")

            val batch = firestore.batch()

            // Route tags actually present on the incoming nodes (e.g. a branching
            // timeline like Fate might carry "UBW" / "HF" instead of the old
            // hardcoded "CANON" / "ESSENTIAL"). "ALL" always leads so GraphEngine's
            // ROUTE_ALL default shows every node. Falls back to a sane default set
            // for older posts / plain linear timelines with no route tags at all.
            val discoveredRouteTags = payload.nodes.flatMap { it.tags }.distinct()
            val routeTags = (listOf("ALL") + discoveredRouteTags.filter { it != "ALL" })
                .ifEmpty { listOf("ALL", "CANON", "ESSENTIAL") }

            // 1. Create Universe Metadata
            val universeRef = firestore.collection("universes").document(newUniverseId)
            batch.set(universeRef, mapOf(
                "id" to newUniverseId,
                "name" to post.universeTitle,
                "description" to post.universeDescription + " (Imported from ${post.authorName})",
                "posterUrl" to (payload.nodes.firstOrNull { !it.posterUrl.isNullOrBlank() }?.posterUrl ?: ""),
                "total_nodes" to payload.nodes.size,
                "available_routes" to routeTags,
                "is_public" to false,
                "owner_id" to uid,
                "timestamp" to System.currentTimeMillis()
            ))

            // 2. Copy & Normalize Nodes
            val resolvedIdByOriginalId = mutableMapOf<String, String>()
            payload.nodes.forEach { node ->
                val canonicalId = TimelineViewModel.resolveMediaId(node)
                if (canonicalId.isBlank()) return@forEach // Safety skip
                
                resolvedIdByOriginalId[node.id] = canonicalId
                val normalizedNode = node.copy(id = canonicalId)
                batch.set(universeRef.collection("nodes").document(canonicalId), normalizedNode)
            }

            // 3. Copy & Normalize Edges
            payload.edges.forEachIndexed { index, edge ->
                val fromId = resolvedIdByOriginalId[edge.from_node_id] ?: edge.from_node_id
                val toId   = resolvedIdByOriginalId[edge.to_node_id]   ?: edge.to_node_id
                
                if (fromId.isBlank() || toId.isBlank()) return@forEachIndexed
                
                val normalizedEdge = edge.copy(from_node_id = fromId, to_node_id = toId)
                val edgeDocId = "${fromId}__${toId}__$index"
                batch.set(universeRef.collection("edges").document(edgeDocId), normalizedEdge)
            }

            // 4. Set Tags — one doc per route actually present, so a branching
            // import (e.g. Fate's UBW / HF routes) gets real, matching filter
            // chips instead of generic ones that don't correspond to anything.
            val tagsRef = universeRef.collection("tags")
            val knownLabels = mapOf(
                "ALL" to "All Content",
                "CANON" to "Canon Only",
                "ESSENTIAL" to "Essential"
            )
            val palette = listOf("#888899", "#4ADE80", "#60A5FA", "#F97316", "#C084FC", "#F472B6")
            routeTags.forEachIndexed { index, tagId ->
                batch.set(
                    tagsRef.document(tagId),
                    mapOf(
                        "label" to (knownLabels[tagId] ?: tagId.lowercase().replaceFirstChar { it.uppercase() }),
                        "order" to index,
                        "color" to palette[index % palette.size]
                    )
                )
            }

            batch.commit().await()

            // ─── Smart Notification: Notify the author of the import ──────────────
            if (uid != post.userId) {
                runCatching {
                    val notif = com.example.watchorderengine.data.model.Notification(
                        userId = post.userId,
                        type = com.example.watchorderengine.data.model.NotificationType.IMPORT,
                        title = "Someone imported your graph!",
                        message = "Your watch order for '${post.universeTitle}' was added to their collection.",
                        senderId = uid,
                        senderName = userPrefs.username.first(),
                        senderAvatarUrl = userPrefs.avatarUrl.first(),
                        targetId = post.postId
                    )
                    firestore.collection("notifications").add(notif).await()
                }
            }

            newUniverseId
        }.onFailure { e ->
            Log.w(TAG, "importTimeline failed: ${e.message}")
        }
    }
}
