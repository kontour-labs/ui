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
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
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
     * A hold nothing can outlast, because the shake no longer waits for the end
     * of one — see [theShakeStartsOnTheFrameTheNumberDrops]. Which is also why
     * this can use [HoldSeconds] again: the tremor is the first thing that
     * happens, so a hold long enough to be unreachable no longer hides it.
     */
    @Test
    fun onlyTheDigitsAboutToChangeShake() {
        val value = mutableStateOf(1200)
        Scene(width = Width, height = Height, density = Density.toFloat()) {
            Counter(value, warn = HoldSeconds.seconds)
        }.use { scene ->
            val rest = scene.frames(10)
            val thousands = rest.firstInkRun()

            value.value = 1199
            val shaking = scene.renderUntil(timeoutMillis = ShakeTimeoutMillis) {
                rest.changedColumnsAgainst(it).isNotEmpty()
            }

            assertTrue(
                shaking != null,
                "nothing moved within ${ShakeTimeoutMillis}ms of 1200 becoming 1199 " +
                    "with a ${HoldSeconds}s warning — the announcement is the whole " +
                    "point of the hold",
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
     * And it is the *first* thing that happens.
     *
     * The inverse of the assertion that used to be here, which is the shape of
     * this defect's history. The tremor began as a `while (true)` for the whole
     * of `warnBefore`; bounding it to 450ms fixed the length and left it at the
     * front of a longer hold, so a 1.5s warning shook and then stood still for a
     * second; moving it to the back put the same second of silence *before* the
     * shake, and that was reported too — an announcement whose first second is
     * nothing happening.
     *
     * So there is no quiet stretch at either end now: the warning **is** the
     * tremor. This asks for both halves of that on the frames right after the
     * drop — something is animating, and a column has actually moved — where the
     * old test asked for the opposite of each.
     *
     * The hold is measured in **real** seconds and every animation in frames, and
     * that mismatch is what makes the test possible: `delay` runs on the wall
     * clock and `animateTo` runs on this scene's clock, so sixty renders cost a
     * second of frame time and nothing like a second of real time. A ten-second
     * hold is still going, which is how the last assertion can be sure the
     * counter has not rolled.
     */
    @Test
    fun theShakeStartsOnTheFrameTheNumberDrops() {
        val value = mutableStateOf(1200)
        Scene(width = Width, height = Height, density = Density.toFloat()) {
            Counter(value, warn = HoldSeconds.seconds)
        }.use { scene ->
            val rest = scene.frames(10)
            value.value = 1199
            scene.advance(PromptFrames)

            assertTrue(
                scene.stillAnimating(),
                "nothing was animating ${PromptFrames} frames into a ${HoldSeconds}s " +
                    "warning. The tremor is the warning and it starts at the drop — " +
                    "a warning that opens with stillness announces nothing, which is " +
                    "what was reported when the shake sat at the far end of a hold.",
            )
            assertTrue(
                rest.changedColumnsAgainst(scene.frames(1)).isNotEmpty(),
                "no column had moved ${PromptFrames} frames after 1200 became 1199. " +
                    "Something asking for frames is not the same as something the " +
                    "reader can see, and the tremor has to be visible this early.",
            )

            // And the number underneath is still the old one, or everything above
            // would read the same on a counter that had simply rolled at once.
            //
            // Put back to 1200: if the drop is still being announced, `shown` is
            // already 1200, the warning ends and the wobble snaps to centre —
            // nothing travels. If it had rolled to 1199, this is an *increase*,
            // which rolls immediately and with no warning, and a roll is still
            // running two frames later.
            value.value = 1200
            scene.advance(PromptFrames)
            assertTrue(
                !scene.stillAnimating(),
                "putting the value back to 1200 started something. A held counter " +
                    "has nothing to travel to, so it had already rolled to 1199 and " +
                    "the hold is not what the frames above were measuring.",
            )
        }
    }

    /**
     * Every digit about to move trembles the same way, and up and down.
     *
     * Reported from a phone: *"when ticking with a wiggle warning in the animated
     * counter, can we please make sure that if multiple digits are about to move,
     * then they wiggle in the same direction"*.
     *
     * They did not. The tremor's direction was `index % 2` — one cell against the
     * next — put there so that two adjacent changing digits in phase could not read
     * as the whole number sliding again. `1200` to `1199` moves three digits, so
     * what a reader got was the hundreds and the units going one way and the tens
     * going the other.
     *
     * And then, a round later: *"can we make the wiggle be up and down instead of
     * side to side?"* So the shift measured is vertical, and a frame that has
     * travelled further sideways than up or down fails.
     *
     * ### Neither existing shake test could see this
     *
     * Both work from `changedColumnsAgainst`, which reports *that* a column moved.
     * Direction needs the ink's centre of mass, so this is the one assertion in the
     * file that weighs pixels rather than counting them: for each digit cell, how
     * high the ink sits, before the drop and on one frame during the tremor.
     *
     * Read on **one** frame for both digits, because the shake is a single
     * `Animatable` passing through zero — sampled on different frames, two cells
     * moving identically would still disagree about which way. `renderUntil` finds
     * a frame where the first of them has actually travelled, and the second is
     * measured on that same frame.
     *
     * The glyphs themselves do not change during the window: `warnBefore` holds the
     * old number on screen and rolls afterwards, so every pixel that moves here
     * moved because it was translated.
     */
    @Test
    fun everyDigitAboutToMoveWigglesTheSameWay() {
        val value = mutableStateOf(1200)
        Scene(width = Width, height = Height, density = Density.toFloat()) {
            Counter(value, warn = HoldSeconds.seconds)
        }.use { scene ->
            val rest = scene.frames(10)
            val cells = rest.digitCells()
            assertEquals(
                4,
                cells.size,
                "1200 should draw as four separated glyphs and drew ${cells.size} " +
                    "runs of ink. A font whose digits touch would merge two cells " +
                    "into one, and then this test is measuring something else.",
            )
            // The hundreds and the tens: adjacent, both about to change — 2 to 1
            // and 0 to 9 — and the two the old parity put in opposite phase.
            val hundreds = cells[1]
            val tens = cells[2]

            value.value = 1199
            val shaking = scene.renderUntil(timeoutMillis = ShakeTimeoutMillis) {
                abs(it.inkMiddle(hundreds) - rest.inkMiddle(hundreds)) > MinShift
            }
            assertTrue(
                shaking != null,
                "the hundreds column never moved within ${ShakeTimeoutMillis}ms of " +
                    "1200 becoming 1199 — there is no tremor to measure",
            )

            val frame = requireNotNull(shaking)
            val hundredsShift = frame.inkMiddle(hundreds) - rest.inkMiddle(hundreds)
            val tensShift = frame.inkMiddle(tens) - rest.inkMiddle(tens)
            val sideways = frame.inkCentre(hundreds) - rest.inkCentre(hundreds)

            assertTrue(
                abs(sideways) < abs(hundredsShift),
                "the hundreds column moved ${sideways}px sideways and " +
                    "${hundredsShift}px up or down on this frame. The warning is " +
                    "supposed to wiggle up and down, not side to side.",
            )

            assertTrue(
                abs(tensShift) > MinShift,
                "the hundreds column moved ${hundredsShift}px on this frame and the " +
                    "tens moved ${tensShift}px, which is nothing. Both digits are " +
                    "about to change and both are supposed to be shaking.",
            )
            assertTrue(
                hundredsShift * tensShift > 0.0,
                "on one frame the hundreds moved ${hundredsShift}px and the tens " +
                    "moved ${tensShift}px — opposite directions. Two digits about " +
                    "to move are supposed to move together.",
            )
        }
    }

    /**
     * Each glyph's own columns, split down the blank gaps between them.
     *
     * The windows come from the **resting** frame and are then widened to the
     * midpoint of each gap, so a glyph that travels a pixel or three stays inside
     * the window it started in and its centre of mass follows it honestly.
     */
    private fun BufferedImage.digitCells(): List<IntRange> {
        val page = getRGB(2, 2)
        val inked = (0 until width).filter { x ->
            (0 until height).any { y -> getRGB(x, y) != page }
        }
        if (inked.isEmpty()) return emptyList()
        val runs = mutableListOf<IntRange>()
        var start = inked.first()
        var end = start
        for (x in inked.drop(1)) {
            if (x > end + 1) {
                runs += start..end
                start = x
            }
            end = x
        }
        runs += start..end
        return runs.mapIndexed { index, run ->
            val leftGap = if (index == 0) run.first else runs[index - 1].last
            val rightGap = if (index == runs.lastIndex) run.last else runs[index + 1].first
            ((run.first + leftGap) / 2)..((run.last + rightGap) / 2)
        }
    }

    /**
     * How high the ink in [columns] sits, weighted by how much of it each row
     * holds. A glyph translated down raises this.
     */
    private fun BufferedImage.inkMiddle(columns: IntRange): Double {
        val page = getRGB(2, 2)
        var weight = 0.0
        var moment = 0.0
        for (y in 0 until height) {
            val ink = columns.count { x -> x in 0 until width && getRGB(x, y) != page }.toDouble()
            weight += ink
            moment += ink * y
        }
        return if (weight == 0.0) 0.0 else moment / weight
    }

    /**
     * Where the ink sits along [columns], weighted by how much of it each column
     * holds. A glyph translated to the right raises this and nothing else does.
     */
    private fun BufferedImage.inkCentre(columns: IntRange): Double {
        val page = getRGB(2, 2)
        var weight = 0.0
        var moment = 0.0
        for (x in columns) {
            if (x !in 0 until width) continue
            val ink = (0 until height).count { y -> getRGB(x, y) != page }.toDouble()
            weight += ink
            moment += ink * x
        }
        return if (weight == 0.0) 0.0 else moment / weight
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
         * A centre-of-mass shift big enough not to be a rounding artefact.
         *
         * The tremor's amplitude is 1.5dp, which is three pixels at this density,
         * so a whole pixel of travel is a third of the extreme and well clear of
         * anything anti-aliasing does on its own.
         */
        const val MinShift = 1.0

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
         * How many frames the tremor gets to become visible.
         *
         * A state change costs a frame to recompose and the effect a frame to
         * launch, so nothing can be asserted on the very next render. Four is
         * that with one to spare, and it is a third of a single 90ms leg — far
         * too short to be reached by a shake that waited for anything.
         */
        const val PromptFrames = 4

        /** Generous: a frame here can cost 45ms and the search runs to a cycle. */
        const val ShakeTimeoutMillis = 15_000L
    }
}
