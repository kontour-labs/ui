package io.kontour.ui.dragdrop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import io.kontour.ui.input.InputModality
import io.kontour.ui.input.LocalInputModality
import io.kontour.ui.interaction.FeedbackDispatcher
import io.kontour.ui.interaction.FeedbackIntent
import io.kontour.ui.interaction.LocalFeedback
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * A thing carried along a list that rides its things — a strip of document
 * tabs — stays on the list's line while the pointer wanders, comes away when
 * the pointer is pulled clear, and boards another such list it is carried into.
 * Each is felt once: a threshold passed coming away, and the softer way back
 * boarding again.
 */
@OptIn(ExperimentalTestApi::class)
class DragTearOffTest {

    private lateinit var host: DragHost
    private lateinit var top: DropListState<String>
    private lateinit var bottom: DropListState<String>
    private val felt = mutableListOf<FeedbackIntent>()
    private var tearOff = 0f

    /** Whether the strips are never left, as a document strip that keeps its own tabs is. */
    private var endless = false

    private fun ComposeUiTest.strips(direction: LayoutDirection = LayoutDirection.Ltr) {
        setContent {
            KontourTheme {
                CompositionLocalProvider(
                    LocalFeedback provides FeedbackDispatcher { felt += it },
                    LocalInputModality provides InputModality.Touch,
                    LocalLayoutDirection provides direction,
                ) {
                    OverlayHost {
                        host = LocalDragHost.current!!
                        tearOff = with(LocalDensity.current) { TearOff.toPx() }
                        Column {
                            top = strip("top", listOf("A", "B", "C"))
                            Spacer(Modifier.height(200.dp))
                            bottom = strip("bottom", listOf("D", "E"))
                        }
                    }
                }
            }
        }
        waitForIdle()
    }

    @Composable
    private fun strip(tag: String, items: List<String>): DropListState<String> {
        val scroll = rememberLazyListState()
        val state = rememberDropListState(
            listState = scroll,
            type = Tab,
            items = items,
            key = { it },
            label = tag,
            itemLabel = { it },
            onDrop = {},
        )
        state.tearOff = if (endless) Float.POSITIVE_INFINITY else tearOff
        LazyRow(state = scroll, modifier = Modifier.size(360.dp, 48.dp).testTag(tag).dropList(state)) {
            dragItems(state) { _, item -> Box(Modifier.width(96.dp).height(48.dp).testTag(item)) }
        }
        return state
    }

    @Test
    fun aWobbleUnderTheTearOffKeepsTheThingOnTheLine() = runComposeUiTest {
        strips()
        onNodeWithTag("A").performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            advanceEventTime(16)
            moveBy(Offset(40f, 0f))
        }
        waitForIdle()
        val session = assertNotNull(host.session, "the hold did not pick the tab up, so this checks nothing")
        val line = session.pointer.y
        assertSame(top, session.riding, "a tab picked up from its strip should ride it")

