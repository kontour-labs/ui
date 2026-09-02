package io.kontour.ui.components.action

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.LocalContentColour
import io.kontour.ui.a11y.contrastEdge
import io.kontour.ui.theme.Theme

/**
 * How much weight a button carries on the screen it sits on.
 *
 * Pick by importance, not by appearance. A screen should have at most one
 * [Primary] — if two things look equally important, the user has to decide
 * which matters, which is the job the design was supposed to do.
 */
enum class ButtonVariant {
    /** The one action the screen exists for. Solid, near-black. */
    Primary,

    /** A real alternative to the primary action. Outlined. */
    Secondary,

    /** Supporting actions that should not compete. Filled with a soft ground. */
    Tertiary,

    /**
     * The brand, as an action. Solid, in the accent tone.
     *
     * [Primary] is near-black and carries the screen's one action; this is for
     * the place where the product should show up as itself — an onboarding
     * "Get started", a "Plan a trip". Two solid buttons on one screen is still
     * one too many, so this is an *alternative* to [Primary], not a companion.
     */
    Accent,

    /** Lowest weight — toolbar actions, inline "edit". No ground until hovered. */
    Ghost,

    /** Deletes, cancels a booking, ends a trip. Solid, in the danger tone. */
    Destructive,

    /** A destructive action that should not shout — usually inside a menu or row. */
    DestructiveGhost,
}

/**
 * How large a button is.
 *
 * [Medium] is the default and covers most cases. [Large] and [XLarge] are for
 * a screen's single main call to action — the full-width "Plan trip" at the
 * bottom of a sheet. [XSmall] and [Small] belong in dense contexts: table rows,
 * toolbars, chips of actions.
 *
 * Sizes come from `Theme.sizing.controlHeight*`, shared with inputs and selects,
 * so a row of mixed controls lines up without per-call-site padding.
 */
enum class ButtonSize {
    XSmall,
    Small,
    Medium,
    Large,
    XLarge,
}

/** Resolved colours for one button variant, in one state. */
@Immutable
data class ButtonColours(
    val container: Color,
    val content: Color,
    val border: Color?,
    val disabledContainer: Color,
    val disabledContent: Color,
    val disabledBorder: Color?,
) {
    fun container(enabled: Boolean): Color = if (enabled) container else disabledContainer
    fun content(enabled: Boolean): Color = if (enabled) content else disabledContent
    fun border(enabled: Boolean): Color? = if (enabled) border else disabledBorder
}

/** Resolved metrics for one button size. */
@Immutable
data class ButtonMetrics(
    val height: Dp,
    val horizontalPadding: Dp,
    val iconSize: Dp,
    val gap: Dp,
    val textStyle: TextStyle,
)

/**
 * Defaults for [Button] and its relatives.
 *
 * Exposed rather than inlined into signatures so a caller can take one value and
 * override it:
 * ```
 * Button(
 *     colours = ButtonDefaults.colours(ButtonVariant.Primary).copy(container = brandOverride),
 *     onClick = ::save,
 * ) { +"Save" }
 * ```
 */
object ButtonDefaults {

    /**
     * A disabled ghost label, on whatever ground the button happens to be on.
     *
     * On an ordinary surface this is the scheme's own disabled token. On a
     * coloured ground there is no such token — the scheme cannot enumerate every
     * container a ghost button might land on — so it falls back to fading the
     * inherited content colour. That is a real 4.5:1 failure by design: disabled
     * controls are exempt from the contrast minimum precisely so that "you
     * cannot use this" reads at a glance.
     */
    @Composable
    @ReadOnlyComposable
    private fun disabledGhostContent(): Color {
        val inherited = LocalContentColour.current
        return if (inherited == Theme.colours.content) {
            Theme.colours.contentDisabled
        } else {
            inherited.copy(alpha = DisabledContentAlpha)
        }
    }

    /** Matches the ratio between `content` and `contentDisabled` in the schemes. */
    private const val DisabledContentAlpha = 0.45f

