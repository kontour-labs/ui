package io.kontour.ui.components.selection

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * One slider thumb, stretched by how far it is from where it is trying to be.
 *
 * Shared by [Slider] and [RangeSlider] because they are the same control with a
 * second thumb on it, and `RangeSlider`'s own header already says what happens
 * otherwise: *"Two sliders in one library that answer the same gesture
 * differently is worse than either of them being wrong."* That was true of the
 * gesture and had been copied by hand for the drawing.
 *
 * ### One signal, three situations
 *
 * [reachPx] is the gap between the thumb's **target** and where it is actually
 * drawn — signed, positive meaning it is being pulled to the right — and every
 * case the two sliders have falls out of it without a special case:
 *
 * - **A dragged thumb on a stepped slider** is held at its detent while the
 *   finger goes past, so the target leads the drawing by the strain and the
 *   thumb elongates toward the finger. The longer it is held between notches the
 *   further it stretches, and it snaps round again the moment it lands.
 * - **A tapped thumb** is travelling: its spring lags its new value, so it
 *   stretches along the direction of travel and rounds off as it arrives. This is
 *   what the docs have claimed the slider does since it was written, and what it
 *   did not do — it grew 25% uniformly and stayed a circle.
 * - **A thumb being pushed by the other one** lags for the same reason a tapped
 *   one does, so being shoved reads as being shoved. That is the case this was
 *   asked for.
 *
 * - **A dragged thumb on a continuous slider** is pinned to the finger, so the
 *   gap is zero and it stays round. Which is right: there is nothing straining.
 *
 * At `reachPx == 0` this is a circle, so there is no discontinuity between a
 * thumb that is moving and one that has stopped.
 *
 * @param scale The uniform grow-while-touched, applied to the radius. The stretch
 *   is on top of it and along one axis only.
 * @param aspect How much wider than tall the thumb is: 1 at rest, growing
 *   towards [SliderDefaults.ThumbAspect] while it is held.
 * @param squashPx How hard the thumb is being pushed into a wall it cannot pass
 *   — signed the same way as [reachPx], and a *shortening* rather than a
 *   stretch. Its own channel rather than a second contributor to [reachPx],
 *   which is what it used to be: see the note in the body.
 * @param ringPx The page-coloured ring that keeps the thumb legible where it
 *   overlaps the filled track. A constant width rather than a scaled one: a
 *   border that thickens as the thumb grows reads as the thumb changing weight.
 */
