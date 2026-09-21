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
import io.kontour.ui.components.selection.RangeSlider
import io.kontour.ui.components.selection.SegmentedControl
import io.kontour.ui.components.selection.Slider
import io.kontour.ui.components.selection.Switch
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Pushing a control into its end stop makes the thumb **shorter**, not longer.
 *
 * The first rubber band grew it. That was built to the word "stretching" in the
 * report and the report meant the other thing: something pushed into a wall it
 * cannot pass squashes against the wall, the way anything pressed into an
 * immovable object does. What it did instead was elongate *backwards*, away from
 * the stop — because the band was summed into the reach, and the reach exists to
 * make a thumb grow toward where it is trying to get to, which at an end stop is
 * nowhere.
 *
 * ### Two claims, and the second is the one the first version got wrong
 *
 * **Shorter**: the thumb's drawn width falls while the finger pushes past.
 *
 * **Pinned**: its leading edge does not move, so what comes in is the trailing
 * one. That is what separates a squash from a shrink — a thumb that got smaller
 * symmetrically would pull away from the end it had just run into, which reads as
 * retreating rather than as being compressed.
 *
 * Every measurement is taken with the finger **still down** and with the thumb
 * already at the wall before the push begins, so the two frames differ by the
 * band and by nothing else: a released control springs back, and the press growth
 * and the travel stretch would both be mid-animation in the difference.
 */
class EndStopSquashTest {

    /**
     * A slider's thumb, driven past the right-hand end of its track.
     *
     * The baseline is a **press** at the far end rather than a drag to it, and
     * that is the whole reason this measurement is readable. A drag that arrives
     * at the end has already overshot it by the time it gets there — the fraction
     * is clamped and the remainder is in the band — so a thumb "resting at the
     * wall" at the end of a drag is a thumb that is already squashed. A press
     * emits its value from `onStart` and produces no delta at all, so the thumb
     * travels to the end, grows under the finger, and sits there with the band at
     * zero.
     *
     * Continuous rather than stepped for the same kind of reason: a stepped thumb
     * is held at its detent while the finger travels, so it carries a reach of its
     * own and the two signals would be mixed in the one number this is isolating.
     */
    @Test
    fun aSliderThumbShortensAgainstTheEndOfItsTrack() {
        var value by mutableStateOf(0.5f)
        var bounds = Rect.Zero

        Scene(width = 700, height = 240) {
            Box(Modifier.fillMaxSize().background(Color.White).padding(20.dp)) {
                Slider(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier.width(200.dp).reportBounds { bounds = it },
                )
            }
        }.use { scene ->
            scene.frames(3)
            assertTrue(bounds.width > 0f, "the slider never reported a size")

            val press = Offset(bounds.right - 2f, bounds.center.y)
            scene.press(press)
            val atWall = requireNotNull(scene.frames(Settle).thumbRun(bounds)) {
                "no thumb found at the end of the track"
            }

            walk(scene, press, Offset(bounds.right + Overshoot, bounds.center.y))
            val pushed = requireNotNull(scene.frames(2).thumbRun(bounds)) {
                "no thumb found while pushing past the end of the track"
            }
            scene.release(Offset(bounds.right + Overshoot, bounds.center.y))

            val before = atWall.last - atWall.first + 1
            val after = pushed.last - pushed.first + 1
            assertTrue(
                after < before - Tolerance,
                "the thumb was ${before}px wide resting against the end of the " +
                    "track and ${after}px wide while the finger pushed past it. " +
                    "Pushing into a wall that will not move squashes the thing " +
                    "doing the pushing; this one did not give at all, or it grew.",
            )
            assertTrue(
                abs(pushed.last - atWall.last) <= Tolerance,
                "the thumb reached column ${atWall.last} at the end stop and column " +
                    "${pushed.last} while being pushed past it. The leading edge is " +
                    "against the wall and the wall does not move — an edge that " +
                    "retreats is the thumb shrinking away from the stop rather than " +
                    "compressing into it.",
            )
        }
    }

