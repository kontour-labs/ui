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
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import kotlin.math.PI
import kotlin.math.pow
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

    // **Measured off the thumb's own width, which is not the same as its resting
    // diameter once it is being held.**
    //
    // It was a fraction of the *drawn* width, which on a thumb already grown
    // under the finger cancels out: at 1.25x grown and 0.16 squashed the
    // arithmetic came back above the resting size, so pushing into a wall could
    // not make the thumb smaller than the circle it is at rest. That was
    // reported on `Switch` and fixed by targeting a fraction off the resting
    // diameter instead — which was right there and wrong here, because a held
    // slider thumb is nothing like its resting diameter. It is `2·r·aspect`:
    // 12dp of radius, 1.25 of press scale and 1.5 of aspect is **45dp wide**,
    // where twice the resting radius is 24. A quarter off the latter is an 18dp
    // target, so a full push took 45dp to 18 — 60% of the thumb, against
    // `Switch`'s 25 for the same gesture and the same constant. Reported as the
    // slider deforming far too much, and it is the reference frame rather than
    // the constant that was wrong.
    //
    // So the target is [ThumbSquash] off the thumb's own **natural** width: what
    // it is drawn at in this frame before the reach stretch, which is `halfWidth`
    // doubled. On a thumb nobody is touching that is exactly the resting
    // diameter, so `Switch` is unaffected and the two now deform by the same
    // fraction of themselves — 45dp to 36 here, 24dp to 19.2 there.
    val width = stretchedRight - stretchedLeft
    val target = width + (halfWidth * 2f * (1f - ThumbSquash) - width) * pull
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
        squircle = true,
    )
    squashedCapsule(
        left = left,
        top = centreY - r,
        right = right,
        bottom = centreY + r,
        colour = fillColour,
        wallOnRight = wallOnRight,
        inset = ringPx,
        squircle = true,
    )
}

