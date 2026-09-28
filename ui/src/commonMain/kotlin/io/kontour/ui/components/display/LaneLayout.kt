package io.kontour.ui.components.display

/*
 * Where a history's branches run: the lanes of a `BranchTimeline`, laid out the
 * way `git log --graph` lays them out, one row at a time from the newest commit
 * down. Pure, so the whole graph is decided before anything draws, and a lazy
 * list can draw any row of it on its own.
 */

/**
 * A line through one row, from lane [from] at one end to lane [to] at the other.
 *
 * @param ink The line's colour, as a turn in the palette — a turn rather than a
 *   colour so the layout can be worked out outside composition.
 * @param owners The commits, by row, whose line down to a parent this is: the
 *   one that opened the lane first. A lane two children are both waiting on the
 *   same parent through has both, from the row where the second joined it.
 * @param parent The row of the parent it is heading for, or -1 for a parent
 *   the list does not reach.
 */
internal data class LaneEdge(
    val from: Int,
    val to: Int,
    val ink: Int,
    val owners: List<Int> = emptyList(),
    val parent: Int = -1,
)

/**
 * One row of the graph.
 *
 * @param node The lane the row's commit sits in.
 * @param ink The commit's colour: its lane's.
 * @param incoming Lanes arriving at the commit from the row above — the one it
 *   continues, and any others that end here because they were waiting for it.
 * @param passing Lanes running straight through the row, past the commit.
 * @param outgoing Lanes leaving the commit for the row below — its first parent
 *   in its own lane, and any others to where they are waited for.
 */
internal class LaneRow(
    val node: Int,
    val ink: Int,
    val incoming: List<LaneEdge>,
    val passing: List<LaneEdge>,
    val outgoing: List<LaneEdge>,
)

/** The whole graph: a row per commit, and the most lanes any row uses. */
internal class LaneGraph(val rows: List<LaneRow>, val width: Int)

/**
 * Lays out the lanes for commits [ids], newest first, whose parents are
 * [parents] — the first of them the one the commit continues.
 *
 * Row by row:
 * - A commit takes the leftmost lane already waiting for it. With none waiting,
 *   it is a new tip, in the leftmost free lane, in the palette's next colour.
 * - Any other lanes waiting for it end at it: branches that forked from here.
 * - Its first parent continues its lane. If another lane is already waiting for
 *   that parent too, both run on and meet at the parent, as git draws it.
 * - Each other parent — a merge — joins the lane waiting for it, or opens a new
 *   lane to the right of the commit, in the next colour.
 * - A commit with no parents ends its lane, and a parent that never appears
 *   keeps its lane running to the bottom.
 */