    /**
     * A squashed thumb is an egg: round against the wall, squashed on the far side.
     *
     * The rest of this file asks how *wide* the thumb is. This asks what shape it
     * is, and it is the half a width measurement cannot see.
     *
     * ### Three shapes, each one the answer to the last
     *
     * It was **four radii**, the cap against the wall keeping the resting radius
     * and the trailing corners shrinking — *"only the half of the circle that's on
     * the opposite side to the way the user is dragging gets squashed"*. That
     * shipped and came back: held and fully squashed the thumb is 18dp across and
     * 30dp tall, so the trailing radius works out at 3dp against the leading 15.
     * Half a circle against a corner that is nearly square is a cut, not a squash.
     *
     * Then **one ellipse**, both ends — *"make it squash more to a vertical
     * ellipse pressed up against the end stop, rather than flattening the end."*
     * An ellipse has no end to chop. But it goes pointy on the end that is
     * *touching*, and a ball pressed into a wall does not: *"we want to keep the
     * side of the head that's pressed up against the edge circular, but we want to
     * squash the other side in a bit."*
     *
     * So now the wall side keeps the cap it rests as and only the free side
     * squashes. This test is what stops the next change from quietly going back to
     * either of the first two.
     *
     * ### Measured as an implied radius at each end
     *
     * A column `d` pixels inside an end has an ink height, and half of that is how
     * far the outline has climbed from the tip. An end that is an arc of radius `R`
     * gives `h² = 2Rd - d²`, so every column implies an `R` — and the two ends
     * implying **different** ones is the whole claim.
     *
     * | | wall end | free end | free ÷ wall |
     * |---|---|---|---|
     * | four radii, a cut at the free end | 30px | 6px | 0.2 |
     * | one ellipse, what this replaces | 48px | 48px | 1.0 |
     * | an egg | 42.5px | 86.5px | **2.0** |
     *
     * The free end reading *larger* is not a mistake. A tall narrow half ellipse
     * is flat where it is widest and sharp where it is tallest — the opposite way
     * round from how it reads at a glance — so squashing the free side flattens
     * its tip and leaves the wall's cap the rounder of the two. What matters is
     * that the two disagree at all: one ellipse cannot, because the same ellipse
     * is at both ends of it.
     *
     * Measured on the *fill* rather than the silhouette, because the ring around
     * it is the page's own colour and the page is what "ink" is measured against.
     * Which is the honest thing to measure anyway: it is the inner shape that a
     * reader sees as the thumb.
     */
    @Test
    fun aSquashedThumbIsAnEggAndNotAnEllipse() {
        var value by mutableStateOf(0.5f)
        var bounds = Rect.Zero
        var wallEnd = 0f
        var freeEnd = 0f

        Scene(width = 700, height = 240) {
            Box(Modifier.fillMaxSize().background(Color.White).padding(20.dp)) {
                Slider(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier.width(200.dp).reportBounds { bounds = it },
                )
            }
        }.use { scene ->
            scene.frames(3)
            assertTrue(bounds.width > 0f, "the slider never reported a size")

            val press = Offset(bounds.right - 2f, bounds.center.y)
            scene.press(press)
            scene.frames(Settle)

            val past = Offset(bounds.right + Overshoot, bounds.center.y)
            walk(scene, press, past)
            val shot = scene.frames(2)
            val pushed = requireNotNull(shot.thumbRun(bounds)) { "no thumb while pushing" }
            wallEnd = shot.impliedRadius(pushed.last - Probe, bounds, Probe)
            freeEnd = shot.impliedRadius(pushed.first + Probe, bounds, Probe)
            scene.release(past)
        }

        assertTrue(
            freeEnd > wallEnd * EndRatio,
            "pushed into the stop, the thumb's wall end implies a radius of " +
                "${wallEnd}px and its free end ${freeEnd}px, a ratio of " +
                "${freeEnd / wallEnd}. One ellipse implies the same at both and comes " +
                "out at 1.0 — which is the shape that squashed the end it was " +
                "pressing against as hard as the end it was not. A cut free end " +
                "comes out below 1 the other way",
        )
        assertTrue(
            wallEnd > CutEnd,
            "the wall end implies a radius of ${wallEnd}px. The end against the wall " +
                "is the one that keeps the cap it rests as, so this is the reading " +
                "that catches a squash eating into it",
        )
    }

