package io.kontour.ui.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan

/**
 * A rectangle whose corners curve *continuously* rather than as circular arcs.
 *
 * A [androidx.compose.foundation.shape.RoundedCornerShape] corner is a quarter
 * circle bolted between two straight edges, and the join is a discontinuity in
 * curvature: the edge has none, the arc has all of it, and the eye reads the
 * seam even when it cannot name it. A squircle spends part of the corner easing
 * curvature in and part easing it out, so the transition has no seam. It is what
 * iOS has drawn since the 7 icon grid and what Figma calls corner smoothing.
 *
 * The construction is the standard one: each corner is a shortened circular arc
 * with a cubic Bézier on either side blending it into the straight edge.
 * [smoothing] is the fraction of the corner's 90° given over to those blends —
 * `0f` is exactly a rounded rectangle, `1f` gives the arc nothing at all.
 * [DefaultSmoothing] is 0.6, which is close to the system corner curve on iOS.
 *
 * ### What it costs
 *
 * `createOutline` returns [Outline.Generic], not [Outline.Rounded]. A generic
 * outline clips through a path rather than a fast rounded-rect path, and the
 * same is true of the border and the shadow. That is why the token scale spends
 * it only from `medium` up — below about 12dp the smoothing is not visible and
 * the cost buys nothing, and [Shapes.pill] is a true capsule where there is no
 * curvature discontinuity to remove in the first place.
 *
 * The path is cached on the size, the four resolved radii and the layout
 * direction, so a shape that draws every frame at a steady size builds its path
 * once.
 *
 * ### Degenerate corners
 *
 * Two cases have to be handled rather than assumed away, because both produce a
 * self-intersecting path rather than an error:
 *
 * - A radius larger than the space available. Each corner gets a *budget* — its
 *   share of each adjacent edge, split with the other corner on that edge in
 *   proportion to the two radii — and is clamped to it.
 * - A corner at, or near, its budget. Smoothing needs room *beyond* the radius
 *   to put the blends in, so it tapers to zero as the corner saturates. A
 *   fully-saturated corner is a semicircle and there is nothing to smooth.
 *
 * A radius of zero draws a square corner, which is what makes [Shapes.sheet] —
 * a squircle with its bottom two corners zeroed — come out right.
 */
