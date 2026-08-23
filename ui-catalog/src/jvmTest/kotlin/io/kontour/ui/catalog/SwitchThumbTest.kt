package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import io.kontour.ui.components.selection.Switch
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Where the thumb is drawn, frame by frame, through a drag and its release.
 *
 * Neither of the two defects these cover is visible in a still. The switch at
 * rest was always right; what was wrong was the frame *after the finger lifted*,
 * and the frames while it was still down. So both tests drive a real drag and
 * read the thumb out of every frame of it.
 *
 * ### Finding the thumb without knowing its colour
 *
 * The thumb is grey on a transparent track when off and white on a blue one when
 * on, and during the 150ms after a commit it is somewhere between the two — so
 * no fixed colour finds it, and the frames that matter most are exactly the ones
 * mid-transition.
 *
 * What is stable is that the thumb is the widest thing inside the track that is
 * not the track. The track's own fill is read from the 1dp band between the
 * border and the thumb — the only rows inside the track the thumb can never
 * reach, at any position or stretch — and the thumb is then the longest run
 * along the centre row that differs from it. The border shows up as a run too,
 * and is 4px against the thumb's 44.
 */
class SwitchReleaseTest {

    /**
     * A committed drag carries on from where the finger left it.
     *
     * It used to jump backwards the width of the track first. `dragFraction` and
     * an `animateDpAsState` keyed on `checked` were two sources of truth that
     * were never reconciled: the animation sat parked at the *pre-drag* end for
     * the whole gesture, and the moment the drag cleared itself the draw fell
     * back to it. So the thumb was drawn at the far end for one frame, and only
     * then set off — a jump cut in the middle of the one gesture the control
     * exists for.
     *
     * Measured as monotonicity rather than against expected positions. The claim
     * is not that the thumb is anywhere in particular; it is that it never goes
     * backwards while travelling forwards, which is exactly what a jump cut is
     * and what nothing else can be.
     */
    @Test
    fun aCommittedDragNeverGoesBackwards() {
        val centres = mutableListOf<Float>()
        var checked by mutableStateOf(false)
        var bounds = Rect.Zero

        Scene(width = 400, height = 200) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                Switch(
                    checked = checked,
                    onCheckedChange = { checked = it },
                    modifier = Modifier.reportBounds { bounds = it },
                )
            }
        }.use { scene ->
            scene.frames(3)
            assertTrue(bounds.width > 0f, "the switch never reported a size")

            scene.drag(
                from = bounds.alongX(0.2f),
                to = bounds.alongX(1.4f),
                onFrame = { _, frame -> centres += frame.thumbCentre(bounds) },
            )
            // The release itself renders nothing; the jump was on the frame
            // after it, and the spring across takes about a dozen more.
            repeat(SettleFrames) { centres += scene.frame().thumbCentre(bounds) }
        }

        val backwards = centres.zipWithNext().filter { (a, b) -> b < a - Antialiasing }
        // Computed with `orNull`, because `assertTrue`'s message is built before
        // the assertion runs and the passing case has nothing to take a max of.
        val worst = backwards.maxOfOrNull { (a, b) -> a - b } ?: 0f
        assertTrue(
            backwards.isEmpty(),
            "the thumb travelled backwards ${backwards.size} times during a " +
                "committed drag, the worst by ${worst.toInt()}px: " +
                centres.joinToString { it.toInt().toString() },
        )
    }

    /**
     * A drag that does not carry springs back rather than cutting back.
     *
     * The revert case had no animation at all, for a subtler reason than the
     * commit case: `checked` never changed, so the `animateDpAsState` target
     * never changed either, so nothing ran. The thumb was simply drawn at the
     * resting position on the next frame, wherever the finger had left it.
     *
     * Counted as distinct positions rather than measured, because "it animates"
     * has no threshold — one frame at the release point and one at rest is a
     * teleport however far apart they are, and three is a spring however close.
     *
     * Counted from the thumb's **leading edge**, not its centre. The first
     * version of this measured the centre and **passed against the defect**: the
     * thumb un-stretches on release whatever else it does, and a box that is
     * getting narrower against a pinned left edge has a centre that moves. Three
     * distinct centres, none of them travel. The leading edge only moves if the
     * thumb does.
     */
    @Test
    fun aDragThatDoesNotCarrySpringsBack() {
        val afterRelease = mutableListOf<Int>()
        var checked by mutableStateOf(false)
        var bounds = Rect.Zero

        Scene(width = 400, height = 200) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                Switch(
                    checked = checked,
                    onCheckedChange = { checked = it },
                    modifier = Modifier.reportBounds { bounds = it },
                )
            }
        }.use { scene ->
            scene.frames(3)
            scene.drag(from = bounds.alongX(0.1f), to = bounds.alongX(0.35f), steps = 8)
            repeat(SettleFrames) { afterRelease += scene.frame().thumbLeadingEdge(bounds) }
        }

        assertTrue(!checked, "the short drag committed — this case is about the revert")

        val distinct = afterRelease.distinct()
        assertTrue(
            distinct.size >= 3,
            "the thumb occupied ${distinct.size} position(s) in the " +
                "$SettleFrames frames after a reverted drag " +
                "(${distinct.joinToString()}) — it is cutting back to rest, not " +
                "springing back to it",
        )
    }
}

