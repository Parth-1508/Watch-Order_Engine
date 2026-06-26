package com.example.watchorderengine.data.graph

import com.example.watchorderengine.data.model.Edge
import com.example.watchorderengine.data.model.MediaNode
import java.util.LinkedList

/**
 * Stateless, pure graph engine — zero Android or Firestore dependencies.
 * Every function is deterministic: same inputs → same outputs, every time.
 * This makes it trivially unit-testable with plain JUnit5.
 *
 * Algorithms implemented:
 *   1. Kahn's BFS Topological Sort  → valid watch order + level assignment
 *   2. Column Assignment             → visual branching positions
 *   3. Route Filter                  → tag-based node pruning
 *   4. Spoiler Shield                → frontier-based blur computation
 */
object GraphEngine {

    // ─── Public Result Types ──────────────────────────────────────────────────

    /**
     * The complete spatial layout of the DAG, ready for the ViewModel
     * to build [TimelineRow] objects from.
     *
     * @param sortedIds   Node IDs in topological order (valid watch sequence).
     * @param levelMap    nodeId → row index (0 = first row, top of timeline).
     * @param columnMap   nodeId → column index (0 = main, 1+ = branches).
     * @param maxColumns  Total parallel columns in the widest row.
     */
    data class GraphLayout(
        val sortedIds: List<String>,
        val levelMap: Map<String, Int>,
        val columnMap: Map<String, Int>,
        val maxColumns: Int,
        val isCycleDetected: Boolean = false
    )

    data class OutgoingConnection(
        val fromColumn: Int,
        val toColumn: Int,
        val isFromNodeCompleted: Boolean
    )

    // ─── Algorithm 1: Kahn's BFS Topological Sort + Level Assignment ─────────

    /**
     * Produces a topological ordering of the DAG nodes and assigns each node
     * a (level, column) position for 2D timeline rendering.
     *
     * TIME COMPLEXITY: O(V + E)
     * WHERE:  V = number of nodes,  E = number of edges
     *
     * The level of a node = length of the longest path from any root to that node.
     * This ensures that prerequisites always appear at a lower (earlier) level
     * than the nodes that depend on them.
     *
     * @throws IllegalStateException if a cycle is detected (invalid DAG).
     */
    fun computeLayout(nodes: List<MediaNode>, edges: List<Edge>): GraphLayout {
        if (nodes.isEmpty()) return GraphLayout(emptyList(), emptyMap(), emptyMap(), 0)

        val allIds = nodes.map { it.id }.toSet()

        // ── Build adjacency maps ──────────────────────────────────────────────
        // outEdges: nodeId → list of successor nodeIds
        // inEdges:  nodeId → list of predecessor nodeIds
        val outEdges = mutableMapOf<String, MutableList<String>>()
        val inEdges = mutableMapOf<String, MutableList<String>>()

        for (edge in edges) {
            // Skip dangling edges that reference nodes not in our filtered set
            if (edge.from_node_id !in allIds || edge.to_node_id !in allIds) continue
            outEdges.getOrPut(edge.from_node_id) { mutableListOf() }.add(edge.to_node_id)
            inEdges.getOrPut(edge.to_node_id) { mutableListOf() }.add(edge.from_node_id)
        }

        // ── Kahn's Algorithm ─────────────────────────────────────────────────
        // inDegree[id] = number of unprocessed predecessors for that node.
        // Starts at the total number of predecessors; decremented as they're processed.
        val inDegree = allIds.associateWith { inEdges[it]?.size ?: 0 }.toMutableMap()
        val levelMap = mutableMapOf<String, Int>()

        // Seed the BFS queue with all root nodes (no predecessors)
        val queue: LinkedList<String> = LinkedList(allIds.filter { inDegree[it] == 0 })
        queue.forEach { levelMap[it] = 0 }

        val sortedIds = mutableListOf<String>()
        var cycleDetected = false

        while (sortedIds.size < allIds.size) {
            if (queue.isEmpty()) {
                // ── CYCLE RECOVERY ───────────────────────────────────────────
                // If the queue is empty but we haven't processed all nodes, a 
                // cycle exists. To prevent a hard crash, we'll pick the best 
                // candidate node to "break" the cycle.
                cycleDetected = true
                val remainingIds = allIds - sortedIds.toSet()
                
                // Candidate: node with the lowest current inDegree among remaining nodes.
                // If there's a tie, use chrono_order (earlier first).
                val candidate = remainingIds.minByOrNull { id ->
                    val degree = inDegree[id] ?: 0
                    val chrono = nodes.find { it.id == id }?.chrono_order ?: 0f
                    // Pack degree and chrono into a comparable pair
                    degree.toFloat() * 1000000f + chrono
                } ?: break
                
                // Force-clear inDegree and ensure it has a level
                inDegree[candidate] = 0
                if (candidate !in levelMap) {
                    levelMap[candidate] = (levelMap.values.maxOrNull() ?: -1) + 1
                }
                queue.add(candidate)
            }

            // Sort queue by chrono_order to ensure deterministic vertical levels
            queue.sortBy { nodes.find { n -> n.id == it }?.chrono_order ?: 0f }

            val current = queue.poll()!!
            sortedIds.add(current)
            val currentLevel = levelMap[current] ?: 0

            // For each successor of the current node:
            outEdges[current]?.forEach { next ->
                // The successor's level is at least (current level + 1).
                // We take the max to handle diamond-merge patterns correctly.
                levelMap[next] = maxOf(levelMap[next] ?: 0, currentLevel + 1)
                
                val currentDegree = inDegree[next] ?: 1
                if (currentDegree > 0) {
                    inDegree[next] = currentDegree - 1
                }

                // Once all predecessors of 'next' are processed, enqueue it
                if (inDegree[next] == 0 && next !in sortedIds && next !in queue) {
                    queue.add(next)
                }
            }
        }

        // ── Column Assignment ─────────────────────────────────────────────────
        val columnMap = assignColumns(sortedIds, outEdges, inEdges)
        val maxColumns = (columnMap.values.maxOrNull() ?: 0) + 1

        return GraphLayout(sortedIds, levelMap, columnMap, maxColumns, cycleDetected)
    }