    /**
     * A segmented control's thumb, driven past its last segment.
     *
     * This is the control the squash changed the *anchor* of, and the trailing
     * edge is what says whether the anchor is right. A stretch pivots on the edge
     * the thumb is leaving so it reaches toward where it is going; a squash pivots
     * on the edge against the wall so it shortens into it. Those are opposite
     * sides, and the two used to be read off one signed sum — which picks the
     * wrong one for whichever of them is non-zero. Get it backwards and the thumb
     * still narrows by exactly as much, from the wrong end.
     *
     * So the measurement is the **left** edge, and only the left edge. The right
     * one is flush with the end of the track, where the control's own border and
     * the indicator's shadow are within a few pixels of each other and of the
     * page — a reading there says as much about which of the three a row of
     * pixels belongs to as it does about the thumb. `Slider` above pins the
     * leading edge out in the open, where there is nothing else to confuse it
     * with.
     *
     * The baseline is a drag to the **centre of the last segment**, which is
     * inside the track: nothing is refused there, so the band is at rest, and the
     * finger being on the segment's centre leaves no lean either.
     */
    @Test
    fun aSegmentedThumbShortensFromItsTrailingEdgeAtTheEnd() {
        var selected by mutableStateOf(0)
        var bounds = Rect.Zero

        Scene(width = 700, height = 240) {
            Box(Modifier.fillMaxSize().background(Color.White).padding(20.dp)) {
                SegmentedControl(
                    options = listOf("One", "Two", "Three"),
                    selected = selected,
                    onSelectedChange = { selected = it },
                    modifier = Modifier.width(240.dp).reportBounds { bounds = it },
                )
            }
        }.use { scene ->
            scene.frames(3)
            assertTrue(bounds.width > 0f, "the segmented control never reported a size")

            val from = bounds.alongX(FromSegment)
            val centre = bounds.alongX(LastSegmentCentre)
            scene.press(from)
            walk(scene, from, centre)

            val row = bounds.center.y.toInt()
            val settled = scene.frames(Settle)
            val track = settled.getRGB((bounds.left + TrackProbe).toInt(), row)
            val atWall = requireNotNull(settled.fillRun(bounds, row, track)) {
                "no thumb found on the last segment"
            }

            walk(scene, centre, Offset(bounds.right + Overshoot, bounds.center.y))
            val pushed = requireNotNull(scene.frames(2).fillRun(bounds, row, track)) {
                "no thumb found while pushing past the last segment"
            }
            scene.release(Offset(bounds.right + Overshoot, bounds.center.y))

            assertTrue(
                pushed.first > atWall.first + Tolerance,
                "the thumb's left edge was at column ${atWall.first} resting on the " +
                    "last segment and column ${pushed.first} while the finger pushed " +
                    "past the end of the track. It has to come in: the right edge is " +
                    "against the wall, so a thumb that gives at all gives from this " +
                    "side. An edge that stays put is a squash anchored on the wrong " +
                    "one — the stretch's anchor, which is the opposite side.",
            )
        }
    }

    /**
     * And it can be dragged back without letting go.
     *
     * Reported in as many words: *"if I drag far enough and it gets squashed,
     * then I can't drag it back the other way without first letting go, and then
     * grabbing again"*. Letting go was not a workaround — it was the only thing
     * that reset the accumulator.
     *
     * `RubberBand.payBack` did its half correctly: a finger coming home closes
     * the stretch it opened before the content moves again. What undid it was
     * the very next line, which re-pulled the band to its limit from an
     * overshoot that had not changed, because `fingerX` was still far past the
     * track. The band closed and reopened on the same frame, forever.
     *
     * So this drags well past the end, then drags back **without lifting**, and
     * asks whether the thumb moved. It is deliberately a different question from
     * the squash tests above: those ask what the thumb's shape does, and this
     * asks whether the control is answering at all.
     *
     * ### A progression ladder was tried here and does not work
     *
     * [aSliderThumbSquashesFurtherTheFurtherItIsPushed] is the natural companion
     * and it was written for this control first. It passed on the defect, which
     * is worse than not existing, and the reason is worth keeping so nobody
     * writes it again.
     *
     * Two things defeat it. The thumb is a *surface* a few greys from the well
     * it sits in, so the only threshold that can find its edge is one low enough
     * to find its **shadow** too — at a firmer threshold the widest run across
     * the row is a thirteen-pixel label glyph that never moves — and the
     * shadow's gradient crosses that threshold at a place that wobbles several
     * pixels as the thumb lands on different subpixels. That is a third of the
     * whole 24px signal.
     *
     * And the compounding does not show up between two depths anyway. The old
     * arithmetic pulled by the *total* overshoot each frame, so it ran away only
     * while the finger kept moving past the stop — which is also when the
     * correct arithmetic is pulling hardest. Both reach the limit; the broken
     * one just gets there sooner. What separates them is what happens when the
     * finger comes **back**, which is this test.
     */
    @Test
    fun aSegmentedThumbComesBackWithoutLettingGo() {
        segmented { scene, bounds, row, track ->
            val from = bounds.alongX(FromSegment)
            val past = Offset(bounds.right + Overshoot, bounds.center.y)
            scene.press(from)
            walk(scene, from, past)
            val pushed = requireNotNull(scene.frames(2).fillRun(bounds, row, track)) {
                "no thumb found while pushing past the end of the track"
            }

            // Back to the first segment, still down.
            walk(scene, past, from)
            val returned = requireNotNull(scene.frames(Settle).fillRun(bounds, row, track)) {
                "no thumb found after dragging back"
            }
            scene.release(from)

            assertTrue(
                returned.first < pushed.first - Segment,
                "the thumb sat at ${pushed.first}..${pushed.last} pushed past the " +
                    "end of the track, and at ${returned.first}..${returned.last} " +
                    "after the finger came all the way back to the first segment " +
                    "without lifting. It has not moved a segment's width, so the " +
                    "control is not answering the finger any more: the band is " +
                    "being refilled from a stale overshoot on every frame that " +
                    "pays it back.",
            )
        }
    }

