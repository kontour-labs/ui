package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.selection.RangeSlider
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The range slider answers a drag the way [io.kontour.ui.components.selection.Slider]
 * does.
 *
 * Its own file says "two sliders in one library that answer the same gesture
 * differently is worse than either of them being wrong", and for a while they
 * did: the plain slider took ownership of the pointer and this one was still a
 * `Modifier.draggable`, which lets the vertical half of every change through to
 * a parent scroller. See `SliderDragOwnershipTest` for the whole of that story.
 *
 * The second test is the shove. Dragging one thumb into the other pushes it
 * along, and the pushed thumb is drawn welded to the one pushing it rather than
 * springing along behind — which is what "the other control starts to jump
 * around a bit" was. The drawn position is not reachable from a test; what is
 * reachable is that the *values* stay in contact for the whole of the push, and
 * that is the input the drawing is derived from.
 */
class RangeSliderDragTest {

    @Test
    fun aDragSurvivesTheFingerStrayingVertically() {
        var value by mutableStateOf(0.2f..0.8f)
        var bounds = Rect.Zero

        Scene(width = 600, height = 400) {
            KontourTheme {
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(Color.White)
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                ) {
                    RangeSlider(
                        value = value,
                        onValueChange = { value = it },
                        modifier = Modifier.reportBounds { bounds = it },
                    )
                    Box(Modifier.height(2000.dp))
                }
            }
        }.use { scene ->
            scene.frames(3)

            val y = bounds.center.y
            // On the end thumb, which sits at 0.8.
            scene.press(Offset(bounds.left + bounds.width * 0.8f, y))
            scene.frames(1)
            val afterPress = value.endInclusive
            scene.move(Offset(bounds.left + bounds.width * 0.7f, y))
            scene.frames(1)
            val afterHorizontal = value.endInclusive

            scene.move(Offset(bounds.left + bounds.width * 0.6f, y + 160f))
            scene.frames(1)
            val afterStray = value.endInclusive
            scene.release(Offset(bounds.left + bounds.width * 0.6f, y + 160f))
            scene.frames(2)

            assertTrue(
                afterHorizontal < afterPress,
                "the drag never started: pressed at $afterPress, then moving " +
                    "along the track left the end thumb at $afterHorizontal",
            )
            assertTrue(
                afterStray < afterHorizontal,
                "the range slider stopped following the finger once it left the " +
                    "track vertically — $afterHorizontal before the stray and " +
                    "$afterStray after, with the pointer still down.",
            )
        }
    }

    @Test
    fun aShovedThumbStaysInContact() {
        var value by mutableStateOf(0.4f..0.6f)
        var bounds = Rect.Zero
        val separations = mutableListOf<Float>()

        Scene(width = 600, height = 300) {
            KontourTheme {
                Column(Modifier.fillMaxSize().background(Color.White).padding(20.dp)) {
                    RangeSlider(
                        value = value,
                        onValueChange = { value = it },
                        minDistance = 0.1f,
                        modifier = Modifier.reportBounds { bounds = it },
                    )
                }
            }
        }.use { scene ->
            scene.frames(3)

            val y = bounds.center.y
            // Take the end thumb and walk it left, through the start thumb and
            // on toward the beginning of the track.
            scene.press(Offset(bounds.left + bounds.width * 0.6f, y))
            scene.frames(1)

            var at = 0.6f
            while (at > 0.15f) {
                at -= 0.05f
                scene.move(Offset(bounds.left + bounds.width * at, y))
                scene.frames(1)
                separations += value.endInclusive - value.start
            }
            scene.release(Offset(bounds.left + bounds.width * at, y))
            scene.frames(2)

            assertTrue(
                separations.all { it >= 0.1f - 0.001f },
                "the range closed past its own minimum while being shoved: $separations",
            )
            assertTrue(
                separations.last() <= 0.1f + 0.01f,
                "the thumbs never came into contact, so nothing was being " +
                    "pushed and the test proves nothing: $separations",
            )
            assertTrue(
                value.start < 0.3f,
                "the start thumb was not shoved along — it finished at " +
                    "${value.start} with the end thumb at ${value.endInclusive}",
            )
        }
    }
}
