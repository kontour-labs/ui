package io.kontour.ui.components.action

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import io.kontour.ui.foundation.GroupPosition
import io.kontour.ui.foundation.SystemIcons
import io.kontour.ui.foundation.shape
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A group of actions, and the three ways a builder-collected row goes wrong.
 *
 * The corners are the visual point and the goldens cover those. What a golden
 * cannot see is whether each button kept its *own* callback, whether disabling
 * reaches all of them, and which end the first one is at when the reader starts
 * from the right.
 */
@OptIn(ExperimentalTestApi::class)
class ButtonGroupTest {

    private val icon = SystemIcons.Plus

    /**
     * Each action fires its own callback.
     *
     * The builder collects lambdas into a list and replays them during layout.
     * Capturing the loop variable rather than the action's own closure gives
     * every button the last action's callback — a group where all three buttons
     * zoom in, which looks completely correct until you press the first one.
     */
    @Test
    fun everyActionFiresItsOwnCallback() = runComposeUiTest {
        val pressed = mutableListOf<String>()
        setContent {
            KontourTheme {
                ButtonGroup {
                    item(onClick = { pressed += "out" }, contentDescription = "Zoom out", icon = icon)
                    item(onClick = { pressed += "centre" }, contentDescription = "Recentre", icon = icon)
                    item(onClick = { pressed += "in" }, contentDescription = "Zoom in", icon = icon)
                }
            }
        }

        onNodeWithContentDescription("Zoom out").performClick()
        onNodeWithContentDescription("Zoom in").performClick()
        onNodeWithContentDescription("Recentre").performClick()

        assertEquals(listOf("out", "in", "centre"), pressed)
    }

    /** Disabling the group disables every button in it. */
    @Test
    fun disablingTheGroupReachesEveryAction() = runComposeUiTest {
        setContent {
            KontourTheme {
                ButtonGroup(enabled = false) {
                    item(onClick = {}, contentDescription = "Zoom out", icon = icon)
                    item(onClick = {}, contentDescription = "Zoom in", icon = icon)
                }
            }
        }

        onNodeWithContentDescription("Zoom out").assertIsNotEnabled()
        onNodeWithContentDescription("Zoom in").assertIsNotEnabled()
    }

    /**
     * One action can be unavailable while the rest are not.
     *
     * Zoom in at maximum zoom, with the rest of the cluster still live. A group
     * that can only be disabled wholesale forces the caller to hide the button
     * instead, and a cluster that changes width as you use it is worse than a
     * greyed one.
     */
    @Test
    fun oneActionDisablesWithoutTheOthers() = runComposeUiTest {
        setContent {
            KontourTheme {
                ButtonGroup {
                    item(onClick = {}, contentDescription = "Zoom out", icon = icon)
                    item(onClick = {}, contentDescription = "Zoom in", icon = icon, enabled = false)
                }
            }
        }

        onNodeWithContentDescription("Zoom out").assertIsEnabled()
        onNodeWithContentDescription("Zoom in").assertIsNotEnabled()
    }

    /**
     * The first action leads in whichever direction the reader starts from.
     *
     * A `Row` already handles this; what does not, if it is built with left and
     * right, is the corner shaping. The shape is `topEnd`/`bottomEnd` rather
     * than `topRight`/`bottomRight` for exactly this, and the two are
     * indistinguishable in English. This pins the layout half so a future change
     * to hard-coded corners has something arguing with it.
     */
    @Test
    fun theFirstActionLeadsInBothDirections() = runComposeUiTest {
        setContent {
            KontourTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    ButtonGroup(Modifier.testTag("group")) {
                        item(onClick = {}, contentDescription = "First", icon = icon)
                        item(onClick = {}, contentDescription = "Last", icon = icon)
                    }
                }
            }
        }

        val first = onNodeWithContentDescription("First").getUnclippedBoundsInRoot()
        val last = onNodeWithContentDescription("Last").getUnclippedBoundsInRoot()

        assertTrue(
            first.left > last.left,
            "under RTL the first action should sit to the right of the last, " +
                "but first is at ${first.left} and last at ${last.left}",
        )
    }

    /**
     * Down a column the rule turns on its side: the first button squares its
     * bottom corners and the last its top ones, and nothing squares a side edge.
     */
    @Test
    fun aVerticalGroupSquaresTheCornersFacingUpAndDown() {
        val round = RoundedCornerShape(12.dp)
        val square = CornerSize(ButtonGroupDefaults.InnerCorner)
        val rounded = CornerSize(12.dp)

        val first = GroupPosition.First.shape(round, ButtonGroupDefaults.InnerCorner, Orientation.Vertical)
        assertEquals(
            listOf(rounded, rounded, square, square),
            listOf(first.topStart, first.topEnd, first.bottomEnd, first.bottomStart),
            "the top button of a vertical group rounds its top and squares its bottom",
        )
        val last = GroupPosition.Last.shape(round, ButtonGroupDefaults.InnerCorner, Orientation.Vertical)
        assertEquals(
            listOf(square, square, rounded, rounded),
            listOf(last.topStart, last.topEnd, last.bottomEnd, last.bottomStart),
            "the bottom button of a vertical group squares its top and rounds its bottom",
        )
        val across = GroupPosition.First.shape(round, ButtonGroupDefaults.InnerCorner, Orientation.Horizontal)
        assertEquals(
            listOf(rounded, square, square, rounded),
            listOf(across.topStart, across.topEnd, across.bottomEnd, across.bottomStart),
            "and a row is unchanged: the first button squares the corners facing its neighbour",
        )
    }

    /**
     * Every button in a column takes the widest one's width, and the first is on
     * top whichever way the screen reads.
     */
    @Test
    fun aVerticalGroupIsOneWidthAndReadsTopDownInBothDirections() = runComposeUiTest {
        setContent {
            KontourTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    VerticalButtonGroup {
                        item(onClick = {}) { +"Go" }
                        item(onClick = {}) { +"Plan a longer journey" }
                    }
                }
            }
        }

        // The merged tree, so each is the button the text is in rather than the text.
        val short = onNodeWithText("Go").getUnclippedBoundsInRoot()
        val long = onNodeWithText("Plan a longer journey").getUnclippedBoundsInRoot()
        assertEquals(
            long.width, short.width,
            "a vertical group's buttons should all be as wide as the widest",
        )
        assertTrue(
            short.top < long.top,
            "the first action should be on top, and it is at ${short.top} against ${long.top}",
        )
    }
}
