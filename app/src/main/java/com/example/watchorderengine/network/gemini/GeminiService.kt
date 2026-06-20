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

@JsonClass(generateAdapter = true)
data class GeminiWatchOrder(
    @Json(name = "arcs") val arcs: List<GeminiArc>
)

@JsonClass(generateAdapter = true)
data class GeminiArc(
    @Json(name = "arc_name")        val arcName: String,
    @Json(name = "synopsis")        val synopsis: String,
    @Json(name = "start_season")    val startSeason: Int,
    @Json(name = "start_episode")   val startEpisode: Int,
    @Json(name = "end_season")      val endSeason: Int,
    @Json(name = "end_episode")     val endEpisode: Int,
    @Json(name = "classification")  val classification: String,
    @Json(name = "tags")            val tags: List<String>,
    @Json(name = "watch_priority")  val watchPriority: String,
    @Json(name = "spoiler_free_hint") val spoilerFreeHint: String
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
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val mediaTypeJson = "application/json; charset=utf-8".toMediaType()

    companion object {
        private const val ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent"
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
        val requestJson = buildRequestJson(prompt)

        val request = Request.Builder()
            .url("$ENDPOINT?key=$apiKey")
            .post(requestJson.toRequestBody(mediaTypeJson))
            .header("Content-Type", "application/json")
            .build()

        return@withContext try {
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext GeminiResult.Error(
                "Empty response from Gemini (HTTP ${response.code})"
            )

            if (!response.isSuccessful) {
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
            Generate a precise watch-order guide for the series or movie "$showTitle".
            Overview: $overview
            $episodeInfo
            
            IMPORTANT: Return ONLY raw JSON matching the schema below. No markdown formatting, no code blocks, no preamble.
            
            Return JSON with "arcs" array. Each arc has:
            - arc_name (string): Name of the story arc.
            - synopsis (string): Brief summary of what happens.
            - start_season (integer): 1-based season number.
            - start_episode (integer): 1-based episode number within that season.
            - end_season (integer): 1-based season number.
            - end_episode (integer): 1-based episode number within that season.
            - classification (string): Must be exactly "CANON", "FILLER", or "MIXED".
            - tags (array of strings): Relevant keywords.
            - watch_priority (string): Must be "ESSENTIAL", "RECOMMENDED", "OPTIONAL", or "SKIP".
            - spoiler_free_hint (string): A tip for new viewers.
        """.trimIndent()
    }

    private fun buildRequestJson(prompt: String): String {
        val escapedPrompt = prompt.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        return """
        {
          "contents": [{"parts": [{"text": "$escapedPrompt"}]}],
          "generationConfig": {
            "responseMimeType": "application/json",
            "responseSchema": {
              "type": "object",
              "properties": {
                "arcs": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "properties": {
                      "arc_name": {"type": "string"},
                      "synopsis": {"type": "string"},
                      "start_season": {"type": "integer"},
                      "start_episode": {"type": "integer"},
                      "end_season": {"type": "integer"},
                      "end_episode": {"type": "integer"},
                      "classification": {"type": "string", "enum": ["CANON", "FILLER", "MIXED"]},
                      "tags": {"type": "array", "items": {"type": "string"}},
                      "watch_priority": {"type": "string", "enum": ["ESSENTIAL", "RECOMMENDED", "OPTIONAL", "SKIP"]},
                      "spoiler_free_hint": {"type": "string"}
                    },
                    "required": ["arc_name", "synopsis", "start_season", "start_episode", "end_season", "end_episode", "classification", "tags", "watch_priority", "spoiler_free_hint"]
                  }
                }
              },
              "required": ["arcs"]
            }
          }
        }
        """.trimIndent()
    }
}
