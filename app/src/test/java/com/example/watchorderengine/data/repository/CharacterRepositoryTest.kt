package com.example.watchorderengine.data.repository

import com.example.watchorderengine.network.AnilistApiService
import com.example.watchorderengine.network.TmdbApiService
import com.example.watchorderengine.network.gemini.GeminiService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock

class CharacterRepositoryTest {

    private val tmdbApi: TmdbApiService = mock()
    private val anilistApi: AnilistApiService = mock()
    private val geminiService: GeminiService = mock()

    private lateinit var repository: CharacterRepository

    @Before
    fun setup() {
        repository = CharacterRepository(tmdbApi, anilistApi, geminiService)
    }

    @Test
    fun `matchCharacterArt - exact match`() {
        val artMap = mapOf("luffy" to "http://luffy.jpg", "zoro" to "http://zoro.jpg")
        val result = repository.matchCharacterArt(artMap, "Luffy")
        assertEquals("http://luffy.jpg", result)
    }

    @Test
    fun `matchCharacterArt - fuzzy match (contained)`() {
        val artMap = mapOf("monkey d. luffy" to "http://luffy.jpg")
        val result = repository.matchCharacterArt(artMap, "Luffy")
        assertEquals("http://luffy.jpg", result)
    }

    @Test
    fun `matchCharacterArt - no match`() {
        val artMap = mapOf("luffy" to "http://luffy.jpg")
        val result = repository.matchCharacterArt(artMap, "Nami")
        assertNull(result)
    }

    @Test
    fun `nameMatches - various cases`() {
        assertTrue(repository.nameMatches("Monkey D. Luffy", "Luffy"))
        assertTrue(repository.nameMatches("Luffy", "Monkey D. Luffy"))
        assertFalse(repository.nameMatches("Zoro", "Luffy"))
    }
}
