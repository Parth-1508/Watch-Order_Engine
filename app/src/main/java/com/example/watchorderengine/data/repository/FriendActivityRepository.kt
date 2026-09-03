package com.example.watchorderengine.data.repository

import android.util.Log
import com.example.watchorderengine.data.model.ActivityEvent
import com.example.watchorderengine.data.model.Edge
import com.example.watchorderengine.data.model.FollowRecord
import com.example.watchorderengine.data.model.MediaNode
import com.example.watchorderengine.data.model.Notification
import com.example.watchorderengine.data.model.NotificationType
import com.example.watchorderengine.data.model.ReviewDocument
import com.example.watchorderengine.data.model.SharedTimelineCodec
import com.example.watchorderengine.data.model.UniverseComment
import com.example.watchorderengine.data.model.UserProfile
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Represents one row in the Friend Activity feed — either a completion
 * ([ActivityEvent]) or a rating ([ReviewDocument]).
 */
sealed class FriendActivityItem {
    abstract val timestampMillis: Long
    abstract val authorName: String
    abstract val authorAvatarUrl: String?

    data class Completion(val event: ActivityEvent) : FriendActivityItem() {
        override val timestampMillis get() = event.createdAt?.toDate()?.time ?: 0L
        override val authorName get() = event.authorName
        override val authorAvatarUrl get() = event.authorAvatarUrl
    }

    data class Rating(
        val review: ReviewDocument,
        override val authorName: String,
        override val authorAvatarUrl: String?
    ) : FriendActivityItem() {
        override val timestampMillis get() = review.createdAt?.toDate()?.time ?: 0L
    }
}

/**
 * Repository for discussion comments, following relationships, friend activity feeds,
 * and direct timeline sharing.
 */