@Immutable
class SquircleShape(
    topStart: CornerSize,
    topEnd: CornerSize,
    bottomEnd: CornerSize,
    bottomStart: CornerSize,
    val smoothing: Float = DefaultSmoothing,
) : CornerBasedShape(topStart, topEnd, bottomEnd, bottomStart) {

    private var cache: CachedPath? = null

    override fun copy(
        topStart: CornerSize,
        topEnd: CornerSize,
        bottomEnd: CornerSize,
        bottomStart: CornerSize,
    ): CornerBasedShape = SquircleShape(topStart, topEnd, bottomEnd, bottomStart, smoothing)

    override fun createOutline(
        size: Size,
        topStart: Float,
        topEnd: Float,
        bottomEnd: Float,
        bottomStart: Float,
        layoutDirection: LayoutDirection,
    ): Outline {
        if (topStart + topEnd + bottomEnd + bottomStart == 0f) {
            return Outline.Rectangle(Rect(Offset.Zero, size))
        }

        // The four resolved radii arrive in logical order. Everything below works
        // in visual order, which is the same mapping RoundedCornerShape does.
        val ltr = layoutDirection == LayoutDirection.Ltr
        val topLeft = if (ltr) topStart else topEnd
        val topRight = if (ltr) topEnd else topStart
        val bottomRight = if (ltr) bottomEnd else bottomStart
        val bottomLeft = if (ltr) bottomStart else bottomEnd

        val cached = cache
        if (cached != null && cached.matches(size, topLeft, topRight, bottomRight, bottomLeft)) {
            return Outline.Generic(cached.path)
        }

        val path = buildPath(size, topLeft, topRight, bottomRight, bottomLeft)
        cache = CachedPath(size, topLeft, topRight, bottomRight, bottomLeft, path)
        return Outline.Generic(path)
    }

    private fun buildPath(
        size: Size,
        topLeft: Float,
        topRight: Float,
        bottomRight: Float,
        bottomLeft: Float,
    ): Path {
        val w = size.width
        val h = size.height
        val ceiling = min(w, h) / 2f

        // Clamp to the half-dimension first so the budgets below are computed from
        // radii that could actually be drawn.
        val tl = topLeft.coerceIn(0f, ceiling)
        val tr = topRight.coerceIn(0f, ceiling)
        val br = bottomRight.coerceIn(0f, ceiling)
        val bl = bottomLeft.coerceIn(0f, ceiling)

        val corners = listOf(
            // corner point, incoming direction, outgoing direction, radius, budget
            Corner(Offset(0f, 0f), Offset(0f, -1f), Offset(1f, 0f), tl, budget(tl, bl, h, tr, w)),
            Corner(Offset(w, 0f), Offset(1f, 0f), Offset(0f, 1f), tr, budget(tr, tl, w, br, h)),
            Corner(Offset(w, h), Offset(0f, 1f), Offset(-1f, 0f), br, budget(br, tr, h, bl, w)),
            Corner(Offset(0f, h), Offset(-1f, 0f), Offset(0f, -1f), bl, budget(bl, br, w, tl, h)),
        )

        val path = Path()
        val params = corners.map { params(it) }

        // Start on the top edge, just past whatever the top-left corner consumes,
        // then run clockwise. Each corner ends exactly where the next one starts,
        // so the only explicit line is the one closing each edge.
        val first = corners[0]
        path.moveTo(
            first.point.x + first.outgoing.x * params[0].p,
            first.point.y + first.outgoing.y * params[0].p,
        )

        for (i in 1..3) appendCorner(path, corners[i], params[i])
        // Corner 0 is the one we started inside, so it closes the ring.
        appendCorner(path, corners[0], params[0])
        path.close()
        return path
    }

    /**
     * How much of the two adjacent edges this corner may spend.
     *
     * Split with the other corner on each edge in proportion to the two radii, so
     * a large corner beside a small one gets most of the edge rather than half of
     * it, and take the tighter of the two.
     */
    private fun budget(
        radius: Float,
        otherOnFirstEdge: Float,
        firstEdgeLength: Float,
        otherOnSecondEdge: Float,
        secondEdgeLength: Float,
    ): Float {
        if (radius == 0f) return 0f
        val first = radius / (radius + otherOnFirstEdge) * firstEdgeLength
        val second = radius / (radius + otherOnSecondEdge) * secondEdgeLength
        return min(first, second)
    }

    private fun params(corner: Corner): CornerParams {
        val budget = corner.budget
        val radius = min(corner.radius, budget)
        if (radius <= 0f) return CornerParams.Square

        // Smoothing needs room past the radius to put the blends in. Once the
        // corner has spent its whole budget on the arc there is none left, and the
        // ceiling here goes to zero of its own accord.
        val ceiling = (budget / radius - 1f).coerceIn(0f, 1f)
        val s = min(smoothing, ceiling)

        val arcDegrees = 90f * (1f - s)
        val arcChord = sin(arcDegrees / 2f * DEG).toFloat() * radius * SQRT2
        val alpha = (90f - arcDegrees) / 2f
        val handle = radius * tan(alpha / 2f * DEG).toFloat()
        val beta = 45f * s
        val c = handle * cos(beta * DEG).toFloat()
        val d = c * tan(beta * DEG).toFloat()

        val p = min((1f + s) * radius, budget)
        val b = ((p - arcChord - c - d) / 3f).coerceAtLeast(0f)
        val a = 2f * b

        return CornerParams(radius, a, b, c, d, p, arcDegrees)
    }

    private fun appendCorner(path: Path, corner: Corner, param: CornerParams) {
        val point = corner.point
        val u = corner.incoming
        val v = corner.outgoing

        if (param.radius <= 0f) {
            path.lineTo(point.x, point.y)
            return
        }

        val start = Offset(point.x - u.x * param.p, point.y - u.y * param.p)
        path.lineTo(start.x, start.y)

        val a = param.a
        val b = param.b
        val c = param.c
        val d = param.d

        path.cubicTo(
            start.x + u.x * a, start.y + u.y * a,
            start.x + u.x * (a + b), start.y + u.y * (a + b),
            start.x + u.x * (a + b + c) + v.x * d, start.y + u.y * (a + b + c) + v.y * d,
        )

        val centre = Offset(point.x - u.x * param.radius + v.x * param.radius, point.y - u.y * param.radius + v.y * param.radius)
        val sweepStart = atan2(-v.y.toDouble(), -v.x.toDouble()) * RAD + (90f - param.arcDegrees) / 2f
        path.arcTo(
            rect = Rect(
                centre.x - param.radius,
                centre.y - param.radius,
                centre.x + param.radius,
                centre.y + param.radius,
            ),
            startAngleDegrees = sweepStart.toFloat(),
            sweepAngleDegrees = param.arcDegrees,
            forceMoveTo = false,
        )

        // Where the arc actually finished, rather than where the maths says it
        // should have — the mirrored blend is relative to it.
        val arcEndAngle = (sweepStart + param.arcDegrees) * DEG
        val end = Offset(
            centre.x + param.radius * cos(arcEndAngle).toFloat(),
            centre.y + param.radius * sin(arcEndAngle).toFloat(),
        )

        path.cubicTo(
            end.x + u.x * d + v.x * c, end.y + u.y * d + v.y * c,
            end.x + u.x * d + v.x * (b + c), end.y + u.y * d + v.y * (b + c),
            end.x + u.x * d + v.x * (a + b + c), end.y + u.y * d + v.y * (a + b + c),
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SquircleShape) return false
        return topStart == other.topStart &&
            topEnd == other.topEnd &&
            bottomEnd == other.bottomEnd &&
            bottomStart == other.bottomStart &&
            smoothing == other.smoothing
    }

    override fun hashCode(): Int {
        var result = topStart.hashCode()
        result = 31 * result + topEnd.hashCode()
        result = 31 * result + bottomEnd.hashCode()
        result = 31 * result + bottomStart.hashCode()
        result = 31 * result + smoothing.hashCode()
        return result
    }

    override fun toString(): String =
        "SquircleShape(topStart=$topStart, topEnd=$topEnd, bottomEnd=$bottomEnd, " +
            "bottomStart=$bottomStart, smoothing=$smoothing)"

    companion object {
        /**
         * The house corner smoothing, 0.6.
         *
         * Chosen to sit close to the iOS system corner curve. It is a constant
         * rather than a [Shapes] field because a scale with two different
         * smoothings in it is a scale whose corners do not match each other; a
         * consumer who wants a different one passes it to every [SquircleShape]
         * they build, which is the point at which they will notice.
         */
        const val DefaultSmoothing: Float = 0.6f
    }
}

