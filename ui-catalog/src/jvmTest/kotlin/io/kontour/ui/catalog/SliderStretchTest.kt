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
import io.kontour.ui.components.selection.Slider
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The thumbs stretch, and they only stretch when something is straining them.
 *
 * `selection.md` has credited the slider with this since it was written — *"the
 * thumb grows while dragged and settles back with a bounce"* — and what it
 * actually did was scale a circle 25% and stay a circle. Nothing elongated,
 * nothing lagged, and the range slider's thumbs passed straight through each
 * other's business with no acknowledgement at all.
 *
 * ### Measured as wider than a uniform grow can account for
 *
 * The thumb is 11dp of radius and grows 1.25× while touched, so the widest a
 * circle can be is 27.5dp — [UniformGrow] px at this density. Anything past that
 * is elongation and nothing else can produce it, which is what makes this a
 * measurement rather than a threshold someone picked.
 *
 * The row is taken through the thumb's own centre and the run is bounded at both
 * ends, so the track — 6dp tall, well clear of a 22dp thumb's centre row — never
 * joins the measurement.
 */
class SliderStretchTest {

    /**
     * A stepped drag held between two notches strains, and shows it.
     *
     * This is the case that failed the first implementation of the signal. The
     * stretch was driven by the gap between the detent animation and its own
     * target, and a spring converging on a target closes that gap in a few
     * frames — so a thumb *held* between detents, which is precisely the
     * situation the strain depicts, sat there perfectly round. It is driven by
     * the gap to the finger now, which holds for as long as the finger does.
     */
    @Test
    fun aSteppedThumbStretchesTowardTheFingerItIsHeldBy() {
        var value by mutableStateOf(0.5f)
        var bounds = Rect.Zero
        val widths = mutableListOf<Int>()

        Scene(width = 700, height = 240) {
            Box(Modifier.fillMaxSize().background(Color.White).padding(20.dp)) {
                Slider(
                    value = value,
                    onValueChange = { value = it },
                    steps = 4,
                    modifier = Modifier.reportBounds { bounds = it },
                )
            }
        }.use { scene ->
            scene.frames(3)
            assertTrue(bounds.width > 0f, "the slider never reported a size")

            // Slowly, and only a little: the point is to sit between detents
            // rather than to cross them.
            scene.drag(
                from = bounds.alongX(0.5f),
                to = bounds.alongX(0.62f),
                steps = 24,
                onFrame = { _, frame -> widths += frame.thumbElongation(bounds) },
            )
            repeat(24) { scene.frame() }
        }

        val most = widths.max()
        assertTrue(
            most > Tolerance,
            "the thumb was at most ${most}px wider than it was tall during a " +
                "stepped drag — it stayed round. A thumb held between two detents " +
                "is straining against one of them, and that is the whole of what " +
                "a detent feels like.",
        )
    }

    /**
     * And it relaxes back to its resting shape once nothing is pulling on it.
     *
     * This used to assert the settled thumb was *round*, which stopped being the
     * resting shape when the thumb became a horizontal capsule: it is
     * [io.kontour.ui.components.selection.SliderDefaults.ThumbAspect] wider than
     * it is tall before anything touches it. The property worth keeping is not
     * the number, it is that the stretch is a movement rather than a shape — so
     * the resting elongation is *measured* from an untouched slider rather than
     * assumed, and this compares against that. It cannot go stale the next time
     * the thumb's proportions are tuned.
     */
    @Test
    fun aThumbRelaxesToItsRestingShape() {
        var value by mutableStateOf(0.5f)
        var bounds = Rect.Zero
        var untouched = 0
        var atRest = 0

        Scene(width = 700, height = 240) {
            Box(Modifier.fillMaxSize().background(Color.White).padding(20.dp)) {
                Slider(
                    value = value,
                    onValueChange = { value = it },
                    steps = 4,
                    modifier = Modifier.reportBounds { bounds = it },
                )
            }
        }.use { scene ->
            untouched = scene.frames(3).thumbElongation(bounds)
            scene.drag(from = bounds.alongX(0.5f), to = bounds.alongX(0.62f), steps = 12)
            atRest = scene.frames(40).thumbElongation(bounds)
        }

        assertTrue(
            abs(atRest - untouched) <= Tolerance,
            "a slider left alone draws a thumb ${untouched}px wider than it is " +
                "tall; after a drag settled it is ${atRest}px. The stretch has " +
                "become a shape rather than a movement.",
        )
    }

    /**
     * A pushed thumb stretches too, which is the case this was asked for.
     *
     * The thumb being shoved has no finger on it, so its stretch comes from the
     * distance it still has to travel rather than from a strain. It had none at
     * all before: the eased spec was switched to `snap()` for the whole control
     * while *any* drag was in progress, so the pushed thumb arrived instantly,
     * nothing lagged, and there was nothing to draw.
     */
    @Test
    fun aPushedThumbStretchesAsItIsShoved() {
        var range by mutableStateOf(2f..8f)
        var bounds = Rect.Zero
        val widths = mutableListOf<Int>()

        Scene(width = 700, height = 240) {
            Box(Modifier.fillMaxSize().background(Color.White).padding(20.dp)) {
                RangeSlider(
                    value = range,
                    onValueChange = { range = it },
                    valueRange = 0f..10f,
                    minDistance = 2f,
                    modifier = Modifier.reportBounds { bounds = it },
                )
            }
        }.use { scene ->
            scene.frames(3)
            // The start thumb, driven hard right into the end thumb and past it.
            scene.drag(
                from = bounds.alongX(0.2f),
                to = bounds.alongX(0.95f),
                steps = 16,
                onFrame = { _, frame -> widths += frame.thumbElongation(bounds) },
            )
            repeat(20) { scene.frame() }
        }

        val most = widths.max()
        assertTrue(
            most > Tolerance,
            "neither thumb ever got more than ${most}px wider than it was tall " +
                "while one shoved the other — being pushed looks exactly like " +
                "being placed.",
        )
    }