    @Composable
    @ReadOnlyComposable
    fun colours(variant: ButtonVariant): ButtonColours {
        val c = Theme.colours
        // Disabled styling is shared: a flat sunken ground and muted content,
        // rather than each variant fading its own colours. A disabled outlined
        // button and a disabled solid one should not look like different
        // controls — they are both "not available right now".
        val disabledContainer = when (variant) {
            ButtonVariant.Ghost, ButtonVariant.DestructiveGhost, ButtonVariant.Secondary ->
                Color.Transparent
            else -> c.surfaceSunken
        }
        // A disabled button is a flat sunken ground on a page that is nearly the
        // same colour, so at the high-contrast tier it needs an edge for exactly
        // the reason `Tertiary` does. Disabled controls are exempt from the
        // *text* minimum, not from having a discernible boundary.
        val edge = contrastEdge()
        val disabledBorder = when {
            variant == ButtonVariant.Secondary -> c.outline
            // Every variant, ghosts included. An enabled ghost gains an edge at
            // this tier, so a disabled one without it is not a fainter button —
            // it is not a button, and the row of icon buttons reads as five
            // controls and one stray glyph.
            edge != null -> c.outline
            else -> null
        }

        return when (variant) {
            ButtonVariant.Primary -> ButtonColours(
                container = c.primary,
                content = c.onPrimary,
                border = null,
                disabledContainer = disabledContainer,
                disabledContent = c.contentDisabled,
                disabledBorder = disabledBorder,
            )

            ButtonVariant.Secondary -> ButtonColours(
                container = Color.Transparent,
                content = c.content,
                border = c.outlineStrong,
                disabledContainer = disabledContainer,
                disabledContent = c.contentDisabled,
                disabledBorder = disabledBorder,
            )

            // `surfaceSunken` on `background` measures 1.14:1 at the
            // high-contrast light tier and 1.06:1 at dark — a button-shaped
            // region of the page either way. The edge is what makes it a control.
            ButtonVariant.Tertiary -> ButtonColours(
                container = c.surfaceSunken,
                content = c.content,
                border = edge?.let { c.outline },
                disabledContainer = disabledContainer,
                disabledContent = c.contentDisabled,
                disabledBorder = disabledBorder,
            )

            // The only variant that reads its label from the ground it is on
            // rather than from the scheme. A ghost button has no container of
            // its own, so it inherits whatever the surrounding surface set as
            // the content colour: near-black on a card, white on a danger
            // toast, `accent.onSolid` inside a coach mark. On an ordinary surface
            // this resolves to `c.content` and nothing changes.
            // A ghost button is chrome-less on purpose, and at the high-contrast
            // tier that purpose loses: a control whose only boundary is the
            // shape of its own text is one WCAG 1.4.11 has an opinion about. It
            // becomes a secondary button, which is the right answer for a user
            // who asked for this.
            ButtonVariant.Ghost -> ButtonColours(
                container = Color.Transparent,
                content = LocalContentColour.current,
                border = edge?.let { LocalContentColour.current },
                disabledContainer = disabledContainer,
                disabledContent = disabledGhostContent(),
                disabledBorder = disabledBorder,
            )

            ButtonVariant.Accent -> ButtonColours(
                container = c.accent.solid,
                content = c.accent.onSolid,
                border = null,
                disabledContainer = disabledContainer,
                disabledContent = c.contentDisabled,
                disabledBorder = disabledBorder,
            )

            ButtonVariant.Destructive -> ButtonColours(
                container = c.danger.solid,
                content = c.danger.onSolid,
                border = null,
                disabledContainer = disabledContainer,
                disabledContent = c.contentDisabled,
                disabledBorder = disabledBorder,
            )

            ButtonVariant.DestructiveGhost -> ButtonColours(
                container = Color.Transparent,
                content = c.danger.onContainer,
                // The tone's own border, so the edge stays part of the button
                // rather than a grey rectangle drawn around a red label.
                border = edge?.let { c.danger.border },
                disabledContainer = disabledContainer,
                disabledContent = c.contentDisabled,
                disabledBorder = disabledBorder,
            )
        }
    }

