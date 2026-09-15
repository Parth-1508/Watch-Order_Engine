package com.example.watchorderengine.data.repository

import android.util.Log
import com.example.watchorderengine.data.model.ActivityType
import com.example.watchorderengine.data.model.FollowRelation
import com.example.watchorderengine.data.model.Notification
import com.example.watchorderengine.data.model.NotificationType
import com.example.watchorderengine.data.model.UserActivity
import com.example.watchorderengine.data.prefs.UserPreferencesRepository
import com.google.firebase.auth.FirebaseAuth
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

private const val TAG = "FriendRepository"
private const val COLLECTION_FOLLOWS = "follows"
private const val COLLECTION_USER_PROFILES = "user_profiles"
private const val COLLECTION_ACTIVITY = "activity"

private const val MAX_WHERE_IN = 30

@Singleton
class FriendRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val userPrefs: UserPreferencesRepository,
) {
    private fun followDocId(followerId: String, followingId: String) = "${followerId}_$followingId"

    private suspend fun currentDisplayName(): String {
        val fromPrefs = userPrefs.username.first()
        val firebaseUser = auth.currentUser
        return when {
            fromPrefs != "Player One" && fromPrefs != "Guest" && fromPrefs.isNotBlank() -> fromPrefs
            !firebaseUser?.displayName.isNullOrBlank() -> firebaseUser?.displayName ?: "Explorer"
            else -> "Explorer"
        }
    }

    private suspend fun currentAvatarUrl(): String? =
        userPrefs.avatarUrl.first() ?: auth.currentUser?.photoUrl?.toString()

    // ─── Following ──────────────────────────────────────────────────────────

    suspend fun isFollowing(targetUserId: String): Boolean = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: return@withContext false
        if (uid == targetUserId) return@withContext false
        try {
            firestore.collection(COLLECTION_FOLLOWS)
                .document(followDocId(uid, targetUserId))
                .get().await().exists()
        } catch (e: Exception) {
            Log.w(TAG, "isFollowing check failed: ${e.message}")
            false
        }
    }

    suspend fun followUser(targetUserId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val uid = auth.currentUser?.uid ?: throw IllegalStateException("Not authenticated — cannot follow.")
            if (uid == targetUserId) throw IllegalArgumentException("Can't follow yourself.")

            val username = currentDisplayName()
            val avatarUrl = currentAvatarUrl()

            firestore.collection(COLLECTION_FOLLOWS)
                .document(followDocId(uid, targetUserId))
                .set(
                    FollowRelation(
                        followerId = uid,
                        followingId = targetUserId,
                        followerName = username,
                        followerAvatarUrl = avatarUrl,
                        timestamp = System.currentTimeMillis()
                    )
                )
                .await()

            runCatching {
                val notif = Notification(
                    userId = targetUserId,
                    type = NotificationType.FOLLOW,
                    title = "New follower!",
                    message = "$username started following you.",
                    senderId = uid,
                    senderName = username,
                    senderAvatarUrl = avatarUrl,
                    targetId = uid
                )
                firestore.collection("notifications").add(notif).await()
            }
            Unit
        }.onFailure { e ->
            Log.w(TAG, "followUser failed: ${e.message}")
        }
    }

    suspend fun unfollowUser(targetUserId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val uid = auth.currentUser?.uid ?: throw IllegalStateException("Not authenticated")
            firestore.collection(COLLECTION_FOLLOWS)
                .document(followDocId(uid, targetUserId))
                .delete().await()
            Unit
        }.onFailure { e ->
            Log.w(TAG, "unfollowUser failed: ${e.message}")
        }
    }

    fun observeFollowing(): Flow<List<FollowRelation>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val registration = firestore.collection(COLLECTION_FOLLOWS)
            .whereEqualTo("followerId", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "observeFollowing failed: ${error.message}")
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val relations = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        doc.toObject<FollowRelation>()?.apply { followId = doc.id }
                    } catch (e: Exception) {
                        null
                    }
                } ?: emptyList()
                trySend(relations)
            }
        awaitClose { registration.remove() }
    }.flowOn(Dispatchers.IO)

    // ─── Activity feed ──────────────────────────────────────────────────────

    suspend fun getFriendActivity(): Result<List<UserActivity>> = withContext(Dispatchers.IO) {
        runCatching {
            val uid = auth.currentUser?.uid ?: return@withContext Result.success(emptyList())

            val followedIds = firestore.collection(COLLECTION_FOLLOWS)
                .whereEqualTo("followerId", uid)
                .get().await()
                .documents.mapNotNull { it.getString("followingId") }
                .take(MAX_WHERE_IN)

            if (followedIds.isEmpty()) return@withContext Result.success(emptyList())

            val publicIds = firestore.collection(COLLECTION_USER_PROFILES)
                .whereIn("userId", followedIds)
                .whereEqualTo("isActivityPublic", true)
                .get().await()
                .documents.mapNotNull { it.getString("userId") }

            if (publicIds.isEmpty()) return@withContext Result.success(emptyList())

            val activitySnapshot = firestore.collectionGroup(COLLECTION_ACTIVITY)
                .whereIn("userId", publicIds)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(50)
                .get().await()

            activitySnapshot.documents.mapNotNull { doc ->
                try {
                    doc.toObject<UserActivity>()?.apply { activityId = doc.id }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse activity ${doc.id}: ${e.message}")
                    null
                }
            }
        }.onFailure { e ->
            Log.w(TAG, "getFriendActivity failed: ${e.message}")
        }
    }

    suspend fun recordActivity(
        type: ActivityType,
        mediaId: String,
        mediaTitle: String,
        mediaPosterUrl: String?,
        rating: Float? = null,
    ) = withContext(Dispatchers.IO) {
        try {
            val uid = auth.currentUser?.uid ?: return@withContext
            val activity = UserActivity(
                userId = uid,
                userName = currentDisplayName(),
                userAvatarUrl = currentAvatarUrl(),
                type = type,
                mediaId = mediaId,
                mediaTitle = mediaTitle,
                mediaPosterUrl = mediaPosterUrl,
                rating = rating,
                timestamp = System.currentTimeMillis()
            )
            firestore.collection(COLLECTION_USER_PROFILES).document(uid)
                .collection(COLLECTION_ACTIVITY).add(activity).await()
        } catch (e: Exception) {
            Log.w(TAG, "recordActivity failed: ${e.message}")
        }
    }
}
