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
        
        val query = firestore.collection(COLLECTION_GLOBAL_FEED)
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(FEED_LIMIT)

        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "Global feed listener error: ${error.message}")
                trySend(Result.failure(error))
                return@addSnapshotListener
            }

            val userPosts = snapshot?.documents?.mapNotNull { doc ->
                try {
                    doc.toObject<CommunityPost>()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse post ${doc.id}: ${e.message}")
                    null
                }
            } ?: emptyList()

            // Merge predefined at top, then user posts. Distinct by postId to avoid duplicates.
            val combined = (predefined + userPosts).distinctBy { it.postId }
            trySend(Result.success(combined))
        }

        awaitClose {
            Log.d(TAG, "Removing global feed listener")
            registration.remove()
        }
    }.flowOn(Dispatchers.IO)

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

            val post = CommunityPost(
                userId              = uid,
                authorName          = username,
                authorAvatarUrl     = avatarUrl,
                universeTitle       = title,
                universeDescription = description,
                nodesJson           = nodesJson,
                likesCount          = 0,
                likedByUsers        = emptyList(),
                timestamp           = System.currentTimeMillis(),
            )

            firestore.collection(COLLECTION_GLOBAL_FEED).add(post).await()
            Log.d(TAG, "Shared timeline '$title' to global feed")
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

            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(postRef)
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

            Unit
        }.onFailure { e ->
            Log.w(TAG, "toggleLikePost failed for $postId: ${e.message}")
        }
    }

    /**
     * Deletes a post the current user authored.
     */
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

            // 1. Create Universe Metadata
            val universeRef = firestore.collection("universes").document(newUniverseId)
            batch.set(universeRef, mapOf(
                "id" to newUniverseId,
                "name" to post.universeTitle,
                "description" to post.universeDescription + " (Imported from ${post.authorName})",
                "posterUrl" to (payload.nodes.firstOrNull()?.posterUrl ?: ""),
                "total_nodes" to payload.nodes.size,
                "available_routes" to listOf("ALL", "CANON", "ESSENTIAL"),
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

            // 4. Set Default Tags
            val tagsRef = universeRef.collection("tags")
            val defaultTags = listOf(
                "ALL" to mapOf("label" to "All Content", "order" to 0, "color" to "#888899"),
                "CANON" to mapOf("label" to "Canon Only", "order" to 1, "color" to "#4ADE80"),
                "ESSENTIAL" to mapOf("label" to "Essential", "order" to 2, "color" to "#60A5FA")
            )
            defaultTags.forEach { (tagId, data) ->
                batch.set(tagsRef.document(tagId), data)
            }

            batch.commit().await()
            newUniverseId
        }.onFailure { e ->
            Log.w(TAG, "importTimeline failed: ${e.message}")
        }
    }

    suspend fun deletePost(postId: String, authorUserId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val uid = auth.currentUser?.uid
                    ?: throw IllegalStateException("Not authenticated.")
                if (uid != authorUserId) {
                    throw IllegalStateException("You can only delete your own posts.")
                }
                firestore.collection(COLLECTION_GLOBAL_FEED).document(postId).delete().await()
                Unit
            }
        }
}