@Singleton
class FriendActivityRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private val TAG = "FriendActivityRepo"

    // ─── Following ──────────────────────────────────────────────────────────

    private fun followingRef(ownerUid: String) =
        firestore.collection("users").document(ownerUid).collection("following")

    suspend fun followUser(ownerUid: String, targetUid: String, targetDisplayName: String, targetAvatarUrl: String?) {
        try {
            followingRef(ownerUid).document(targetUid).set(
                FollowRecord(followedUserId = targetUid, followedDisplayName = targetDisplayName, followedAvatarUrl = targetAvatarUrl)
            ).await()
        } catch (e: Exception) {
            Log.w(TAG, "followUser failed: ${e.message}")
        }
    }

    suspend fun unfollowUser(ownerUid: String, targetUid: String) {
        try {
            followingRef(ownerUid).document(targetUid).delete().await()
        } catch (e: Exception) {
            Log.w(TAG, "unfollowUser failed: ${e.message}")
        }
    }

    fun observeFollowing(ownerUid: String): Flow<List<FollowRecord>> = callbackFlow {
        val registration = followingRef(ownerUid).addSnapshotListener { snapshot, error ->
            if (error != null) { Log.w(TAG, "observeFollowing error: ${error.message}"); return@addSnapshotListener }
            trySend(snapshot?.documents?.mapNotNull { it.toObject(FollowRecord::class.java) } ?: emptyList())
        }
        awaitClose { registration.remove() }
    }.flowOn(Dispatchers.IO)

    suspend fun observeFollowingOnce(ownerUid: String): List<FollowRecord> = try {
        followingRef(ownerUid).get().await().documents.mapNotNull { it.toObject(FollowRecord::class.java) }
    } catch (e: Exception) {
        Log.w(TAG, "observeFollowingOnce failed: ${e.message}")
        emptyList()
    }

    // ─── Comments ───────────────────────────────────────────────────────────

    private fun commentsRef(universeId: String) =
        firestore.collection("universes").document(universeId).collection("comments")

    suspend fun postComment(
        universeId: String, authorId: String, authorName: String, authorAvatarUrl: String?,
        text: String, parentCommentId: String? = null
    ): Result<Unit> = runCatching {
        commentsRef(universeId).add(
            UniverseComment(
                universeId = universeId, parentCommentId = parentCommentId,
                authorId = authorId, authorName = authorName, authorAvatarUrl = authorAvatarUrl,
                text = text
            )
        ).await()
        Unit
    }

    fun observeComments(universeId: String): Flow<List<UniverseComment>> = callbackFlow {
        val registration = commentsRef(universeId)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { Log.w(TAG, "observeComments error: ${error.message}"); return@addSnapshotListener }
                trySend(snapshot?.documents?.mapNotNull { it.toObject(UniverseComment::class.java) } ?: emptyList())
            }
        awaitClose { registration.remove() }
    }.flowOn(Dispatchers.IO)

    // ─── Activity events (completions) ─────────────────────────────────────

    suspend fun recordCompletion(userId: String, authorName: String, authorAvatarUrl: String?, mediaId: String, mediaTitle: String, mediaPosterUrl: String?, episodeCount: Int) {
        try {
            firestore.collection("activity_events").add(
                ActivityEvent(
                    userId = userId, authorName = authorName, authorAvatarUrl = authorAvatarUrl,
                    type = ActivityEvent.EventType.COMPLETED.name,
                    mediaId = mediaId, mediaTitle = mediaTitle, mediaPosterUrl = mediaPosterUrl,
                    episodeCount = episodeCount
                )
            ).await()
        } catch (e: Exception) {
            Log.w(TAG, "recordCompletion failed (non-fatal): ${e.message}")
        }
    }

    // ─── Merged feed ─────────────────────────────────────────────────────────

    private fun <T> List<T>.chunkedForIn(): List<List<T>> = chunked(30)

    suspend fun getFriendActivity(followingIds: List<String>, limit: Long = 50): List<FriendActivityItem> {
        if (followingIds.isEmpty()) return emptyList()

        val completions = mutableListOf<ActivityEvent>()
        val ratings = mutableListOf<ReviewDocument>()

        for (chunk in followingIds.chunkedForIn()) {
            try {
                val eventsSnap = firestore.collection("activity_events")
                    .whereIn("userId", chunk)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .limit(limit)
                    .get().await()
                completions += eventsSnap.documents.mapNotNull { it.toObject(ActivityEvent::class.java) }

                val reviewsSnap = firestore.collection("reviews")
                    .whereIn("user_id", chunk)
                    .orderBy("created_at", Query.Direction.DESCENDING)
                    .limit(limit)
                    .get().await()
                ratings += reviewsSnap.documents.mapNotNull { it.toObject(ReviewDocument::class.java) }
            } catch (e: Exception) {
                Log.w(TAG, "getFriendActivity chunk failed: ${e.message}")
            }
        }

        val profileById = mutableMapOf<String, UserProfile>()
        for (chunk in ratings.map { it.userId }.distinct().chunkedForIn()) {
            if (chunk.isEmpty()) continue
            try {
                val profilesSnap = firestore.collection("user_profiles")
                    .whereIn(FieldPath.documentId(), chunk)
                    .get().await()
                profilesSnap.documents.forEach { doc ->
                    doc.toObject(UserProfile::class.java)?.let { profileById[doc.id] = it }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Author profile batch lookup failed: ${e.message}")
            }
        }

        val ratingItems = ratings.map { review ->
            val profile = profileById[review.userId]
            FriendActivityItem.Rating(
                review = review,
                authorName = profile?.displayName?.takeIf { it.isNotBlank() } ?: "Someone",
                authorAvatarUrl = profile?.avatarUrl
            )
        }

        return (completions.map { FriendActivityItem.Completion(it) } + ratingItems)
            .sortedByDescending { it.timestampMillis }
            .take(limit.toInt())
    }

    // ─── Direct timeline sharing ─────────────────────────────────────────────

    private fun sharedTimelinesRef(recipientUid: String) =
        firestore.collection("users").document(recipientUid).collection("shared_timelines")

    suspend fun shareTimelineWithFriend(
        senderUid: String,
        senderName: String,
        senderAvatarUrl: String?,
        recipientUid: String,
        timelineTitle: String,
        timelineCoverUrl: String?,
        nodes: List<MediaNode>,
        edges: List<Edge>
    ): Result<Unit> = runCatching {
        val nodesJson = SharedTimelineCodec.encode(nodes, edges)

        val shareDoc = mapOf(
            "senderId" to senderUid,
            "senderName" to senderName,
            "senderAvatarUrl" to senderAvatarUrl,
            "timelineTitle" to timelineTitle,
            "timelineCoverUrl" to timelineCoverUrl,
            "nodesJson" to nodesJson,
            "createdAt" to FieldValue.serverTimestamp()
        )
        val shareRef = sharedTimelinesRef(recipientUid).add(shareDoc).await()

        firestore.collection("notifications").add(
            Notification(
                userId = recipientUid,
                type = NotificationType.TIMELINE_SHARE,
                title = "$senderName sent you a timeline",
                message = timelineTitle,
                targetId = shareRef.id,
                senderId = senderUid,
                senderName = senderName,
                senderAvatarUrl = senderAvatarUrl,
                imageUrl = timelineCoverUrl
            )
        ).await()
        Unit
    }

    suspend fun getSharedTimeline(recipientUid: String, shareId: String): Map<String, Any?>? = try {
        sharedTimelinesRef(recipientUid).document(shareId).get().await().data
    } catch (e: Exception) {
        Log.w(TAG, "getSharedTimeline failed: ${e.message}")
        null
    }
}