/**
 * A capsule while it is wider than tall; an egg once it is not — round on the
 * end that is pressed against something, squashed on the end that is not.
 *
 * ### What it is for
 *
 * Pushing a thumb into a wall it cannot pass squashes it, and the shape of that
 * squash has now been through four answers. Each one fixed the last and was
 * reported back.
 *
 * **Four radii, one per corner.** The cap against the wall kept the resting
 * radius and the trailing corners shrank. *"That is not half a circle, it is a
 * chopped end"* — at the 18dp across and 30dp tall the depth then gave, the
 * trailing radius works out at 3dp against the leading 15dp, and the straight
 * edges between them are what reads as a cut.
 *
 * **One ellipse, both ends.** *"Make it squash more to a vertical ellipse
 * pressed up against the end stop, rather than flattening the end."* An ellipse
 * has no end to chop. But it squashes the end **against the wall** as much as
 * the free one, and a ball pressed into a wall does not go pointy where it is
 * touching.
 *
 * **An egg.** The wall side keeps the cap the thumb rests as and only the free
 * side gives. On a `Switch` that also makes the pressed end *exactly concentric*
 * with the track's end arc, which is what the 2dp of padding is for: the cap's
 * centre and the arc's centre are the same point.
 *
 * **An egg whose curvature is continuous, which is this one.** The first egg
 * matched the cap's curvature *at* the join and then left it immediately: 1.00 →
 * 1.72 → 2.76 → 3.93 over the first few percent of the outline, peaking at 4.80.
 * Curvature-continuous by the letter and a kink to look at — reported as wanting
 * *"more smoothing/rounding between the two halves"*, and then, exactly: *"it
 * needs to be G2 continuous."*
 *
 * ### The ease starts **before** the join
 *
 * That is the whole change, and it is what a G2 join needs in practice rather
 * than in principle. Matching curvature at a point costs nothing if the curvature
 * is allowed to rocket away from it the moment the point is past; what makes a
 * join read as smooth is the curvature *arriving* and *leaving* at a rate the eye
 * cannot catch.
 *
 * So the cap is exact for [SquashEaseStart] of its quarter turn, and from there
 * the outline eases — through the join and on to the free tip — on a
 * **smootherstep**, whose first and second derivatives are zero at both ends. The
 * curvature therefore leaves the cap's own value with zero slope, rises to 3.26
 * rather than 4.80, and settles onto the free ellipse the same way.
 *
 * ```
 * A(φ) = far + (cap - far) · (1 - smootherstep(t)),  t = (φ - φ₀) / (π - φ₀)
 * point = (A(φ)·cos φ, half·sin φ)
 * ```
 *
 * `φ` runs from 0 at the wall tip through the join to π at the free tip, and
 * `φ₀` is where the ease begins. Before `φ₀` the weight is exactly 1 and the
 * outline is exactly the cap's own ellipse — which is what keeps the concentric
 * claim true where it is measured.
 *
 * ### What has to be true whatever the ease
 *
 * `A(φ)·cos φ` has to stay **monotone** across the free side. A weight that holds
 * the cap's radius too long reaches further out at the shoulder than it does at
 * the tip and has to come back, which draws a waist in the free end; at
 * [SquashEaseStart] of 1.0 — an ease beginning exactly at the join — this family
 * does precisely that.
 *
 * And the curvature **has** to rise above the cap's somewhere. Travelling the
 * free side turns the tangent through 90°, and with the curvature pinned to the
 * cap's at the join, a curve whose radius only ever grew would need `far ≥ cap` —
 * no squash at all — while one whose radius only ever shrank could not climb the
 * full half-height. So a squash with a concentric full-radius cap always has a
 * tighter passage in it somewhere. What is ours to choose is where it is, how
 * high it goes, and how gently it is reached.
 *
 * ### [WallCapShare], and why the cap is not always the full radius
 *
 * A cap of the resting radius is as wide as the thumb is **tall**, so a box
 * squashed to less than three halves of that radius would have the cap eat the
 * whole width and leave the free side a flat back — a half moon, which is the
 * chopped end again by another route. So the cap is the resting radius *or*
 * [WallCapShare] of the width, whichever is smaller.
 *
 * **Nothing this library draws reaches that clamp**, and it is the arithmetic that
 * says so rather than the eye: a full squash leaves `1.6·r·aspect` across a `2·r`
 * height, so the clamp would need `1.6·r·aspect < 1.5·r` and the aspect never
 * goes below 1. It stays because this is a primitive that takes a **box** — a
 * caller is free to hand it one narrower than any control here does. Under the
 * older quarter it sat exactly on the limit for a thumb resting as a circle,
 * which was two constants meeting rather than a rule.
 *
 * ### Which branch each control takes
 *
 * The egg is `Switch`'s shape and the capsule is the sliders'. A switch's thumb
 * rests as a circle, so [ThumbSquash] off it is an egg from the first pixel of
 * squash. A held slider's is `SliderDefaults.ThumbAspect` times as wide as it is
 * tall before the squash starts — 45dp against 30 — and a fifth off that leaves
 * 36, so it narrows and stays a round rect. The path below is reached on a slider
 * only where the aspect is under 1.25: the opening frames of a press, and reduced
 * motion, where the thumb never lengthens at all.
 *
 * ### Two primitives where it can, a path where it cannot
 *
 * The capsule branch is still a round rect on Skia's RRect fast path, and the
 * cap's exact quarter turns are still an `arcTo` on its own ellipse. Only the
 * eased part is sampled, from a table of fixed angles with no trigonometry at
 * draw time, and only where a squash has taken the thumb narrower than its own
 * height. At rest and in flight nothing here allocates.
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
    /**
     * Squircle caps rather than semicircles, while the box is at least as wide as
     * it is tall — the slider's thumb. `Switch` keeps the round ones, whose pressed
     * end is concentric with its track's. The egg below is the same either way: it
     * is only reached in the few frames a squash takes the thumb narrower than tall.
     */
    squircle: Boolean = false,
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
        if (squircle) {
            squircleStadium(left + inset, top + inset, right - inset, bottom - inset, colour, style = style)
            return
        }
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
    // `x` is measured toward the wall, so this is the only place the side of the
    // thumb the wall is on enters the arithmetic.
    val toWall = if (wallOnRight) 1f else -1f

    val egg = Path()
    val tip = squashedOutlinePoint(cap, far, half, SquashSteps)
    egg.moveTo(centreX + toWall * tip.x, centreY)
    // Up the free side to where the cap is still exact.
    for (i in SquashSteps - 1 downTo 0) {
        val point = squashedOutlinePoint(cap, far, half, i)
        egg.lineTo(centreX + toWall * point.x, centreY - point.y)
    }
    // And across the wall on the cap's own ellipse, which is a true circle
    // wherever the thumb rests as one.
    egg.arcTo(
        Rect(centreX - cap, centreY - half, centreX + cap, centreY + half),
        if (wallOnRight) -SquashEaseStartDegrees else 180f + SquashEaseStartDegrees,
        if (wallOnRight) SquashEaseStartDegrees * 2f else -SquashEaseStartDegrees * 2f,
        false,
    )
    for (i in 1..SquashSteps) {
        val point = squashedOutlinePoint(cap, far, half, i)
        egg.lineTo(centreX + toWall * point.x, centreY + point.y)
    }
    egg.close()
    drawPath(egg, colour, style = style)
}

