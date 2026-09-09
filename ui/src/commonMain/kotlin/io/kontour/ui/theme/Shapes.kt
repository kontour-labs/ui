package io.kontour.ui.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.min

/**
 * Where a height-derived corner stops growing: 18dp.
 *
 * Not a tuned number. It is half [Sizing.controlHeightSmall], and that is what
 * makes the rule one rule instead of two competing ones:
 *
 * * at `small` and below, half the height is *under* this, so the corner is
 *   exactly half the height and the control is a pill;
 * * at 36dp precisely the two readings give the same 18, so there is no step at
 *   the join;
 * * above it the corner stops and the control gets progressively squarer — a
 *   52dp field goes from 26dp to 18, a 60dp XL button from 30 to 18.
 *
 * A capsule at every height was the previous rule, and it was right about the
 * bottom of the scale and wrong about the top: at 14dp an `XSmall` button was
 * nearly a pill already and an `XLarge` was nearly square, which is what the
 * proportional corner fixed — but a 60dp button at 30dp is not a considered
 * radius, it is a stadium. The cap keeps the first half and drops the second.
 *
 * [Shapes.pill] is deliberately *not* capped, because it is for the things that
 * are round from what they are rather than from how tall they are: an avatar, a
 * day cell, an icon button. See [Shapes].
 */
val CapsuleCap: Dp = 18.dp

