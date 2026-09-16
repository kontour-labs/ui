package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.sp
import io.kontour.ui.components.display.AnimatedCounter
import io.kontour.ui.theme.Theme
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Only the column that changed moves, and the row does not change width.
 *
 * Those are the two claims that separate this from a `Text` handed a new string.
 * Both are about *columns*, and columns are not in the semantics tree — the
 * cells are cleared on purpose so a screen reader hears one number rather than
 * six characters. So this reads pixels.
 *
 * ### Nothing here is a hardcoded pixel range
 *
 * The first draft named the two digit cells by pixel — `4..34` and `44..74` —
 * which is a fact about one font at one size, and a test that has to be
 * re-measured whenever the type scale moves is a test that will one day be
 * re-measured into passing. Both assertions are derived from the drawn ink
 * instead: find where the number is, and ask which half of it moved.
 */
class AnimatedCounterTest {

    /**
     * 14 to 13 rolls one column.
     *
     * The tens digit is a `1` in both, so nothing in the left half of the number
     * may change at all; the units digit has to be somewhere between `4` and `3`
     * partway through, rather than already being one of them. A counter that
     * cross-faded the whole string fails the first; one that swapped instantly
     * fails the second.
     */
    @Test
    fun onlyTheChangedDigitMoves() {
        val value = mutableStateOf(14)
        Scene(width = Width, height = Height, density = Density.toFloat()) {
            Counter(value)
        }.use { scene ->
            val before = scene.frames(10)
            // The tens digit, located rather than guessed: the first unbroken
            // run of inked columns is the `1`, whatever the font does with it.
            // An earlier draft compared against the midpoint of the whole
            // number and failed by one pixel once the digits became tabular —
            // a midpoint is not a cell boundary, and the boundary is what the
            // claim is about.
            val tens = before.firstInkRun()

            value.value = 13
            // Well inside the roll. `AnimatedContent` takes a frame or two to
            // notice the change, and sampling before it has started reports
            // "nothing moved" for a counter that is about to move — which is the
            // failure this test would most like to avoid producing falsely.
            val midway = scene.frames(MidRollFrames)
            val settled = scene.frames(SettleFrames)

            val moved = before.changedColumnsAgainst(midway)
            assertTrue(
                moved.isNotEmpty(),
                "${MidRollFrames} frames after 14 became 13, nothing on screen had " +
                    "changed at all",
            )
            assertTrue(
                moved.none { it in tens },
                "the tens digit occupies columns ${tens.first}..${tens.last} and " +
                    "columns ${moved.first()}..${moved.last()} moved — it is a `1` " +
                    "in both 14 and 13, so anything happening there means the whole " +
                    "number is animating rather than the digit that changed",
            )
            assertTrue(
                before.changedColumnsAgainst(midway).isNotEmpty() &&
                    settled.changedColumnsAgainst(midway).isNotEmpty(),
                "partway through, the units column is already showing one of the " +
                    "two digits outright — it swapped rather than rolled",
            )
            assertTrue(
                settled.changedColumnsAgainst(scene.frames(4)).isEmpty(),
                "the roll never settles",
            )
        }
    }

    /**
     * A proportional font draws `1` narrower than `8`.
     *
     * Left alone, a counter counting past a `1` changes width and drags whatever
     * is beside it sideways — on "14 min · Platform 2" that is the platform
     * number twitching every minute. Every cell is the widest digit instead.
     *
     * Measured as the **laid-out** width, not the drawn ink: a `1` centred in a
     * full-width cell still has narrower ink than an `8`, and it is supposed to.
     * What must not move is the box, because the box is what the next thing in
     * the row is placed against.
     *
     * ### And measured in the *theme's* font
     *
     * The first draft styled the specimen with a bare `TextStyle(fontSize = …)`,
     * which on the JVM resolves to a default whose digits are all 41px wide.
     * Against that font the fixed-width cell is a no-op, the test passed with
     * the whole mechanism deleted, and it was checking nothing. The theme's face
     * draws `1` at 23px and `0` at 42px — a 19px twitch per column, which is
     * what this is actually about.
     */
    @Test
    fun theRowDoesNotChangeWidthAsItCounts() {
        val narrow = laidOutWidth(11)
        val wide = laidOutWidth(88)

        assertEquals(
            narrow,
            wide,
            "11 lays out ${narrow}px wide and 88 lays out ${wide}px — the counter " +
                "changes width as it counts, so everything beside it moves",
        )
    }