internal fun layOutLanes(ids: List<Any>, parents: List<List<Any>>): LaneGraph {
    class Lane(val expects: Any, val ink: Int, val owners: List<Int>, val parent: Int)

    val rowOf = HashMap<Any, Int>(ids.size).apply { ids.forEachIndexed { row, id -> getOrPut(id) { row } } }
    val lanes = ArrayList<Lane?>()
    var turns = 0
    fun freeSlot(from: Int = 0): Int {
        for (j in from until lanes.size) if (lanes[j] == null) return j
        while (lanes.size < from) lanes += null
        lanes += null
        return lanes.lastIndex
    }
    fun Lane.edge(from: Int, to: Int) = LaneEdge(from, to, ink, owners, parent)

    val rows = ArrayList<LaneRow>(ids.size)
    var width = 0
    ids.forEachIndexed { index, id ->
        val waiting = lanes.indices.filter { lanes[it]?.expects == id }
        val node = if (waiting.isEmpty()) freeSlot() else waiting.first()
        val ink = if (waiting.isEmpty()) turns++ else lanes[node]!!.ink
        val incoming = waiting.map { lanes[it]!!.edge(it, node) }
        val passing = lanes.indices
            .filter { lanes[it] != null && it !in waiting }
            .map { lanes[it]!!.edge(it, it) }
        waiting.forEach { lanes[it] = null }

        val outgoing = ArrayList<LaneEdge>()
        parents.getOrElse(index) { emptyList() }.distinct().forEachIndexed { turn, parent ->
            val parentRow = rowOf[parent]?.takeIf { it > index } ?: -1
            if (turn == 0) {
                while (lanes.size <= node) lanes += null
                val lane = Lane(parent, ink, listOf(index), parentRow)
                lanes[node] = lane
                outgoing += lane.edge(node, node)
            } else {
                val existing = lanes.indices.firstOrNull { it != node && lanes[it]?.expects == parent }
                if (existing != null) {
                    // Joining a lane already on its way to this parent: the bend
                    // across is this commit's alone, and from here down the lane
                    // is both commits' line to it.
                    val joined = lanes[existing]!!
                    outgoing += LaneEdge(node, existing, joined.ink, listOf(index), parentRow)
                    lanes[existing] = Lane(parent, joined.ink, joined.owners + index, joined.parent)
                } else {
                    val slot = freeSlot(from = node + 1)
                    val lane = Lane(parent, turns++, listOf(index), parentRow)
                    lanes[slot] = lane
                    outgoing += lane.edge(node, slot)
                }
            }
        }

        val used = maxOf(
            node,
            incoming.maxOfOrNull { it.from } ?: 0,
            passing.maxOfOrNull { it.from } ?: 0,
            outgoing.maxOfOrNull { it.to } ?: 0,
        ) + 1
        width = maxOf(width, used)
        while (lanes.isNotEmpty() && lanes.last() == null) lanes.removeAt(lanes.lastIndex)
        rows += LaneRow(node, ink, incoming, passing, outgoing)
    }
    return LaneGraph(rows, width)
}

/**
 * The rows [reached] descends from, itself included — the part of a history a
 * `BranchProgress` has got to — or null when [reached] is not in the list.
 */
internal fun reachedRows(ids: List<Any>, parents: List<List<Any>>, reached: Any): BooleanArray? {
    val rowOf = HashMap<Any, Int>(ids.size).apply { ids.forEachIndexed { row, id -> getOrPut(id) { row } } }
    val start = rowOf[reached] ?: return null
    val seen = BooleanArray(ids.size)
    val queue = ArrayDeque<Int>().apply { add(start) }
    seen[start] = true
    while (queue.isNotEmpty()) {
        val row = queue.removeFirst()
        parents.getOrElse(row) { emptyList() }.forEach { parent ->
            val next = rowOf[parent] ?: return@forEach
            if (!seen[next]) {
                seen[next] = true
                queue.add(next)
            }
        }
    }
    return seen
}

/**
 * The same graph, with every lane that closes into a commit bending toward it in
 * the row above rather than in the commit's own row.
 *
 * A commit sits near the top of its row — on its label's first line — so the
 * stretch of row above it is short, and lanes converging on it there had to
 * swing across in a few dp and came in almost flat. The row above has its whole
 * height to do it in. So each converging lane is moved to end, at the bottom of
 * the row above, where the commit is, and runs straight down into it: the bend
 * goes from the lane's own line (or its commit) above, across that whole row.
 */
internal fun LaneGraph.bentEarly(): LaneGraph {
    val incoming = rows.map { it.incoming.toMutableList() }
    val passing = rows.map { it.passing.toMutableList() }
    val outgoing = rows.map { it.outgoing.toMutableList() }
    for (i in 0 until rows.lastIndex) {
        for (edge in rows[i + 1].incoming) {
            if (edge.from == edge.to) continue
            passing[i].update { if (it.to == edge.from) it.copy(to = edge.to) else it }
            outgoing[i].update { if (it.to == edge.from) it.copy(to = edge.to) else it }
            incoming[i + 1].update { if (it == edge) it.copy(from = edge.to) else it }
        }
    }
    return LaneGraph(
        rows = rows.mapIndexed { i, row -> LaneRow(row.node, row.ink, incoming[i], passing[i], outgoing[i]) },
        width = width,
    )
}

/** Each element replaced by [transform] of it — `replaceAll`, which is Java's and not in common code. */
private inline fun <T> MutableList<T>.update(transform: (T) -> T) {
    for (index in indices) this[index] = transform(this[index])
}
