package io.kontour.ui.interaction

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The release velocity of an owned drag is the finger's, whether or not the node
 * it is on moves with it.
 *
 * Reported from a phone against `SwipeActions`: a quick swipe showed an action at
 * full size, then closed the row again. The velocity was read from each change's
 * position, which is in the dragging node's own coordinates — and a swipe row's
 * node moves with the finger, so a steady swipe measured close to nothing, and
 * the last frame's lag read as a flick back the other way.
 */
@OptIn(ExperimentalTestApi::class)
class OwnedDragVelocityTest {

    @Test
    fun aNodeThatMovesWithTheFingerReportsTheFingersSpeed() {
        val moving = releaseVelocity(followsFinger = true)
        val still = releaseVelocity(followsFinger = false)
        // Ten pixels every ten milliseconds: a thousand a second.
        assertTrue(still in 800f..1200f, "a still node measured ${still}px/s for a 1000px/s swipe")
        assertTrue(
            moving in 800f..1200f,
            "a node moving with the finger measured ${moving}px/s for the same 1000px/s swipe " +
                "(a still one measured ${still}px/s)",
        )
    }

    private fun releaseVelocity(followsFinger: Boolean): Float {
        var released = Float.NaN
        runComposeUiTest {
            setContent {
                var moved by mutableFloatStateOf(0f)
                val scope = rememberCoroutineScope()
                Box(Modifier.size(400.dp, 100.dp)) {
                    Box(
                        Modifier
                            .offset { IntOffset(if (followsFinger) moved.roundToInt() else 0, 0) }
                            .size(200.dp, 100.dp)
                            .horizontalDragOwning(
                                enabled = true,
                                interactionSource = null,
                                scope = scope,
                                onStart = {},
                                onDelta = { moved += it },
                                onEnd = {},
                                onRelease = { released = it },
                            )
                    )
                }
            }
            onRoot().performTouchInput {
                down(Offset(20f, 20f))
                repeat(20) { moveBy(Offset(10f, 0f), delayMillis = 10) }
                up()
            }
            waitForIdle()
        }
        return released
    }
}
