package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.Text
import io.kontour.ui.motion.MarqueeDefaults
import io.kontour.ui.motion.marquee
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Theme
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * It scrolls only when it has to, and never when the user has asked it not to.
 *
 * `Modifier.marquee` is a wrapper, and a wrapper's tests are about the wrapping:
 * the pace and the [io.kontour.ui.theme.Motion.reduceMotion] rule are what this
 * library added, and both are invisible in a still. So the test watches a strip
 * of pixels over time and asks whether it moved.
 *
 * ### Why pixels rather than a state object
 *
 * There is nothing to ask. Foundation's marquee is a layout modifier with no
 * public state — no offset to read, no "is running" flag. What it does is move
 * ink, so what a test can check is whether the ink moved. That also happens to
 * be the claim worth checking, rather than a proxy for it.
 */
class MarqueeTest {

    @Test
    fun textTooWideForItsBoxScrolls() {
        assertTrue(
            movesOverTime(TooWide, reduceMotion = false),
            "a label wider than its box never moved — the marquee is not running, " +
                "so the end of the text can never be read",
        )
    }

    @Test
    fun textThatFitsStaysStill() {
        assertTrue(
            !movesOverTime(Fits, reduceMotion = false),
            "a label that fits its box is scrolling anyway — the modifier is safe " +
                "to apply unconditionally only if it does nothing in the common case",
        )
    }

    /**
     * The one animation in the library that is switched off rather than softened.
     *
     * Everything else degrades — a spring becomes a tween, a slide becomes a
     * fade. This one stops, because there is no gentler version of "forever",
     * and perpetual movement at the edge of vision is the specific thing that
     * preference exists to stop.
     */
    @Test
    fun reducedMotionStopsItEntirely() {
        assertTrue(
            !movesOverTime(TooWide, reduceMotion = true),
            "the label is still scrolling under reduced motion",
        )
    }

    /**
     * A held marquee is the finger's: it moves with it, and stands still while
     * the finger does, instead of carrying on its pass underneath.
     */
    @Test
    fun aDraggableMarqueeFollowsTheFingerAndHoldsStillUnderIt() {
        scene(TooWide, reduceMotion = false, draggable = true).use { scene ->
            // Two frames: the first measures the text, and only the second knows
            // there is anything to drag. No finger lands sooner than that.
            val before = scene.frames(2)
            // Well inside the initial pause, so any movement is the finger's.
            scene.drag(from = Grab, to = Grab - Pull, steps = 10, release = false)
            val pulled = scene.frames(2)
            assertTrue(!before.sameAs(pulled), "a pulled marquee did not move with the finger")
            val held = scene.frames(framesFor(MarqueeDefaults.Pause.inWholeMilliseconds.toInt() + Travel))
            assertTrue(
                pulled.sameAs(held),
                "a marquee held still under a finger carried on scrolling underneath it",
            )
            scene.release(Grab - Pull)
            val letGo = scene.frames(framesFor(Coast))
            val later = scene.frames(framesFor(MarqueeDefaults.Pause.inWholeMilliseconds.toInt() + Travel))
            assertTrue(
                !letGo.sameAs(later),
                "a marquee let go never set off again — it is stuck part-way through its text",
            )
        }
    }

    /** It is off unless asked for: the same drag on a plain marquee moves nothing. */
    @Test
    fun aMarqueeIsNotDraggableUnlessAskedTo() {
        scene(TooWide, reduceMotion = false, draggable = false).use { scene ->
            val before = scene.frames(2)
            scene.drag(from = Grab, to = Grab - Pull, steps = 10, release = false)
            assertTrue(
                before.sameAs(scene.frames(2)),
                "a marquee that was not made draggable moved under a finger",
            )
        }
    }