    /**
     * The two thumbs never merge into one while one shoves the other.
     *
     * A pushed thumb reaches its new value at once and its spring does not, and
     * against a fast drag that spring falls far enough behind that the thumb
     * doing the pushing catches up with where the pushed one is *drawn*. The two
     * ran together into a single blob halfway along the track — and every
     * assertion in `RangeSliderPushTest` passed while it happened, because the
     * reported range was correct the whole time. It only showed up in a
     * filmstrip.
     *
     * Which is the argument for this test existing: the value being right is not
     * the same claim as the control looking right, and the gap between those two
     * is where this round keeps finding things.
     */
    @Test
    fun theThumbsNeverMergeWhileOneShovesTheOther() {
        var range by mutableStateOf(1f..4f)
        var bounds = Rect.Zero
        val counts = mutableListOf<Int>()

        Scene(width = 700, height = 240) {
            Box(Modifier.fillMaxSize().background(Color.White).padding(20.dp)) {
                RangeSlider(
                    value = range,
                    onValueChange = { range = it },
                    valueRange = 0f..10f,
                    minDistance = 2f,
                    modifier = Modifier.reportBounds { bounds = it },
                )
            }
        }.use { scene ->
            scene.frames(3)
            scene.drag(
                from = bounds.alongX(0.1f),
                to = bounds.alongX(0.97f),
                steps = 15,
                onFrame = { _, frame -> counts += frame.thumbCount(bounds) },
            )
            repeat(10) { counts += scene.frame().thumbCount(bounds) }
        }

        val merged = counts.count { it < 2 }
        assertTrue(
            merged == 0,
            "the two thumbs were drawn as one on $merged of ${counts.size} " +
                "frames while the start thumb pushed the end thumb. They are " +
                "held two units apart on a ten-unit range the whole way — a " +
                "fifth of the track — so there is no frame on which they should " +
                "be touching, let alone one shape.",
        )
    }

    private companion object {
        /** Two antialiased edges and the odd rounded pixel. */
        const val Tolerance = 4
    }
}

/**
 * How much wider than tall the thumb is, in pixels. Zero for a circle.
 *
 * Scale-free on purpose. The first version of this compared the thumb's width
 * against the widest a 1.25×-grown circle could be, sampled along one row a
 * little above centre — and that row cuts a **chord**, not the diameter, so it
 * under-read every frame by about 8px and reported no stretch on drags that a
 * filmstrip showed plainly elongated. Width against height needs no constant, no
 * density arithmetic and no assumption about the grow: a capsule is wider than
 * it is tall and a circle is not, whatever size either of them is.
 */
private fun java.awt.image.BufferedImage.thumbElongation(bounds: Rect): Int {
    val page = getRGB(2, 2)
    val top = bounds.top.toInt().coerceAtLeast(0)
    val bottom = (bounds.bottom.toInt() - 1).coerceAtMost(height - 1)

    // Ink per column, then keep only the columns too tall to be the track.
    // The track is 6dp and the thumb 22dp, so nothing in between is ambiguous.
    var widest = 0
    var wide = 0
    var tallest = 0
    for (x in bounds.left.toInt().coerceAtLeast(0)..(bounds.right.toInt() - 1).coerceAtMost(width - 1)) {
        var ink = 0
        for (y in top..bottom) if (differsFrom(getRGB(x, y), page)) ink++
        if (ink > TrackDepth) {
            wide++
            if (ink > tallest) tallest = ink
        } else if (wide > 0) {
            if (wide > widest) widest = wide
            wide = 0
        }
    }
    if (wide > widest) widest = wide
    return widest - tallest
}

/** How many separate thumbs are drawn. Two, unless something has run together. */
private fun java.awt.image.BufferedImage.thumbCount(bounds: Rect): Int {
    val page = getRGB(2, 2)
    val top = bounds.top.toInt().coerceAtLeast(0)
    val bottom = (bounds.bottom.toInt() - 1).coerceAtMost(height - 1)

    var groups = 0
    var inGroup = false
    for (x in bounds.left.toInt().coerceAtLeast(0)..(bounds.right.toInt() - 1).coerceAtMost(width - 1)) {
        var ink = 0
        for (y in top..bottom) if (differsFrom(getRGB(x, y), page)) ink++
        val thumb = ink > TrackDepth
        if (thumb && !inGroup) groups++
        inGroup = thumb
    }
    return groups
}

/** Taller than the 6dp track at this density, and well under a 22dp thumb. */
private const val TrackDepth = 20

private fun differsFrom(a: Int, b: Int): Boolean =
    kotlin.math.abs((a shr 16 and 0xFF) - (b shr 16 and 0xFF)) > 24 ||
        kotlin.math.abs((a shr 8 and 0xFF) - (b shr 8 and 0xFF)) > 24 ||
        kotlin.math.abs((a and 0xFF) - (b and 0xFF)) > 24
