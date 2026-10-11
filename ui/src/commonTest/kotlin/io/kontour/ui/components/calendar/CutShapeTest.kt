package io.kontour.ui.components.calendar

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * An event cut off by the edge of the view is square on that side, and rounded
 * where it really ends: a bar at its start or end, a block at its top or foot.
 */
class CutShapeTest {

    private fun rounded(shape: androidx.compose.ui.graphics.Shape, direction: LayoutDirection = LayoutDirection.Ltr) =
        when (val outline = shape.createOutline(Size(100f, 20f), direction, Density(1f))) {
            is Outline.Rounded -> outline.roundRect
            is Outline.Rectangle -> RoundRect(outline.rect)
            else -> error("an event's outline is $outline")
        }

    @Test
    fun aBarRunningOnIsSquareOnlyOnTheSideItRunsOnAt() {
        val capsule = RoundedCornerShape(50)
        val before = rounded(cutShape(capsule, EventPresentation.Bar, clippedStart = true, clippedEnd = false))
        assertEquals(0f, before.topLeftCornerRadius.x, "running on before, its start is still rounded")
        assertEquals(0f, before.bottomLeftCornerRadius.x)
        assertTrue(before.topRightCornerRadius.x > 0f, "its end, where it ends, was squared too")
        val after = rounded(cutShape(capsule, EventPresentation.Bar, clippedStart = false, clippedEnd = true))
        assertTrue(after.topLeftCornerRadius.x > 0f)
        assertEquals(0f, after.bottomRightCornerRadius.x, "running on after, its end is still rounded")
        val both = rounded(cutShape(capsule, EventPresentation.Bar, clippedStart = true, clippedEnd = true))
        assertEquals(0f, both.topLeftCornerRadius.x + both.topRightCornerRadius.x + both.bottomLeftCornerRadius.x + both.bottomRightCornerRadius.x, "running on both ways, it is a block")
    }

    @Test
    fun rightToLeftItsStartIsOnTheRight() {
        val start = rounded(cutShape(RoundedCornerShape(50), EventPresentation.Bar, clippedStart = true, clippedEnd = false), LayoutDirection.Rtl)
        assertEquals(0f, start.topRightCornerRadius.x, "right to left, the start is the right")
        assertTrue(start.topLeftCornerRadius.x > 0f)
    }

    @Test
    fun aBlockRunningPastMidnightIsSquareAtItsFoot() {
        val foot = rounded(cutShape(RoundedCornerShape(4.dp), EventPresentation.Block, clippedStart = false, clippedEnd = true))
        assertTrue(foot.topLeftCornerRadius.x > 0f && foot.topRightCornerRadius.x > 0f, "its top, where it starts, was squared")
        assertEquals(0f, foot.bottomLeftCornerRadius.x + foot.bottomRightCornerRadius.x, "running on past midnight, its foot is rounded")
    }

    @Test
    fun anEventShownWholeKeepsItsShape() {
        val capsule = RoundedCornerShape(50)
        assertTrue(cutShape(capsule, EventPresentation.Bar, clippedStart = false, clippedEnd = false) === capsule)
        assertTrue(cutShape(capsule, EventPresentation.Line, clippedStart = true, clippedEnd = true) === capsule, "a line has no ground to square")
    }
}
