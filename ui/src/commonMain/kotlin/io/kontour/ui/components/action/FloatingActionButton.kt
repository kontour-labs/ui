package io.kontour.ui.components.action

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.a11y.minimumTouchTarget
import io.kontour.ui.foundation.Icon
import io.kontour.ui.foundation.ProvideTextStyle
import io.kontour.ui.foundation.Surface
import io.kontour.ui.foundation.Text
import io.kontour.ui.input.focusRing
import io.kontour.ui.interaction.Feedback
import io.kontour.ui.interaction.FeedbackIntent
import io.kontour.ui.interaction.kontourIndication
import io.kontour.ui.foundation.RowContentScope
import io.kontour.ui.foundation.contentScope
import io.kontour.ui.motion.AnimatedSlot
import io.kontour.ui.theme.Shadow
import io.kontour.ui.theme.Theme

/** How large a [FloatingActionButton] is. */
enum class FabSize(internal val container: Dp, internal val icon: Dp) {
    Small(40.dp, 20.dp),
    Medium(56.dp, 24.dp),
    Large(72.dp, 32.dp),
}

/**
 * A floating action button — the one persistent action on a screen.
 *
 * ```
 * FloatingActionButton(
 *     icon = Icons.Plus,
 *     contentDescription = "Add favourite",
 *     onClick = ::addFavourite,
 * )
 * ```
 *
 * Floats above content, so it takes [io.kontour.ui.theme.Elevation.medium] and
 * a pill shape. One per screen: a second FAB is two competing "the" actions.
 *
 * If the action needs a label to be understood, use [ExtendedFloatingActionButton]
 * rather than hoping the icon carries it.
 *
 * @param border A hairline round the edge. Null for the default near-black FAB,
 *   where there is nothing to tell apart. It exists for the *light* one: in the
 *   light scheme `background`, `surface` and `surfaceRaised` are all white, so a
 *   FAB in a surface colour is a white circle on a white page held together by
 *   its shadow alone — which is legible over a map and not much else. This is the
 *   same hairline `OverlaySurface` gives every menu and popover, for the same
 *   reason, and [FabMenu] puts it on its items by default.
 */
@Composable
fun FloatingActionButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** Swaps the icon for a spinner and blocks input. See [LoadingSwap]. */
    loading: Boolean = false,
    /** What a screen reader announces while [loading]. */
    loadingLabel: String = Theme.strings.loading,
    size: FabSize = FabSize.Medium,
    shape: Shape = Theme.shapes.control,
    containerColour: Color = Theme.colours.primary,
    contentColour: Color = Theme.colours.onPrimary,
    border: BorderStroke? = null,
    interactionSource: MutableInteractionSource? = null,
) {
    val interactions = interactionSource ?: remember { MutableInteractionSource() }
    val feedback = Feedback
    val (fabColor, fabContent, fabShadow) = fabColours(enabled, containerColour, contentColour)
    val interactive = enabled && !loading

    Surface(
        modifier = modifier
            .minimumTouchTarget()
            .focusRing(interactions, shape)
            .defaultMinSize(minWidth = size.container, minHeight = size.container)
            .height(size.container)
            .clickable(
                interactionSource = interactions,
                indication = kontourIndication(shape, FabDefaults.pressScale(size)),
                enabled = interactive,
                role = Role.Button,
                onClick = {
                    feedback.perform(FeedbackIntent.Confirm)
                    onClick()
                },
            ),
        shape = shape,
        colour = fabColor,
        contentColour = fabContent,
        border = border,
        shadow = fabShadow,
        // `defaultMinSize` makes the surface larger than the icon inside it, so
        // without this the icon lands in the FAB's top-left corner rather than
        // its middle.
        contentAlignment = Alignment.Center,
    ) {
        LoadingSwap(loading = loading, spinnerSize = size.icon) {
            Icon(icon, contentDescription = contentDescription, size = size.icon)
        }
    }
}