    /**
     * A segmented control, its probe row, and the track's own colour.
     *
     * Three tests want the same scene and the same two readings off it, and the
     * readings are the fiddly part — the track's colour has to be sampled from a
     * column the thumb is not on, or the run being measured is "everything that
     * is not the thumb".
     *
     * So the probe is at the **far** end. Every one of these starts on the first
     * segment, which is where the thumb is at the moment the colour is read; the
     * first draft probed the near end, read the thumb's own fill as the track,
     * and measured the complement of the thumb — a 315px "thumb" in a control
     * whose segments are 144px, and every reading the wrong way round.
     */
    private fun segmented(
        body: (scene: Scene, bounds: Rect, row: Int, track: Int) -> Unit,
    ) {
        var selected by mutableStateOf(0)
        var bounds = Rect.Zero

        Scene(width = 700, height = 240) {
            Box(Modifier.fillMaxSize().background(Color.White).padding(20.dp)) {
                SegmentedControl(
                    options = listOf("One", "Two", "Three"),
                    selected = selected,
                    onSelectedChange = { selected = it },
                    modifier = Modifier.width(240.dp).reportBounds { bounds = it },
                )
            }
        }.use { scene ->
            scene.frames(3)
            assertTrue(bounds.width > 0f, "the segmented control never reported a size")
            val row = bounds.center.y.toInt()
            val track = scene.frames(1).getRGB((bounds.right - TrackProbe).toInt(), row)
            body(scene, bounds, row, track)
        }
    }

    /**
     * And it goes on squashing for as long as the finger goes on pushing.
     *
     * The report: *"it feels like there's just 2 states at the moment: squashed
     * or normal"*. It very nearly was. The band's limit was a fraction of the
     * thumb — about 6.6dp on a slider, 6dp on a switch — and a finger crosses
     * that inside one frame, so the deformation went from nothing to everything
     * between two renders and stayed there however much further the hand went.
     *
     * Four depths rather than two, because two cannot tell a gradient from a
     * step: `aSliderThumbShortensAgainstTheEndOfItsTrack` above passes on the
     * old arithmetic and always did. Each of these has to be visibly narrower
     * than the one before it, which on the old limit is false from the second
     * sample on — 32px past the stop was already at the clamp.
     *
     * The depths are not evenly spaced and should not be. The response is
     * `1 - exp(-travel / EndStopTravel)`, so even steps of *finger* would give
     * ever-smaller steps of *thumb* and the last pair would come down to
     * antialiasing. These are roughly even in the fraction they reach — about a
     * fifth, a half, three quarters, and most of the way.
     */
    /**
     * Letting go does not pass through the full-width pill on the way home.
     *
     * Reported, of the slider: *"make sure it smoothly animates back to the
     * little circle when the user lets go, rather than snapping to the
     * full-width pill before animating back."*
     *
     * The thumb's drawn width is two animations multiplied together and they
     * came home on different springs — the squash on `springSnappy`, the stretch
     * on `springBouncy`, which is slower and oscillates. `sliderThumb` draws
     * `width·(1−pull) + 1.5r·pull`, so `pull` reached zero while `width` was
     * still `2r·1.5`: the thumb left its squashed 1.5r, went **through** the
     * full 3r pill, and only then came home to 2r. See `Slider`'s `thumbReturn`.
     *
     * ### Sampled every frame, and the resting width is the line
     *
     * The excursion is a handful of frames wide, so a test that looks at the
     * shape once after letting go will miss it — the first attempt at this
     * sampled every few frames and read 30, 40, 30, 36 against a resting 36,
     * which is the defect but only barely. This walks the frames one at a time
     * and takes the widest.
     *
     * The line is the thumb's own resting width, measured before anything is
     * touched, plus the usual tolerance. A thumb coming out of a squash is
     * *narrower* than resting and has to grow back to it; what it must not do is
     * overshoot on the way, and after the fix the arithmetic peaks 3% over.
     */
    @Test
    fun aReleasedThumbDoesNotSwellPastItsRestingWidth() {
        var value by mutableStateOf(0.5f)
        var bounds = Rect.Zero

        Scene(width = 700, height = 240) {
            Box(Modifier.fillMaxSize().background(Color.White).padding(20.dp)) {
                Slider(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier.width(200.dp).reportBounds { bounds = it },
                )
            }
        }.use { scene ->
            scene.frames(3)
            assertTrue(bounds.width > 0f, "the slider never reported a size")

            val resting = requireNotNull(scene.frames(Settle).thumbRun(bounds)) {
                "no thumb found at rest"
            }.width()

            val press = Offset(bounds.right - 2f, bounds.center.y)
            scene.press(press)
            val pushedTo = Offset(bounds.right + Overshoot, bounds.center.y)
            walk(scene, press, pushedTo)
            val squashed = requireNotNull(scene.frames(2).thumbRun(bounds)) {
                "no thumb found while pushing past the end of the track"
            }.width()
            assertTrue(
                squashed < resting,
                "the thumb was ${squashed}px wide pushed into the end stop against " +
                    "${resting}px at rest, so the gesture never squashed it and " +
                    "there is no release for this to be measuring",
            )

            scene.release(pushedTo)
            val path = (0 until ReleaseFrames).map {
                requireNotNull(scene.frame().thumbRun(bounds)) {
                    "no thumb found on the way back from the end stop"
                }.width()
            }

            val widest = path.max()
            assertTrue(
                widest <= resting + Tolerance,
                "on the way back from a squash the thumb reached ${widest}px " +
                    "against a resting width of ${resting}px. It starts this " +
                    "journey at ${squashed}px and its destination is ${resting}px, " +
                    "so anything wider is the stretch it was let go from " +
                    "re-inflating it — the full-width pill that was reported. " +
                    "The widths, frame by frame: $path",
            )
            assertTrue(
                abs(path.last() - resting) <= Tolerance,
                "the thumb finished at ${path.last()}px rather than back at its " +
                    "resting ${resting}px, so it has not animated home at all and " +
                    "the assertion above proves nothing",
            )

            // And it only ever widens, which is the report in its own words:
            // *"smoothly animates back to the little circle"* rather than out to
            // something and back. A pixel of slack for the rounding — the run is
            // counted in whole columns off a rendered frame.
            val wentBack = path.zipWithNext().firstOrNull { (a, b) -> b < a - 1 }
            assertTrue(
                wentBack == null,
                "the thumb went from ${wentBack?.first}px to ${wentBack?.second}px " +
                    "on its way back from the end stop, so it widened past where " +
                    "it was going and came back — which is the full-width pill " +
                    "even when the peak stays under the resting width. " +
                    "The widths, frame by frame: $path",
            )
        }
    }

