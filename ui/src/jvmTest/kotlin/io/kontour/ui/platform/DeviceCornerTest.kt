package io.kontour.ui.platform

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.kontour.ui.sheet.SheetDefaults
import io.kontour.ui.sheet.SheetPresentation
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The display's corners reach the shapes that are nested inside it.
 *
 * `ConcentricTest` has the arithmetic and `DeviceCornerTableTest` has the
 * parser; what neither can see is whether the platform's answer is *wired* to
 * anything. This is the wiring, and it is only testable at all because the JVM
 * actual has a seam — the same one `systemDarkOverride` is, and for the same
 * reason: the two platforms that can really answer have no test source set here.
 *
 * ### What a phone does that this cannot
 *
 * `:ui:compileIosMainKotlinMetadata` type-checks the iOS actual against real
 * UIKit and that is the end of what this repository can do with it. Whether
 * `_displayCornerRadius` is still readable, whether `RoundedCorner` reports all
 * four positions on a given OEM's build, and whether the curated smoothing looks
 * right against a real bezel are all a device's to settle.
 */
class DeviceCornerTest {

    @Test
    fun aSheetsTopCornersTakeTheDisplaysOwn() {
        val corners = shapeUnder(DeviceCorners.uniform(55.dp))
        // The gap the sheet sits below the window's top edge comes off first, so
        // the corner is the bezel's less that — concentric rather than equal.
        assertTrue(
            corners.topStart > Theme34,
            "a 55dp display left the sheet on its own ${corners.topStart}px " +
                "corner, so the platform's answer is reaching nothing",
        )
        assertEquals(
            corners.topStart,
            corners.topEnd,
            "a symmetric display drew two different top corners",
        )
        assertEquals(0f, corners.bottomStart, "a bezel rounded the sheet's flush edge")
    }

    /**
     * An asymmetric display draws two different corners, which is the whole
     * reason all four are read.
     *
     * A sheet only has two corners to move, so the case that shows here is a
     * left/right difference rather than the top/bottom one the table carries.
     * They are the same fact: the platform reports **physical** positions, and a
     * rotated window is exactly how a top/bottom asymmetry arrives as a
     * left/right one.
     */
    @Test
    fun anAsymmetricDisplayDrawsTwoDifferentTopCorners() {
        val corners = shapeUnder(DeviceCorners(60.dp, 20.dp, 20.dp, 60.dp))
        assertTrue(
            corners.topStart > corners.topEnd + 1f,
            "a display with a 60dp left corner and a 20dp right one drew " +
                "${corners.topStart} and ${corners.topEnd}. One reading applied " +
                "four times is what this is here to catch.",
        )
    }

    @Test
    fun noDisplayLeavesTheThemesOwnCorner() {
        val corners = shapeUnder(null)
        assertEquals(
            Theme34,
            corners.topStart,
            "with nothing to be concentric with, the sheet is supposed to keep " +
                "exactly the shape the scale gave it",
        )
    }

    /** The sheet's resolved corners, in pixels, under a given display. */
    private fun shapeUnder(device: DeviceCorners?): Corners {
        var shape: Shape? = null
        deviceCornersOverride = device
        val scene = ImageComposeScene(width = 200, height = 200, density = Density(1f)) {
            KontourTheme {
                shape = SheetDefaults.shapeFor(SheetPresentation.Edge)
                Box(Modifier.fillMaxSize())
            }
        }
        try {
            scene.render(0L).close()
        } finally {
            scene.close()
            deviceCornersOverride = null
        }
        val resolved = shape as CornerBasedShape
        val box = Size(400f, 400f)
        val density = Density(1f)
        return Corners(
            topStart = resolved.topStart.toPx(box, density),
            topEnd = resolved.topEnd.toPx(box, density),
            bottomStart = resolved.bottomStart.toPx(box, density),
        )
    }

    private class Corners(val topStart: Float, val topEnd: Float, val bottomStart: Float)

    private companion object {
        /** `Theme.shapes.sheet` is `extraLarge`, which is 34dp at 1x. */
        const val Theme34 = 34f
    }
}
