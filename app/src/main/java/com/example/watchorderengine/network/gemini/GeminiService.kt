package com.example.watchorderengine.network.gemini

import com.example.watchorderengine.BuildConfig
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

// ─── Gemini Response Models ───────────────────────────────────────────────────

/**
 * The structured JSON Gemini returns: a full chronological/branching DAG
 * for the requested show or movie — nodes (watchable units / arcs) plus
 * directed edges expressing "watch A before B" prerequisites.
 */
@JsonClass(generateAdapter = true)
data class GeminiWatchOrder(
    @Json(name = "nodes") val nodes: List<GeminiNode>,
    @Json(name = "edges") val edges: List<GeminiEdge>
)

@JsonClass(generateAdapter = true)
data class GeminiNode(
    @Json(name = "node_id")          val nodeId: String,
    @Json(name = "title")            val title: String,
    @Json(name = "synopsis")         val synopsis: String,
    @Json(name = "content_type")     val contentType: String,     // "MOVIE" | "SERIES" | "SPECIAL" | "EPISODE" | "SHORT"
    @Json(name = "start_season")     val startSeason: Int,
    @Json(name = "start_episode")    val startEpisode: Int,
    @Json(name = "end_season")       val endSeason: Int,
    @Json(name = "end_episode")      val endEpisode: Int,
    @Json(name = "chrono_order")     val chronoOrder: Float,
    @Json(name = "release_order")    val releaseOrder: Float,
    @Json(name = "phase")            val phase: String,
    @Json(name = "tags")             val tags: List<String>,
    @Json(name = "is_branch_point")  val isBranchPoint: Boolean,
    @Json(name = "is_merge_point")   val isMergePoint: Boolean,
    @Json(name = "tmdb_id")          val tmdbId: Int = 0,
    @Json(name = "search_query")     val searchQuery: String? = null
)

@JsonClass(generateAdapter = true)
data class GeminiEdge(
    @Json(name = "from_node_id") val fromNodeId: String,
    @Json(name = "to_node_id")   val toNodeId: String,
    @Json(name = "type")         val type: String,          // "REQUIRED" | "RECOMMENDED" | "OPTIONAL"
    @Json(name = "label")        val label: String
)

@JsonClass(generateAdapter = true)
internal data class GeminiResponse(
    @Json(name = "candidates") val candidates: List<GeminiCandidate>?
)

@JsonClass(generateAdapter = true)
internal data class GeminiCandidate(
    @Json(name = "content") val content: GeminiContent?
)

@JsonClass(generateAdapter = true)
internal data class GeminiContent(
    @Json(name = "parts") val parts: List<GeminiPart>?
)

@JsonClass(generateAdapter = true)
internal data class GeminiPart(
    @Json(name = "text") val text: String?
)

@JsonClass(generateAdapter = true)
internal data class GeminiRequestBody(
    @Json(name = "contents") val contents: List<GeminiRequestContent>,
    @Json(name = "generationConfig") val generationConfig: GeminiGenerationConfig
)

@JsonClass(generateAdapter = true)
internal data class GeminiRequestContent(@Json(name = "parts") val parts: List<GeminiRequestPart>)

@JsonClass(generateAdapter = true)
internal data class GeminiRequestPart(@Json(name = "text") val text: String)

@JsonClass(generateAdapter = true)
internal data class GeminiGenerationConfig(
    @Json(name = "responseMimeType") val responseMimeType: String,
    @Json(name = "responseSchema") val responseSchema: Any
)

sealed interface GeminiResult {
    data class Success(val watchOrder: GeminiWatchOrder) : GeminiResult
    data class Error(val message: String) : GeminiResult
}

@Singleton
class GeminiService @Inject constructor() {

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val mediaTypeJson = "application/json; charset=utf-8".toMediaType()

    companion object {
        private const val ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"
    }

    suspend fun generateWatchOrder(
        showTitle: String,
        overview: String,
        seasonEpisodeCounts: List<Int>,
        mediaType: String
    ): GeminiResult = withContext(Dispatchers.IO) {

        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank()) {
            return@withContext GeminiResult.Error(
                "GEMINI_API_KEY is not set. Add it to local.properties."
            )
        }

        val prompt = buildPrompt(showTitle, overview, seasonEpisodeCounts, mediaType)
        val schemaAny = moshi.adapter(Any::class.java).fromJson(RESPONSE_SCHEMA_JSON)
            ?: return@withContext GeminiResult.Error("Internal error building Gemini request schema.")

        val requestBody = GeminiRequestBody(
            contents = listOf(GeminiRequestContent(parts = listOf(GeminiRequestPart(prompt)))),
            generationConfig = GeminiGenerationConfig(
                responseMimeType = "application/json",
                responseSchema = schemaAny
            )
        )
        val requestJson = moshi.adapter(GeminiRequestBody::class.java).toJson(requestBody)

        val request = Request.Builder()
            .url("$ENDPOINT?key=$apiKey")
            .post(requestJson.toRequestBody(mediaTypeJson))
            .header("Content-Type", "application/json")
            .build()

