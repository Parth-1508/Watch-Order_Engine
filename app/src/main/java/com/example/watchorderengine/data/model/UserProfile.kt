package com.example.watchorderengine.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Firestore-backed public profile document for a single user.
 */
data class UserProfile(
    var userId: String = "",

    var displayName: String = "",
    var avatarUrl: String? = null,

    var isStatsPublic: Boolean = false,
    var isFavoritesPublic: Boolean = false,

    /** kotlinx.serialization-encoded `List<MediaSummary>`. Decode via [favoriteShows]. */
    var favoriteShowsJson: String = "",

    /** kotlinx.serialization-encoded [UserStats], or "" if never computed. Decode via [watchStats]. */
    var watchStatsJson: String = "",
) {
    /** Decoded [favoriteShowsJson]. Returns an empty list if malformed or unset. */
    @get:Exclude
    val favoriteShows: List<MediaSummary>
        get() = FavoriteShowsCodec.decode(favoriteShowsJson)

    /** Decoded [watchStatsJson]. Returns null if malformed or unset. */
    @get:Exclude
    val watchStats: UserStats?
        get() = WatchStatsCodec.decode(watchStatsJson)
}

private val userProfileJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/**
 * Encode/decode helpers for [UserProfile.favoriteShowsJson].
 */
object FavoriteShowsCodec {
    fun encode(shows: List<MediaSummary>): String =
        userProfileJson.encodeToString(shows)

    fun decode(json: String): List<MediaSummary> {
        if (json.isBlank()) return emptyList()
        return try {
            userProfileJson.decodeFromString<List<MediaSummary>>(json)
        } catch (e: Exception) {
            emptyList()
        }
    }
}

/**
 * Encode/decode helpers for [UserProfile.watchStatsJson].
 */
object WatchStatsCodec {
    fun encode(stats: UserStats): String =
        userProfileJson.encodeToString(stats)

    fun decode(json: String): UserStats? {
        if (json.isBlank()) return null
        return try {
            userProfileJson.decodeFromString<UserStats>(json)
        } catch (e: Exception) {
            null
        }
    }
}
