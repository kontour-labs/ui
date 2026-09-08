package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.action.FabSize
import io.kontour.ui.components.action.ExtendedFloatingActionButton
import io.kontour.ui.components.action.FloatingActionButton
import io.kontour.ui.foundation.SystemIcons
import io.kontour.ui.theme.KontourTheme
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A collapsed FAB is as wide as it is tall, at every size.
 *
 * Reported as "it's not a perfect circle when not expanded (in small and large
 * variants)", which is a claim about *layout* rather than about the corner —
 * a square box with a capsule corner is a circle, and an oblong one is not,
 * however the corner is specified.
 *
 * Measured rather than eyeballed because the golden cannot answer it: the FAB
 * casts a shadow, so the ink in the image is several pixels wider than the
 * control on every side and a bounding box over it is square whether the FAB is
 * or not.
 */
class FabShapeTest {

    private fun boundsOf(size: FabSize): Rect {
        var bounds = Rect.Zero
        Scene(width = 400, height = 400) {
            KontourTheme {
                Box(Modifier.fillMaxSize().background(Color.White).padding(20.dp)) {
                    FloatingActionButton(
                        icon = SystemIcons.Plus,
                        contentDescription = "Add",
                        onClick = {},
                        size = size,
                        modifier = Modifier.reportBounds { bounds = it },
                    )
                }
            }
        }.use { it.frames(3) }
        return bounds
    }

    private fun extendedBoundsOf(size: FabSize): Rect {
        var bounds = Rect.Zero
        Scene(width = 400, height = 400) {
            KontourTheme {
                Box(Modifier.fillMaxSize().background(Color.White).padding(20.dp)) {
                    ExtendedFloatingActionButton(
                        icon = SystemIcons.Plus,
                        contentDescription = "Add",
                        onClick = {},
                        size = size,
                        expanded = false,
                        modifier = Modifier.reportBounds { bounds = it },
                    ) { +"Add" }
                }
            }
        }.use { it.frames(6) }
        return bounds
    }

    @Test
    fun everyPlainSizeIsSquare() {
        val offenders = FabSize.entries.mapNotNull { size ->
            val b = boundsOf(size)
            if (abs(b.width - b.height) > 1f) "$size is ${b.width}×${b.height}" else null
        }

        assertTrue(
            offenders.isEmpty(),
            "a FAB has to be square or its capsule corner cannot draw a circle: " +
                offenders.joinToString("; "),
        )
    }

    /**
     * And the extended one, collapsed, is the same circle.
     *
     * This is the one that was reported and the one that was wrong. A collapsed
     * `ExtendedFloatingActionButton` is its icon plus a horizontal padding, and
     * that padding was a flat 16dp — which happens to equal `(56 - 24) / 2` and
     * so drew a circle at `Medium` and an oblong at both of its neighbours.
     * Exactly the sizes the report named.
     */
    @Test
    fun everyExtendedSizeIsSquareWhenCollapsed() {
        val offenders = FabSize.entries.mapNotNull { size ->
            val b = extendedBoundsOf(size)
            if (abs(b.width - b.height) > 1f) "$size is ${b.width}×${b.height}" else null
        }

        assertTrue(
            offenders.isEmpty(),
            "a collapsed extended FAB is not square, so it draws a lozenge where " +
                "the plain FAB beside it draws a circle: ${offenders.joinToString("; ")}",
        )
    }

    /**
     * And square is not the same claim as round.
     *
     * **Both tests above pass on a rounded square**, which is what the reporter
     * was actually looking at: "when the expanding fab collapses, it should morph
     * into a circle shape". A box can be exactly as wide as it is tall and still
     * have an 18dp corner on it, and nothing in this file could tell.
     *
     * That is not hypothetical. Round 26 capped the height-derived corners at
     * `CapsuleCap`, and stage 1b then moved "the ten circles" onto `pill` so the
     * cap could not reach them. `FloatingActionButton` was in that sweep;
     * `ExtendedFloatingActionButton` was not, and kept `Theme.shapes.control` —
     * half its height *up to 18dp*. On a 56dp box that is 18 rather than 28.
     *
     * ### Reading a corner off a render, without trusting the shadow
     *
     * Walk in along the diagonal from the box's corner until the fill starts. A
     * corner of radius `r` puts its arc `r(√2 - 1)` from the corner along that
     * diagonal, so the first lit pixel at diagonal offset `d` implies
     * `r = d(2 + √2)`. Reported as a fraction of half the box's height: **1.0 is
     * a circle**, and the 18dp cap on a 56dp FAB is 0.64.
     *
     * The threshold is on the *fill*, not on ink. A FAB casts a shadow, so there
     * is grey outside the control on every side — the same trap the KDoc at the
     * top of this file describes for bounding boxes. The fill is
     * `colours.primary`, near-black on a white page, and the shadow never gets
     * near it.
     */
    @Test
    fun aCollapsedExtendedFabIsRoundAndNotJustSquare() {
        val offenders = FabSize.entries.mapNotNull { size ->
            val fraction = extendedCornerFraction(size)
            if (fraction < RoundEnough) "$size is ${fraction.round()}" else null
        }

        assertTrue(
            offenders.isEmpty(),
            "a collapsed extended FAB's corner is not half its height, so it draws " +
                "a rounded square where the plain FAB beside it draws a circle " +
                "(1.0 is round, the 18dp cap is 0.64): ${offenders.joinToString("; ")}",
        )
    }

