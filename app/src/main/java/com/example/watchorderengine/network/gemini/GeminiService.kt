package com.example.watchorderengine.network.gemini

import com.example.watchorderengine.BuildConfig
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
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

// ─── Raw Ingestion Input (TMDB / AniList — fetched BEFORE Gemini ever runs) ───

@JsonClass(generateAdapter = true)
data class RawMediaItem(
    @Json(name = "item_id")       val itemId: String,
    @Json(name = "title")         val title: String,
    @Json(name = "overview")      val overview: String,
    @Json(name = "content_type")  val contentType: String,     // "MOVIE" | "SERIES" | "SPECIAL" | "OVA"
    @Json(name = "season_number") val seasonNumber: Int? = null,
    @Json(name = "episode_count") val episodeCount: Int? = null,
    @Json(name = "release_date")  val releaseDate: String? = null,
    @Json(name = "tmdb_id")       val tmdbId: Int = 0,
    @Json(name = "anilist_id")    val anilistId: Int? = null,
    @Json(name = "source")        val source: String,            // "TMDB_SEASON" | "TMDB_MOVIE" | "ANILIST_RELATION"
    @Json(name = "poster_path")   val posterPath: String? = null
)

// ─── Gemini Response Models (SORTER output only) ──────────────────────────────

@JsonClass(generateAdapter = true)
data class GeminiWatchOrder(
    @Json(name = "nodes") val nodes: List<GeminiNode>,
    @Json(name = "edges") val edges: List<GeminiEdge>
)

@JsonClass(generateAdapter = true)
data class GeminiNode(
    @Json(name = "item_id")          val itemId: String,   // MUST match a RawMediaItem.itemId verbatim
    @Json(name = "chrono_order")     val chronoOrder: Float,
    @Json(name = "release_order")    val releaseOrder: Float,
    @Json(name = "phase")            val phase: String,
    @Json(name = "filler")           val filler: Boolean,
    @Json(name = "is_branch_point")  val isBranchPoint: Boolean,
    @Json(name = "is_merge_point")   val isMergePoint: Boolean
)