/**
 * One sample of the eased part of the outline, measured from the cap's centre.
 *
 * `x` is toward the wall and `y` up, so sample `0` is where the ease begins —
 * still on the cap, above the centre and on the wall's side of it — and
 * [SquashSteps] is the free tip, level with the centre on the other side.
 * Mirrored for the bottom half by the caller, and for the other wall by the sign
 * of `x`.
 *
 * Pulled out of [squashedCapsule] because it is the whole of the shape and none
 * of the drawing: that the outline leaves the cap on the cap's own curvature, and
 * leaves it *gently*, is arithmetic, and is asserted as arithmetic in
 * `SquashedThumbOutlineTest`.
 */
internal fun squashedOutlinePoint(cap: Float, far: Float, half: Float, i: Int): Offset =
    Offset(
        x = (far + (cap - far) * SquashEase[i]) * SquashCos[i],
        y = half * SquashSin[i],
    )

/**
 * The most of a squashed thumb's width the circular cap may take.
 *
 * Two thirds, which is the same as saying the free side never gets less than a
 * third. Below that it has too little depth left to read as a curve at all and
 * the thumb becomes a half moon — the chopped end the ellipse was brought in to
 * remove.
 *
 * It is a ceiling rather than a share, and at [ThumbSquash] nothing reaches it:
 * the narrowest box any control hands [squashedCapsule] is `1.6·r` across a `2·r`
 * height, and two thirds of 1.6 is comfortably above 1. So every squashed thumb
 * in the library keeps a cap of the full resting radius, which is what makes the
 * pressed end concentric with the arc it is pressed into. The clamp stays because
 * this is a drawing primitive that takes a box rather than a control that takes a
 * constant: hand it something narrower and the free side still reads as a curve
 * instead of a flat back.
 */
private const val WallCapShare: Float = 2f / 3f

/**
 * How much of the cap's quarter turn is exactly the cap, before the ease begins.
 *
 * Three fifths, measured. The ease has to start before the join or there is
 * nowhere for the curvature to ramp: beginning it exactly at the join is what the
 * shape did before, and the curvature went 1.00 → 3.93 over the first four
 * percent of the outline. Starting it earlier spreads the same turn over more
 * outline, and the peak falls with it:
 *
 * | ease begins | peak curvature | the cap's own arc, off a true circle |
 * |---|---|---|
 * | at the join | 4.02, and the free side waists | — |
 * | 0.8 of the quarter | 3.50 | 0.0013dp |
 * | **0.6 of the quarter** | **3.26** | **0.013dp** |
 * | 0.4 of the quarter | 3.12 | 0.054dp |
 *
 * Curvature as a multiple of the cap's own. The last column is what it costs: the
 * outline is no longer *exactly* the cap over the eased part, so the wall side
 * departs from a true circle — by thirteen thousandths of a dp at three fifths,
 * which is a fiftieth of a pixel on a phone, and inward, so it takes nothing off
 * the clearance the cap is concentric for. Past three fifths the peak stops
 * falling much and that departure starts growing.
 */
private const val SquashEaseStart: Float = 0.6f

/** [SquashEaseStart] as an arc, which is the form `arcTo` wants. */
private const val SquashEaseStartDegrees: Float = SquashEaseStart * 90f

/**
 * How many straight segments the eased part of the outline is drawn with.
 *
 * It spans from [SquashEaseStart] of the cap's quarter round to the free tip —
 * about 126° — and the samples are fixed angles, so the sine, the cosine and the
 * easing weight are all constants and the draw is multiply-adds with no
 * trigonometry. Thirty-two puts the worst chord about 0.05px from the true curve
 * at the largest size any thumb here is drawn at.
 */
