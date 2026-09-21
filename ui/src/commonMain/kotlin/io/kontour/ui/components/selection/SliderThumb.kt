package io.kontour.ui.components.selection

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

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

    // Round on the end against the wall and squashed on the other, which needs
    // to know which end that is — the pinning above decides where the thumb is,
    // this decides what it looks like. See [squashedCapsule].
    val wallOnRight = squashPx > 0f
    // **The ring is the fill's own outline, stroked** — one shape drawn twice
    // rather than two shapes, so the halo is a true offset curve and an even
    // width the whole way round. Stroking is centred, so a stroke of twice the
    // ring on a shape inset by the ring lands its outer edge exactly on the
    // thumb's box, and its inner half is painted over by the fill.
    //
    // Two nested shapes is what this used to be, and on a squashed thumb it does
    // not hold: the inner egg's proportions are not the outer's, so the gap ran
    // 1.59dp at the shoulders against 2.00 at the four extremes. An ellipse hid
    // that — it came out 1.92 to 2.00 — which is why it was only worth a
    // sentence before.
    squashedCapsule(
        left = left,
        top = centreY - r,
        right = right,
        bottom = centreY + r,
        colour = ringColour,
        wallOnRight = wallOnRight,
        inset = ringPx,
        style = Stroke(width = ringPx * 2f),
    )
    squashedCapsule(
        left = left,
        top = centreY - r,
        right = right,
        bottom = centreY + r,
        colour = fillColour,
        wallOnRight = wallOnRight,
        inset = ringPx,
    )
}

/**
 * A capsule while it is wider than tall; an egg once it is not — round on the
 * end that is pressed against something, squashed on the end that is not.
 *
 * ### What it is for
 *
 * Pushing a thumb into a wall it cannot pass squashes it, and the shape of that
 * squash has now been through three answers. Each one fixed the last and was
 * reported back.
 *
 * **Four radii, one per corner.** The cap against the wall kept the resting
 * radius and the trailing corners shrank. *"That is not half a circle, it is a
 * chopped end"* — at 18dp across and 30dp tall the trailing radius works out at
 * 3dp against the leading 15dp, and the straight edges between them are what
 * reads as a cut.
 *
 * **One ellipse, both ends.** *"Make it squash more to a vertical ellipse
 * pressed up against the end stop, rather than flattening the end."* An ellipse
 * has no end to chop, and it is the older and better answer to what a squash
 * looks like. But it squashes the end **against the wall** as much as the free
 * one, and a ball pressed into a wall does not go pointy where it is touching:
 * *"we want to keep the side of the head that's pressed up against the edge
 * circular, but we want to squash the other side in a bit."*
 *
 * **An egg, which is the balance between the two.** The wall side is a circular
 * cap of the thumb's own resting radius — so it keeps exactly the silhouette it
 * had before the finger arrived — and the free side eases in to a shallower
 * ellipse. On a `Switch` that also makes the pressed end *exactly concentric*
 * with the track's end arc, which is what the 2dp of padding is for: the cap's
 * centre and the arc's centre are the same point.
 *
 * ### The join is smooth, and that is the whole difficulty
 *
 * A semicircle and a half ellipse butted together on the vertical centre share a
 * tangent but not a curvature: at a full squash the outline's radius of
 * curvature steps from `r` to `r/4` in one pixel, and that step is visible as a
 * kink at the widest row. Two conics cannot do better — matching curvature at
 * the join *is* the circle.
 *
 * So the free half is not an ellipse but a family of them, swept:
 *
 * ```
 * reach(θ) = (far + (cap - far) · (1 - sin θ)⁶) · sin θ
 * ```
 *
 * At the join it is exactly the cap's own ellipse, so the curvature is continuous
 * by construction; at the tip the weight has decayed to nothing and it is exactly
 * the shallow ellipse, so the free side really is the ellipse rather than
 * something on its way to one. The exponent is where the transition is spent —
 * see [SquashEase], which is the difference between a shape with a shoulder and a
 * shape without one.
 *
 * ### What has to be true whatever the exponent
 *
 * `reach` has to stay monotone. A weighting that holds the cap's radius for
 * longer still — a smoothstep, `(1 - sin²θ)` — reaches further out at the
 * shoulder than it does at the tip and has to come back, which draws a waist in
 * the free end.
 *
 * And the curvature **has** to rise above the cap's somewhere. Travelling the
 * free side turns the tangent through 90°, and with `ρ(0) = cap` a curve whose
 * radius only ever grew would need `far ≥ cap` — no squash at all — while one
 * whose radius only ever shrank could not climb the full half-height. So a squash
 * with a concentric full-radius cap always has a tighter passage in it somewhere.
 * What is ours to choose is where, and how much of the outline it takes.
 *
 * ### [WallCapShare], and why the cap is not always the full radius
 *
 * A cap of the resting radius is as wide as the thumb is **tall**, so on a thumb
 * squashed narrower than its own height it can eat the whole width and leave the
 * free side a flat back — a half moon, which is the chopped end again by another
 * route. A held slider thumb is 18dp across and 30dp tall at a full squash, and
 * an unclamped cap would take 15 of the 18.
 *
 * So the cap is the resting radius *or* [WallCapShare] of the width, whichever is
 * smaller. A thumb that rests as a **circle** never reaches that clamp — a
 * quarter off a circle is exactly the limit — so `Switch` keeps a truly circular
 * cap at every depth, and only a long capsule ever gives any of it back.
 *
 * ### Two primitives where it can, a path where it cannot
 *
 * The capsule branch is still a round rect on Skia's RRect fast path. The egg
 * cannot be: it is a swept family, not a conic, so it is a path — built from a
 * table of fixed angles with no trigonometry at draw time, and built **only while
 * a finger is pressed into an end stop**, which is the one moment a thumb is not
 * a capsule. At rest and in flight nothing here allocates.
 *
 * ### The ring
 *
 * `sliderThumb` draws this twice for its halo — once stroked, once filled, both
 * on the *same* inset shape. A ring drawn as a second, larger egg is not an even
 * width: the two eggs have different proportions, so the gap measures 1.59dp at
 * the shoulders against 2.00 at the four extremes. Stroking one outline is a true
 * offset curve and is exactly even, and it keeps the wall side circular either
 * way — the offset of a circular arc is a circular arc.
 *
 * [inset] steps every dimension in together and the cap is measured on the
 * *outer* box, so the cap's centre does not move and the two draws cannot
 * disagree about which branch they are in.
 *
 * @param wallOnRight Which end is against the wall, and therefore which end keeps
 *   its circle. The *pinning* — holding that edge still while the other comes in
 *   — is the caller's, and is what makes the thumb press rather than shrink.
 * @param inset Draws the same shape stepped in by this much on all four sides.
 */
