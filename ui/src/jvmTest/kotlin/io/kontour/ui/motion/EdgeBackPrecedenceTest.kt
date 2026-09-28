package io.kontour.ui.motion

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.NavigationEventHandler
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import io.kontour.ui.components.display.Carousel
import io.kontour.ui.components.display.CarouselState
import io.kontour.ui.components.display.rememberCarouselState
import io.kontour.ui.components.list.SwipeAction
import io.kontour.ui.components.list.SwipeActions
import io.kontour.ui.components.list.SwipeActionsState
import io.kontour.ui.components.list.SwipeValue
import io.kontour.ui.components.list.rememberSwipeActionsState
import io.kontour.ui.components.selection.Slider
import io.kontour.ui.foundation.SystemIcons
import io.kontour.ui.foundation.Text
import io.kontour.ui.overlay.LocalTakesBackSwipe
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.overlay.RegisterBackHandler
import io.kontour.ui.theme.KontourTheme
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The screen's edge and the controls on a page share one finger: from the very
 * edge a swipe is back, from anywhere else it is the control's.
 *
 * Reported from a phone, with how iOS does it: "if there was a swipe actions box
 * on the page, then it shouldn't trigger if I'm swiping the swipe actions box.
 * However, if I do swipe from the very edge of the screen, it should do the
 * swipe-back animation."
 */
@OptIn(ExperimentalTestApi::class)
class EdgeBackPrecedenceTest {

    private object Info : NavigationEventInfo()

    private class Recorder : NavigationEventHandler<NavigationEventInfo>(Info, true) {
        var started = 0
        var completed = 0

        override fun onBackStarted(event: NavigationEvent) {
            started++
        }

        override fun onBackCompleted() {
            completed++
        }
    }

    private fun owner(dispatcher: NavigationEventDispatcher) = object : NavigationEventDispatcherOwner {
        override val navigationEventDispatcher = dispatcher
    }

    @Test
    fun fromTheEdgeASwipeOverARowIsBack() = edgeOverARow(LayoutDirection.Ltr)

    @Test
    fun rightToLeftTheEdgeIsTheRightOne() = edgeOverARow(LayoutDirection.Rtl)

    private fun edgeOverARow(direction: LayoutDirection) = runComposeUiTest {
        val recorder = Recorder()
        lateinit var row: SwipeActionsState
        page(recorder, direction) {
            row = rememberSwipeActionsState()
            // Actions on both sides: whichever way the finger goes, the row would take it.
            SwipeActions(
                state = row,
                start = listOf(SwipeAction("Pin", SystemIcons.Close, onAction = {}, background = Color.Blue)),
                end = listOf(SwipeAction("Remove", SystemIcons.Close, onAction = {}, background = Color.Red)),
                modifier = Modifier.fillMaxWidth().testTag("row"),
            ) { Text("Perth Busport", Modifier.fillMaxWidth().height(64.dp)) }
        }
        onNodeWithTag("row").performTouchInput {
            val fromEdge = if (direction == LayoutDirection.Ltr) 4f else width - 4f
            val towards = if (direction == LayoutDirection.Ltr) width * 0.8f else width * 0.2f
            swipe(Offset(fromEdge, centerY), Offset(towards, centerY), durationMillis = 300)
        }
        waitForIdle()
        assertEquals(1, recorder.completed, "$direction: a swipe from the very edge should go back")
        assertEquals(SwipeValue.Resting, row.currentValue, "$direction: and leave the row where it was")
    }

    @Test
    fun fromTheMiddleOfARowItIsNeverBack() = runComposeUiTest {
        val recorder = Recorder()
        lateinit var row: SwipeActionsState
        page(recorder, LayoutDirection.Ltr, content = true) {
            row = rememberSwipeActionsState()
            // Actions on the end only: a swipe towards the start has nothing to
            // show, and used to fall through to the page's own swipe back.
            SwipeActions(
                state = row,
                end = listOf(SwipeAction("Remove", SystemIcons.Close, onAction = {}, background = Color.Red)),
                modifier = Modifier.fillMaxWidth().testTag("row"),
            ) { Text("Elizabeth Quay", Modifier.fillMaxWidth().height(64.dp)) }
        }
        onNodeWithTag("row").performTouchInput {
            swipe(Offset(width * 0.3f, centerY), Offset(width * 0.9f, centerY), durationMillis = 300)
        }
        waitForIdle()
        assertEquals(0, recorder.started, "a swipe across the middle of a row is the row's")
    }

    @Test
    fun aSliderPressedInTheEdgeBandKeepsItsDrag() = runComposeUiTest {
        val recorder = Recorder()
        var value by mutableFloatStateOf(0f)
        page(recorder, LayoutDirection.Ltr) {
            Slider(value = value, onValueChange = { value = it }, modifier = Modifier.fillMaxWidth().testTag("slider"))
        }
        onNodeWithTag("slider").performTouchInput {
            swipe(Offset(4f, centerY), Offset(width * 0.7f, centerY), durationMillis = 300)
        }
        waitForIdle()
        assertEquals(0, recorder.started, "a slider that took the press keeps the drag, edge or not")
        assertTrue(value > 0.4f, "the slider should have moved with the finger, and is at $value")
    }

    @Test
    fun aCarouselKeepsItsSwipe() = runComposeUiTest {
        val recorder = Recorder()
        lateinit var photos: CarouselState
        page(recorder, LayoutDirection.Ltr, content = true) {
            photos = rememberCarouselState { 3 }
            Carousel(photos, contentDescription = "Stop photos", modifier = Modifier.fillMaxWidth().height(96.dp).testTag("photos")) {
                Text("Photo ${it + 1}", Modifier.fillMaxWidth().height(96.dp))
            }
        }
        runOnIdle { runBlocking { photos.listState.scrollToItem(1) } }
        waitForIdle()
        onNodeWithTag("photos").performTouchInput {
            swipe(Offset(width * 0.3f, centerY), Offset(width * 0.9f, centerY), durationMillis = 300)
        }
        waitForIdle()
        assertEquals(0, recorder.started, "a swipe the carousel can take is the carousel's")
        assertEquals(0, photos.currentPage, "and it went back a photo")
    }

    private fun ComposeUiTest.page(
        recorder: Recorder,
        direction: LayoutDirection,
        content: Boolean = false,
        body: @Composable () -> Unit,
    ) {
        val platform = NavigationEventDispatcher()
        setContent {
            CompositionLocalProvider(
                LocalNavigationEventDispatcherOwner provides owner(platform),
                LocalBackStyle provides BackStyle.Swipe,
                LocalTakesBackSwipe provides true,
                LocalLayoutDirection provides direction,
            ) {
                KontourTheme(darkTheme = false, reduceMotion = true) {
                    OverlayHost(Modifier.fillMaxSize()) {
                        RegisterBackHandler(LocalNavigationEventDispatcherOwner.current, recorder)
                        Column(Modifier.fillMaxSize().then(if (content) Modifier.contentSwipeBack() else Modifier)) {
                            body()
                        }
                    }
                }
            }
        }
        waitForIdle()
    }
}
