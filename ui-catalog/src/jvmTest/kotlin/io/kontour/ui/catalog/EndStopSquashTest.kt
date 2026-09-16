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
import io.kontour.ui.components.selection.Slider
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
    }
}

/**
 * The widest run of columns whose ink is taller than the track, or null.
 *
 * The same reading `SliderStretchTest` takes and for the same reason: the track
 * is 6dp and the thumb 22dp, so depth separates them with nothing ambiguous in
 * between and no density arithmetic anywhere.
 */
private fun BufferedImage.thumbRun(bounds: Rect): IntRange? {
    val page = getRGB(2, 2)
    val top = bounds.top.toInt().coerceAtLeast(0)
    val bottom = (bounds.bottom.toInt() - 1).coerceAtMost(height - 1)

    var best: IntRange? = null
    var start = -1
    for (x in 0 until width) {
        var ink = 0
        for (y in top..bottom) if (differs(getRGB(x, y), page)) ink++
        if (ink > TrackDepth) {
            if (start < 0) start = x
        } else if (start >= 0) {
            best = widest(best, start..(x - 1))
            start = -1
        }
    }
    return if (start >= 0) widest(best, start until width) else best
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
 */
private fun BufferedImage.fillRun(bounds: Rect, row: Int, track: Int): IntRange? {
    var best: IntRange? = null
    var start = -1
    val from = (bounds.left + Margin).toInt().coerceAtLeast(0)
    val to = (bounds.right - Margin).toInt().coerceAtMost(width - 1)
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