    @Composable
    @ReadOnlyComposable
    fun metrics(size: ButtonSize): ButtonMetrics {
        val s = Theme.sizing
        val t = Theme.typography
        return when (size) {
            ButtonSize.XSmall -> ButtonMetrics(
                height = s.controlHeightXSmall,
                horizontalPadding = 10.dp,
                iconSize = s.iconSmall,
                gap = 4.dp,
                textStyle = t.labelSmall,
            )

            ButtonSize.Small -> ButtonMetrics(
                height = s.controlHeightSmall,
                horizontalPadding = 14.dp,
                iconSize = s.iconSmall,
                gap = 6.dp,
                textStyle = t.labelMedium,
            )

            ButtonSize.Medium -> ButtonMetrics(
                height = s.controlHeightMedium,
                horizontalPadding = 20.dp,
                iconSize = s.iconMedium,
                gap = 8.dp,
                textStyle = t.labelMedium,
            )

            ButtonSize.Large -> ButtonMetrics(
                height = s.controlHeightLarge,
                horizontalPadding = 24.dp,
                iconSize = s.iconLarge,
                gap = 8.dp,
                textStyle = t.labelLarge,
            )

            ButtonSize.XLarge -> ButtonMetrics(
                height = s.controlHeightXLarge,
                horizontalPadding = 32.dp,
                iconSize = s.iconLarge,
                gap = 10.dp,
                textStyle = t.labelLarge,
            )
        }
    }

    /**
     * How far a control shrinks on press.
     *
     * ### Ghost, and what the rule about it was actually about
     *
     * Ghost buttons did not shrink at all, for a stated reason that is right:
     * with no visible container, a shrinking *label* reads as the text jumping
     * rather than as a control responding. The trouble is that it is a rule
     * about text, and it was being applied to glyphs. A default [IconButton] is
     * Ghost and every `IconToggleButton` hard-codes Ghost, so the two most
     * tapped controls in the library could not respond to a press at all — and a
     * `Toolbar`'s own documented example puts a `ButtonGroup` that shrinks
     * beside a bare `IconButton` that does not. A glyph is a shape, not a line of
     * type, and shrinking one reads exactly as it should.
     *
     * So [iconOnly] splits it: ghost *text* keeps its 1f, ghost *icons* respond
     * like everything else.
     *
     * ### And why one number was never going to work
     *
     * [io.kontour.ui.interaction.DefaultPressScale] is 3%, and 3% is what you
     * pick when the same constant has to serve a 28dp icon button and a
     * full-width XLarge one. It is invisible on the small control and it is the
     * most the large one can take: shrink a wide button 7% and it reads as the
     * layout collapsing. Scaling with the control instead gives the small ones
     * the movement that makes the switch and the checkbox feel alive — they are
     * small controls, which is most of why they can afford it — and leaves the
     * large ones where they were.
     */
    fun pressScale(
        variant: ButtonVariant,
        size: ButtonSize = ButtonSize.Medium,
        iconOnly: Boolean = false,
    ): Float {
        val ghost = variant == ButtonVariant.Ghost || variant == ButtonVariant.DestructiveGhost
        if (ghost && !iconOnly) return 1f
        return when (size) {
            ButtonSize.XSmall, ButtonSize.Small -> SmallPressScale
            ButtonSize.Medium -> MediumPressScale
            ButtonSize.Large, ButtonSize.XLarge -> io.kontour.ui.interaction.DefaultPressScale
        }
    }

    /** 28 and 36dp: small enough that 7% reads as a press rather than as a collapse. */
    const val SmallPressScale: Float = 0.93f

    /** 44dp. */
    const val MediumPressScale: Float = 0.95f
}
