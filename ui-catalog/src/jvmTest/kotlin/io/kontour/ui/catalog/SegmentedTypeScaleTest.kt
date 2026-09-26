package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.selection.SegmentedControl
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A segmented control does not cut its own labels, at any text size.
 *
 * Reported as "changing text size on mobile sometimes breaks components, like
 * the segmented picker". Two separate losses were behind it and only one of them
 * was written down.
 *
 * ### The one nothing was watching
 *
 * The control pinned an exact height of `max(controlHeightMedium, minTouchTarget)`
 * — 48dp on Android, 44 elsewhere — at every font scale, because `Sizing` is
 * keyed on contrast and never on type. Less 12dp of track padding that is a 36dp
 * content box against a 14sp label with a 1.20 line height, which crosses it at
 * about 2.14x. Past that the segment's own `clip` cut the glyphs top and bottom.
 * There is no vertical equivalent of an ellipsis, so nothing marked the loss.
 *
 * ### Why no existing test could see either
 *
 * `WidthSweepTest` already sweeps the whole registry at 1.0, 1.5 and 2.0, and
 * `ComponentContractTest` renders every spec at 200% in RTL. Both compare the
 * **component** against its **container** — and the registry says so itself:
 * *"it measures ink spilling out of the box, and clipped text keeps every pixel
 * inside — that is what clipping is."* This defect is the **label** against the
 * **component**, and a 48dp box stays 48dp whatever it cuts off inside.
 *
 * ### What this measures
 *
 * Ink, in both directions, against the control's own edges. A label that has been
 * ellipsised is narrower than the same label given room; a label that has been
 * clipped touches the boundary it was cut at. So: draw the control twice, once at
 * a width where the labels comfortably fit and once where they do not, and
 * require the second to ink as much as the first. Stacking satisfies that;
 * ellipsising cannot.
 */
class SegmentedTypeScaleTest {

    /**
     * The reported case: four word labels, one of them long, in a phone's width.
     *
     * `Keyboard` is the label from the catalog's own Input modality control, which
     * is where this was seen.
     */
    private val options = listOf("Auto", "Touch", "Mouse", "Keyboard")

    @Test
    fun theLabelsAreNotCutWhenTheTrackIsTooNarrowForThem() {
        val roomy = inkWidth(widthDp = 520, fontScale = 1f)
        val squeezed = inkWidth(widthDp = 244, fontScale = 1f)

        assertTrue(roomy > 0, "nothing was drawn at all in the wide case")
        assertTrue(
            squeezed >= roomy - Tolerance,
            "the labels ink ${squeezed}px of a 244dp track against ${roomy}px " +
                "given room. A narrower track is not a reason to draw less of a " +
                "word — the control stacks its options into rows when they stop " +
                "fitting, and stacked every label is drawn in full.",
        )
    }

    @Test
    fun theLabelsAreNotCutAtTwoHundredPerCentType() {
        val roomy = inkWidth(widthDp = 520, fontScale = 1f)
        val large = inkWidth(widthDp = 380, fontScale = 2f)

        // Twice the type is not exactly twice the ink — antialiasing and hinting
        // are not linear — but it is far more than the same, and nowhere near
        // less. Anything at or below the 1x figure has lost glyphs.
        assertTrue(
            large > roomy,
            "at 200% type the labels ink ${large}px against ${roomy}px at 100%. " +
                "Bigger type draws more ink, so this is the control cutting words " +
                "to fit a box that did not grow with them.",
        )
    }

    @Test
    fun theGlyphsAreNotClippedTopOrBottomAtLargeType() {
        // The vertical half, and the one nothing guarded. Measured as ink
        // touching the control's own first and last rows: a glyph cut by the
        // segment's clip runs right up to the boundary, and one with room does
        // not.
        for (scale in listOf(1f, 1.3f, 2f)) {
            val touching = inkTouchesTheSegmentEdge(widthDp = 380, fontScale = scale)
            assertTrue(
                !touching,
                "at ${scale}x type the label's ink reaches the edge its segment " +
                    "is clipped at, which is a glyph being cut. " +
                    "The height has to grow with the type: the content box is a " +
                    "constant 36dp and a 14sp line is not.",
            )
        }
    }

    /** How many pixels of label the control draws, in total. */
    private fun inkWidth(widthDp: Int, fontScale: Float): Int {
        val frame = render(widthDp, fontScale)
        return (0 until frame.height).sumOf { y ->
            (0 until frame.width).count { x -> frame.isLabel(x, y) }
        }
    }

    /**
     * Whether any label ink reaches the edge a segment is clipped at.
     *
     * The **segment's** boundary, not the control's. The first draft sampled the
     * control's own first and last rows and passed against the very clipping it
     * was written for: those rows are the track's background, 6dp of padding
     * outside anything a segment draws, so no label could reach them whether or
     * not it had been cut. The clip is `TrackPadding` in from each edge.
     */
    private fun inkTouchesTheSegmentEdge(widthDp: Int, fontScale: Float): Boolean {
        var bounds = Rect.Zero
        val frame = render(widthDp, fontScale) { bounds = it }
        val pad = TrackPadding * Density
        val top = (bounds.top.toInt() + pad).coerceIn(0, frame.height - 1)
        val bottom = (bounds.bottom.toInt() - pad - 1).coerceIn(0, frame.height - 1)
        return (0 until frame.width).any { x ->
            frame.isLabel(x, top) || frame.isLabel(x, bottom)
        }
    }

    /**
     * Label ink, told apart from the track and the thumb by darkness.
     *
     * The page is white, the sunken track is a light grey and the thumb is white
     * again; only the type is dark. So one threshold separates the thing being
     * measured from everything drawn behind it, with no colour identity to pin.
     */
    private fun BufferedImage.isLabel(x: Int, y: Int): Boolean =
        (getRGB(x, y) shr 8 and 0xFF) < LabelChannel

    private fun render(
        widthDp: Int,
        fontScale: Float,
        onBounds: (Rect) -> Unit = {},
    ): BufferedImage {
        var frame: BufferedImage? = null
        var picked by mutableStateOf(0)
        Scene(
            width = (widthDp + Margin * 2) * Density,
            height = Height,
            density = Density.toFloat(),
            reduceMotion = true,
        ) {
            // Provided inside the scene as well as on it, because `Scene` builds
            // its own `Density` with the default font scale of 1 — the whole
            // golden suite is blind to type size for exactly that reason.
            CompositionLocalProvider(
                LocalDensity provides Density(Density.toFloat(), fontScale)
            ) {
                Box(Modifier.fillMaxSize().background(Color.White).padding(Margin.dp)) {
                    SegmentedControl(
                        options = options,
                        selectedIndex = picked,
                        onSelectedIndexChange = { picked = it },
                        modifier = Modifier.width(widthDp.dp).reportBounds(onBounds),
                    )
                }
            }
        }.use { scene -> frame = scene.frames(6) }
        return requireNotNull(frame)
    }

    private companion object {
        const val Density = 2
        const val Margin = 16
        const val Height = 700

        /** Dark type against a white page and a light grey track. */
        const val LabelChannel = 0x60

        /** `SegmentedControlDefaults.TrackPadding`, in dp. The segment starts here. */
        const val TrackPadding = 6

        /**
         * Slack on the ink comparison, in pixels.
         *
         * Not zero: a narrower track lays the same words out at different
         * subpixel positions, so the antialiasing differs by a few pixels either
         * way. An ellipsis costs whole glyphs — hundreds — so nothing near this
         * threshold is the failure being looked for.
         */
        const val Tolerance = 40
    }
}