/**
 * The corner-radius scale.
 *
 * **One step, all the way up.** Every rung is 6dp above the one below it, and
 * that regularity is the point rather than a tidiness: the concentricity rule —
 * an inner radius is its container's radius minus the gap between them — only
 * holds if the scale it steps through is even. Use [inset] rather than picking
 * the next token down by eye.
 *
 * | Token | Radius | Used by |
 * |---|---|---|
 * | [extraSmall] | 10dp | Badges, tags, inline code |
 * | [small] | 16dp | Small containers, swatches |
 * | [medium] | 22dp | Cards, list groups, menus |
 * | [large] | 28dp | Dialogs, large cards |
 * | [extraLarge] | 34dp | Sheets, hero panels |
 * | [control] | half its height, up to [CapsuleCap] | Buttons, chips, switches |
 * | [field] | half its height, up to [CapsuleCap] | Text fields, selects, time fields |
 * | [pill] | fully round | Avatars, scrollbars, swatches, day cells, FABs |
 * | [sheet] | 34dp top only | Bottom sheets |
 * | [sideSheet] | 34dp leading only | Side sheets |
 *
 * ### The numbers line up on purpose
 *
 * `22` used to be justified as half the medium control height, so that a medium
 * button came out exactly a capsule. That reading is gone: a 44dp button stops at
 * [CapsuleCap] and lands on 18. The rung is still 22, and the relationship it now
 * carries is the more useful one — **22 minus 18 is 4**, which is `spacing.xxs`.
 * A standard control sitting in a standard container with one unit of padding
 * round it is concentric by construction, without either of them naming a radius.
 *
 * The rest of the ladder works the same way one step up: `panel` at 28 holds a
 * `container` at 22 with 6dp of ring, and `extraLarge` at 34 holds a `panel` at
 * 28 with the same. The even 6dp step is the mechanism [inset] walks.
 *
 * [control] and [field] do not take a rung at all — they take
 * [CapsuleCornerSize], half the shorter side up to [CapsuleCap]. That is the only
 * way a family stays consistent across its *own* size scale, and it is why a 44dp
 * switch and a 44dp button agree without either of them naming a number.
 *
 * ### One kind of corner
 *
 * Every rung is a [SquircleShape] — curvature eased in and out rather than a
 * quarter circle bolted between two straight edges. It reads as softer at the
 * same nominal radius, and it is what makes a surface look drawn rather than
 * clipped.
 *
 * The small rungs used to be circular on the grounds that the smoothing is not
 * visible below about 12dp and a generic path costs more to clip, border and
 * shadow than a rounded rectangle. True on both counts, and still the wrong
 * trade: G2 continuity that stops partway up the scale is a discontinuity in the
 * *scale*, which is more visible than the one it was avoiding — a badge on a card
 * had a corner from a different design system to the card. The cost is bounded by
 * [SquircleShape]'s path cache.
 *
 * [pill] survives for the things that are round because of what they *are* rather
 * than because of how tall they happen to be: an avatar, a scrollbar thumb, a
 * status dot, the ring round a radio button, a colour swatch, an icon button, a
 * day cell. Everything else that reads as a lozenge — a chip, a toast, a nav
 * indicator, a skeleton line — is [capsule], which is the same silhouette with
 * the family's own curvature.
 *
 * ### Naming one costs nothing to look at, and decides what the cap does
 *
 * On a *square* box the two are the same picture. [capsule] resolves to half the
 * shorter side, which on a square leaves no straight edge for the smoothing to
 * ease into, so the squircle collapses onto the circle [pill] draws — they differ
 * only by the cubic path's approximation of an arc, which is a fraction of a
 * pixel at the rim and nothing at all in the middle. Swapping every square
 * [capsule] in the library to [pill] moved 29 goldens and not one of them by more
 * than a one-pixel rim.
 *
 * So the choice is not about today's render. It is about [CapsuleCornerSize]'s
 * cap: a capped [capsule] on a 50dp box is an 18dp rounded square, and a [pill]
 * on the same box is still a circle. A day cell is the case that makes this
 * concrete — its fill, its "today" ring and its range caps have to agree with
 * each other, and they only do if none of them is capped.
 *
 * A selection indicator used to be on that list and is not any more. A 56x32
 * travelling pill is not a circle; it is a lozenge behind a row of controls that
 * are squircles, which is exactly the mismatch this token pair exists to name.
 *
 * ### Ask for what a thing *is*
 *
 * [control], [field], [container] and [panel] are the four names components
 * actually use. They are the reason two buttons cannot disagree: there is one
 * place that says what a button's corner is, and every button reads it.
 *
 * Reaching past them to a rung of the size scale is for genuine one-offs — an
 * avatar, a scrollbar, a skeleton line — where the shape is a property of that
 * one thing rather than of a family. When a component reaches for `small`
 * because it happens to be the right number today, it stops tracking the family
 * it belongs to, and that is exactly how a design system drifts.
 *
 * They are also the seam a consumer wants. Overriding [pill] to square off
 * buttons would not even work any more — a button reads [control] — and
 * overriding [control] moves the buttons and leaves avatars and scrollbars
 * alone.
 *
 * ### One number for sheets
 *
 * [sheet] and [sideSheet] are [extraLarge] — 34dp — with two corners zeroed, derived
 * rather than restated. A panel against the edge of the window should be square
 * where it meets that edge — a rounded corner there leaves a sliver of
 * background showing through — but it should be *the same radius* as a hero
 * panel on the side that faces the content, and for a while it silently was not.
 */
