package com.example.watchorderengine.data.repository

import android.util.Log
import com.example.watchorderengine.data.model.CharacterAppearance
import com.example.watchorderengine.data.model.CharacterDetail
import com.example.watchorderengine.data.model.CreditItem
import com.example.watchorderengine.network.AnilistApiService
import com.example.watchorderengine.network.AnilistCharacters
import com.example.watchorderengine.network.AnilistMedia
import com.example.watchorderengine.network.AnilistRelations
import com.example.watchorderengine.network.AnilistRequest
import com.example.watchorderengine.network.TmdbApiService
import com.example.watchorderengine.network.TmdbConfig
import com.example.watchorderengine.network.WikipediaApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Merges three data sources into one [CharacterDetail]:
 *   - TMDB `/person/{id}` (append_to_response=combined_credits,images,external_ids)
 *     → real actor bio, photos, filmography.
 *   - AniList GraphQL `Media` → fictional character description, art, role,
 *     age/gender, the Japanese voice actor (anime only), AND — via the
 *     media's `relations` graph — every other movie/special in the same
 *     franchise, which lets us tell whether this *character* (not the actor)
 *     also appears in those entries (e.g. every "One Piece" movie Luffy is in).
 *   - Wikipedia REST `/page/summary/{title}` → fictional character LORE for
 *     everything AniList doesn't cover — live-action and Western-animation
 *     characters (Tony Stark, Walter White, etc.), where AniList has no entry
 *     at all and TMDB has no backstory field whatsoever.
 *
 * Both AniList and Wikipedia enrichment are strictly best-effort: any failure
 * (network, no match, disambiguation page, non-anime title) just means the
 * character-specific fields fall back gracefully, never a hard error for the
 * whole screen — only a failed TMDB person fetch fails the screen, since
 * that's the one source every character actually has.
 */
