package io.kontour.ui.theme

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.display.LinearProgress
import io.kontour.ui.foundation.GroupPosition
import io.kontour.ui.foundation.shape
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A theme that squares the scale squares everything the library draws, and the
 * default theme draws what it always did.
 *
 * Asked for for 0.5.2 as a "square" theme in which *"I'm able to make some
 * things square, but not all things"*: progress bars, slider tracks, page dots,
 * timeline stops, checkboxes and the corners where grouped rows meet each named
 * a radius of their own that no theme reached.
 */
@OptIn(ExperimentalTestApi::class)
class SquareThemeTest {

    private val density = Density(1f)
    private val square = kontourShapes(
        extraSmall = 0.dp, step = 0.dp, capsuleCap = 0.dp, pillCap = 0.dp, lineCap = StrokeCap.Butt,
    )

    @Test
    fun aGroupsInnerCornersAreNoRounderThanItsOuterOnes() {
        val middle = GroupPosition.Middle.shape(square.container, 4.dp, Orientation.Vertical)
        assertEquals(listOf(0f, 0f, 0f, 0f), radii(middle), "a square theme's grouped rows met at rounded corners")
        // The default is untouched: 4dp where the rows meet, inside a 22dp card.
        val defaults = GroupPosition.Middle.shape(Shapes().container, 4.dp, Orientation.Vertical)
        assertEquals(listOf(4f, 4f, 4f, 4f), radii(defaults))
    }

    @Test
    fun aSquareCornerOutsetStaysSquare() {
        assertEquals(0f, square.control.outset(3.dp).topStart.toPx(Size(80f, 40f), density))
        assertEquals(21f, Shapes().control.outset(3.dp).topStart.toPx(Size(86f, 46f), density))
    }

    @Test
    fun theMarkFollowsTheLadderDownButNotUp() {
        assertEquals(Shapes().mark, kontourShapes().mark)
        assertEquals(6f, Shapes().mark.topStart.toPx(Size(20f, 20f), density))
        assertEquals(0f, square.mark.topStart.toPx(Size(20f, 20f), density))
        assertEquals(2f, kontourShapes(extraSmall = 6.dp, step = 2.dp).mark.topStart.toPx(Size(20f, 20f), density))
        // A rounder ladder leaves a checkbox a checkbox: 10dp on a 20dp box is a circle.
        assertEquals(6f, kontourShapes(extraSmall = 14.dp).mark.topStart.toPx(Size(20f, 20f), density))
    }

    @Test
    fun theLineCapIsTheFactorysToSet() {
        assertEquals(StrokeCap.Round, Shapes().lineCap)
        assertEquals(StrokeCap.Butt, square.lineCap)
    }

    @Test
    fun aDotIsACircleByDefaultAndASquareInASquareTheme() {
        assertTrue(dotCorner(Shapes().pill) < 0.5f, "the default pill drew a dot with a filled corner")
        assertEquals(1f, dotCorner(square.pill), "a square pill drew a dot with an empty corner")
    }

    @Test
    fun aProgressBarsEndsAreTheThemes() {
        fun corner(shapes: Shapes?): Float {
            var alpha = 0f
            runComposeUiTest {
                setContent {
                    KontourTheme(shapes = shapes ?: Shapes(), reduceMotion = true) {
                        Box(Modifier.background(Color.White).padding(8.dp)) {
                            LinearProgress(
                                progress = 1f,
                                modifier = Modifier.width(120.dp).testTag("bar"),
                                colour = Color.Black,
                                height = 12.dp,
                            )
                        }
                    }
                }
                waitForIdle()
                val image = onNodeWithTag("bar").captureToImage().toPixelMap()
                alpha = image[0, 0].let { 1f - (it.red + it.green + it.blue) / 3f }
            }
            return alpha
        }
        assertTrue(corner(null) < 0.5f, "the default bar's corner was filled: its ends were not round")
        assertTrue(corner(square) > 0.95f, "a square theme's bar still had round ends")
    }

    /** How much of a 20 by 20 dot's top-left pixel is ink, from 0 to 1. */
    private fun dotCorner(shape: CornerBasedShape): Float {
        val image = ImageBitmap(20, 20)
        CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(image), Size(20f, 20f)) {
            drawDot(Color.Black, radius = 10f, center = Offset(10f, 10f), shape = shape)
        }
        return image.toPixelMap()[0, 0].alpha
    }

    private fun radii(shape: CornerBasedShape): List<Float> =
        listOf(shape.topStart, shape.topEnd, shape.bottomEnd, shape.bottomStart).map { it.toPx(Size(100f, 40f), density) }
}
