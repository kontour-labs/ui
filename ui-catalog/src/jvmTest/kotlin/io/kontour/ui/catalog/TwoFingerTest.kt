package io.kontour.ui.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.display.Carousel
import io.kontour.ui.components.display.rememberCarouselState
import io.kontour.ui.components.selection.RangeSlider
import io.kontour.ui.components.selection.Slider
import io.kontour.ui.foundation.Text
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.sheet.BottomSheet
import io.kontour.ui.sheet.SheetDetent
import io.kontour.ui.sheet.rememberSheetState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * A second finger, on components written for one.
 *
 * Every gesture test in this suite drives a single pointer, because until now
 * that is all `Scene` could send: it went through `sendPointerEvent`'s
 * one-pointer overload, so nothing in the repository had ever put two fingers on
 * a component. `ImageComposeScene` has always had a list-taking overload; the
 * harness simply did not reach it.
 *
 * That is a gap with a shape. A drag handler tracks *a* pointer — where it went
 * down, how far it has travelled, whether it has passed a threshold — and the
 * cases that break one are all about a second: two fingers on the same thumb and
 * a component averaging them into a position between; one arriving mid-drag and
 * the handler switching to it, or to a delta measured from the wrong origin; the
 * first lifting while the second stays down, which is a release event that is not
 * the end of the gesture.
 *
 * A phone produces all three by accident. A thumb and a palm, a second hand
 * arriving, a finger rolling off an edge.
 *
 * The assertion is the same as the other stability sweeps: **no throw, and the
 * component keeps rendering.** What it should *do* with two fingers is a design
 * question, and mostly the answer is "carry on with one of them" — that belongs
 * in each component's own test, where a decision can be argued. That it does not
 * come down is this file's.
 */
class TwoFingerTest {

    @Test
    fun aSliderThumbSurvivesTwoFingers() = survives("two fingers on a slider") {
        var value by mutableStateOf(0.5f)
        var bounds = Rect.Zero
        scene {
            Slider(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.width(300.dp).reportBounds { bounds = it },
            )
        }.use { scene ->
            scene.advance(5)
            val thumb = bounds.alongX(0.5f)
            scene.pinch(thumb, thumb + Offset(4f, 0f), travel = Offset(80f, 0f))
            scene.advance(10)
        }
    }

    @Test
    fun aRangeSliderSurvivesAFingerOnEachThumb() = survives("a finger on each thumb") {
        var value by mutableStateOf(0.3f..0.7f)
        var bounds = Rect.Zero
        scene {
            RangeSlider(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.width(300.dp).reportBounds { bounds = it },
            )
        }.use { scene ->
            scene.advance(5)
            // Both thumbs, driven towards each other, which is the case that has
            // a minimum distance between them to satisfy.
            scene.pinch(bounds.alongX(0.3f), bounds.alongX(0.7f), travel = Offset(60f, 0f))
            scene.advance(10)
        }
    }

    /**
     * A second finger arriving while a sheet is already being dragged.
     *
     * The sheet reads a drag as a distance from where the gesture started, so a
     * pointer that appears halfway through has an origin the handler never saw.
     */
    @Test
    fun aSheetSurvivesASecondFingerMidDrag() = survives("a second finger on a sheet") {
        scene {
            val state = rememberSheetState(
                detents = listOf(SheetDetent.Hidden, SheetDetent.Half, SheetDetent.Full),
                initialDetent = SheetDetent.Half,
            )
            OverlayHost(Modifier.fillMaxSize()) {
                BottomSheet(state = state) { Text("Trip") }
            }
        }.use { scene ->
            scene.advance(20)
            val first = Offset(400f, 300f)
            scene.touch(PointerEventType.Press, listOf(Scene.Touch(1L, first)))
            scene.advance(2)
            // Moving one finger, then adding another mid-travel, then moving both.
            for (step in 1..5) {
                scene.touch(
                    PointerEventType.Move,
                    listOf(Scene.Touch(1L, first + Offset(0f, step * 20f))),
                )
            }
            val second = Offset(500f, 380f)
            scene.touch(
                PointerEventType.Press,
                listOf(Scene.Touch(1L, first + Offset(0f, 100f)), Scene.Touch(2L, second)),
            )
            for (step in 1..5) {
                scene.touch(
                    PointerEventType.Move,
                    listOf(
                        Scene.Touch(1L, first + Offset(0f, 100f + step * 20f)),
                        Scene.Touch(2L, second + Offset(0f, step * 20f)),
                    ),
                )
            }
            // The first lifts while the second stays down: a release that is not
            // the end of the gesture.
            scene.touch(
                PointerEventType.Release,
                listOf(
                    Scene.Touch(1L, first + Offset(0f, 200f), down = false),
                    Scene.Touch(2L, second + Offset(0f, 100f)),
                ),
            )
            scene.advance(5)
            scene.touch(
                PointerEventType.Release,
                listOf(Scene.Touch(2L, second + Offset(0f, 100f), down = false)),
            )
            scene.advance(30)
        }
    }

