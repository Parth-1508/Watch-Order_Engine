package com.example.watchorderengine.data.import_list

import android.util.Log
import com.example.watchorderengine.data.db.WatchOrderDatabase
import com.example.watchorderengine.data.db.entity.MediaEntity
import com.example.watchorderengine.data.db.entity.UserProgressEntity
import com.example.watchorderengine.data.model.TrackingState
import com.example.watchorderengine.network.AnilistApiService
import com.example.watchorderengine.network.AnilistRequest
import com.example.watchorderengine.network.TmdbApiService
import com.example.watchorderengine.network.TmdbConfig
import com.example.watchorderengine.util.retry
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AnimeListImport"

@Singleton
class AnimeListImportRepository @Inject constructor(
    private val anilistApi: AnilistApiService,
    private val tmdbApi: TmdbApiService,
    private val db: WatchOrderDatabase,
    private val mediaRepository: com.example.watchorderengine.data.repository.MediaRepository
) {

    /**
     * Fetches the given AniList user's complete anime list via the public GraphQL API.
     */
    suspend fun fetchAniListEntries(username: String): List<ImportedAnimeEntry> {
        val body = AnilistRequest(
            query     = buildAniListUserListQuery(),
            variables = mapOf("name" to username)
        )
        val response = anilistApi.query(body)

        if (!response.isSuccessful) {
            val errBody = response.errorBody()?.string() ?: "unknown"
            throw Exception("AniList API error ${response.code()}: $errBody")
        }

        val errors = response.body()?.errors
        if (!errors.isNullOrEmpty()) {
            val msg = errors.firstOrNull()?.message ?: "Unknown AniList error"
            if (msg.contains("User not found", ignoreCase = true) ||
                msg.contains("Invalid", ignoreCase = true)) {
                throw Exception("AniList user '$username' not found.")
            }
            throw Exception("AniList error: $msg")
        }

        val collection = response.body()?.data?.mediaListCollection
        if (collection != null) {
            return collection.lists?.flatMap { list ->
                val listStatus = list.status ?: "PLANNING"
                list.entries?.map { e ->
                    val media = e.media
                    val title = media?.title?.english?.takeIf { it.isNotBlank() }
                        ?: media?.title?.romaji ?: "Unknown"
                    ImportedAnimeEntry(
                        malId         = media?.idMal,
                        anilistId     = media?.id,
                        title         = title,
                        coverImageUrl = media?.coverImage?.large,
                        trackingState = anilistStatusToTrackingState(e.status ?: listStatus),
                        userRating    = e.score?.let { if (it > 0) it.coerceIn(0.5f, 10f) else null },
                        progress      = e.progress ?: 0,
                        totalEpisodes = media?.episodes,
                        source        = ImportedAnimeEntry.Source.ANILIST
                    )
                } ?: emptyList()
            } ?: emptyList()
        }

        return emptyList()
    }

    /**
     * Fetches a MAL user's anime list via Jikan v4.
     */
    suspend fun fetchMalEntries(username: String): List<ImportedAnimeEntry> {
        val allEntries = mutableListOf<ImportedAnimeEntry>()
        var page = 1
        var hasNextPage = true

        val jikanRetrofit = buildJikanRetrofit()

        while (hasNextPage) {
            val response: Response<JikanUserListResponse> = try {
                jikanRetrofit.getUserAnimeList(username = username, page = page)
            } catch (e: Exception) {
                throw Exception("Jikan network error (page $page): ${e.message}")
            }

            if (response.code() == 404) throw Exception("MAL user '$username' not found.")
            if (!response.isSuccessful) {
                throw Exception("Jikan API error ${response.code()} on page $page.")
            }

            val body = response.body() ?: break

            body.data.forEach { entry ->
                val status = entry.listStatus.status
                val score  = entry.listStatus.score
                allEntries.add(
                    ImportedAnimeEntry(
                        malId         = entry.node.malId,
                        anilistId     = null,
                        title         = entry.node.title,
                        coverImageUrl = entry.node.images?.jpg?.largeImageUrl
                                     ?: entry.node.images?.jpg?.imageUrl,
                        trackingState = malStatusToTrackingState(status),
                        userRating    = if (score > 0) score.toFloat().coerceIn(0.5f, 10f) else null,
                        progress      = entry.listStatus.numEpisodesWatched,
                        totalEpisodes = null, // Jikan user list doesn't reliably give series total here
                        source        = ImportedAnimeEntry.Source.MAL
                    )
                )
            }

            hasNextPage = body.pagination?.hasNextPage == true
            page++
            if (page > 30) break
        }

        Log.d(TAG, "MAL import: fetched ${allEntries.size} entries across ${page - 1} page(s)")
        return allEntries
    }

    /**
     * Persists a list of imported entries to Room as [UserProgressEntity] rows.
     */
    suspend fun persistEntriesToRoom(
        entries: List<ImportedAnimeEntry>,
        overwrite: Boolean = false,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): Int {
        var written = 0
        val now = System.currentTimeMillis()
        val total = entries.size

        for ((index, entry) in entries.withIndex()) {
            onProgress(index + 1, total)
            try {
                // Ensure the show exists in our local Media cache first
                var mediaId = resolveMediaId(entry)
                
                if (mediaId == null) {
                    mediaId = attemptDiscoveryAndCache(entry)
                }

                if (mediaId == null) {
                    Log.d(TAG, "Skipping '${entry.title}' — could not resolve or cache locally")
                    continue
                }

                val existing = db.userProgressDao().getProgress(mediaId)
                if (existing != null && !overwrite) {
                    continue
                }

                // Ensure full details exist before marking episodes
                mediaRepository.ensureDetailsFetched(mediaId)

                db.userProgressDao().upsert(
                    UserProgressEntity(
                        mediaId       = mediaId,
                        trackingState = entry.trackingState.name,
                        userRating    = entry.userRating,
                        userNotes     = existing?.userNotes ?: "",
                        priorityTag   = existing?.priorityTag ?: "NONE",
                        updatedAt     = now
                    )
                )

                // Sync the tracking state (and potentially rating) to cloud
                mediaRepository.updateTrackingState(mediaId, entry.trackingState)
                if (entry.userRating != null) {
                    mediaRepository.updateRating(mediaId, entry.userRating)
                }

                // Handle episode marking
                if (entry.trackingState == TrackingState.COMPLETED || 
                    (entry.trackingState == TrackingState.WATCHING && entry.progress > 0)) {
                    
                    val seasons = db.seasonDao().getSeasonsByMedia(mediaId)
                    val matchingSeason = seasons.find { 
                        it.name.contains(entry.title, ignoreCase = true) || 
                        entry.title.contains(it.name, ignoreCase = true) 
                    }

                    if (matchingSeason != null && entry.trackingState == TrackingState.COMPLETED) {
                        Log.d(TAG, "Segmented show detected: marking season ${matchingSeason.seasonNumber} for '${entry.title}'")
                        mediaRepository.markSeasonAsWatched(mediaId, matchingSeason.seasonNumber)
                    } else {
                        // Fallback to absolute episode marking
                        val upTo = if (entry.trackingState == TrackingState.COMPLETED) {
                            (entry.totalEpisodes ?: 0) + 1
                        } else {
                            entry.progress + 1
                        }
                        
                        if (upTo > 1) {
                            mediaRepository.markAllPreviousAsWatched(mediaId, upTo)
                        } else if (entry.trackingState == TrackingState.COMPLETED) {
                            mediaRepository.markAllAsWatched(mediaId)
                        }
                    }
                }

                written++
            } catch (e: Exception) {
                Log.w(TAG, "Failed to persist '${entry.title}': ${e.message}")
            }
        }

        Log.d(TAG, "Import complete: $written/${entries.size} entries written to Room")
        return written
    }

    private suspend fun attemptDiscoveryAndCache(entry: ImportedAnimeEntry): String? {
        try {
            val searchResponse = retry { tmdbApi.searchMulti(query = entry.title) }
            if (!searchResponse.isSuccessful) return null
            
            val results = searchResponse.body()?.results ?: return null
            val bestMatch = results.firstOrNull { it.mediaType == "tv" } 
                ?: results.firstOrNull { it.mediaType == "movie" }
                ?: return null

            val type = bestMatch.mediaType ?: "tv"
            val mediaId = buildMediaId(bestMatch.id, type)
            
            val genresList = TmdbConfig.genreNamesFor(bestMatch.genreIds ?: emptyList(), type == "movie")
            
            db.mediaDao().upsert(
                MediaEntity(
                    id = mediaId, 
                    tmdbId = bestMatch.id,
                    anilistId = entry.anilistId,
                    title = bestMatch.title ?: bestMatch.name ?: entry.title,
                    originalTitle = bestMatch.title ?: bestMatch.name ?: entry.title,
                    overview = "", tagline = "", status = "",
                    posterUrl   = TmdbConfig.buildImageUrl(bestMatch.posterPath),
                    backdropUrl = TmdbConfig.buildImageUrl(bestMatch.backdropPath, TmdbConfig.PosterSize.HD),
                    mediaCategory = if (type == "movie") "MOVIE" else "TV_SHOW",
                    genres = genresList, ageRating = "NR",
                    voteAverage = bestMatch.voteAverage?.toFloat() ?: 0f, 
                    voteCount = bestMatch.voteCount ?: 0,
                    runtime = null, numberOfSeasons = null, numberOfEpisodes = null,
                    releaseDate = bestMatch.releaseDate ?: bestMatch.firstAirDate,
                    releaseYear = (bestMatch.releaseDate ?: bestMatch.firstAirDate)?.take(4) ?: "",
                    trailerKey = null, castJson = "[]", recommendationsJson = "[]", arcsJson = "[]"
                )
            )
            return mediaId
        } catch (e: Exception) {
            Log.w(TAG, "Discovery failed for '${entry.title}': ${e.message}")
            return null
        }
    }

    private fun buildMediaId(tmdbId: Int, mediaType: String?): String {
        val prefix = when (mediaType?.lowercase()) {
            "movie" -> "tmdb_m_"
            "tv"    -> "tmdb_t_"
            else    -> "tmdb_"
        }
        return "$prefix$tmdbId"
    }

    private suspend fun resolveMediaId(entry: ImportedAnimeEntry): String? {
        if (entry.anilistId != null) {
            val match = db.mediaDao().getByAnilistId(entry.anilistId)
            if (match != null) return match.id
        }
        val cleanTitle = entry.title.trim().lowercase()
        val allMedia = db.mediaDao().getAll()
        val match = allMedia.firstOrNull {
            it.title.lowercase().contains(cleanTitle) ||
            cleanTitle.contains(it.title.lowercase())
        }
        if (match != null) return match.id
        return null
    }

    private fun buildAniListUserListQuery() = """
        query(${'$'}name: String) {
          MediaListCollection(userName: ${'$'}name, type: ANIME) {
            lists {
              name
              status
              entries {
                score(format: POINT_10)
                status
                progress
                media {
                  id
                  idMal
                  title { english romaji }
                  coverImage { large }
                  episodes
                }
              }
            }
          }
        }
    """.trimIndent()

    private fun anilistStatusToTrackingState(status: String?): TrackingState = when (status) {
        "COMPLETED" -> TrackingState.COMPLETED
        "CURRENT"   -> TrackingState.WATCHING
        "PAUSED"    -> TrackingState.PAUSED
        "PLANNING"  -> TrackingState.PLANNED
        "DROPPED"   -> TrackingState.DROPPED
        else        -> TrackingState.PLANNED
    }

    private fun malStatusToTrackingState(status: String?): TrackingState = when (status) {
        "watching"        -> TrackingState.WATCHING
        "completed"       -> TrackingState.COMPLETED
        "on_hold"         -> TrackingState.PAUSED
        "dropped"         -> TrackingState.DROPPED
        "plan_to_watch"   -> TrackingState.PLANNED
        else              -> TrackingState.PLANNED
    }

    private fun buildJikanRetrofit(): JikanUserListService {
        val client = OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        return Retrofit.Builder()
            .baseUrl("https://api.jikan.moe/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(
                com.squareup.moshi.Moshi.Builder()
                    .addLast(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
                    .build()
            ))
            .build()
            .create(JikanUserListService::class.java)
    }
}

private interface JikanUserListService {
    @GET("v4/users/{username}/animelist")
    suspend fun getUserAnimeList(
        @Path("username") username: String,
        @Query("status")  status: Int? = null,
        @Query("limit")   limit: Int = 300,
        @Query("page")    page: Int = 1
    ): Response<JikanUserListResponse>
}