    /**
     * And a range slider's does the same, which it did not.
     *
     * **The first test in this file to touch `RangeSlider` at all**, and that is
     * why the bug it catches survived a fix written for it. `EndStopSquashTest`
     * never mentioned the control; the one range-slider test that pushes past a
     * wall reads `range.start`, a value, and the two that measure its *shape* drag
     * to 0.95 of the bounds — inside the track, so the band is never charged. No
     * test both charged the band and looked at a frame after release.
     *
     * What that hid: `RangeSlider` was given the same `springGentle` band release
     * as `Slider`, and never showed it. The squash was gated on `activeThumb`,
     * which `onEnd` clears on the same tick it launches the release — so the
     * spring ran, and every frame of it drew zero squash. `pull` stepped from
     * nearly one to exactly zero between two frames while the thumb was still a
     * stretched capsule, which is the full-width pill the slider's fix exists to
     * remove, except arrived at in a single frame rather than animated. See
     * `RangeSlider`'s `bandThumb`.
     *
     * The end thumb, pushed past the right-hand end of the track: that is where
     * `past` is non-zero and the band is actually charged. The measurement is the
     * same as the slider's above — the widest run of thumb on the centre row —
     * and so are both claims.
     */
    @Test
    fun aReleasedRangeThumbDoesNotSwellPastItsRestingWidth() {
        var range by mutableStateOf(0.3f..0.7f)
        var bounds = Rect.Zero

        Scene(width = 700, height = 240) {
            Box(Modifier.fillMaxSize().background(Color.White).padding(20.dp)) {
                RangeSlider(
                    value = range,
                    onValueChange = { range = it },
                    modifier = Modifier.width(200.dp).reportBounds { bounds = it },
                )
            }
        }.use { scene ->
            scene.frames(3)
            assertTrue(bounds.width > 0f, "the range slider never reported a size")

            // **Only the right half of the canvas.** There are two thumbs, and
            // `thumbRun` returns the *widest* run of thumb-deep ink — which is the
            // start thumb, sitting still at 0.3 and reporting the same width before
            // and after, so the precondition below passed on nothing. The start
            // thumb is at 0.3 and the end one at 0.7, so half the control
            // separates them; the window runs to the canvas edge rather than the
            // control's, because a stretched thumb pushed into an end stop
            // overhangs its own bounds. See [thumbRun].
            val rightHalf = (bounds.left + bounds.width / 2f).toInt() until scene.width

            val resting = requireNotNull(scene.frames(Settle).thumbRun(bounds, rightHalf)) {
                "no thumb found at rest in the right half of the control"
            }.width()

            // Grab the end thumb rather than the track: a press on a range slider
            // picks a thumb and does not move one, so the gesture has to start on
            // the one being tested.
            val press = Offset(bounds.left + bounds.width * 0.7f, bounds.center.y)
            scene.press(press)
            val pushedTo = Offset(bounds.right + Overshoot, bounds.center.y)
            walk(scene, press, pushedTo)
            val squashed = requireNotNull(scene.frames(2).thumbRun(bounds, rightHalf)) {
                "no thumb found while pushing past the end of the track"
            }.width()
            assertTrue(
                squashed < resting,
                "the end thumb was ${squashed}px wide pushed past the end of the " +
                    "track against ${resting}px at rest, so the band was never " +
                    "charged and there is no release for this to be measuring",
            )

            scene.release(pushedTo)
            val path = (0 until ReleaseFrames).map {
                requireNotNull(scene.frame().thumbRun(bounds, rightHalf)) {
                    "no thumb found on the way back from the end stop"
                }.width()
            }

            val widest = path.max()
            assertTrue(
                widest <= resting + Tolerance,
                "on the way back from a squash the end thumb reached ${widest}px " +
                    "against a resting width of ${resting}px. It starts at " +
                    "${squashed}px and its destination is ${resting}px, so " +
                    "anything wider is the stretch it was let go from re-inflating " +
                    "it. The widths, frame by frame: $path",
            )
            val wentBack = path.zipWithNext().firstOrNull { (a, b) -> b < a - 1 }
            assertTrue(
                wentBack == null,
                "the end thumb went from ${wentBack?.first}px to " +
                    "${wentBack?.second}px on its way home, so it widened past " +
                    "where it was going and came back. The widths, frame by " +
                    "frame: $path",
            )
        }
    }

