package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.selection.SegmentedControl
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Changing the type size under a finger does not stop the control being a control.
 *
 * ### Read off the screenshots before it was reproduced
 *
 * Reported as *"weird issues with controls when changing the text size, but it
 * only happens sometimes"*, with pictures: a segmented control whose thumb is
 * about a fifth too wide and sitting between two segments. Both numbers are
 * named in the source. A fifth is `MaxSegmentStretch`, the cap — so the lean was
 * at its limit, not part way. And the lean is multiplied by `engaged`, which
 * springs to 1 only while `dragging` is true.
 *
 * So the control believed a finger was still down on it, long after one was.
 * `onEnd` never ran.
 *
 * ### Why the Text size screen, and nothing else
 *
 * A gesture's `onEnd` is called when the pointer lifts **and** when the gesture
 * is cancelled — that was already true. What it cannot survive is the whole
 * pointer-input coroutine being cancelled, because the bookkeeping sat after the
 * loop rather than in a `finally`.
 *
 * Compose cancels it for one reason that has nothing to do with pointers:
 * `SuspendingPointerInputModifierNode` resets its handler when the node's
 * `Density` changes, and a `Density` carries the **font scale**. A Text size
 * screen is therefore the one place in an app where using a control changes the
 * density the control is laid out with, in the middle of using it. "Only
 * sometimes" is "only on that screen, and only if the pointer is still down when
 * the new scale lands".
 *
 * ### Measured against itself
 *
 * There is no constant to compare a thumb against — a segment's width depends on
 * the type scale that has just changed, and the padding around the track is
 * private. So the same gesture is performed twice at the same two type scales,
 * differing only in **when** the scale changes: under the finger, or after it
 * has lifted. Both end on the same selection at the same size, so both have to
 * end with the thumb in the same place. One of them used to leave it stranded a
 * fifth too wide.
 */
class StrandedThumbTest {

    @Test
    fun aTypeSizeChangeUnderAFingerDoesNotStrandTheThumb() {
        val interrupted = gesture(changeScaleUnderTheFinger = true)
        val settled = resting(interrupted.selected)

        assertTrue(
            settled.last - settled.first > MinimumThumb,
            "a control that was never touched drew a ${settled.last - settled.first}px " +
                "thumb, so this measured nothing",
        )
        assertTrue(
            abs(interrupted.thumb.first - settled.first) <= Tolerance &&
                abs(interrupted.thumb.last - settled.last) <= Tolerance,
            "after a drag whose type size changed under the finger, the thumb is " +
                "at ${interrupted.thumb.first}..${interrupted.thumb.last} on " +
                "segment ${interrupted.selected}. A control showing that same " +
                "segment, never touched, draws it at ${settled.first}.." +
                "${settled.last}. Wherever the drag ended, the thumb belongs on " +
                "the segment — one that is wider and sits off it is a control " +
                "that still believes a finger is down, because the density change " +
                "cancelled the gesture's coroutine and the end of the gesture was " +
                "the line after the loop.",
        )
    }

    /**
     * And the drag that was not interrupted is unaffected.
     *
     * The control for the control. Wrapping a gesture in a `finally` is the kind
     * of change that can quietly fire the end of a drag twice, or fire it on a
     * press that never became one — both of which would show up here as a
     * selection that did not move or a thumb that did not travel.
     */
    @Test
    fun anUninterruptedDragStillLandsWhereTheFingerDid() {
        val clean = gesture(changeScaleUnderTheFinger = false)
        val settled = resting(LastSegment)

        assertEquals(
            LastSegment,
            clean.selected,
            "a drag from the first segment to the last selected segment " +
                "${clean.selected}",
        )
        assertTrue(
            abs(clean.thumb.first - settled.first) <= Tolerance &&
                abs(clean.thumb.last - settled.last) <= Tolerance,
            "after an ordinary drag the thumb is at ${clean.thumb.first}.." +
                "${clean.thumb.last}, where an untouched control draws the same " +
                "segment at ${settled.first}..${settled.last}",
        )
    }

    /** The thumb of a control showing [selected], at the larger type size, never touched. */
    private fun resting(selected: Int): IntRange {
        var bounds = Rect.Zero
        return Scene(width = 700, height = 240) {
            Box(Modifier.fillMaxSize().background(Color.White).padding(20.dp)) {
                SegmentedControl(
                    options = listOf("One", "Two", "Three"),
                    selectedIndex = selected,
                    onSelectedIndexChange = {},
                    modifier = Modifier.width(240.dp).reportBounds { bounds = it },
                )
            }
        }.use { scene ->
            scene.frames(3)
            scene.typeScale(LargeType)
            val shot = scene.frames(Settle)
            val row = bounds.center.y.toInt()
            val track = shot.getRGB((bounds.left + TrackProbe).toInt(), row)
            requireNotNull(shot.fillRun(bounds, row, track)) {
                "no thumb found on an untouched control"
            }
        }
    }