/**
 * A [FloatingActionButton] whose middle is yours.
 *
 * ```
 * FloatingActionButton(onClick = ::open, contentDescription = "Open menu") {
 *     Icon(SystemIcons.Plus, contentDescription = null, modifier = Modifier.rotate(turn))
 * }
 * ```
 *
 * The icon form covers what a FAB usually holds and should stay the first
 * choice. This one is for the times the glyph has to be *treated* rather than
 * just chosen — turned, cross-faded, badged — which an `ImageVector` parameter
 * cannot express, because by the time the component has it there is nowhere left
 * to put a modifier.
 *
 * [FabMenu] is the case that forced it: its anchor rotates a plus into a cross,
 * and rotating the button instead only works while the button is round.
 *
 * @param contentDescription Announced by a screen reader, and set on the button
 *   rather than on whatever is inside it — the slot's own content should pass
 *   `null`, or the control is named twice.
 */
@Composable
fun FloatingActionButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** Swaps the icon for a spinner and blocks input. See [LoadingSwap]. */
    loading: Boolean = false,
    /** What a screen reader announces while [loading]. */
    loadingLabel: String = Theme.strings.loading,
    size: FabSize = FabSize.Medium,
    shape: Shape = Theme.shapes.control,
    containerColour: Color = Theme.colours.primary,
    contentColour: Color = Theme.colours.onPrimary,
    border: BorderStroke? = null,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    val interactions = interactionSource ?: remember { MutableInteractionSource() }
    val feedback = Feedback
    val (fabColor, fabContent, fabShadow) = fabColours(enabled, containerColour, contentColour)
    val interactive = enabled && !loading

    Surface(
        modifier = modifier
            .minimumTouchTarget()
            .focusRing(interactions, shape)
            .defaultMinSize(minWidth = size.container, minHeight = size.container)
            .height(size.container)
            .semantics { this.contentDescription = contentDescription }
            .clickable(
                interactionSource = interactions,
                indication = kontourIndication(shape, FabDefaults.pressScale(size)),
                enabled = interactive,
                role = Role.Button,
                onClick = {
                    feedback.perform(FeedbackIntent.Confirm)
                    onClick()
                },
            ),
        shape = shape,
        colour = fabColor,
        contentColour = fabContent,
        border = border,
        shadow = fabShadow,
        contentAlignment = Alignment.Center,
        content = { LoadingSwap(loading = loading, spinnerSize = size.icon) { content() } },
    )
}

/**
 * A FAB that carries a label as well as an icon, and can collapse to just the
 * icon as the user scrolls.
 *
 * ```
 * ExtendedFloatingActionButton(
 *     icon = Icons.Navigation,
 *     contentDescription = "Start trip",
 *     expanded = !listState.isScrollingDown,
 *     onClick = ::startTrip,
 * ) { +"Start trip" }
 * ```
 *
 * The collapse animates the *width* rather than cross-fading between two
 * components, so the icon stays put and the label slides out from behind it.
 * Cross-fading makes the icon appear to jump sideways.
 *
 * @param contentDescription Announced by a screen reader. Kept separate from
 *   the label slot because the label may be terse where the announcement should
 *   not be — "Start" on screen, "Start trip to Perth Station" for a screen
 *   reader. It also has to survive the collapse, when there is no label left to
 *   read.
 */
