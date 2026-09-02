package io.kontour.ui.components.action

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import io.kontour.ui.a11y.minimumTouchTarget
import io.kontour.ui.foundation.Icon
import io.kontour.ui.foundation.LocalContentColour
import io.kontour.ui.foundation.strikethrough
import io.kontour.ui.input.focusRing
import io.kontour.ui.interaction.Feedback
import io.kontour.ui.interaction.FeedbackIntent
import io.kontour.ui.interaction.kontourIndication
import io.kontour.ui.theme.Theme

/**
 * A button whose whole label is an icon.
 *
 * ```
 * IconButton(
 *     icon = Icons.Close,
 *     contentDescription = "Close",
 *     onClick = ::dismiss,
 * )
 * ```
 *
 * The visual bounds stay small — the icon plus a little padding — while the
 * touch target expands to the platform minimum around it. That is why a toolbar
 * of 20dp icons is still usable with a thumb.
 *
 * @param contentDescription **Required and non-null.** There is no visible text
 *   to fall back on, so an icon button without a description is a control a
 *   screen-reader user simply cannot identify. If the icon is genuinely
 *   decorative, it is not a button.
 * @param rotation Degrees to rotate the icon, animated.
 *
 *   Deliberately *not* `Modifier.rotate()` at the call site, for two reasons.
 *   The caller's `modifier` lands on the outer box, which carries the touch
 *   target, the focus ring, the ripple and the container — a rotation there
 *   turns the hit rectangle and the focus ring with the glyph, which is
 *   invisible on a pill and wrong everywhere else. And this is a *target*, not a
 *   value: it is animated through `springBouncy`, so it overshoots a few degrees
 *   and settles, which a static modifier cannot express.
 *
 *   For disclosure chevrons and menu/close morphs, pass the target — 
 *   `if (expanded) ChevronTurn else 0f` — rather than swapping between two
 *   icons, which reads as a flicker. Never pass an already-animated angle: two
 *   springs in series arrive late and land differently from every other arrow.
 */
@Composable
fun IconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /**
     * Swaps the glyph for a spinner and blocks input.
     *
     * The same exchange a `Button` makes — see [LoadingSwap] — because an icon
     * button firing a request is the same situation as a labelled one firing it,
     * and until now only the labelled one could say so.
     */
    loading: Boolean = false,
    /** What a screen reader announces while [loading]. */
    loadingLabel: String = Theme.strings.loading,
    variant: ButtonVariant = ButtonVariant.Ghost,
    size: ButtonSize = ButtonSize.Medium,
    shape: Shape = Theme.shapes.control,
    rotation: Float = 0f,
    colours: ButtonColours = ButtonDefaults.colours(variant),
    metrics: ButtonMetrics = ButtonDefaults.metrics(size),
    interactionSource: MutableInteractionSource? = null,
) {
    val interactions = interactionSource ?: remember { MutableInteractionSource() }
    val feedback = Feedback
    val interactive = enabled && !loading

    IconButtonSurface(
        icon = icon,
        contentDescription = contentDescription,
        modifier = modifier.semantics { if (loading) stateDescription = loadingLabel },
        enabled = enabled,
        loading = loading,
        shape = shape,
        rotation = rotation,
        colours = colours,
        metrics = metrics,
        interactions = interactions,
        indication = kontourIndication(
            shape,
            ButtonDefaults.pressScale(variant, size, iconOnly = true),
        ),
        behaviour = Modifier.clickable(
            interactionSource = interactions,
            indication = null,
            enabled = interactive,
            role = Role.Button,
            onClick = {
                feedback.perform(FeedbackIntent.Selection)
                onClick()
            },
        ),
    )
}

/**
 * An [IconButton] with an on/off state.
 *
 * ```
 * IconToggleButton(
 *     icon = if (favourite) Icons.StarFilled else Icons.StarOutline,
 *     contentDescription = "Favourite",
 *     checked = favourite,
 *     onCheckedChange = viewModel::setFavourite,
 * )
 * ```
 *
 * Announces itself as a toggle with its current state, so a screen reader says
 * "Favourite, ticked" rather than just "Favourite". Pass a distinct [icon] per
 * state — the state must be visible, not only audible.
 *
 * `Role.Checkbox`, not `Role.Switch`, and the same as [FilterChip][
 * io.kontour.ui.components.selection.FilterChip]: `Switch` is reserved for the
 * sliding control, where the announcement "on/off" matches something the user
 * can see. A star that announces itself as a switch describes a widget that is
 * not on screen.
 *
 * @param stateDescription Overrides the announced state. Defaults to the
 *   platform's own ticked/unticked wording; pass something specific where that is
 *   ambiguous ("Showing live vehicles" / "Hiding live vehicles").
 */
