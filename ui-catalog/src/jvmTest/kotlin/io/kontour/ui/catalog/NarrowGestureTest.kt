package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Trash
import io.kontour.ui.components.display.Carousel
import io.kontour.ui.components.display.rememberCarouselState
import io.kontour.ui.components.list.SwipeAction
import io.kontour.ui.components.list.SwipeActions
import io.kontour.ui.components.list.SwipeValue
import io.kontour.ui.components.list.rememberSwipeActionsState
import io.kontour.ui.components.selection.RangeSlider
import io.kontour.ui.components.selection.Rating
import io.kontour.ui.components.selection.SegmentedControl
import io.kontour.ui.components.selection.Slider
import io.kontour.ui.components.selection.Switch
import io.kontour.ui.foundation.Text
import io.kontour.ui.nav.tabSwipe
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.ContrastLevel
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.kontourSizing
import kotlin.test.Test
import kotlin.test.fail

/**
 * Every gesture still works when the component is narrow.
 *
 * This is the half of "works at all screen sizes" that a picture cannot answer.
 * Every draggable thing here computes its thresholds from its own **measured
 * width** — a swipe commits at 0.6 of the row, a tab steps every 0.25 of the
 * pane, a slider maps the finger onto the track, a switch's travel is 20dp of a
 * 48dp control. A threshold that is a sensible fraction at 600dp can be smaller
 * than touch slop at 120dp, and the gesture then does nothing at all while
 * looking exactly as it should.
 *
 * Nothing looked for this. The gesture tests each drive their component at one
 * comfortable width; the width sweep asks whether it *draws*. A control that
 * draws perfectly and cannot be operated is the worse of the two failures,
 * because there is no picture of it that looks wrong.
 *
 * ### Widths, and why 120 is in them
 *
 * 120dp is a third of a 360dp phone, which is what a component gets in a
 * three-column row — the shape that produced the squeezed-`Switch` crash. 600dp
 * is the gallery's width and is here as the control: a gesture that fails at 600
 * is broken outright rather than broken narrow, and the message says which.
 */
class NarrowGestureTest {

    @Test
    fun everyGestureSurvivesANarrowComponent() {
        val failures = mutableListOf<String>()

        for (case in Cases) {
            for (width in Widths) {
                val specimen = case.make()
                val outcome = try {
                    if (drive(specimen, case, width)) null else "did nothing"
                } catch (error: Throwable) {
                    "threw ${error::class.simpleName}: ${error.message?.lineSequence()?.firstOrNull()}"
                }
                if (outcome != null) failures += "${case.name} at ${width}dp — $outcome"
            }
        }

        if (failures.isNotEmpty()) {
            fail(
                "${failures.size} gestures failed on a narrow component:\n" +
                    failures.joinToString("\n") { "  · $it" }
            )
        }
    }