@Singleton
class CharacterRepository @Inject constructor(
    private val tmdbApi: TmdbApiService,
    private val anilistApi: AnilistApiService,
    private val wikipediaApi: WikipediaApiService
) {
    companion object { private const val TAG = "CharacterRepository" }

    // In-memory cache so the Characters list (one batch lookup per show) and
    // the Character Detail screen (one lookup per character tapped) don't
    // each fire their own AniList request for the same show. Best-effort —
    // never persisted, just avoids redundant network calls within a session.
    private val mediaCache = ConcurrentHashMap<String, AnilistMedia>()

    /**
     * @param tmdbPersonId   The TMDB person ID (from CastMember.tmdbId).
     * @param characterName  The fictional character name (e.g., "Naruto Uzumaki").
     * @param showTitle      The parent show's title — used as a fallback AniList search.
     * @param isAnime        Whether to attempt AniList character enrichment at all.
     * @param anilistId      The parent show's AniList media ID, if known (MediaDetail.anilistId).
     *                       Looking it up by ID is far more reliable than a title search and is
     *                       always preferred when available.
     */
    suspend fun getCharacterDetail(
        tmdbPersonId: Int,
        characterName: String,
        showTitle: String,
        isAnime: Boolean,
        anilistId: Int? = null
    ): Result<CharacterDetail> = withContext(Dispatchers.IO) {
        runCatching {
            // Clean character name from common TMDB suffixes like "(voice)"
            val cleanName = characterName
                .replace(Regex("\\s*\\(voice\\)\\s*", RegexOption.IGNORE_CASE), "")
                .replace(Regex("\\s*\\(uncredited\\)\\s*", RegexOption.IGNORE_CASE), "")
                .trim()

            // Fetch TMDB person + AniList media (character + relations) + Wikipedia
            // lore all in parallel — none of them depend on each other's result.
            val tmdbDeferred = async { tmdbApi.getPersonDetail(tmdbPersonId) }
            val aniListDeferred = async {
                if (isAnime) fetchAniListMedia(anilistId, showTitle) else null
            }
            val wikiDeferred = async { getCharacterLore(cleanName, showTitle) }

            val tmdbResp = tmdbDeferred.await()
            val media = aniListDeferred.await()
            val (wikiLore, wikiImageUrl) = wikiDeferred.await()

            if (!tmdbResp.isSuccessful) {
                error("TMDB person fetch failed: HTTP ${tmdbResp.code()}")
            }
            val person = tmdbResp.body() ?: error("Empty TMDB person response")

            val aniListEdges = media?.characters?.edges ?: emptyList()
            Log.d(TAG, "AniList matching: Searching for '$cleanName' among ${aniListEdges.size} characters")

            // Fuzzy-match the AniList character by name. 
            // Priority 1: Exact/Contains match on full name
            var aniEdge = aniListEdges.find { edge -> 
                val match = nameMatches(edge.node?.name?.full, cleanName)
                if (match) Log.d(TAG, "AniList Match Found (Full): '${edge.node?.name?.full}'")
                match
            }
            
            // Priority 2: Part-of-name match (handles "Luffy" matching "Monkey D. Luffy")
            if (aniEdge == null) {
                val nameParts = cleanName.split(" ").filter { it.length > 2 }
                aniEdge = aniListEdges.find { edge ->
                    val fullName = edge.node?.name?.full?.lowercase() ?: return@find false
                    val match = nameParts.any { part -> fullName.contains(part.lowercase()) }
                    if (match) Log.d(TAG, "AniList Match Found (Partial): '${edge.node?.name?.full}' matched via one of $nameParts")
                    match
                }
            }
            
            if (aniEdge == null) {
                Log.d(TAG, "AniList Match NOT Found for '$cleanName'")
            }

            val aniChar = aniEdge?.node
            val voiceActor = aniEdge?.voiceActors?.firstOrNull()

            // Collect all available fictional character photos (Gallery)
            // 1. Main AniList character art
            // 2. Wikipedia character art (if any)
            // 3. Covers of related movies where the character appears
            val fictionalArt = mutableListOf<String>()
            aniChar?.image?.large?.let { fictionalArt.add(it) }
            wikiImageUrl?.let { if (!fictionalArt.contains(it)) fictionalArt.add(it) }
            
            // Add covers of related media that feature this character
            media?.relations?.edges?.orEmpty()
                ?.filter { edge -> 
                    edge.node?.characters?.edges?.any { charEdge -> 
                        nameMatches(charEdge.node?.name?.full, cleanName)
                    } == true 
                }
                ?.mapNotNull { it.node?.coverImage?.extraLarge ?: it.node?.coverImage?.large }
                ?.forEach { url -> if (!fictionalArt.contains(url)) fictionalArt.add(url) }

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

            // AniList's character description (anime) wins when present —
            // it's purpose-written about the fictional character. Wikipedia
            // lore is the fallback for everything AniList doesn't cover:
            // live-action characters, Western animation, or an anime title
            // AniList just didn't have this character listed for.
            val aniListDescription = cleanAniListText(aniChar?.description)
            val characterLore = aniListDescription ?: wikiLore ?: ""

            CharacterDetail(
                characterName        = characterName,
                characterDescription = characterLore,
                characterImageUrl    = fictionalArt.firstOrNull() ?: mainProfile,
                characterPhotos      = fictionalArt,
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

                wikiLore = wikiLore,

                knownForCredits = castCredits.distinctBy { it.id }.take(6).map(::toCreditItem),
                allCastCredits  = castCredits.take(30).map(::toCreditItem),

                characterAppearances = if (isAnime) computeCharacterAppearances(media, characterName) else emptyList()
            )
        }.also { result ->
            if (result.isFailure) {
                Log.w(TAG, "getCharacterDetail($tmdbPersonId, $characterName) failed: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    /**
     * Batch lookup for the Characters list on the media detail screen: maps every
     * AniList character name (lowercased) in [showTitle]/[anilistId] to its AniList
     * art URL, in a single request, so the list can show character art next to (or
     * instead of) the voice actor's TMDB headshot without firing one AniList query
     * per cast row.
     *
     * Empty map for non-anime titles or on any failure — purely additive enrichment.
     */
    suspend fun getCharacterArtMap(
        anilistId: Int?,
        showTitle: String,
        isAnime: Boolean
    ): Map<String, String> = withContext(Dispatchers.IO) {
        if (!isAnime) return@withContext emptyMap()
        val media = fetchAniListMedia(anilistId, showTitle) ?: return@withContext emptyMap()
        media.characters?.edges
            ?.mapNotNull { edge ->
                val name = edge.node?.name?.full?.lowercase() ?: return@mapNotNull null
                val img = edge.node.image?.large ?: return@mapNotNull null
                name to img
            }
            ?.toMap()
            ?: emptyMap()
    }

    /**
     * Looks up the best AniList art URL for [characterName] out of [artMap] (as
     * returned by [getCharacterArtMap]), using the same fuzzy either-contains
     * match used everywhere else in this repository.
     */
    fun matchCharacterArt(artMap: Map<String, String>, characterName: String): String? {
        val lower = characterName.lowercase()
        artMap[lower]?.let { return it }
        return artMap.entries.firstOrNull { (name, _) -> nameMatches(name, characterName) }?.value
    }

    /**
     * Walks a media's `relations` edges for entries with format "MOVIE" (or
     * "SPECIAL"/"OVA") and checks each one's own character list for [characterName],
     * plus the media itself if it's already a movie/special. This is how we build
     * "every One Piece movie Luffy appears in" from a single AniList response,
     * without any extra network round-trips — the relations' character edges are
     * already embedded in the one query fetchAniListMedia() makes.
     *
     * Best-effort and necessarily limited to AniList's *direct* relations graph for
     * the matched media — a movie linked only to another movie (not the main show)
     * won't surface here. Good enough to catch the vast majority of franchise films.
     */
    private fun computeCharacterAppearances(media: AnilistMedia?, characterName: String): List<CharacterAppearance> {
        if (media == null) return emptyList()
        val standaloneFormats = setOf("MOVIE", "SPECIAL", "OVA", "ONA")

        fun toAppearance(node: AnilistMedia): CharacterAppearance? {
            val edge = node.characters?.edges?.find { nameMatches(it.node?.name?.full, characterName) } ?: return null
            return CharacterAppearance(
                anilistId = node.id,
                title = node.title?.english ?: node.title?.romaji ?: "Unknown",
                imageUrl = node.coverImage?.extraLarge ?: node.coverImage?.large,
                year = node.startDate?.year?.toString(),
                role = edge.role
            )
        }

        val results = mutableListOf<CharacterAppearance>()
        if (media.format in standaloneFormats) {
            toAppearance(media)?.let { results += it }
        }
        media.relations?.edges.orEmpty()
            .mapNotNull { it.node }
            .filter { it.format in standaloneFormats }
            .distinctBy { it.id }
            .forEach { node -> toAppearance(node)?.let { results += it } }

        return results.distinctBy { it.anilistId }
            .sortedWith(compareBy(nullsLast<String>()) { it.year })
    }

    /** Case-insensitive, either-direction substring match — shared by every name lookup in this class. */
    private fun nameMatches(candidate: String?, characterName: String): Boolean {
        if (candidate.isNullOrBlank()) return false
        val a = candidate.lowercase()
        val b = characterName.lowercase()
        return a.contains(b) || b.contains(a)
    }

    /**
     * Fetches a fictional character's lore/backstory from Wikipedia — the
     * fallback source for everything AniList doesn't cover (live-action,
     * Western animation). Returns null (never throws) if:
     *   - Wikipedia has no page for this character at all (404)
     *   - the page found is a disambiguation page (the [WikipediaSummaryResponse.type]
     *     check below) — e.g. "Tony Stark" colliding with an unrelated real
     *     person of the same name
     *   - the page has no usable extract ("no-extract" type, or blank text)
     *   - any network/parsing error
     *
     * Tries a franchise-qualified query first ("Tony Stark (Marvel Cinematic
     * Universe)" / "Tony Stark Marvel"), since a bare character name alone is
     * exactly the case most likely to hit a disambiguation page or the wrong
     * person entirely. Falls back to the bare character name only if the
     * qualified query comes back empty.
     */
    suspend fun getCharacterLore(characterName: String, mediaTitle: String): Pair<String?, String?> {
        // Handle names like "Peter Parker / Spider-Man" by trying both parts separately.
        val nameParts = characterName.split("/").map { it.trim() }.filter { it.isNotBlank() }
        
        for (part in nameParts) {
            // Wikipedia article titles commonly use the "(context)" disambiguator
            // pattern for fictional characters, e.g. "Tony Stark (Marvel Cinematic
            // Universe)" or "Walter White (Breaking Bad)" — try that shape first.
            val qualifiedQuery = "$part ($mediaTitle)"
            val plainQualifiedQuery = "$part $mediaTitle"

            val result = fetchWikipediaSummary(qualifiedQuery)
                ?: fetchWikipediaSummary(plainQualifiedQuery)
                ?: fetchWikipediaSummary(part)
            
            if (result != null) return result
        }
        
        return null to null
    }

    /** Single Wikipedia lookup attempt. Returns Pair(extract, imageUrl) or null on any failure. */
    private suspend fun fetchWikipediaSummary(query: String): Pair<String?, String?>? {
        return try {
            // Wikipedia's REST API treats the title as a path segment where
            // spaces must be underscores; Retrofit's @Path percent-encodes
            // everything else (parens, unicode, etc.) for us.
            val pathTitle = query.trim().replace(" ", "_")
            if (pathTitle.isBlank()) return null

            val response = wikipediaApi.getPageSummary(pathTitle)

            if (response.code() == 404) {
                // Expected, common outcome — most characters simply don't have
                // a dedicated Wikipedia page. Not a warning-worthy failure.
                return null
            }
            if (!response.isSuccessful) {
                Log.w(TAG, "Wikipedia returned HTTP ${response.code()} for '$query'")
                return null
            }

            val body = response.body() ?: return null

            // Disambiguation/no-extract pages return 200 OK but carry no
            // usable lore — trusting `extract` here without this check is
            // exactly how an unrelated person's bio would end up shown.
            if (body.type == "disambiguation" || body.type == "no-extract") {
                return null
            }

            val extract = body.extract?.trim()?.takeIf { it.isNotBlank() }
            // Prefer the original high-res image, fallback to thumbnail.
            val imageUrl = body.originalImage?.source ?: body.thumbnail?.source
            
            if (extract == null && imageUrl == null) return null
            
            extract to imageUrl
        } catch (e: Exception) {
            Log.w(TAG, "Wikipedia lookup failed for '$query': ${e.message}")
            null
        }
    }

    /**
     * Fetches the AniList [AnilistMedia] for a show — by [anilistId] when available
     * (far more reliable than a title search; this is how MediaDetail.anilistId gets
     * used), falling back to a fuzzy title search otherwise. The single response
     * includes both the show's own character list AND the `relations` graph (with
     * each related movie/special's own character list embedded), so this one
     * request feeds both [getCharacterDetail]'s character tab and the franchise
     * "appearances" list — no extra round-trips needed.
     *
     * Cached in-memory per session, keyed by id (preferred) or title, since both the
     * Characters list and every character tapped on the same show would otherwise
     * each re-fetch this identical response.
     */
    private suspend fun fetchAniListMedia(anilistId: Int?, showTitle: String): AnilistMedia? {
        val cacheKey = anilistId?.let { "id:$it" } ?: "title:${showTitle.lowercase()}"
        mediaCache[cacheKey]?.let { return it }

        // Clean show title to remove common parenthetical fluff from TMDB
        // that often confuses AniList's search (e.g., "One Piece (TV Series)")
        val cleanTitle = showTitle
            .replace(Regex("\\s*\\(TV Series\\)\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*\\(Movie\\)\\s*", RegexOption.IGNORE_CASE), "")
            .trim()

        val query = """
            query (${'$'}id: Int, ${'$'}search: String) {
              Page(page: 1, perPage: 5) {
                media(id: ${'$'}id, search: ${'$'}search, type: ANIME, sort: SEARCH_MATCH) {
                  id
                  format
                  title { romaji english }
                  startDate { year }
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
                  relations {
                    edges {
                      relationType
                      node {
                        id
                        format
                        title { romaji english }
                        coverImage { large extraLarge }
                        startDate { year }
                        characters(perPage: 30) {
                          edges {
                            role
                            node { id name { full } }
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
        """.trimIndent()

        val media = try {
            val response = anilistApi.query(
                AnilistRequest(
                    query = query,
                    variables = mapOf("id" to anilistId, "search" to if (anilistId == null) cleanTitle else null)
                )
            )
            if (!response.isSuccessful) {
                Log.w(TAG, "AniList returned HTTP ${response.code()} for '$showTitle' (id=$anilistId)")
                null
            } else {
                val data = response.body()?.data
                // Title or ID search: merge results into one "super-media" so
                // we find the character even if we hit the "wrong" show first.
                val results = data?.page?.media ?: emptyList()
                if (results.isEmpty()) {
                    Log.d(TAG, "AniList returned zero media for '$cleanTitle' (id=$anilistId)")
                    null
                } else {
                    Log.d(TAG, "AniList found ${results.size} media matches for '$cleanTitle'")
                    AnilistMedia(
                        id = results.first().id,
                        idMal = results.first().idMal,
                        title = results.first().title,
                        description = results.first().description,
                        bannerImage = results.first().bannerImage,
                        coverImage = results.first().coverImage,
                        episodes = results.first().episodes,
                        averageScore = results.first().averageScore,
                        genres = results.first().genres,
                        tags = results.first().tags,
                        // MERGED CHARACTERS AND RELATIONS
                        characters = AnilistCharacters(edges = results.flatMap { it.characters?.edges ?: emptyList() }.distinctBy { it.node?.id }),
                        relations = AnilistRelations(edges = results.flatMap { it.relations?.edges ?: emptyList() }.distinctBy { it.node?.id }),
                        format = results.first().format,
                        startDate = results.first().startDate
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "AniList media fetch failed for '$showTitle' (id=$anilistId): ${e.message}")
            null
        }

        mediaCache[cacheKey] = media ?: return null
        return media
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
