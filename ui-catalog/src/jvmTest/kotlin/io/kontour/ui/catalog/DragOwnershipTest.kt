package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.selection.SegmentedControl
import io.kontour.ui.theme.KontourTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A drag that starts off-axis still belongs to the control it started on.
 *
 * Reported for a fourth time: *"if you start dragging them, but move your
 * cursor/finger far enough away… they stop being selected"*. Round 25 diagnosed
 * it, wrote [io.kontour.ui.interaction.horizontalDragOwning] for it, applied it
 * to `Slider` and `RangeSlider`, and covered those two with
 * `SliderDragOwnershipTest`. That test passes. So does the same gesture applied
 * to every other draggable control in the library — and the reporter still sees
 * the defect, which means the gesture in the test is not the gesture in the hand.
 *
 * ### The gesture in the test was the wrong one
 *
 * `SliderDragOwnershipTest` presses, moves **along** the axis, and only then
 * strays across it. That ordering decides the outcome before the stray happens:
 * the along-axis movement passes the control's touch slop, the control consumes,
 * and in current Compose `PointerInputChange.consume()` takes the whole change
 * rather than one axis of it — so the scroller above never sees anything and can
 * never come back for it. Written that way, the test passes on controls that are
 * demonstrably broken.
 *
 * The gesture in the hand starts off-axis. Press, and move away — one motion,
 * one direction, whatever direction the finger happened to take.
 *
 * ### The number, measured in a browser on the reporter's platform
 *
 * The built site at 390x844 with touch emulation, `#/components/slider`, a
 * synthetic 20-step drag from a control 40px to the right and a varying distance
 * down, screenshot compared against resting:
 *
 * | Down | Slope | Outcome |
 * |---|---|---|
 * | 0px | 0.00 | control tracks |
 * | 10px | 0.25 | control tracks |
 * | 20px | 0.50 | control tracks |
 * | 40px | **1.00** | **gesture lost** |
 * | 60px | 1.50 | gesture lost |
 * | 100px | 2.50 | gesture lost |
 *
 * The threshold is a slope of one — forty-five degrees — which is not a distance
 * at all. It is a race: both the control and the scroller above it are waiting
 * for touch slop on their own axis, and whichever axis crosses first claims the
 * gesture. Steeper than 45° the scroller gets there first, and everything after
 * that belongs to it.
 *
 * The same sweep run against `Slider` on the same page, in the same scroller,
 * under the same synthetic events, produced byte-identical screenshots at every
 * slope up to 2.3. `Slider` claims on the down and has no slop to lose a race
 * with, which is what rules out the browser as the thief and names the race.
 *
 * ### Why this covers one component and not eight
 *
 * The race is only lost when the control's axis differs from the scroller's. A
 * vertical drag inside a vertical scroller is decided by depth rather than by
 * slope — the child is asked first and reaches the same threshold at the same
 * event — so `Scrollbar` and `PullToRefresh` are not exposed to it.
 *
 * **`Toast` was on that list and came off it in Round 28**, and not because the
 * claim was wrong. It was right for as long as a toast dragged on one axis. A
 * toast now dismisses toward its anchored edge *or* sideways, and the sideways
 * half is a horizontal drag inside a vertical list — the race this file is
 * about. It owns its drag now, and `ToastSwipeTest` carries the sweep.
 *
 * That leaves the horizontal controls inside the site's vertical list, and most
 * of those should keep losing. A `Switch`, a `TabBar` swipe and a `Carousel` are
 * all things you tap or flick past, and every platform lets a diagonal drag from
 * one scroll the page instead — iOS does it, Android does it, and a library that
 * did otherwise would feel stuck. `Carousel` declines the gesture on purpose and
 * has a KDoc saying so.
 *
 * `SegmentedControl` is the one that should not lose, for the same reason
 * `Slider` should not: the press is already a value change. It selects the
 * segment under the finger on the down, its own KDoc opens with "drag across the
 * segments and the thumb comes with you", and a control that hands the gesture
 * away at 45° does that for a third of the directions a finger can take.
 */
class DragOwnershipTest {

    /**
     * The demo layout the report came from, near enough to matter.
     *
     * The docs site puts every demo inside a `LazyColumn` (`Site.kt:578`), not
     * the `verticalScroll` `SliderDragOwnershipTest` uses. Both scroll; they are
     * not the same code, and this round's subject is tests that pass on a shape
     * the user does not have. The filler below is tall enough that the list
     * genuinely can scroll — a scroller with nowhere to go declines the gesture
     * and the test would pass for the wrong reason.
     */
    private fun scene(content: @Composable () -> Unit) = Scene(width = 500, height = 900) {
        KontourTheme {
            LazyColumn(Modifier.fillMaxSize().background(Color.White).padding(20.dp)) {
                item { content() }
                item { Box(Modifier.fillMaxWidth().height(2000.dp)) }
            }
        }
    }