    /**
     * A slider answers a press anywhere on itself, including at its very ends.
     *
     * The thumb is held back from each end by its own radius so it is not
     * clipped there, and that hold-back used to be a `padding` modifier with the
     * gesture handlers *inside* it. So the outer 11dp at each end of a slider
     * was dead to touch — which is exactly where the thumb sits when the value
     * is at its minimum or its maximum, so **half the thumb could not be
     * grabbed** at either end of the range. On a 120dp slider that is 18% of the
     * control, and it is the part a finger reaches for to say "none" or "all".
     *
     * Pinned directly as well as through the sweep above, because the sweep
     * found it by accident — it presses 5% of the way in, which happens to land
     * inside the margin below 220dp and outside it above. That is a coincidence
     * of the ladder, and a coincidence is not a regression test.
     */
    @Test
    fun aSliderAnswersAPressAtItsVeryEnd() {
        val dead = mutableListOf<String>()

        for (xDp in listOf(1, 4, 8, 11)) {
            var value by mutableStateOf(0.5f)
            var bounds = Rect.Zero

            Scene(width = 800, height = 300, density = Density.toFloat()) {
                Harness {
                    Box(Modifier.fillMaxSize().background(Color.White), Alignment.TopCenter) {
                        Box(Modifier.padding(top = 40.dp).width(300.dp).reportBounds { bounds = it }) {
                            Slider(
                                value = value,
                                onValueChange = { value = it },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }.use { scene ->
                scene.frames(6)
                val y = bounds.center.y
                scene.drag(
                    from = androidx.compose.ui.geometry.Offset(bounds.left + xDp * Density, y),
                    to = androidx.compose.ui.geometry.Offset(bounds.left + xDp * Density + 200f, y),
                    steps = 20,
                )
                scene.frames(10)
            }

            if (value == 0.5f) dead += "${xDp}dp"
        }

        if (dead.isNotEmpty()) {
            fail(
                "a press ${dead.joinToString(" and ")} from a slider's leading " +
                    "edge did nothing at all. The thumb's radius is 11dp, so that " +
                    "strip is where the thumb *is* when the value is at its " +
                    "minimum — the control looks entirely normal and half its " +
                    "thumb cannot be picked up."
            )
        }
    }

    /** Drives the gesture once and reports whether anything changed. */
    private fun drive(specimen: Specimen, case: Case, width: Int): Boolean {
        var bounds = Rect.Zero

        Scene(
            width = (width + Margin * 2) * Density,
            height = SceneHeight,
            density = Density.toFloat(),
        ) {
            Harness {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.White),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Box(
                        Modifier
                            .padding(top = Margin.dp)
                            .width(width.dp)
                            .reportBounds { bounds = it }
                    ) {
                        specimen.content()
                    }
                }
            }
        }.use { scene ->
            scene.frames(6)
            if (bounds.width <= 0f) return false

            if (case.vertical) {
                scene.drag(bounds.alongY(0.85f), bounds.alongY(0.1f), steps = 24)
            } else if (case.backwards) {
                scene.drag(bounds.alongX(0.95f), bounds.alongX(-0.2f), steps = 24)
            } else {
                // Beyond the far edge, so a threshold expressed as a fraction of
                // the width is comfortably cleared at every width in the ladder.
                scene.drag(bounds.alongX(0.05f), bounds.alongX(1.2f), steps = 24)
            }
            scene.frames(24)
        }

        return specimen.changed()
    }

    @Composable
    private fun Harness(content: @Composable () -> Unit) {
        KontourTheme(
            reduceMotion = true,
            sizing = kontourSizing(ContrastLevel.Standard).copy(minTouchTarget = 48.dp),
        ) {
            OverlayHost(Modifier.fillMaxSize()) { content() }
        }
    }

    /** One specimen, and the question of whether the gesture moved it. */
    private class Specimen(
        val content: @Composable () -> Unit,
        val changed: () -> Boolean,
    )

    /** One draggable component, built fresh for each width. */
    private class Case(
        val name: String,
        val vertical: Boolean = false,
        /**
         * True for a gesture whose *forward* direction is leftwards.
         *
         * A tab swipe drags left to go to the next tab, the way a page does; a
         * row's `end` actions are revealed by dragging toward the leading edge;
         * a carousel's next page comes in from the right. Dragging all three the
         * other way asks them to go back from the first item, which correctly
         * does nothing — and the first run of this reported all three as broken
         * at every width, which is how a harness fault announces itself.
         */
        val backwards: Boolean = false,
        val make: () -> Specimen,
    )

    private val Cases = listOf(
        Case("Switch") {
            var checked by mutableStateOf(false)
            Specimen(
                content = { Switch(checked = checked, onCheckedChange = { checked = it }) },
                changed = { checked },
            )
        },
        Case("Slider") {
            var value by mutableStateOf(0f)
            Specimen(
                content = {
                    Slider(value = value, onValueChange = { value = it }, modifier = Modifier.fillMaxWidth())
                },
                changed = { value > 0f },
            )
        },
        Case("Slider (stepped)") {
            var value by mutableStateOf(0f)
            Specimen(
                content = {
                    Slider(
                        value = value,
                        onValueChange = { value = it },
                        steps = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                changed = { value > 0f },
            )
        },
        Case("RangeSlider") {
            var range by mutableStateOf(0.2f..0.8f)
            Specimen(
                content = {
                    RangeSlider(
                        value = range,
                        onValueChange = { range = it },
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                changed = { range != 0.2f..0.8f },
            )
        },
        Case("SegmentedControl") {
            var selected by mutableStateOf(0)
            Specimen(
                content = {
                    SegmentedControl(
                        options = listOf("A", "B", "C"),
                        selected = selected,
                        onSelectedChange = { selected = it },
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                changed = { selected != 0 },
            )
        },
        Case("Rating") {
            var value by mutableStateOf(0f)
            Specimen(
                content = {
                    Rating(
                        value = value,
                        contentDescription = "Stars",
                        onValueChange = { value = it },
                    )
                },
                changed = { value > 0f },
            )
        },
        Case("tabSwipe", backwards = true) {
            var tab by mutableStateOf(0)
            Specimen(
                content = {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .background(Color.LightGray)
                            .tabSwipe(selected = tab, count = 3, onSelectedChange = { tab = it })
                    )
                },
                changed = { tab != 0 },
            )
        },
        Case("SwipeActions", backwards = true) {
            val state = SwipeStateHolder()
            Specimen(
                content = {
                    state.state = rememberSwipeActionsState()
                    SwipeActions(
                        modifier = Modifier.fillMaxWidth(),
                        end = listOf(
                            SwipeAction(
                                label = "Delete",
                                icon = Tabler.Outline.Trash,
                                onAction = { state.fired = true },
                                background = Color.Red,
                                isFullSwipeAction = true,
                            ),
                        ),
                    ) {
                        Box(Modifier.fillMaxWidth().height(72.dp).background(Color.White))
                    }
                },
                // Either it committed, or it came to rest showing the action.
                changed = { state.fired || state.state?.currentValue != SwipeValue.Resting },
            )
        },
        Case("Carousel", backwards = true) {
            var page by mutableStateOf(0)
            Specimen(
                content = {
                    val carousel = rememberCarouselState(pageCount = { 3 })
                    PageWatcher(carousel.currentPage) { page = it }
                    Carousel(
                        state = carousel,
                        contentDescription = "Photos",
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                    ) { index ->
                        Box(Modifier.fillMaxWidth().height(120.dp).background(Color.LightGray)) {
                            Text("$index")
                        }
                    }
                },
                changed = { page != 0 },
            )
        },
    )

    /** Mutable holder so a `remember`ed state can be read after the scene closes. */
    private class SwipeStateHolder {
        var state: io.kontour.ui.components.list.SwipeActionsState? = null
        var fired: Boolean = false
    }

    /** Reports a carousel's page out of composition without holding its state. */
    @Composable
    private fun PageWatcher(page: Int, report: (Int) -> Unit) {
        report(page)
    }

    private companion object {
        /**
         * A third of a phone, half a phone, a phone, and the gallery's width.
         *
         * `120` is what a component gets in a three-column row on a 360dp
         * screen — the shape behind the squeezed-`Switch` crash. `600` is the
         * control: a failure there is not about being narrow.
         */
        val Widths = listOf(120, 200, 320, 600)

        const val Density = 2
        const val SceneHeight = 700
        const val Margin = 60
    }
}
