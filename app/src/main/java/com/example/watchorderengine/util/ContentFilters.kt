package com.example.watchorderengine.util

import com.example.watchorderengine.network.model.TmdbPersonCastCredit

object ContentFilters {

    /**
     * Filters out unscripted talk shows, game shows, reality TV, and self-appearances.
     */
    fun isScriptedCredit(credit: TmdbPersonCastCredit): Boolean {
        val title = (credit.title ?: credit.name ?: "").lowercase()
        val character = (credit.character ?: "").lowercase()

        if (character.contains("self") || character.contains("himself") || character.contains("herself") || character.contains("host") || character.contains("guest")) {
            return false
        }

        if (title.contains("tonight show") || title.contains("jimmy kimmel") || title.contains("late show") || title.contains("daily show") || title.contains("comic con") || title.contains("academy awards") || title.contains("grammy")) {
            return false
        }

        val genres = credit.genreIds ?: emptyList()
        // TMDB genre IDs: 10763 (News), 10764 (Reality), 10767 (Talk)
        if (genres.contains(10763) || genres.contains(10764) || genres.contains(10767)) {
            return false
        }

        return true
    }
}
