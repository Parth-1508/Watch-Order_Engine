package com.example.watchorderengine.data.repository

import android.util.Log
import com.example.watchorderengine.data.model.Notification
import com.example.watchorderengine.data.model.NotificationType
import com.example.watchorderengine.util.NotificationHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.toObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "NotificationRepository"
private const val COLLECTION_NOTIFICATIONS = "notifications"

@Singleton
class NotificationRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val notificationHelper: NotificationHelper
) {

    fun observeNotifications(): Flow<Result<List<Notification>>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(Result.success(emptyList()))
            close()
            return@callbackFlow
        }

        val query = firestore.collection(COLLECTION_NOTIFICATIONS)
            .whereEqualTo("userId", uid)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(50)

        var isFirstSnapshot = true

        val listener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "Listen failed", error)
                trySend(Result.failure(error))
                return@addSnapshotListener
            }

            val list = snapshot?.documents?.mapNotNull { doc ->
                doc.toObject<Notification>()?.apply { id = doc.id }
            } ?: emptyList()

            if (isFirstSnapshot) {
                // The initial full result set — never fire system notifications
                // from this one, it's a catch-up read, not new activity.
                isFirstSnapshot = false
            } else {
                snapshot?.documentChanges
                    ?.filter { it.type == DocumentChange.Type.ADDED }
                    ?.forEach { change ->
                        val notif = change.document.toObject<Notification>()?.apply {
                            id = change.document.id
                        } ?: return@forEach

                        if (!notif.isRead) {
                            notificationHelper.showNotification(
                                id = notif.id.hashCode(),
                                title = notif.title,
                                message = notif.message,
                                targetId = notif.targetId
                            )
                        }
                    }
            }

            trySend(Result.success(list))
        }

        awaitClose { listener.remove() }
    }.flowOn(Dispatchers.IO)

    suspend fun markAsRead(notificationId: String) = withContext(Dispatchers.IO) {
        runCatching {
            firestore.collection(COLLECTION_NOTIFICATIONS)
                .document(notificationId)
                .update("isRead", true)
                .await()
        }
    }

    suspend fun markAllAsRead() = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: return@withContext
        runCatching {
            val unread = firestore.collection(COLLECTION_NOTIFICATIONS)
                .whereEqualTo("userId", uid)
                .whereEqualTo("isRead", false)
                .get()
                .await()

            if (unread.isEmpty) return@runCatching

            val batch = firestore.batch()
            unread.documents.forEach { doc ->
                batch.update(doc.reference, "isRead", true)
            }
            batch.commit().await()
        }
    }

    suspend fun deleteNotification(notificationId: String) = withContext(Dispatchers.IO) {
        runCatching {
            firestore.collection(COLLECTION_NOTIFICATIONS)
                .document(notificationId)
                .delete()
                .await()
        }
    }

    /**
     * Client-side "Smart" notification generator.
     * In a real app, this would be a Cloud Function, but for this feature 
     * we can synthesize personalized ones based on local state.
     */
    suspend fun sendSystemNotification(
        type: NotificationType,
        title: String,
        message: String,
        targetId: String? = null,
        imageUrl: String? = null
    ) = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: return@withContext
        runCatching {
            val notif = Notification(
                userId = uid,
                type = type,
                title = title,
                message = message,
                timestamp = System.currentTimeMillis(),
                isRead = false,
                targetId = targetId,
                imageUrl = imageUrl
            )
            firestore.collection(COLLECTION_NOTIFICATIONS).add(notif).await()
        }
    }
}