@Composable
fun IconToggleButton(
    icon: ImageVector,
    contentDescription: String,
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    /**
     * Drawn instead of [icon] while [checked] — a filled star against an outline
     * one, a bookmark against its outline.
     *
     * Null keeps [icon] in both states, where colour alone carries the toggle.
     * That is legible but it is one channel, and a filled counterpart is the
     * clearest signal a toggle has: it survives greyscale, low vision and the
     * colour being changed by a theme. Prefer passing one.
     *
     * The swap cross-fades rather than cutting, so the two glyphs read as one
     * mark changing state.
     */
    checkedIcon: ImageVector? = null,
    /**
     * Draws a slash across [icon] while [checked], instead of swapping glyphs.
     *
     * For the toggles whose off state is conventionally "the same thing, with a
     * line through it": a revealed password, a muted alert, a hidden layer. Icon
     * sets ship the slashed counterpart as its own glyph and swapping to it is a
     * cut — the line is simply there on the next frame. Drawn, it arrives.
     *
     * Ignored when [checkedIcon] is given: a glyph that already has a slash in
     * it does not want a second one.
     *
     * A struck toggle also keeps its ghost container while checked, rather than
     * taking the accent one every other toggle takes. The slash is already an
     * unmistakable statement of the state, and a filled chip behind it says the
     * same thing a second time — too loudly for what is usually a secondary
     * affordance sitting inside something else.
     */
    strikethrough: Boolean = false,
    enabled: Boolean = true,
    size: ButtonSize = ButtonSize.Medium,
    shape: Shape = Theme.shapes.control,
    stateDescription: String? = null,
    interactionSource: MutableInteractionSource? = null,
) {
    val interactions = interactionSource ?: remember { MutableInteractionSource() }
    val feedback = Feedback
    val checkedColours = ButtonDefaults.colours(ButtonVariant.Tertiary)
    val uncheckedColours = ButtonDefaults.colours(ButtonVariant.Ghost)
    val accentColours = checkedColours.copy(
        container = Theme.colours.accent.container,
        content = Theme.colours.accent.onContainer,
    )

    // A struck glyph carries its own state, so the container stays out of it.
    //
    // Every other checked toggle takes the accent container, because "filled
    // star" against "outline star" is a real but quiet difference and the tint
    // is what makes it carry across a room. A slash is not quiet. Tinting as
    // well puts a filled chip behind a secondary affordance — the reveal eye in
    // a password field is the case that showed it — and says the same thing
    // twice, the second time louder than the control deserves.
    val struck = strikethrough && checkedIcon == null
    val strike by animateFloatAsState(
        targetValue = if (struck && checked) 1f else 0f,
        animationSpec = Theme.motion.tweenDefault(),
        label = "iconToggleStrike",
    )

    IconButtonSurface(
        icon = if (checked) checkedIcon ?: icon else icon,
        // Cross-faded when the two glyphs differ, so a star filling in reads as
        // the same star rather than one icon leaving and another arriving.
        crossFadeIcon = checkedIcon != null,
        // Read in the draw phase: a line moving across a glyph is not worth
        // recomposing a button for.
        strike = if (struck) ({ strike }) else null,
        contentDescription = contentDescription,
        // The state description goes on the same node as the toggle, not on a
        // wrapper around it. A wrapper produces two nodes — a labelled one with
        // no action and an actionable one with no label — and assistive tech
        // reads the pair as a control inside a control.
        modifier = modifier.then(
            if (stateDescription != null) {
                Modifier.semantics { this.stateDescription = stateDescription }
            } else {
                Modifier
            }
        ),
        enabled = enabled,
        shape = shape,
        rotation = 0f,
        colours = if (checked && !struck) accentColours else uncheckedColours,
        metrics = ButtonDefaults.metrics(size),
        interactions = interactions,
        // An inert toggle still gets an indication node; it simply never sees a
        // press, because nothing is feeding its interaction source.
        indication = kontourIndication(
            shape,
            ButtonDefaults.pressScale(ButtonVariant.Ghost, size, iconOnly = true),
        ),
        // `null` is inert but still announces as a checkbox in its current
        // state, the same as [Checkbox] and [Switch]. A toggle wired to nothing
        // that also says nothing is a decoration.
        behaviour = if (onCheckedChange == null) {
            Modifier.semantics {
                role = Role.Checkbox
                toggleableState = ToggleableState(checked)
            }
        } else {
            Modifier.toggleable(
                value = checked,
                interactionSource = interactions,
                indication = null,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = {
                    feedback.perform(FeedbackIntent.Selection)
                    onCheckedChange(it)
                },
            )
        },
    )
}

