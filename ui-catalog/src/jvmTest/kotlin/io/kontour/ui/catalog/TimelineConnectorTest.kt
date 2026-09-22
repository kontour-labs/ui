package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.display.ConnectorStyle
import io.kontour.ui.components.display.Timeline
import io.kontour.ui.components.display.TimelineItem
import io.kontour.ui.foundation.Text
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Every connector style runs the whole length of its row.
 *
 * Reported of the dotted one: it "stops just a bit short of the actual timeline
 * point". It did, and by an amount that depended on the row — which is why it
 * reads as a rendering fault rather than as a setting.
 *
 * ### Two styles had a remainder, and one of them twice over
 *
 * All three used to be one `drawLine` from the node's gap down to the bottom of
 * the gutter with the style as a dash pattern over it. `Solid` has no pattern and
 * ends where the line ends. The other two are walked from the start of the path
 * and abandoned wherever the path runs out, so whatever is left over is blank:
 * `Dashed` loses up to one gap, and `Dotted` — a dash of length *zero* — loses a
 * whole dot, because there is no partial dot to draw.
 *
 * `Dotted` loses one **even when the pitch divides the run exactly**, which is the
 * part worth writing down: a zero-length dash that falls on the path's own
 * endpoint is not drawn at all. The 120dp arm below has a remainder of nought and
 * still came up a full 8px short, so "make the pitch divide" is not a fix on its
 * own and the dots have to be placed rather than patterned.
 *
 * Only the dots were reported. The dashes are here because they are the same
 * fault and were found by the same measurement.
 *
 * ### Measured against the solid line, not against a number
 *
 * The bottom of the ink is not the bottom of the row: a round cap puts half a
 * stroke past the end, and its antialiased tip fades out over a pixel or two. So
 * the assertion is `Dotted` against `Solid` in the same scene at the same height,
 * where that overhang is identical and cancels.
 *
 * ### Why the heights are a sweep
 *
 * The shortfall is the run length modulo the pitch, so a row can be exactly
 * right by luck. At this density the pitch is 8px and the run is
 * `height − 32px`, which makes 120dp a remainder of zero — worth keeping as the
 * arm that passed all along, and useless on its own.
 */
class TimelineConnectorTest {

    @Test
    fun aDottedConnectorReachesAsFarDownTheRowAsASolidOne() {
        val short = mutableListOf<String>()
        for (height in Heights) {
            val solid = reach(ConnectorStyle.Solid, height)
            val dotted = reach(ConnectorStyle.Dotted, height)
            if (solid - dotted > Tolerance) {
                short += "at ${height}dp the solid line reaches ${solid}px and the " +
                    "dots stop at ${dotted}px, ${solid - dotted}px short"
            }
        }

        assertTrue(
            short.isEmpty(),
            "the dotted connector stopped short of where the same row's solid one " +
                "ends — ${short.joinToString("; ")}. A connector joins one node to " +
                "the next, so a gap at the bottom of it reads as the rail coming " +
                "apart rather than as a dot pitch that did not divide.",
        )
    }

    /**
     * And the dashed one, which had the same fault more quietly.
     *
     * A dash that is cut short by the end of the line still reaches it; a run that
     * finishes *inside a gap* does not, and at four of these four heights it did.
     * It is a gap rather than a whole dot, which is presumably why only the dots
     * were reported — and is the same defect.
     */
    @Test
    fun aDashedConnectorReachesTheEndToo() {
        val short = mutableListOf<String>()
        for (height in Heights) {
            val solid = reach(ConnectorStyle.Solid, height)
            val dashed = reach(ConnectorStyle.Dashed, height)
            if (solid - dashed > Tolerance) {
                short += "at ${height}dp: ${dashed}px against ${solid}px"
            }
        }
        assertTrue(short.isEmpty(), "the dashed connector stopped short — ${short.joinToString("; ")}")
    }

    /** How far down the page the gutter's ink goes, for one style at one height. */
    private fun reach(style: ConnectorStyle, rowHeight: Int): Float {
        var bounds = Rect.Zero
        var lowest = -1f
        Scene(width = 400, height = 500) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                Timeline(Modifier.width(300.dp)) {
                    TimelineItem(
                        connector = style,
                        nodeColour = Color.Black,
                        connectorColour = Color.Black,
                        modifier = Modifier.height(rowHeight.dp).reportBounds { bounds = it },
                    ) {
                        Text("Perth Station")
                    }
                }
            }
        }.use { scene ->
            val image = scene.frames(8)
            assertTrue(bounds.width > 0f, "the timeline item never reported a size")
            lowest = image.lowestGutterInk(bounds)
                ?: error("nothing was drawn in the gutter at all for $style")
        }
        return lowest
    }

    /**
     * The last row of the gutter with anything dark in it.
     *
     * Reaches a little past the row, because a round cap does. The content beside
     * the gutter is well clear of the 28dp band this looks at.
     */
    private fun BufferedImage.lowestGutterInk(bounds: Rect): Float? {
        val gutter = bounds.left.toInt() until (bounds.left + GutterWidth).toInt()
        var lowest = -1
        for (y in bounds.top.toInt() until minOf(height, (bounds.bottom + Overhang).toInt())) {
            if (gutter.any { x -> dark(x, y) }) lowest = y
        }
        return if (lowest < 0) null else lowest.toFloat()
    }

    private fun BufferedImage.dark(x: Int, y: Int): Boolean {
        val p = getRGB(x, y)
        return ((p shr 16 and 0xFF) + (p shr 8 and 0xFF) + (p and 0xFF)) / 3 < 200
    }

    private companion object {
        /**
         * Four rows, whose runs leave remainders of nought, two, four and six
         * pixels against the dots' 8px pitch.
         *
         * The nought is not a control — it failed too, for the endpoint reason
         * above — but it is the arm that says so.
         */
        val Heights = listOf(120, 121, 122, 123)

        /** 28dp of gutter at density two. */
        const val GutterWidth = 56f

        /** Room for the round cap and its antialiased tip. */
        const val Overhang = 8f

        /**
         * Half a stroke, which is what an antialiased cap tip can differ by.
         *
         * Anything more is a missing dot: the smallest shortfall the fault can
         * produce is a quarter of the pitch and the largest is all of it.
         */
        const val Tolerance = 2f
    }
}
