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
 * same is true of the border and the shadow. Every rung of the token scale pays
 * it, the two small ones included: continuity that stops partway up a scale is a
 * discontinuity *in* the scale, and a badge with a corner from a different
 * design system to the card under it is more visible than the cost. What does
 * not pay it is [Shapes.pill], which is a circle by intent rather than by
 * saturation — an avatar, a status dot, a scrollbar thumb.
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

    /**
     * The last few paths this shape built, newest overwriting oldest.
     *
     * One entry was enough while a shape instance belonged to one component. It
     * is not now that the semantic tokens alias the scale — `Shapes.container`
     * *is* `Shapes.medium`, one object shared by every card, row, menu, popover
     * and drawer on screen. Two containers at different sizes evicted each
     * other, so both rebuilt their path on every draw, and building one is four
     * corners of trigonometry and twelve cubic segments.
     *
     * Written round-robin rather than least-recently-used: with a handful of
     * live sizes the two orders keep the same set, and a plain cursor has no
     * bookkeeping to get wrong. Not synchronised — a shape is used from the
     * thread that draws it, and the worst a race can do here is rebuild a path
     * that had already been built.
     */
    private val cache = arrayOfNulls<CachedPath>(CacheEntries)
    private var cursor = 0

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

        for (entry in cache) {
            // Null means the cache has not filled yet, and it fills in order, so
            // there is nothing past the first hole to look at.
            if (entry == null) break
            if (entry.matches(size, topLeft, topRight, bottomRight, bottomLeft)) {
                return Outline.Generic(entry.path)
            }
        }

        val path = buildPath(size, topLeft, topRight, bottomRight, bottomLeft)
        cache[cursor] = CachedPath(size, topLeft, topRight, bottomRight, bottomLeft, path)
        cursor = (cursor + 1) % cache.size
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

        // Budgets in travel order: the edge this corner is entered along, then
        // the one it leaves along. Top-left is entered up the left edge, which it
        // shares with bottom-left, and left along the top edge, shared with
        // top-right.
        val corners = listOf(
            Corner(
                Offset(0f, 0f), Offset(0f, -1f), Offset(1f, 0f), tl,
                share(tl, bl, h), share(tl, tr, w),
            ),
            Corner(
                Offset(w, 0f), Offset(1f, 0f), Offset(0f, 1f), tr,
                share(tr, tl, w), share(tr, br, h),
            ),
            Corner(
                Offset(w, h), Offset(0f, 1f), Offset(-1f, 0f), br,
                share(br, tr, h), share(br, bl, w),
            ),
            Corner(
                Offset(0f, h), Offset(-1f, 0f), Offset(0f, -1f), bl,
                share(bl, br, w), share(bl, tl, h),
            ),
        )

        val path = Path()
        val params = corners.map { params(it) }

        // Start on the top edge, just past whatever the top-left corner consumes,
        // then run clockwise. Each corner ends exactly where the next one starts,
        // so the only explicit line is the one closing each edge.
        val first = corners[0]
        path.moveTo(
            first.point.x + first.outgoing.x * params[0].outgoing.p,
            first.point.y + first.outgoing.y * params[0].outgoing.p,
        )

        for (i in 1..3) appendCorner(path, corners[i], params[i])
        // Corner 0 is the one we started inside, so it closes the ring.
        appendCorner(path, corners[0], params[0])
        path.close()
        return path
    }

    /**
     * How much of **one** edge this corner may spend.
     *
     * Split with the other corner on that edge in proportion to the two radii, so
     * a large corner beside a small one gets most of the edge rather than half of
     * it.
     */
    private fun share(radius: Float, otherOnEdge: Float, edgeLength: Float): Float {
        if (radius == 0f) return 0f
        return radius / (radius + otherOnEdge) * edgeLength
    }

    /**
     * The corner's geometry, with **each side smoothed as far as its own edge
     * allows**.
     *
     * ### Why the two sides are not asked the same question
     *
     * Smoothing needs room past the radius to put its blend in — `(1 + s)` times
     * the radius along the edge — so a corner that has spent its whole share of
     * an edge on the arc has none left on that side. That much was always true.
     * What was wrong was taking the *tighter* of the two edges and applying it to
     * both, because it makes one saturated edge silently square off the other.
     *
     * A capsule is where that bites, and a capsule is most of this library.
     * `Shapes.control` is half the short side, so on any button the two corners
     * at one end meet in the middle of that end with nothing between them: the
     * short edge is saturated, exactly and always. Under the old rule that
     * dropped the smoothing on the *long* edge too, and the result is a plain
     * circular arc — so every `Button`, `IconButton`, `Chip`, `Tag`, `FAB`,
     * `Toolbar`, `Tab` and `Switch` in the library drew a rounded rectangle while
     * naming a squircle and paying [Outline.Generic] for it. Against a `Card`,
     * which is not saturated and does smooth, the two read as corners from
     * different design systems, which is the entire complaint the shape scale
     * exists to answer.
     *
     * Per-edge, the same corner keeps its full arc where the end meets its
     * neighbour and eases into the long edge where there is room. The extent does
     * not change — a button is still exactly as round at its ends, within a
     * fifth of a percent of area — but the curvature no longer steps from the arc
     * to the straight edge.
     *
     * It also settles the exceptions by construction rather than by a list. A
     * square box at capsule radius saturates on *both* edges, so an `IconButton`,
     * an `Avatar`, a status dot and a radio ring stay true circles with nothing
     * opting them out.
     */
    private fun params(corner: Corner): CornerParams {
        // The arc has to fit on both edges before either side can smooth.
        val radius = min(corner.radius, min(corner.budgetIn, corner.budgetOut))
        if (radius <= 0f) return CornerParams.Square

        val sIn = min(smoothing, (corner.budgetIn / radius - 1f).coerceIn(0f, 1f))
        val sOut = min(smoothing, (corner.budgetOut / radius - 1f).coerceIn(0f, 1f))

        return CornerParams(
            radius = radius,
            incoming = side(radius, sIn, corner.budgetIn),
            outgoing = side(radius, sOut, corner.budgetOut),
            // Each side gives up `45 * s` degrees of the quarter to its blend.
            arcStartDegrees = 45f * sIn,
            arcDegrees = 90f - 45f * (sIn + sOut),
        )
    }

    /**
     * One side of a corner: how far it reaches along its edge, and the cubic that
     * gets it there.
     *
     * [CornerSide.projection] is where the arc ends, measured from the corner
     * point along the edge, and [CornerSide.offset] is how far that point sits
     * off the edge — `r(1 - sin 45s)` and `r(1 - cos 45s)`, which is just the arc
     * endpoint written in the edge's own axes. The rest of the reach is the
     * blend: [CornerSide.c] and the offset set the tangent at the arc, in the
     * ratio `tan 45s` so the cubic arrives along it, and [CornerSide.a] and
     * [CornerSide.b] run along the straight edge so it leaves with no curvature
     * at all.
     *
     * The projection used to be derived from the arc's chord, which is the same
     * number only while both sides are smoothed equally — the chord lies at 45°
     * to the edges exactly then and not otherwise.
     */
    private fun side(radius: Float, s: Float, budget: Float): CornerSide {
        val half = 45f * s
        val projection = radius * (1f - sin(half * DEG).toFloat())
        val offset = radius * (1f - cos(half * DEG).toFloat())
        val c = radius * tan(half / 2f * DEG).toFloat() * cos(half * DEG).toFloat()
        val p = min((1f + s) * radius, budget)
        val b = ((p - projection - c) / 3f).coerceAtLeast(0f)
        return CornerSide(a = 2f * b, b = b, c = c, offset = offset, p = p, projection = projection)
    }

    private fun appendCorner(path: Path, corner: Corner, param: CornerParams) {
        val point = corner.point
        val u = corner.incoming
        val v = corner.outgoing

        if (param.radius <= 0f) {
            path.lineTo(point.x, point.y)
            return
        }

        val into = param.incoming
        val away = param.outgoing

        val start = Offset(point.x - u.x * into.p, point.y - u.y * into.p)
        path.lineTo(start.x, start.y)

        // Two control points still on the straight edge, so the curve leaves it
        // with no curvature at all, and a third that *is* the arc's start —
        // `projection` along the edge, `offset` off it. The gap between the last
        // two sets the tangent there, so the cubic meets the arc going the way
        // the arc goes.
        val a = into.a
        val b = into.b
        path.cubicTo(
            start.x + u.x * a, start.y + u.y * a,
            start.x + u.x * (a + b), start.y + u.y * (a + b),
            point.x - u.x * into.projection + v.x * into.offset,
            point.y - u.y * into.projection + v.y * into.offset,
        )

        val centre = Offset(
            point.x - u.x * param.radius + v.x * param.radius,
            point.y - u.y * param.radius + v.y * param.radius,
        )
        val sweepStart = atan2(-v.y.toDouble(), -v.x.toDouble()) * RAD + param.arcStartDegrees
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

        // The same three mirrored, anchored on `end` and using the outgoing
        // side's own blend: `offset` back onto the edge line, then `c`, `b`, `a`
        // along it.
        val outA = away.a
        val outB = away.b
        val outC = away.c
        path.cubicTo(
            end.x + u.x * away.offset + v.x * outC,
            end.y + u.y * away.offset + v.y * outC,
            end.x + u.x * away.offset + v.x * (outB + outC),
            end.y + u.y * away.offset + v.y * (outB + outC),
            end.x + u.x * away.offset + v.x * (outA + outB + outC),
            end.y + u.y * away.offset + v.y * (outA + outB + outC),
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

        /**
         * How many distinct sizes one shape instance remembers a path for.
         *
         * Four covers what a screen actually holds — a card, a list row, a menu
         * and a popover are four sizes, and everything else on screen repeats
         * one of them. A miss is a rebuild, so a larger cache only helps a
         * screen that already has more distinct containers than it has room to
         * show.
         */
        private const val CacheEntries = 4
    }
}

