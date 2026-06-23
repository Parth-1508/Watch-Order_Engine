package com.example.watchorderengine.data.model

import kotlinx.serialization.Serializable

/** Full character+actor detail shown on the Character Detail screen. */
@Serializable
data class CharacterDetail(
    // ── Character (fictional) ─────────────────────────────────────────────────
    val characterName: String,
    val characterDescription: String,      // AniList (anime) first, else Wikipedia lore for live-action/Western characters, else empty
    val characterImageUrl: String?,        // AniList character image (anime) or TMDB profile
    val characterRole: String,             // "MAIN" | "SUPPORTING" | "BACKGROUND"
    val characterGender: String?,
    val characterAge: String?,
    val characterNativeName: String?,      // Japanese name for anime characters

    /** Additional fictional character images from AniList (if any). */
    val characterPhotos: List<String> = emptyList(),

    // ── Wikipedia lore ────────────────────────────────────────────────────────
    val wikiLore: String? = null,

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
    val allCastCredits: List<CreditItem>,

    // ── Character's own franchise appearances (AniList) ─────────────────────────
    // Every movie/special in the franchise's AniList relations graph that this
    // *fictional character* appears in — e.g. every "One Piece" movie Luffy is
    // in. Distinct from knownForCredits/allCastCredits above, which are the real
    // voice actor's unrelated TMDB acting credits.
    val characterAppearances: List<CharacterAppearance> = emptyList()
)

@Serializable
data class CharacterAppearance(
    val anilistId: Int,
    val title: String,
    val imageUrl: String?,
    val year: String?,
    val role: String?      // "MAIN" | "SUPPORTING" | "BACKGROUND" in that specific movie
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