internal const val SquashSteps: Int = 32

private val SquashAngles = FloatArray(SquashSteps + 1) {
    val start = SquashEaseStart * (PI / 2.0)
    (start + (PI - start) * it / SquashSteps).toFloat()
}

private val SquashSin = FloatArray(SquashSteps + 1) { sin(SquashAngles[it].toDouble()).toFloat() }

private val SquashCos = FloatArray(SquashSteps + 1) { cos(SquashAngles[it].toDouble()).toFloat() }

/**
 * `1 - smootherstep(t)` — the weight the cap's own ellipse still carries.
 *
 * A **smootherstep**, `6t⁵ - 15t⁴ + 10t³`, rather than anything cheaper: its
 * first *and* second derivatives vanish at both ends. That is the whole point of
 * it here. The first derivative vanishing is what makes the outline leave the cap
 * without a corner; the second is what makes its **curvature** leave the cap's
 * value with zero slope, which is the difference between a join that is
 * curvature-continuous on paper and one that looks it.
 *
 * Measured across the first tenth of the outline past the top, as a multiple of
 * the cap's own curvature:
 *
 * ```
 * before   1.00  1.72  2.76  3.93  4.71  4.65
 * now      1.16  1.32  1.54  1.83  2.18  2.57
 * ```
 */
private val SquashEase = FloatArray(SquashSteps + 1) {
    val t = it.toFloat() / SquashSteps
    1f - t * t * t * (t * (t * 6f - 15f) + 10f)
}

/**
 * How much narrower than its resting self a thumb gets, pushed all the way into
 * a wall.
 *
 * A fifth, shared with `Switch`'s thumb deliberately rather than coincidentally:
 * a squash is a squash, and controls in one library that deform by visibly
 * different amounts under the same gesture is the class of inconsistency
 * `SliderThumb` exists to remove for the two sliders. Written out in both because
 * they share no other arithmetic.
 *
 * It was a quarter, and on a slider it was not a quarter of anything the reader
 * could see: the target was a fraction off the *resting* diameter while the thumb
 * being squashed was the held capsule, 45dp wide against 24. That put a full push
 * at 18dp — 60% of the thumb gone, against `Switch`'s 25 for the same gesture and
 * the same constant. Reported as the slider deforming far too much. Both halves
 * of that are fixed: the reference frame above, and a fifth here rather than a
 * quarter.
 *
 * ### What a fifth costs, and it is worth knowing
 *
 * A thumb only becomes an **egg** once it is narrower than it is tall, and a held
 * slider thumb is `ThumbAspect` — 1.5 — times as wide as it is tall before the
 * squash starts. So it would have to give up a third of itself to get there, and
 * a third is not the *"only really needs to deform a bit"* this was asked for: at
 * a fifth it lands at 36dp against the 30 it is tall, narrowed and still a
 * capsule. `Switch`'s thumb rests as a circle, so it is an egg from the first
 * pixel of squash. Both give the same fraction of themselves, which is the part
 * that has to be consistent; what that fraction *looks* like is the control's own
 * proportions and not something to correct for.
 */
private const val ThumbSquash: Float = 0.2f

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

/**
 * A stadium whose ends are superellipse halves rather than semicircles: a squircle
 * when it is as wide as it is tall, and a squircle stretched along the middle when
 * it is wider.
 *
 * The slider's head, which was asked for as a squircle — "the head is not a
 * squircle, and it should be". A superellipse, `|x|⁴ + |y|⁴ = 1`, has zero
 * curvature where it meets the straight top and bottom edges, so the join is G2
 * without anything to ease: the curve is already flat when the flat begins.
 *
 * Not `SquircleShape`, for the reason the thumb has always been drawn rather than
 * clipped: it is a different size on every frame of a drag, and a shape's path
 * cache is keyed on its size. The outline here is a fixed table of unit points —
 * no trigonometry at draw time — scaled to the box.
 */