/** A squircle with the same radius on all four corners. */
fun SquircleShape(radius: Dp, smoothing: Float = SquircleShape.DefaultSmoothing): SquircleShape =
    SquircleShape(CornerSize(radius), CornerSize(radius), CornerSize(radius), CornerSize(radius), smoothing)

/** A squircle with a radius per corner, in logical (start/end) order. */
fun SquircleShape(
    topStart: Dp,
    topEnd: Dp,
    bottomEnd: Dp,
    bottomStart: Dp,
    smoothing: Float = SquircleShape.DefaultSmoothing,
): SquircleShape = SquircleShape(
    CornerSize(topStart),
    CornerSize(topEnd),
    CornerSize(bottomEnd),
    CornerSize(bottomStart),
    smoothing,
)

private const val SQRT2 = 1.4142135f
private const val DEG = PI / 180.0
private const val RAD = 180.0 / PI

private class Corner(
    val point: Offset,
    val incoming: Offset,
    val outgoing: Offset,
    val radius: Float,
    val budget: Float,
)

private class CornerParams(
    val radius: Float,
    val a: Float,
    val b: Float,
    val c: Float,
    val d: Float,
    val p: Float,
    val arcDegrees: Float,
) {
    companion object {
        val Square = CornerParams(0f, 0f, 0f, 0f, 0f, 0f, 0f)
    }
}

private class CachedPath(
    private val size: Size,
    private val topLeft: Float,
    private val topRight: Float,
    private val bottomRight: Float,
    private val bottomLeft: Float,
    val path: Path,
) {
    fun matches(size: Size, topLeft: Float, topRight: Float, bottomRight: Float, bottomLeft: Float): Boolean =
        this.size == size &&
            this.topLeft == topLeft &&
            this.topRight == topRight &&
            this.bottomRight == bottomRight &&
            this.bottomLeft == bottomLeft
}