        return@withContext try {
            android.util.Log.d("GeminiService", "Sending request to Gemini...")
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext GeminiResult.Error(
                "Empty response from Gemini (HTTP ${response.code})"
            )
            android.util.Log.d("GeminiService", "Received response: ${response.code}")

            if (!response.isSuccessful) {
                android.util.Log.e("GeminiService", "Gemini error body: $body")
                return@withContext GeminiResult.Error("Gemini error HTTP ${response.code}")
            }

            val envelope = moshi.adapter(GeminiResponse::class.java).fromJson(body)
            val rawJson = envelope?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: return@withContext GeminiResult.Error("Gemini returned an empty response body.")

            val watchOrder = moshi.adapter(GeminiWatchOrder::class.java).fromJson(rawJson)
                ?: return@withContext GeminiResult.Error("Failed to parse Gemini response.")

            GeminiResult.Success(watchOrder)

        } catch (e: Exception) {
            GeminiResult.Error("Network error calling Gemini: ${e.message}")
        }
    }

    private fun buildPrompt(
        showTitle: String,
        overview: String,
        seasonEpisodeCounts: List<Int>,
        mediaType: String
    ): String {
        val episodeInfo = if (mediaType == "tv" && seasonEpisodeCounts.isNotEmpty()) {
            "Episode counts: ${seasonEpisodeCounts.joinToString(", ")}"
        } else ""

        return """
            You are a world-class anime and cinema archivist specializing in "Master Watch Orders". 
            Generate a definitive, high-accuracy branching DAG (Directed Acyclic Graph) for: "$showTitle".
            Overview: $overview
            $episodeInfo

            CORE PHILOSOPHY:
            1. Group content into logical "Arcs" or "Movies". 
            2. For long series, do NOT generate one node per episode. Group them into cohesive Arcs (e.g., "Chunin Exams: Eps 20-67").
            3. Use edges ONLY for strict requirements. Avoid "spaghetti" connections.
            4. Identify true branches (e.g., skip filler, watch a movie optionally).

            NODE SPECIFICATION:
            - node_id: lowercase slug (e.g., "arc_land_of_waves")
            - title: Clear name
            - synopsis: 1-sentence summary
            - content_type: MOVIE, SERIES, EPISODE, SHORT, SPECIAL
            - start_season / start_episode / end_season / end_episode: Precise boundaries.
            - chrono_order: Sequential float.
            - release_order: Sequential float.
            - phase: Major saga name.
            - tags: [CANON, FILLER, MIXED, MOVIE, SPECIAL, MUST_WATCH].
            - is_branch_point: True if this node leads to multiple mutually exclusive or optional paths.
            - is_merge_point: True if this node requires multiple previous paths to be finished.
            - tmdb_id: The official TMDB ID for this specific movie or series. For arcs within the current show, use 0.
            - search_query: A precise TMDB search string for this specific node. 
              IMPORTANT: For sequels, include the full name and year (e.g., "Iron Man 2 2010", "Avengers: Endgame 2019"). 
              For series arcs, search for the series name (e.g., "Naruto Shippuden").

            EDGE SPECIFICATION:
            - type: REQUIRED (prerequisite), RECOMMENDED (best flow), OPTIONAL (extra).

            FORMAT: Return ONLY valid JSON.
        """.trimIndent()
    }

    private val RESPONSE_SCHEMA_JSON = """
        {
          "type": "object",
          "properties": {
            "nodes": {
              "type": "array",
              "items": {
                "type": "object",
                "properties": {
                  "node_id":         { "type": "string" },
                  "title":           { "type": "string" },
                  "synopsis":        { "type": "string" },
                  "content_type":    { "type": "string", "enum": ["MOVIE", "SERIES", "EPISODE", "SHORT", "SPECIAL", "COMIC", "NOVEL", "GAME"] },
                  "start_season":    { "type": "integer" },
                  "start_episode":   { "type": "integer" },
                  "end_season":      { "type": "integer" },
                  "end_episode":     { "type": "integer" },
                  "chrono_order":    { "type": "number" },
                  "release_order":   { "type": "number" },
                  "phase":           { "type": "string" },
                  "tags":            { "type": "array", "items": { "type": "string" } },
                  "is_branch_point": { "type": "boolean" },
                  "is_merge_point":  { "type": "boolean" },
                  "tmdb_id":         { "type": "integer" },
                  "search_query":    { "type": "string" }
                },
                "required": ["node_id", "title", "synopsis", "content_type", 
                             "start_season", "start_episode", "end_season", "end_episode", 
                             "chrono_order", "release_order", "phase", "tags", 
                             "is_branch_point", "is_merge_point", "tmdb_id", "search_query"]
              }
            },
            "edges": {
              "type": "array",
              "items": {
                "type": "object",
                "properties": {
                  "from_node_id": { "type": "string" },
                  "to_node_id":   { "type": "string" },
                  "type":         { "type": "string", "enum": ["REQUIRED", "RECOMMENDED", "OPTIONAL"] },
                  "label":        { "type": "string" }
                },
                "required": ["from_node_id", "to_node_id", "type", "label"]
              }
            }
          },
          "required": ["nodes", "edges"]
        }
    """.trimIndent()
}