    /**
     * Assigns a visual column to each node, creating the left-right branch layout.
     *
     * STRATEGY:
     *   Root nodes → column 0.
     *   One successor → inherits parent's column (straight line, no branch).
     *   Multiple successors → first inherits parent's column; each additional
     *                         successor gets the next available rightmost column.
     *   Merge nodes (multiple parents) → assigned the minimum parent column
     *                                    (snaps back to the "leftmost" branch).
     *
     * Processes nodes in topological order, so parent columns are always
     * assigned before children need them.
     */
    private fun assignColumns(
        sortedIds: List<String>,
        outEdges: Map<String, List<String>>,
        inEdges: Map<String, List<String>>
    ): Map<String, Int> {
        val columnMap = mutableMapOf<String, Int>()
        val activeColumnsAtLevel = mutableMapOf<Int, MutableSet<Int>>()
        var nextGlobalColumn = 0

        for (nodeId in sortedIds) {
            val parents = inEdges[nodeId]
            
            if (nodeId !in columnMap) {
                if (parents.isNullOrEmpty()) {
                    columnMap[nodeId] = nextGlobalColumn++
                } else {
                    // Try to inherit the most common parent column
                    val parentCols = parents.mapNotNull { columnMap[it] }
                    columnMap[nodeId] = parentCols.minOrNull() ?: nextGlobalColumn++
                }
            }

            val currentCol = columnMap[nodeId]!!
            val successors = outEdges[nodeId] ?: emptyList()
            
            successors.forEachIndexed { index, successor ->
                if (successor !in columnMap) {
                    columnMap[successor] = if (index == 0) {
                        currentCol // Continue main branch
                    } else {
                        nextGlobalColumn++ // Start new branch
                    }
                }
            }
        }

        return columnMap
    }

    // ─── Algorithm 2: Route Filter ────────────────────────────────────────────

