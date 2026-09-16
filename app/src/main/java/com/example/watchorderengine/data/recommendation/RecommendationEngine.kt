package com.example.watchorderengine.data.recommendation

import com.example.watchorderengine.data.db.entity.MediaEntity
import com.example.watchorderengine.data.db.entity.UserProgressEntity
import com.example.watchorderengine.data.model.TrackingState
import kotlin.math.ln
import kotlin.math.sqrt

data class Recommendation(
    val media: MediaEntity,
    val score: Double,
    val matchedGenres: List<String>,
    val matchPercentage: Int = 85
)

private val STATE_WEIGHT = mapOf(
    TrackingState.COMPLETED to 1.0,
    TrackingState.WATCHING   to 0.75,
    TrackingState.PAUSED     to 0.4,
    TrackingState.PLANNED    to 0.2,
    TrackingState.DROPPED    to -0.8,
)

private fun ratingMultiplier(userRating: Float?): Double {
    if (userRating == null) return 1.0
    return if (userRating >= 8f) 1.8 else if (userRating < 5f) 0.3 else 1.0
}

object RecommendationEngine {

    fun generateRecommendations(
        completedMedia: List<Pair<MediaEntity, UserProgressEntity>>,
        candidates: List<MediaEntity>,
        preferredGenres: Set<String> = emptySet(),
        topK: Int = 20,
        minCandidateVotes: Int = 50,
    ): List<Recommendation> {

        val tasteVector = mutableMapOf<String, Double>()

        // 1. Initial boost from onboarding choices
        preferredGenres.forEach { genre ->
            tasteVector[genre] = 2.0 // Strong initial weight
        }

        // 2. Adjust based on real behavior & ratings
        for ((media, progress) in completedMedia) {
            val stateWeight  = STATE_WEIGHT[TrackingState.valueOf(progress.trackingState)] ?: 0.0
            val ratingBoost  = ratingMultiplier(progress.userRating)
            val itemWeight   = stateWeight * ratingBoost

            if (itemWeight == 0.0) continue

            for (genre in media.genres) {
                tasteVector[genre] = (tasteVector[genre] ?: 0.0) + itemWeight
            }
        }

        if (tasteVector.isEmpty()) {
            return candidates
                .filter { !it.posterUrl.isNullOrBlank() }
                .sortedByDescending { it.voteAverage }
                .take(topK)
                .map { Recommendation(it, it.voteAverage.toDouble(), emptyList(), 80) }
        }

        val totalTracked = completedMedia.size.toDouble() + (if (preferredGenres.isNotEmpty()) 1.0 else 0.0)
        val genreDocFrequency = mutableMapOf<String, Int>()
        
        for ((media, _) in completedMedia) {
            for (genre in media.genres) {
                genreDocFrequency[genre] = (genreDocFrequency[genre] ?: 0) + 1
            }
        }
        preferredGenres.forEach { genre ->
            genreDocFrequency[genre] = (genreDocFrequency[genre] ?: 0) + 1
        }

        for ((genre, rawWeight) in tasteVector) {
            val df  = genreDocFrequency[genre] ?: 1
            val idf = ln(totalTracked / df + 1.0)
            tasteVector[genre] = rawWeight * idf
        }

        val norm = l2Norm(tasteVector.values)
        if (norm == 0.0) return emptyList()
        for (key in tasteVector.keys) { tasteVector[key] = tasteVector[key]!! / norm }

        val eligibleCandidates = candidates.filter { !it.posterUrl.isNullOrBlank() }

        val scored = eligibleCandidates.map { candidate ->
            val candidateVector = candidate.genres.associateWith { 1.0 }
            val similarity = dotProduct(tasteVector, candidateVector)
            val qualityBoost = (candidate.voteAverage / 10.0).coerceIn(0.0, 1.0)
            
            val finalScore   = (similarity * 0.8) + (qualityBoost * 0.1) + 0.1
            val matchPct     = ((similarity.coerceIn(0.1, 0.98) * 100)).toInt().coerceIn(65, 98)
            val matchedGenres = candidate.genres.filter { tasteVector.containsKey(it) }

            Quadruple(candidate, finalScore, matchedGenres, matchPct)
        }

        val finalResults = scored
            .filter { (_, score, _, _) -> score > 0.0 }
            .sortedByDescending { (_, score, _, _) -> score }
            .take(topK)
            .map { (media, score, genres, pct) ->
                Recommendation(
                    media         = media,
                    score         = score,
                    matchedGenres = genres.take(3),
                    matchPercentage = pct
                )
            }
        
        if (finalResults.size < 5) {
            val existingIds = finalResults.map { it.media.id }.toSet()
            val extras = candidates
                .filter { it.id !in existingIds }
                .sortedByDescending { it.voteAverage }
                .take(5 - finalResults.size)
                .map { Recommendation(it, 0.0, emptyList(), 75) }
            return finalResults + extras
        }
        
        return finalResults
    }

    private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    private fun dotProduct(a: Map<String, Double>, b: Map<String, Double>): Double {
        val (smaller, larger) = if (a.size <= b.size) a to b else b to a
        return smaller.entries.sumOf { (key, value) -> value * (larger[key] ?: 0.0) }
    }

    private fun l2Norm(values: Collection<Double>): Double =
        sqrt(values.sumOf { it * it })

    fun buildReasonLabel(matchedGenres: List<String>): String {
        if (matchedGenres.isEmpty()) return "Trending in your region"
        val genreStr = when (matchedGenres.size) {
            1    -> matchedGenres[0]
            2    -> "${matchedGenres[0]} & ${matchedGenres[1]}"
            else -> "${matchedGenres.dropLast(1).joinToString(", ")} & ${matchedGenres.last()}"
        }
        return "Because you like $genreStr"
    }
}