@JsonClass(generateAdapter = true)
data class GeminiEdge(
    @Json(name = "from_item_id") val fromItemId: String,
    @Json(name = "to_item_id")   val toItemId: String,
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
    @Json(name = "responseSchema") val responseSchema: Any? = null
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

    private val rawItemListAdapter =
        moshi.adapter<List<RawMediaItem>>(Types.newParameterizedType(List::class.java, RawMediaItem::class.java))

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

    /**
     * Sorts and classifies a FIXED array of [RawMediaItem]s that were already
     * fetched from TMDB/AniList. Gemini cannot add, remove, rename, or
     * re-identify entries here — it only assigns chronological order, a
     * phase label, a filler flag, and (optionally) branch/merge edges
     * between the exact items it was given.
     */
    suspend fun generateWatchOrder(
        showTitle: String,
        rawItems: List<RawMediaItem>
    ): GeminiResult = withContext(Dispatchers.IO) {

        if (rawItems.isEmpty()) {
            return@withContext GeminiResult.Error("No raw TMDB/AniList items supplied to sort.")
        }

        val allKeys = getAllApiKeys()
        if (allKeys.isEmpty()) {
            return@withContext GeminiResult.Error(
                "No Gemini API keys found. Add them to local.properties."
            )
        }

        if (rawItems.size == 1) {
            val only = rawItems.first()
            return@withContext GeminiResult.Success(
                GeminiWatchOrder(
                    nodes = listOf(
                        GeminiNode(
                            itemId = only.itemId, chronoOrder = 0f, releaseOrder = 0f,
                            phase = "Main Story", filler = false,
                            isBranchPoint = false, isMergePoint = false
                        )
                    ),
                    edges = emptyList()
                )
            )
        }

        val prompt = buildSortPrompt(showTitle, rawItems)
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

        var lastError: String? = null

        // Cycle through keys until one works or all fail
        for (apiKey in allKeys) {
            val request = Request.Builder()
                .url("$ENDPOINT?key=$apiKey")
                .post(requestJson.toRequestBody(mediaTypeJson))
                .header("Content-Type", "application/json")
                .build()

            try {
                android.util.Log.d("GeminiService", "Sending sort request to Gemini (Key starts with: ${apiKey.take(6)}...)")
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: continue
                
                if (response.isSuccessful) {
                    val envelope = moshi.adapter(GeminiResponse::class.java).fromJson(body)
                    val rawJson = envelope?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                        ?: return@withContext GeminiResult.Error("Gemini returned an empty response body.")

                    val parsed = moshi.adapter(GeminiWatchOrder::class.java).fromJson(rawJson)
                        ?: return@withContext GeminiResult.Error("Failed to parse Gemini response.")

                    return@withContext GeminiResult.Success(sanitizeAgainstRawItems(parsed, rawItems))
                } else {
                    lastError = "Gemini error HTTP ${response.code}: $body"
                    android.util.Log.e("GeminiService", "Key failed (${response.code}). Trying next key if available...")
                    if (response.code == 401 || response.code == 429) continue
                    else break
                }
            } catch (e: Exception) {
                lastError = "Network error: ${e.message}"
                android.util.Log.e("GeminiService", "Network error with key. Trying next...")
                continue
            }
        }

        GeminiResult.Error(lastError ?: "Failed to generate watch order after trying all API keys.")
    }

    /**
     * Fetches a character description from Gemini as a high-quality fallback
     * when Wikipedia provides irrelevant or missing data.
     */
    suspend fun fetchCharacterLore(
        characterName: String,
        mediaTitle: String,
        actorName: String?
    ): String? = withContext(Dispatchers.IO) {
        val allKeys = getAllApiKeys()
        if (allKeys.isEmpty()) return@withContext null

        val prompt = """
            Provide a concise "about the character" summary for the fictional character "$characterName" 
            from the movie/show "$mediaTitle"${if (actorName != null) " played by $actorName" else ""}.
            Focus on their role, personality, and significance in the story.
            If you don't have specific info about this character, respond with "UNKNOWN".
            Output should be plain text, max 3 sentences. Do not use markdown.
        """.trimIndent()

        val requestBody = GeminiRequestBody(
            contents = listOf(GeminiRequestContent(parts = listOf(GeminiRequestPart(prompt)))),
            generationConfig = GeminiGenerationConfig(responseMimeType = "text/plain")
        )
        val requestJson = moshi.adapter(GeminiRequestBody::class.java).toJson(requestBody)

        for (apiKey in allKeys) {
            val request = Request.Builder()
                .url("$ENDPOINT?key=$apiKey")
                .post(requestJson.toRequestBody(mediaTypeJson))
                .build()

            try {
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: continue
                if (response.isSuccessful) {
                    val envelope = moshi.adapter(GeminiResponse::class.java).fromJson(body)
                    val text = envelope?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
                    if (text == null || text.equals("UNKNOWN", ignoreCase = true)) return@withContext null
                    return@withContext text
                }
            } catch (e: Exception) {
                continue
            }
        }
        null
    }

    private fun getAllApiKeys(): List<String> {
        return listOfNotNull(
            BuildConfig.GEMINI_API_KEY.takeIf { it.isNotBlank() },
            BuildConfig.GEMINI_BURNER_KEY_1.takeIf { it.isNotBlank() },
            BuildConfig.GEMINI_BURNER_KEY_2.takeIf { it.isNotBlank() },
            BuildConfig.GEMINI_BURNER_KEY_3.takeIf { it.isNotBlank() },
            BuildConfig.GEMINI_BURNER_KEY_4.takeIf { it.isNotBlank() },
            BuildConfig.GEMINI_BURNER_KEY_5.takeIf { it.isNotBlank() },
            BuildConfig.GEMINI_BURNER_KEY_6.takeIf { it.isNotBlank() },
            BuildConfig.GEMINI_BURNER_KEY_7.takeIf { it.isNotBlank() }
        ).distinct()
    }


    private fun sanitizeAgainstRawItems(
        watchOrder: GeminiWatchOrder,
        rawItems: List<RawMediaItem>
    ): GeminiWatchOrder {
        val validIds = rawItems.map { it.itemId }.toSet()
        val seen = mutableSetOf<String>()

        val cleanNodes = watchOrder.nodes.filter { node ->
            val isValid = node.itemId in validIds
            val isFirstSeen = seen.add(node.itemId)
            isValid && isFirstSeen
        }

        val missing = (validIds - seen).map { id ->
            val nextOrder = (cleanNodes.size).toFloat()
            GeminiNode(
                itemId = id, chronoOrder = nextOrder, releaseOrder = nextOrder,
                phase = "Uncategorized", filler = false,
                isBranchPoint = false, isMergePoint = false
            )
        }

        val finalIds = seen + missing.map { it.itemId }
        val cleanEdges = watchOrder.edges.filter { it.fromItemId in finalIds && it.toItemId in finalIds }

        return GeminiWatchOrder(nodes = cleanNodes + missing, edges = cleanEdges)
    }

    private fun buildSortPrompt(showTitle: String, rawItems: List<RawMediaItem>): String {
        val itemsJson = rawItemListAdapter.toJson(rawItems)

        return """
            You are a SORTER, not an archivist. Do NOT use your own memory of "$showTitle"
            to generate content — every entry you are allowed to talk about is already
            listed below, fetched directly from TMDB/AniList.

            RAW_ITEMS (the complete, fixed, real dataset — ${rawItems.size} entries):
            $itemsJson

            YOUR ONLY JOB:
            1. Assign each item a chrono_order (story chronology) and release_order
               (real-world release order) as floats, lowest = first.
            2. Assign each item a short "phase" label (saga/season grouping) for display.
            3. Set filler=true only for items that are optional/skippable side content;
               filler=false for anything required to follow the main story.
            4. Set is_branch_point/is_merge_point ONLY where the supplied items
               genuinely diverge or reconverge. Do not invent branches.
            5. Add edges only between item_id values that appear in RAW_ITEMS.

            HARD RULES — violating any of these makes your response unusable:
            - Do NOT add an item that is not in RAW_ITEMS.
            - Do NOT omit an item that IS in RAW_ITEMS — every item_id above must
              appear exactly once in your "nodes" array.
            - Every "item_id" you output must be copied character-for-character
              from RAW_ITEMS. Never paraphrase, retype, or invent one.
            - Do NOT include title, overview, episode counts, or any TMDB/AniList
              ID in your response — those fields already exist upstream; you were
              not given a place to set them because you are not allowed to.

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
                  "item_id":         { "type": "string" },
                  "chrono_order":    { "type": "number" },
                  "release_order":   { "type": "number" },
                  "phase":           { "type": "string" },
                  "filler":          { "type": "boolean" },
                  "is_branch_point": { "type": "boolean" },
                  "is_merge_point":  { "type": "boolean" }
                },
                "required": ["item_id", "chrono_order", "release_order", "phase",
                             "filler", "is_branch_point", "is_merge_point"]
              }
            },
            "edges": {
              "type": "array",
              "items": {
                "type": "object",
                "properties": {
                  "from_item_id": { "type": "string" },
                  "to_item_id":   { "type": "string" },
                  "type":         { "type": "string", "enum": ["REQUIRED", "RECOMMENDED", "OPTIONAL"] },
                  "label":        { "type": "string" }
                },
                "required": ["from_item_id", "to_item_id", "type", "label"]
              }
            }
          },
          "required": ["nodes", "edges"]
        }
    """.trimIndent()
}