    /**
     * The warning shakes the digits that are about to change, and only those.
     *
     * 1200 falling to 1199 is the case worth testing rather than 14 to 13: three
     * of the four positions change and the leading `1` does not, so a wiggle on
     * the whole row and a wiggle on the changing digits are visibly different
     * pictures. The reported behaviour was the first one, and it is the one that
     * looks right until you ask what it is telling the reader — "something is
     * about to change" rather than "this part of the number is".
     *
     * Read during the **hold**, before the roll: `warnBefore` keeps the old
     * number on screen, so every column that differs from the rest state in that
     * window differs because it is being shaken.
     *
     * A short hold here rather than [HoldSeconds], because the shake is at the
     * *end* of one — see [theNumberIsHeldStillUntilTheShake] — so a hold nothing
     * can outlast is a hold whose shake never arrives. [ShortHoldMillis] is long
     * enough that the wait before the tremor is a real wait and short enough that
     * a second or so of wall clock reaches the far side of it.
     *
     * [Scene.renderUntil] rather than a frame count, and for the reason its own
     * documentation gives: what is being waited for is a `delay`, which runs on
     * the wall clock, and this scene's frames do not. The elapsed-time assertion
     * is the other half of the claim — the tremor may not arrive *early*.
     */
    @Test
    fun onlyTheDigitsAboutToChangeShakeAtTheEndOfTheWarning() {
        val value = mutableStateOf(1200)
        Scene(width = Width, height = Height, density = Density.toFloat()) {
            Counter(value, warn = ShortHoldMillis.milliseconds)
        }.use { scene ->
            val rest = scene.frames(10)
            val thousands = rest.firstInkRun()

            value.value = 1199
            val started = System.nanoTime()
            val shaking = scene.renderUntil(timeoutMillis = ShakeTimeoutMillis) {
                rest.changedColumnsAgainst(it).isNotEmpty()
            }
            val elapsed = (System.nanoTime() - started) / 1_000_000

            assertTrue(
                shaking != null,
                "nothing moved within ${ShakeTimeoutMillis}ms of 1200 becoming 1199 " +
                    "with a ${ShortHoldMillis}ms warning — the announcement is the " +
                    "whole point of the hold",
            )
            assertTrue(
                elapsed >= LeadInFloorMillis,
                "the digits moved ${elapsed}ms after the drop, and the tremor is " +
                    "supposed to sit at the end of a ${ShortHoldMillis}ms hold — at " +
                    "least ${LeadInFloorMillis}ms of it. A shake at the front leaves " +
                    "the reader watching a still number for the rest of the warning, " +
                    "which is what was reported.",
            )
            val moved = rest.changedColumnsAgainst(requireNotNull(shaking))
            assertTrue(
                moved.none { it in thousands },
                "the thousands digit occupies columns ${thousands.first}.." +
                    "${thousands.last} and columns ${moved.first()}..${moved.last()} " +
                    "moved. It is a `1` in both 1200 and 1199, so it has nothing to " +
                    "announce — a tremor there is the whole row shaking, which says " +
                    "only that *something* is changing.",
            )
        }
    }

    /**
     * And the number is perfectly still until then.
     *
     * Two defects, one assertion. The wiggle used to run `while (true)` for
     * exactly as long as `warnBefore`, so a warning long enough to read was a
     * tremor long enough to look like a fault. Bounding it fixed the length and
     * left it at the front, so a 1.5s warning shook for 450ms and then stood
     * still for a second — reported as the wiggle stopping for a moment before
     * the counter ticks down. A warning is only a warning if it is next to what
     * it warns of, so the stillness comes first now and the tremor runs out onto
     * the roll.
     *
     * What that means here is that a hold nothing can outlast is a hold in which
     * **nothing happens at all** — no pixels move, and nothing asks for a frame.
     * The second half is the stronger of the two: a settled scene has no
     * invalidations, so this catches a wiggle that has gone still at an amplitude
     * of zero while continuing to ask for frames, which is the defect this
     * repository keeps finding under the heading of animations running for
     * nobody.
     *
     * The hold is measured in **real** seconds and every animation in frames, and
     * that mismatch is what makes the test possible: `delay` runs on the wall
     * clock and `animateTo` runs on this scene's clock, so sixty renders cost a
     * second of frame time and nothing like a second of real time. A ten-second
     * hold is still going.
     */
    @Test
    fun theNumberIsHeldStillUntilTheShake() {
        val value = mutableStateOf(1200)
        Scene(width = Width, height = Height, density = Density.toFloat()) {
            Counter(value, warn = HoldSeconds.seconds)
        }.use { scene ->
            val rest = scene.frames(10)
            value.value = 1199
            scene.advance(3)
            assertTrue(
                !scene.stillAnimating(),
                "something was animating three frames into a ${HoldSeconds}s hold. " +
                    "The tremor belongs at the far end of it, and nothing else in " +
                    "this component moves while the old number is being held.",
            )

            repeat(ShakeSearchFrames) {
                assertTrue(
                    rest.changedColumnsAgainst(scene.frames(1)).isEmpty(),
                    "a column moved inside the first ${ShakeSearchFrames} frames of a " +
                        "${HoldSeconds}s hold — the shake is back at the front, which " +
                        "puts a second of stillness between it and the roll it is " +
                        "warning about",
                )
            }

            scene.advance(ShakeSettledFrames)
            assertTrue(
                !scene.stillAnimating(),
                "still animating ${ShakeSettledFrames} frames into the warning, and " +
                    "the shake has not been reached yet — so something is running " +
                    "for the length of the hold again.",
            )

            // And the number on screen is still the old one, or the assertion
            // above would pass just as well for a counter that had finished
            // rolling. Compared against a render taken before the change, which
            // is the only thing that distinguishes "held, still" from "rolled,
            // still".
            val held = scene.frames(2)
            assertTrue(
                held.changedColumnsAgainst(scene.frames(2)).isEmpty(),
                "the drawn number is still changing after the shake has stopped",
            )
            value.value = 1200
            assertTrue(
                held.changedColumnsAgainst(scene.frames(2)).isEmpty(),
                "putting the value back to 1200 changed the picture, so the counter " +
                    "had already rolled to 1199 and the hold is not what the frames " +
                    "above were measuring",
            )
        }
    }

