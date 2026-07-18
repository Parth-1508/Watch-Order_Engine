package com.example.watchorderengine.data.recommendation

import com.example.watchorderengine.data.db.entity.MediaEntity
import com.example.watchorderengine.data.db.entity.UserProgressEntity
import com.example.watchorderengine.data.model.TrackingState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendationEngineTest {

    private fun createMedia(id: String, genres: List<String>, voteAverage: Float = 7.0f) = MediaEntity(
        id = id,
        tmdbId = id.filter { it.isDigit() }.toIntOrNull() ?: 1,
        anilistId = null,
        title = "Title $id",
        originalTitle = "Original $id",
        overview = "Overview",
        tagline = "Tagline",
        status = "Released",
        posterUrl = "http://example.com/$id.jpg",
        backdropUrl = null,
        mediaCategory = "MOVIE",
        genres = genres,
        ageRating = "NR",
        voteAverage = voteAverage,
        voteCount = 100,
        runtime = 120,
        numberOfSeasons = null,
        numberOfEpisodes = null,
        releaseDate = "2024-01-01",
        releaseYear = "2024",
        trailerKey = null,
        castJson = "[]",
        recommendationsJson = "[]",
        arcsJson = "[]"
    )

    private fun createProgress(mediaId: String, state: TrackingState, rating: Float? = null) = UserProgressEntity(
        mediaId = mediaId,
        trackingState = state.name,
        userRating = rating
    )

    @Test
    fun `generateRecommendations - onboarding boost`() {
        val candidates = listOf(
            createMedia("1", listOf("Action", "Sci-Fi")),
            createMedia("2", listOf("Comedy")),
            createMedia("3", listOf("Drama"))
        )

        val results = RecommendationEngine.generateRecommendations(
            completedMedia = emptyList(),
            candidates = candidates,
            preferredGenres = setOf("Action"),
            topK = 5
        )

        assertEquals("Title 1", results.first().media.title)
        assertTrue(results.first().matchedGenres.contains("Action"))
    }

    @Test
    fun `generateRecommendations - behavior weight (completed vs dropped)`() {
        val completedMedia = createMedia("C1", listOf("Horror"))
        val droppedMedia   = createMedia("D1", listOf("Romance"))

        val candidates = listOf(
            createMedia("1", listOf("Horror")),
            createMedia("2", listOf("Romance"))
        )

        val results = RecommendationEngine.generateRecommendations(
            completedMedia = listOf(
                completedMedia to createProgress("C1", TrackingState.COMPLETED),
                droppedMedia to createProgress("D1", TrackingState.DROPPED)
            ),
            candidates = candidates,
            topK = 5
        )

        assertEquals("Title 1", results.first().media.title)
    }

    @Test
    fun `generateRecommendations - rating multiplier effect`() {
        val highRated = createMedia("H1", listOf("Sci-Fi"))
        val lowRated  = createMedia("L1", listOf("Thriller"))

        val candidates = listOf(
            createMedia("1", listOf("Sci-Fi")),
            createMedia("2", listOf("Thriller"))
        )

        val results = RecommendationEngine.generateRecommendations(
            completedMedia = listOf(
                highRated to createProgress("H1", TrackingState.COMPLETED, 10f),
                lowRated to createProgress("L1", TrackingState.COMPLETED, 2f)
            ),
            candidates = candidates,
            topK = 5
        )

        assertEquals("Title 1", results.first().media.title)
    }

    @Test
    fun `buildReasonLabel - formatting`() {
        assertEquals("Because you like Action", RecommendationEngine.buildReasonLabel(listOf("Action")))
        assertEquals("Because you like Action & Comedy", RecommendationEngine.buildReasonLabel(listOf("Action", "Comedy")))
        assertEquals("Because you like Action, Comedy & Drama", RecommendationEngine.buildReasonLabel(listOf("Action", "Comedy", "Drama")))
        assertEquals("Trending in your region", RecommendationEngine.buildReasonLabel(emptyList()))
    }
}