    @Test
    fun aSliderThumbSquashesFurtherTheFurtherItIsPushed() {
        var value by mutableStateOf(0.5f)
        var bounds = Rect.Zero

        Scene(width = 700, height = 240) {
            Box(Modifier.fillMaxSize().background(Color.White).padding(20.dp)) {
                Slider(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier.width(200.dp).reportBounds { bounds = it },
                )
            }
        }.use { scene ->
            scene.frames(3)
            assertTrue(bounds.width > 0f, "the slider never reported a size")

            val press = Offset(bounds.right - 2f, bounds.center.y)
            scene.press(press)
            var at = press
            var previous = requireNotNull(scene.frames(Settle).thumbRun(bounds)) {
                "no thumb found at the end of the track"
            }.width()

            val widths = mutableListOf(previous)
            for (depth in Depths) {
                val to = Offset(bounds.right + depth, bounds.center.y)
                walk(scene, at, to)
                at = to
                val width = requireNotNull(scene.frames(2).thumbRun(bounds)) {
                    "no thumb found ${depth}px past the end of the track"
                }.width()
                widths += width
                assertTrue(
                    width < previous - Gradient,
                    "pushed ${depth}px past the end the thumb is ${width}px wide, " +
                        "against ${previous}px at the depth before it. The squash " +
                        "has to keep answering the finger: widths so far are " +
                        "$widths. Equal numbers from some depth on mean the band " +
                        "has hit a limit small enough to cross in one frame, which " +
                        "is the two-state squash that was reported.",
                )
                previous = width
            }

            scene.release(at)
        }
    }

    /**
     * A switch's thumb ends up narrower than it is tall.
     *
     * The other half of the report, and the sharper half: *"the switch head
     * doesn't get narrower than a circle"*. It could not. The squash was a sixth
     * off the **stretched** width, and the stretch under a finger is up to
     * 1.25x — so the deepest push worked out at `24 x 1.25 x 0.84`, which is
     * 25.2dp against a resting 24. The two factors cancelled, and the hardest
     * shove made the thumb very slightly *wider* than it is at rest.
     *
     * So the claim is put in terms nothing about the implementation can satisfy
     * by accident: the drawn thumb has to be narrower **than the same thumb is
     * tall, in the same frame**. Both are measured off the pixels, so no
     * constant, density or token is being trusted — a circle fails this, and
     * anything wider than a circle fails it by more.
     */
    @Test
    fun aSwitchThumbGetsNarrowerThanItIsTall() {
        var checked by mutableStateOf(false)
        var bounds = Rect.Zero

        Scene(width = 400, height = 200) {
            Box(Modifier.fillMaxSize().background(Color.White).padding(20.dp)) {
                Switch(
                    checked = checked,
                    onCheckedChange = { checked = it },
                    modifier = Modifier.reportBounds { bounds = it },
                )
            }
        }.use { scene ->
            scene.frames(3)
            assertTrue(bounds.width > 0f, "the switch never reported a size")

            val from = bounds.alongX(SwitchGrab)
            val past = Offset(bounds.right + Overshoot, bounds.center.y)
            scene.press(from)
            walk(scene, from, past)

            val row = bounds.center.y.toInt()
            val shot = scene.frames(2)
            // The far end of the track, which the thumb has left: the switch is
            // on by now and the thumb is jammed against the other side.
            val track = shot.getRGB((bounds.left + SwitchProbe).toInt(), row)
            val thumb = requireNotNull(shot.fillRun(bounds, row, track, SwitchProbe)) {
                "no thumb found while pushing past the end of the switch"
            }
            scene.release(past)

            val width = thumb.width()
            val height = shot.runDown(bounds, (thumb.first + thumb.last) / 2, track)
            assertTrue(
                width < height - Tolerance,
                "pushed hard into the end of its track the thumb is ${width}px " +
                    "wide and ${height}px tall. A thumb that cannot get narrower " +
                    "than its own height cannot get narrower than the circle it " +
                    "rests as, which is what a squash on the already-stretched " +
                    "width does: the press growth cancels it exactly.",
            )
        }
    }

