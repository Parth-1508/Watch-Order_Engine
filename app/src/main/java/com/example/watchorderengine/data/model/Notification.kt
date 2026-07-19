package com.example.watchorderengine.data.model

import com.google.firebase.firestore.DocumentId
import kotlinx.serialization.Serializable

enum class NotificationType {
    LIKE,          // Someone liked your post
    IMPORT,        // Someone imported your timeline
    RECOMMENDATION, // Personalized recommendation
    STREAK,        // Daily streak reminder
    SYSTEM         // System updates
}

@Serializable
data class Notification(
    @DocumentId
    var id: String = "",
    val userId: String = "",       // Recipient
    val type: NotificationType = NotificationType.SYSTEM,
    val title: String = "",
    val message: String = "",
    val timestamp: Long = System.currentTimeMillis(),

    @get:com.google.firebase.firestore.PropertyName("isRead")
    @set:com.google.firebase.firestore.PropertyName("isRead")
    @field:com.google.firebase.firestore.PropertyName("isRead")
    var isRead: Boolean = false,
    
    // Metadata for navigation or context
    val targetId: String? = null,   // mediaId, universeId, or postId
    val senderId: String? = null,   // Who triggered it
    val senderName: String? = null,
    val senderAvatarUrl: String? = null,
    val imageUrl: String? = null    // Thumbnail (e.g. poster)
)
