package com.example.watchorderengine.data.model

import com.example.watchorderengine.data.db.entity.ReviewEntity
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.*

@Serializable
data class ReviewDocument(
    @DocumentId
    val id: String = "",

    @get:PropertyName("media_id")
    @set:PropertyName("media_id")
    var mediaId: String = "",

    @get:PropertyName("media_title")
    @set:PropertyName("media_title")
    var mediaTitle: String = "",

    @get:PropertyName("media_poster_url")
    @set:PropertyName("media_poster_url")
    var mediaPosterUrl: String? = null,

    @get:PropertyName("user_id")
    @set:PropertyName("user_id")
    var userId: String = "",

    @get:PropertyName("rating")
    @set:PropertyName("rating")
    var rating: Double = 0.0,

    @get:PropertyName("review_text")
    @set:PropertyName("review_text")
    var reviewText: String = "",

    @get:PropertyName("has_spoilers")
    @set:PropertyName("has_spoilers")
    var hasSpoilers: Boolean = false,

    @get:PropertyName("watched_date")
    @set:PropertyName("watched_date")
    var watchedDate: String? = null,

    @get:PropertyName("created_at")
    @set:PropertyName("created_at")
    @Contextual
    var createdAt: Timestamp? = null,

    @get:PropertyName("updated_at")
    @set:PropertyName("updated_at")
    @Contextual
    var updatedAt: Timestamp? = null,

    @get:PropertyName("author_name")
    @set:PropertyName("author_name")
    var authorName: String = "",

    @get:PropertyName("author_avatar_url")
    @set:PropertyName("author_avatar_url")
    var authorAvatarUrl: String? = null,

    @get:PropertyName("emoji_reaction")
    @set:PropertyName("emoji_reaction")
    var emojiReaction: String = "🤩",
)

fun ReviewEntity.toFirestoreDocument(authorName: String, authorAvatarUrl: String?): ReviewDocument {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    return ReviewDocument(
        id             = id,
        mediaId        = mediaId,
        mediaTitle     = mediaTitle,
        mediaPosterUrl = mediaPosterUrl,
        userId         = userId,
        rating         = rating.toDouble(),
        reviewText     = reviewText,
        hasSpoilers    = hasSpoilers,
        watchedDate    = watchedDate?.let { sdf.format(Date(it)) },
        authorName      = authorName,
        authorAvatarUrl = authorAvatarUrl,
    )
}

fun ReviewDocument.toRoomEntity(): ReviewEntity {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    return ReviewEntity(
        id          = id,
        mediaId     = mediaId,
        mediaTitle  = mediaTitle.ifBlank { mediaId },
        mediaPosterUrl = mediaPosterUrl,
        userId      = userId,
        rating      = rating.toFloat(),
        reviewText  = reviewText,
        hasSpoilers = hasSpoilers,
        watchedDate = watchedDate?.let { runCatching { sdf.parse(it)?.time }.getOrNull() },
        createdAt   = createdAt?.toDate()?.time ?: System.currentTimeMillis(),
        updatedAt   = updatedAt?.toDate()?.time ?: System.currentTimeMillis(),
        isSynced    = true,
    )
}

// ─── Unified Review Domain Model ──────────────────────────────────────────────

@Serializable
data class ReviewItem(
    val id: String,
    val authorName: String,
    val authorAvatarUrl: String?,
    val rating: Float?,        // null if source doesn't provide 1-5 or 1-10
    val reviewText: String,
    val source: ReviewSource,
    val createdAt: Long,
    val hasSpoilers: Boolean = false,
    val externalUrl: String? = null,
    val emojiReaction: String = "🤩"
)

enum class ReviewSource {
    LOCAL,
    TMDB,
    ANILIST,
    MAL
}