    /** Moves from [from] to [to] over [Steps] moves, rendering each, without lifting. */
    private fun walk(scene: Scene, from: Offset, to: Offset) {
        repeat(Steps) { step ->
            val t = (step + 1).toFloat() / Steps
            scene.move(Offset(from.x + (to.x - from.x) * t, from.y + (to.y - from.y) * t))
            scene.frame()
        }
    }

    private companion object {
        /** Enough to take the band to its limit, and well past the track. */
        const val Overshoot = 140f
        const val Steps = 14

        /**
         * Four pushes past the stop, in pixels at this scene's density 2.
         *
         * Roughly a fifth, a half, three quarters and most of the way out, given
         * a 26dp travel constant — even in the deformation rather than in the
         * finger, which is what keeps the last pair apart.
         */
        val Depths = listOf(12f, 32f, 70f, 150f)

        /** Each step of the pull has to move the thumb by more than an edge. */
        const val Gradient = 2


        /** Inside the switch, on the side the thumb starts. */
        const val SwitchGrab = 0.2f

        /** Past the switch's own rounded end, in pixels. */
        const val SwitchProbe = 6f

        /** Frames for the press growth and the thumb's own spring to land. */
        const val Settle = 24

        /** Inside the first segment, and clear of its label. */
        const val FromSegment = 0.12f

        /**
         * The last of three segments, at its middle.
         *
         * `(12dp of track padding + two and a half segments) / 240dp`, which is
         * where the thumb comes to rest with nothing leaning it either way.
         */
        const val LastSegmentCentre = 0.82f

        /** Far enough in to be the track and not the control's border. */
        const val TrackProbe = 30f

        /** Two antialiased edges and the odd rounded pixel. */
        const val Tolerance = 3

        /**
         * How far inside an end to sample the thumb's height, in pixels.
         *
         * Far enough in that a full cap has curved away appreciably — on a 24px
         * radius, six pixels in is about two thirds of the height — and not so
         * far that a flattened corner has finished curving too.
         */
        /**
         * How far inside each end the shape test reads, in pixels.
         *
         * Four, at this scene's density two, so a dp either way. Near enough to
         * the tip that the implied radius is the tip's own and not the shoulder's,
         * and far enough in that antialiasing is not most of the reading: one
         * pixel in, a half-height of three carries a quarter of a pixel of ramp on
         * each side and the implied radius swings by a third.
         */
        const val Probe = 4

        /**
         * How much flatter the free end has to read than the wall end.
         *
         * Measured at 2.0 — 86.5px against 42.5. One ellipse gives exactly 1,
         * because it is the same ellipse at both ends, so 1.5 sits between them
         * with room for the antialiasing on either reading.
         */
        const val EndRatio = 1.5f

        /**
         * The least the wall end may imply and still be the cap it rests as.
         *
         * Measured at 42.5px, against the 6px a 3dp corner implied when the free
         * end was a cut and the wall end the only round thing left. Thirty is
         * clear of both.
         */
        const val CutEnd = 30f

        /** A third of a 240dp control at density 2, less its padding. Comfortably under a segment. */
        const val Segment = 120


        /**
         * Long enough to contain the whole of the return, sampled one at a time.
         *
         * `springSnappy` is over in about a dozen frames and `springBouncy` — the
         * spring the stretch used to come home on, and the one that made the
         * excursion long as well as tall — takes rather longer. Forty is past
         * both.
         */
        const val ReleaseFrames = 40
    }
}

/**
 * The widest run of columns whose ink is taller than the track, or null.
 *
 * The same reading `SliderStretchTest` takes and for the same reason: the track
 * is 6dp and the thumb 22dp, so depth separates them with nothing ambiguous in
 * between and no density arithmetic anywhere.
 */
/**
 * The widest run of columns deep enough in ink to be a thumb.
 *
 * ### [columns] defaults to the whole canvas, and [bounds] is vertical only
 *
 * Not an oversight, and worth stating because it looks like one. A slider's thumb
 * **overhangs its own control** when it is stretched and pushed into an end stop:
 * the reach adds up to `0.6r` past the track's end and the track is already inset
 * by `SliderThumbReach`, so on the 700px canvas here the thumb reaches column 467
 * against a reported right bound of 440. Clamping the scan to [bounds] cut the
 * leading edge off, and the shape test two above this one went from reading a
 * curve to reading whatever was 2px inside an arbitrary clip — 6px of climb
 * against the real 18.
 *
 * So [columns] is how a caller that needs a window says so. The range slider's
 * case is the one that does: two thumbs on one canvas, and `widest` otherwise
 * returns whichever is wider — the one standing still, which reports the same
 * number before and after and makes a precondition pass on nothing.
 */
