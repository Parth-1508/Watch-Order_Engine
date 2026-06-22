package com.example.watchorderengine.data

import com.example.watchorderengine.data.model.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.toObject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for all data. Abstracts Firestore from the ViewModel.
 */
@Singleton
class WatchOrderRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {

    // ─── Firestore Reference Helpers ─────────────────────────────────────────

    private fun universeRef(universeId: String) =
        firestore.collection("universes").document(universeId)

    private fun nodesRef(universeId: String) =
        universeRef(universeId).collection("nodes")

    private fun edgesRef(universeId: String) =
        universeRef(universeId).collection("edges")

    private fun tagsRef(universeId: String) =
        universeRef(universeId).collection("tags")

    private fun progressRef(universeId: String): com.google.firebase.firestore.DocumentReference {
        val uid = requireAuth()
        return firestore.collection("users").document(uid)
            .collection("progress").document(universeId)
    }

    private fun requireAuth() =
        auth.currentUser?.uid ?: error("Action requires an authenticated user.")

    // ─── Real-time Read Flows ─────────────────────────────────────────────────

    /** Emits a list of all available entertainment universes. */
    fun getUniverses(): Flow<List<Universe>> = callbackFlow {
        val listener = firestore.collection("universes")
            .addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                val universes = snap?.documents?.mapNotNull { doc ->
                    doc.toObject<Universe>()?.copy(id = doc.id)
                } ?: emptyList()
                trySend(universes)
            }
        awaitClose { listener.remove() }
    }


    /**
     * Emits [Universe] metadata whenever the Firestore document changes.
     */
    fun getUniverse(universeId: String): Flow<Universe> = callbackFlow {
        val listener = universeRef(universeId).addSnapshotListener { snap, err ->
            if (err != null) { close(err); return@addSnapshotListener }
            snap?.toObject<Universe>()?.copy(id = snap.id)?.let { trySend(it) }
        }
        awaitClose { listener.remove() }
    }


    /**
     * Emits the full node list whenever any node in the subcollection changes.
     */
    fun getNodes(universeId: String): Flow<List<MediaNode>> = callbackFlow {
        val listener = nodesRef(universeId).addSnapshotListener { snap, err ->
            if (err != null) { close(err); return@addSnapshotListener }
            val nodes = snap?.documents?.mapNotNull { doc ->
                doc.toObject<MediaNode>()
            } ?: emptyList()
            trySend(nodes)
        }
        awaitClose { listener.remove() }
    }


    fun getEdges(universeId: String): Flow<List<Edge>> = callbackFlow {
        val listener = edgesRef(universeId).addSnapshotListener { snap, err ->
            if (err != null) { close(err); return@addSnapshotListener }
            val edges = snap?.documents?.mapNotNull { doc -> doc.toObject<Edge>() } ?: emptyList()
            trySend(edges)
        }
        awaitClose { listener.remove() }
    }


    fun getContextTags(universeId: String): Flow<List<ContextTag>> = callbackFlow {
        val listener = tagsRef(universeId)
            .orderBy("order")
            .addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                val tags = snap?.documents?.mapNotNull { doc -> doc.toObject<ContextTag>() } ?: emptyList()
                trySend(tags)
            }
        awaitClose { listener.remove() }
    }


    fun getUserProgress(universeId: String): Flow<UserProgress> = callbackFlow {
        val uid = requireAuth()
        val listener = progressRef(universeId).addSnapshotListener { snap, err ->
            if (err != null) { close(err); return@addSnapshotListener }
            val progress = snap?.toObject<UserProgress>() ?: UserProgress(userId = uid, universeId = universeId)
            trySend(progress)
        }
        awaitClose { listener.remove() }
    }


    /** Finds if this media belongs to any universe tree. Checks both TMDB and AniList IDs. */
    fun findUniverseForMedia(tmdbId: Int, anilistId: Int?): Flow<Universe?> = callbackFlow {
        val listener = firestore.collection("universes")
            .addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                
                val universes = snap?.documents?.mapNotNull { doc -> 
                    doc.toObject<Universe>()?.copy(id = doc.id)
                } ?: emptyList()
                
                this@callbackFlow.launch {
                    for (u in universes) {
                        // Check nodes for TMDB ID match
                        val tmdbNodesSnap = nodesRef(u.id).whereEqualTo("tmdb_id", tmdbId).get().await()
                        if (!tmdbNodesSnap.isEmpty) {
                            trySend(u)
                            return@launch
                        }
                        
                        // Check nodes for AniList ID match if provided
                        if (anilistId != null && anilistId > 0) {
                            val anilistNodesSnap = nodesRef(u.id).whereEqualTo("anilist_id", anilistId).get().await()
                            if (!anilistNodesSnap.isEmpty) {
                                trySend(u)
                                return@launch
                            }
                        }
                    }
                    trySend(null)
                }
            }
        awaitClose { listener.remove() }
    }

    suspend fun setNodeCompletion(
        universeId: String,
        nodeId: String,
        completed: Boolean
    ): Result<Unit> = runCatching {
        val uid = requireAuth()
        val fieldUpdate = if (completed) {
            FieldValue.arrayUnion(nodeId)
        } else {
            FieldValue.arrayRemove(nodeId)
        }

        progressRef(universeId).set(
            mapOf(
                "user_id" to uid,
                "universe_id" to universeId,
                "completed_node_ids" to fieldUpdate,
                "last_updated" to FieldValue.serverTimestamp()
            ),
            SetOptions.merge()
        ).await()
    }


    suspend fun setActiveRoute(universeId: String, routeTag: String): Result<Unit> =
        runCatching {
            progressRef(universeId).set(
                mapOf("active_route" to routeTag),
                SetOptions.merge()
            ).await()
        }


    suspend fun setSpoilerShieldEnabled(universeId: String, enabled: Boolean): Result<Unit> =
        runCatching {
            progressRef(universeId).set(
                mapOf("spoiler_shield_enabled" to enabled),
                SetOptions.merge()
            ).await()
        }

    // ─── Gemini-Generated Universe Publishing ─────────────────────────────────

    /**
     * Persists a Gemini-generated watch-order DAG to Firestore as a new
     * universe, immediately after generation succeeds.
     */
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
                "name" to universeName,
                "description" to "AI-generated watch order via Gemini.",
                "posterUrl" to coverUrl,
                "total_nodes" to nodes.size,
                "available_routes" to listOf("ALL", "CANON", "ESSENTIAL")
            ),
            SetOptions.merge()
        )

        // Default tags for filtering
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

    suspend fun clearGeneratedUniverse(universeId: String): Result<Unit> = runCatching {
        val nodeDocs = nodesRef(universeId).get().await()
        val edgeDocs = edgesRef(universeId).get().await()

        val batch = firestore.batch()
        nodeDocs.documents.forEach { batch.delete(it.reference) }
        edgeDocs.documents.forEach { batch.delete(it.reference) }
        batch.commit().await()
    }
}
