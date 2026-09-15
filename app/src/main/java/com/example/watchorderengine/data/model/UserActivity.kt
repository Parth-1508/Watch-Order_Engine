package com.example.watchorderengine.data.model

import com.google.firebase.firestore.DocumentId

enum class ActivityType {
    COMPLETED,
    RATED
}

data class UserActivity(
    @DocumentId
    var activityId: String = "",
    var userId: String = "",
    var userName: String = "",
    var userAvatarUrl: String? = null,
    var type: ActivityType = ActivityType.COMPLETED,
    var mediaId: String = "",
    var mediaTitle: String = "",
    var mediaPosterUrl: String? = null,
    var rating: Float? = null,
    var timestamp: Long = 0L,
)
