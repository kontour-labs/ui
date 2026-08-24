package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Trash
import io.kontour.ui.components.list.SwipeAction
import io.kontour.ui.components.list.SwipeActions
import io.kontour.ui.components.selection.SegmentedControl
import io.kontour.ui.interaction.FeedbackDispatcher
import io.kontour.ui.interaction.FeedbackIntent
import io.kontour.ui.interaction.LocalFeedback
import io.kontour.ui.nav.tabSwipe
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The drag gestures click as they cross their detents.
 *
 * The library already had the vocabulary and had applied it unevenly. A slider
 * ticked per step, a wheel picker per item, a segmented control per segment —
 * and the tab bar spent a `Selection` on every step it passed, while the
 * swipeable row, which has the most pronounced detents of anything here, was
 * silent from the first pixel to the commit. A gesture that clicks on some
 * controls and not others does not read as a system with a feel; it reads as
 * some of it being finished.
 *
 * ### Recorded through `LocalFeedback`
 *
 * `LocalFeedback` is a `staticCompositionLocalOf<FeedbackDispatcher>` and
 * `FeedbackDispatcher` is a `fun interface`, so a test can install one that
 * writes down what it was asked to do. That is the whole instrument — no
 * platform, no expect/actual, no mocking. The mapping from intent to physical
 * haptic is one decision in one place, which is the property that makes this
 * possible and is why the intents exist at all.
 *
 * The *sequence* is what is asserted, not a count: what matters is that the
 * ticks come one per boundary rather than one per frame, and that the one
 * intent with a consequence behind it fires exactly once.
 */
class DetentHapticsTest {

    @Test
    fun aSegmentedControlTicksOncePerSegmentCrossed() {
        val felt = mutableListOf<FeedbackIntent>()
        var selected by mutableStateOf(0)
        var bounds = Rect.Zero

        Scene(width = 700, height = 200) {
            Recording(felt) {
                Box(Modifier.fillMaxSize().background(Color.White).padding(20.dp)) {
                    SegmentedControl(
                        options = listOf("Depart", "Arrive", "Both", "Neither"),
                        selected = selected,
                        onSelectedChange = { selected = it },
                        modifier = Modifier.fillMaxWidth().reportBounds { bounds = it },
                    )
                }
            }
        }.use { scene ->
            scene.frames(3)
            // Right across all four, slowly enough that many frames fall inside
            // each segment — which is the case a per-frame implementation fails.
            scene.drag(bounds.alongX(0.05f), bounds.alongX(0.95f), steps = 40)
            scene.frames(4)
        }

        val ticks = felt.count { it == FeedbackIntent.Tick }
        assertTrue(
            ticks in 3..5,
            "a drag across four segments produced $ticks ticks (${felt.summary()}). " +
                "Three boundaries were crossed, so three is the answer; anything " +
                "near forty is one per frame and anything near zero is silence.",
        )
        assertTrue(
            felt.lastOrNull() == FeedbackIntent.GestureEnd,
            "the drag ended without a settle: ${felt.summary()}",
        )
    }

    @Test
    fun tabsTickPerStepAndSelectOnceAtTheEnd() {
        val felt = mutableListOf<FeedbackIntent>()
        var tab by mutableStateOf(0)
        var bounds = Rect.Zero

        Scene(width = 700, height = 300) {
            Recording(felt) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.White)
                        .tabSwipe(selected = tab, count = 4, onSelectedChange = { tab = it })
                        .reportBounds { bounds = it }
                )
            }
        }.use { scene ->
            scene.frames(3)
            scene.drag(bounds.alongX(0.95f), bounds.alongX(0.05f), steps = 30)
            scene.frames(4)
        }

        assertTrue(tab > 0, "the swipe did not change tab at all")
        assertTrue(
            felt.count { it == FeedbackIntent.Selection } == 1,
            "a swipe through ${tab + 1} tabs fired " +
                "${felt.count { it == FeedbackIntent.Selection }} selections " +
                "(${felt.summary()}). Stepping past a tab is a detent crossed, " +
                "not a decision made — the decision happens once, when the " +
                "finger lifts.",
        )
        assertTrue(
            felt.count { it == FeedbackIntent.Tick } >= tab,
            "only ${felt.count { it == FeedbackIntent.Tick }} ticks for $tab " +
                "steps: ${felt.summary()}",
        )
    }

    @Test
    fun aSwipedRowTicksPerActionAndWarnsOnceAtThePointOfNoReturn() {
        val felt = mutableListOf<FeedbackIntent>()
        var bounds = Rect.Zero

        Scene(width = 700, height = 200) {
            Recording(felt) {
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    SwipeActions(
                        modifier = Modifier.fillMaxWidth().height(72.dp)
                            .reportBounds { bounds = it },
                        end = listOf(
                            SwipeAction(
                                label = "Delete",
                                icon = Tabler.Outline.Trash,
                                onAction = {},
                                background = Color.Red,
                                isFullSwipeAction = true,
                            ),
                        ),
                    ) {
                        Box(Modifier.fillMaxWidth().height(72.dp).background(Color.White))
                    }
                }
            }
        }.use { scene ->
            scene.frames(3)
            // Right across, past the reveal and past the commit threshold.
            scene.drag(bounds.alongX(0.95f), bounds.alongX(0.02f), steps = 30, release = false)
            scene.frames(6)
        }

        assertTrue(
            felt.contains(FeedbackIntent.Tick),
            "dragging a row clear across its actions produced no ticks at all: " +
                felt.summary(),
        )
        assertTrue(
            felt.count { it == FeedbackIntent.DragThreshold } == 1,
            "the point of no return fired " +
                "${felt.count { it == FeedbackIntent.DragThreshold }} times " +
                "(${felt.summary()}). It is one edge in the gesture and it is the " +
                "only one with a consequence behind it, so it is worth exactly one " +
                "distinct thing.",
        )
    }

    /** Installs a dispatcher that writes down what it is asked to do. */
    @Composable
    private fun Recording(into: MutableList<FeedbackIntent>, content: @Composable () -> Unit) {
        CompositionLocalProvider(
            LocalFeedback provides FeedbackDispatcher { into += it },
            content = content,
        )
    }

    private fun List<FeedbackIntent>.summary(): String =
        if (isEmpty()) "nothing at all" else groupingBy { it }.eachCount().toString()
}