@Immutable
data class Shapes(
    val extraSmall: CornerBasedShape = SquircleShape(10.dp),
    val small: CornerBasedShape = SquircleShape(16.dp),
    val medium: CornerBasedShape = SquircleShape(22.dp),
    val large: CornerBasedShape = SquircleShape(28.dp),
    val extraLarge: CornerBasedShape = SquircleShape(34.dp),
    val pill: CornerBasedShape = RoundedCornerShape(percent = 50),

    /**
     * A capsule, drawn as a squircle. The shape [pill] should have been.
     *
     * Half the shorter side, so it is a capsule at any size — and a *circle* on
     * a square box, in the same sense [pill] is, because a corner with no room
     * on either edge has nothing to smooth. See [SquircleShape]'s `params`.
     *
     * The distinction between the two is worth stating because it is the whole
     * of this token. [pill] is a true circular arc, and it is right for the
     * things that genuinely are circles — an avatar, a status dot, the ring round
     * a radio button, a scrollbar thumb. Everything *else* that was reaching for
     * `pill` wanted a capsule, and a capsule with circular ends beside a family
     * of squircles is the mismatch that makes nesting look wrong: two curves that
     * meet at a tangent but disagree about everything after it.
     *
     * [control] is this, named for what presses it. This one is named for the
     * shape, because a skeleton's line, a toast and a sheet's grab bar are
     * capsules that nobody presses.
     */
    val capsule: CornerBasedShape = SquircleShape(CapsuleCornerSize(cap = CapsuleCap)),

    /**
     * Anything you press, and anything that labels a thing you could press.
     *
     * Buttons, icon buttons, split buttons, button groups, chips, tags, floating
     * actions, and the toolbar that holds them. Half its own height up to
     * [CapsuleCap], so a row of mixed actions has one corner regardless of what
     * each one's height happens to be — which is the thing a fixed radius cannot
     * do: at 14dp an `XSmall` button was nearly a capsule already and an `XLarge`
     * was nearly square, so one component disagreed with itself across its own
     * size scale. The cap stops the same disagreement happening at the other end;
     * see [CapsuleCap] for why 18dp is the join rather than a tuned value.
     *
     * A container that wraps controls can no longer share this rule and be
     * concentric for free — two 18dp corners with 6dp between them pinch at the
     * corners rather than tracking. It has to derive its own from its children's
     * with [outset], the way `ToolbarDefaults.Shape` does.
     */
    val control: CornerBasedShape = capsule,

    /**
     * Anything that holds a value the user typed or chose.
     *
     * Text fields, selects, the segmented control's track, a time field.
     *
     * The same rule as [control], and it used to be a fixed 14dp on the argument
     * that a capsule reads as something to press rather than something to fill
     * in. The argument was half right. A single-line field *is* a control by
     * every other measure — same height, same row, same press target — and
     * giving it a different corner from the button beside it was the
     * inconsistency, not the fix.
     *
     * What the old reasoning was actually protecting against is a multi-line
     * field: a text area shaped like a lozenge is nobody's idea of a text area.
     * [CapsuleCornerSize]'s cap handles that directly, so the box stays a box
     * without the single-line case having to pay for it.
     *
     * The cap used to be 26dp — half [Sizing.controlHeightLarge], the height a
     * text field's `minHeight` resolves to — chosen as the narrowest line that
     * still left a single-line field on a capsule. It is [CapsuleCap] now, the
     * same 18dp every other height-derived corner stops at, so a field and the
     * button beside it agree at every height rather than only below 52dp.
     *
     * The multi-line case the old cap was protecting is protected harder, not
     * less: a text area was a 26dp lozenge and is now an 18dp box.
     */
    val field: CornerBasedShape = SquircleShape(CapsuleCornerSize(cap = CapsuleCap)),

    /**
     * Anything that holds other components.
     *
     * Cards, list rows, accordions, menus, popovers, the rich tooltip, a
     * coachmark's bubble. One rung above [field] so a control inside a container
     * is visibly inside it.
     */
    val container: CornerBasedShape = medium,

    /**
     * A modal panel that owns the screen's attention.
     *
     * Dialogs, the command palette, the expanded search. Above [container]
     * because it is not sitting in the page, it is in front of it.
     */
    val panel: CornerBasedShape = large,

    /**
     * Bottom sheets. Rounded at the top only — the bottom edge sits against the
     * bottom of the window, and rounding a corner that is off-screen just
     * leaves a sliver of background showing through at full expansion.
     */
    val sheet: CornerBasedShape = extraLarge.topCornersOnly(),

    /**
     * Side sheets and rails, as they appear on the *trailing* edge: rounded on
     * the side facing the content, square against the window edge. Mirror it
     * with [mirrorHorizontally] for a leading-edge sheet.
     */
    val sideSheet: CornerBasedShape = extraLarge.leadingCornersOnly(),
)

