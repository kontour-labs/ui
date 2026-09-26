package io.kontour.ui.theme

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.PathSegment
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Where a squircle's edges stop being straight, read back off the path it draws.
 *
 * Each edge of the path is one straight line between the two corners on it — the
 * curves are cubics and arcs — so the ends of that line are exactly where each
 * corner's curve begins. [cornerReaches] has to report the same places, or anything
 * built to meet the edge where it is straight (a chat bubble's tail) meets it on
 * the curve instead.
 */
class SquircleReachTest {

    private val density = Density(2f)
    private val size = Size(300f, 200f)

    @Test
    fun aCapsulesReachIsWhereItsPathLeavesTheEdge() {
        assertReachesMatch(SquircleShape(CapsuleCornerSize(cap = Shapes.CapsuleCap)))
    }

    @Test
    fun mixedCornersEachReachAsFarAsTheirOwnCurve() {
        assertReachesMatch(
            SquircleShape(
                topStart = CornerSize(4.dp),
                topEnd = CornerSize(18.dp),
                bottomEnd = CornerSize(10.dp),
                bottomStart = CornerSize(0.dp),
            ),
        )
    }

    /**
     * Two corners too large for their side are scaled down together before they
     * are drawn, and the reach sees them scaled.
     */
    @Test
    fun cornersTooLargeForTheirSideAreMeasuredAsDrawn() {
        assertReachesMatch(
            SquircleShape(
                topStart = CornerSize(80.dp),
                topEnd = CornerSize(12.dp),
                bottomEnd = CornerSize(12.dp),
                bottomStart = CornerSize(60.dp),
            ),
        )
    }

    private fun assertReachesMatch(shape: SquircleShape) {
        for (direction in LayoutDirection.entries) {
            val path = (shape.createOutline(size, direction, density) as Outline.Generic).path
            val lines = buildList {
                for (segment in path) {
                    if (segment.type != PathSegment.Type.Line) continue
                    val p = segment.points
                    add(Offset(p[0], p[1]) to Offset(p[2], p[3]))
                }
            }
            fun along(onEdge: (Offset) -> Boolean): List<Offset> =
                lines.filter { (a, b) -> onEdge(a) && onEdge(b) }.flatMap { listOf(it.first, it.second) }

            val w = size.width
            val h = size.height
            val top = along { abs(it.y) < Near }
            val bottom = along { abs(it.y - h) < Near }
            val left = along { abs(it.x) < Near }
            val right = along { abs(it.x - w) < Near }
            val reaches = shape.cornerReaches(size, density, direction)
            val arm = "$shape $direction"

            assertNear(top.minOf { it.x }, reaches.topLeft.x, "$arm: top-left along the top")
            assertNear(w - top.maxOf { it.x }, reaches.topRight.x, "$arm: top-right along the top")
            assertNear(bottom.minOf { it.x }, reaches.bottomLeft.x, "$arm: bottom-left along the bottom")
            assertNear(w - bottom.maxOf { it.x }, reaches.bottomRight.x, "$arm: bottom-right along the bottom")
            assertNear(left.minOf { it.y }, reaches.topLeft.y, "$arm: top-left down the left")
            assertNear(h - left.maxOf { it.y }, reaches.bottomLeft.y, "$arm: bottom-left up the left")
            assertNear(right.minOf { it.y }, reaches.topRight.y, "$arm: top-right down the right")
            assertNear(h - right.maxOf { it.y }, reaches.bottomRight.y, "$arm: bottom-right up the right")
        }
    }

    private fun assertNear(drawn: Float, reported: Float, what: String) {
        assertTrue(abs(drawn - reported) < Near, "$what: the path leaves the edge at $drawn, reported $reported")
    }

    private companion object {
        const val Near = 0.01f
    }
}
