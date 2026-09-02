package com.example.watchorderengine.data

import com.example.watchorderengine.data.model.*
import com.example.watchorderengine.network.gemini.GeminiEdge
import com.example.watchorderengine.network.gemini.GeminiNode
import com.example.watchorderengine.network.gemini.GeminiArcSegment
import com.example.watchorderengine.network.gemini.RawMediaItem
import com.example.watchorderengine.network.TmdbConfig
import com.example.watchorderengine.data.db.WatchOrderDatabase
import com.example.watchorderengine.data.db.entity.PendingSyncTaskEntity
import com.example.watchorderengine.data.db.entity.TaskType
import com.example.watchorderengine.data.prefs.UserPreferencesRepository
import com.example.watchorderengine.data.sync.SyncWorker
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.toObject
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WatchOrderRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val db: WatchOrderDatabase
) {

    private fun universeRef(universeId: String): DocumentReference =
        firestore.collection("universes").document(universeId)

    private fun nodesRef(universeId: String): CollectionReference =
        universeRef(universeId).collection("nodes")

    private fun edgesRef(universeId: String): CollectionReference =
        universeRef(universeId).collection("edges")

    private fun tagsRef(universeId: String): CollectionReference =
        universeRef(universeId).collection("tags")

    private fun progressRef(universeId: String): DocumentReference {
        val uid = requireAuth()
        return firestore.collection("users").document(uid)
            .collection("progress").document(universeId)
    }

    private fun requireAuth(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("User not authenticated.")

    fun isNetworkAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val capabilities = cm.getNetworkCapabilities(cm.activeNetwork)
        return capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    /** Emits a list of universes owned by the current user. */
    fun getUniverses(): Flow<List<Universe>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listener = firestore.collection("universes")
            .whereEqualTo("owner_id", uid)
            .addSnapshotListener { snap, err ->
                if (err != null) { 
                    Log.e("WatchOrderRepo", "Error fetching universes: ${err.message}")
                    return@addSnapshotListener 
                }
                
                val universes = snap?.documents?.mapNotNull { doc ->
                    try {
                        val u = doc.toObject<Universe>()
                        if (u != null) {
                            // Ensure the ID is always set from the document ID
                            u.id = doc.id
                        }
                        u
                    } catch (e: Exception) {
                        Log.e("WatchOrderRepo", "Error parsing universe ${doc.id}: ${e.message}")
                        null
                    }
                } ?: emptyList()
                trySend(universes)
            }
        awaitClose { listener.remove() }
    }

    fun getUniverse(universeId: String): Flow<Universe> = callbackFlow {
        val listener = universeRef(universeId).addSnapshotListener { snap, _ ->
            val u = snap?.toObject<Universe>()
            if (u != null) {
                u.id = snap.id
                trySend(u)
            }
        }
        awaitClose { listener.remove() }
    }

    fun getNodes(universeId: String): Flow<List<MediaNode>> = callbackFlow {
        val listener = nodesRef(universeId).addSnapshotListener { snap, _ ->
            val nodes = snap?.documents?.mapNotNull { it.toObject<MediaNode>() } ?: emptyList()
            trySend(nodes)
        }
        awaitClose { listener.remove() }
    }

    fun getEdges(universeId: String): Flow<List<Edge>> = callbackFlow {
        val listener = edgesRef(universeId).addSnapshotListener { snap, _ ->
            val edges = snap?.documents?.mapNotNull { it.toObject<Edge>() } ?: emptyList()
            trySend(edges)
        }
        awaitClose { listener.remove() }
    }

    fun getContextTags(universeId: String): Flow<List<ContextTag>> = callbackFlow {
        val listener = tagsRef(universeId).orderBy("order").addSnapshotListener { snap, _ ->
            val tags = snap?.documents?.mapNotNull { it.toObject<ContextTag>() } ?: emptyList()
            trySend(tags)
        }
        awaitClose { listener.remove() }
    }

    fun getUserProgress(universeId: String): Flow<UserProgress> = callbackFlow {
        val listener = progressRef(universeId).addSnapshotListener { snap, _ ->
            val progress = snap?.toObject<UserProgress>() ?: UserProgress()
            trySend(progress)
        }
        awaitClose { listener.remove() }
    }

    fun findUniversesForMedia(tmdbId: Int): Flow<List<Universe>> = callbackFlow {
        val listener = firestore.collectionGroup("nodes")
            .whereEqualTo("tmdb_id", tmdbId)
            .addSnapshotListener { snap, err ->
                if (err != null) { 
                    Log.w("WatchOrderRepo", "findUniversesForMedia: Permission denied or missing index. Skipping.")
                    trySend(emptyList())
                    return@addSnapshotListener 
                }
                
                val universeRefs = snap?.documents?.mapNotNull { it.reference.parent.parent }?.distinct() ?: emptyList()
                
                if (universeRefs.isEmpty()) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                launch {
                    try {
                        val universes = universeRefs.mapNotNull { 
                            try {
                                it.get().await().toObject<Universe>()
                            } catch (e: Exception) {
                                Log.e("WatchOrderRepo", "Error parsing universe in findUniversesForMedia: ${e.message}")
                                null
                            }
                        }
                        trySend(universes)
                    } catch (e: Exception) {
                        Log.e("WatchOrderRepo", "findUniversesForMedia fetch failed: ${e.message}")
                        trySend(emptyList())
                    }
                }
            }
        awaitClose { listener.remove() }
    }

    suspend fun setNodeCompletion(
        universeId: String,
        nodeId: String,
        completed: Boolean,
        context: Context
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (isNetworkAvailable(context)) {
                setNodeCompletionDirect(universeId, nodeId, completed).getOrThrow()
            } else {
                db.pendingSyncTaskDao().insert(
                    PendingSyncTaskEntity(
                        taskType = TaskType.NODE_COMPLETION,
                        universeId = universeId,
                        nodeId = nodeId,
                        completed = completed
                    )
                )
                SyncWorker.enqueue(context)
                Unit
            }
        }
    }

    internal suspend fun setNodeCompletionDirect(
        universeId: String,
        nodeId: String,
        completed: Boolean,
    ): Result<Unit> = runCatching {
        val ref = progressRef(universeId)
        if (completed) {
            ref.set(mapOf("completed_node_ids" to FieldValue.arrayUnion(nodeId)), SetOptions.merge()).await()
        } else {
            ref.update("completed_node_ids", FieldValue.arrayRemove(nodeId)).await()
        }
    }

    internal suspend fun mirrorEpisodeWatchedToFirestore(
        episodeId: String,
        mediaId: String,
        watched: Boolean,
    ): Result<Unit> = runCatching {
        val uid = requireAuth()
        firestore.collection("users").document(uid)
            .collection("episode_progress").document(episodeId)
            .set(
                mapOf(
                    "media_id" to mediaId,
                    "watched" to watched,
                    "timestamp" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            ).await()
    }

    suspend fun setActiveRoute(universeId: String, routeTag: String): Result<Unit> = runCatching {
        progressRef(universeId).set(mapOf("active_route" to routeTag), SetOptions.merge()).await()
    }

    suspend fun updateUniversePoster(universeId: String, posterUrl: String): Result<Unit> = runCatching {
        universeRef(universeId).update("posterUrl", posterUrl).await()
    }

    suspend fun setSpoilerShieldEnabled(universeId: String, enabled: Boolean): Result<Unit> = runCatching {
        progressRef(universeId).set(mapOf("spoiler_shield_enabled" to enabled), SetOptions.merge()).await()
    }

    // ─── Gemini-Generated Universe Publishing ─────────────────────────────────

    suspend fun publishGeneratedUniverse(
        universeId: String,
        universeName: String,
        coverUrl: String,
        nodes: List<com.example.watchorderengine.data.model.MediaNode>,
        edges: List<com.example.watchorderengine.data.model.Edge>
    ): Result<Unit> = runCatching {
        check(nodes.isNotEmpty()) { "Cannot publish an empty universe." }

        val batch = firestore.batch()

        batch.set(
            universeRef(universeId),
            mapOf(
                "id" to universeId,
                "owner_id" to requireAuth(),
                "name" to universeName,
                "description" to "AI-generated watch order via Gemini.",
                "posterUrl" to coverUrl,
                "total_nodes" to nodes.size,
                "available_routes" to listOf("ALL", "CANON", "ESSENTIAL"),
                "is_public" to false,
                "timestamp" to System.currentTimeMillis()
            ),
            SetOptions.merge()
        )

        val defaultTags = listOf(
            "ALL" to mapOf("label" to "All Content", "order" to 0, "color" to "#888899"),
            "CANON" to mapOf("label" to "Canon Only", "order" to 1, "color" to "#4ADE80"),
            "ESSENTIAL" to mapOf("label" to "Essential", "order" to 2, "color" to "#60A5FA")
        )
        defaultTags.forEach { (tagId, data) ->
            batch.set(tagsRef(universeId).document(tagId), data, SetOptions.merge())
        }

        nodes.forEach { node ->
            batch.set(nodesRef(universeId).document(node.id), node)
        }
        edges.forEachIndexed { index, edge ->
            val edgeDocId = "${edge.from_node_id}__${edge.to_node_id}__$index"
            batch.set(edgesRef(universeId).document(edgeDocId), edge)
        }

        batch.commit().await()
    }

    suspend fun deleteUniverse(universeId: String): Result<Unit> = runCatching {
        val nodeDocs = nodesRef(universeId).get().await()
        val edgeDocs = edgesRef(universeId).get().await()
        val tagDocs  = tagsRef(universeId).get().await()

        val batch = firestore.batch()
        nodeDocs.documents.forEach { batch.delete(it.reference) }
        edgeDocs.documents.forEach { batch.delete(it.reference) }
        tagDocs.documents.forEach { batch.delete(it.reference) }
        batch.delete(universeRef(universeId))

        batch.commit().await()
    }

    // ─── Shared Gemini result caches (global, NOT per-user) ───────────────────

    private val arcCacheCollection get() = firestore.collection("arc_segmentation_cache")
    private val sortCacheCollection get() = firestore.collection("watch_order_sort_cache")

    data class CachedArcSegment(
        val arcName: String = "",
        val startAbsoluteEpisode: Int = 0,
        val endAbsoluteEpisode: Int = 0,
        val filler: Boolean = false
    )

    data class ArcSegmentationCacheDoc(
        val mediaId: String = "",
        val episodeCountAtSegmentation: Int = 0,
        val arcs: List<CachedArcSegment> = emptyList(),
        @com.google.firebase.firestore.ServerTimestamp
        val cachedAt: com.google.firebase.Timestamp? = null
    )

    suspend fun getCachedArcSegments(
        mediaId: String,
        currentEpisodeCount: Int
    ): List<GeminiArcSegment>? = withContext(Dispatchers.IO) {
        try {
            val doc = arcCacheCollection.document(mediaId).get().await()
            if (!doc.exists()) return@withContext null
            val cached = doc.toObject(ArcSegmentationCacheDoc::class.java) ?: return@withContext null
            if (cached.episodeCountAtSegmentation != currentEpisodeCount) {
                Log.d("WatchOrderRepo", "Arc cache stale for $mediaId (cached=${cached.episodeCountAtSegmentation}, current=$currentEpisodeCount) — will re-segment")
                return@withContext null
            }
            cached.arcs.map {
                GeminiArcSegment(
                    arcName = it.arcName,
                    startAbsoluteEpisode = it.startAbsoluteEpisode,
                    endAbsoluteEpisode = it.endAbsoluteEpisode,
                    filler = it.filler
                )
            }.also { Log.d("WatchOrderRepo", "Arc cache HIT for $mediaId — skipped a Gemini call") }
        } catch (e: Exception) {
            Log.w("WatchOrderRepo", "Arc cache read failed for $mediaId (failing open — will call Gemini): ${e.message}")
            null
        }
    }

    suspend fun cacheArcSegments(
        mediaId: String,
        episodeCount: Int,
        arcs: List<GeminiArcSegment>
    ) = withContext(Dispatchers.IO) {
        try {
            val doc = ArcSegmentationCacheDoc(
                mediaId = mediaId,
                episodeCountAtSegmentation = episodeCount,
                arcs = arcs.map { CachedArcSegment(it.arcName, it.startAbsoluteEpisode, it.endAbsoluteEpisode, it.filler) }
            )
            arcCacheCollection.document(mediaId).set(doc).await()
        } catch (e: Exception) {
            Log.w("WatchOrderRepo", "Arc cache write failed for $mediaId (non-fatal): ${e.message}")
        }
    }

    data class CachedGeminiNode(
        val itemId: String = "",
        val chronoOrder: Float = 0f,
        val releaseOrder: Float = 0f,
        val phase: String = "",
        val filler: Boolean = false,
        val isBranchPoint: Boolean = false,
        val isMergePoint: Boolean = false
    )

    data class CachedGeminiEdge(
        val fromItemId: String = "",
        val toItemId: String = "",
        val type: String = "",
        val label: String = ""
    )

    data class SortCacheDoc(
        val mediaId: String = "",
        val rawItemsFingerprint: String = "",
        val nodes: List<CachedGeminiNode> = emptyList(),
        val edges: List<CachedGeminiEdge> = emptyList(),
        @com.google.firebase.firestore.ServerTimestamp
        val cachedAt: com.google.firebase.Timestamp? = null
    )

    suspend fun getCachedSortResult(
        mediaId: String,
        fingerprint: String
    ): Pair<List<GeminiNode>, List<GeminiEdge>>? = withContext(Dispatchers.IO) {
        try {
            val doc = sortCacheCollection.document(mediaId).get().await()
            if (!doc.exists()) return@withContext null
            val cached = doc.toObject(SortCacheDoc::class.java) ?: return@withContext null
            if (cached.rawItemsFingerprint != fingerprint) {
                Log.d("WatchOrderRepo", "Sort cache stale for $mediaId — raw items changed, will re-sort")
                return@withContext null
            }
            val nodes = cached.nodes.map {
                GeminiNode(it.itemId, it.chronoOrder, it.releaseOrder, it.phase, it.filler, it.isBranchPoint, it.isMergePoint)
            }
            val edges = cached.edges.map { GeminiEdge(it.fromItemId, it.toItemId, it.type, it.label) }
            Log.d("WatchOrderRepo", "Sort cache HIT for $mediaId — skipped a Gemini call")
            nodes to edges
        } catch (e: Exception) {
            Log.w("WatchOrderRepo", "Sort cache read failed for $mediaId (failing open — will call Gemini): ${e.message}")
            null
        }
    }

    suspend fun cacheSortResult(
        mediaId: String,
        fingerprint: String,
        nodes: List<GeminiNode>,
        edges: List<GeminiEdge>
    ) = withContext(Dispatchers.IO) {
        try {
            val doc = SortCacheDoc(
                mediaId = mediaId,
                rawItemsFingerprint = fingerprint,
                nodes = nodes.map { CachedGeminiNode(it.itemId, it.chronoOrder, it.releaseOrder, it.phase, it.filler, it.isBranchPoint, it.isMergePoint) },
                edges = edges.map { CachedGeminiEdge(it.fromItemId, it.toItemId, it.type, it.label) }
            )
            sortCacheCollection.document(mediaId).set(doc).await()
        } catch (e: Exception) {
            Log.w("WatchOrderRepo", "Sort cache write failed for $mediaId (non-fatal): ${e.message}")
        }
    }

    suspend fun clearGeneratedUniverse(universeId: String): Result<Unit> = runCatching {
        val nodeDocs = nodesRef(universeId).get().await()
        val edgeDocs = edgesRef(universeId).get().await()

        val batch = firestore.batch()
        nodeDocs.documents.forEach { batch.delete(it.reference) }
        edgeDocs.documents.forEach { batch.delete(it.reference) }
        batch.commit().await()
    }

    suspend fun publishSortedUniverse(
        universeId: String,
        universeName: String,
        coverUrl: String,
        rawItems: List<RawMediaItem>,
        sortedNodes: List<GeminiNode>,
        sortedEdges: List<GeminiEdge>,
        resolveMediaId: (RawMediaItem) -> Pair<String, String>
    ): Result<Unit> = runCatching {
        check(sortedNodes.isNotEmpty()) { "Cannot publish an empty universe." }
        val itemsById = rawItems.associateBy { it.itemId }
        val resolvedIdByItemId = mutableMapOf<String, String>()

        val mediaNodes = sortedNodes.mapNotNull { node ->
            val raw = itemsById[node.itemId] ?: return@mapNotNull null
            val (parentMediaId, tmdbMediaType) = resolveMediaId(raw)

            val nodeId = raw.itemId
            resolvedIdByItemId[node.itemId] = nodeId

            MediaNode(
                id              = nodeId,
                title           = raw.title,
                content_type    = raw.contentType,
                type            = if (tmdbMediaType == "movie") MediaCategory.MOVIE else MediaCategory.TV_SHOW,
                tmdb_id         = raw.tmdbId,
                tmdb_media_type = tmdbMediaType,
                chrono_order    = node.chronoOrder,
                release_order   = node.releaseOrder,
                phase           = node.phase,
                tags            = if (node.filler) listOf("FILLER") else listOf("CANON"),
                episodeCount    = raw.episodeCount ?: 0,
                posterUrl       = raw.posterPath?.let { 
                    if (it.startsWith("http")) it 
                    else TmdbConfig.buildImageUrl(it, TmdbConfig.PosterSize.LARGE) 
                },
                parentMediaId   = parentMediaId,
                seasonNumber    = raw.seasonNumber ?: -1
            )
        }

        val mediaEdges = sortedEdges.mapNotNull { edge ->
            val from = resolvedIdByItemId[edge.fromItemId] ?: return@mapNotNull null
            val to   = resolvedIdByItemId[edge.toItemId]   ?: return@mapNotNull null
            if (from == to) null else Edge(from_node_id = from, to_node_id = to, type = edge.type)
        }

        publishGeneratedUniverse(
            universeId   = universeId,
            universeName = universeName,
            coverUrl     = coverUrl,
            nodes        = mediaNodes,
            edges        = mediaEdges
        ).getOrThrow()
    }

    // ─── Danger Zone: Full Cloud Wipe ──────────────────────────────────────────

    suspend fun deleteAllGeneratedUniverses(): Result<Unit> = runCatching {
        val uid = requireAuth()
        val universeDocs = firestore.collection("universes")
            .whereEqualTo("owner_id", uid)
            .get().await()

        for (universeDoc in universeDocs.documents) {
            val universeId = universeDoc.id
            val nodeDocs = nodesRef(universeId).get().await()
            val edgeDocs = edgesRef(universeId).get().await()
            val tagDocs  = tagsRef(universeId).get().await()
            val progressDoc = progressRef(universeId)

            (nodeDocs.documents + edgeDocs.documents + tagDocs.documents + universeDoc)
                .chunked(450)
                .forEach { chunk ->
                    val batch = firestore.batch()
                    chunk.forEach { batch.delete(it.reference) }
                    batch.commit().await()
                }
            
            progressDoc.delete().await()
        }
    }
}
