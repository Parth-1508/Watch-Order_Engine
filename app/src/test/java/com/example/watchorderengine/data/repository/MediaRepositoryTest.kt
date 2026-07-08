package com.example.watchorderengine.data.repository

import com.example.watchorderengine.data.WatchOrderRepository
import com.example.watchorderengine.data.db.WatchOrderDatabase
import com.example.watchorderengine.data.prefs.UserPreferencesRepository
import com.example.watchorderengine.network.JikanApiService
import com.example.watchorderengine.network.TmdbApiService
import com.example.watchorderengine.network.gemini.GeminiService
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock

class MediaRepositoryTest {
    private val db: WatchOrderDatabase = mock()
    private val moshi: Moshi = Moshi.Builder().build()
    private val tmdbApi: TmdbApiService = mock()
    private val jikanApi: JikanApiService = mock()
    private val gemini: GeminiService = mock()
    private val watchOrderRepo: WatchOrderRepository = mock()
    private val userPrefs: UserPreferencesRepository = mock()
    private val firestore: FirebaseFirestore = mock()
    private val auth: FirebaseAuth = mock()

    private lateinit var repository: MediaRepository

    @Before
    fun setup() {
        repository = MediaRepository(db, moshi, tmdbApi, jikanApi, gemini, watchOrderRepo, userPrefs, firestore, auth)
    }

    @Test
    fun `extractTmdbId extracts correct numeric ID for various formats`() {
        assertEquals(10193, repository.extractTmdbId("tmdb_m_10193"))
        assertEquals(10193, repository.extractTmdbId("tmdb_t_10193"))
        assertEquals(10193, repository.extractTmdbId("tmdb_10193"))
        assertEquals(10193, repository.extractTmdbId("10193"))
        assertEquals(null, repository.extractTmdbId("invalid"))
        assertEquals(42, repository.extractTmdbId("abc_42"))
    }

    @Test
    fun `isMovieId correctly identifies movie IDs`() {
        assertTrue(repository.isMovieId("tmdb_m_123"))
        assertTrue(!repository.isMovieId("tmdb_t_123"))
    }

    @Test
    fun `isTvId correctly identifies TV show IDs`() {
        assertTrue(repository.isTvId("tmdb_t_123"))
        assertTrue(!repository.isTvId("tmdb_m_123"))
    }
}