@Composable
fun ExtendedFloatingActionButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** Swaps the icon for a spinner and blocks input. See [LoadingSwap]. */
    loading: Boolean = false,
    /** What a screen reader announces while [loading]. */
    loadingLabel: String = Theme.strings.loading,
    expanded: Boolean = true,
    size: FabSize = FabSize.Medium,
    shape: Shape = Theme.shapes.control,
    containerColour: Color = Theme.colours.primary,
    contentColour: Color = Theme.colours.onPrimary,
    border: BorderStroke? = null,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowContentScope.() -> Unit,
) {
    val interactions = interactionSource ?: remember { MutableInteractionSource() }
    val motion = Theme.motion
    val feedback = Feedback
    val (fabColor, fabContent, fabShadow) = fabColours(enabled, containerColour, contentColour)
    val interactive = enabled && !loading

    // Collapsed, the padding is whatever makes the button as wide as it is tall,
    // which is a different number at every size: the icon is the only content
    // left, so the width is `padding + icon + padding` and it has to come out at
    // `container`. It was a flat 16dp, which is `(56 - 24) / 2` — correct for
    // `Medium` by arithmetic accident and wrong either side of it, so the
    // collapsed button drew a circle at one size and a lozenge at the other two.
    val horizontalPadding by animateDpAsState(
        targetValue = if (expanded) ExpandedPadding else (size.container - size.icon) / 2,
        animationSpec = motion.springOrTween(motion.springDefault),
        label = "fabPadding",
    )

    Surface(
        modifier = modifier
            .minimumTouchTarget()
            .focusRing(interactions, shape)
            .height(size.container)
            .clickable(
                interactionSource = interactions,
                indication = kontourIndication(shape, FabDefaults.pressScale(size)),
                enabled = interactive,
                role = Role.Button,
                onClick = {
                    feedback.perform(FeedbackIntent.Confirm)
                    onClick()
                },
            ),
        shape = shape,
        colour = fabColor,
        contentColour = fabContent,
        border = border,
        shadow = fabShadow,
    ) {
        // The whole row swaps, icon and label together: a spinner beside a
        // label that is still there would read as the label being the thing
        // that is not loading.
        LoadingSwap(loading = loading, spinnerSize = size.icon) {
        Row(
            modifier = Modifier
                .height(size.container)
                .padding(horizontal = horizontalPadding),
            // No `spacedBy`: the label's gap travels inside `AnimatedSlot`, or
            // the row drops it in one frame when the label leaves composition.
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = if (expanded) null else contentDescription,
                size = size.icon,
            )
            AnimatedSlot(
                visible = expanded,
                gap = Theme.spacing.xs,
                enter = expandHorizontally(motion.springOrTween(motion.springDefault)) +
                    fadeIn(motion.tweenFast()),
                exit = shrinkHorizontally(motion.springOrTween(motion.springDefault)) +
                    fadeOut(motion.tweenFast()),
            ) {
                ProvideTextStyle(Theme.typography.labelLarge) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        contentScope(maxLines = 1, content = content)
                    }
                }
            }
        }
        }
    }
}

/**
 * A disabled FAB looks disabled.
 *
 * It did not: `containerColour` and `contentColour` were used whatever `enabled`
 * said, so the only difference between a live FAB and a dead one was that
 * tapping it did nothing. Every other control in the library resolves a disabled
 * pair — see `ButtonColours.container(enabled)` — and a floating action is the
 * one most likely to be the only affordance on a screen, so it is the worst
 * place to leave the state invisible. It is also a WCAG 1.4.1 problem rather
 * than a cosmetic one: "you cannot press this" was carried by behaviour alone.
 *
 * The shadow goes with it. A control that cannot be pressed should not be the
 * thing floating highest off the page.
 */
@Composable
private fun fabColours(
    enabled: Boolean,
    containerColour: Color,
    contentColour: Color,
): Triple<Color, Color, Shadow> = if (enabled) {
    Triple(containerColour, contentColour, Theme.elevation.medium)
} else {
    Triple(Theme.colours.surfaceSunken, Theme.colours.contentDisabled, Theme.elevation.flat)
}

/** How much room the label gets either side of it once the button is open. */
private val ExpandedPadding = 20.dp

/** Metrics for a [FloatingActionButton] that are not on [FabSize] itself. */
object FabDefaults {
    /**
     * How far a FAB shrinks on press.
     *
     * By its container, on the same reasoning as
     * [io.kontour.ui.components.action.ButtonDefaults.pressScale]: a 40dp circle
     * can take the movement that makes a small control feel alive, and a 72dp one
     * cannot. A FAB is a single closed shape rather than a row of type, which is
     * why even the large one is worth more than the 3% a wide button gets.
     */
    fun pressScale(size: FabSize): Float = when (size) {
        FabSize.Small -> ButtonDefaults.SmallPressScale
        FabSize.Medium, FabSize.Large -> ButtonDefaults.MediumPressScale
    }
}
