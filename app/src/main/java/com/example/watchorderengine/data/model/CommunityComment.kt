package com.example.watchorderengine.data.model

import com.google.firebase.firestore.DocumentId

data class CommunityComment(
    @DocumentId
    var commentId: String = "",
    var postId: String = "",
    var userId: String = "",
    var authorName: String = "",
    var authorAvatarUrl: String? = null,
    var text: String = "",
    var parentCommentId: String? = null,
    var replyCount: Long = 0L,
    var likesCount: Long = 0L,
    var likedByUsers: List<String> = emptyList(),
    var timestamp: Long = 0L,
)