    @Test
    fun aSegmentedControlKeepsADragThatStartsOffAxis() {
        var selected by mutableStateOf(0)
        var bounds = Rect.Zero

        scene {
            SegmentedControl(
                options = listOf("One", "Two", "Three"),
                selected = selected,
                onSelectedChange = { selected = it },
                // Narrow, so a drag steeper than 45° still fits the scene.
                modifier = Modifier.width(150.dp).reportBounds { bounds = it },
            )
        }.use { scene ->
            scene.frames(3)

            // From the middle of the first segment to the middle of the third.
            val from = Offset(bounds.left + bounds.width / 6f, bounds.center.y)
            val along = bounds.width * 2f / 3f
            // Twice the along distance, so the gesture is unambiguously past the
            // 45° threshold the browser measured rather than sitting on it.
            val stray = along * 2f

            scene.drag(
                from = from,
                to = Offset(from.x + along, from.y + stray),
                steps = 20,
            )
            scene.frames(2)

            assertEquals(
                2,
                selected,
                "a drag from the first segment to the third at ${
                    "%.1f".format(stray / along)
                }:1 down-to-along left the thumb on segment $selected. The " +
                    "`LazyColumn` above it passed its vertical slop before the " +
                    "control passed its horizontal one and took the gesture; " +
                    "the same drag straight along the axis works, which is why " +
                    "nothing caught this.",
            )
        }
    }

    @Test
    fun aSegmentedControlStillWorksDraggedStraightAlongItsAxis() {
        // The control for the test above. If this one ever fails, the fix has
        // broken the ordinary gesture rather than added the awkward one, and the
        // failure above would be meaningless on its own.
        var selected by mutableStateOf(0)
        var bounds = Rect.Zero

        scene {
            SegmentedControl(
                options = listOf("One", "Two", "Three"),
                selected = selected,
                onSelectedChange = { selected = it },
                modifier = Modifier.width(150.dp).reportBounds { bounds = it },
            )
        }.use { scene ->
            scene.frames(3)

            val from = Offset(bounds.left + bounds.width / 6f, bounds.center.y)
            scene.drag(
                from = from,
                to = Offset(from.x + bounds.width * 2f / 3f, from.y),
                steps = 20,
            )
            scene.frames(2)

            assertEquals(
                2,
                selected,
                "dragging straight from the first segment to the third left the " +
                    "thumb on segment $selected",
            )
        }
    }

    @Test
    fun aDragOnTheScrollersOwnAxisIsDecidedByDepthRatherThanByAngle() {
        // The measurement behind changing six components: nothing.
        //
        // `Scrollbar` and `PullToRefresh` drag along the same axis the page
        // scrolls on, and the argument for leaving them alone is that there is
        // no race to lose — both are waiting for the same threshold on the same
        // axis, and the child is asked first. (`Toast` was here too until it
        // grew a sideways dismiss; see the note above.) That is a claim about
        // Compose's arbitration rather than about the library, so it is measured
        // here rather than asserted in a comment; if a Compose upgrade changes
        // it, this says so instead of the next reporter.
        //
        // The gesture is the mirror of the failing one: twice as far across the
        // control's axis as along it, which is what took the segmented control's
        // drag away.
        var travelled = 0f
        var bounds = Rect.Zero

        scene {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .background(Color.Gray)
                    .reportBounds { bounds = it }
                    .draggable(
                        state = rememberDraggableState { travelled += it },
                        orientation = Orientation.Vertical,
                    )
            )
        }.use { scene ->
            scene.frames(3)

            val along = 200f
            scene.drag(
                from = bounds.center,
                to = Offset(bounds.center.x + along * 2f, bounds.center.y + along),
                steps = 20,
            )
            scene.frames(2)

            // Not the whole 200: `draggable` swallows movement up to its own
            // touch slop before it starts reporting. What matters is that most
            // of the drag arrived rather than none of it.
            assertTrue(
                travelled > along * 0.8f,
                "a vertical drag two-to-one off its own axis reported " +
                    "${travelled}px of ${along}px. On the scroller's own axis " +
                    "the child is asked first and claims at the same threshold, " +
                    "so this should be nearly all of it — if it is nearly none, " +
                    "the exemption for `Scrollbar` and `PullToRefresh` " +
                    "no longer holds and they need the owning drag too.",
            )
            assertTrue(
                abs(travelled) < along * 1.2f,
                "the drag reported ${travelled}px for a ${along}px gesture, " +
                    "which is more movement than the finger made",
            )
        }
    }
}

