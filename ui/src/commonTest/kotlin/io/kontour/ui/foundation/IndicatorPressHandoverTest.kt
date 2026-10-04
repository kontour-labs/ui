package io.kontour.ui.foundation

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A marker that leant toward a pressed item goes on from the lean when the
 * press lets go, rather than springing back out of it first.
 *
 * Reported: *"on platforms with mouse input, when i click a segmented control,
 * it sort of 'jiggles' a bit before sliding across. this also happens if i tap
 * and hold on mobile/touchscreen, but it's way more noticeable on
 * desktop/mouse."* A press leans the marker toward the item, and the release
 * selects it. The lean was let go on release and sprang back on the snappy
 * spring, while the travel started from the marker's resting place on the
 * slower default spring, from standing — so for the first frames after the
 * click the lean undid itself faster than the travel replaced it, and the
 * marker's leading edge went *back* before it went across: 119, 119, 117, 118
 * and only then 128, here. A mouse shows it on every click.
 *
 * A black marker on white, so its edges are exact; the segmented control's
 * thumb is the same marker under a shadow.
 */
@OptIn(ExperimentalTestApi::class)
class IndicatorPressHandoverTest {

    private val itemWidth = 100

    private fun ComposeUiTest.group() {
        setContent {
            KontourTheme(darkTheme = false) {
                var selected by remember { mutableIntStateOf(0) }
                val state = remember { SelectionIndicatorState() }
                Box(Modifier.background(Color.White)) {
                    SelectionIndicatorBox(
                        state = state,
                        sizing = IndicatorSizing.Fill,
                        indicator = { Box(Modifier.fillMaxSize().background(Color.Black)) },
                    ) {
                        Row {
                            for (i in 0 until 3) {
                                val interactions = remember { MutableInteractionSource() }
                                Box(
                                    Modifier
                                        .size(itemWidth.dp, 40.dp)
                                        .testTag("item$i")
                                        .selectionIndicatorItem(i, i == selected)
                                        .anticipatesSelection(i, i == selected, interactions)
                                        .selectable(
                                            selected = i == selected,
                                            onClick = { selected = i },
                                            interactionSource = interactions,
                                            indication = null,
                                        ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    /** The marker's right edge, in pixels, along the row's middle. */
    private fun ComposeUiTest.leadingEdge(): Int {
        val image = onRoot().captureToImage().toPixelMap()
        val y = image.height / 2
        var edge = -1
        for (x in 0 until image.width) if (image[x, y].red < 0.5f) edge = x
        return edge
    }

    @Test
    fun aClickedItemIsReachedForAndThenGoneToWithoutGoingBack() = runComposeUiTest {
        group()
        waitForIdle()
        mainClock.autoAdvance = false
        val rest = leadingEdge()

        val edges = mutableListOf<Int>()
        onNodeWithTag("item1").performMouseInput { moveTo(center); press() }
        // A click: the button down for six frames, about a tenth of a second.
        repeat(6) {
            mainClock.advanceTimeByFrame()
            edges += leadingEdge()
        }
        val leant = edges.last()
        onNodeWithTag("item1").performMouseInput { release() }
        repeat(40) {
            mainClock.advanceTimeByFrame()
            edges += leadingEdge()
        }

        val density = this.density.density
        assertTrue(leant > rest, "the press never leant the marker (edge $rest, then $leant): this measured nothing")
        assertTrue(
            edges.last() >= (2 * itemWidth * density).toInt() - 2,
            "the marker never arrived on the second item: its edge ended at ${edges.last()}",
        )
        // Overshoot at the far end is the travel's own spring; what must not
        // happen is a step back on the way.
        val arrived = edges.indexOfFirst { it >= (2 * itemWidth * density).toInt() - 2 }
        val before = edges.take(arrived.coerceAtLeast(1))
        val back = before.zipWithNext().filter { (a, b) -> b < a - 1 }
        assertTrue(
            back.isEmpty(),
            "the marker's leading edge went back on its way across: $back, in $edges",
        )
    }
}