    /**
     * Drags across the control, changes the type size on the way, lifts, settles.
     *
     * A **drag** rather than a tap, because that is what the screen in the report
     * is: a Text size control's segments are type scales, so a finger moving
     * across it changes the density it is being laid out with, repeatedly, while
     * it is still down. A tap changes the scale too, but only after the finger
     * has gone.
     *
     * [changeScaleUnderTheFinger] is the whole variable. Both runs perform the
     * same drag and end at the same type size on the same segment; they differ
     * only in whether the size lands while the pointer is down.
     */
    private fun gesture(changeScaleUnderTheFinger: Boolean): Outcome {
        var selected by mutableStateOf(0)
        var bounds = Rect.Zero

        return Scene(width = 700, height = 240) {
            Box(Modifier.fillMaxSize().background(Color.White).padding(20.dp)) {
                SegmentedControl(
                    options = listOf("One", "Two", "Three"),
                    selectedIndex = selected,
                    onSelectedIndexChange = { selected = it },
                    modifier = Modifier.width(240.dp).reportBounds { bounds = it },
                )
            }
        }.use { scene ->
            scene.frames(3)

            val from = bounds.alongX(FirstSegment)
            val to = bounds.alongX(LastSegmentCentre)
            scene.press(from)
            // Stopped just *past* a boundary rather than half way across, and
            // that is the difference between a test and a coincidence. The
            // thumb's lean is its distance from the selected segment's centre, so
            // a finger that stops on a centre leans by nothing and a stranded
            // control looks exactly like a settled one. A finger that has just
            // crossed into a segment is as far from its centre as it ever gets —
            // and it is also the moment a Text size control changes the scale.
            walkTo(scene, from, to, 0, CrossOver)

            if (changeScaleUnderTheFinger) {
                scene.typeScale(LargeType)
                scene.frames(4)
                walkTo(scene, from, to, CrossOver, Steps)
                scene.release(to)
            } else {
                walkTo(scene, from, to, CrossOver, Steps)
                scene.release(to)
                scene.frames(4)
                scene.typeScale(LargeType)
            }

            val settled = scene.frames(Settle)
            val row = bounds.center.y.toInt()
            val track = settled.getRGB((bounds.left + TrackProbe).toInt(), row)
            val thumb = requireNotNull(settled.fillRun(bounds, row, track)) {
                "no thumb found after the gesture"
            }
            Outcome(selected, thumb)
        }
    }

    /** Moves from step [at] to step [until] of the drag, rendering each, without lifting. */
    private fun walkTo(scene: Scene, from: Offset, to: Offset, at: Int, until: Int) {
        for (step in (at + 1)..until) {
            val t = step.toFloat() / Steps
            scene.move(Offset(from.x + (to.x - from.x) * t, from.y))
            scene.frame()
        }
    }

    /** What the control settled on, and where it drew its thumb. */
    private data class Outcome(val selected: Int, val thumb: IntRange)

    private companion object {
        /** Inside the first segment, and clear of its label. */
        const val FirstSegment = 0.12f

        /** The last of three segments, at its middle — see `EndStopSquashTest`. */
        const val LastSegmentCentre = 0.82f

        /** Moves across the whole control. */
        const val Steps = 16

        /**
         * The step the type size changes on.
         *
         * A drag from 0.12 to 0.82 of the control crosses into the middle segment
         * around step five, so six is just inside it — about a sixth of the
         * control from that segment's centre, which is most of the lean a thumb
         * can have.
         */
        const val CrossOver = 6

        /** Where an uninterrupted drag across the whole control has to end. */
        const val LastSegment = 2

        /** A reader who has turned the type size up, as the report's screen does. */
        const val LargeType = 1.25f

        /** Long enough for the thumb's travel and the release spring to land. */
        const val Settle = 60

        /** Far enough in to be the track and not the control's border. */
        const val TrackProbe = 30f

        /** Narrower than this is not a segment in a 240dp control. */
        const val MinimumThumb = 80

        /** Two antialiased edges and the odd rounded pixel. */
        const val Tolerance = 3
    }
}
