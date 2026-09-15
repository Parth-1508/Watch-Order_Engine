package com.example.watchorderengine.util

import com.example.watchorderengine.data.model.CreditItem
import com.example.watchorderengine.network.TmdbConfig
import com.example.watchorderengine.network.model.TmdbPersonCastCredit

object TmdbMappers {

    fun toCreditItem(credit: TmdbPersonCastCredit): CreditItem {
        val title = credit.title ?: credit.name ?: "Untitled"
        val isMovie = credit.mediaType == "movie" || credit.title != null
        val mediaType = if (isMovie) "movie" else "tv"
        val rawDate = credit.releaseDate ?: credit.firstAirDate ?: ""
        val year = rawDate.take(4)
        val prefix = if (isMovie) "tmdb_m_" else "tmdb_t_"
        val mediaId = "$prefix${credit.id}"

        return CreditItem(
            tmdbId = credit.id,
            creditId = credit.creditId ?: "${credit.id}_${credit.character ?: ""}",
            mediaId = mediaId,
            title = title,
            character = credit.character ?: "",
            posterUrl = TmdbConfig.buildImageUrl(credit.posterPath),
            year = year,
            mediaType = mediaType,
            voteAverage = credit.voteAverage?.toFloat() ?: 0f,
            episodeCount = credit.episodeCount
        )
    }
}
