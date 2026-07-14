package com.example.watchorderengine.data.repository

import com.example.watchorderengine.data.db.dao.ReviewDao
import com.example.watchorderengine.data.WatchOrderRepository
import com.example.watchorderengine.data.db.entity.PendingSyncTaskEntity
import com.example.watchorderengine.data.db.entity.ReviewEntity
import com.example.watchorderengine.data.db.entity.TaskType
import com.example.watchorderengine.data.model.*
import com.example.watchorderengine.network.AnilistApiService
import com.example.watchorderengine.network.AnilistRequest
import com.example.watchorderengine.network.JikanApiService
import com.example.watchorderengine.network.TmdbApiService
import com.example.watchorderengine.network.TmdbConfig
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.toObject
import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
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
    private val watchOrderRepository: WatchOrderRepository,
    private val tmdbApi: TmdbApiService,
    private val anilistApi: AnilistApiService,
    private val jikanApi: JikanApiService
) {

    fun observeReviewsForMedia(mediaId: String): Flow<List<ReviewItem>> =
        firestore.collection("reviews")
            .whereEqualTo("media_id", mediaId)
            .orderBy("updated_at", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { it.toObject<ReviewDocument>()?.toReviewItem() }
            }

    fun observeReviewsByUser(userId: String): Flow<List<ReviewEntity>> =
        reviewDao.observeReviewsByUser(userId)

    fun observeGlobalAverageRating(): Flow<Float?> =
        reviewDao.observeGlobalAverageRating()

    suspend fun submitReview(
        mediaId: String,
        rating: Float,
        text: String,
        hasSpoilers: Boolean,
        emojiReaction: String,
        context: Context
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: return@withContext Result.failure(Exception("Not authenticated"))
        val username = userPrefs.username.first()
        val avatarUrl = userPrefs.avatarUrl.first()
        val media = mediaDao.getById(mediaId)

        val reviewId = UUID.randomUUID().toString()
        val timestamp = com.google.firebase.Timestamp.now()
        
        val doc = ReviewDocument(
            id = reviewId,
            mediaId = mediaId,
            mediaTitle = media?.title ?: "Unknown Title",
            mediaPosterUrl = media?.posterUrl,
            userId = uid,
            rating = rating.toDouble(),
            reviewText = text,
            hasSpoilers = hasSpoilers,
            authorName = username,
            authorAvatarUrl = avatarUrl,
            createdAt = timestamp,
            updatedAt = timestamp,
            emojiReaction = emojiReaction
        )

        // 1. Save to Room (Keep local for offline/history)
        val entity = doc.toRoomEntity().copy(isSynced = false)
        reviewDao.upsert(entity)

        // 2. Sync gate
        val syncEnabled = userPrefs.cloudSyncEnabled.first()
        if (!syncEnabled) return@withContext Result.success(Unit)

        // 3. Online: sync immediately to GLOBAL collection
        if (watchOrderRepository.isNetworkAvailable(context)) {
            try {
                firestore.collection("reviews").document(reviewId)
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
        firestore.collection("reviews").document(reviewId)
            .set(doc, SetOptions.merge())
            .await()
        
        reviewDao.markSynced(reviewId)
    }

    suspend fun deleteReview(reviewId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: return@withContext Result.failure(Exception("Not authenticated"))
        
        // 1. Delete from Room
        reviewDao.deleteById(reviewId)

        // 2. Delete from Firestore (Global collection)
        try {
            firestore.collection("reviews").document(reviewId)
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

    // ─── External Reviews ───────────────────────────────────────────────────────

    suspend fun getAggregatedReviews(mediaId: String): List<ReviewItem> = withContext(Dispatchers.IO) {
        val media = mediaDao.getById(mediaId) ?: return@withContext emptyList()
        val category = media.mediaCategory.uppercase()
        val isMovie = category == "MOVIE" || category == "SHORT"
        val tmdbId = media.tmdbId
        
        // Resolve IDs
        var anilistId = media.anilistId
        var malIdToUse: Int? = null

        // Lenient Anime check: Animation genre OR Japanese origin OR Category ANIME
        val isAnime = media.genres.contains("Animation") || 
                     media.mediaCategory == "ANIME" || 
                     media.originalLanguage == "ja"

        val userAvatar = userPrefs.avatarUrl.first()
        val userName = userPrefs.username.first()
        val localReviews = reviewDao.getReviewsForMedia(mediaId).map { it.toReviewItem(userName, userAvatar) }
        val externalReviews = mutableListOf<ReviewItem>()

        // 1. TMDB Reviews
        try {
            val tmdbResponse = if (isMovie) tmdbApi.getMovieReviews(tmdbId) else tmdbApi.getTvReviews(tmdbId)
            if (tmdbResponse.isSuccessful) {
                tmdbResponse.body()?.results?.map {
                    val avatarPath = it.authorDetails?.avatarPath
                    val avatarUrl = when {
                        avatarPath.isNullOrEmpty() -> "https://ui-avatars.com/api/?name=${it.author}"
                        avatarPath.startsWith("/") -> "https://image.tmdb.org/t/p/w200$avatarPath"
                        else -> avatarPath
                    }
                    ReviewItem(
                        id = "tmdb_${it.id}",
                        authorName = it.author,
                        authorAvatarUrl = avatarUrl,
                        rating = it.authorDetails?.rating?.toFloat(), // TMDB is 1-10
                        reviewText = it.content,
                        source = ReviewSource.TMDB,
                        createdAt = try { 
                            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                                timeZone = TimeZone.getTimeZone("UTC")
                            }.parse(it.createdAt)?.time ?: 0L
                        } catch (e: Exception) { 0L },
                        externalUrl = it.url
                    )
                }?.let { externalReviews.addAll(it) }
            }
        } catch (e: Exception) {
            Log.e("ReviewRepo", "TMDB Reviews error for $tmdbId", e)
        }

        // 2. Resolve AniList/MAL IDs if it's potentially an anime
        if (isAnime) {
            val cleanTitle = media.title.replace(Regex("\\(\\d{4}\\)"), "")
                .replace(Regex("\\[.*?\\]"), "")
                .trim()

            if (anilistId == null) {
                try {
                    val searchBody = AnilistRequest(
                        query = "query(${'$'}search: String) { Media(search: ${'$'}search, type: ANIME) { id idMal } }",
                        variables = mapOf("search" to cleanTitle)
                    )
                    val resp = anilistApi.query(searchBody)
                    if (resp.isSuccessful) {
                        val aniMedia = resp.body()?.data?.media
                        if (aniMedia != null) {
                            anilistId = aniMedia.id
                            malIdToUse = aniMedia.idMal
                            db.mediaDao().upsert(media.copy(anilistId = anilistId))
                        }
                    }
                } catch (e: Exception) { /* ignore */ }
            } else {
                // We have anilistId, check if we can get idMal if it's missing
                try {
                    val query = "query(${'$'}id: Int) { Media(id: ${'$'}id) { idMal } }"
                    val resp = anilistApi.query(AnilistRequest(query, mapOf("id" to anilistId)))
                    if (resp.isSuccessful) malIdToUse = resp.body()?.data?.media?.idMal
                } catch (e: Exception) {}
            }
            
            // Tertiary Fallback: Jikan Search
            if (malIdToUse == null) {
                try {
                    val jikanSearch = jikanApi.searchAnime(cleanTitle)
                    if (jikanSearch.isSuccessful) {
                        malIdToUse = jikanSearch.body()?.data?.firstOrNull()?.malId
                    }
                } catch (e: Exception) { /* ignore */ }
            }
        }

        // 3. AniList Reviews
        if (anilistId != null) {
            try {
                val query = """
                    query(${'$'}id: Int) {
                      Media(id: ${'$'}id) {
                        reviews(perPage: 10, sort: [ID_DESC]) {
                          nodes {
                            id
                            summary
                            body
                            score
                            createdAt
                            user { name avatar { large } }
                          }
                        }
                      }
                    }
                """.trimIndent()
                val response = anilistApi.query(AnilistRequest(query, mapOf("id" to anilistId)))
                if (response.isSuccessful) {
                    val mediaData = response.body()?.data?.media
                    mediaData?.reviews?.nodes?.map {
                        ReviewItem(
                            id = "anilist_${it.id}",
                            authorName = it.user?.name ?: "AniList User",
                            authorAvatarUrl = it.user?.avatar?.large,
                            rating = it.score?.toFloat()?.div(10f), // AniList score is 0-100
                            reviewText = it.body ?: it.summary ?: "",
                            source = ReviewSource.ANILIST,
                            createdAt = it.createdAt?.toLong()?.times(1000L) ?: 0L,
                            externalUrl = "https://anilist.co/review/${it.id}"
                        )
                    }?.let { externalReviews.addAll(it) }
                }
            } catch (e: Exception) {
                Log.e("ReviewRepo", "AniList Reviews error for $anilistId", e)
            }
        }

        // 4. MAL (Jikan) Reviews
        if (malIdToUse != null) {
            try {
                val jikanResponse = jikanApi.getAnimeReviews(malIdToUse)
                if (jikanResponse.isSuccessful) {
                    jikanResponse.body()?.data?.map {
                        ReviewItem(
                            id = "mal_${it.malId}",
                            authorName = it.user.username,
                            authorAvatarUrl = it.user.images?.jpg?.imageUrl,
                            rating = it.score.toFloat(), 
                            reviewText = it.review,
                            source = ReviewSource.MAL,
                            createdAt = try { 
                                // MAL dates can be ISO 8601 or simple strings. Try ISO first.
                                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
                                sdf.parse(it.date)?.time ?: 0L
                            } catch (e: Exception) {
                                // Fallback: just use current time or try a simpler format if needed
                                System.currentTimeMillis() 
                            },
                            hasSpoilers = it.isSpoiler,
                            externalUrl = it.url
                        )
                    }?.let { externalReviews.addAll(it) }
                } else {
                    Log.w("ReviewRepo", "MAL Reviews failed: ${jikanResponse.code()} for $malIdToUse")
                }
            } catch (e: Exception) {
                Log.e("ReviewRepo", "MAL Reviews error for $malIdToUse", e)
            }
        }

        (localReviews + externalReviews).sortedByDescending { it.createdAt }
    }

    private fun ReviewEntity.toReviewItem(authorName: String, avatarUrl: String?) = ReviewItem(
        id = id,
        authorName = authorName,
        authorAvatarUrl = avatarUrl,
        rating = rating,
        reviewText = reviewText,
        source = ReviewSource.LOCAL,
        createdAt = updatedAt,
        hasSpoilers = hasSpoilers,
        emojiReaction = "🤩" // Default for old reviews
    )

    fun ReviewDocument.toReviewItem() = ReviewItem(
        id = id,
        authorName = authorName,
        authorAvatarUrl = authorAvatarUrl,
        rating = rating.toFloat(),
        reviewText = reviewText,
        source = ReviewSource.LOCAL,
        createdAt = updatedAt?.toDate()?.time ?: createdAt?.toDate()?.time ?: 0L,
        hasSpoilers = hasSpoilers,
        emojiReaction = emojiReaction
    )
}
