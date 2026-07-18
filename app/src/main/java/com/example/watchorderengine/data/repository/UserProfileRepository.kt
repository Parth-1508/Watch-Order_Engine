package com.example.watchorderengine.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.example.watchorderengine.R
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
            if (userId == "woe_admin") {
                trySend(Result.success(
                    UserProfile(
                        userId = "woe_admin",
                        displayName = "Watch Order Engine",
                        avatarUrl = "woe_internal_avatar",
                        isStatsPublic = true
                    )
                ))
                close()
                return@callbackFlow
            }

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
        if (userId == "woe_admin") {
            return@withContext Result.success(
                UserProfile(
                    userId = "woe_admin",
                    displayName = "Watch Order Engine",
                    avatarUrl = "woe_internal_avatar",
                    isStatsPublic = true,
                    isFavoritesPublic = false
                )
            )
        }
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
            // Resize to 180x180 for profile icon (Tiny, fast, fits Firestore 1MB easily)
            val scaled = if (bitmap.width > 180 || bitmap.height > 180) {
                Bitmap.createScaledBitmap(bitmap, 180, 180, true)
            } else bitmap

            val outputStream = ByteArrayOutputStream()
            // High compression for thumbnails
            scaled.compress(Bitmap.CompressFormat.JPEG, 60, outputStream)
            val bytes = outputStream.toByteArray()
            val base64 = Base64.encodeToString(bytes, Base64.DEFAULT)
            
            "data:image/jpeg;base64,$base64"
        }
    }

    /**
     * Helper to prepare a model for Coil's AsyncImage. 
     * If the string is a Base64 data URI, it decodes it to a ByteArray which Coil prefers.
     */
    fun getAvatarModel(url: String?): Any? {
        if (url == null) return null
        if (url == "woe_internal_avatar") return R.drawable.ic_launcher_foreground
        return if (url.startsWith("data:image/jpeg;base64,")) {
            try {
                val base64Data = url.substringAfter("base64,")
                Base64.decode(base64Data, Base64.DEFAULT)
            } catch (e: Exception) {
                url // Fallback to raw string
            }
        } else {
            url
        }
    }
}
