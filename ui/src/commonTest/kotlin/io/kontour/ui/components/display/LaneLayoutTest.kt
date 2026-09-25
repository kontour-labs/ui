package io.kontour.ui.components.display

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Where a history's branches run, row by row, newest commit first.
 *
 * Each case is a small history written the way `git log` prints it — a commit
 * and its parents — and the graph is read back as the lane each commit sits in
 * and the lines into, past and out of it.
 */
class LaneLayoutTest {

    private fun graph(vararg commits: Pair<String, List<String>>, explicit: List<Color> = emptyList()) =
        layOutLanes(commits.map { it.first }, commits.map { it.second }, explicit)

    private fun LaneRow.lines(edges: List<LaneEdge>) = edges.map { it.from to it.to }

    @Test
    fun aStraightHistoryIsOneLane() {
        val g = graph("c" to listOf("b"), "b" to listOf("a"), "a" to emptyList())
        assertEquals(1, g.width)
        assertEquals(listOf(0, 0, 0), g.rows.map { it.node })
        assertEquals(emptyList(), g.rows[0].let { it.lines(it.incoming) }, "the newest commit is a tip")
        assertEquals(listOf(0 to 0), g.rows[1].let { it.lines(it.incoming) })
        assertEquals(emptyList(), g.rows[2].let { it.lines(it.outgoing) }, "the root ends its lane")
        assertTrue(g.rows.all { it.ink == g.rows[0].ink }, "one lane, one colour")
    }

    /**
     *     m   merge feature into main
     *     |\
     *     | f  feature work
     *     b |  main work
     *     |/
     *     a   where feature forked
     */
    @Test
    fun aMergedFeatureBranchOpensALaneToTheRightAndClosesIntoItsFork() {
        val g = graph(
            "m" to listOf("b", "f"),
            "b" to listOf("a"),
            "f" to listOf("a"),
            "a" to emptyList(),
        )
        assertEquals(2, g.width)
        val (merge, main, feature, fork) = g.rows
        assertEquals(listOf(0 to 0, 0 to 1), merge.lines(merge.outgoing), "a merge leaves down its own lane and out to a new one")
        assertTrue(merge.outgoing[1].ink != merge.ink, "the merged branch is a different colour")
        assertEquals(0, main.node)
        assertEquals(listOf(1 to 1), main.lines(main.passing), "the feature lane passes the main commit")
        assertEquals(1, feature.node)
        assertEquals(listOf(0 to 0), feature.lines(feature.passing))
        assertEquals(merge.outgoing[1].ink, feature.ink, "the feature commit is its lane's colour")
        assertEquals(0, fork.node)
        assertEquals(listOf(0 to 0, 1 to 0), fork.lines(fork.incoming), "both lanes meet at the fork")
    }

    /** Two tips on one parent run side by side and meet at it. */
    @Test
    fun twoTipsOnOneParentMeetAtIt() {
        val g = graph("x" to listOf("p"), "y" to listOf("p"), "p" to emptyList())
        assertEquals(listOf(0, 1, 0), g.rows.map { it.node })
        assertTrue(g.rows[0].ink != g.rows[1].ink, "two tips, two colours")
        assertEquals(listOf(0 to 0, 1 to 0), g.rows[2].let { it.lines(it.incoming) })
    }

    @Test
    fun aNamedColourRecoloursItsLaneFromThereDown() {
        val release = Color(0xFFAA00AA)
        val g = graph(
            "c" to listOf("b"),
            "b" to listOf("a"),
            "a" to emptyList(),
            explicit = listOf(Color.Unspecified, release, Color.Unspecified),
        )
        assertTrue(g.rows[0].ink.resolve(Palette) != release)
        assertEquals(release, g.rows[1].ink.resolve(Palette))
        assertEquals(release, g.rows[1].outgoing.single().ink.resolve(Palette))
        assertEquals(release, g.rows[2].ink.resolve(Palette), "and the lane stays that colour below")
        assertEquals(g.rows[0].ink.resolve(Palette), g.rows[1].incoming.single().ink.resolve(Palette), "the line in is still the one above's")
    }

    /** A history cut short: the parent is not in the list, so its lane runs on to the bottom. */
    @Test
    fun aParentThatNeverAppearsRunsToTheBottom() {
        val g = graph("m" to listOf("b", "old"), "b" to listOf("a"), "a" to emptyList())
        assertEquals(listOf(1 to 1), g.rows[1].let { it.lines(it.passing) })
        assertEquals(listOf(1 to 1), g.rows[2].let { it.lines(it.passing) }, "still waiting at the last row")
    }