/**
 * The same scale, from a different starting rung, in a different step.
 *
 * [Shapes]'s own defaults are a ladder — 10, 16, 22, 28, 34 — and five derived
 * tokens that point at rungs of it. Changing the ladder therefore meant
 * restating all thirteen fields, because a `data class` default cannot be
 * expressed in terms of an argument the caller supplied. That is not a
 * hypothetical: the GTurbo demo theme wanted 6dp in steps of 2 and spent
 * thirteen lines saying so, twelve of which repeated the library's own policy
 * back to it.
 *
 * ```
 * KontourTheme(shapes = kontourShapes(extraSmall = 6.dp, step = 2.dp, capsuleCap = 10.dp)) {
 *     AppRoot()
 * }
 * ```
 *
 * `kontourShapes()` with no arguments is `Shapes()`, and `ShapeScaleTest` asserts
 * exactly that. It reads as a tautology and is the whole proof: the factory is
 * only worth having if it reproduces the defaults it is meant to replace, and
 * every argument below moves one number in one place rather than opening a
 * second way to describe the same scale.
 *
 * ### What it does not take
 *
 * [Shapes.control], [Shapes.field], [Shapes.container] and [Shapes.panel] — the
 * four the shape KDoc calls "the seam a consumer wants" — are policy, not
 * geometry: which rung a pressable thing lands on is a decision about the
 * design, and a brand that disagrees is not adjusting a scale but replacing a
 * mapping. `copy` already says that clearly:
 *
 * ```
 * kontourShapes(extraSmall = 6.dp, step = 2.dp).let { it.copy(control = it.small, field = it.small) }
 * ```
 *
 * which is GTurbo, whose buttons are small rounded rectangles rather than
 * capsules. Two lines instead of thirteen, and the one line that differs from
 * the library is the one a reader should be looking at.
 *
 * @param smoothing Applied to every squircle in the scale, and the reason it is
 *   an argument here rather than a [Shapes] field. [SquircleShape.DefaultSmoothing]
 *   explains why a scale must not mix two smoothings; this is how a consumer
 *   changes it without being able to mix them. `0f` is a plain rounded rectangle
 *   throughout, which is the one-line answer to "turn the continuous corners off".
 * @param capsuleCap The ceiling on [Shapes.capsule], [Shapes.control] and
 *   [Shapes.field] — see [CapsuleCap] for why the default is 18dp. A brand
 *   compressing the ladder almost always wants this smaller too, and forgetting
 *   it is what leaves a small-cornered design with capsule buttons.
 */
fun kontourShapes(
    extraSmall: Dp = 10.dp,
    step: Dp = 6.dp,
    smoothing: Float = SquircleShape.DefaultSmoothing,
    capsuleCap: Dp = CapsuleCap,
): Shapes {
    val xs = SquircleShape(extraSmall, smoothing)
    val sm = SquircleShape(extraSmall + step, smoothing)
    val md = SquircleShape(extraSmall + step * 2, smoothing)
    val lg = SquircleShape(extraSmall + step * 3, smoothing)
    val xl = SquircleShape(extraSmall + step * 4, smoothing)
    val capsule = SquircleShape(CapsuleCornerSize(cap = capsuleCap), smoothing)
    return Shapes(
        extraSmall = xs,
        small = sm,
        medium = md,
        large = lg,
        extraLarge = xl,
        // Not a squircle and not affected by [smoothing]: a true circular arc is
        // what this token *is*, and the shapes that reach for it — an avatar, a
        // status dot, a radio ring — are circles rather than rounded boxes.
        pill = RoundedCornerShape(percent = 50),
        capsule = capsule,
        control = capsule,
        field = SquircleShape(CapsuleCornerSize(cap = capsuleCap), smoothing),
        container = md,
        panel = lg,
        sheet = xl.topCornersOnly(),
        sideSheet = xl.leadingCornersOnly(),
    )
}

/**
 * Half the shorter side, up to [cap]. What keeps a family's corner consistent
 * across its own size scale.
 *
 * A fixed radius cannot keep a family consistent across its own size scale: at
 * 14dp an `XSmall` button was nearly a capsule already and an `XLarge` was nearly
 * square. A 50% corner fixes that but has the opposite failure — it has no idea
 * how tall the thing is, so a multi-line text area becomes a lozenge.
 *
 * This is the rule that satisfies both. Anything up to [cap] × 2 tall is exactly
 * a capsule, so **two components of the same height agree by construction** — a
 * 44dp button and a 44dp switch have the same corner without either of them
 * naming a number. Anything taller stops growing and stays a box.
 *
 * @param cap The radius to stop growing at. Uncapped by default, which is now the
 *   *unusual* choice rather than the normal one: [Shapes.capsule],
 *   [Shapes.control] and [Shapes.field] all pass [CapsuleCap]. Leave it uncapped
 *   for something round from what it is rather than from how tall it is — and
 *   where that is the whole reason, prefer [Shapes.pill], which says so.
 */