internal fun DrawScope.squircleStadium(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    colour: Color,
    alpha: Float = 1f,
    style: DrawStyle = Fill,
) {
    val half = (bottom - top) / 2f
    if (half <= 0f || right - left < half * 2f) return
    val centreY = (top + bottom) / 2f
    val leftCentre = left + half
    val rightCentre = right - half
    val last = SquircleQuarterX.lastIndex
    val path = Path()
    path.moveTo(leftCentre, top)
    path.lineTo(rightCentre, top)
    // Top-right quarter, from the top join down to the right-hand tip…
    for (i in 1..last) path.lineTo(rightCentre + half * SquircleQuarterX[i], centreY - half * SquircleQuarterY[i])
    // …and bottom-right, from the tip back to the bottom join.
    for (i in last - 1 downTo 0) path.lineTo(rightCentre + half * SquircleQuarterX[i], centreY + half * SquircleQuarterY[i])
    path.lineTo(leftCentre, bottom)
    for (i in 1..last) path.lineTo(leftCentre - half * SquircleQuarterX[i], centreY + half * SquircleQuarterY[i])
    for (i in last - 1 downTo 0) path.lineTo(leftCentre - half * SquircleQuarterX[i], centreY - half * SquircleQuarterY[i])
    path.close()
    drawPath(path, colour, alpha = alpha, style = style)
}

/**
 * The slider's value, in a bubble above its head.
 *
 * Asked for: "the option to display a label above the head as you're dragging it".
 * Drawn in the slider's own draw pass rather than composed, because it follows the
 * thumb every frame of a drag and a composed label would be a recomposition a
 * frame; and outside the slider's bounds, which a draw can do and a layout cannot,
 * so the control's size and hit area are the same with or without it. A parent
 * that clips — a card, the top of a scroller — will cut it, and the docs say so.
 *
 * [progress] fades it and, unless [scaleIn] is off, grows it out of the head it
 * belongs to. Clamped to the control's width, so a thumb at either end still has
 * its whole label.
 */
internal fun DrawScope.sliderValueLabel(
    text: TextLayoutResult,
    centreX: Float,
    thumbTop: Float,
    progress: Float,
    scaleIn: Boolean,
    container: Color,
    paddingHorizontal: Float,
    paddingVertical: Float,
    gap: Float,
) {
    if (progress <= 0f) return
    val height = text.size.height + paddingVertical * 2f
    val width = maxOf(text.size.width + paddingHorizontal * 2f, height)
    val left = (centreX - width / 2f).coerceIn(0f, (size.width - width).coerceAtLeast(0f))
    val bottom = thumbTop - gap
    val top = bottom - height
    val pivot = Offset(centreX.coerceIn(left, left + width), bottom)
    val shown = progress.coerceIn(0f, 1f)
    withTransform({ if (scaleIn) scale(shown, shown, pivot) }) {
        squircleStadium(left, top, left + width, bottom, container, alpha = shown)
        drawText(
            text,
            topLeft = Offset(left + (width - text.size.width) / 2f, top + paddingVertical),
            alpha = shown,
        )
    }
}

/**
 * One quarter of a unit superellipse (`n = 4`), from the top (0, 1) round to the
 * right-hand tip (1, 0), as `x` and `y` magnitudes.
 *
 * Sampled by `x` over the half nearer the top and by `y` over the half nearer the
 * tip, so the points are even along the curve: parametrised by angle, a
 * superellipse bunches its samples where it is flattest and leaves the corner
 * coarse.
 */
private val SquircleQuarter: Pair<FloatArray, FloatArray> = run {
    // Where the curve crosses the diagonal, x = y = 2^(-1/4).
    val diagonal = 2.0.pow(-0.25)
    val steps = SquircleHalfSteps
    val xs = FloatArray(steps * 2 + 1)
    val ys = FloatArray(steps * 2 + 1)
    for (i in 0..steps) {
        val x = diagonal * i / steps
        xs[i] = x.toFloat()
        ys[i] = (1.0 - x.pow(4)).pow(0.25).toFloat()
    }
    for (i in 1..steps) {
        val y = diagonal * (steps - i) / steps
        xs[steps + i] = (1.0 - y.pow(4)).pow(0.25).toFloat()
        ys[steps + i] = y.toFloat()
    }
    xs to ys
}

private val SquircleQuarterX: FloatArray = SquircleQuarter.first
private val SquircleQuarterY: FloatArray = SquircleQuarter.second

/** Samples in each half of a quarter: plenty at a thumb's size, and fixed. */
private const val SquircleHalfSteps = 12
