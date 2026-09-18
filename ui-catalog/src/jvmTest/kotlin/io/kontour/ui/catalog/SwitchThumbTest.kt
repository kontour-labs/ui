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
 * The thumb is 24dp in a 48dp track with 2dp of padding, which leaves 20dp of
 * travel — and stretching it 25% makes it 30dp, which does not fit that
 * arithmetic. The old code let the leading edge run into the track's wall and
 * clamped it there: for the last eighth of an off-to-on drag the gap it was
 * supposed to hold went to **zero**, the thumb overlapped its own border, and it
 * stopped tracking the finger while it did it. The other side kept its clearance, so
 * the control visibly lost its symmetry under the thumb — which is what "the
 * spacing on the sides isn't consistent as you drag it" looks like from outside.
 */
class SwitchGeometryTest {

    /**
     * And a squashed thumb is shorter, which is what keeps it inside that gap.
     *
     * The gap test below walks the same gesture and cannot see this, because it
     * measures widths on the centre row — the one row where a squashed thumb is
     * exactly as clear as it was.
     *
     * At rest the thumb's circle and the track's end arc share a centre: 24dp
     * inside a 28dp track with 2dp of padding, and because the track's short edge
     * is fully saturated `SquircleShape` returns a true semicircle there. So the
     * clearance is 2dp the whole way round. Squashed, the ellipse's height used to
     * be left alone — so its top and bottom swung *toward* the wall and the
     * clearance pinched to 1.17dp at full squash, 41% of the gap gone.
     * `squashedCapsule` now takes the geometric mean of the resting radius and the
     * half-width, which puts the ellipse tangent to that arc at every depth and
     * takes the height from 24dp to 20.8dp.
     *
     * ### The height, rather than the clearance
     *
     * Measuring the clearance directly was the first attempt and it is not worth
     * what it costs. It means comparing two antialiased outlines, and the pixel
     * grid biases them toward each other unevenly: at density 4 the fixed shape
     * read 7.81px of a 8px gap and the defect 6.40px, a 1.4px separation on a
     * 0.83dp effect. A threshold in that gap is a flake waiting for a Skia
     * upgrade.
     *
     * The height is the same claim with ten times the margin — 3.2dp of a 24dp
     * thumb — and it is the mechanism rather than a proxy for it: the ellipse
     * cannot both keep its full height and stay inside the arc. The tangency
     * itself is arithmetic and is asserted as arithmetic, in
     * `SquashedThumbGeometryTest`.
     */
    @Test
    fun aSquashedThumbIsShorterThanTheCircleItRestsAs() {
        var checked by mutableStateOf(false)
        var bounds = Rect.Zero
        var resting = -1
        var squashed = -1

        Scene(width = 400, height = 200) {
            // **Not white.** The thumb is white when the switch is on, so on a
            // white page the thumb and the page are the same colour and the
            // measurement cannot tell where one ends.
            Box(Modifier.fillMaxSize().background(Backing)) {
                Switch(
                    checked = checked,
                    onCheckedChange = { checked = it },
                    modifier = Modifier.reportBounds { bounds = it },
                )
            }
        }.use { scene ->
            scene.frames(3)
            assertTrue(bounds.width > 0f, "the switch never reported a size")

            resting = scene.frames(SettleFrames).thumbHeight(bounds)

            // Past the end and **held** there: the squash only exists while
            // something is pushing against the wall.
            scene.drag(
                from = bounds.alongX(0.2f),
                to = bounds.alongX(1.6f),
                release = false,
            )
            squashed = scene.frames(2).thumbHeight(bounds)
            scene.release(bounds.alongX(1.6f))
        }

        assertTrue(
            resting > 0 && squashed > 0,
            "the thumb was not found: ${resting}px at rest, ${squashed}px pushed",
        )
        assertTrue(
            squashed < resting - Antialiasing,
            "pushed into the end of its track the thumb is ${squashed}px tall " +
                "against ${resting}px at rest. An ellipse that keeps its full " +
                "height while it narrows swings its top and bottom toward the " +
                "wall, so it stops being concentric with the arc it is pressed " +
                "against — the 2dp gap the switch is built on pinches to 1.17dp " +
                "while looking untouched on the centre row.",
        )
    }

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
                "${tightest.second}px the other). It is padded 2dp — ${PaddingPx}px " +
                "at this density — and that has to hold at both ends and " +
                "everywhere between, or the gap is something the user watches " +
                "change as they drag.",
        )
    }
}

/**
 * How tall the thumb is drawn, measured up its own middle.
 *
 * The column is the midpoint of the thumb's horizontal run, which is the one place
 * every shape in the family is its full height — a capsule, a circle and an
 * ellipse alike. Counted as rows that are not the track's own colour, the same
 * reference [thumbRun] uses and for the same reason.
 */
private fun BufferedImage.thumbHeight(bounds: Rect): Int {
    val run = thumbRun(bounds)
    val top = bounds.top.toInt().coerceAtLeast(0)
    val bottom = (bounds.bottom.toInt() - 1).coerceAtMost(height - 1)
    val middle = (run.first + run.last) / 2
    // **Matched against the thumb's own colour, not "not the track's".** That was
    // the first attempt and it reads the thumb plus a row or two of page: the
    // thumb's column sits near the track's rounded end, where the top and bottom
    // rows of the *bounding box* are outside the track's body and are page —
    // which also differs from the track. It came back as 48px against 49px on a
    // shape that had genuinely gone from 48 to 45, and the difference the test is
    // about disappeared into the rounding.
    val thumb = getRGB(middle, (top + bottom) / 2) and 0xFFFFFF
    return (top..bottom).count { y -> !differs(getRGB(middle, y) and 0xFFFFFF, thumb) }
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
 * It has to land in the gap the thumb leaves at the top and bottom of the track
 * — 2dp, so four pixels at this density — because what is sampled here is the
 * *track's* colour, and the run of everything that is not that colour is how the
 * thumb is found. Sample inside the thumb and the two swap places: the "fill"
 * becomes the thumb, the widest run of not-fill becomes the track, and the
 * gaps come back as nonsense.
 *
 * It was 5, which sat in the band between the old 2dp border and a thumb that
 * started 3dp in. There is no border now and the thumb is 2dp in.
 */
private const val BandInset = 2

/** `ThumbPadding` at the scene's density. */
private const val PaddingPx = 4

/** A page colour no token in the library resolves to, so thumb and page differ. */
private val Backing = Color(0xFF3A7D44)

/** One antialiased edge pixel each side, and nothing more. */
private const val Antialiasing = 2


/** Long enough for `springSnappy` to cross the track and settle. */
private const val SettleFrames = 20
