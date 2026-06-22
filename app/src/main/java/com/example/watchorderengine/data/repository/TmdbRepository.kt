package com.example.watchorderengine.data.repository

import com.example.watchorderengine.data.model.MediaNode
import com.example.watchorderengine.data.cache.TmdbFetchState
import com.example.watchorderengine.data.cache.TmdbMetadataCache
import com.example.watchorderengine.network.TmdbApiService
import com.example.watchorderengine.network.TmdbConfig
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.supervisorScope
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository responsible for fetching TMDB metadata and keeping the
 * [TmdbMetadataCache] populated.
 */
@Singleton
class TmdbRepository @Inject constructor(
    private val apiService: TmdbApiService,
    private val cache: TmdbMetadataCache
) {
    companion object {
        private const val BATCH_SIZE = 10
        private const val INTER_BATCH_DELAY_MS = 200L
    }

    /**
     * Fetches TMDB metadata for all [nodes] that aren't already cached.
     */
    suspend fun fetchAndCache(nodes: List<MediaNode>): Map<Int, TmdbFetchState> {
        val uncached = cache.filterUncached(nodes)

        if (uncached.isEmpty()) {
            return cache.snapshotFor(nodes)
        }

        uncached.forEach { node -> cache.put(node.tmdb_id, TmdbFetchState.Loading) }

        uncached.chunked(BATCH_SIZE).forEachIndexed { batchIndex, batch ->
            if (batchIndex > 0) delay(INTER_BATCH_DELAY_MS)

            supervisorScope {
                batch.map { node ->
                    async { fetchSingle(node.tmdb_id, node.tmdb_media_type) }
                }.forEach { it.await() }
            }
        }

        return cache.snapshotFor(nodes)
    }

    private suspend fun fetchSingle(tmdbId: Int, tmdbMediaType: String) {
        if (cache.isSuccessfullyCached(tmdbId)) return

        val state: TmdbFetchState = try {
            val detail = when (tmdbMediaType) {
                TmdbConfig.MEDIA_TYPE_MOVIE -> {
                    val response = apiService.getMovie(tmdbId)
                    if (response.isSuccessful) {
                        response.body()?.toDomainModel()
                            ?: throw IOException("Null body for movie tmdbId=$tmdbId")
                    } else {
                        throw HttpException(response)
                    }
                }
                TmdbConfig.MEDIA_TYPE_TV -> {
                    val response = apiService.getTvShow(tmdbId)
                    if (response.isSuccessful) {
                        response.body()?.toDomainModel()
                            ?: throw IOException("Null body for TV tmdbId=$tmdbId")
                    } else {
                        throw HttpException(response)
                    }
                }
                else -> {
                    // Try movie, then TV as a fallback if the type is unknown or generic
                    val movieResponse = apiService.getMovie(tmdbId)
                    if (movieResponse.isSuccessful && movieResponse.body() != null) {
                        movieResponse.body()!!.toDomainModel()
                    } else {
                        val tvResponse = apiService.getTvShow(tmdbId)
                        if (tvResponse.isSuccessful && tvResponse.body() != null) {
                            tvResponse.body()!!.toDomainModel()
                        } else {
                            throw IOException("Could not resolve metadata for tmdbId=$tmdbId as Movie or TV")
                        }
                    }
                }
            }
            TmdbFetchState.Success(detail)

        } catch (e: HttpException) {
            val errMsg = when (e.code()) {
                401 -> "Unauthorized — check your TMDB API token."
                404 -> "Not found on TMDB (tmdbId=$tmdbId)."
                429 -> "Rate limited by TMDB."
                in 500..599 -> "TMDB server error (${e.code()})."
                else -> "HTTP error ${e.code()}"
            }
            TmdbFetchState.Error(errMsg, tmdbId)

        } catch (e: IOException) {
            TmdbFetchState.Error(
                message = "Network error: ${e.message ?: "Check your connection."}",
                tmdbId = tmdbId
            )

        } catch (e: Exception) {
            TmdbFetchState.Error(
                message = "Unexpected error: ${e.message}",
                tmdbId = tmdbId
            )
        }

        cache.put(tmdbId, state)
    }
}