    /** A lane that closed is the first one a new tip takes. */
    @Test
    fun aClosedLaneIsReused() {
        val g = graph(
            "m" to listOf("b", "f"),
            "f" to listOf("b"),
            "b" to listOf("a"),
            "t" to listOf("a"),
            "a" to emptyList(),
        )
        // f's lane (1) closes into b; the tip t that follows takes lane 1 again
        // rather than opening a third.
        assertEquals(2, g.width)
        assertEquals(1, g.rows[3].node)
    }

    /** Three parents: each merged branch gets its own lane, to the right, in order. */
    @Test
    fun anOctopusMergeOpensALanePerBranch() {
        val g = graph(
            "o" to listOf("a", "b", "c"),
            "c" to listOf("r"),
            "b" to listOf("r"),
            "a" to listOf("r"),
            "r" to emptyList(),
        )
        assertEquals(listOf(0 to 0, 0 to 1, 0 to 2), g.rows[0].let { it.lines(it.outgoing) })
        assertEquals(3, g.width)
        assertEquals(listOf(0 to 0, 1 to 0, 2 to 0), g.rows[4].let { it.lines(it.incoming) })
    }

    /** A merge whose branch another lane already waits for joins that lane rather than opening one. */
    @Test
    fun aMergeJoinsALaneAlreadyWaiting() {
        val g = graph(
            "x" to listOf("f"),
            "m" to listOf("b", "f"),
            "f" to listOf("a"),
            "b" to listOf("a"),
            "a" to emptyList(),
        )
        // x is a tip on f in lane 0; m starts lane 1 and merges f, which lane 0
        // is already waiting for.
        assertEquals(1, g.rows[1].node)
        assertEquals(listOf(1 to 1, 1 to 0), g.rows[1].let { it.lines(it.outgoing) })
        assertEquals(2, g.width)
    }

    /**
     * Lanes that close into a commit bend toward it across the row above, and
     * arrive at it straight down.
     */
    @Test
    fun aClosingLaneBendsInTheRowAbove() {
        val g = graph(
            "m" to listOf("b", "f"),
            "b" to listOf("a"),
            "f" to listOf("a"),
            "a" to emptyList(),
        ).bentEarly()
        val feature = g.rows[2]
        val fork = g.rows[3]
        assertEquals(listOf(1 to 0), feature.lines(feature.outgoing), "the feature commit's line leaves for the fork")
        assertEquals(listOf(0 to 0), feature.lines(feature.passing))
        assertTrue(fork.incoming.all { it.from == it.to }, "every line arrives at the fork straight: ${fork.incoming}")
    }

    /**
     * The invariant a row-by-row drawing rests on: the lanes leaving the bottom
     * of one row are the lanes arriving at the top of the next, so the lines are
     * unbroken across every seam — before and after the bends move up.
     */
    @Test
    fun everyLaneLeavingARowArrivesAtTheNext() {
        val histories = listOf(
            listOf("m" to listOf("b", "f"), "b" to listOf("a"), "f" to listOf("a"), "a" to emptyList()),
            listOf("x" to listOf("p"), "y" to listOf("p"), "p" to emptyList()),
            listOf(
                "o" to listOf("a", "b", "c"), "c" to listOf("r"), "b" to listOf("r"), "a" to listOf("r"),
                "r" to emptyList(),
            ),
            listOf(
                "r2" to listOf("r1"), "m2" to listOf("c4", "f2"), "f2" to listOf("f1"), "c4" to listOf("c3"),
                "r1" to listOf("c3"), "f1" to listOf("c3"), "c3" to listOf("c2"), "c2" to emptyList(),
            ),
            listOf(
                "m" to listOf("b", "f"), "f" to listOf("b"), "b" to listOf("a"), "t" to listOf("a"),
                "a" to emptyList(),
            ),
        )
        for (history in histories) {
            val raw = graph(*history.toTypedArray())
            for ((name, g) in listOf("as laid out" to raw, "bent early" to raw.bentEarly())) {
                g.rows.zipWithNext().forEachIndexed { i, (above, below) ->
                    val leaving = (above.passing + above.outgoing).map { it.to }.toSortedSet()
                    val arriving = (below.passing + below.incoming).map { it.from }.toSortedSet()
                    assertEquals(
                        leaving, arriving,
                        "${history.map { it.first }} $name: lanes leaving row $i are not the lanes arriving at row ${i + 1}",
                    )
                }
            }
        }
    }

    private companion object {
        val Palette = listOf(Color.Red, Color.Green, Color.Blue)
    }
}
