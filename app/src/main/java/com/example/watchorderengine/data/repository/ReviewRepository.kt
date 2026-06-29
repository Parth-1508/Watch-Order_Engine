package com.example.watchorderengine.data.repository

import com.example.watchorderengine.data.db.dao.ReviewDao
import com.example.watchorderengine.data.WatchOrderRepository
import com.example.watchorderengine.data.db.entity.PendingSyncTaskEntity
import com.example.watchorderengine.data.db.entity.ReviewEntity
import com.example.watchorderengine.data.db.entity.TaskType
import com.example.watchorderengine.data.model.ReviewDocument
import com.example.watchorderengine.data.model.toFirestoreDocument
import com.example.watchorderengine.data.model.toRoomEntity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.toObject
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReviewRepository @Inject constructor(
    private val reviewDao: ReviewDao,
    private val mediaDao: com.example.watchorderengine.data.db.dao.MediaDao,
    private val db: com.example.watchorderengine.data.db.WatchOrderDatabase,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val userPrefs: com.example.watchorderengine.data.prefs.UserPreferencesRepository,
    private val watchOrderRepository: WatchOrderRepository
) {

    fun observeReviewsForMedia(mediaId: String): Flow<List<ReviewEntity>> =
        reviewDao.observeReviewsForMedia(mediaId)

    fun observeReviewsByUser(userId: String): Flow<List<ReviewEntity>> =
        reviewDao.observeReviewsByUser(userId)

    suspend fun submitReview(
        mediaId: String,
        rating: Float,
        text: String,
        hasSpoilers: Boolean,
        context: Context
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: return@withContext Result.failure(Exception("Not authenticated"))
        val username = userPrefs.username.first()
        val avatarUrl = userPrefs.avatarUrl.first()
        val media = mediaDao.getById(mediaId)

        val reviewId = UUID.randomUUID().toString()
        val entity = ReviewEntity(
            id = reviewId,
            mediaId = mediaId,
            mediaTitle = media?.title ?: "Unknown Title",
            mediaPosterUrl = media?.posterUrl,
            userId = uid,
            rating = rating,
            reviewText = text,
            hasSpoilers = hasSpoilers,
            isSynced = false
        )

        // 1. Save to Room
        reviewDao.upsert(entity)

        // 2. Sync gate
        val syncEnabled = userPrefs.cloudSyncEnabled.first()
        if (!syncEnabled) return@withContext Result.success(Unit)

        // 3. Online: sync immediately
        if (watchOrderRepository.isNetworkAvailable(context)) {
            try {
                val doc = entity.toFirestoreDocument(username, avatarUrl)
                firestore.collection("users").document(uid)
                    .collection("reviews").document(reviewId)
                    .set(doc, SetOptions.merge())
                    .await()
                
                reviewDao.markSynced(reviewId)
                Result.success(Unit)
            } catch (e: Exception) {
                queueReviewSync(reviewId, context)
                Result.success(Unit)
            }
        } else {
            // 4. Offline: queue for later
            queueReviewSync(reviewId, context)
            Result.success(Unit)
        }
    }

    private suspend fun queueReviewSync(reviewId: String, context: Context) {
        db.pendingSyncTaskDao().insert(
            PendingSyncTaskEntity(
                taskType = TaskType.REVIEW_SUBMISSION,
                reviewId = reviewId
            )
        )
        com.example.watchorderengine.data.sync.SyncWorker.enqueue(context)
    }

    /** Called by SyncWorker — bypasses connectivity check. */
    internal suspend fun syncReviewDirect(reviewId: String): Result<Unit> = runCatching {
        val entity = reviewDao.getById(reviewId) ?: return@runCatching
        val uid = auth.currentUser?.uid ?: error("Not authenticated")
        val username = userPrefs.username.first()
        val avatarUrl = userPrefs.avatarUrl.first()

        val doc = entity.toFirestoreDocument(username, avatarUrl)
        firestore.collection("users").document(uid)
            .collection("reviews").document(reviewId)
            .set(doc, SetOptions.merge())
            .await()
        
        reviewDao.markSynced(reviewId)
    }

    suspend fun deleteReview(reviewId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: return@withContext Result.failure(Exception("Not authenticated"))
        
        // 1. Delete from Room
        reviewDao.deleteById(reviewId)

        // 2. Delete from Firestore
        try {
            firestore.collection("users").document(uid)
                .collection("reviews").document(reviewId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncReviewsFromFirestore(): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: return@withContext Result.failure(Exception("Not authenticated"))
        try {
            val snapshot = firestore.collection("users").document(uid)
                .collection("reviews").get().await()
            
            val reviews = snapshot.documents.mapNotNull { it.toObject<ReviewDocument>()?.toRoomEntity() }
            reviews.forEach { reviewDao.upsert(it) }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
