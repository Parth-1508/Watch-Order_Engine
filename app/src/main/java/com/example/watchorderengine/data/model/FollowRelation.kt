package com.example.watchorderengine.data.model

import com.google.firebase.firestore.DocumentId

data class FollowRelation(
    @DocumentId
    var followId: String = "",
    var followerId: String = "",
    var followingId: String = "",
    var followerName: String = "",
    var followerAvatarUrl: String? = null,
    var timestamp: Long = 0L,
)