    /** The plain FAB, as the control: it is on `pill` and has to stay there. */
    @Test
    fun thePlainFabIsRoundToo() {
        val offenders = FabSize.entries.mapNotNull { size ->
            val fraction = plainCornerFraction(size)
            if (fraction < RoundEnough) "$size is ${fraction.round()}" else null
        }

        assertTrue(offenders.isEmpty(), "the control moved: ${offenders.joinToString("; ")}")
    }

    private fun plainCornerFraction(size: FabSize): Float {
        var bounds = Rect.Zero
        val image = Scene(width = 400, height = 400) {
            KontourTheme {
                Box(Modifier.fillMaxSize().background(Color.White).padding(20.dp)) {
                    FloatingActionButton(
                        icon = SystemIcons.Plus,
                        contentDescription = "Add",
                        onClick = {},
                        size = size,
                        modifier = Modifier.reportBounds { bounds = it },
                    )
                }
            }
        }.use { it.frames(3) }
        return cornerFraction(image, bounds)
    }

    private fun extendedCornerFraction(size: FabSize): Float {
        var bounds = Rect.Zero
        val image = Scene(width = 400, height = 400) {
            KontourTheme {
                Box(Modifier.fillMaxSize().background(Color.White).padding(20.dp)) {
                    ExtendedFloatingActionButton(
                        icon = SystemIcons.Plus,
                        contentDescription = "Add",
                        onClick = {},
                        size = size,
                        expanded = false,
                        modifier = Modifier.reportBounds { bounds = it },
                    ) { +"Add" }
                }
            }
        }.use { it.frames(6) }
        return cornerFraction(image, bounds)
    }

    /**
     * The corner's radius as a fraction of half the box's height.
     *
     * `bounds` is in the scene's own pixels, which is what `reportBounds` reports
     * and what `BufferedImage` is indexed in, so no density conversion belongs
     * here.
     */
    private fun cornerFraction(image: BufferedImage, bounds: Rect): Float {
        val limit = (minOf(bounds.width, bounds.height) / 2f).toInt()
        for (d in 0 until limit) {
            val x = (bounds.left + d).toInt()
            val y = (bounds.top + d).toInt()
            if (luminance(image, x, y) < FillLevel) {
                val radius = d * (2f + sqrt(2f))
                return radius / (bounds.height / 2f)
            }
        }
        return 0f
    }

    private fun luminance(image: BufferedImage, x: Int, y: Int): Float {
        val rgb = image.getRGB(x, y)
        return 0.2126f * ((rgb shr 16) and 0xFF) +
            0.7152f * ((rgb shr 8) and 0xFF) +
            0.0722f * (rgb and 0xFF)
    }

    private fun Float.round(): String = (kotlin.math.round(this * 100) / 100).toString()

    private companion object {
        /**
         * How round a corner has to read to count as one.
         *
         * A true circle measures 1.0 and the answer is quantised by the pixel the
         * walk lands on — one pixel either way is about 0.06 at `Small` — so this
         * is 1.0 less two of those. The defect it exists to catch is 0.64.
         */
        const val RoundEnough = 0.88f

        /**
         * Dark enough to be the FAB's fill rather than its shadow.
         *
         * `colours.primary` is near-black and the shadow over a white page never
         * gets below the low 200s, so anything in between separates them. This is
         * the distinction the bounding-box note at the top of the file is about.
         */
        const val FillLevel = 100f
    }
}
