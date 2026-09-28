package io.kontour.ui.overlay

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import io.kontour.ui.sheet.ModalBottomSheet
import io.kontour.ui.sheet.ModalSideSheet
import io.kontour.ui.sheet.SheetSide
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A back gesture moves an overlay only by the animation it already has — its
 * own appearance run backwards, its own slide, a sheet's own offset — and only
 * when it may be dismissed: under predictive back, or under swipe back for the
 * one overlay iOS swipes away, a side sheet.
 *
 * Measured by where the panel's content lands on screen — its bounds in the
 * root, which carry every layer's transform and every placement offset — at
 * rest and halfway through a gesture, so what is asserted is what the reader
 * sees move.
 */
@OptIn(ExperimentalTestApi::class)
class OverlayBackMotionTest {

    private class Back {
        val dispatcher = NavigationEventDispatcher()
        val input = DirectNavigationEventInput().also { dispatcher.addInput(it) }
        val owner = object : NavigationEventDispatcherOwner {
            override val navigationEventDispatcher = dispatcher
        }

        fun dragTo(progress: Float, edge: Int = NavigationEvent.EDGE_LEFT) {
            input.backStarted(NavigationEvent(edge, 0f, 0f, 0f))
            input.backProgressed(NavigationEvent(edge, progress, 0f, 0f))
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

    @Composable
    private fun FilterSheet() {
        ModalSideSheet(visible = true, onDismissRequest = {}, side = SheetSide.End) {
            Text("Filters", Modifier.fillMaxSize().testTag("panel"))
        }
    }

    /**
     * Halfway back is halfway through the dialog's own exit: the 1.06 it
     * settles down from, halfway — 1.03 — about the same centre.
     */
    @Test
    fun underPredictiveBackTheDialogRunsItsOwnExitBackwards() = runComposeUiTest {
        val back = Back()
        setContent { Harness(back, BackStyle.Predictive) { AlertDialog(dismissible = true) } }
        val rest = bounds("panel")
        runOnUiThread { back.dragTo(0.5f) }
        val mid = bounds("panel")
        assertEquals(1.03f, mid.width / rest.width, 0.01f, "halfway back is not halfway through the dialog's own exit")
        assertEquals(rest.center.x, mid.center.x, 1f)
        assertEquals(rest.center.y, mid.center.y, 1f)

        // Abandoned: its own arrival brings it back.
        runOnUiThread { back.input.backCancelled() }
        assertEquals(rest, bounds("panel"), "an abandoned gesture left the dialog part-way out")
    }

    @Test
    fun underPredictiveBackTheSideSheetSlidesOutByItsOwnSlide() = runComposeUiTest {
        val back = Back()
        setContent { Harness(back, BackStyle.Predictive) { FilterSheet() } }
        val rest = bounds("panel")
        runOnUiThread { back.dragTo(0.5f) }
        val mid = bounds("panel")
        assertEquals(rest.left + rest.width / 2, mid.left, rest.width * 0.03f, "halfway back is half the sheet's slide")
        assertEquals(rest.width, mid.width, 1f, "the sheet slides; it does not shrink")
    }

    @Test
    fun underPredictiveBackTheBottomSheetMovesTowardsHidden() = runComposeUiTest {
        val back = Back()
        setContent {
            Harness(back, BackStyle.Predictive) {
                ModalBottomSheet(visible = true, onDismissRequest = {}) {
                    Text("Departures", Modifier.fillMaxWidth().height(300.dp).testTag("panel"))
                }
            }
        }
        val rest = bounds("panel")
        runOnUiThread { back.dragTo(0.5f) }
        val mid = bounds("panel")
        assertTrue(mid.top > rest.top + 100f, "the sheet did not move down towards hidden: ${rest.top} to ${mid.top}")
        assertEquals(rest.height, mid.height, 1f, "the sheet moves; it does not shrink")

        runOnUiThread { back.input.backCancelled() }
        assertEquals(rest.top, bounds("panel").top, 1f, "an abandoned gesture did not settle the sheet back")
    }

    @Test
    fun aDialogThatMayNotCloseDoesNotMove() = runComposeUiTest {
        val back = Back()
        setContent { Harness(back, BackStyle.Predictive) { AlertDialog(dismissible = false) } }
        val rest = bounds("panel")
        runOnUiThread { back.dragTo(1f) }
        assertEquals(rest, bounds("panel"), "a dialog that may not close moved under back")
    }

    /** Swipe back is iOS's, where back is not a way out of an overlay at all. */
    @Test
    fun underSwipeBackNothingMoves() = runComposeUiTest {
        val back = Back()
        setContent {
            Harness(back, BackStyle.Swipe) {
                AlertDialog(dismissible = true)
            }
        }
        val rest = bounds("panel")
        runOnUiThread { back.dragTo(0.5f) }
        assertEquals(rest, bounds("panel"), "a dialog moved under swipe back")
    }

    /**
     * iOS swipes a side sheet away, towards its own edge: a sheet on the right
     * follows a swipe from the left edge, whose finger moves the way the sheet
     * goes — by its own slide — and not one from the right.
     */
    @Test
    fun underSwipeBackASideSheetFollowsOnlyAGestureTowardsItsEdge() = runComposeUiTest {
        val back = Back()
        setContent { Harness(back, BackStyle.Swipe) { FilterSheet() } }
        val rest = bounds("panel")
        runOnUiThread { back.dragTo(0.5f, NavigationEvent.EDGE_RIGHT) }
        assertEquals(rest, bounds("panel"), "a swipe away from the sheet's edge moved it")
        runOnUiThread { back.input.backCancelled() }

        runOnUiThread { back.dragTo(0.5f) }
        val mid = bounds("panel")
        assertEquals(rest.left + rest.width / 2, mid.left, rest.width * 0.03f, "halfway is half the sheet's own slide")
    }

    /** Under reduced motion the dialog's own exit only fades, so the gesture only fades it. */
    @Test
    fun underReducedMotionTheDialogDoesNotMove() = runComposeUiTest {
        val back = Back()
        setContent { Harness(back, BackStyle.Predictive, reduceMotion = true) { AlertDialog(dismissible = true) } }
        val rest = bounds("panel")
        runOnUiThread { back.dragTo(0.5f) }
        assertEquals(rest, bounds("panel"), "reduced motion moved the dialog")
    }
}
