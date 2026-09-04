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
import io.kontour.ui.components.selection.Slider
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A slider keeps the drag when the finger wanders off it.
 *
 * Reported as: dragging on mobile and letting a finger leave the slider's touch
 * target stops the drag *without lifting it*, and on a desktop the same happens
 * if you press, drag, then scroll a little.
 *
 * The cause is a parent that scrolls. `Modifier.draggable(Horizontal)` consumes
 * only the horizontal half of each pointer change, so a vertical stray feeds the
 * scroller above it, which passes its own touch slop, claims the gesture, and
 * cancels the child's drag underneath it. A slider that is not inside anything
 * scrollable never shows the bug, which is why it survived — and every slider in
 * a real form is inside one.
 */
class SliderDragOwnershipTest {

    @Test
    fun aDragSurvivesTheFingerStrayingVertically() {
        var value by mutableStateOf(0.5f)
        var bounds = Rect.Zero

        Scene(width = 600, height = 400) {
            KontourTheme {
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(Color.White)
                        // The parent this bug needs. Tall content, so it really
                        // can scroll rather than being a no-op wrapper.
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                ) {
                    Slider(
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
            // Pressing jumps the thumb to the finger, so the baseline is where
            // the press landed rather than where the slider started — the value
            // *drops* to 0.2 here, which is the control working.
            scene.press(Offset(bounds.left + bounds.width * 0.2f, y))
            scene.frames(1)
            val afterPress = value
            // Along the track first, so the drag is unambiguously the slider's.
            scene.move(Offset(bounds.left + bounds.width * 0.4f, y))
            scene.frames(1)
            val afterHorizontal = value

            // Now wander well off the track, further than any touch slop, while
            // still pressed — the exact gesture that used to hand the drag away.
            scene.move(Offset(bounds.left + bounds.width * 0.6f, y + 160f))
            scene.frames(1)
            val afterStray = value
            scene.release(Offset(bounds.left + bounds.width * 0.6f, y + 160f))
            scene.frames(2)

            assertTrue(
                afterHorizontal > afterPress,
                "the drag never started: pressed to $afterPress, then moving " +
                    "along the track left it at $afterHorizontal",
            )
            assertTrue(
                afterStray > afterHorizontal,
                "the slider stopped following the finger once it left the track " +
                    "vertically — it read $afterHorizontal before the stray and " +
                    "$afterStray after, with the pointer still down. The parent " +
                    "scroller took the gesture.",
            )
        }
    }
}
