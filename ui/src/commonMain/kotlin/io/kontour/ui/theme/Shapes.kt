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
 * | [control] | half its height, uncapped | Buttons, chips, FABs, switches |
 * | [field] | half its height, up to 26dp | Text fields, selects, time fields |
 * | [pill] | fully round | Avatars, scrollbars, indicators |
 * | [sheet] | 34dp top only | Bottom sheets |
 * | [sideSheet] | 34dp leading only | Side sheets |
 *
 * ### The numbers line up on purpose
 *
 * `22` is not an arbitrary middle rung: the medium control height is 44dp, so a
 * medium button's corner is exactly half its height and the button is a capsule.
 * The ladder is built around that number rather than the other way round, which
 * is what lets a card sit next to a button and read as the same family.
 *
 * [control] and [field] do not take a rung at all — they take
 * [CapsuleCornerSize], half the shorter side. That is the only way a family stays
 * consistent across its *own* size scale, and it is why a 44dp switch and a 44dp
 * button agree without either of them naming a number.
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
 * [pill] survives as a true capsule for the things that are round because of what
 * they are rather than how tall they are: an avatar, a scrollbar, a selection
 * indicator.
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
     * Anything you press, and anything that labels a thing you could press.
     *
     * Buttons, icon buttons, split buttons, button groups, chips, tags, floating
     * actions, and the toolbar that holds them. Half its own height, so a row of
     * mixed actions has one corner regardless of what each one's height happens
     * to be — which is the thing a fixed radius cannot do: at 14dp an `XSmall`
     * button was nearly a capsule already and an `XLarge` was nearly square, so
     * one component disagreed with itself across its own size scale.
     */
    val control: CornerBasedShape = SquircleShape(CapsuleCornerSize()),

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
     * The cap is 26dp — half [Sizing.controlHeightLarge], which is what a text
     * field's `minHeight` resolves to. So the default single-line field lands
     * *exactly* on a capsule and everything taller stops there, which is the
     * narrowest place the line can be drawn while still drawing it.
     */
    val field: CornerBasedShape = SquircleShape(CapsuleCornerSize(cap = 26.dp)),

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
 * Half the shorter side, up to [cap]. What makes a control a capsule at any height.
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
 * @param cap The radius to stop growing at. Uncapped by default, because a
 *   *control* is a capsule at every height — that is the whole property, and a
 *   72dp button with a 30dp corner is not a capsule. It is [Shapes.field] that
 *   needs the cap, and only because a multi-line text area is the one thing here
 *   that is tall without wanting to be round.
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
 */
@Immutable
private data class InsetCornerSize(val base: CornerSize, val gap: Dp) : CornerSize {
    override fun toPx(shapeSize: Size, density: Density): Float =
        (base.toPx(shapeSize, density) - with(density) { gap.toPx() }).coerceAtLeast(0f)
}
