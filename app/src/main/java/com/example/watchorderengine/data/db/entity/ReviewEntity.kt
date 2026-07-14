package com.example.watchorderengine.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "user_reviews",
    foreignKeys = [
        ForeignKey(
            entity = MediaEntity::class,
            parentColumns = ["id"],
            childColumns = ["mediaId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("mediaId"),
        Index("userId"),
        Index("rating"),
    ]
)
data class ReviewEntity(
    @PrimaryKey
    val id: String,
    val mediaId: String,
    val mediaTitle: String,
    val mediaPosterUrl: String?,
    val userId: String,
    val rating: Float,
    val reviewText: String = "",
    val hasSpoilers: Boolean = false,
    val watchedDate: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false,
    val emojiReaction: String = "🤩"
)
