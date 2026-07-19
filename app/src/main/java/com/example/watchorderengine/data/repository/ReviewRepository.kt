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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
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
                Log.d("ReviewRepo", "Observed ${snapshot.size()} global reviews for media $mediaId")
                snapshot.documents.mapNotNull { doc ->
                    try {
                        val review = doc.toObject<ReviewDocument>()
                        if (review == null) {
                            Log.w("ReviewRepo", "Parsed review is null for doc ${doc.id}")
                        }
                        review?.toReviewItem()
                    } catch (e: Exception) {
                        Log.e("ReviewRepo", "Error parsing global review ${doc.id}: ${e.message}")
                        null
                    }
                }
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

        // 3. Online: sync to GLOBAL feed AND User backup
        if (watchOrderRepository.isNetworkAvailable(context)) {
            try {
                Log.d("ReviewRepo", "Submitting review $reviewId for media $mediaId by user $uid")
                val batch = firestore.batch()
                
                // Public Global Feed
                val globalRef = firestore.collection("reviews").document(reviewId)
                batch.set(globalRef, doc)
                
                // User's private backup for sync-on-login
                val userRef = firestore.collection("users").document(uid)
                    .collection("reviews").document(reviewId)
                batch.set(userRef, doc)
                
                batch.commit().await()
                Log.d("ReviewRepo", "Review $reviewId successfully committed to global and user collections")
                
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
        
        val batch = firestore.batch()
        
        // Global Feed
        batch.set(firestore.collection("reviews").document(reviewId), doc)
        
        // User Backup
        batch.set(
            firestore.collection("users").document(uid).collection("reviews").document(reviewId),
            doc
        )
        
        batch.commit().await()
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
            
            Log.d("ReviewRepo", "Syncing ${snapshot.size()} reviews from cloud for user $uid")
            
            val reviews = snapshot.documents.mapNotNull { it.toObject<ReviewDocument>()?.toRoomEntity() }
            reviews.forEach { reviewDao.upsert(it) }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("ReviewRepo", "Review sync failed for user $uid: ${e.message}")
            Result.failure(e)
        }
    }

    // ─── External Reviews ───────────────────────────────────────────────────────

    suspend fun getAggregatedReviews(mediaId: String): List<ReviewItem> = coroutineScope {
        val media = mediaDao.getById(mediaId) ?: return@coroutineScope emptyList()
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

        // 1. Resolve External IDs (needed for AniList/MAL reviews)
        if (isAnime && (anilistId == null || malIdToUse == null)) {
            val cleanTitle = media.title.replace(Regex("\\(\\d{4}\\)"), "")
                .replace(Regex("\\[.*?\\]"), "")
                .replace(Regex("(?i)\\b(the movie|movie|special|ova|ona|tv|series|season \\d+)\\b"), "")
                .replace(Regex("\\s+"), " ")
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
                } catch (e: Exception) { 
                    Log.e("JikanStatus", "AniList ID Resolve Failed", e)
                }
            } else if (malIdToUse == null) {
                // We have anilistId, check if we can get idMal if it's missing
                try {
                    val query = "query(${'$'}id: Int) { Media(id: ${'$'}id) { id idMal } }"
                    val resp = anilistApi.query(AnilistRequest(query, mapOf("id" to anilistId)))
                    if (resp.isSuccessful) {
                        malIdToUse = resp.body()?.data?.media?.idMal
                    }
                } catch (e: Exception) {
                    Log.e("JikanStatus", "AniList MAL ID Resolve Failed", e)
                }
            }
            
            // Tertiary Fallback: Jikan Search
            if (malIdToUse == null) {
                try {
                    val jikanSearch = jikanApi.searchAnime(cleanTitle)
                    if (jikanSearch.isSuccessful) {
                        malIdToUse = jikanSearch.body()?.data?.firstOrNull()?.malId
                    }
                } catch (e: Exception) { 
                    Log.e("JikanStatus", "Jikan Search for Review ID Failed", e)
                }
            }
        }

        // 2. Fetch External Reviews in Parallel
        val tmdbDeferred = async(Dispatchers.IO) {
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
                        val rating = it.authorDetails?.rating?.toFloat()
                        ReviewItem(
                            id = "tmdb_${it.id}",
                            authorName = it.author,
                            authorAvatarUrl = avatarUrl,
                            rating = rating,
                            reviewText = it.content,
                            source = ReviewSource.TMDB,
                            createdAt = parseTmdbDate(it.createdAt),
                            externalUrl = it.url,
                            emojiReaction = getEmojiForRating(rating)
                        )
                    } ?: emptyList()
                } else emptyList()
            } catch (e: Exception) {
                Log.e("ReviewRepo", "TMDB error", e); emptyList()
            }
        }

        val anilistDeferred = async(Dispatchers.IO) {
            val aid = anilistId ?: return@async emptyList<ReviewItem>()
            try {
                val query = """
                    query(${'$'}id: Int) {
                      Media(id: ${'$'}id) {
                        id
                        reviews(perPage: 10, sort: [ID_DESC]) {
                          nodes {
                            id summary body score createdAt
                            user { name avatar { large } }
                          }
                        }
                      }
                    }
                """.trimIndent()
                val response = anilistApi.query(AnilistRequest(query, mapOf("id" to aid)))
                if (response.isSuccessful) {
                    response.body()?.data?.media?.reviews?.nodes?.map {
                        val rating = it.score?.toFloat()?.div(10f)
                        ReviewItem(
                            id = "anilist_${it.id}",
                            authorName = it.user?.name ?: "AniList User",
                            authorAvatarUrl = it.user?.avatar?.large,
                            rating = rating,
                            reviewText = (it.body ?: it.summary ?: "").cleanExternalMarkdown(),
                            source = ReviewSource.ANILIST,
                            createdAt = it.createdAt?.toLong()?.times(1000L) ?: 0L,
                            externalUrl = "https://anilist.co/review/${it.id}",
                            emojiReaction = getEmojiForRating(rating)
                        )
                    } ?: emptyList()
                } else emptyList()
            } catch (e: Exception) {
                Log.e("ReviewRepo", "AniList error", e); emptyList()
            }
        }

        val malDeferred = async(Dispatchers.IO) {
            val mid = malIdToUse ?: return@async emptyList<ReviewItem>()
            try {
                // Add a small delay to avoid hitting Jikan's 3 req/sec rate limit 
                // if searchAnime was called just before.
                delay(1200L)
                val jikanResponse = jikanApi.getAnimeReviews(mid)
                
                if (jikanResponse.isSuccessful) {
                    val reviews = jikanResponse.body()?.data ?: emptyList()
                    reviews.map {
                        val rating = it.score.toFloat()
                        ReviewItem(
                            id = "mal_${it.malId}",
                            authorName = it.user.username,
                            authorAvatarUrl = it.user.images?.jpg?.imageUrl,
                            rating = rating, 
                            reviewText = it.review.cleanExternalMarkdown(),
                            source = ReviewSource.MAL,
                            createdAt = parseMalDate(it.date),
                            hasSpoilers = it.isSpoiler,
                            externalUrl = it.url,
                            emojiReaction = getEmojiForRating(rating)
                        )
                    }
                } else {
                    Log.w("ReviewRepo", "MAL Reviews failed: ${jikanResponse.code()} for $mid")
                    Log.e("JikanStatus", "MAL Review Fetch Failed: ${jikanResponse.errorBody()?.string()}")
                    emptyList()
                }
            } catch (e: Exception) {
                Log.e("ReviewRepo", "MAL Reviews error for $mid", e)
                Log.e("JikanStatus", "MAL Review Exception", e)
                emptyList()
            }
        }

        val woeFirestoreDeferred = async(Dispatchers.IO) {
            try {
                // Fetch public reviews for this media from the global WOE collection
                val snapshot = firestore.collection("reviews")
                    .whereEqualTo("media_id", mediaId)
                    .orderBy("updated_at", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .limit(20)
                    .get().await()

                snapshot.documents.mapNotNull { it.toObject<ReviewDocument>()?.toReviewItem() }
                    .filter { it.userId != auth.currentUser?.uid } // Don't duplicate local review
            } catch (e: Exception) {
                Log.w("ReviewRepo", "WOE Firestore reviews failed: ${e.message}")
                emptyList()
            }
        }

        val externalReviews = awaitAll(tmdbDeferred, anilistDeferred, malDeferred, woeFirestoreDeferred).flatten()
        (localReviews + externalReviews).sortedByDescending { it.createdAt }
    }

    private fun getEmojiForRating(rating: Float?): String {
        if (rating == null) return "🤩"
        val emojis = listOf("🤬", "😡", "☹️", "😐", "🤨", "🙂", "😊", "🤩", "😍", "🤯")
        val index = (rating.toInt() - 1).coerceIn(0, 9)
        return emojis[index]
    }

    private fun parseTmdbDate(dateStr: String): Long = try {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.parse(dateStr)?.time ?: 0L
    } catch (e: Exception) { 0L }

    private fun parseMalDate(dateStr: String): Long = try {
        when {
            dateStr.contains("+") -> SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).parse(dateStr)?.time
            dateStr.endsWith("Z") -> SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(dateStr)?.time
            else -> SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dateStr)?.time
        } ?: 0L
    } catch (e: Exception) { System.currentTimeMillis() }

    private fun String.cleanExternalMarkdown(): String {
        return this.replace(Regex("<[^>]*>"), "") // Strip HTML
            .replace(Regex("__+|\\*+"), "")       // Strip bold/italic markers
            .replace(Regex("\\[(.*?)\\]\\(.*?\\)"), "$1") // Clean Markdown links: [text](url) -> text
            .replace(Regex("~+"), "")             // Strip strikethrough
            .replace(Regex("(?m)^>.*$"), "")       // Strip blockquotes
            .trim()
    }

    private fun ReviewEntity.toReviewItem(authorName: String, avatarUrl: String?) = ReviewItem(
        id = id,
        userId = userId,
        authorName = authorName,
        authorAvatarUrl = avatarUrl,
        rating = rating,
        reviewText = reviewText,
        source = ReviewSource.LOCAL,
        createdAt = updatedAt,
        hasSpoilers = hasSpoilers,
        emojiReaction = emojiReaction
    )

    fun ReviewDocument.toReviewItem() = ReviewItem(
        id = id,
        userId = userId,
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