    /**
     * Prunes the node list to only entries matching the given [routeTag].
     *
     * A node passes the filter if:
     *   - routeTag == ROUTE_ALL  (no filtering — return everything), OR
     *   - routeTag is present in the node's [MediaNode.tags] list.
     *
     * The caller is responsible for also filtering the edge list to remove
     * edges that reference pruned nodes.
     *
     * @param nodes    The full (unfiltered) node list.
     * @param routeTag The tag key to filter on. Use [ROUTE_ALL] for no filter.
     * @return Filtered list, preserving original ordering.
     */
    fun applyRouteFilter(nodes: List<MediaNode>, routeTag: String): List<MediaNode> {
        if (routeTag == ROUTE_ALL) return nodes
        return nodes.filter { node -> routeTag in node.tags }
    }

    // ─── Algorithm 3: Spoiler Shield ─────────────────────────────────────────

    /**
     * Computes which nodes should have their titles BLURRED by the spoiler shield.
     *
     * PHILOSOPHY: The user can always see that a node EXISTS (it's in their timeline),
     * but its title and metadata are redacted until they reach it. This preserves
     * the sense of discovery — you know there's "something next", but not what.
     *
     * REVEAL RULE: A node is "revealed" (title shown) if ANY of these are true:
     *   1. The user has completed it.
     *   2. It is a root node (has no prerequisites in the current route).
     *   3. At least one of its direct predecessors has been completed.
     *
     * We iterate in topological order (sortedNodes) so that "revealed" propagates
     * forward — a node revealed by rule 3 can then reveal its own successors.
     *
     * @param sortedNodes  Nodes in topological order.
     * @param edges        All (filtered) edges in the graph.
     * @param completedIds The effective set of completed node IDs
     *                     (Firestore state merged with optimistic overrides).
     * @return A set of node IDs whose titles should be blurred.
     */
    fun computeSpoilerShield(
        sortedNodes: List<MediaNode>,
        edges: List<Edge>,
        completedIds: Set<String>
    ): Set<String> {
        val allVisibleIds = sortedNodes.map { it.id }.toSet()

        // Build predecessor map: nodeId → set of direct predecessor IDs
        // Only consider edges where both nodes are in the current visible set.
        val predecessors = mutableMapOf<String, MutableSet<String>>()
        for (edge in edges) {
            if (edge.from_node_id in allVisibleIds && edge.to_node_id in allVisibleIds) {
                predecessors.getOrPut(edge.to_node_id) { mutableSetOf() }.add(edge.from_node_id)
            }
        }

        val revealedIds = mutableSetOf<String>()

        for (node in sortedNodes) {
            val preds = predecessors[node.id]
            val isRevealed = when {
                node.id in completedIds -> true        // Rule 1: already watched
                preds.isNullOrEmpty() -> true          // Rule 2: root node (in this route)
                // Rule 3: at least one predecessor is completed
                preds.any { it in completedIds } -> true
                else -> false
            }
            if (isRevealed) revealedIds.add(node.id)
        }

        // Everything that is NOT revealed is blurred
        return allVisibleIds - revealedIds
    }

    /**
     * Computes the outgoing connector lines for each level, used by the UI
     * to draw the bezier lines between timeline rows.
     *
     * @param displayNodeMap nodeId → DisplayNode (for column and completion lookup)
     * @param edges          The filtered edge list
     * @param levelMap       nodeId → row level
     * @return Map of level → list of [OutgoingConnection]s starting at that level
     */
    fun computeConnections(
        displayNodeMap: Map<String, Pair<Int, Boolean>>, // nodeId → (column, isCompleted)
        edges: List<Edge>,
        levelMap: Map<String, Int>
    ): Map<Int, List<OutgoingConnection>> {
        val result = mutableMapOf<Int, MutableList<OutgoingConnection>>()

        for (edge in edges) {
            val (fromCol, fromCompleted) = displayNodeMap[edge.from_node_id] ?: continue
            val (toCol, _) = displayNodeMap[edge.to_node_id] ?: continue
            val fromLevel = levelMap[edge.from_node_id] ?: continue

            result.getOrPut(fromLevel) { mutableListOf() }.add(
                OutgoingConnection(fromCol, toCol, fromCompleted)
            )
        }
        return result
    }

    // ─── Constants ────────────────────────────────────────────────────────────
    const val ROUTE_ALL = "ALL"
}