package com.example.watchorderengine.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.example.watchorderengine.data.model.UserProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.toObject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "UserProfileRepository"
private const val COLLECTION_USER_PROFILES = "user_profiles"

@Singleton
class UserProfileRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    @ApplicationContext private val context: Context
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

    /**
     * Converts a local image to a highly compressed Base64 string to store 
     * directly in Firestore. This bypasses the need for Firebase Storage.
     */
    suspend fun processAvatarToBase64(bitmap: Bitmap): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            // Resize to 256x256 max for profile icon (Firestore friendly)
            val scaled = if (bitmap.width > 256 || bitmap.height > 256) {
                Bitmap.createScaledBitmap(bitmap, 256, 256, true)
            } else bitmap

            val outputStream = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
            val bytes = outputStream.toByteArray()
            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            
            "data:image/jpeg;base64,$base64"
        }
    }
}