internal fun DrawScope.sliderThumb(
    centreX: Float,
    centreY: Float,
    radiusPx: Float,
    scale: Float,
    aspect: Float,
    reachPx: Float,
    squashPx: Float = 0f,
    ringColour: Color,
    fillColour: Color,
    ringPx: Float,
) {
    val r = radiusPx * scale
    val limit = r * SliderDefaults.MaxStretch
    val reach = reachPx.coerceIn(-limit, limit)

    // A circle at rest, a capsule while it is being dragged.
    //
    // It used to be a capsule always, at a fixed 1.5, on the argument that a
    // round thumb reads as a dot sitting *on* the track rather than as a handle
    // *for* it. That is true of a thumb you are holding and not of one you are
    // only looking at: at rest the slider is showing a value, and a circle is
    // the quieter mark for that. Lengthening it on touch says *now* it is a
    // handle, and says it at the moment the claim is true.
    //
    // The corner stays half the height whatever the aspect is, so it is a
    // capsule at every point along the way — the same rule `Shapes.control`
    // uses, arrived at from the drawing side.
    //
    // Drawn as a plain rounded rect rather than clipped to `Shapes.capsule`, and
    // this is the one place in the library where that is the right trade. It
    // used to be justified as "a capsule has no curvature discontinuity to
    // smooth", which turned out to be simply untrue — a capsule's end meets its
    // straight edges with the same step as any other arc, and `SquircleShape`
    // eases it now. The real reason is cost: this thumb stretches to 1.25x and
    // leans toward the finger, so its size is different on every frame of a
    // drag. A shape caches its path on the size it was built at, so a thumb
    // would miss that cache every frame and rebuild four corners of trigonometry
    // and twelve cubics, sixty times a second, under a finger. At 24dp the
    // smoothing is worth about 0.9px.
    val halfWidth = r * aspect

    val stretchedLeft = centreX - halfWidth + minOf(reach, 0f)
    val stretchedRight = centreX + halfWidth + maxOf(reach, 0f)

    // **A wall shortens the thumb. It does not lengthen it.**
    //
    // [squashPx] used to be summed into [reachPx] at the call sites, so the
    // stretch above handled it — which meant pushing into the end of the track
    // made the thumb *grow*, backwards, away from the wall, because growing
    // towards where it is trying to be is exactly what the reach is for. It was
    // built to the word "stretching" in the report and the report meant the
    // other thing: pushing into something that will not move squashes the thing
    // doing the pushing.
    //
    // Its own channel rather than a sign convention on the old one. The two are
    // very nearly exclusive in practice — at a stop the target is clamped to the
    // end, so there is no gap left for the reach to describe — but they are
    // different arithmetic on the same edges, and summing them made one of the
    // two impossible to express at all.
    //
    // Normalised against [EndStopTravel], which is what both sliders hand the
    // band and is a distance the *finger* travels rather than anything about
    // this thumb. It used to be `radiusPx * MaxStretch` — about 6.6dp — and a
    // finger crosses that inside one frame, which is why the deformation read
    // as two states rather than as a pull.
    val squashLimit = EndStopTravel.toPx()
    val pull = if (squashLimit <= 0f) 0f else (abs(squashPx) / squashLimit).coerceIn(0f, 1f)

    // **Measured from the thumb at rest, not from the thumb as it currently is.**
    //
    // It was a fraction of the drawn width, which on a thumb that has already
    // grown under the finger cancels out: at 1.25x grown and 0.16 squashed the
    // arithmetic came back *above* the resting size, so pushing into a wall
    // could not make the thumb smaller than the circle it is at rest. Reported
    // on `Switch`, which had the same bug in the same shape, and true here.
    //
    // So the target is a fixed [ThumbSquash] off the resting diameter and the
    // drawn width travels to it as the band comes out. At rest it is exactly the
    // stretch above, at full pull it is narrower than the thumb has ever been,
    // and in between it tracks the finger.
    val width = stretchedRight - stretchedLeft
    val target = width + (radiusPx * 2f * (1f - ThumbSquash) - width) * pull
    // Pinned against whichever end was pushed into: the leading edge stays on the
    // wall and the trailing one comes in to meet it, so the thumb visibly
    // shortens against the stop and springs back out of it. `Switch` does the
    // same thing to its own thumb at the same amplitude.
    val lost = (width - target).coerceAtLeast(0f)
    val left = if (squashPx > 0f) stretchedLeft + lost else stretchedLeft
    val right = if (squashPx > 0f) stretchedRight else stretchedRight - lost

    drawRoundRect(
        color = ringColour,
        topLeft = Offset(left, centreY - r),
        size = Size(right - left, r * 2f),
        cornerRadius = CornerRadius(r),
    )
    drawRoundRect(
        color = fillColour,
        topLeft = Offset(left + ringPx, centreY - r + ringPx),
        size = Size(
            (right - left - ringPx * 2f).coerceAtLeast(0f),
            (r * 2f - ringPx * 2f).coerceAtLeast(0f),
        ),
        cornerRadius = CornerRadius((r - ringPx).coerceAtLeast(0f)),
    )
}

/**
 * How much narrower than its resting self a thumb gets, pushed all the way into
 * a wall.
 *
 * 0.25, the same as `Switch`'s thumb and `SegmentedControl`'s indicator, and
 * deliberately the same rather than coincidentally: a squash is a squash, and
 * controls in one library that deform by visibly different amounts under the
 * same gesture is the class of inconsistency `SliderThumb` exists to remove for
 * the two sliders. Written out in each of the three because they share no other
 * arithmetic.
 *
 * It was 0.16 *of the drawn width*, which on a thumb already grown under the
 * finger came out wider than the thumb at rest — so the deepest push produced
 * something that was not visibly a squash at all. A quarter off the **resting**
 * width is about 6dp on a 24dp thumb, which reads from across a room.
 */
private const val ThumbSquash: Float = 0.25f