@Immutable
data class CapsuleCornerSize(val cap: Dp = Dp.Infinity) : CornerSize {
    override fun toPx(shapeSize: Size, density: Density): Float =
        min(shapeSize.minDimension / 2f, with(density) { cap.toPx() })
}

/**
 * A corner part-way between two others.
 *
 * For a shape that has to *travel* between two resolutions rather than switch
 * between them — an expanding list header whose bottom corners flatten as it
 * opens, a card that squares off as it docks. Interpolating the resolved
 * pixels rather than the [CornerSize]s is what lets the two ends be different
 * kinds: a percentage on one side and a fixed radius on the other still meet in
 * the middle.
 *
 * Deferred for the same reason [inset] is: a percentage has no value until there
 * is a size to take it of.
 */
@Immutable
data class LerpCornerSize(
    val from: CornerSize,
    val to: CornerSize,
    val fraction: Float,
) : CornerSize {
    override fun toPx(shapeSize: Size, density: Density): Float {
        val a = from.toPx(shapeSize, density)
        return a + (to.toPx(shapeSize, density) - a) * fraction
    }
}

/** Every corner of this shape, [fraction] of the way to [other]'s. */
fun CornerBasedShape.lerpCorners(other: CornerBasedShape, fraction: Float): CornerBasedShape = copy(
    topStart = LerpCornerSize(topStart, other.topStart, fraction),
    topEnd = LerpCornerSize(topEnd, other.topEnd, fraction),
    bottomEnd = LerpCornerSize(bottomEnd, other.bottomEnd, fraction),
    bottomStart = LerpCornerSize(bottomStart, other.bottomStart, fraction),
)

/**
 * Swaps a shape's leading and trailing corners.
 *
 * For a panel that can appear on either edge. The rounded side should always be
 * the one facing the content — a rounded corner against the window edge leaves a
 * sliver of background showing through, and a square corner facing the content
 * makes the panel look welded on.
 */
fun CornerBasedShape.mirrorHorizontally(): CornerBasedShape = copy(
    topStart = topEnd,
    topEnd = topStart,
    bottomStart = bottomEnd,
    bottomEnd = bottomStart,
)

/** Keeps the top two corners and squares off the bottom two. */
fun CornerBasedShape.topCornersOnly(): CornerBasedShape = copy(
    bottomEnd = ZeroCornerSize,
    bottomStart = ZeroCornerSize,
)

/** Keeps the two corners on the leading edge and squares off the trailing pair. */
fun CornerBasedShape.leadingCornersOnly(): CornerBasedShape = copy(
    topEnd = ZeroCornerSize,
    bottomEnd = ZeroCornerSize,
)

/**
 * The radius something nested [gap] inside this shape should use to stay
 * concentric with it.
 *
 * Two rounded rectangles are concentric when the inner radius is the outer
 * radius minus the space between them; get it wrong and the gap visibly widens
 * or pinches around the corner even though it is even along every straight edge.
 * A button inside a toolbar, a segment inside a segmented control, a focus ring
 * outside a field — all the same rule, and all of them used to restate it by
 * hand against a token picked by eye.
 *
 * Floors at zero, so an inset larger than the radius gives a square corner
 * rather than an inverted one, and preserves the shape it is called on — inset a
 * squircle and you get a squircle.
 */
fun CornerBasedShape.inset(gap: Dp): CornerBasedShape = copy(
    topStart = InsetCornerSize(topStart, gap),
    topEnd = InsetCornerSize(topEnd, gap),
    bottomEnd = InsetCornerSize(bottomEnd, gap),
    bottomStart = InsetCornerSize(bottomStart, gap),
)

