package io.kontour.ui.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.kontour.ui.components.datetime.WheelPicker
import io.kontour.ui.components.display.Carousel
import io.kontour.ui.components.display.PageIndicator
import io.kontour.ui.components.display.rememberCarouselState
import io.kontour.ui.components.selection.SegmentedControl
import io.kontour.ui.foundation.Text
import io.kontour.ui.overlay.Dialog
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.sheet.BottomSheet
import io.kontour.ui.sheet.SheetDetent
import io.kontour.ui.sheet.rememberSheetState
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * State that changes after the component has already drawn with it.
 *
 * Every other sweep in this suite hands a component its arguments once and then
 * looks at what came out. That covers the frame a screen is built on and no
 * frame after it — and the interesting failures are all afterwards: a list that
 * was six items long is now two, and something is still pointing at the fifth.
 *
 * These are the ordinary lifecycle of a screen, not contrivances. A collection
 * arrives, is filtered, is refreshed and comes back shorter. A sheet's detents
 * depend on what is in it, and what is in it changes. A dialog is opened and the
 * screen behind it is navigated away from while it is still animating in.
 *
 * The assertion is the same in each: **no throw, and a defined resting state
 * afterwards** — the scene keeps rendering. What a component settles *on* is a
 * design question and belongs in that component's own test; that it settles at
 * all is this file's.
 */
@OptIn(ExperimentalTestApi::class)
class StateLifecycleTest {

    /**
     * The collection is refreshed and comes back shorter than where the user was.
     *
     * `PageIndicator` sizes a `FloatArray` from the count and the carousel holds
     * a page index, so the shrink has to reach both before either is read again.
     */
    @Test
    fun aCarouselSurvivesItsPagesDisappearing() {
        var pages by mutableStateOf(6)
        survives("carousel losing pages") { control ->
            val carousel = rememberCarouselState { pages }
            Box(Modifier.fillMaxSize()) {
                Carousel(state = carousel, contentDescription = "Photos") { Text("page $it") }
                PageIndicator(state = carousel, modifier = Modifier.fillMaxWidth())
            }
            control.onSettled = { pages = 2 }
        }
    }

    /** And when it empties entirely, which is what a failed reload looks like. */
    @Test
    fun aCarouselSurvivesLosingEveryPage() {
        var pages by mutableStateOf(4)
        survives("carousel emptied") { control ->
            val carousel = rememberCarouselState { pages }
            Carousel(state = carousel, contentDescription = "Photos") { Text("page $it") }
            control.onSettled = { pages = 0 }
        }
    }

    /**
     * A sheet's detents are replaced while it is resting on one of the old ones.
     *
     * `rememberSheetState`'s KDoc recommends exactly this — which positions apply
     * depends on what is in the sheet — so the sheet can be sitting at Full when
     * Full stops being a position it is allowed to have.
     */
    @Test
    fun aSheetSurvivesLosingTheDetentItIsRestingOn() {
        survives("sheet losing its detent") { control ->
            val state = rememberSheetState(
                detents = listOf(SheetDetent.Hidden, SheetDetent.Half, SheetDetent.Full),
                initialDetent = SheetDetent.Full,
            )
            OverlayHost(Modifier.fillMaxSize()) {
                BottomSheet(state = state) { Text("Trip") }
            }
            control.onSettled = { state.detents = listOf(SheetDetent.Hidden) }
        }
    }

    /** The picker's list shortens past where it was pointing. */
    @Test
    fun aWheelPickerSurvivesItsListShortening() {
        var items by mutableStateOf(listOf("one", "two", "three", "four", "five"))
        survives("wheel picker list shortening") { control ->
            WheelPicker(items = items, selected = 4, onSelectedChange = {}, label = { it })
            control.onSettled = { items = listOf("one") }
        }
    }

    /**
     * The same shortening, on the wrapping drum.
     *
     * A separate case because it is separate code: `InfiniteWheel` keeps its
     * position in pixels and takes the shortest way round, so a stale index
     * reaches different arithmetic from the finite wheel's.
     */
    @Test
    fun anInfiniteWheelPickerSurvivesItsListShortening() {
        var items by mutableStateOf(listOf("one", "two", "three", "four", "five"))
        survives("infinite wheel picker list shortening") { control ->
            WheelPicker(
                items = items,
                selected = 4,
                onSelectedChange = {},
                label = { it },
                infinite = true,
            )
            control.onSettled = { items = listOf("one") }
        }
    }

    /** A selected index left pointing past the end of its own options. */
    @Test
    fun aSegmentedControlSurvivesLosingTheSelectedOption() {
        var options by mutableStateOf(listOf("Bus", "Train", "Ferry", "Tram"))
        survives("segments shortening") { control ->
            SegmentedControl(options = options, selected = 3, onSelectedChange = {})
            control.onSettled = { options = listOf("Bus") }
        }
    }

