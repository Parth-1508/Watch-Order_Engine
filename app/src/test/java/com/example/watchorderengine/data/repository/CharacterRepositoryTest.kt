package com.example.watchorderengine.data.repository

import com.example.watchorderengine.network.AnilistApiService
import com.example.watchorderengine.network.TmdbApiService
import com.example.watchorderengine.network.WikipediaApiService
import com.example.watchorderengine.network.gemini.GeminiService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock

class CharacterRepositoryTest {

    private val tmdbApi: TmdbApiService = mock()
    private val anilistApi: AnilistApiService = mock()
    private val wikipediaApi: WikipediaApiService = mock()
    private val geminiService: GeminiService = mock()

    private lateinit var repository: CharacterRepository

    @Before
    fun setup() {
        repository = CharacterRepository(tmdbApi, anilistApi, wikipediaApi, geminiService)
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
    fun `isLoreRelevant - positive match by media title`() {
        val extract = "Monkey D. Luffy is the main protagonist of the One Piece series."
        val result = repository.isLoreRelevant(extract, "Luffy", "One Piece", "Mayumi Tanaka")
        assertTrue(result)
    }

    @Test
    fun `isLoreRelevant - positive match by actor name`() {
        val extract = "Monkey D. Luffy is a fictional character voiced by Mayumi Tanaka."
        val result = repository.isLoreRelevant(extract, "Luffy", "One Piece", "Mayumi Tanaka")
        assertTrue(result)
    }

    @Test
    fun `isLoreRelevant - negative match for common name without context`() {
        val extract = "Ally is a 2014 drama film about a girl..."
        // Character is Ally, Movie is "A Star is Born"
        val result = repository.isLoreRelevant(extract, "Ally", "A Star is Born", "Lady Gaga")
        assertFalse(result)
    }

    @Test
    fun `nameMatches - various cases`() {
        assertTrue(repository.nameMatches("Monkey D. Luffy", "Luffy"))
        assertTrue(repository.nameMatches("Luffy", "Monkey D. Luffy"))
        assertFalse(repository.nameMatches("Zoro", "Luffy"))
    }
}
