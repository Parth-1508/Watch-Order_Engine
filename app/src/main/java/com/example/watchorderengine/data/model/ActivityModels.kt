package com.example.watchorderengine.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

/**
 * A follow relationship: "the owner of this subcollection follows [followedUserId]".
 *
 * Collection path: `users/{ownerUid}/following/{followedUserId}`
 * — document ID IS the followed user's UID, so "does X follow Y" is a
 * single `.document(Y_uid).get()` existence check, and unfollow is a
 * single `.document(Y_uid).delete()`.
 */
data class FollowRecord(
    @DocumentId
    var followedUserId: String = "",

    var followedDisplayName: String = "",
    var followedAvatarUrl: String? = null,

    @ServerTimestamp
    var followedAt: Timestamp? = null
)

/**
 * A single "Friend Activity" feed event — either a completion or (mirrored
 * from, not duplicated storage of) a rating.
 *
 * Collection path: `activity_events/{eventId}`
 */
data class ActivityEvent(
    @DocumentId
    var eventId: String = "",

    var userId: String = "",
    var authorName: String = "",
    var authorAvatarUrl: String? = null,

    var type: String = EventType.COMPLETED.name,

    var mediaId: String = "",
    var mediaTitle: String = "",
    var mediaPosterUrl: String? = null,

    /** How many episodes/movies were in the finished watch order. */
    var episodeCount: Int = 0,

    @ServerTimestamp
    var createdAt: Timestamp? = null
) {
    enum class EventType { COMPLETED }
}