/** A squircle with the same [CornerSize] on all four corners. */
fun SquircleShape(
    corner: CornerSize,
    smoothing: Float = SquircleShape.DefaultSmoothing,
): SquircleShape = SquircleShape(corner, corner, corner, corner, smoothing)

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

private const val DEG = PI / 180.0
private const val RAD = 180.0 / PI

private class Corner(
    val point: Offset,
    val incoming: Offset,
    val outgoing: Offset,
    val radius: Float,
    /** This corner's share of the edge it is entered along. */
    val budgetIn: Float,
    /** Its share of the edge it leaves along. */
    val budgetOut: Float,
)

/**
 * One half of a corner: the reach along its edge, and the cubic that gets there.
 *
 * @param a How far the blend runs straight along the edge before it starts to
 *   turn. Twice [b], so the curvature leaves the edge at zero.
 * @param b The second straight run.
 * @param c Toward the corner point along the edge, and [offset] away from it, in
 *   the ratio that puts the cubic's last leg on the arc's tangent.
 * @param offset How far the arc's endpoint sits off this edge.
 * @param p Total reach along the edge, from the corner point.
 * @param projection Where the arc's endpoint sits along the edge, from the
 *   corner point. `p - projection` is what the blend spends.
 */
private class CornerSide(
    val a: Float,
    val b: Float,
    val c: Float,
    val offset: Float,
    val p: Float,
    val projection: Float,
) {
    companion object {
        val None = CornerSide(0f, 0f, 0f, 0f, 0f, 0f)
    }
}

private class CornerParams(
    val radius: Float,
    val incoming: CornerSide,
    val outgoing: CornerSide,
    /** How far past the edge-perpendicular the arc starts, in degrees. */
    val arcStartDegrees: Float,
    val arcDegrees: Float,
) {
    companion object {
        val Square = CornerParams(0f, CornerSide.None, CornerSide.None, 0f, 0f)
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