/**
 * The thumb keeps its clearance on both sides, wherever it is.
 *
 * The thumb is 22dp in a 48dp track with 3dp of padding, which leaves 20dp of
 * travel — and stretching it 25% makes it 27.5dp, which does not fit that
 * arithmetic. The old code let the leading edge run into the track's wall and
 * clamped it there: for the last eighth of an off-to-on drag the gap it was
 * supposed to hold went to **zero**, the thumb overlapped its own border, and it
 * stopped tracking the finger while it did it. The other side kept its 3dp, so
 * the control visibly lost its symmetry under the thumb — which is what "the
 * spacing on the sides isn't consistent as you drag it" looks like from outside.
 */
class SwitchGeometryTest {

    @Test
    fun theGapsEitherSideOfTheThumbStayEven() {
        val gaps = mutableListOf<Pair<Int, Int>>()
        var checked by mutableStateOf(false)
        var bounds = Rect.Zero

        Scene(width = 400, height = 200) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                Switch(
                    checked = checked,
                    onCheckedChange = { checked = it },
                    modifier = Modifier.reportBounds { bounds = it },
                )
            }
        }.use { scene ->
            scene.frames(3)
            assertTrue(bounds.width > 0f, "the switch never reported a size")

            scene.drag(
                from = bounds.alongX(0.2f),
                to = bounds.alongX(1.4f),
                onFrame = { _, frame -> gaps += frame.thumbGaps(bounds) },
            )
            repeat(SettleFrames) { gaps += scene.frame().thumbGaps(bounds) }
        }

        val tightest = gaps.minByOrNull { minOf(it.first, it.second) }!!
        assertTrue(
            minOf(tightest.first, tightest.second) >= PaddingPx - Antialiasing,
            "the thumb came within ${minOf(tightest.first, tightest.second)}px of " +
                "the track's edge during a drag (${tightest.first}px one side, " +
                "${tightest.second}px the other). It is padded 3dp — ${PaddingPx}px " +
                "at this density — and that has to hold at both ends and " +
                "everywhere between, or the gap is something the user watches " +
                "change as they drag.",
        )
    }
}

/** How far in from the track's two edges the thumb's own edges are, in pixels. */
private fun BufferedImage.thumbGaps(bounds: Rect): Pair<Int, Int> {
    val run = thumbRun(bounds)
    return (run.first - bounds.left.toInt()) to (bounds.right.toInt() - 1 - run.last)
}

/** Where the middle of the thumb is, in root pixels. */
private fun BufferedImage.thumbCentre(bounds: Rect): Float = thumbRun(bounds).centre()

/** Where the thumb's left edge is, in root pixels. Unmoved by the stretch alone. */
private fun BufferedImage.thumbLeadingEdge(bounds: Rect): Int = thumbRun(bounds).first

/**
 * The thumb's horizontal extent, found as the widest run inside the track that
 * is not the track's own fill. See [SwitchReleaseTest]'s header.
 */
private fun BufferedImage.thumbRun(bounds: Rect): IntRange {
    val left = bounds.left.toInt().coerceAtLeast(0)
    val right = (bounds.right.toInt() - 1).coerceAtMost(width - 1)
    val top = bounds.top.toInt().coerceAtLeast(0)
    val bottom = (bounds.bottom.toInt() - 1).coerceAtMost(height - 1)

    // Sampled from both bands rather than one, so a single stray row of
    // antialiasing cannot become the majority.
    val counts = HashMap<Int, Int>()
    for (y in listOf(top + BandInset, bottom - BandInset)) {
        for (x in left..right) {
            val rgb = getRGB(x, y) and 0xFFFFFF
            counts[rgb] = (counts[rgb] ?: 0) + 1
        }
    }
    val fill = counts.maxByOrNull { it.value }?.key ?: error("the track drew nothing")

    val centre = (top + bottom) / 2
    var best = IntRange.EMPTY
    var start = -1
    for (x in left..(right + 1)) {
        val inked = x <= right && differs(getRGB(x, centre) and 0xFFFFFF, fill)
        if (inked && start < 0) start = x
        if (!inked && start >= 0) {
            val run = start..(x - 1)
            if (run.last - run.first > best.last - best.first) best = run
            start = -1
        }
    }
    check(!best.isEmpty()) { "no thumb was drawn inside the track" }
    return best
}

private fun differs(a: Int, b: Int): Boolean =
    kotlin.math.abs((a shr 16 and 0xFF) - (b shr 16 and 0xFF)) > 24 ||
        kotlin.math.abs((a shr 8 and 0xFF) - (b shr 8 and 0xFF)) > 24 ||
        kotlin.math.abs((a and 0xFF) - (b and 0xFF)) > 24

/**
 * How far into the track the fill is sampled.
 *
 * The border is 2dp and the thumb starts 3dp in, so at this density there are
 * two rows of pure fill between them at the top and two at the bottom. This is
 * the middle of that band.
 */
private const val BandInset = 5

/** `ThumbPadding` at the scene's density. */
private const val PaddingPx = 6

/** One antialiased edge pixel each side, and nothing more. */
private const val Antialiasing = 2

/** Long enough for `springSnappy` to cross the track and settle. */
private const val SettleFrames = 20
