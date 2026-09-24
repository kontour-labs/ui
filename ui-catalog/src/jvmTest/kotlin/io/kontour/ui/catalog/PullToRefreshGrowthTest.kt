package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.list.PullToRefresh
import io.kontour.ui.components.list.PullToRefreshState
import io.kontour.ui.components.list.rememberPullToRefreshState
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The arc is most of the way round before the finger is.
 *
 * Reported as "the pull to refresh arc doesn't feel like it gets big enough
 * before the spinner starts", and there were three reasons at once rather than
 * the one the sentence names.
 *
 * | | Was |
 * |---|---|
 * | the sweep's ceiling | 190°, a hair over half a circle |
 * | the whole indicator's scale **and** alpha | both `= pull` |
 * | the arc's canvas | 20dp inside a 40dp surface |
 *
 * The second is the one that made a 190° arc read far smaller than 190°: the
 * arc's *apparent* size grew as `pull²`, because its length grew with the sweep
 * and its radius grew with the scale at the same time — at half a pull the user
 * saw a 95° arc drawn at half size and half opacity. Raising the ceiling alone
 * would have been the "number that improves but does not resolve" mistake this
 * repository has already written up once.
 *
 * ### Ink, at two points on the pull
 *
 * Counted rather than measured, the way [PullToRefreshArcTest] counts its
 * handover: the arc is one stroke of one colour, so its ink is its length, and
 * its length is radius times sweep. Comparing half a pull against a full one
 * therefore compares exactly the compounding this is about, without needing to
 * recover an angle from a bitmap.
 *
 * A sweep that is linear in the pull and a size that is already full gives
 * **half**. Measured on the code this was written against, half a pull drew
 * **nothing at all** — 0px against 205px — because a 95° arc at half size is
 * also at half opacity, and half opacity over a white page is lighter than any
 * threshold that is not counting the page as well.
 *
 * That the arc is invisible halfway through the gesture is the report, stated as
 * a number.
 */
class PullToRefreshGrowthTest {

    /**
     * Measured where the circle is first allowed its full size, not at half a pull.
     *
     * The circle now keeps `PullToRefreshDefaults.Clearance` of page above and below
     * it — asked for, as "a little bit more gap between the top of the list when
     * the user first starts pulling" — so until the gap is the circle plus both
     * clearances, 56dp of an 80dp pull, it is smaller *by geometry*. That is not
     * the defect this test is for, and at half a pull it is unavoidable. What this
     * guards is the compounding: once the size is not bounded, the ink has to be
     * the sweep's share of the arc and no less.
     */
    @Test
    fun theArcIsInStepWithThePullOnceTheCircleIsWhole() {
        val half = arcInk(fraction = WholeAt)
        val full = arcInk(fraction = 1f)

        // Only the full pull is guarded. Half a pull drawing *nothing* is not a
        // broken instrument here — it is the reading, and the first version of
        // this test threw it away as one.
        assertTrue(
            full > 40,
            "a full pull drew ${full}px, so there is no arc to take a share of",
        )
        val share = half.toFloat() / full
        assertTrue(
            share >= WholeAt * MinShare / 0.5f,
            "at ${(WholeAt * 100).toInt()}% of a pull the arc carried ${half}px of ink against " +
                "${full}px at a full one, which is ${(share * 100).toInt()}% of it. The sweep " +
                "is linear in the pull, so this is meant to be that share of an arc — " +
                "anything much under that is the indicator still growing while the " +
                "arc is, and the two multiply.",
        )
    }

    /**
     * How much arc ink is on screen at [fraction] of the way to the threshold.
     *
     * **Driven through `PullToRefreshState.drag` rather than by a finger**, which is
     * what that method is public for. It used to press and move, and a gesture's
     * first eighteen-odd pixels go to touch slop — so "half a pull" was really
     * thirty-nine hundredths of one, and the ratio this test reports was an
     * underestimate of itself. The constant below is meticulous about resistance and
     * says nothing about slop, which is exactly the kind of arithmetic that is right
     * until something else measures the same pixels.
     *
     * What exposed it was the indicator gaining a geometric bound — it may not be
     * drawn wider than the gap it sits in — which bites below 40dp of gap and so
     * bit at 0.39 of a pull and not at 0.5. The gesture is covered by
     * `PullToRefreshGestureTest`; what this test is about is ink at two points on
     * the pull, and the points may as well be the ones it names.
     */
    private fun arcInk(fraction: Float): Int {
        var state: PullToRefreshState? = null
        var ink = 0

        Scene(width = 400, height = 600) {
            val pull = rememberPullToRefreshState()
            state = pull
            val rows = rememberLazyListState()
            // As in `PullToRefreshArcTest`: an opaque ground, or the gap the pull
            // opens reads as ink and swamps the three hundred pixels of ring.
            Box(Modifier.fillMaxSize().background(Color.White))
            PullToRefresh(
                refreshing = false,
                onRefresh = {},
                state = pull,
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(
                    state = rows,
                    modifier = Modifier.fillMaxSize().background(Color.White),
                ) {
                    items(40) { Box(Modifier.fillMaxWidth().height(40.dp)) }
                }
            }
        }.use { scene ->
            scene.frames(4)
            requireNotNull(state).drag(ThresholdTravel * fraction)
            // The drawn gap is a spring chasing the state's, and both the indicator's
            // position and its bound are read off the drawn one.
            ink = scene.frames(SettleFrames).darkInk()
        }
        return ink
    }

    private fun BufferedImage.darkInk(): Int {
        var dark = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                if ((getRGB(x, y) shr 16 and 0xFF) < 120) dark++
            }
        }
        return dark
    }

    private companion object {
        /** Long enough for the gap's spring to arrive at what it was told. */
        const val SettleFrames = 90

        /**
         * The pull that puts the indicator exactly on its threshold.
         *
         * `Threshold` is 80dp at a scene density of 2, and `Resistance` applies
         * **outward past the threshold only** (`PullToRefresh.kt:117`) — so
         * everything up to it is one-to-one and this is 160, not the 400 that
         * dividing by the resistance gives. An earlier guess put both fractions
         * past the threshold, where `pull` is clamped, and the two frames came
         * back byte-identical at 299px each: a ratio of exactly 1.00, which is
         * what an instrument that never varied its input reports.
         */
        const val ThresholdTravel = 160f

        /**
         * How much of the full arc has to be drawn at half a pull, were the circle
         * whole there; scaled to [WholeAt] where it is used.
         *
         * Half is the target and the bar is a little under it, because the arc
         * has round caps: a short arc keeps both of them, so its ink does not
         * fall quite in proportion to its angle. The defect is nearer a quarter.
         */
        const val MinShare = 0.42f

        /**
         * The first share of a pull at which the circle may be its full 40dp: the
         * circle plus 8dp of clearance at each end, over the 80dp threshold.
         */
        const val WholeAt = 0.7f
    }
}