/**
 * How far past a stop a finger travels for a full deformation.
 *
 * The scale of the pull rather than a hard stop — [io.kontour.ui.interaction.RubberBand]
 * approaches its limit and never arrives, so a finger travels about this far for
 * 63% of the squash, twice for 86% and two and a half times for 90%. 26dp puts
 * a full squash at roughly 60dp of travel, which is a deliberate gesture and not
 * an accident of where a finger stopped.
 *
 * Every control a finger can push past the end of takes this one number:
 * `Slider`, `RangeSlider`, `Switch` and `SegmentedControl`. They used to derive
 * a limit each from their own geometry — 6.6dp for a slider thumb, 6dp for a
 * switch, a fifth of a segment — and every one of them was small enough for a
 * single frame's delta to cross, which is what "there are only two states,
 * squashed and normal" was describing.
 *
 * It lives here rather than on a `*Defaults` object because it is a fact about
 * what a rubber band feels like, not a dial a brand reaches for, and here rather
 * than beside `RubberBand` because the sheet and the wheel picker measure their
 * overshoot in their own content and should go on doing so.
 */
internal val EndStopTravel: Dp = 26.dp

/**
 * The detent marks along a slider's track.
 *
 * One drawing, because there were two and they had drifted into being the same
 * arithmetic written twice — `trackHeight * 0.22f`, in `Slider` and in
 * `RangeSlider`, differing only in how each decides whether a mark is covered.
 * That is the shape [sliderThumb] is already in and for the same reason.
 *
 * **Two shapes, because they say two different things.** A major mark is a bar
 * that *crosses* the track: these were dots at a factor of the track height —
 * 1.76dp across on a 4dp track, under two pixels at 1x — and the report was that
 * they are not obvious enough. A dot on a bar is the hardest mark to see there
 * is, because it shares the track's own axis and can only differ from it in
 * colour. A bar differs in *shape*, and stays legible when the two colours are
 * close.
 *
 * A minor mark stays a dot, and that is the point of it: it is a finer reading
 * between two notches, and it should read as subordinate at a glance rather than
 * as a shorter bar somebody has to measure against its neighbours. They
 * subdivide each step, so `steps = 4, minorTicks = 1` is a dot halfway between
 * every pair of bars.
 *
 * There is room for the bars. The track is 4dp but the control is 44dp and this
 * draws unclipped and centred, so a mark up to about the thumb's own diameter is
 * still inside its silhouette — and the thumb is painted after, over the top.
 *
 * **The colour rule follows the shape, and only the dots take it.** A dot lives
 * *inside* the 4dp track, so on the filled side it has to be drawn in the fill's
 * contrasting tone or it vanishes — which is what `onPrimary` has always been
 * for. A bar does not need that: it is legible where it stands proud, against
 * the page. Giving the bars the same treatment made every one of them punch a
 * white gap through the fill, so a stepped slider arrived as a **dashed line** —
 * a worse misreading than invisible dots were, because it looks like a property
 * of the track rather than a scale drawn over it.
 *
 * So the fill runs solid under the bars and is dotted by the minor marks exactly
 * as it used to be.
 *
 * @param covered Whether the mark at this position has been passed — the filled
 *   side of a slider, or the inside of a range's band. The two controls answer
 *   it differently, and it is the only thing they do not share.
 */
internal fun DrawScope.sliderTicks(
    trackLeft: Float,
    trackWidth: Float,
    centreY: Float,
    steps: Int,
    minorTicks: Int,
    widthPx: Float,
    heightPx: Float,
    coveredColour: Color,
    uncoveredColour: Color,
    covered: (x: Float) -> Boolean,
) {
    if (steps <= 0) return
    val everyMinor = minorTicks.coerceAtLeast(0) + 1
    val divisions = (steps + 1) * everyMinor
    val radius = CornerRadius(widthPx / 2f)
    for (i in 0..divisions) {
        val x = trackLeft + trackWidth * i / divisions
        if (i % everyMinor == 0) {
            drawRoundRect(
                color = uncoveredColour,
                topLeft = Offset(x - widthPx / 2f, centreY - heightPx / 2f),
                size = Size(widthPx, heightPx),
                cornerRadius = radius,
            )
        } else {
            drawCircle(
                color = if (covered(x)) coveredColour else uncoveredColour,
                radius = widthPx / 2f,
                center = Offset(x, centreY),
            )
        }
    }
}
