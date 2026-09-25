package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import io.kontour.ui.components.datetime.DateRangePicker
import java.awt.image.BufferedImage
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * While a range is dragged, the band flows into the days about to be chosen and
 * out of the ones about to be let go, and always touches the handle.
 *
 * Reported: *"if i drag it across, then i should see it starting to fill the date
 * that i'm dragging from. similarly, if i drag down, then it should start filling
 * the remainder of the week that it's currently on, plus the start of the next
 * week up to the date we're dragging to. basically … the accent colour should
 * always be touching the top and/or the start of the handle we're dragging."*
 * The cap leaned toward the finger and left a gap behind it; the band only ever
 * changed a whole day at a time.
 *
 * August 2026 with Monday first: five blanks, six rows, and a 700px scene makes
 * every cell 100px square. Day *d* is cell `d + 4`.
 */
class DateRangeReachTest {

    /** Leaning toward the 13th from the 12th, the part of the 12th the cap has left is band. */
    @Test
    fun leaningAcrossFillsTheDayBeingLeft() {
        held(anchor = 10, head = 12, lean = Offset(0.45f, 0f)) { image, grid ->
            val left = grid.cell(12) - Offset(Cell * 0.45f, 0f)
            assertTrue(image.isRangeTint(left), "the start of the 12th, behind the leaning cap, was not band")
        }
    }

    /**
     * Leaning down from the 12th toward the 19th, the rest of the week and the start
     * of the next both start to fill — and nothing past the day being aimed at.
     */
    @Test
    fun leaningDownFillsTheRestOfTheWeekAndTheStartOfTheNext() {
        held(anchor = 10, head = 12, lean = Offset(0f, 0.45f)) { image, grid ->
            assertTrue(image.isRangeTint(grid.below(13)), "the 13th, the rest of the week, was not filling")
            assertTrue(image.isRangeTint(grid.below(17)), "the 17th, the start of the next week, was not filling")
            assertTrue(!image.isRangeTint(grid.below(16)), "the end of the week filled already, half a row from it")
            assertTrue(!image.isRangeTint(grid.below(20)), "the 20th, past the day being aimed at, filled")
        }
    }

    /**
     * Wherever the finger leans within the head's day, the band touches the cap: at
     * its start, or at its top. The cap's edges are where its cell's are, moved by
     * the pull — the 12th is the range's end, so its fill runs to its cell's start
     * edge and sits a 1dp inset below its top — and the pixels just past them are
     * read.
     */
    @Test
    fun theBandAlwaysTouchesTheHandle() {
        val leans = listOf(-0.45f, -0.2f, 0f, 0.2f, 0.45f)
        val misses = mutableListOf<String>()
        for (dx in leans) for (dy in leans) {
            held(anchor = 3, head = 12, lean = Offset(dx, dy)) { image, grid ->
                // Settled, the cap is the pull's share of the lean from its own cell.
                val centre = grid.cell(12) + Offset(dx, dy) * (Cell * Pull)
                val left = centre.x - Cell / 2f
                val top = centre.y - Cell / 2f + InsetPx
                val start = image.isRangeTint(Offset(left - 4f, centre.y))
                val above = image.isRangeTint(Offset(centre.x, top - 4f))
                if (!start && !above) misses += "($dx, $dy)"
            }
        }
        assertTrue(misses.isEmpty(), "leaning ${misses.joinToString()}, the band touched neither the cap's start nor its top")
    }

    /**
     * Backing up from the 19th toward the 12th, the end of the week above and the
     * start of the 19th's own week empty out of the band; the days nearer the anchor
     * stay.
     */
    @Test
    fun backingUpEmptiesTheDaysBeingLeft() {
        held(anchor = 3, head = 19, lean = Offset(0f, -0.45f)) { image, grid ->
            assertTrue(!image.isRangeTint(grid.below(16)), "the 16th, the end of the week above, was still band")
            assertTrue(image.isRangeTint(grid.below(13)), "the 13th, nearer the anchor, emptied")
            assertTrue(image.isRangeTint(grid.below(17)), "the 17th, the start of the head's week, emptied first")
        }
    }

