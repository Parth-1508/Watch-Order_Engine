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

@Singleton
class CharacterRepository @Inject constructor(
    private val tmdbApi: TmdbApiService,
    private val anilistApi: AnilistApiService,
    private val wikipediaApi: WikipediaApiService
) {
    companion object { private const val TAG = "CharacterRepository" }

    private val mediaCache = ConcurrentHashMap<String, AnilistMedia>()

    suspend fun getCharacterDetail(
        tmdbPersonId: Int,
        characterName: String,
        showTitle: String,
        isAnime: Boolean,
        anilistId: Int? = null
    ): Result<CharacterDetail> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanCharName = characterName
                .replace(Regex("\\s*\\(voice\\)\\s*", RegexOption.IGNORE_CASE), "")
                .replace(Regex("\\s*\\(uncredited\\)\\s*", RegexOption.IGNORE_CASE), "")
                .trim()

            val cleanShowTitle = showTitle
                .replace(Regex("\\(\\d{4}\\)"), "")
                .replace(Regex("\\s*\\(TV Series\\)\\s*", RegexOption.IGNORE_CASE), "")
                .replace(Regex("\\s*\\(Movie\\)\\s*", RegexOption.IGNORE_CASE), "")
                .trim()

            Log.d(TAG, "getCharacterDetail: id=$tmdbPersonId, name='$cleanCharName', show='$cleanShowTitle'")

            // 1. Fetch TMDB Person first to get their real name for high-context Wiki search
            val tmdbResp = tmdbApi.getPersonDetail(tmdbPersonId)
            if (!tmdbResp.isSuccessful) {
                error("TMDB person fetch failed: HTTP ${tmdbResp.code()}")
            }
            val person = tmdbResp.body() ?: error("Empty TMDB person response")

            // 2. Fetch others in parallel
            val aniListDeferred = async {
                if (isAnime) fetchAniListMedia(anilistId, cleanShowTitle) else null
            }
            val wikiDeferred = async { getCharacterLore(cleanCharName, cleanShowTitle, person.name) }

            val media = aniListDeferred.await()
            val (wikiLore, wikiImageUrl) = wikiDeferred.await()

            val aniListEdges = media?.characters?.edges ?: emptyList()
            
            var aniEdge = aniListEdges.find { edge -> 
                nameMatches(edge.node?.name?.full, cleanCharName) || nameMatches(edge.node?.name?.native, cleanCharName)
            }
            
            if (aniEdge == null) {
                val nameParts = cleanCharName.split(" ").filter { it.length > 2 }
                aniEdge = aniListEdges.find { edge ->
                    val fullName = edge.node?.name?.full?.lowercase() ?: return@find false
                    val nativeName = edge.node.name?.native?.lowercase() ?: ""
                    nameParts.any { part -> fullName.contains(part.lowercase()) || nativeName.contains(part.lowercase()) }
                }
            }

            val aniChar = aniEdge?.node
            val voiceActor = aniEdge?.voiceActors?.firstOrNull()

            val fictionalArt = mutableListOf<String>()
            val aniImg = aniChar?.image?.large?.takeIf { TmdbConfig.isValidImageUrl(it) }
            aniImg?.let { fictionalArt.add(it) }
            
            val validWikiImg = wikiImageUrl?.takeIf { TmdbConfig.isValidImageUrl(it) }
            validWikiImg?.let { if (!fictionalArt.contains(it)) fictionalArt.add(it) }

            if (isAnime) {
                person.taggedImages?.results.orEmpty()
                    .filter { it.imageType == "still" || it.imageType == null }
                    .filter { tagged ->
                        val mTitle = tagged.media?.title ?: tagged.media?.name
                        mTitle != null && (
                            mTitle.equals(cleanShowTitle, ignoreCase = true) ||
                            mTitle.contains(cleanShowTitle, ignoreCase = true) ||
                            cleanShowTitle.contains(mTitle, ignoreCase = true)
                        )
                    }
                    .sortedByDescending { it.voteAverage ?: 0.0 }
                    .take(6)
                    .mapNotNull { TmdbConfig.buildImageUrl(it.filePath, TmdbConfig.PosterSize.CARD) }
                    .filter { TmdbConfig.isValidImageUrl(it) }
                    .forEach { url -> if (!fictionalArt.contains(url)) fictionalArt.add(url) }

                media?.relations?.edges.orEmpty()
                    .filter { edge -> 
                        edge.node?.characters?.edges?.any { charEdge -> 
                            nameMatches(charEdge.node?.name?.full, cleanCharName)
                        } == true 
                    }
                    .mapNotNull { it.node?.coverImage?.extraLarge ?: it.node?.coverImage?.large }
                    .filter { TmdbConfig.isValidImageUrl(it) }
                    .forEach { url -> if (!fictionalArt.contains(url)) fictionalArt.add(url) }
            }

            val actorGenderStr = when (person.gender) {
                1 -> "Female"; 2 -> "Male"; 3 -> "Non-binary"; else -> null
            }

            val mainProfile = TmdbConfig.buildProfileUrl(person.profilePath, TmdbConfig.ProfileSize.LARGE)
                ?.takeIf { TmdbConfig.isValidImageUrl(it) }
                
            val extraPhotos = person.images?.profiles
                ?.sortedByDescending { it.voteAverage ?: 0.0 }
                ?.take(8)
                ?.mapNotNull { TmdbConfig.buildProfileUrl(it.filePath, TmdbConfig.ProfileSize.LARGE) }
                ?.filter { TmdbConfig.isValidImageUrl(it) }
                ?: emptyList()
            val allPhotos = (listOfNotNull(mainProfile) + extraPhotos).distinct()

            val castCredits = person.combinedCredits?.cast
                ?.filter { it.mediaType == "movie" || it.mediaType == "tv" }
                ?.filter { (it.title ?: it.name)?.lowercase() != cleanShowTitle.lowercase() }
                ?.sortedByDescending { it.popularity ?: 0.0 }
                ?: emptyList()

            fun toCreditItem(credit: com.example.watchorderengine.network.model.TmdbPersonCastCredit): CreditItem {
                val prefix = if (credit.mediaType == "tv") "tmdb_t_" else "tmdb_m_"
                return CreditItem(
                    tmdbId = credit.id,
                    creditId = credit.creditId,
                    mediaId = "$prefix${credit.id}",
                    title = credit.title ?: credit.name ?: "Unknown",
                    character = credit.character ?: "",
                    posterUrl = TmdbConfig.buildImageUrl(credit.posterPath, TmdbConfig.PosterSize.CARD),
                    year = (credit.releaseDate ?: credit.firstAirDate)?.take(4) ?: "",
                    mediaType = credit.mediaType ?: "movie",
                    voteAverage = credit.voteAverage?.toFloat() ?: 0f,
                    episodeCount = credit.episodeCount
                )
            }

            val aniListDescription = cleanAniListText(aniChar?.description)
            val characterLore = aniListDescription ?: wikiLore ?: ""

            val primaryCharImg = aniImg
                ?: validWikiImg
                ?: fictionalArt.firstOrNull { it != aniImg && it != validWikiImg }
                ?: mainProfile

            CharacterDetail(
                characterName        = characterName,
                characterDescription = characterLore,
                characterImageUrl    = primaryCharImg,
                characterPhotos      = fictionalArt.filter { it.isNotBlank() },
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

                characterAppearances = if (isAnime) computeCharacterAppearances(media, cleanCharName) else emptyList()
            )
        }
    }

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
                if (!TmdbConfig.isValidImageUrl(img)) return@mapNotNull null
                name to img
            }
            ?.toMap()
            ?: emptyMap()
    }

    fun matchCharacterArt(artMap: Map<String, String>, characterName: String): String? {
        val lower = characterName.lowercase()
        artMap[lower]?.let { return it }
        return artMap.entries.firstOrNull { (name, _) -> nameMatches(name, characterName) }?.value
    }

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

    private fun nameMatches(candidate: String?, characterName: String): Boolean {
        if (candidate.isNullOrBlank()) return false
        val a = candidate.lowercase()
        val b = characterName.lowercase()
        return a.contains(b) || b.contains(a)
    }

    suspend fun getCharacterLore(characterName: String, mediaTitle: String, actorName: String? = null): Pair<String?, String?> {
        val nameParts = characterName.split("/").map { it.trim() }.filter { it.isNotBlank() }
        for (part in nameParts) {
            // Context-aware search queries to prevent common name ambiguity (Bug F fix)
            val contextQueries = mutableListOf(
                "$part ($mediaTitle character)",
                "$part $mediaTitle movie character",
                "$part from $mediaTitle",
                "$part ($mediaTitle)",
                "$part $mediaTitle"
            )
            
            if (!actorName.isNullOrBlank()) {
                contextQueries.add(0, "$part played by $actorName")
                contextQueries.add(1, "$part $actorName")
            }

            for (q in contextQueries) {
                val result = fetchWikipediaSummary(q)
                if (result != null) return result
            }

            // Wikipedia is very sensitive to the exact title. Fictional characters 
            // almost always have a suffix in parentheses if they are popular.
            val contexts = listOf(
                "Marvel Cinematic Universe",
                "Marvel Comics",
                "DC Extended Universe",
                "DC Comics",
                "comics",
                "film",
                "character",
                "video game"
            )
            
            // Try specific contexts
            for (ctx in contexts) {
                val qualifiedQuery = "$part ($ctx)"
                val result = fetchWikipediaSummary(qualifiedQuery)
                if (result != null) return result
            }
            
            // Try "CharacterName Marvel" etc.
            val simpleContexts = listOf("Marvel", "DC", "Disney")
            for (ctx in simpleContexts) {
                val result = fetchWikipediaSummary("$part $ctx")
                if (result != null) return result
            }

            // Finally try the bare name
            val plainResult = fetchWikipediaSummary(part)
            if (plainResult != null) return plainResult
        }
        return null to null
    }

    private suspend fun fetchWikipediaSummary(query: String): Pair<String?, String?>? {
        return try {
            val pathTitle = query.trim().replace(" ", "_")
            if (pathTitle.isBlank()) return null
            val response = wikipediaApi.getPageSummary(pathTitle)
            if (!response.isSuccessful) {
                if (response.code() != 404) {
                    Log.d(TAG, "Wikipedia HTTP ${response.code()} for '$query'")
                }
                return null
            }
            val body = response.body() ?: return null

            if (body.type != null && body.type != "standard") {
                Log.d(TAG, "Wikipedia type='${body.type}' for '$query' — skipping (not a standard article)")
                return null
            }

            val extract = body.extract?.trim()?.takeIf { it.isNotBlank() }
            var imageUrl = body.thumbnail?.source ?: body.originalImage?.source

            if (imageUrl?.startsWith("//") == true) {
                imageUrl = "https:$imageUrl"
            }

            if (extract == null && imageUrl == null) {
                Log.d(TAG, "Wikipedia page found for '$query' but it has neither extract nor image")
                null
            } else {
                if (imageUrl == null) Log.d(TAG, "Wikipedia page found for '$query' with text but NO image (article has no infobox/lead image)")
                extract to imageUrl
            }
        } catch (e: Exception) {
            Log.w(TAG, "Wikipedia fetch threw for '$query': ${e.message}")
            null
        }
    }

    private suspend fun fetchAniListMedia(anilistId: Int?, showTitle: String): AnilistMedia? {
        val cacheKey = anilistId?.let { "id:$it" } ?: "title:${showTitle.lowercase()}"
        mediaCache[cacheKey]?.let { return it }

        val cleanTitle = showTitle
            .replace(Regex("\\(\\d{4}\\)"), "")
            .replace(Regex("\\s*\\(TV Series\\)\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*\\(Movie\\)\\s*", RegexOption.IGNORE_CASE), "")
            .trim()

        if (anilistId != null) {
            val query = """
                query (${'$'}id: Int) {
                  Media(id: ${'$'}id, type: ANIME) {
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
                        voiceActors(language: JAPANESE, sort: [FAVOURITES_DESC]) {
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
            """.trimIndent()

            val media = try {
                val response = anilistApi.query(AnilistRequest(query, mapOf("id" to anilistId)))
                if (response.isSuccessful) response.body()?.data?.media else null
            } catch (e: Exception) { null }
            
            if (media != null) {
                mediaCache[cacheKey] = media
                return media
            }
        }

        val searchQuery = """
            query (${'$'}search: String) {
              Page(page: 1, perPage: 3) {
                media(search: ${'$'}search, type: ANIME) {
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
                      voiceActors(language: JAPANESE, sort: [FAVOURITES_DESC]) {
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
            val response = anilistApi.query(AnilistRequest(searchQuery, mapOf("search" to cleanTitle)))
            if (!response.isSuccessful) null
            else {
                val results = response.body()?.data?.page?.media ?: emptyList()
                if (results.isEmpty()) null
                else {
                    val first = results.first()
                    AnilistMedia(
                        id = first.id,
                        idMal = first.idMal,
                        title = first.title,
                        description = first.description,
                        bannerImage = first.bannerImage,
                        coverImage = first.coverImage,
                        episodes = first.episodes,
                        averageScore = first.averageScore,
                        genres = first.genres,
                        tags = first.tags,
                        characters = AnilistCharacters(edges = results.flatMap { it.characters?.edges ?: emptyList() }.distinctBy { it.node?.id }),
                        relations = AnilistRelations(edges = results.flatMap { it.relations?.edges ?: emptyList() }.distinctBy { it.node?.id }),
                        format = first.format,
                        startDate = first.startDate
                    )
                }
            }
        } catch (e: Exception) {
            null
        }

        mediaCache[cacheKey] = media ?: return null
        return media
    }

    private val htmlTagRegex = Regex("<[^>]+>")
    private val spoilerRegex = Regex("~!.*?!~", RegexOption.DOT_MATCHES_ALL)

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
