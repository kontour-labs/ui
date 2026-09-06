package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import io.kontour.ui.components.list.PullToRefresh
import io.kontour.ui.components.list.PullToRefreshDefaults
import io.kontour.ui.components.list.PullToRefreshState
import io.kontour.ui.components.list.rememberPullToRefreshState
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The spinner opens where the pull arc finished.
 *
 * `PullToRefresh` draws its own arc while the finger is down — it grows and
 * turns with the pull, which is the progress the gesture is making — and hands
 * over to a [io.kontour.ui.components.display.Spinner] the moment the pull
 * commits. The two were the same length at the handover and nothing else: the
 * pull's head travelled 300° to finish at seven o'clock, and the spinner opened
 * at `drawArc`'s zero, which is three. So the arc the user had just dragged all
 * the way round jumped back across the dial in one frame.
 *
 * ### Measured as two frames of the same control, overlaid
 *
 * Both frames hold one arc on a white ground with the indicator in exactly the
 * same place — the indicator sits at the threshold while refreshing, and this
 * pulls to exactly the threshold, so nothing moves but the arc itself. The
 * question is then how much of the ink is in the same place in both, which is a
 * number rather than a judgement: an arc that carried on from where it was
 * overlaps itself almost entirely, and one that jumped across the dial does not
 * overlap at all.
 */
class PullToRefreshHandoffTest {

    @Test
    fun theSpinnerOpensWhereThePullArcFinished() {
        var refreshing by mutableStateOf(false)
        var state: PullToRefreshState? = null
        var pulled: BufferedImage? = null
        var spinning: BufferedImage? = null

        Scene(width = 400, height = 300) {
            // White *behind* the control as well as inside it. The content is
            // offset down by the pull and the indicator is revealed in the gap,
            // so without this the gap is transparent — and `getRGB` reports
            // transparent as black, which the ink sampler below would read as a
            // hundred thousand pixels of arc identical in both frames. The
            // first draft of this test passed against the unfixed code for
            // exactly that reason.
            Box(Modifier.fillMaxSize().background(Color.White)) {
                val live = rememberPullToRefreshState()
                state = live
                PullToRefresh(refreshing = refreshing, onRefresh = {}, state = live) {
                    Box(Modifier.fillMaxSize().background(Color.White))
                }
            }
        }.use { scene ->
            scene.frame()
            // Exactly the threshold, which is where the indicator rests while
            // refreshing too — so the swap moves the arc and nothing else.
            val threshold = with(Density(SceneDensity)) {
                PullToRefreshDefaults.Threshold.toPx()
            }
            requireNotNull(state).drag(threshold)
            // The indicator springs down to meet the pull; let it arrive.
            pulled = scene.frames(60)
            refreshing = true
            // Two frames, not one: the swap takes a composition, and the
            // spinner has turned about ten degrees by the second — which the
            // threshold below allows for.
            spinning = scene.frames(2)
        }

        val before = requireNotNull(pulled).ink()
        val after = requireNotNull(spinning).ink()

        assertTrue(before.isNotEmpty(), "the pull drew no arc at all")
        assertTrue(after.isNotEmpty(), "the spinner drew no arc at all")
        // An arc is a thin stroke on a 40dp circle. Anything approaching the
        // size of the scene means the sampler has found something that is not
        // the arc, and every number below it would be meaningless.
        val ceiling = requireNotNull(pulled).let { it.width * it.height / 20 }
        assertTrue(
            before.size < ceiling && after.size < ceiling,
            "the ink sampler found ${before.size} and ${after.size} dark pixels " +
                "against a ceiling of $ceiling — that is not an arc, so this " +
                "test is measuring the wrong thing",
        )

        val shared = before.count { it in after }
        val union = before.size + after.size - shared
        val overlap = shared.toFloat() / union

        assertTrue(
            overlap >= MinimumOverlap,
            "the pull's final arc and the spinner's first share " +
                "${(overlap * 100).toInt()}% of their ink " +
                "(${before.size}px then ${after.size}px, $shared in common). " +
                "The spinner is not opening where the pull left off — it starts " +
                "somewhere else on the dial and the arc jumps to get there.",
        )
    }

    /** Which pixels hold arc, as packed x/y. The rest of the scene is white. */
    private fun BufferedImage.ink(): Set<Int> {
        val found = mutableSetOf<Int>()
        for (y in 0 until height) {
            for (x in 0 until width) {
                val rgb = getRGB(x, y)
                val lum = (((rgb shr 16) and 0xFF) + ((rgb shr 8) and 0xFF) + (rgb and 0xFF)) / 3
                if (lum < InkLuminance) found += y * width + x
            }
        }
        return found
    }

    private companion object {
        const val SceneDensity = 2f

        /**
         * Dark enough to be the arc rather than the indicator's shadow.
         *
         * The arc is the surface's content colour, which is near black on the
         * near-white raised ground; the shadow is a few percent of black over
         * white and lands in the 230s.
         */
        const val InkLuminance = 100

        /**
         * How much of the two arcs has to coincide.
         *
         * They are the same length and in the same place if the handover is
         * right, so the only differences are the ten degrees the spinner turns
         * in the two frames it takes to appear and a third of a pixel of stroke
         * — call it ninety per cent, and take sixty to leave room. An arc that
         * jumped from seven o'clock to three shares nothing at all: 190° of
         * sweep leaves a 170° gap, and the two land on opposite sides of it.
         */
        const val MinimumOverlap = 0.6f
    }
}
