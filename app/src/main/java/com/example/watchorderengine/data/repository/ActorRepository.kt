package com.example.watchorderengine.data.repository

import android.util.Log
import com.example.watchorderengine.data.db.dao.FavoriteActorDao
import com.example.watchorderengine.data.db.entity.FavoriteActorEntity
import com.example.watchorderengine.data.model.ActorDetail
import com.example.watchorderengine.data.model.ActorSummary
import com.example.watchorderengine.data.model.CreditItem
import com.example.watchorderengine.data.model.MediaCategory
import com.example.watchorderengine.data.model.MediaSummary
import com.example.watchorderengine.network.TmdbApiService
import com.example.watchorderengine.network.TmdbConfig
import com.example.watchorderengine.util.ContentFilters
import com.example.watchorderengine.util.TmdbMappers
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ActorRepository"

@Singleton
class ActorRepository @Inject constructor(
    private val apiService: TmdbApiService,
    private val favoriteActorDao: FavoriteActorDao,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {

    suspend fun searchActors(query: String): List<ActorSummary> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        try {
            val response = apiService.searchPerson(query)
            if (!response.isSuccessful) return@withContext emptyList()
            response.body()?.results?.map {
                ActorSummary(
                    id = it.id,
                    name = it.name,
                    profilePath = TmdbConfig.buildImageUrl(it.profilePath),
                    knownForDepartment = it.knownForDepartment,
                    popularity = it.popularity?.toFloat()
                )
            } ?: emptyList()
        } catch (e: Exception) {
            Log.w(TAG, "searchActors failed for query '$query': ${e.message}")
            emptyList()
        }
    }

    suspend fun getActorDetail(personId: Int): ActorDetail? = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getPersonDetail(personId)
            if (!response.isSuccessful || response.body() == null) return@withContext null
            val body = response.body()!!

            val rawCast = body.combinedCredits?.cast ?: emptyList()
            val scriptedCast = rawCast.filter { ContentFilters.isScriptedCredit(it) }
            val credits = scriptedCast.map { TmdbMappers.toCreditItem(it) }

            val topWorks = credits.sortedWith(
                compareByDescending<CreditItem> { it.voteAverage }
                    .thenByDescending { it.year }
            ).distinctBy { it.mediaId }.take(10)

            val photos = body.images?.profiles?.mapNotNull {
                TmdbConfig.buildImageUrl(it.filePath)
            } ?: emptyList()

            ActorDetail(
                id = body.id,
                name = body.name,
                biography = body.biography ?: "",
                profilePath = TmdbConfig.buildImageUrl(body.profilePath),
                birthday = body.birthday,
                placeOfBirth = body.placeOfBirth,
                knownForDepartment = body.knownForDepartment,
                images = photos,
                filmography = credits.sortedByDescending { it.year },
                topWorks = topWorks
            )
        } catch (e: Exception) {
            Log.w(TAG, "getActorDetail failed for personId $personId: ${e.message}")
            null
        }
    }

    fun observeIsFavorite(personId: Int): Flow<Boolean> =
        favoriteActorDao.observeIsFavorite(personId)

    suspend fun isFavorite(personId: Int): Boolean = withContext(Dispatchers.IO) {
        favoriteActorDao.isFavorite(personId)
    }

    suspend fun toggleFavorite(actor: ActorSummary) = withContext(Dispatchers.IO) {
        val currentlyFav = favoriteActorDao.isFavorite(actor.id)
        val uid = auth.currentUser?.uid

        if (currentlyFav) {
            favoriteActorDao.deleteById(actor.id)
            if (uid != null) {
                try {
                    firestore.collection("users").document(uid)
                        .collection("favorite_actors").document(actor.id.toString())
                        .delete().await()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to remove favorite actor from Firestore: ${e.message}")
                }
            }
        } else {
            val entity = FavoriteActorEntity(
                id = actor.id,
                name = actor.name,
                profilePath = actor.profilePath
            )
            favoriteActorDao.upsert(entity)
            if (uid != null) {
                try {
                    firestore.collection("users").document(uid)
                        .collection("favorite_actors").document(actor.id.toString())
                        .set(entity).await()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to save favorite actor to Firestore: ${e.message}")
                }
            }
        }
    }

    suspend fun syncFavoriteActorsFromCloud() = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: return@withContext
        try {
            val snap = firestore.collection("users").document(uid)
                .collection("favorite_actors").get().await()
            val remoteEntities = snap.documents.mapNotNull { doc ->
                try {
                    val id = doc.getLong("id")?.toInt() ?: doc.id.toIntOrNull() ?: return@mapNotNull null
                    val name = doc.getString("name") ?: ""
                    val profilePath = doc.getString("profilePath")
                    FavoriteActorEntity(id = id, name = name, profilePath = profilePath)
                } catch (e: Exception) {
                    null
                }
            }
            if (remoteEntities.isNotEmpty()) {
                favoriteActorDao.upsertAll(remoteEntities)
            }
        } catch (e: Exception) {
            Log.w(TAG, "syncFavoriteActorsFromCloud failed: ${e.message}")
        }
    }

    suspend fun getTitlesForFavoriteActors(): List<MediaSummary> = withContext(Dispatchers.IO) {
        val favorites = favoriteActorDao.getAll()
        if (favorites.isEmpty()) return@withContext emptyList()

        val allTitles = mutableListOf<MediaSummary>()
        val seenMediaIds = mutableSetOf<String>()

        for (fav in favorites.take(5)) {
            try {
                val resp = apiService.getPersonCombinedCredits(fav.id)
                if (!resp.isSuccessful) continue
                val credits = resp.body()?.cast ?: continue
                val scripted = credits.filter { ContentFilters.isScriptedCredit(it) }

                for (credit in scripted.take(6)) {
                    val isMovie = credit.mediaType == "movie" || credit.title != null
                    val prefix = if (isMovie) "tmdb_m_" else "tmdb_t_"
                    val mediaId = "$prefix${credit.id}"

                    if (seenMediaIds.add(mediaId)) {
                        allTitles.add(
                            MediaSummary(
                                id = mediaId,
                                tmdbId = credit.id,
                                title = credit.title ?: credit.name ?: "Untitled",
                                posterUrl = TmdbConfig.buildImageUrl(credit.posterPath),
                                backdropUrl = null,
                                mediaCategory = if (isMovie) MediaCategory.MOVIE else MediaCategory.TV_SHOW,
                                voteAverage = credit.voteAverage?.toFloat() ?: 0f,
                                releaseYear = (credit.releaseDate ?: credit.firstAirDate ?: "").take(4),
                                trackingState = null,
                                ageRating = "",
                                releaseDate = credit.releaseDate ?: credit.firstAirDate
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed fetching credits for favorite actor ${fav.name}: ${e.message}")
            }
        }

        allTitles.sortedByDescending { it.releaseYear }
    }
}
