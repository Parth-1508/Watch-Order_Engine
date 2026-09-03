package com.example.watchorderengine.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

/**
 * A single comment (or reply) on a shared timeline.
 *
 * Collection path: `universes/{universeId}/comments/{commentId}`
 */
data class UniverseComment(
    @DocumentId
    var commentId: String = "",

    /** Null for a top-level comment; the parent's [commentId] for a reply. */
    var parentCommentId: String? = null,

    var universeId: String = "",
    var authorId: String = "",
    var authorName: String = "",
    var authorAvatarUrl: String? = null,

    var text: String = "",

    var likedByUsers: List<String> = emptyList(),
    var likesCount: Long = 0L,

    @ServerTimestamp
    var createdAt: Timestamp? = null,

    var isDeleted: Boolean = false
)
