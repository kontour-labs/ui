package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.kontour.ui.components.list.PullToRefresh
import io.kontour.ui.components.list.PullToRefreshState
import io.kontour.ui.components.list.rememberPullToRefreshState
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The circle stays in the gap it is revealed in — over nothing, and whole.
 *
 * Reported from a phone: *"in the pull to refresh, I don't want the circle to
 * overlap the content at any point. It should appear slightly higher of where it
 * is, but without being cut off."*
 *
 * Both halves were happening, and they are the same arithmetic. The indicator is a
 * fixed 40dp circle centred in the gap, so a gap of `G` puts its edges at
 * `G/2 ± 20dp` — below the content's top edge for every `G` under 40dp, and above
 * the container's own top edge by exactly as much, where a clipping ancestor (a
 * `Surface`, the catalog's own `Card`) shaves it flat. Moving it cannot fix that:
 * there is nowhere in a 20dp gap to put a 40dp circle. Sizing it to the gap can.
 *
 * ### Where these measurements are taken
 *
 * Driven through `PullToRefreshState.drag`, which is public for this reason, so the
 * gap is an exact number of pixels rather than a finger's travel less whatever
 * touch slop took.
 *
 * 32dp of pull — 64px here — is the worst case and is not a round number by
 * accident. The indicator finishes growing in at `GrowthShare` of the 80dp
 * threshold, which is 32dp, so that is the first gap at which the old code asked
 * for the full 40dp circle: 80px of circle in a 64px gap, hanging 8px over the
 * content and 8px above the container.
 *
 * Three colours, so pixels can be told apart without knowing the theme's numbers:
 * the page behind everything is blue, the content is black, and the indicator's
 * surface is the only light thing on screen. A shadow over either of the other two
 * stays dark, so "light" means the circle itself and not its bleed.
 */
class PullIndicatorTest {

    @Test
    fun theCircleIsNeverOverTheContentAndIsNeverCutOff() {
        pulledTo(WorstCaseGap) { frame ->
            val top = frame.contentTop()
            assertTrue(
                abs(top - WorstCaseGap.toInt()) <= 3,
                "the content's top edge settled at $top rather than at the " +
                    "${WorstCaseGap.toInt()}px gap that was asked for, so this is " +
                    "not the case the numbers above were worked out for",
            )

            val circle = frame.lightBounds()
            assertTrue(
                circle != null,
                "no indicator was drawn at all — `surfaceRaised` is supposed to be " +
                    "the one light thing on this page",
            )
            val (columns, rows) = requireNotNull(circle)

            assertTrue(
                rows.last < top,
                "the circle's ink reaches row ${rows.last} and the content starts " +
                    "at row $top, so ${rows.last - top + 1}px of it is drawn over " +
                    "the list. The report: I don't want the circle to overlap the " +
                    "content at any point.",
            )

            val width = columns.last - columns.first + 1
            val height = rows.last - rows.first + 1
            assertTrue(
                abs(width - height) <= 3,
                "the circle measures ${width}x${height}px, which is not round. It " +
                    "is centred in a ${WorstCaseGap.toInt()}px gap at a size that " +
                    "does not fit in one, so the container's top edge has taken " +
                    "${width - height}px off it — the other half of the report.",
            )
        }
    }

    /**
     * And the circle everybody actually looks at is still the full 40dp.
     *
     * The bound has to be a bound and not a shrink: once the gap is at least the
     * circle's own size there is nothing to solve, and from there to a full pull the
     * indicator is the size it has always been. Measured at the threshold, which is
     * where the gesture commits and where the arc is read.
     */
    @Test
    fun aFullPullStillDrawsTheWholeCircle() {
        pulledTo(FullPullGap) { frame ->
            val (columns, rows) = requireNotNull(frame.lightBounds())
            val width = columns.last - columns.first + 1
            val height = rows.last - rows.first + 1
            assertTrue(
                abs(width - IndicatorPx) <= 4 && abs(height - IndicatorPx) <= 4,
                "at a full pull the circle measures ${width}x${height}px against " +
                    "the ${IndicatorPx.toInt()}px it is declared as — the bound on " +
                    "small gaps has followed it all the way up",
            )
            assertTrue(
                rows.last < frame.contentTop(),
                "even at a full pull the circle is over the content",
            )
        }
    }

