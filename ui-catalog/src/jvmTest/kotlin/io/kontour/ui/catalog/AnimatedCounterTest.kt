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
                Modifier.fillMaxSize().background(Theme.colors.background),
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
    private fun Counter(value: MutableState<Int>) {
        Box(
            Modifier.fillMaxSize().background(Theme.colors.background),
            Alignment.TopStart,
        ) {
            AnimatedCounter(
                value = value.value,
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
    }
}
