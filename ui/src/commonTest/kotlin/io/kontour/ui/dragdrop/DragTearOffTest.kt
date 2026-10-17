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

    private fun ComposeUiTest.strips() {
        setContent {
            KontourTheme {
                CompositionLocalProvider(
                    LocalFeedback provides FeedbackDispatcher { felt += it },
                    LocalInputModality provides InputModality.Touch,
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
        state.tearOff = tearOff
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
        assertEquals(line, session.pointer.y, 0.5f, "the tab left its strip's line inside the tear-off")
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

    private companion object {
        val Tab = DragType<String>("tab")
        val TearOff = 32.dp
    }
}
