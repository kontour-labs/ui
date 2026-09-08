package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.list.PullToRefresh
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

    @Test
    fun theArcIsHalfDrawnAtHalfAPull() {
        val half = arcInk(fraction = 0.5f)
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
            share >= MinShare,
            "at half a pull the arc carried ${half}px of ink against ${full}px at a " +
                "full one, which is ${(share * 100).toInt()}% of it. The sweep is " +
                "linear in the pull, so half a pull is meant to be half an arc — " +
                "anything much under that is the indicator still growing while the " +
                "arc is, and the two multiply.",
        )
    }

    /** How much arc ink is on screen at [fraction] of the way to the threshold. */
    private fun arcInk(fraction: Float): Int {
        var bounds = Rect.Zero
        var ink = 0

        Scene(width = 400, height = 600) {
            val pull = rememberPullToRefreshState()
            val rows = rememberLazyListState()
            // As in `PullToRefreshArcTest`: an opaque ground, or the gap the pull
            // opens reads as ink and swamps the three hundred pixels of ring.
            Box(Modifier.fillMaxSize().background(Color.White))
            PullToRefresh(
                refreshing = false,
                onRefresh = {},
                state = pull,
                modifier = Modifier.fillMaxSize().reportBounds { bounds = it },
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
            require(bounds.height > 0f) { "the container never reported a size" }

            val from = Offset(bounds.center.x, bounds.top + 40f)
            val travel = ThresholdTravel * fraction
            scene.press(from)
            repeat(Steps) { step ->
                scene.move(Offset(from.x, from.y + travel * (step + 1) / Steps))
                scene.frame()
            }
            ink = scene.frames(6).darkInk()
            scene.release(Offset(from.x, from.y + travel))
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
        const val Steps = 24

        /**
         * Finger travel that puts the pull exactly on its threshold.
         *
         * `Threshold` is 80dp at a scene density of 2, and `Resistance` applies
         * **outward past the threshold only** (`PullToRefresh.kt:103`) — so
         * everything up to it is one-to-one and this is 160, not the 400 that
         * dividing by the resistance gives. That first guess put both fractions
         * past the threshold, where `pull` is clamped, and the two frames came
         * back byte-identical at 299px each: a ratio of exactly 1.00, which is
         * what an instrument that never varied its input reports.
         */
        const val ThresholdTravel = 160f

        /**
         * How much of the full arc has to be drawn at half a pull.
         *
         * Half is the target and the bar is a little under it, because the arc
         * has round caps: a short arc keeps both of them, so its ink does not
         * fall quite in proportion to its angle. The defect is nearer a quarter.
         */
        const val MinShare = 0.42f
    }
}