        onNodeWithTag("A").performTouchInput {
            advanceEventTime(16)
            moveBy(Offset(30f, tearOff * 0.6f))
        }
        waitForIdle()
        assertSame(top, session.riding, "a wobble under the tear-off pulled the tab clear")
        // Held on the line, and drawn there: the pointer is still over the strip.
        assertEquals(line, session.pointer.y, "a wobble under the tear-off moved the tab off its strip's line")
        assertEquals(Offset.Zero, session.nudge, "a wobble over its strip drew the tab off the line")
        assertTrue(FeedbackIntent.DragThreshold !in felt, "a threshold was felt without one being passed: $felt")
        onNodeWithTag("A").performTouchInput { up() }
        waitForIdle()
    }

    @Test
    fun pulledClearItFollowsThePointerAndIsFeltOnce() = runComposeUiTest {
        strips()
        onNodeWithTag("A").performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            repeat(6) {
                advanceEventTime(16)
                moveBy(Offset(0f, tearOff * 0.75f))
            }
        }
        waitForIdle()
        val session = assertNotNull(host.session, "the hold did not pick the tab up, so this checks nothing")
        assertNull(session.riding, "pulled well past the tear-off, the tab still rides its strip")
        assertEquals(1, felt.count { it == FeedbackIntent.DragThreshold }, "coming away should be felt once: $felt")
        onNodeWithTag("A").performTouchInput { up() }
        waitForIdle()
    }

    @Test
    fun carriedIntoAnotherStripItBoardsIt() = runComposeUiTest {
        strips()
        val from = onNodeWithTag("A").fetchSemanticsNode().boundsInRoot.center
        val to = onNodeWithTag("bottom").fetchSemanticsNode().boundsInRoot.center
        onNodeWithTag("A").performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            repeat(12) { i ->
                advanceEventTime(16)
                moveTo(center + (to - from) * ((i + 1) / 12f))
            }
        }
        waitForIdle()
        val session = assertNotNull(host.session, "the hold did not pick the tab up, so this checks nothing")
        assertSame(bottom, session.riding, "a tab carried into another strip should ride that one")
        assertEquals(1, felt.count { it == FeedbackIntent.DragThreshold }, "coming away should be felt once: $felt")
        assertEquals(1, felt.count { it == FeedbackIntent.DragThresholdBack }, "boarding should be felt once: $felt")
        assertSame(bottom.target, session.target, "the bottom strip should be what the tab is over")
        onNodeWithTag("A").performTouchInput { up() }
        waitForIdle()
    }

    /**
     * A finger's wobble across the strip, out past its foot by less than the
     * hold, leaves the tab on the line and drawn there. It gave half of every
     * wobble, and a tab moved along its strip went out of line with the others.
     */
    @Test
    fun aFewDpOfWobblePastTheStripLeavesItOnTheLine() = runComposeUiTest {
        strips()
        val session = pickUpA()
        val line = session.pointer.y
        val at = session.pointer
        val foot = onNodeWithTag("top").fetchSemanticsNode().boundsInRoot.bottom
        val hold = with(density) { RideHold.toPx() }
        val drawn = mutableListOf<Float>()
        for ((along, across) in listOf(6.dp to 4.dp, 12.dp to (-4).dp, 18.dp to 3.dp, 24.dp to (-2).dp)) {
            fingerTo(at + Offset(with(density) { along.toPx() }, with(density) { across.toPx() }))
            drawn += session.drawn.y + session.grab.y - line
        }
        // And to just short of the hold past the strip's foot.
        fingerTo(Offset(at.x, foot + hold - 1f))
        drawn += session.drawn.y + session.grab.y - line
        assertSame(top, session.riding)
        assertEquals(line, session.pointer.y, "a wobble moved where the tab is held off its strip's line")
        assertTrue(drawn.all { it == 0f }, "a wobble drew the tab off its strip's line, by ${drawn.joinToString()} px")
        onNodeWithTag("top").performTouchInput { up() }
        waitForIdle()
    }

    /**
     * Pulled past the hold, it is drawn a little towards the pull, more slowly
     * the further it goes and never as far as the stretch, and still held on
     * the line, until the pull passes the tear-off.
     */
    @Test
    fun pulledTowardsTheTearOffItStretchesAndStaysOnTheLine() = runComposeUiTest {
        strips()
        val session = pickUpA()
        val line = session.pointer.y
        val x = session.pointer.x
        val foot = onNodeWithTag("top").fetchSemanticsNode().boundsInRoot.bottom
        val stretch = with(density) { RideStretch.toPx() }
        val given = mutableListOf<Float>()
        for (share in listOf(0.4f, 0.65f, 0.9f)) {
            fingerTo(Offset(x, foot + tearOff * share))
            assertSame(top, session.riding, "pulled ${share * 100}% of the tear-off past its strip, the tab came away")
            assertEquals(line, session.pointer.y, "pulled, the tab should still be held on the line")
            given += session.nudge.y
        }
        val steps = given.zipWithNext { a, b -> b - a }
        assertTrue(given.first() > 0f && steps.all { it > 0f }, "the stretch should grow with the pull: $given")
        assertTrue(steps[1] < steps[0], "the stretch should stiffen as it goes: $given")
        assertTrue(given.last() < stretch, "the stretch should stay short of its most, $stretch px: $given")
        assertTrue(FeedbackIntent.DragThreshold !in felt, "a threshold was felt without one being passed: $felt")
        fingerTo(Offset(x, foot + tearOff * 1.2f))
        assertNull(session.riding, "pulled past the tear-off, the tab should come away")
        onNodeWithTag("top").performTouchInput { up() }
        waitForIdle()
    }

    /**
     * A strip that is never left — a document strip whose tabs do not move
     * between strips — holds the tab on its line and draws it there, however
     * far down it is pulled.
     */
    @Test
    fun aStripThatIsNeverLeftHoldsItOnTheLineHoweverFarItIsPulled() = runComposeUiTest {
        endless = true
        strips()
        val session = pickUpA()
        val line = session.pointer.y
        for (down in listOf(30.dp, 120.dp, 400.dp)) {
            fingerTo(Offset(session.pointer.x, line + with(density) { down.toPx() }))
            assertSame(top, session.riding, "pulled ${down} down, the tab left a strip that is never left")
            assertEquals(line, session.pointer.y, "pulled ${down} down, the tab was held off its strip's line")
            assertEquals(Offset.Zero, session.nudge, "pulled ${down} down, the tab was drawn off its strip's line")
        }
        assertTrue(FeedbackIntent.DragThreshold !in felt, "a threshold was felt on a strip that is never left: $felt")
        onNodeWithTag("top").performTouchInput { up() }
        waitForIdle()
    }

    @Test
    fun carriedNearAnotherStripItIsTakenIn() = takenIn(LayoutDirection.Ltr)

    @Test
    fun carriedNearAnotherStripItIsTakenInRightToLeft() = takenIn(LayoutDirection.Rtl)

    /**
     * Carried towards the bottom strip, the tab is drawn towards its line from
     * outside its capture, and taken in from inside it, short of the strip
     * itself: here, near its far end, three quarters of the tear-off above it.
     * It was taken in only once the pointer was over the strip.
     */
    private fun takenIn(direction: LayoutDirection) = runComposeUiTest {
        strips(direction)
        val session = pickUpA()
        val start = session.pointer
        val strip = onNodeWithTag("bottom").fetchSemanticsNode().boundsInRoot
        val capture = tearOff * 0.75f
        val end = if (direction == LayoutDirection.Ltr) strip.right - 40f else strip.left + 40f
        val near = Offset(end, strip.top - capture - with(density) { 20.dp.toPx() })
        repeat(12) { i -> fingerTo(start + (near - start) * ((i + 1) / 12f)) }
        assertNull(session.riding, "outside the capture, the tab should still be free")
        assertTrue(session.nudge.y > 0f, "coming up to the strip, the tab should be drawn towards its line: ${session.nudge}")
        fingerTo(Offset(end, strip.top - capture + 2f))
        assertSame(bottom, session.riding, "inside its capture, the bottom strip should take the tab in")
        assertSame(bottom.target, session.target, "the bottom strip should be what the tab is over")
        assertEquals(1, felt.count { it == FeedbackIntent.DragThresholdBack }, "being taken in should be felt once: $felt")
        assertTrue(session.pointer.y >= strip.top && session.pointer.y < strip.bottom, "taken in, the tab should be held on the strip's line: ${session.pointer}")
        onNodeWithTag("top").performTouchInput { up() }
        waitForIdle()
    }

    /**
     * A held on its centre for a long press, and picked up: by the first event
     * past the hold's deadline, a move along of a pixel.
     */
    private fun ComposeUiTest.pickUpA(): DragSession {
        onNodeWithTag("A").performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            advanceEventTime(16)
            moveBy(Offset(1f, 0f))
        }
        waitForIdle()
        return assertNotNull(host.session, "the hold did not pick the tab up, so this checks nothing")
    }

    /**
     * The finger already down moved to [inRoot], through the top strip, which
     * stays where it is while the tab it is carrying folds away.
     */
    private fun ComposeUiTest.fingerTo(inRoot: Offset) {
        val origin = onNodeWithTag("top").fetchSemanticsNode().boundsInRoot.topLeft
        onNodeWithTag("top").performTouchInput {
            advanceEventTime(16)
            moveTo(inRoot - origin)
        }
        waitForIdle()
    }

    /**
     * Carried along its strip towards the end, the thing stops where the last
     * one ends. It used to be the pointer that was kept on the strip, and a
     * thing held at its start went its whole width past the end, over
     * whatever came after: a document strip's "+".
     */
    @Test
    fun carriedTowardsTheEndOfItsStripItStopsWhereTheLastEnds() = runComposeUiTest {
        strips()
        val strip = onNodeWithTag("top").fetchSemanticsNode().boundsInRoot
        val end = strip.left + with(density) { (96.dp * 3).toPx() }
        onNodeWithTag("A").performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            // Along to near the strip's own end, well past the last thing and
            // still on the strip: the pointer, not the thing, inside it.
            repeat(12) {
                advanceEventTime(16)
                moveBy(Offset(24.dp.toPx(), 0f))
            }
        }
        waitForIdle()
        val session = assertNotNull(host.session, "the hold did not pick the thing up, so this checks nothing")
        assertSame(top, session.riding, "carried along its strip, the thing should still ride it")
        val right = session.topLeft.x + session.size.width
        assertTrue(right <= end + 0.5f, "the thing went past the end of the last one: it ends at $right, the last at $end")
        assertTrue(session.topLeft.x >= strip.left - 0.5f, "the thing went before the strip's start")
        onNodeWithTag("A").performTouchInput { up() }
        waitForIdle()
    }

    private companion object {
        val Tab = DragType<String>("tab")
        val TearOff = 32.dp
    }
}
