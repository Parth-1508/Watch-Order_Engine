package com.example.watchorderengine.data.repository

import android.net.Uri
import android.util.Log
import com.example.watchorderengine.data.model.UserProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.toObject
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "UserProfileRepository"
private const val COLLECTION_USER_PROFILES = "user_profiles"
private const val STORAGE_AVATARS_PATH = "avatars"

@Singleton
class UserProfileRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage,
    private val auth: FirebaseAuth,
) {

    fun observeProfile(userId: String): Flow<Result<UserProfile?>> =
        callbackFlow<Result<UserProfile?>> {
            val registration = firestore.collection(COLLECTION_USER_PROFILES)
                .document(userId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "observeProfile($userId) failed: ${error.message}")
                        trySend(Result.failure(error))
                        return@addSnapshotListener
                    }
                    val profile = try {
                        snapshot?.toObject<UserProfile>()
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse profile for $userId: ${e.message}")
                        null
                    }
                    trySend(Result.success(profile))
                }
            awaitClose { registration.remove() }
        }.flowOn(Dispatchers.IO)

    suspend fun getProfile(userId: String): Result<UserProfile?> = withContext(Dispatchers.IO) {
        runCatching {
            firestore.collection(COLLECTION_USER_PROFILES)
                .document(userId)
                .get()
                .await()
                .toObject<UserProfile>()
        }.onFailure { e ->
            Log.w(TAG, "getProfile($userId) failed: ${e.message}")
        }
    }

    suspend fun saveProfile(profile: UserProfile): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val uid = auth.currentUser?.uid
                ?: throw IllegalStateException("Not authenticated — cannot save profile.")
            require(profile.userId.isBlank() || profile.userId == uid) {
                "Cannot save a profile document for another user."
            }
            firestore.collection(COLLECTION_USER_PROFILES)
                .document(uid)
                .set(profile.copy(userId = uid))
                .await()
            Unit
        }.onFailure { e ->
            Log.w(TAG, "saveProfile failed: ${e.message}")
        }
    }

    suspend fun uploadAvatar(localImageUri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val uid = auth.currentUser?.uid
                ?: throw IllegalStateException("Not authenticated — cannot upload avatar.")
            val ref = storage.reference.child("$STORAGE_AVATARS_PATH/$uid.jpg")
            ref.putFile(localImageUri).await()
            ref.downloadUrl.await().toString()
        }.onFailure { e ->
            Log.w(TAG, "uploadAvatar failed: ${e.message}")
        }
    }
}
