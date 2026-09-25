package io.kontour.ui.components.display

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified

/*
 * Where a history's branches run: the lanes of a `BranchTimeline`, laid out the
 * way `git log --graph` lays them out, one row at a time from the newest commit
 * down. Pure, so the whole graph is decided before anything draws, and a lazy
 * list can draw any row of it on its own.
 */

/**
 * A lane's colour: its turn in the palette, unless the caller named one. Kept
 * as a turn rather than a colour so the layout can be worked out outside
 * composition, where the theme's palette cannot be read.
 */
internal class LaneInk(val turn: Int, val explicit: Color = Color.Unspecified) {
    fun resolve(palette: List<Color>): Color =
        if (explicit.isSpecified || palette.isEmpty()) explicit else palette[turn % palette.size]

    override fun equals(other: Any?): Boolean = other is LaneInk && other.turn == turn && other.explicit == explicit
    override fun hashCode(): Int = 31 * turn + explicit.hashCode()
    override fun toString(): String = if (explicit.isSpecified) "LaneInk($explicit)" else "LaneInk(#$turn)"
}

/** A line through one row, from lane [from] at one end to lane [to] at the other, in [ink]. */
internal data class LaneEdge(val from: Int, val to: Int, val ink: LaneInk)

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
    val ink: LaneInk,
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
 *
 * [explicit] recolours a commit's lane from that commit down, where it is not
 * [Color.Unspecified].
 */
internal fun layOutLanes(
    ids: List<Any>,
    parents: List<List<Any>>,
    explicit: List<Color> = emptyList(),
): LaneGraph {
    class Lane(val expects: Any, val ink: LaneInk)

    val lanes = ArrayList<Lane?>()
    var turns = 0
    fun nextInk() = LaneInk(turns++)
    fun freeSlot(from: Int = 0): Int {
        for (j in from until lanes.size) if (lanes[j] == null) return j
        while (lanes.size < from) lanes += null
        lanes += null
        return lanes.lastIndex
    }

    val rows = ArrayList<LaneRow>(ids.size)
    var width = 0
    ids.forEachIndexed { index, id ->
        val waiting = lanes.indices.filter { lanes[it]?.expects == id }
        val given = explicit.getOrNull(index)?.takeIf { it.isSpecified }
        val node: Int
        val ink: LaneInk
        if (waiting.isEmpty()) {
            node = freeSlot()
            ink = if (given != null) LaneInk(-1, given) else nextInk()
        } else {
            node = waiting.first()
            ink = if (given != null) LaneInk(-1, given) else lanes[node]!!.ink
        }
        val incoming = waiting.map { LaneEdge(it, node, lanes[it]!!.ink) }
        val passing = lanes.indices
            .filter { lanes[it] != null && it !in waiting }
            .map { LaneEdge(it, it, lanes[it]!!.ink) }
        waiting.forEach { lanes[it] = null }

        val outgoing = ArrayList<LaneEdge>()
        parents.getOrElse(index) { emptyList() }.distinct().forEachIndexed { turn, parent ->
            if (turn == 0) {
                while (lanes.size <= node) lanes += null
                lanes[node] = Lane(parent, ink)
                outgoing += LaneEdge(node, node, ink)
            } else {
                val existing = lanes.indices.firstOrNull { it != node && lanes[it]?.expects == parent }
                if (existing != null) {
                    outgoing += LaneEdge(node, existing, lanes[existing]!!.ink)
                } else {
                    val slot = freeSlot(from = node + 1)
                    val branch = nextInk()
                    lanes[slot] = Lane(parent, branch)
                    outgoing += LaneEdge(node, slot, branch)
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
            passing[i].replaceAll { if (it.to == edge.from) it.copy(to = edge.to) else it }
            outgoing[i].replaceAll { if (it.to == edge.from) it.copy(to = edge.to) else it }
            incoming[i + 1].replaceAll { if (it == edge) it.copy(from = edge.to) else it }
        }
    }
    return LaneGraph(
        rows = rows.mapIndexed { i, row -> LaneRow(row.node, row.ink, incoming[i], passing[i], outgoing[i]) },
        width = width,
    )
}