    @Test
    fun aCarouselSurvivesTwoFingers() = survives("two fingers on a carousel") {
        scene {
            val carousel = rememberCarouselState { 5 }
            Carousel(
                state = carousel,
                contentDescription = "Photos",
                modifier = Modifier.padding(16.dp),
            ) { Text("page $it") }
        }.use { scene ->
            scene.advance(10)
            scene.pinch(Offset(300f, 200f), Offset(500f, 200f), travel = Offset(-120f, 0f))
            scene.advance(20)
        }
    }

    /**
     * The control, and this file is worthless without it.
     *
     * Four tests that pass by not throwing also pass if only one finger ever
     * arrives — if the event carries a single pointer, if the ids collide, if
     * `pressed` is wrong and Compose treats the second as already lifted. Every
     * one of those looks exactly like a component handling two fingers
     * gracefully.
     *
     * `StateLifecycleTest` learned this the expensive way: six green tests whose
     * mutation was never reaching the component, found only because a seventh
     * asserted that something *should* break and it did not.
     *
     * So this watches the pointer stream directly and requires that two changes
     * arrive in one event.
     */
    @Test
    fun theHarnessReallySendsTwoFingers() {
        var mostAtOnce = 0
        Scene(width = 800, height = 600, density = 2f, reduceMotion = true) {
            Box(
                Modifier.fillMaxSize().pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            mostAtOnce = maxOf(mostAtOnce, event.changes.count { it.pressed })
                        }
                    }
                }
            )
        }.use { scene ->
            scene.advance(3)
            scene.pinch(Offset(300f, 300f), Offset(420f, 300f), travel = Offset(60f, 0f))
            scene.advance(3)
        }

        assertEquals(
            2,
            mostAtOnce,
            "the two-finger harness delivered at most $mostAtOnce pressed pointer(s) in a " +
                "single event. Every other test in this file asserts that two fingers are " +
                "survived, and none of them can tell the difference between surviving two " +
                "and only ever being sent one.",
        )
    }

    // ---- the harness -----------------------------------------------------

    private fun scene(content: @Composable () -> Unit) =
        Scene(width = 800, height = 600, density = 2f, reduceMotion = true) {
            Box(Modifier.fillMaxSize()) { content() }
        }

    /**
     * Two fingers down, both travelling by [travel], both up.
     *
     * The plainest two-finger gesture there is, and the one a phone produces by
     * accident most often: a thumb and the heel of a hand, moving together.
     */
    private fun Scene.pinch(a: Offset, b: Offset, travel: Offset, steps: Int = 8) {
        touch(PointerEventType.Press, listOf(Scene.Touch(1L, a), Scene.Touch(2L, b)))
        for (step in 1..steps) {
            val at = travel * (step.toFloat() / steps)
            touch(
                PointerEventType.Move,
                listOf(Scene.Touch(1L, a + at), Scene.Touch(2L, b + at)),
            )
        }
        touch(
            PointerEventType.Release,
            listOf(
                Scene.Touch(1L, a + travel, down = false),
                Scene.Touch(2L, b + travel, down = false),
            ),
        )
    }

    private fun survives(what: String, gesture: () -> Unit) {
        try {
            gesture()
        } catch (error: Throwable) {
            fail(
                "$what threw ${error::class.simpleName}: ${error.message}\n\n" +
                    "A drag handler written for one pointer meets a second by accident " +
                    "constantly — a thumb and a palm, a second hand arriving, a finger " +
                    "rolling off an edge. It does not have to do anything clever with it; " +
                    "it does have to stay up.",
            )
        }
    }
}
