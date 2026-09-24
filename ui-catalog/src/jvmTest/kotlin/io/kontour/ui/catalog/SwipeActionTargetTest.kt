package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import io.kontour.ui.components.list.SwipeAction
import io.kontour.ui.components.list.SwipeActions
import io.kontour.ui.theme.ComponentDefaults
import io.kontour.ui.components.list.SwipeValue
import io.kontour.ui.components.list.rememberSwipeActionsState
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Trash
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Spacing
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A revealed swipe action is tappable everywhere it is painted.
 *
 * It was not. The button inside the 88dp panel wrapped its icon and label —
 * about 40dp of it — and sat centred, so the coloured strip down each side was
 * inert. A thumb landing near the edge of something that is obviously a button
 * did nothing at all, which reads as the swipe having failed rather than as the
 * target being narrower than the paint.
 *
 * Tested by tapping 2dp inside the panel rather than by measuring a node: what
 * matters is whether the action runs, and that is the same question the user is
 * asking.
 */
class SwipeActionTargetTest {

    @Test
    fun tappingTheEdgeOfARevealedActionRunsIt() {
        var fired = 0
        var row = Rect.Zero

        val scene = ImageComposeScene(width = 600, height = 160, density = Density(2f)) {
            KontourTheme(darkTheme = false, reduceMotion = true) {
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    SwipeActions(
                        // Revealed from the first frame. `animateTo` would need
                        // frames to travel in and this test taps on frame four.
                        state = rememberSwipeActionsState(initialValue = SwipeValue.End),
                        end = listOf(
                            SwipeAction(
                                label = "Delete",
                                icon = Tabler.Outline.Trash,
                                onAction = { fired++ },
                                background = Color(0xFFB3261E),
                            ),
                        ),
                        modifier = Modifier.onGloballyPositioned {
                            row = Rect(it.positionInRoot(), it.size.toSize())
                        },
                    ) {
                        Box(Modifier.fillMaxWidth().height(48.dp).background(Color.White))
                    }
                }
            }
        }

        try {
            repeat(4) { scene.render(16_000_000L * it) }

            assertTrue(row.width > 0f, "the row never reported a size")
            // The one action is pinned to the trailing edge, 88dp wide.
            // `SwipeActionsDefaults.ActionWidth` is a theme read now, so it
            // cannot be asked outside composition. The theme's own defaults are
            // plain data and are what the component resolves to here.
            val slotLeft = row.right - with(Density(2f)) {
                ComponentDefaults().swipeActionWidth.toPx()
            }
            // The button sits half a gap inside its slot, so the page shows
            // between it and whatever is next to it.
            val panelLeft = slotLeft + with(Density(2f)) { Spacing().xs.toPx() } / 2f
            val tap = Offset(panelLeft + Inset, row.center.y)

            scene.sendPointerEvent(PointerEventType.Press, tap, type = PointerType.Touch)
            scene.render(64_000_000L)
            scene.sendPointerEvent(PointerEventType.Release, tap, type = PointerType.Touch)
            scene.render(80_000_000L)

            assertTrue(
                fired == 1,
                "tapping ${Inset}px inside the left edge of the revealed action " +
                    "(panel starts at ${panelLeft}px, row is ${row.width}px wide) " +
                    "ran it $fired times — the touch target is narrower than the " +
                    "panel it is painted on",
            )
        } finally {
            scene.close()
        }
    }

    /**
     * A tap on the row itself, while it shows its actions, puts it back — and is not
     * also a tap on whatever the row opens.
     */
    @Test
    fun tappingARevealedRowClosesItWithoutClickingIt() {
        var clicked = 0
        var fired = 0
        var state: io.kontour.ui.components.list.SwipeActionsState? = null

        Scene(width = 600, height = 160, reduceMotion = true) {
            val swipe = rememberSwipeActionsState(initialValue = SwipeValue.End)
            state = swipe
            Box(Modifier.fillMaxSize().background(Color.White)) {
                SwipeActions(
                    state = swipe,
                    end = listOf(
                        SwipeAction(
                            label = "Delete",
                            icon = Tabler.Outline.Trash,
                            onAction = { fired++ },
                            background = Color(0xFFB3261E),
                        ),
                    ),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .background(Color.White)
                            .clickable { clicked++ }
                    )
                }
            }
        }.use { scene ->
            scene.frames(4)
            // The row has slid 88dp toward the start; its visible part is on the left.
            scene.tap(Offset(100f, 48f))
            scene.frames(30)
        }

        assertTrue(clicked == 0, "the tap closed nothing and opened the row instead ($clicked clicks)")
        assertTrue(fired == 0, "a tap on the row ran the action")
        assertTrue(
            requireNotNull(state).currentValue == SwipeValue.Resting,
            "a tap on a row showing its actions left it at ${state?.currentValue}",
        )
    }

    private companion object {
        /** 2dp at the test's density. Inside the paint, outside the old target. */
        const val Inset = 4f
    }
}