private fun BufferedImage.thumbRun(
    bounds: Rect,
    columns: IntRange = 0 until width,
): IntRange? {
    val page = getRGB(2, 2)
    val top = bounds.top.toInt().coerceAtLeast(0)
    val bottom = (bounds.bottom.toInt() - 1).coerceAtMost(height - 1)
    val first = columns.first.coerceAtLeast(0)
    val last = columns.last.coerceAtMost(width - 1)

    var best: IntRange? = null
    var start = -1
    for (x in first..last) {
        var ink = 0
        for (y in top..bottom) if (differs(getRGB(x, y), page)) ink++
        if (ink > TrackDepth) {
            if (start < 0) start = x
        } else if (start >= 0) {
            best = widest(best, start..(x - 1))
            start = -1
        }
    }
    return if (start >= 0) widest(best, start..last) else best
}

/**
 * The widest run of columns on one [row] that is not the [track]'s own colour.
 *
 * For a thumb that is a *fill* rather than a silhouette. A segmented control's
 * indicator is a surface behind a label, so a row across it is the fill and the
 * glyphs — both of which differ from the track, and both of which are the thumb.
 * Other segments' labels differ too and are a few pixels wide each, where the
 * thumb is a whole segment, so the widest run is the one wanted.
 *
 * The scan stops short of the control's edges. The track is inset from them by
 * its padding, and a border and a shadow live in that gap.
 *
 * Internal rather than private because `StrandedThumbTest` reads the same thumb
 * the same way, and two copies of a fill-versus-fill threshold would drift.
 */
internal fun BufferedImage.fillRun(
    bounds: Rect,
    row: Int,
    track: Int,
    margin: Float = Margin,
): IntRange? {
    var best: IntRange? = null
    var start = -1
    val from = (bounds.left + margin).toInt().coerceAtLeast(0)
    val to = (bounds.right - margin).toInt().coerceAtMost(width - 1)
    for (x in from..to) {
        if (differs(getRGB(x, row), track, Faint)) {
            if (start < 0) start = x
        } else if (start >= 0) {
            best = widest(best, start..(x - 1))
            start = -1
        }
    }
    return if (start >= 0) widest(best, start..to) else best
}

/** How tall the fill at column [x] is, in rows that are not the [track]. */
private fun BufferedImage.runDown(bounds: Rect, x: Int, track: Int): Int {
    val top = bounds.top.toInt().coerceAtLeast(0)
    val bottom = (bounds.bottom.toInt() - 1).coerceAtMost(height - 1)
    return (top..bottom).count { y -> differs(getRGB(x, y), track, Faint) }
}

/**
 * How many rows of column [x] hold ink, within [bounds].
 *
 * The page is sampled at a corner rather than assumed white, for the same reason
 * every other reading here does it.
 */
/**
 * The radius of the arc an end would have to be, read off one column.
 *
 * An end that is an arc of radius `R` has climbed to a half-height `h` a distance
 * `d` in from its tip, with `h² = 2Rd - d²`. Rearranged, one column is enough:
 * `R = (h² + d²) / 2d`. It is the same reading `SquashedThumbOutlineTest` takes of
 * the arithmetic, taken here of the pixels instead.
 */
private fun BufferedImage.impliedRadius(x: Int, bounds: Rect, depth: Int): Float {
    val half = inkHeight(x, bounds) / 2f
    val d = depth.toFloat()
    return (half * half + d * d) / (2f * d)
}

private fun BufferedImage.inkHeight(x: Int, bounds: Rect): Int {
    if (x < 0 || x >= width) return 0
    val page = getRGB(2, 2)
    val top = bounds.top.toInt().coerceAtLeast(0)
    val bottom = (bounds.bottom.toInt() - 1).coerceAtMost(height - 1)
    return (top..bottom).count { y -> differs(getRGB(x, y), page) }
}

/** A run's width in columns, ends included. */
private fun IntRange.width(): Int = last - first + 1

private fun widest(a: IntRange?, b: IntRange): IntRange =
    if (a == null || b.last - b.first > a.last - a.first) b else a

/** Taller than the 6dp track at this density, and well under a 22dp thumb. */
private const val TrackDepth = 20

/** Past the control's border and shadow, in pixels. */
private const val Margin = 16f

/**
 * A fill against a fill, rather than ink against a page.
 *
 * A segmented control's thumb and the well it sits in are two greys a few steps
 * apart — the pair `surfaceIndicator` and `surfaceSunken` were tuned to, and the
 * whole reason the thumb carries a shadow. Four is under that and clear of the
 * dither.
 */
private const val Faint = 4

private fun differs(a: Int, b: Int, by: Int = 24): Boolean =
    abs((a shr 16 and 0xFF) - (b shr 16 and 0xFF)) > by ||
        abs((a shr 8 and 0xFF) - (b shr 8 and 0xFF)) > by ||
        abs((a and 0xFF) - (b and 0xFF)) > by
