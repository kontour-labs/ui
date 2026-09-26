package io.kontour.ui.overlay

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import io.kontour.ui.foundation.Text
import io.kontour.ui.motion.BackStyle
import io.kontour.ui.motion.LocalBackStyle
import io.kontour.ui.sheet.ModalSideSheet
import io.kontour.ui.sheet.SheetSide
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * An overlay follows a back gesture in the feel of its platform, and holds
 * still under reduced motion.
 *
 * Measured by where the panel's content lands on screen — its bounds in the
 * root, which carry the layer's transform — at rest and halfway through a
 * gesture, so what is asserted is what the reader sees move.
 */
@OptIn(ExperimentalTestApi::class)
class OverlayBackMotionTest {

    private class Back {
        val dispatcher = NavigationEventDispatcher()
        val input = DirectNavigationEventInput().also { dispatcher.addInput(it) }
        val owner = object : NavigationEventDispatcherOwner {
            override val navigationEventDispatcher = dispatcher
        }

        fun dragTo(progress: Float) {
            input.backStarted(NavigationEvent(NavigationEvent.EDGE_LEFT, 0f, 0f, 0f))
            input.backProgressed(NavigationEvent(NavigationEvent.EDGE_LEFT, progress, 0f, 0f))
        }
    }

    private fun ComposeUiTest.bounds(tag: String): Rect {
        waitForIdle()
        return onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    }

    @Composable
    private fun Harness(
        back: Back,
        style: BackStyle,
        reduceMotion: Boolean = false,
        content: @Composable () -> Unit,
    ) {
        CompositionLocalProvider(
            LocalNavigationEventDispatcherOwner provides back.owner,
            LocalBackStyle provides style,
        ) {
            KontourTheme(darkTheme = false, reduceMotion = reduceMotion) {
                OverlayHost(Modifier.size(800.dp, 600.dp)) {
                    content()
                }
            }
        }
    }

    @Composable
    private fun AlertDialog(dismissible: Boolean) {
        Dialog(visible = true, onDismissRequest = {}, dismissible = dismissible) {
            Text("Delete this trip?", Modifier.size(200.dp, 80.dp).testTag("panel"))
        }
    }

    @Test
    fun underPredictiveBackTheDialogShrinksWhereItIs() = runComposeUiTest {
        val back = Back()
        setContent { Harness(back, BackStyle.Predictive) { AlertDialog(dismissible = true) } }
        val rest = bounds("panel")
        runOnUiThread { back.dragTo(0.5f) }
        val mid = bounds("panel")
        assertEquals(0.95f, mid.width / rest.width, 0.01f, "halfway through, the dialog is not at 95%")
        assertEquals(rest.center.x, mid.center.x, 1f, "the dialog drifted instead of shrinking in place")
        assertEquals(rest.center.y, mid.center.y, 1f, "the dialog drifted instead of shrinking in place")
    }

    @Test
    fun underSwipeBackTheDialogOnlyFades() = runComposeUiTest {
        val back = Back()
        setContent { Harness(back, BackStyle.Swipe) { AlertDialog(dismissible = true) } }
        val rest = bounds("panel")
        runOnUiThread { back.dragTo(0.5f) }
        assertEquals(rest, bounds("panel"), "UIKit never drags an alert; the dialog moved")
    }

    @Test
    fun aDialogThatMayNotCloseGivesALittleAndNoMore() = runComposeUiTest {
        val back = Back()
        setContent { Harness(back, BackStyle.Predictive) { AlertDialog(dismissible = false) } }
        val rest = bounds("panel")
        runOnUiThread { back.dragTo(1f) }
        val full = bounds("panel")
        val shrink = 1f - full.width / rest.width
        assertTrue(shrink > 0f, "a dialog that refuses back did not move at all, so the hand is not told it was heard")
        assertTrue(shrink <= 0.02f, "a dialog that refuses back gave ${shrink * 100}%, more than the 2% nudge")
    }

    @Test
    fun underSwipeBackASideSheetFollowsTheFingerToItsEdge() = runComposeUiTest {
        val back = Back()
        setContent {
            Harness(back, BackStyle.Swipe) {
                ModalSideSheet(visible = true, onDismissRequest = {}, side = SheetSide.End) {
                    Text("Filters", Modifier.fillMaxSize().testTag("panel"))
                }
            }
        }
        val rest = bounds("panel")
        runOnUiThread { back.dragTo(0.5f) }
        val mid = bounds("panel")
        assertTrue(mid.left > rest.left, "the sheet did not move towards its edge")
        assertEquals(rest.width, mid.width, 1f, "under swipe back the sheet moves; it does not shrink")
    }

    @Test
    fun underReducedMotionNothingMoves() = runComposeUiTest {
        val back = Back()
        setContent {
            Harness(back, BackStyle.Predictive, reduceMotion = true) {
                ModalSideSheet(visible = true, onDismissRequest = {}, side = SheetSide.End) {
                    Text("Filters", Modifier.fillMaxSize().testTag("panel"))
                }
            }
        }
        val rest = bounds("panel")
        runOnUiThread { back.dragTo(0.5f) }
        assertEquals(rest, bounds("panel"), "reduced motion moved the sheet")
    }
}