    /**
     * The screen behind a dialog is navigated away from while it is arriving.
     *
     * The host leaves composition mid-animation, taking the entry, its scrim and
     * the backdrop layer with it. Everything in `OverlayHost` that cleans up on
     * disposal runs here, out of order, halfway through a spring.
     */
    @Test
    fun anOverlaySurvivesItsHostLeavingMidAnimation() {
        var present by mutableStateOf(true)
        survives("host leaving mid-animation", settleAfter = 3) { control ->
            if (present) {
                OverlayHost(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize())
                    Dialog(visible = true, onDismissRequest = {}) { Text("Rename") }
                }
            } else {
                Box(Modifier.fillMaxSize())
            }
            control.onSettled = { present = false }
        }
    }

    /**
     * The control, and this file needs one.
     *
     * Six tests that pass by not throwing are six tests that also pass if the
     * mutation never happens — if `onSettled` is never called, if the state was
     * recreated inside composition and the write was undone by the next frame,
     * if the component stopped reading it. `SheetFramePressureTest`'s first
     * draft passed with every counter at zero on a sheet that never moved, which
     * is the same failure with a stopwatch instead of a `try`.
     *
     * So one case mutates into something the library is known to refuse.
     * `Carousel` requires a non-negative page count, loudly and by name, so a
     * count driven to −1 **must** bring this file's harness down. If it stops
     * doing so, the other six have stopped meaning anything and this says which.
     */
    @Test
    fun aMutationThatShouldFailDoes() {
        var pages by mutableStateOf(3)
        val outcome = runCatching {
            survives("the control") { control ->
                val carousel = rememberCarouselState { pages }
                Carousel(state = carousel, contentDescription = "Photos") { Text("page $it") }
                control.onSettled = { pages = -1 }
            }
        }.exceptionOrNull()

        val message = outcome?.message.orEmpty()
        assertTrue(
            "pageCount" in message,
            "driving a carousel's page count to -1 after it had drawn did not reach the " +
                "component: ${outcome?.let { it::class.simpleName + ": " + message } ?: "nothing threw"}. " +
                "Every other test in this file asserts that a mutation is survived, and none " +
                "of them can tell the difference between surviving one and never making it.",
        )
    }

    // ---- the harness -----------------------------------------------------

    /**
     * What the content under test uses to say "change something now".
     *
     * The mutation cannot happen inside composition — writing state a composable
     * has read is how you get an infinite recomposition rather than a lifecycle
     * test — so the content registers it here and [survives] runs it between
     * frames, which is where a real one happens.
     *
     * The state itself is declared in the test body rather than in the content
     * lambda, and that is not style: a `mutableStateOf` created inside the
     * composable is recreated on every recomposition, so the change would be
     * undone by the next frame and the test would pass without having tested
     * anything.
     */
    private class Control {
        var onSettled: () -> Unit = {}
    }

    private fun ComposeUiTest.settle(frames: Int) {
        repeat(frames) { mainClock.advanceTimeByFrame() }
        // The reason this file is not on `Scene`. `ImageComposeScene.render`
        // does not propagate an exception thrown during **recomposition** —
        // only the initial composition, which happens in its constructor. So a
        // harness built on it catches the crash a component has on its first
        // frame and silently misses every crash it has on a later one, which is
        // the entire subject of this file. `waitForIdle` rethrows.
        waitForIdle()
    }

    private fun survives(
        what: String,
        settleAfter: Int = 20,
        content: @Composable (Control) -> Unit,
    ) {
        val control = Control()
        try {
            runDesktopComposeUiTest(width = 800, height = 600) {
                // Hand-driven: several of these components animate on arrival,
                // and an auto-advancing clock chases a spring rather than
                // settling on it.
                mainClock.autoAdvance = false
                setContent {
                    KontourTheme(reduceMotion = true) {
                        Box(Modifier.fillMaxSize()) { content(control) }
                    }
                }
                settle(settleAfter)

                control.onSettled()
                // A write made from outside a composition sits in the global
                // snapshot until somebody says so. The Recomposer normally does
                // this from a coroutine that is not running here.
                Snapshot.sendApplyNotifications()

                // Long enough for whatever the change started to finish, and for
                // anything that was going to fail on a stale index to be read
                // again on the way.
                settle(60)
            }
        } catch (error: Throwable) {
            fail(
                "$what threw ${error::class.simpleName}: ${error.message}\n\n" +
                    "State a component has already drawn with can change under it, and this " +
                    "is the ordinary lifecycle of a screen rather than a contrivance: a " +
                    "collection is refreshed and comes back shorter, a sheet's detents " +
                    "follow its contents, a dialog outlives the screen that opened it.",
            )
        }
    }
}
