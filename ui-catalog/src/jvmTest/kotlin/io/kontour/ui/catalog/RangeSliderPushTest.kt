package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.selection.RangeSlider
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * One thumb shoves the other along rather than stopping dead against it.
 *
 * The old rule was a clamp, and `selection.md` stated it as a virtue: *"Crossing
 * is clamped, not swapped"*. Swapping was indeed the wrong answer — a range that
 * inverts under the finger is one the user has to drag twice to fix — but so was
 * blocking. It jams the control at exactly the moment the user is asking for the
 * narrowest range there is, and it breaks the one promise a drag makes, which is
 * that the thing under your finger goes where your finger goes.
 *
 * `minDistance` is the same question asked deliberately: a departure window of
 * "no less than twenty minutes" is a real requirement, and there was no way to
 * express it. Both thumbs now respect it, from a drag and from assistive tech
 * alike.
 *
 * ### Asserted on the reported value, not on pixels
 *
 * The gesture is what is under test and the value is what it produces. Every
 * frame of every drag is checked, not just the last one, because a range that
 * ends up legal after passing through an illegal state is a range a caller
 * watching `onValueChange` has already seen inverted.
 */
class RangeSliderPushTest {

    @Test
    fun draggingTheEndThumbLeftPushesTheStartThumbAlong() {
        val seen = mutableListOf<ClosedFloatingPointRange<Float>>()
        var range by mutableStateOf(2f..8f)
        var bounds = Rect.Zero

        Scene(width = 700, height = 240) {
            Box(Modifier.fillMaxSize().background(Color.White).padding(20.dp)) {
                RangeSlider(
                    value = range,
                    onValueChange = { range = it },
                    valueRange = Range,
                    minDistance = MinDistance,
                    modifier = Modifier.reportBounds { bounds = it },
                )
            }
        }.use { scene ->
            scene.frames(3)
            assertTrue(bounds.width > 0f, "the range slider never reported a size")
            scene.drag(
                from = bounds.alongX(0.85f),
                to = bounds.alongX(0.02f),
                steps = 24,
                onFrame = { _, _ -> seen += range },
            )
            scene.frames(6)
        }

        seen.assertNeverIllegal()
        assertTrue(
            range.start <= Range.start + Slack,
            "the end thumb was dragged to the far left and the start thumb " +
                "stayed at ${range.start} — it should have been pushed to the " +
                "start of the track ahead of it, not blocked the drag",
        )
        assertTrue(
            range.endInclusive - range.start in MinDistance - Slack..MinDistance + Slack,
            "the pair came to rest ${range.endInclusive - range.start} apart with a " +
                "minimum of $MinDistance — pushed to the wall, the two should be " +
                "exactly their minimum apart",
        )
    }

    @Test
    fun draggingTheStartThumbRightPushesTheEndThumbAlong() {
        val seen = mutableListOf<ClosedFloatingPointRange<Float>>()
        var range by mutableStateOf(2f..8f)
        var bounds = Rect.Zero

        Scene(width = 700, height = 240) {
            Box(Modifier.fillMaxSize().background(Color.White).padding(20.dp)) {
                RangeSlider(
                    value = range,
                    onValueChange = { range = it },
                    valueRange = Range,
                    minDistance = MinDistance,
                    modifier = Modifier.reportBounds { bounds = it },
                )
            }
        }.use { scene ->
            scene.frames(3)
            scene.drag(
                from = bounds.alongX(0.2f),
                to = bounds.alongX(0.98f),
                steps = 24,
                onFrame = { _, _ -> seen += range },
            )
            scene.frames(6)
        }

        seen.assertNeverIllegal()
        assertTrue(
            range.endInclusive >= Range.endInclusive - Slack,
            "the start thumb was dragged to the far right and the end thumb " +
                "stayed at ${range.endInclusive} — it should have been pushed to " +
                "the end of the track ahead of it",
        )
        assertTrue(
            range.start <= Range.endInclusive - MinDistance + Slack,
            "the start thumb ended at ${range.start}, past the point where its " +
                "neighbour has run out of track. The track's end stops the drag; " +
                "the other thumb no longer does, but the wall behind it still has to.",
        )
    }

    /**
     * A minimum wider than the track asks for the whole track and gets it.
     *
     * `valueRange.endInclusive - gap` below `valueRange.start` gives `coerceIn`
     * an inverted range, and `coerceIn` **throws** on one — the same trap that
     * took a frame down from inside `Switch`'s draw, found the same way.
     *
     * Asserted on the range *opening*, not on the absence of a crash. The first
     * version of this checked that the value stayed inside its own bounds and
     * **passed with the clamp deleted**: an exception thrown inside a pointer
     * handler leaves the value exactly where it was, which is inside its bounds.
     * "Nothing bad happened" and "nothing happened" are the same picture. The
     * drag has to be shown doing its job.
     */
    @Test
    fun aMinimumWiderThanTheTrackOpensTheWholeRange() {
        var range by mutableStateOf(2f..8f)
        var bounds = Rect.Zero

        Scene(width = 700, height = 240) {
            Box(Modifier.fillMaxSize().background(Color.White).padding(20.dp)) {
                RangeSlider(
                    value = range,
                    onValueChange = { range = it },
                    valueRange = Range,
                    minDistance = 999f,
                    modifier = Modifier.reportBounds { bounds = it },
                )
            }
        }.use { scene ->
            scene.frames(3)
            scene.drag(from = bounds.alongX(0.8f), to = bounds.alongX(0.1f), steps = 12)
            scene.frames(6)
        }

        assertTrue(
            range.start <= Range.start + Slack && range.endInclusive >= Range.endInclusive - Slack,
            "a minimum of 999 on a range of $Range left the thumbs at $range. " +
                "Clamped to the span it is a request for the whole track, and the " +
                "drag should have opened it to exactly that; unclamped it throws " +
                "from inside the gesture and the value never moves at all — which " +
                "looks identical from the outside unless you ask the drag to have " +
                "done something.",
        )
    }

    private fun List<ClosedFloatingPointRange<Float>>.assertNeverIllegal() {
        val inverted = firstOrNull { it.start > it.endInclusive }
        assertTrue(inverted == null, "the range inverted mid-drag: $inverted")

        val tooClose = firstOrNull { it.endInclusive - it.start < MinDistance - Slack }
        assertTrue(
            tooClose == null,
            "the thumbs came ${tooClose?.let { it.endInclusive - it.start }} apart " +
                "mid-drag against a minimum of $MinDistance. A caller watching " +
                "`onValueChange` has already been handed that.",
        )

        val outside = firstOrNull {
            it.start < Range.start - Slack || it.endInclusive > Range.endInclusive + Slack
        }
        assertTrue(outside == null, "the range left its own bounds mid-drag: $outside")
    }

    private companion object {
        val Range = 0f..10f
        const val MinDistance = 2f

        /** One pixel of a 576px track is under 0.02 of a 10-unit range. */
        const val Slack = 0.05f
    }
}
