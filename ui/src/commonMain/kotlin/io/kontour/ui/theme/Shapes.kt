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

/**
 * The corner-radius scale.
 *
 * **One step, all the way up.** Every rung is 6dp above the one below it, and
 * that regularity is the point rather than a tidiness: the concentricity rule —
 * an inner radius is its container's radius minus the gap between them — only
 * holds if the scale it steps through is even. The old `4 / 4 / 4 / 8` ladder
 * broke at the top, so a control nested in a dialog was concentric and the same
 * control nested in a sheet was not. Use [inset] rather than picking the next
 * token down by eye.
 *
 * | Token | Radius | Corner | Used by |
 * |---|---|---|---|
 * | [extraSmall] | 8dp | circular | Badges, tags, inline code |
 * | [small] | 14dp | circular | Buttons, inputs, checkboxes |
 * | [medium] | 20dp | squircle | Cards, list groups, menus |
 * | [large] | 26dp | squircle | Dialogs, large cards |
 * | [extraLarge] | 32dp | squircle | Sheets, hero panels |
 * | [pill] | fully round | capsule | Nav bars, chips, avatars, FABs |
 * | [sheet] | 32dp top only | squircle | Bottom sheets |
 * | [sideSheet] | 32dp leading only | squircle | Side sheets |
 *
 * ### Why two kinds of corner
 *
 * From [medium] up the corners are [SquircleShape] — curvature eased in and out
 * rather than a quarter circle bolted between two straight edges. It reads as
 * softer at the same nominal radius, and it is what makes a large surface look
 * drawn rather than clipped.
 *
 * It is not free: a squircle is a generic path to clip, to border and to shadow.
 * Below about 12dp the smoothing is not visible, so [extraSmall] and [small] stay
 * circular and pay nothing for it. [pill] is a true capsule, where the corner is
 * a semicircle and there is no curvature discontinuity to remove in the first
 * place — which is also why it is the most-used token here and should stay that
 * way.
 *
 * ### One number for sheets
 *
 * [sheet] and [sideSheet] are [extraLarge] with two corners zeroed, derived
 * rather than restated. A panel against the edge of the window should be square
 * where it meets that edge — a rounded corner there leaves a sliver of
 * background showing through — but it should be *the same radius* as a hero
 * panel on the side that faces the content, and for a while it silently was not.
 */
@Immutable
data class Shapes(
    val extraSmall: CornerBasedShape = RoundedCornerShape(8.dp),
    val small: CornerBasedShape = RoundedCornerShape(14.dp),
    val medium: CornerBasedShape = SquircleShape(20.dp),
    val large: CornerBasedShape = SquircleShape(26.dp),
    val extraLarge: CornerBasedShape = SquircleShape(32.dp),
    val pill: CornerBasedShape = RoundedCornerShape(percent = 50),

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
