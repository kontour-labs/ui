package io.kontour.ui.catalog

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerType
import io.kontour.ui.components.datetime.WheelPicker
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The drum turns under a mouse as well as under a thumb.
 *
 * A `LazyColumn` is dragged by touch and, on desktop, only by the wheel — which
 * is the right convention for a list and the wrong one for a *drum*. Nobody has
 * ever set a time by scrolling a picker with a mouse wheel; they grab it.
 *
 * The second case here is the other half of that: what the drum must *not* do.
 */
class WheelPickerDragTest {

    @Test
    fun aMouseDragTurnsTheWheel() {
        var selected by mutableIntStateOf(12)
        var bounds = Rect.Zero

        Scene(width = 400, height = 400) {
            Box(Modifier.fillMaxSize()) {
                WheelPicker(
                    items = (0..23).toList(),
                    selected = selected,
                    onSelectedChange = { selected = it },
                    label = { it.toString().padStart(2, '0') },
                    modifier = Modifier.reportBounds { bounds = it },
                )
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
            scene.frames(6)
        }

        assertTrue(
            selected < 12,
            "a mouse drag downward left the wheel on $selected — it did not turn",
        )
    }

    @Test
    fun aTouchDragStillTurnsItOnce() {
        // The list already handles touch. An outer drag that also handled it
        // would turn the drum twice as far as the finger moved.
        var selected by mutableIntStateOf(12)
        var bounds = Rect.Zero

        Scene(width = 400, height = 400) {
            Box(Modifier.fillMaxSize()) {
                WheelPicker(
                    items = (0..23).toList(),
                    selected = selected,
                    onSelectedChange = { selected = it },
                    label = { it.toString().padStart(2, '0') },
                    itemHeight = androidx.compose.ui.unit.Dp(40f),
                    modifier = Modifier.reportBounds { bounds = it },
                )
            }
        }.use { scene ->
            scene.frames(4)
            // Two rows' worth: 80dp at this density is 160px, and the wheel is
            // 40dp a row.
            scene.drag(
                from = bounds.alongY(0.5f),
                to = androidx.compose.ui.geometry.Offset(bounds.center.x, bounds.center.y + 160f),
                steps = 20,
            )
            scene.frames(8)
        }

        assertTrue(
            selected in 9..11,
            "a two-row touch drag moved the wheel from 12 to $selected — it " +
                "should have travelled about two rows, and anything further " +
                "means the drag is being handled twice",
        )
    }
}

/**
 * A drum's scrolling stays in the drum.
 *
 * A `LazyColumn` is a nested-scroll child by default, so whatever its fling
 * behaviour has not consumed goes up to the nearest scrollable ancestor. A wheel
 * holds twelve or twenty-four rows with padding at each end, so a flick reaches
 * an end routinely rather than exceptionally — and the leftover went into the
 * page behind it. Reported as "when I finish scrolling a wheel, the velocity
 * continues into the lazy list".
 *
 * ### Measured on the page, not on the wheel
 *
 * The wheel's own behaviour is unchanged and is not what was wrong. What is
 * asserted is that the *page* did not move, which is the complaint — so the
 * wheel sits inside a scrolling column exactly as it does in the catalog and in
 * any settings screen, and the column's offset is read afterwards.
 */
class WheelPickerContainmentTest {

    @Test
    fun aWheelDoesNotScrollThePageBehindIt() {
        val page = pageOffsetAfterSpinning()
        assertTrue(
            page == 0,
            "the page behind the wheel scrolled to ${page}px while the wheel was " +
                "being spun — the drum's leftover velocity is escaping into it",
        )
    }

    @Test
    fun thePageStillScrollsWhenItIsTheOneBeingDragged() {
        // The control. A boundary that swallowed everything would pass the test
        // above and break the screen.
        val page = pageOffsetAfterSpinning(onTheWheel = false)
        assertTrue(
            page > 0,
            "a drag below the wheel left the page at ${page}px — the containment " +
                "is eating scrolls that were never the wheel's",
        )
    }

    /** Spins the drum (or the page below it) and reports where the page ended up. */
    private fun pageOffsetAfterSpinning(onTheWheel: Boolean = true): Int {
        var selected by mutableIntStateOf(12)
        lateinit var scroll: ScrollState
        var wheel = Rect.Zero

        Scene(width = 400, height = 900) {
            val state = rememberScrollState()
            scroll = state
            Column(Modifier.fillMaxSize().verticalScroll(state)) {
                WheelPicker(
                    items = (0..23).toList(),
                    selected = selected,
                    onSelectedChange = { selected = it },
                    label = { it.toString().padStart(2, '0') },
                    modifier = Modifier.reportBounds { wheel = it },
                )
                // A landing strip below the wheel: somewhere to start a drag
                // that is plainly the page's, and enough height that the page
                // has room to move.
                Box(Modifier.fillMaxWidth().height(600.dp))
            }
        }.use { scene ->
            scene.frames(4)
            val at = if (onTheWheel) {
                wheel.center
            } else {
                // Below the drum, on the strip, and comfortably inside the scene.
                Offset(wheel.center.x, wheel.bottom + 60f)
            }
            // Hard and fast, to the top of the drum's travel and past it.
            scene.drag(
                from = at,
                to = Offset(at.x, at.y - 320f),
                steps = 6,
                pointer = PointerType.Touch,
            )
            scene.frames(90)
        }
        return scroll.value
    }
}