    /**
     * There is page between the circle and the list from the first pixels of a pull.
     *
     * Reported from a phone: the circle needs "a little bit more gap between the top
     * of the list when the user first starts pulling on it". Sized to the gap, it
     * filled the gap exactly — its bottom edge resting on the list's top edge until
     * the gap passed 40dp — so early in a pull it read as sitting on the content
     * rather than in the space above it. It now keeps [Clearance] of page above and
     * below at every gap, and is smaller for it while the gap is short.
     */
    @Test
    fun theCircleKeepsItsDistanceFromTheListEarlyInAPull() {
        pulledTo(EarlyGap) { frame ->
            val top = frame.contentTop()
            val (_, rows) = requireNotNull(frame.lightBounds()) { "no indicator was drawn" }
            val below = top - rows.last - 1
            val above = rows.first
            assertTrue(
                below >= Clearance - 2,
                "${EarlyGap.toInt()}px into a pull the circle ends ${below}px above the list, " +
                    "where it should keep ${Clearance}px of page between them",
            )
            assertTrue(
                above >= Clearance - 2,
                "the circle starts ${above}px below the top, where it should keep ${Clearance}px",
            )
        }
    }

    /** Opens the gap to exactly [gap] pixels, lets it settle, and reads the frame. */
    private fun pulledTo(gap: Float, check: (BufferedImage) -> Unit) {
        var state: PullToRefreshState? = null
        Scene(width = 400, height = 400, darkTheme = false) {
            val live = rememberPullToRefreshState()
            state = live
            Box(Modifier.fillMaxSize().background(Color.Blue)) {
                PullToRefresh(
                    refreshing = false,
                    onRefresh = {},
                    state = live,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Box(Modifier.fillMaxSize().background(Color.Black))
                }
            }
        }.use { scene ->
            scene.frames(4)
            requireNotNull(state).drag(gap)
            // The drawn gap is a spring chasing the state's, so it needs long
            // enough to arrive — the whole point of reading the drawn one.
            check(scene.frames(SettleFrames))
        }
    }

    /**
     * The first row that is content rather than page, read far from the circle.
     *
     * Column 8: the circle is centred in a 400px scene, so it and its shadow live
     * well inside the middle, and the page out here is untouched by either.
     */
    private fun BufferedImage.contentTop(): Int =
        (0 until height).firstOrNull { y -> getRGB(EdgeColumn, y) and 0xFFFFFF == 0 }
            ?: height

    /** The columns and rows holding anything light — which is only the indicator. */
    private fun BufferedImage.lightBounds(): Pair<IntRange, IntRange>? {
        var minX = width
        var maxX = -1
        var minY = height
        var maxY = -1
        for (y in 0 until height) {
            for (x in 0 until width) {
                val rgb = getRGB(x, y)
                val light = (rgb shr 16 and 0xFF) > LightFloor &&
                    (rgb shr 8 and 0xFF) > LightFloor &&
                    (rgb and 0xFF) > LightFloor
                if (!light) continue
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
        }
        return if (maxX < 0) null else (minX..maxX) to (minY..maxY)
    }

    private companion object {
        /** 30dp of pull: under the circle's own size, where it used to fill the gap. */
        const val EarlyGap = 60f

        /** `Theme.spacing.xs` above and below the circle, at the scene's density of 2. */
        const val Clearance = 16

        /** 40dp of indicator at the scene's density of 2. */
        const val IndicatorPx = 80f

        /**
         * `GrowthShare` of the 80dp threshold, in pixels: the first gap at which
         * the old code asked for the whole circle, and so the widest the overhang
         * ever got.
         */
        const val WorstCaseGap = 64f

        /** The threshold itself, where the gesture commits. */
        const val FullPullGap = 160f

        const val EdgeColumn = 8

        /** Blue and black both fail this; `surfaceRaised` in a light theme passes. */
        const val LightFloor = 150

        /** Long enough for the gap's spring to arrive. */
        const val SettleFrames = 90
    }
}
