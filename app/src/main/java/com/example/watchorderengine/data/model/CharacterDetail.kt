package com.example.watchorderengine.data.model

import kotlinx.serialization.Serializable

/** Full character+actor detail shown on the Character Detail screen. */
@Serializable
data class CharacterDetail(
    // ── Character (fictional) ─────────────────────────────────────────────────
    val characterName: String,
    val characterDescription: String,      // From AniList (anime) or empty for live-action
    val characterImageUrl: String?,        // AniList character image (anime) or TMDB profile
    val characterRole: String,             // "MAIN" | "SUPPORTING" | "BACKGROUND"
    val characterGender: String?,
    val characterAge: String?,
    val characterNativeName: String?,      // Japanese name for anime characters

    // ── Actor / Voice Actor (real person) ─────────────────────────────────────
    val actorTmdbId: Int,
    val actorName: String,
    val actorBiography: String,
    val actorProfileUrl: String?,
    val actorBirthday: String?,
    val actorDeathday: String?,
    val actorPlaceOfBirth: String?,
    val actorGender: String?,              // "Male" | "Female" | "Non-binary"
    val actorKnownFor: String?,            // e.g., "Acting"
    val actorAlsoKnownAs: List<String>,
    val actorPhotos: List<String>,         // extra profile image URLs

    // Voice actor (for anime — separate from the live-action actor)
    val voiceActorName: String?,
    val voiceActorImageUrl: String?,

    // ── Filmography (top credits sorted by popularity) ─────────────────────────
    val knownForCredits: List<CreditItem>,
    val allCastCredits: List<CreditItem>
)

@Serializable
data class CreditItem(
    val tmdbId: Int,
    val creditId: String,
    val mediaId: String,                   // "tmdb_12345" — matches AppNavigation's id scheme
    val title: String,
    val character: String,
    val posterUrl: String?,
    val year: String,
    val mediaType: String,                 // "movie" | "tv"
    val voteAverage: Float,
    val episodeCount: Int?                 // null for movies
)