internal fun DrawScope.squashedCapsule(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    colour: Color,
    wallOnRight: Boolean = true,
    inset: Float = 0f,
    style: DrawStyle = Fill,
) {
    val outerHeight = bottom - top
    val outerWidth = right - left
    if (outerHeight <= 0f || outerWidth <= 0f) return

    val restingHalf = outerHeight / 2f
    val half = restingHalf - inset
    if (half <= 0f || outerWidth - inset * 2f <= 0f) return

    // Decided on the **outer** box, so the ring and the fill are never two
    // different shapes: at the crossover an inset box can be narrower than it is
    // tall while the box around it is not.
    if (outerWidth / 2f >= restingHalf) {
        // Half the height, so the ends are full semicircles — a capsule, and a
        // circle at the moment the two are equal.
        drawRoundRect(
            color = colour,
            topLeft = Offset(left + inset, top + inset),
            size = Size(outerWidth - inset * 2f, outerHeight - inset * 2f),
            cornerRadius = CornerRadius(half),
            style = style,
        )
        return
    }

    val wallCap = minOf(restingHalf, outerWidth * WallCapShare)
    val cap = wallCap - inset
    val far = (outerWidth - wallCap - inset).coerceAtLeast(0f)
    if (cap <= 0f) return

    // Invariant under [inset]: the wall steps in by it and the cap gives it up.
    val centreX = if (wallOnRight) right - wallCap else left + wallCap
    val centreY = (top + bottom) / 2f
    // Away from the wall, which is the only direction anything is squashed in.
    val away = if (wallOnRight) -1f else 1f

    val egg = Path()
    egg.moveTo(centreX, centreY - half)
    for (i in 1..SquashSteps) {
        val point = squashedOutlinePoint(cap, far, half, i)
        egg.lineTo(centreX + away * point.x, centreY - point.y)
    }
    for (i in SquashSteps - 1 downTo 0) {
        val point = squashedOutlinePoint(cap, far, half, i)
        egg.lineTo(centreX + away * point.x, centreY + point.y)
    }
    // And back up the wall side, which is the cap's own ellipse exactly.
    egg.arcTo(
        Rect(centreX - cap, centreY - half, centreX + cap, centreY + half),
        90f,
        if (wallOnRight) -180f else 180f,
        false,
    )
    egg.close()
    drawPath(egg, colour, style = style)
}

