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
     * A slider's thumb narrows under a push and **does not pass the circle**.
     *
     * The amplitude, which is the half the shape tests cannot see. It was a
     * quarter off the *resting* diameter while the thumb being squashed was the
     * held capsule — 45dp wide against a resting 24, so the target came out at
     * 18dp and a full push took 60% of the thumb, against `Switch`'s 25 for the
     * same gesture and the same constant. Reported as the slider deforming far
     * too much.
     *
     * A fifth off the thumb's **own** width now, which on this control lands it
     * at 36dp against the 30dp it is tall: narrowed, and still a capsule. That is
     * the point of this test rather than an admission — a thumb only becomes an
     * egg once it is narrower than it is tall, and a held slider thumb would have
     * to give up a third of itself to get there. The egg is `Switch`'s business,
     * where the thumb rests as a circle and is one from the first pixel of
     * squash; `SwitchGeometryTest` and `SquashedThumbOutlineTest` are where its
     * shape is pinned.
     *
     * So the two ends here should imply the **same** radius — two intact caps —
     * where the shape that went too far read 42.5px against 86.5px.
     */
    @Test
    fun aSquashedSliderThumbNarrowsWithoutPassingTheCircle() {
        var value by mutableStateOf(0.5f)
        var bounds = Rect.Zero
        var held = 0
        var squashed = 0
        var tall = 0
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
            held = requireNotNull(scene.frames(Settle).thumbRun(bounds)) {
                "no thumb found held at the end of the track"
            }.width()

            val past = Offset(bounds.right + Overshoot, bounds.center.y)
            walk(scene, press, past)
            val shot = scene.frames(2)
            val pushed = requireNotNull(shot.thumbRun(bounds)) { "no thumb while pushing" }
            squashed = pushed.width()
            tall = shot.inkHeight((pushed.first + pushed.last) / 2, bounds)
            wallEnd = shot.impliedRadius(pushed.last - Probe, bounds, Probe)
            freeEnd = shot.impliedRadius(pushed.first + Probe, bounds, Probe)
            scene.release(past)
        }

        assertTrue(
            squashed < held - Tolerance,
            "held at the end of its track the thumb is ${held}px wide and pushed " +
                "past it ${squashed}px — it did not give at all",
        )
        assertTrue(
            squashed >= tall,
            "pushed past the end the thumb is ${squashed}px wide against ${tall}px " +
                "tall, so it has narrowed past the circle and become an egg. A " +
                "fifth off its own width should not get there; a third would, and " +
                "a third is not the bit of deformation this is supposed to be",
        )
        assertTrue(
            abs(freeEnd - wallEnd) <= wallEnd * EndsAlike,
            "the thumb's wall end implies a radius of ${wallEnd}px and its free " +
                "end ${freeEnd}px. While it is still a capsule both ends are the " +
                "same cap, so these have to agree — they read 42.5 against 86.5 " +
                "when the squash was deep enough to make an egg of it",
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
                    selectedIndex = selected,
                    onSelectedIndexChange = { selected = it },
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
     * And it squashes down the other axis too, when it is stacked.
     *
     * The deformation is one piece of arithmetic with the axis chosen at the last
     * line — `translationY`/`scaleY` where a row uses X — so this is the test
     * that the choosing is wired rather than a second test of the squash. It
     * would pass on the unfixed control by accident and does not: stacked, the
     * control had no drag at all, so there was nothing to push past the end.
     *
     * **Measured as the thumb's height at a column near the track's left edge.**
     * Labels are centred, so a column that far out crosses the thumb's fill and
     * nothing else; anywhere nearer the middle and the other rows' glyphs are in
     * the count too.
     */
    @Test
    fun aStackedSegmentedThumbShortensAgainstTheEndOfItsTrack() {
        var selected by mutableStateOf(0)
        var bounds = Rect.Zero

        Scene(width = 560, height = 640, density = 2f) {
            Box(Modifier.fillMaxSize().background(Color.White).padding(16.dp)) {
                SegmentedControl(
                    // **Two options, in a track too narrow for either label.**
                    // Four rows would stack just as well and would not measure
                    // as much: the band is charged by the finger travel the
                    // track *refuses*, which is half a segment, and half of a
                    // quarter of this track is under `EndStopTravel`. With two
                    // rows the refusal saturates the band and the squash is its
                    // full depth rather than half of it — 9px of signal against
                    // 3, on a reading whose noise is a pixel.
                    options = listOf("Keyboard", "Touchscreen"),
                    selectedIndex = selected,
                    onSelectedIndexChange = { selected = it },
                    modifier = Modifier.width(130.dp).reportBounds { bounds = it },
                )
            }
        }.use { scene ->
            scene.frames(3)
            assertTrue(
                bounds.height > StackedFloor,
                "the control measured ${bounds.height}px tall, which is one row: " +
                    "it did not stack, so there is no vertical track to push " +
                    "against and this measured nothing",
            )

            val probe = (bounds.left + StackedProbe).toInt()
            val top = Offset(bounds.center.x, bounds.top + bounds.height * StackedFirst)
            val last = Offset(bounds.center.x, bounds.top + bounds.height * StackedLast)
            val track = scene.frames(1).getRGB((bounds.right - TrackProbe).toInt(), bounds.center.y.toInt())

            // Down to the last row and held there, with nothing refused yet.
            scene.press(top)
            walk(scene, top, last)
            val atWall = scene.frames(Settle).thumbDown(bounds, probe, track)

            // And on past the end of the track.
            val past = Offset(bounds.center.x, bounds.bottom + Overshoot)
            walk(scene, last, past)
            val pushed = scene.frames(2).thumbDown(bounds, probe, track)
            scene.release(past)

            assertTrue(
                atWall > MinimumThumb,
                "the thumb measured ${atWall}px tall at the end of a stacked " +
                    "track, which is not a thumb — this measured nothing",
            )
            assertTrue(
                pushed < atWall - Tolerance,
                "the stacked thumb is ${atWall}px tall resting against the end " +
                    "of its track and ${pushed}px while the finger pushes past " +
                    "it. Stacked, the wall is the bottom of the track and the " +
                    "squash runs down the same axis the drag does",
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
                    selectedIndex = selected,
                    onSelectedIndexChange = { selected = it },
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
     * ### Sampled every frame, and bounded at both ends
     *
     * The excursion is a handful of frames wide, so a test that looks at the
     * shape once after letting go will miss it — the first attempt at this
     * sampled every few frames and read 30, 40, 30, 36 against a resting 36,
     * which is the defect but only barely. This walks the frames one at a time.
     *
     * The lines are the two widths the thumb has been: it must not go above the
     * width it was **held** at, and it must not dip below its **resting** one.
     * Both rather than one, because a fifth off its own width leaves a squashed
     * slider thumb wider than the circle it rests as — so the release comes home
     * by *narrowing*, and a ceiling at the resting width alone would be broken by
     * every frame of a perfectly good return. See `SliderThumb`'s `ThumbSquash`.
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
            // Against the **held** width, not the resting one. A held thumb is
            // half as wide again as the circle it rests as, and the squash is a
            // fifth of *that* — so a squashed thumb under a finger is still
            // wider than an untouched one, and always was on this control. See
            // `ThumbSquash`.
            val held = requireNotNull(scene.frames(Settle).thumbRun(bounds)) {
                "no thumb found held at the end of the track"
            }.width()
            val pushedTo = Offset(bounds.right + Overshoot, bounds.center.y)
            walk(scene, press, pushedTo)
            val squashed = requireNotNull(scene.frames(2).thumbRun(bounds)) {
                "no thumb found while pushing past the end of the track"
            }.width()
            assertTrue(
                squashed < held,
                "the thumb was ${squashed}px wide pushed into the end stop against " +
                    "${held}px held there, so the gesture never squashed it and " +
                    "there is no release for this to be measuring",
            )

            scene.release(pushedTo)
            val path = (0 until ReleaseFrames).map {
                requireNotNull(scene.frame().thumbRun(bounds)) {
                    "no thumb found on the way back from the end stop"
                }.width()
            }

            // **Between the two widths it was, and past neither.** The thumb was
            // held at `held` and squashed to `squashed`; letting go unwinds the
            // band *and* the press growth at once, so it comes home by narrowing
            // rather than by widening — which is why this is bounded at both ends
            // rather than asserted monotone. A release that re-inflates the
            // stretch it was let go from goes above `held`; one that overshoots
            // its destination dips below `resting`. Either is the full-width pill
            // that was reported, in one direction or the other.
            val widest = path.max()
            assertTrue(
                widest <= held + Tolerance,
                "on the way back from a squash the thumb reached ${widest}px, " +
                    "wider than the ${held}px it was ever held at. It starts this " +
                    "journey at ${squashed}px and its destination is ${resting}px, " +
                    "so anything above the width it was squashed *from* is the " +
                    "stretch re-inflating it. The widths, frame by frame: $path",
            )
            assertTrue(
                path.min() >= resting - Tolerance,
                "on the way back the thumb narrowed to ${path.min()}px, past its " +
                    "resting ${resting}px, and had to come back out. " +
                    "The widths, frame by frame: $path",
            )
            assertTrue(
                abs(path.last() - resting) <= Tolerance,
                "the thumb finished at ${path.last()}px rather than back at its " +
                    "resting ${resting}px, so it has not animated home at all and " +
                    "the assertions above prove nothing",
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
            // Against the **held** width. See the slider's version above: a held
            // thumb is wider than the circle it rests as, and a fifth off it is
            // still wider than that circle.
            val held = requireNotNull(scene.frames(Settle).thumbRun(bounds, rightHalf)) {
                "no thumb found held in the right half of the control"
            }.width()
            val pushedTo = Offset(bounds.right + Overshoot, bounds.center.y)
            walk(scene, press, pushedTo)
            val squashed = requireNotNull(scene.frames(2).thumbRun(bounds, rightHalf)) {
                "no thumb found while pushing past the end of the track"
            }.width()
            assertTrue(
                squashed < held,
                "the end thumb was ${squashed}px wide pushed past the end of the " +
                    "track against ${held}px held there, so the band was never " +
                    "charged and there is no release for this to be measuring",
            )

            scene.release(pushedTo)
            val path = (0 until ReleaseFrames).map {
                requireNotNull(scene.frame().thumbRun(bounds, rightHalf)) {
                    "no thumb found on the way back from the end stop"
                }.width()
            }

            // Bounded at both ends rather than asserted monotone — see the
            // slider's version above for why letting go narrows rather than
            // widens.
            val widest = path.max()
            assertTrue(
                widest <= held + Tolerance,
                "on the way back from a squash the end thumb reached ${widest}px, " +
                    "wider than the ${held}px it was ever held at. It starts at " +
                    "${squashed}px and its destination is ${resting}px, so " +
                    "anything above the width it was squashed *from* is the stretch " +
                    "re-inflating it. The widths, frame by frame: $path",
            )
            assertTrue(
                path.min() >= resting - Tolerance,
                "on the way back the end thumb narrowed to ${path.min()}px, past " +
                    "its resting ${resting}px. The widths, frame by frame: $path",
            )
            assertTrue(
                abs(path.last() - resting) <= Tolerance,
                "the end thumb finished at ${path.last()}px rather than back at " +
                    "its resting ${resting}px, so it has not animated home at all " +
                    "and the assertions above prove nothing. The widths, frame by " +
                    "frame: $path",
            )
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

        /** Far enough in to be on the thumb, far enough out to miss the labels. */
        const val StackedProbe = 20f

        /** Inside the first of two stacked rows, and inside the last. */
        const val StackedFirst = 0.25f
        const val StackedLast = 0.75f

        /** Taller than one row at 2x, which only a stacked control is. */
        const val StackedFloor = 100f

        /** Shorter than this is not a stacked thumb at 2x. */
        const val MinimumThumb = 40

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
         * How far apart the two ends may read and still be the same cap.
         *
         * A capsule's ends are one radius, so the two readings differ only by
         * what antialiasing does to a three-pixel half-height. A fifth is
         * comfortably inside that and nowhere near the 2.0 an egg gives.
         */
        const val EndsAlike = 0.2f

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

/**
 * The **longest unbroken** run of non-[track] rows at column [x].
 *
 * [runDown]'s count is the wrong instrument for a stacked control and the
 * difference cost a debugging round: it totals every row that is not the track,
 * and at a column ten dp in from the edge the track's own rounded corners show
 * the page for several rows at each end. A squashing thumb and a corner arc are
 * then one number, and the thumb reading *taller* under a squash is what that
 * looks like.
 */
private fun BufferedImage.thumbDown(bounds: Rect, x: Int, track: Int): Int {
    val top = bounds.top.toInt().coerceAtLeast(0)
    val bottom = (bounds.bottom.toInt() - 1).coerceAtMost(height - 1)
    var best = 0
    var run = 0
    for (y in top..bottom) {
        if (differs(getRGB(x, y), track, Faint)) {
            run++
            if (run > best) best = run
        } else {
            run = 0
        }
    }
    return best
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
