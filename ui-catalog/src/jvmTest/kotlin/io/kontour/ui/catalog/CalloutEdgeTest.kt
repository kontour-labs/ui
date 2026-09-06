package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.display.Callout
import io.kontour.ui.foundation.Text
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A callout's accent is its leading edge, not a bar near it.
 *
 * The rule used to be a rounded bar set 8dp in from the edge, and the reason
 * was sound as far as it went: a band flush against a rounded container is cut
 * off by the corner, so on a short callout there is very little of it left. The
 * report is that the result looks like a tally mark someone left in the box and
 * should read as the very edge being highlighted.
 *
 * The taper is the effect rather than the fault. Measured here as one number:
 * how far in from the container's own leading edge the accent starts, on a row
 * through the middle where the corner is not involved. Eight dp of clear pale
 * ground before the rule is the defect; nothing is the fix.
 */
class CalloutEdgeTest {

    @Test
    fun theAccentStartsAtTheContainersLeadingEdge() {
        val image = render()
        val rows = image.rowsHoldingCallout()
        assertTrue(rows.isNotEmpty(), "no callout was drawn at all")
        val middle = rows[rows.size / 2]

        val edge = (0 until image.width).first { !image.isGround(it, middle) }
        val accent = (0 until image.width).firstOrNull { image.isAccent(it, middle) }

        assertTrue(accent != null, "no accent ink on the callout's middle row at all")
        assertTrue(
            accent - edge <= Slack,
            "the callout's leading edge is at ${edge}px and its accent starts at " +
                "${accent}px — ${accent - edge}px of plain container between the " +
                "two. The rule is a bar sitting near the edge rather than the " +
                "edge itself.",
        )
    }

    private fun render(): BufferedImage {
        var image: BufferedImage? = null
        Scene(width = 400, height = 200) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                Box(Modifier.padding(20.dp).width(280.dp)) {
                    Callout { Text("Trams do not run to the terminus after nine.") }
                }
            }
        }.use { scene -> image = scene.frames(20) }
        return requireNotNull(image)
    }

    /** White, or near enough — the scene's own background. */
    private fun BufferedImage.isGround(x: Int, y: Int): Boolean {
        val rgb = getRGB(x, y)
        return ((rgb shr 16) and 0xFF) > 250 &&
            ((rgb shr 8) and 0xFF) > 250 &&
            (rgb and 0xFF) > 250
    }

    /**
     * The accent rather than the pale container behind it.
     *
     * The two are the same hue and could not be told apart by colour alone; they
     * are told apart by weight. `accent.container` is a tint a few percent off
     * white and lands in the 230s, `accent.solid` is a saturated blue whose
     * green channel is under a hundred.
     */
    private fun BufferedImage.isAccent(x: Int, y: Int): Boolean =
        ((getRGB(x, y) shr 8) and 0xFF) < 150

    /** Rows that hold any of the callout, ground excluded. */
    private fun BufferedImage.rowsHoldingCallout(): List<Int> =
        (0 until height).filter { y -> (0 until width).any { !isGround(it, y) } }

    private companion object {
        /** A pixel of antialiasing on the container's own edge. */
        const val Slack = 2
    }
}