    /**
     * The first unbroken run of inked columns — the leftmost glyph.
     *
     * A digit cell is wider than the digit in it, so the columns between two
     * digits are blank and the runs separate cleanly.
     */
    private fun BufferedImage.firstInkRun(): IntRange {
        val page = getRGB(2, 2)
        val inked = (0 until width).filter { x ->
            (0 until height).any { y -> getRGB(x, y) != page }
        }
        val start = inked.first()
        var end = start
        for (x in inked) {
            if (x > end + 1) break
            end = x
        }
        return start..end
    }

    /** The columns holding any ink at all. */
    private fun BufferedImage.inkColumns(): IntRange {
        val page = getRGB(2, 2)
        val inked = (0 until width).filter { x ->
            (0 until height).any { y -> getRGB(x, y) != page }
        }
        return inked.first()..inked.last()
    }

    /** Which columns differ between this frame and [other]. */
    private fun BufferedImage.changedColumnsAgainst(other: BufferedImage): List<Int> =
        (0 until width).filter { x ->
            (0 until height).any { y -> getRGB(x, y) != other.getRGB(x, y) }
        }

    private fun laidOutWidth(value: Int): Int {
        var bounds = Rect.Zero
        Scene(
            width = Width,
            height = Height,
            density = Density.toFloat(),
            reduceMotion = true,
        ) {
            Box(
                Modifier.fillMaxSize().background(Theme.colours.background),
                Alignment.TopStart,
            ) {
                AnimatedCounter(
                    value = value,
                    modifier = Modifier.reportBounds { bounds = it },
                    style = Theme.typography.bodyMedium.copy(fontSize = FontSize.sp),
                )
            }
        }.use { it.frames(4) }
        return bounds.width.toInt()
    }

    @Composable
    private fun Counter(value: MutableState<Int>, warn: Duration = Duration.ZERO) {
        Box(
            Modifier.fillMaxSize().background(Theme.colours.background),
            Alignment.TopStart,
        ) {
            AnimatedCounter(
                value = value.value,
                warnBefore = warn,
                style = Theme.typography.bodyMedium.copy(fontSize = FontSize.sp),
            )
        }
    }

    private companion object {
        const val Density = 2
        const val Width = 300
        const val Height = 120
        const val FontSize = 32

        /**
         * Six frames in — past the two the state change costs, and well short of
         * the eleven the roll takes.
         */
        const val MidRollFrames = 6

        /** Long enough for the spring to land, whatever the frame budget. */
        const val SettleFrames = 60

        /**
         * A hold nothing in this test can outlast.
         *
         * Real seconds against a frame clock: sixty renders here cost a second of
         * *frame* time and a fraction of a second of wall time, so ten seconds of
         * `delay` is never reached however many frames a test draws.
         */
        const val HoldSeconds = 10

        /**
         * How many frames to look through for the shake's furthest excursion.
         *
         * A there-and-back is two 90ms legs, or about twelve frames, and the
         * first two or three go on the effect noticing the change. Sixteen covers
         * a whole cycle from a standing start whichever frame the animation
         * actually begins on.
         */
        const val ShakeSearchFrames = 16

        /**
         * Frames by which the shake must have stopped.
         *
         * Two there-and-backs at a 90ms leg plus a 90ms settle is 450ms, which is
         * twenty-nine frames. Forty-five leaves half again as much slack and is
         * still nowhere near the hold.
         */
        const val ShakeSettledFrames = 45

        /**
         * A hold short enough that its far end is reachable in wall-clock time.
         *
         * The mirror of [HoldSeconds], and both are needed because the tremor and
         * the wait before it are measured on different clocks. 900ms against a
         * 450ms tremor leaves about 450ms of stillness first, which is a real
         * wait on any machine and a fraction of a second to sit through.
         *
         * The tremor is cut short by the roll here and would not be on a device:
         * the remaining 450ms is *frame* time, which is about a second of wall
         * clock in this harness, and the hold ends on the wall clock. Everything
         * asserted above happens on the tremor's first visible frame, so the
         * truncation does not reach it.
         */
        const val ShortHoldMillis = 900L

        /**
         * The earliest the tremor may appear in a [ShortHoldMillis] warning.
         *
         * `delay` will not return early, so this is a floor rather than an
         * estimate: 900 − 450 is 450, and 350 leaves room for the clock being
         * read a frame late without leaving room for a shake at the front.
         */
        const val LeadInFloorMillis = 350L

        /** Generous: the wait is under a second and a frame here can cost 45ms. */
        const val ShakeTimeoutMillis = 15_000L
    }
}