/**
 * The shared body of [IconButton] and [IconToggleButton]: a circular container
 * with one centred, optionally-rotated glyph.
 *
 * [behaviour] is the caller's `clickable` or `toggleable` — passed in rather than
 * built here so that the role, the state and the action all land on **one**
 * semantics node. That is the whole reason this exists: the obvious alternative,
 * wrapping an `IconButton` in a `Box` that adds toggle semantics, produces a node
 * tree that reads as a switch containing a button.
 */
@Composable
private fun IconButtonSurface(
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier,
    enabled: Boolean,
    loading: Boolean = false,
    crossFadeIcon: Boolean = false,
    /** How much of a slash is drawn across the glyph. See [IconToggleButton]. */
    strike: (() -> Float)? = null,
    shape: Shape,
    rotation: Float,
    colours: ButtonColours,
    metrics: ButtonMetrics,
    interactions: MutableInteractionSource,
    behaviour: Modifier,
    /** Applied ahead of the container so the whole button scales, not the glyph. */
    indication: androidx.compose.foundation.Indication?,
) {
    val motion = Theme.motion

    val container by animateColorAsState(
        targetValue = colours.container(enabled),
        animationSpec = motion.tweenFast(),
        label = "iconButtonContainer",
    )
    val contentColour by animateColorAsState(
        targetValue = colours.content(enabled),
        animationSpec = motion.tweenFast(),
        label = "iconButtonContent",
    )
    val animatedRotation by animateFloatAsState(
        targetValue = rotation,
        // Bouncy: a chevron that overshoots a few degrees and settles reads as
        // a physical flip rather than a value being assigned.
        animationSpec = motion.springOrTween(motion.springBouncy),
        label = "iconButtonRotation",
    )
    val borderColour = colours.border(enabled)
    // The control height, not the icon plus a padding of its own.
    //
    // It used to be `iconSize + iconOnlyPadding * 2`, which is a second way of
    // saying how tall a control is — and it disagreed with the first at three
    // of the five sizes: an XSmall icon button was 24dp beside a 28dp button, a
    // Small one 28dp beside 36dp, a Medium one 40dp beside 44dp. Large and
    // XLarge happened to agree, which is why it survived.
    //
    // `Sizing` already promises that "a row of mixed controls lines up without
    // per-call-site padding", and this was the one control not keeping it:
    // every `ButtonGroup` mixing an icon action with a labelled one was ragged,
    // and so was the trailing half of a `SplitButton`. The padding round the
    // glyph is now whatever is left over, which is the only way the two can
    // agree by construction rather than by being kept in step.
    val boxSize = metrics.height

    Box(
        modifier = modifier
            .minimumTouchTarget()
            .focusRing(interactions, shape)
            // Ahead of the container — see `Button` for why. The `behaviour`
            // modifier below sits past `.background()`, so an indication handed
            // to it scaled the glyph inside a circle that never moved.
            .indication(interactions, indication)
            .size(boxSize)
            .clip(shape)
            .background(container, shape)
            .then(
                if (borderColour != null) {
                    Modifier.border(BorderStroke(Theme.sizing.borderWidthStrong, borderColour), shape)
                } else {
                    Modifier
                }
            )
            .then(behaviour),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColour provides contentColour) {
            LoadingSwap(loading = loading, spinnerSize = metrics.iconSize) {
                if (crossFadeIcon) {
                    AnimatedContent(
                        targetState = icon,
                        transitionSpec = {
                            fadeIn(motion.tweenFast()) +
                                scaleIn(motion.tweenFast(), initialScale = 0.7f) togetherWith
                                fadeOut(motion.tweenFast()) +
                                scaleOut(motion.tweenFast(), targetScale = 0.7f)
                        },
                        label = "iconToggleGlyph",
                    ) { glyph ->
                        Icon(
                            imageVector = glyph,
                            contentDescription = contentDescription,
                            modifier = Modifier
                                .rotate(animatedRotation)
                                .slash(strike, contentColour, container),
                            size = metrics.iconSize,
                        )
                    }
                } else {
                    Icon(
                        imageVector = icon,
                        contentDescription = contentDescription,
                        modifier = Modifier
                            .rotate(animatedRotation)
                            .slash(strike, contentColour, container),
                        size = metrics.iconSize,
                    )
                }
            }
        }
    }
}

/**
 * The slash across a toggled-off glyph, or nothing at all.
 *
 * A tiny wrapper so the two `Icon` call sites above read the same whether or not
 * a slash was asked for — see [strikethrough] for what it draws and why the
 * groove is the container's colour rather than the page's.
 */
private fun Modifier.slash(
    strike: (() -> Float)?,
    colour: Color,
    container: Color,
): Modifier = if (strike == null) {
    this
} else {
    strikethrough(
        progress = strike,
        colour = colour,
        halo = container,
        width = StrikeWidth,
    )
}

/** The slash's own thickness. The same weight as a control's strong border. */
private val StrikeWidth = 2.dp
