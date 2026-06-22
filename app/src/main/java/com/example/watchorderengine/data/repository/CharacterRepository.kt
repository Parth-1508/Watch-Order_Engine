package com.example.watchorderengine.data.repository

import android.util.Log
import com.example.watchorderengine.data.model.CharacterDetail
import com.example.watchorderengine.data.model.CreditItem
import com.example.watchorderengine.network.AnilistApiService
import com.example.watchorderengine.network.AnilistCharacterEdge
import com.example.watchorderengine.network.AnilistRequest
import com.example.watchorderengine.network.TmdbApiService
import com.example.watchorderengine.network.TmdbConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Merges two data sources into one [CharacterDetail]:
 *   - TMDB `/person/{id}` (append_to_response=combined_credits,images,external_ids)
 *     → real actor bio, photos, filmography.
 *   - AniList GraphQL `Page.media.characters` → fictional character description,
 *     art, role, age/gender, and the Japanese voice actor (anime only).
 *
 * AniList enrichment is strictly best-effort: any failure (network, no match,
 * non-anime title) just means the character-specific fields fall back to TMDB
 * data, never a hard error for the whole screen.
 */
@Singleton
class CharacterRepository @Inject constructor(
    private val tmdbApi: TmdbApiService,
    private val anilistApi: AnilistApiService
) {
    companion object { private const val TAG = "CharacterRepository" }

    /**
     * @param tmdbPersonId   The TMDB person ID (from CastMember.tmdbId).
     * @param characterName  The fictional character name (e.g., "Naruto Uzumaki").
     * @param showTitle      The parent show's title — used to search AniList for the right series.
     * @param isAnime        Whether to attempt AniList character enrichment at all.
     */
    suspend fun getCharacterDetail(
        tmdbPersonId: Int,
        characterName: String,
        showTitle: String,
        isAnime: Boolean
    ): Result<CharacterDetail> = withContext(Dispatchers.IO) {
        runCatching {
            // Fetch TMDB person + AniList character search in parallel.
            val tmdbDeferred = async { tmdbApi.getPersonDetail(tmdbPersonId) }
            val aniListDeferred = async {
                if (isAnime) fetchAniListCharacters(showTitle) else emptyList()
            }

            val tmdbResp = tmdbDeferred.await()
            val aniListEdges = aniListDeferred.await()

            if (!tmdbResp.isSuccessful) {
                error("TMDB person fetch failed: HTTP ${tmdbResp.code()}")
            }
            val person = tmdbResp.body() ?: error("Empty TMDB person response")

            // Fuzzy-match the AniList character by name (case-insensitive, either-contains).
            val aniEdge = aniListEdges.find { edge ->
                val name = edge.node?.name?.full ?: return@find false
                name.lowercase().contains(characterName.lowercase()) ||
                    characterName.lowercase().contains(name.lowercase())
            }
            val aniChar = aniEdge?.node
            val voiceActor = aniEdge?.voiceActors?.firstOrNull()

            val actorGenderStr = when (person.gender) {
                1 -> "Female"; 2 -> "Male"; 3 -> "Non-binary"; else -> null
            }

            val mainProfile = TmdbConfig.buildImageUrl(person.profilePath, TmdbConfig.PosterSize.HD)
            val extraPhotos = person.images?.profiles
                ?.sortedByDescending { it.voteAverage ?: 0.0 }
                ?.take(8)
                ?.mapNotNull { TmdbConfig.buildImageUrl(it.filePath, TmdbConfig.PosterSize.LARGE) }
                ?: emptyList()
            val allPhotos = listOfNotNull(mainProfile) + extraPhotos

            // Top credits by popularity, excluding the show we navigated from.
            val castCredits = person.combinedCredits?.cast
                ?.filter { it.mediaType == "movie" || it.mediaType == "tv" }
                ?.filter { (it.title ?: it.name)?.lowercase() != showTitle.lowercase() }
                ?.sortedByDescending { it.popularity ?: 0.0 }
                ?: emptyList()

            fun toCreditItem(credit: com.example.watchorderengine.network.model.TmdbPersonCastCredit) = CreditItem(
                tmdbId = credit.id,
                creditId = credit.creditId,
                // Matches the rest of the app's id scheme (safeMediaId() in AppNavigation
                // expects a "tmdb_" prefix, not "movie_"/"tv_").
                mediaId = "tmdb_${credit.id}",
                title = credit.title ?: credit.name ?: "Unknown",
                character = credit.character ?: "",
                posterUrl = TmdbConfig.buildImageUrl(credit.posterPath, TmdbConfig.PosterSize.CARD),
                year = (credit.releaseDate ?: credit.firstAirDate)?.take(4) ?: "",
                mediaType = credit.mediaType ?: "movie",
                voteAverage = credit.voteAverage?.toFloat() ?: 0f,
                episodeCount = credit.episodeCount
            )

            CharacterDetail(
                characterName        = characterName,
                characterDescription = cleanAniListText(aniChar?.description) ?: "",
                characterImageUrl    = aniChar?.image?.large ?: mainProfile,
                characterRole        = aniEdge?.role ?: "MAIN",
                characterGender      = aniChar?.gender,
                characterAge         = aniChar?.age,
                characterNativeName  = aniChar?.name?.native,

                actorTmdbId       = person.id,
                actorName         = person.name,
                actorBiography    = person.biography?.trim() ?: "",
                actorProfileUrl   = mainProfile,
                actorBirthday     = person.birthday,
                actorDeathday     = person.deathday,
                actorPlaceOfBirth = person.placeOfBirth,
                actorGender       = actorGenderStr,
                actorKnownFor     = person.knownForDepartment,
                actorAlsoKnownAs  = person.alsoKnownAs ?: emptyList(),
                actorPhotos       = allPhotos,

                voiceActorName      = voiceActor?.name?.full,
                voiceActorImageUrl  = voiceActor?.image?.large,

                knownForCredits = castCredits.distinctBy { it.id }.take(6).map(::toCreditItem),
                allCastCredits  = castCredits.take(30).map(::toCreditItem)
            )
        }.also { result ->
            if (result.isFailure) {
                Log.w(TAG, "getCharacterDetail($tmdbPersonId, $characterName) failed: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    /**
     * Searches AniList for [showTitle] and returns its character edges (role +
     * character node + voice actors). Empty list on any failure — this is
     * best-effort enrichment, never fatal to the screen.
     */
    private suspend fun fetchAniListCharacters(showTitle: String): List<AnilistCharacterEdge> {
        val query = """
            query (${'$'}search: String) {
              Page(page: 1, perPage: 1) {
                media(search: ${'$'}search, type: ANIME, sort: SEARCH_MATCH) {
                  id
                  characters(sort: [ROLE, RELEVANCE], perPage: 25) {
                    edges {
                      role
                      node {
                        id
                        name { full native }
                        description(asHtml: false)
                        image { large }
                        gender
                        age
                      }
                      voiceActors(language: JAPANESE, sort: RELEVANCE) {
                        id
                        name { full }
                        language
                        image { large }
                      }
                    }
                  }
                }
              }
            }
        """.trimIndent()

        return try {
            val response = anilistApi.query(
                AnilistRequest(query = query, variables = mapOf("search" to showTitle))
            )
            if (!response.isSuccessful) {
                Log.w(TAG, "AniList returned HTTP ${response.code()} for '$showTitle'")
                return emptyList()
            }
            response.body()?.data?.page?.media?.firstOrNull()?.characters?.edges ?: emptyList()
        } catch (e: Exception) {
            Log.w(TAG, "AniList character fetch failed for '$showTitle': ${e.message}")
            emptyList()
        }
    }

    private val htmlTagRegex = Regex("<[^>]+>")
    private val spoilerRegex = Regex("~!.*?!~", RegexOption.DOT_MATCHES_ALL)

    /** Strips HTML tags and AniList's `~! spoiler !~` markers from a description. */
    private fun cleanAniListText(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return raw
            .replace(spoilerRegex, "[SPOILER]")
            .replace(htmlTagRegex, "")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .trim()
    }
}