    /** Text that fits has nothing to pull, and a drag over it leaves it where it is. */
    @Test
    fun textThatFitsDoesNotDrag() {
        scene(Fits, reduceMotion = false, draggable = true).use { scene ->
            val before = scene.frames(2)
            scene.drag(from = Grab, to = Grab - Pull, steps = 10, release = false)
            assertTrue(before.sameAs(scene.frames(2)), "a label that fits its box moved under a finger")
        }
    }

    /**
     * Under reduced motion it is truncated, as it would be without the modifier,
     * until it is pulled. Pulled, it moves with the finger, because motion the
     * user makes is not motion done to them. Let go, it never sets off by itself.
     */
    @Test
    fun underReducedMotionItDragsButNeverSetsOffByItself() {
        val plain = scene(TooWide, reduceMotion = true, draggable = null).use { it.frames(2) }
        scene(TooWide, reduceMotion = true, draggable = true).use { scene ->
            val before = scene.frames(2)
            assertTrue(
                plain.sameAs(before),
                "under reduced motion, a draggable marquee at rest does not look like the " +
                    "plain truncated text",
            )
            scene.drag(from = Grab, to = Grab - Pull, steps = 10)
            val letGo = scene.frames(framesFor(Coast))
            assertTrue(!before.sameAs(letGo), "under reduced motion, a pulled marquee did not move")
            val later = scene.frames(framesFor(MarqueeDefaults.Pause.inWholeMilliseconds.toInt() + Travel))
            assertTrue(letGo.sameAs(later), "under reduced motion, a marquee let go set off by itself")
        }
    }

    /** Whether the strip of pixels under the label changes between two samples. */
    private fun movesOverTime(text: String, reduceMotion: Boolean): Boolean {
        scene(text, reduceMotion, draggable = false).use { scene ->
            // Past the initial pause, so the first sample is taken while it is
            // already under way — sampling from rest would report "still" for a
            // marquee that simply had not set off yet.
            val settled = scene.frames(framesFor(MarqueeDefaults.Pause.inWholeMilliseconds.toInt() + Settle))
            val later = scene.frames(framesFor(Travel))
            return !settled.sameAs(later)
        }
    }

    /** The label in a box, with the marquee, a draggable one, or with `null` none at all. */
    private fun scene(text: String, reduceMotion: Boolean, draggable: Boolean?): Scene = Scene(
        width = Width,
        height = Height,
        density = Density.toFloat(),
        reduceMotion = reduceMotion,
    ) {
        Box(
            Modifier.fillMaxSize().background(Theme.colours.background).padding(16.dp),
            Alignment.CenterStart,
        ) {
            Box(Modifier.width(BoxWidth.dp)) {
                Text(
                    text = text,
                    maxLines = 1,
                    modifier = if (draggable == null) Modifier else Modifier.marquee(draggable = draggable),
                )
            }
        }
    }

    private fun BufferedImage.sameAs(other: BufferedImage): Boolean {
        if (width != other.width || height != other.height) return false
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (getRGB(x, y) != other.getRGB(x, y)) return false
            }
        }
        return true
    }

    private fun framesFor(millis: Int): Int = (millis / FrameMillis).coerceAtLeast(1)

    private companion object {
        const val Density = 2
        const val Width = 600
        const val Height = 160

        /** Narrower than [TooWide] needs and wider than [Fits] needs. */
        const val BoxWidth = 120

        const val TooWide = "Elizabeth Quay Bus Station, Stand E"
        const val Fits = "Stand E"

        const val FrameMillis = 16

        /** Past the initial pause and into the travel. */
        const val Settle = 200

        /** Long enough that reading pace has moved the text a visible distance. */
        const val Travel = 400

        /** Long enough for a let-go marquee to stop coasting. */
        const val Coast = 1000

        /** On the label, in pixels: 16dp in, plus half of [BoxWidth]. */
        val Grab = Offset((16 + BoxWidth / 2f) * Density, Height / 2f)

        /** Far enough past the touch slop to move the text a visible distance. */
        val Pull = Offset(40f * Density, 0f)
    }
}
