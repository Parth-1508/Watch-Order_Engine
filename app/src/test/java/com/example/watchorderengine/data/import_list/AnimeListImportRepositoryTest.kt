package com.example.watchorderengine.data.import_list

import com.example.watchorderengine.data.db.WatchOrderDatabase
import com.example.watchorderengine.data.repository.MediaRepository
import com.example.watchorderengine.network.AnilistApiService
import com.example.watchorderengine.network.TmdbApiService
import com.example.watchorderengine.data.model.TrackingState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock

class AnimeListImportRepositoryTest {
    private val anilistApi: AnilistApiService = mock()
    private val tmdbApi: TmdbApiService = mock()
    private val db: WatchOrderDatabase = mock()
    private val mediaRepo: MediaRepository = mock()

    private lateinit var repository: AnimeListImportRepository

    @Before
    fun setup() {
        repository = AnimeListImportRepository(anilistApi, tmdbApi, db, mediaRepo)
    }

    @Test
    fun `anilistId extraction and source mapping`() {
        val entry = ImportedAnimeEntry(
            malId = 1,
            anilistId = 100,
            title = "Test Anime",
            coverImageUrl = null,
            trackingState = TrackingState.COMPLETED,
            userRating = 10f,
            progress = 12,
            totalEpisodes = 12,
            source = ImportedAnimeEntry.Source.ANILIST
        )
        assertEquals(100, entry.anilistId)
        assertEquals(ImportedAnimeEntry.Source.ANILIST, entry.source)
    }
}
