package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import io.kontour.ui.components.datetime.WheelPicker
import io.kontour.ui.interaction.FeedbackDispatcher
import io.kontour.ui.interaction.FeedbackIntent
import io.kontour.ui.interaction.LocalFeedback
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tapping a row you can see turns the drum to it, and says nothing on the way.
 *
 * Reported as missing, and it was missing on both paths: the rows are boxes with
 * a `Text` in them and nothing anywhere handled a click, so reaching a value two
 * rows up meant dragging the drum by exactly two rows. On a phone that is a
 * gesture; on a desktop it is a gesture nobody makes.
 *
 * ### The silence is the harder half
 *
 * A tap animates, and the animation crosses every row between here and there. A
 * detent haptic per row would be four buzzes for a gesture in which the finger
 * crossed nothing — worse than the missing feature, and exactly the shape of the
 * defect the round-25 audit removed fifty-seven times over.
 *
 * The two drums needed different answers. The infinite one already gates its
 * ticker on `isScrollInProgress`, which is the finger and the fling it threw and
 * is false for an `Animatable` — so its tap is silent for free. The finite one
 * scrolls a `LazyColumn`, and `animateScrollToItem` sets `isScrollInProgress`
 * exactly as a fling does, so it needs a flag of its own. Re-arming the ticker
 * once is *not* enough there and that is the trap: it swallows the first crossing
 * and reports the rest, which passes a test that only counts whether the value
 * arrived.
 *
 * Both are asserted here, because the thing that makes them different is
 * invisible from the outside.
 */
class WheelTapTest {

    @Test
    fun aFiniteWheelTurnsToATappedRowAndFiresNothing() {
        val felt = mutableListOf<FeedbackIntent>()
        val landed = tap(infinite = false, rowsBelowCentre = 2, felt = felt)

        assertEquals(
            Start + 2, landed,
            "tapping two rows below the centre of a wheel showing $Start landed on " +
                "$landed. The row under the finger is the value, and nothing else " +
                "is: a drum is read by what is in the band.",
        )
        assertEquals(
            emptyList(), felt,
            "the turn fired ${if (felt.isEmpty()) "nothing" else felt.toString()}. " +
                "An animated scroll crosses every row between here and there, and " +
                "none of them is a detent a finger crossed — `reset()` alone " +
                "swallows the first and reports the others, which looks like a fix " +
                "and is two buzzes.",
        )
    }

    @Test
    fun anInfiniteWheelTurnsToATappedRowAndFiresNothing() {
        val felt = mutableListOf<FeedbackIntent>()
        val landed = tap(infinite = true, rowsBelowCentre = -2, felt = felt)

        assertEquals(
            Start - 2, landed,
            "tapping two rows above the centre of an infinite wheel showing $Start " +
                "landed on $landed. A visible row is the nearest representative of " +
                "its value by definition, so the turn is the rows counted and never " +
                "the long way round.",
        )
        assertEquals(
            emptyList(), felt,
            "the turn fired ${if (felt.isEmpty()) "nothing" else felt.toString()}",
        )
    }

    /**
     * A tap that lands inside the centre row changes nothing and is not an error.
     *
     * Worth pinning: the arithmetic rounds a distance to a row count, so the
     * centre band rounds to zero — and a component that answered a tap on the
     * value already selected by re-animating to it would re-arm its ticker,
     * re-notify its caller, and interrupt a drag the finger had just finished.
     */
    @Test
    fun aTapOnTheCentreRowDoesNothing() {
        val felt = mutableListOf<FeedbackIntent>()
        val landed = tap(infinite = false, rowsBelowCentre = 0, felt = felt)

        assertEquals(
            Start, landed,
            "tapping the row already in the band moved the drum to $landed",
        )
        assertTrue(felt.isEmpty(), "tapping the selected row fired $felt")
    }

    /**
     * Taps [rowsBelowCentre] rows from the middle and returns where the drum settled.
     *
     * The row under the tap is found by arithmetic rather than by hit-testing, for
     * the same reason the component finds it that way: the centre row is at the
     * container's middle by construction and every row is [RowHeight] tall.
     */
    private fun tap(
        infinite: Boolean,
        rowsBelowCentre: Int,
        felt: MutableList<FeedbackIntent>,
    ): Int {
        var selected by mutableStateOf(Start)
        var bounds = Rect.Zero

        Scene(width = 300, height = 400, density = Density.toFloat()) {
            Recording(felt) {
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    WheelPicker(
                        items = (0..23).toList(),
                        selectedIndex = selected,
                        onSelectedIndexChange = { selected = it },
                        label = { it.toString().padStart(2, '0') },
                        infinite = infinite,
                        modifier = Modifier.reportBounds { bounds = it },
                    )
                }
            }
        }.use { scene ->
            scene.frames(6)
            scene.tap(
                Offset(
                    bounds.center.x,
                    bounds.center.y + rowsBelowCentre * RowHeight * Density,
                )
            )
            // Long enough for the spring or the smooth scroll to land, and for a
            // haptic fired on the way to have been recorded.
            scene.frames(90)
        }
        return selected
    }

    /** Records what the drum asks for, which is the whole instrument. */
    @Composable
    private fun Recording(into: MutableList<FeedbackIntent>, content: @Composable () -> Unit) {
        CompositionLocalProvider(
            LocalFeedback provides FeedbackDispatcher { into += it },
            content = content,
        )
    }

    private companion object {
        const val Density = 2
        const val Start = 9

        /** `WheelPicker`'s own default, which this does not override. */
        const val RowHeight = 40
    }
}