    /** Let go between days, and the band settles on the range that was chosen. */
    @Test
    fun aReleaseSettlesOnTheCommittedRange() {
        var start by mutableStateOf<LocalDate?>(null)
        var end by mutableStateOf<LocalDate?>(null)
        lateinit var grid: Grid
        lateinit var settled: BufferedImage
        picker(onRange = { s, e -> start = s; end = e }, start = { start }, end = { end }) { scene, g ->
            grid = g
            scene.drag(from = g.cell(3), to = g.cell(19), steps = 24, release = false)
            scene.frames(20)
            val between = g.cell(19) - Offset(0f, Cell * 0.45f)
            scene.move(between)
            scene.frames(12)
            scene.release(between)
            settled = scene.frames(40)
        }
        assertEquals(LocalDate(2026, 8, 3) to LocalDate(2026, 8, 19), start to end)
        for (day in listOf(13, 16, 17, 18)) {
            assertTrue(settled.isRangeTint(grid.below(day)), "the $day, in the range chosen, was not band after letting go")
        }
    }

    /** Right to left, the day under the finger is the one chosen — the grid's hit test mirrors with it. */
    @Test
    fun aRightToLeftDragPicksTheDaysUnderTheFinger() {
        var start by mutableStateOf<LocalDate?>(null)
        var end by mutableStateOf<LocalDate?>(null)
        picker(
            onRange = { s, e -> start = s; end = e },
            start = { start },
            end = { end },
            direction = LayoutDirection.Rtl,
        ) { scene, g ->
            scene.drag(from = g.cell(10), to = g.cell(14), steps = 24)
            scene.frames(6)
        }
        assertEquals(LocalDate(2026, 8, 10) to LocalDate(2026, 8, 14), start to end)
    }

    /** Anchored on [anchor], dragged to [head], then leaning by [lean] cells and held still. */
    private fun held(anchor: Int, head: Int, lean: Offset, check: (BufferedImage, Grid) -> Unit) {
        var start by mutableStateOf<LocalDate?>(null)
        var end by mutableStateOf<LocalDate?>(null)
        picker(onRange = { s, e -> start = s; end = e }, start = { start }, end = { end }) { scene, grid ->
            scene.drag(from = grid.cell(anchor), to = grid.cell(head), steps = 24, release = false)
            scene.frames(20)
            val at = grid.cell(head) + lean * Cell
            scene.move(at)
            val image = scene.frames(24)
            scene.release(at)
            check(image, grid)
        }
    }

    private fun picker(
        onRange: (LocalDate, LocalDate?) -> Unit,
        start: () -> LocalDate?,
        end: () -> LocalDate?,
        direction: LayoutDirection = LayoutDirection.Ltr,
        body: (Scene, Grid) -> Unit,
    ) {
        var bounds = Rect.Zero
        Scene(width = 700, height = 800) {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    DateRangePicker(
                        start = start(),
                        end = end(),
                        onRangeSelected = onRange,
                        today = LocalDate(2026, 8, 1),
                        modifier = Modifier.reportBounds { bounds = it },
                    )
                }
            }
        }.use { scene ->
            scene.frames(6)
            assertTrue(bounds.width > 0f, "the picker never reported a size")
            body(scene, Grid(bounds, direction == LayoutDirection.Rtl))
        }
    }

    /** August 2026's grid in a picker at [bounds]: seven columns, six rows, five blanks. */
    private class Grid(val bounds: Rect, val rtl: Boolean) {
        fun cell(day: Int): Offset {
            val top = bounds.bottom - Rows * Cell
            val index = day - 1 + LeadingBlanks
            val column = index % 7 + 0.5f
            val x = if (rtl) bounds.right - column * Cell else bounds.left + column * Cell
            return Offset(x, top + (index / 7 + 0.5f) * Cell)
        }

        /** Inside the band's height and below the day's digit. */
        fun below(day: Int): Offset = cell(day) + Offset(0f, Cell * 0.35f)
    }

    /** Bluer than it is red: the range tint, and nothing else this picker draws. */
    private fun BufferedImage.isRangeTint(at: Offset): Boolean {
        val rgb = getRGB(at.x.toInt(), at.y.toInt())
        return (rgb and 0xFF) - (rgb shr 16 and 0xFF) > 4
    }

    private companion object {
        const val LeadingBlanks = 5
        const val Rows = 6

        /** A cell at this scene's width: 700px over seven columns. */
        const val Cell = 100f

        /** `CalendarMonthDefaults.CellInset` at density two. */
        const val InsetPx = 2f

        /** The share of the lean a cap follows the finger by: `SliderDefaults.DetentPull`. */
        const val Pull = 0.45f
    }
}