/**
 * A [CornerSize] that resolves to another one, less [gap].
 *
 * It has to defer rather than subtract up front: a [CornerSize] can be a
 * percentage, and a percentage of what is not known until there is a size and a
 * density to resolve it against.
 *
 * ### Resolved against the outer box, which it has to reconstruct
 *
 * The exact mirror of [OutsetCornerSize], and it was not always. This used to
 * resolve the base against the size it was handed — the *inner* box — on the
 * argument that "the inner shape is drawn on the inner box, so the size the
 * corner should resolve against is the size it is given". That is the wrong
 * question. The base belongs to the **outer** shape; what is being asked is what
 * the outer shape's corner *is*, and then what a box [gap] inside it should use.
 * Resolve the outer shape's spec on the inner box and a proportional corner has
 * already lost the gap once before the subtraction takes it again.
 *
 * A fixed radius is immune — 22dp is 22dp on any box — which is why the two
 * `inset` call sites on rungs (`Menu`, `CommandPalette`) were always right and
 * nothing caught it. The one on a capsule was not: `SegmentedControl`'s thumb
 * came out at 10dp where concentricity wanted 16, six too square on a six dp
 * gap, and `SegmentedControlDefaults.TrackPadding`'s own KDoc recorded the
 * padding being tuned by eye against the skew.
 */
@Immutable
private data class InsetCornerSize(val base: CornerSize, val gap: Dp) : CornerSize {
    override fun toPx(shapeSize: Size, density: Density): Float {
        val shrink = with(density) { gap.toPx() }
        val outer = Size(shapeSize.width + shrink * 2f, shapeSize.height + shrink * 2f)
        return (base.toPx(outer, density) - shrink).coerceAtLeast(0f)
    }
}

/**
 * The radius a box drawn [gap] *outside* this shape should use to stay
 * concentric with it.
 *
 * [inset]'s other half, and the one a focus ring needs: the ring is a larger box
 * around a component, so it wants the component's radius plus the distance
 * between them. Same rule as [inset], read in the other direction.
 *
 * ### Why this is not [inset] with the sign flipped
 *
 * Both halves resolve the base against the box **the base's own shape is drawn
 * on**, and then step. For an outset that box is the inner one; for an inset it
 * is the outer one. Neither is the box being handed in, so both reconstruct.
 *
 * A *proportional* corner is where getting it wrong shows.
 * [Shapes.pill] on a 52dp component is 26dp, and the ring 3dp outside it wants
 * 29; resolve it against the 58dp ring instead and it answers 29 before the gap
 * is added, so the ring is drawn at 32 — over-rounded by exactly the gap, every
 * time.
 *
 * A fixed radius is immune to that, and so is a capped capsule once it is at its
 * cap: `CapsuleCornerSize(cap = 26.dp)` reads 26 on a 52dp field and 26 on the
 * 58dp ring around it. So the fault would be invisible on every text field in the
 * library and plain on every avatar — the reverse of the bug this replaced, where
 * a percentage corner was the one thing that came out right. Worth knowing before
 * "it looks fine on a field" is taken as evidence.
 *
 * So [OutsetCornerSize] reconstructs the inner box by subtracting the gap it knows
 * it added, resolves there, and only then grows.
 */
fun CornerBasedShape.outset(gap: Dp): CornerBasedShape = copy(
    topStart = OutsetCornerSize(topStart, gap),
    topEnd = OutsetCornerSize(topEnd, gap),
    bottomEnd = OutsetCornerSize(bottomEnd, gap),
    bottomStart = OutsetCornerSize(bottomStart, gap),
)

/**
 * A [CornerSize] that resolves to another one, plus [gap].
 *
 * Deferred for the same reason [InsetCornerSize] is, and resolved against the
 * *inner* box rather than the one it is asked about — see [outset].
 */
@Immutable
private data class OutsetCornerSize(val base: CornerSize, val gap: Dp) : CornerSize {
    override fun toPx(shapeSize: Size, density: Density): Float {
        val grow = with(density) { gap.toPx() }
        val inner = Size(
            (shapeSize.width - grow * 2f).coerceAtLeast(0f),
            (shapeSize.height - grow * 2f).coerceAtLeast(0f),
        )
        return base.toPx(inner, density) + grow
    }
}