/**
 * One sample of the free half's outline, measured from the cap's centre.
 *
 * `x` is how far it reaches away from the wall, `y` how far above the centre —
 * so sample `0` is the join at the top of the shape and [SquashSteps] is the tip,
 * level with the centre. Mirrored for the bottom half by the caller, and for the
 * other wall by the sign of `x`.
 *
 * Pulled out of [squashedCapsule] because it is the whole of the shape and none
 * of the drawing: that the free side leaves the join on the cap's own radius is
 * arithmetic, and is asserted as arithmetic in `SquashedThumbOutlineTest`.
 */
internal fun squashedOutlinePoint(cap: Float, far: Float, half: Float, i: Int): Offset =
    Offset(
        x = (far + (cap - far) * SquashEase[i]) * SquashSin[i],
        y = half * SquashCos[i],
    )

/**
 * The most of a squashed thumb's width the circular cap may take.
 *
 * Two thirds, which is the same as saying the free side never gets less than a
 * third. Below that it has too little depth left to read as a curve at all and
 * the thumb becomes a half moon — the chopped end the ellipse was brought in to
 * remove.
 *
 * It is a ceiling rather than a share: for a thumb that rests as a **circle** the
 * cap is the resting radius at every depth this library squashes to, because a
 * [ThumbSquash] off a circle lands exactly on the limit and never past it. Only a
 * thumb that rests as a long capsule — a held slider's — ever gives any of its
 * cap back, and then only over the last quarter of the pull.
 */
private const val WallCapShare: Float = 2f / 3f

/**
 * How many straight segments the free half is drawn with, per quarter.
 *
 * The outline is a swept family of ellipses rather than a conic, so it is
 * sampled. Twenty puts the worst chord about 0.09px from the true curve at the
 * largest size any thumb here is drawn at, which is a quarter of what
 * antialiasing is already doing to the edge.
 *
 * The samples are fixed angles, so the sine, the cosine and the easing weight are
 * all constants — the draw is twenty multiply-adds and no trigonometry.
 */
internal const val SquashSteps: Int = 20

private val SquashSin = FloatArray(SquashSteps + 1) {
    sin(it * (PI / 2.0) / SquashSteps).toFloat()
}

private val SquashCos = FloatArray(SquashSteps + 1) {
    cos(it * (PI / 2.0) / SquashSteps).toFloat()
}

/**
 * `(1 - sin θ)⁶` — the weight the cap's own ellipse still carries at each sample.
 *
 * **The exponent decides where the transition is spent**, and that is the whole
 * look of the thing. A low one drags the cap's radius a long way round before
 * giving it up, and the curve then has to turn hard to reach the tip — the free
 * side comes out with a shoulder and a flat back, which is what *"the new one
 * just feels off"* was. A high one is the free side's ellipse almost everywhere
 * and spends the transition in the first dp off the join, which is what *"a
 * concentric circular cap blending into an ellipse"* asks for.
 *
 * Six, chosen by rendering the family: at two the shoulder is plain at any size,
 * and past about eight the silhouette stops changing because it has converged on
 * the ellipse. Measured on the switch at a full squash, the free side leaves the
 * join on 7.81dp of the cap's 12.00 — a plain ellipse butted on leaves on 3.01.
 */
private val SquashEase = FloatArray(SquashSteps + 1) {
    val s = 1f - SquashSin[it]
    val cube = s * s * s
    cube * cube
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
