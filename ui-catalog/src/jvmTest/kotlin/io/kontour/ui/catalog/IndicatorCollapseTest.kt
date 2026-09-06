package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.IndicatorSizing
import io.kontour.ui.foundation.SelectionIndicatorBox
import io.kontour.ui.foundation.rememberSelectionIndicatorState
import io.kontour.ui.foundation.selectionIndicatorItem
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A marker that has faded out comes back where it is wanted, at once.
 *
 * The third of the three things reported about collapsible lists: after
 * collapsing the group holding the current page, selecting an item elsewhere
 * made the marker "appear a split second late".
 *
 * The effect that drives it ran the travel and the fade in sequence —
 * `bounds.animateTo(target, spring)` suspends until the spring settles, and only
 * then does `alpha.animateTo(1f)` start. That is right for a marker moving
 * between two visible items, where it is already opaque and the fade is a no-op.
 * It is wrong for one that is not on screen: it travels invisibly from wherever
 * it happened to be when it went, and turns up at the far end when the spring
 * has finished. The same stale rect is what sends it "far above the list" when
 * the group it left has closed over its old position.
 *
 * Faded out is the same situation as never drawn, so it now takes the same
 * branch the first appearance does: put it where it is going and fade it up
 * there.
 *
 * ### Measured as frames to first ink
 *
 * Not where the marker ends up — both versions end up in the right place. The
 * defect is entirely in *when*, so this counts the frames between the selection
 * changing and any marker reaching the screen.
 */
class IndicatorCollapseTest {

    @Test
    fun aMarkerThatFadedOutComesBackWithoutWaitingForTheTravel() {
        val delay = framesUntilTheMarkerReturns()
        assertTrue(
            delay in 0 until PromptFrames,
            "the marker took $delay frames to reach the screen after the " +
                "selection changed. It is travelling from where it used to be " +
                "with nothing drawn, and only fading in once the spring has " +
                "settled — which is a marker that appears late somewhere the " +
                "user has already looked.",
        )
    }

    private fun framesUntilTheMarkerReturns(): Int {
        var groupOpen by mutableStateOf(true)
        var selected by mutableStateOf(0)
        var appeared = -1

        Scene(width = 300, height = 400) {
            val state = rememberSelectionIndicatorState()
            Box(Modifier.fillMaxSize().background(Color.White)) {
                SelectionIndicatorBox(
                    state = state,
                    sizing = IndicatorSizing.Inset(0.dp, 0.dp),
                    indicator = { Box(Modifier.fillMaxSize().background(Marker)) },
                ) {
                    Column {
                        // The collapsible group, at the top. Its row is the one
                        // selected to begin with, so closing the group is what
                        // takes the marker off the screen.
                        if (groupOpen) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(RowHeight.dp)
                                    .selectionIndicatorItem(key = 0, selected = selected == 0)
                            )
                        }
                        // Somewhere else entirely, far enough down that a spring
                        // crossing the gap takes many frames.
                        repeat(4) { Box(Modifier.fillMaxWidth().height(RowHeight.dp)) }
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(RowHeight.dp)
                                .selectionIndicatorItem(key = 1, selected = selected == 1)
                        )
                    }
                }
            }
        }.use { scene ->
            scene.frames(30)
            groupOpen = false
            // Long enough for the fade-out to finish, so the marker really is
            // off the screen when the next selection arrives.
            scene.frames(30)
            selected = 1
            repeat(Frames) { frame ->
                if (scene.frame().holdsMarker() && appeared < 0) appeared = frame
            }
        }
        return appeared
    }

    private fun BufferedImage.holdsMarker(): Boolean {
        for (y in 0 until height) {
            for (x in 0 until width) {
                val rgb = getRGB(x, y)
                val r = (rgb shr 16) and 0xFF
                val g = (rgb shr 8) and 0xFF
                val b = rgb and 0xFF
                if (r > b + Separation && r > g + Separation) return true
            }
        }
        return false
    }

    private companion object {
        val Marker = Color(0xFFCC0000)
        const val RowHeight = 40
        const val Frames = 60

        /**
         * How soon "at once" is.
         *
         * The marker needs a frame for the effect to run and a few for the fade
         * to reach a countable opacity. A spring crossing five rows takes many
         * times that, which is the gap this sits in.
         */
        const val PromptFrames = 8

        /** Enough of a hue to name, at any opacity worth counting. */
        const val Separation = 25
    }
}
