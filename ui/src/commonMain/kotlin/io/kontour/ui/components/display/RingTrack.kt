package io.kontour.ui.components.display

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate

/**
 * A ring that is not a circle: the rounded or square box the theme's pill gives
 * a spinner or a progress ring, for runs along it measured in degrees as
 * `drawArc` measures them, clockwise from three o'clock.
 *
 * An angle is a share of the way round, from twelve o'clock: the run goes at an
 * even pace along the edge rather than an even angle, which on a square is the
 * one that reads as steady, and a quarter of a ring is a quarter of the box's
 * outline. The outline and its measure are kept until the box or the corner
 * changes, so a moving frame builds nothing but the run it draws.
 *
 * Made for 0.5.2, when a square theme's spinner went back to a circle round a
 * square timeline stop, and its progress rings were circles beside square
 * everything else.
 */
internal class RingTrack {
    private val outline = Path()
    private val run = Path()
    private val measure = PathMeasure()
    private var box = Size.Zero
    private var corner = -1f

    /** The outline's length, once [fit]. */
    val length: Float get() = measure.length

    /** Builds the outline of a [box] with [corner]s, from its top centre round clockwise, unless it already is. */
    fun fit(box: Size, corner: Float) {
        if (box == this.box && corner == this.corner) return
        this.box = box
        this.corner = corner
        outline.rewind()
        val w = box.width
        val h = box.height
        if (w > 0f && h > 0f) {
            val r = corner.coerceIn(0f, minOf(w, h) / 2f)
            outline.moveTo(w / 2f, 0f)
            outline.lineTo(w - r, 0f)
            if (r > 0f) outline.arcTo(Rect(w - 2f * r, 0f, w, 2f * r), -90f, 90f, false)
            outline.lineTo(w, h - r)
            if (r > 0f) outline.arcTo(Rect(w - 2f * r, h - 2f * r, w, h), 0f, 90f, false)
            outline.lineTo(r, h)
            if (r > 0f) outline.arcTo(Rect(0f, h - 2f * r, 2f * r, h), 90f, 90f, false)
            outline.lineTo(0f, r)
            if (r > 0f) outline.arcTo(Rect(0f, 0f, 2f * r, 2f * r), 180f, 90f, false)
            outline.close()
        }
        measure.setPath(outline, forceClosed = true)
    }

    /** The whole ring, at [at]. */
    fun drawWhole(scope: DrawScope, colour: Color, at: Offset, stroke: Stroke) {
        scope.translate(at.x, at.y) { drawPath(outline, colour, style = stroke) }
    }

    /** How many degrees of this ring [distance] along its outline is. */
    fun degreesFor(distance: Float): Float = if (length > 0f) distance / length * 360f else 0f

    /** Where on the outline [angle] is, relative to [at]. */
    fun pointAt(at: Offset, angle: Float): Offset = measure.getPosition(distanceOf(angle)) + at

    /** The run from [startAngle] round [sweep] degrees, at [at]. */
    fun drawRun(scope: DrawScope, colour: Color, at: Offset, startAngle: Float, sweep: Float, stroke: Stroke) {
        val length = length
        if (length <= 0f || sweep <= 0f) return
        val from = distanceOf(startAngle)
        val along = (sweep / 360f).coerceAtMost(1f) * length
        run.rewind()
        val to = from + along
        if (to <= length) {
            measure.getSegment(from, to, run, startWithMoveTo = true)
        } else {
            measure.getSegment(from, length, run, startWithMoveTo = true)
            measure.getSegment(0f, to - length, run, startWithMoveTo = false)
        }
        scope.translate(at.x, at.y) { drawPath(run, colour, style = stroke) }
    }

    /** From twelve o'clock, where the outline starts, clockwise. */
    private fun distanceOf(angle: Float): Float = (((angle + 90f) / 360f) % 1f + 1f) % 1f * length
}
