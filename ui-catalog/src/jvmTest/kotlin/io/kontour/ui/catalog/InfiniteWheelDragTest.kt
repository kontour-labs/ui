package io.kontour.ui.catalog

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.datetime.WheelPicker
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The wrapping drum answers a mouse too.
 *
 * Reported as *"in the infinite wheel picker, there's no click-and-drag"*, and
 * the two paths explain it between them. The finite wheel carries four gesture
 * nodes — a `pointerInput` that eats stray scroll events, a `nestedScroll`, a
 * `draggable` and a snap fling — and the `draggable`'s own KDoc says why it is
 * there: *"the `draggable` only ever sees a **mouse** the list declined"*. A
 * `LazyColumn` answers a finger and a wheel; it does not answer a grab.
 *
 * The infinite path had three modifiers in total: a height, a `clipToBounds` and
 * a `scrollable`. `scrollable` handles touch and the wheel, and a mouse drag is
 * neither — so on a desktop the drum could not be grabbed at all.
 *
 * ### Why the finite tests never covered it
 *
 * `WheelPickerDragTest.aMouseDragTurnsTheWheel` and
 * `WheelPickerContainmentTest.aWheelDoesNotScrollThePageBehindIt` both drive the
 * default `infinite = false`. The catalog's own demo defaults to `infinite = true`
 * (`DateTimeDemos.kt`, "Wrap around"), so the wheel every reader meets is the one
 * neither test touches. These are those two tests, on the other path.
 */
class InfiniteWheelDragTest {

    @Test
    fun aMouseDragTurnsTheWrappingDrum() {
        var selected by mutableIntStateOf(12)
        var bounds = Rect.Zero

        Scene(width = 400, height = 400) {
            KontourTheme {
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    WheelPicker(
                        items = (0..23).toList(),
                        selected = selected,
                        onSelectedChange = { selected = it },
                        label = { it.toString().padStart(2, '0') },
                        infinite = true,
                        modifier = Modifier.reportBounds { bounds = it },
                    )
                }
            }
        }.use { scene ->
            scene.frames(4)
            assertTrue(bounds.height > 0f, "the wheel never reported a size")
            scene.drag(
                from = bounds.alongY(0.2f),
                to = bounds.alongY(0.8f),
                steps = 20,
                pointer = PointerType.Mouse,
            )
            scene.frames(20)
        }

        assertTrue(
            selected != 12,
            "a mouse drag down the drum left it on $selected — it did not turn " +
                "at all. `scrollable` answers touch and the wheel; a grab is " +
                "neither, and the finite path adds a `draggable` for exactly this.",
        )
    }

    @Test
    fun aTouchDragStillTurnsItOnce() {
        // The control the finite path also has. A `draggable` stacked on top of
        // a `scrollable` that already handles touch would turn the drum twice as
        // far as the finger went — the same trap the finite wheel documents.
        var selected by mutableIntStateOf(12)
        var bounds = Rect.Zero

        Scene(width = 400, height = 400) {
            KontourTheme {
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    WheelPicker(
                        items = (0..23).toList(),
                        selected = selected,
                        onSelectedChange = { selected = it },
                        label = { it.toString().padStart(2, '0') },
                        itemHeight = 40.dp,
                        infinite = true,
                        modifier = Modifier.reportBounds { bounds = it },
                    )
                }
            }
        }.use { scene ->
            scene.frames(4)
            // Two rows up, at density 2: 40dp is 80px, so 160px of finger.
            val from = bounds.center
            scene.drag(
                from = from,
                to = androidx.compose.ui.geometry.Offset(from.x, from.y - 160f),
                steps = 20,
            )
            scene.frames(20)
        }

        assertEquals(
            14,
            selected,
            "a two-row drag up left the drum on $selected rather than 14 — a " +
                "drag handled twice moves four rows, not two.",
        )
    }

    @Test
    fun theWrappingDrumDoesNotScrollThePageBehindIt() {
        // `InfiniteWheel`'s `rememberScrollableState` consumes every pixel by
        // construction, so nothing is left for a parent — but a `draggable`
        // added above it is a new way for a gesture to escape, and this is the
        // assertion that says it did not.
        var selected by mutableIntStateOf(12)
        lateinit var scroll: ScrollState

        Scene(width = 400, height = 500) {
            KontourTheme {
                scroll = rememberScrollState()
                Column(Modifier.fillMaxSize().background(Color.White).verticalScroll(scroll)) {
                    Box(Modifier.fillMaxWidth().height(60.dp))
                    WheelPicker(
                        items = (0..23).toList(),
                        selected = selected,
                        onSelectedChange = { selected = it },
                        label = { it.toString().padStart(2, '0') },
                        infinite = true,
                    )
                    Box(Modifier.fillMaxWidth().height(2000.dp))
                }
            }
        }.use { scene ->
            scene.frames(4)
            scene.drag(
                from = androidx.compose.ui.geometry.Offset(200f, 260f),
                to = androidx.compose.ui.geometry.Offset(200f, 100f),
                steps = 20,
            )
            scene.frames(30)
        }

        assertEquals(
            0,
            scroll.value,
            "the page behind the wrapping drum scrolled to ${scroll.value}px " +
                "while the drum was being spun",
        )
    }
}
