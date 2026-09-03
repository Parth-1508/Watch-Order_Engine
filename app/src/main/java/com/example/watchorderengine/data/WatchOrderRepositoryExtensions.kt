package com.example.watchorderengine.data

import com.example.watchorderengine.data.model.Edge
import com.example.watchorderengine.data.model.MediaNode

/**
 * Saves a hand-built graph (from GraphBuilderScreen) as a private personal
 * universe — same underlying storage as an AI-generated one, just skipping
 * Gemini and the Community public-feed path entirely.
 */
suspend fun WatchOrderRepository.publishCustomUniverse(
    universeId: String,
    universeName: String,
    coverUrl: String,
    nodes: List<MediaNode>,
    edges: List<Edge>
): Result<Unit> = runCatching {
    check(nodes.isNotEmpty()) { "Cannot publish an empty custom timeline." }

    publishGeneratedUniverse(
        universeId   = universeId,
        universeName = universeName,
        coverUrl     = coverUrl,
        nodes        = nodes,
        edges        = edges
    ).getOrThrow()
}
