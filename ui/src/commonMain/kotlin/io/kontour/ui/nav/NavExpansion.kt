package io.kontour.ui.nav

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.kontour.ui.a11y.contrastEdge
import io.kontour.ui.a11y.minimumTouchTarget
import io.kontour.ui.adaptive.sheetEdges
import io.kontour.ui.adaptive.topEdges
import io.kontour.ui.foundation.ProvideTextStyle
import io.kontour.ui.foundation.RowContentScope
import io.kontour.ui.foundation.Surface
import io.kontour.ui.foundation.contentScope
import io.kontour.ui.input.focusRing
import io.kontour.ui.input.pointerCursor
import io.kontour.ui.interaction.kontourIndication
import io.kontour.ui.overlay.LocalOverlayHost
import io.kontour.ui.overlay.OverlayEntry
import io.kontour.ui.overlay.OverlayLayer
import io.kontour.ui.overlay.ScrimStyle
import io.kontour.ui.theme.Shadow
import io.kontour.ui.theme.Theme

/**
 * How much room a navigation surface is currently giving its slots.
 *
 * A [NavRail]'s `header` and `footer`, a [ModalNavRail]'s, and a [NavBar]'s
 * slots all sit inside a surface whose width is not theirs to choose — 88dp of
 * rail one moment and 280dp the next, or a hundred-odd dp between two pairs of
 * destinations. Slot content that wants to
 * respond to that has had no way to know.
 *
 * It is deliberately not about search. A profile row that shows an avatar alone
 * when narrow and an avatar with a name when wide, a "New trip" button that
 * drops its label, a filter chip that becomes an icon — all the same question,
 * and all previously answerable only by the caller threading its own copy of the
 * rail's `expanded` flag down by hand.
 *
 * ```kotlin
 * NavRail(header = {
 *     val room = LocalNavExpansion.current
 *     Row { Avatar(user); if (room.expanded) Text(user.name) }
 * }) { … }
 * ```
 *
 * @property expanded Whether there is room for a label beside an icon. What a
 *   rail decides from its animated width, so it flips partway through an
 *   expansion rather than on the frame the toggle was pressed.
 * @property onSurface Whether there is a panel behind this slot. True in every
 *   navigation surface — a bar, a rail and a modal rail all have one; false for
 *   content outside them. It decides which way a control has to go to be
 *   *seen*: raised and light over content, recessed against a panel. A control
 *   that picks one and keeps it is invisible in the other, which is how a search
 *   pill ended up the same colour as the page it was drawn on.
 *
 * Constructible, because the surfaces in this package are not the only things
 * that can host a slot: an app with its own navigation chrome should be able to
 * publish the same answer, and content written against this local then works
 * inside it without knowing it is not a [NavRail].
 */
@Stable
class NavExpansion private constructor(
    val expanded: Boolean,
    private val fixed: Float,
    private val progressOf: (() -> Float)?,
    val onSurface: Boolean,
) {
    /** Room that does not change while this is provided: a bar's, a modal rail's. */
    constructor(expanded: Boolean, progress: Float, onSurface: Boolean) :
        this(expanded, progress, null, onSurface)

    /**
     * Room that follows a resize: [progress] is read wherever [NavExpansion.progress]
     * is, so a surface animating its width provides this once and its slots
     * read the width frame by frame — in a draw or layout lambda, without
     * recomposing — rather than the surface providing a new value, and
     * recomposing every reader, on every frame.
     */
    constructor(expanded: Boolean, progress: () -> Float, onSurface: Boolean) :
        this(expanded, Float.NaN, progress, onSurface)

    /**
     * 0 when the surface is at its narrowest and 1 at its widest, for content
     * that wants to interpolate rather than switch. Always 1 in a surface that
     * does not resize.
     *
     * Read it where it is used — in `graphicsLayer {}`, `drawBehind {}` or a
     * `layout {}` block — and a resize redraws the reader without recomposing
     * it. Read in composition, every frame of a resize recomposes the reader;
     * for a decision, `derivedStateOf { room.progress > x }` recomposes it once.
     */
    val progress: Float get() = progressOf?.invoke() ?: fixed

    // Compared by value, because [LocalNavExpansion] is: a surface that provides
    // the same room on its next composition must not look like new room to
    // every slot reading it. A resizing surface's room is the same room for as
    // long as it keeps the same source for its progress.
    override fun equals(other: Any?): Boolean =
        other is NavExpansion &&
            expanded == other.expanded &&
            onSurface == other.onSurface &&
            progressOf === other.progressOf &&
            fixed.toRawBits() == other.fixed.toRawBits()

    override fun hashCode(): Int {
        var result = expanded.hashCode()
        result = 31 * result + (progressOf?.hashCode() ?: fixed.hashCode())
        result = 31 * result + onSurface.hashCode()
        return result
    }

    internal companion object {
        /** A bar's: no room for labels, on the bar's surface. */
        val Bar = NavExpansion(expanded = false, progress = 1f, onSurface = true)

        /** A modal rail's: all the room there is, on a panel. */
        val Drawer = NavExpansion(expanded = true, progress = 1f, onSurface = true)
    }
}

/**
 * The room available to the slot being composed.
 *
 * Defaults to fully expanded, because content that is not inside a navigation
 * surface has whatever width its parent gave it and should not act cramped.
 */
val LocalNavExpansion = compositionLocalOf {
    NavExpansion(expanded = true, progress = 1f, onSurface = false)
}

/** Where an expanded [NavExpandingSlot] goes. */
enum class NavExpandPlacement {
    /**
     * Over the keyboard, where the finger already is.
     *
     * The panel stays near the thumb that opened it and its content fills the
     * screen above it, which is the arrangement of every map app's search. Its
     * cost is that the content runs *up* from the control, so the first item is
     * furthest from the eye's resting point.
     */
    AboveKeyboard,

    /**
     * At the top of the screen, keyboard below it.
     *
     * Content reads downwards from the control in the order a list is normally
     * read, and the control ends up where a browser or a settings screen puts
     * one. Its cost is the reach: it travels the height of the screen away from
     * the thumb that tapped it.
     */
    Top,
}

/** Metrics for [NavExpandingSlot]. */
object NavExpandingSlotDefaults {
    /** How far the expanded panel sits in from the window's sides. */
    val Margin = 16.dp

    /**
     * What a control in a navigation slot should stand on to be seen.
     *
     * Here rather than private because the choice is not [NavExpandingSlot]'s
     * alone: anything a caller puts in a `header`, `action` or `footer` faces the
     * same question, and the answer depends on which surface it landed in rather
     * than on what it is.
     */
    @Composable
    @ReadOnlyComposable
    fun containerColour(): Color =
        if (LocalNavExpansion.current.onSurface) {
            Theme.colours.surfaceSunken
        } else {
            Theme.colours.surface
        }

    /** The shadow that goes with [containerColour]. A recess casts none. */
    @Composable
    @ReadOnlyComposable
    fun shadow(): Shadow =
        if (LocalNavExpansion.current.onSurface) Shadow.None else Theme.elevation.low
}

/**
 * A control in a navigation surface that opens into a panel over the screen.
 *
 * ```kotlin
 * var open by remember { mutableStateOf(false) }
 *
 * NavExpandingSlot(
 *     expanded = open,
 *     onExpandedChange = { open = it },
 *     expandedContent = { FilterList(onPick = { open = false }) },
 * ) {
 *     +Tabler.Outline.Filter
 *     +"Filters"
 * }
 * ```
 *
 * [NavSearch] is this with a search field in it, and is the reason it exists —
 * but nothing here knows about searching. A filter, a "where to?" prompt, an
 * account switcher and a compose box are the same shape: a small thing in the
 * bar that needs the whole screen once it is being used.
 *
 * ### Why the panel is an overlay rather than a taller bar
 *
 * A navigation bar clears `WindowInsets.bottomEdges`, whose own documentation
 * says why: *"A navigation bar holds no text field and should stay where it is
 * while the user types."* A bar that rose with the keyboard would contradict the
 * inset it asks for. So the control stays where it is and the panel goes into
 * the [io.kontour.ui.overlay.OverlayHost], the same way
 * [io.kontour.ui.overlay.CommandPalette] does, where it is free to take the
 * keyboard inset ([NavExpandPlacement.AboveKeyboard]) or the status bar's
 * ([NavExpandPlacement.Top]) without the bar knowing anything about it.
 *
 * The host brings the rest for nothing: a scrim over the content, back and
 * escape closing it, focus trapped inside it, and its own entrance.
 *
 * @param containerColour The collapsed control's ground. Chosen from
 *   [LocalNavExpansion] by default: a recessed `surfaceSunken` on a navigation
 *   surface, where a raised pill would be the surface's own colour, and a raised
 *   `surface` anywhere else.
 * @param interactionSource Hoisted, to observe the control's press and hover.
 * @param expandedContent The panel. Given a `ColumnScope` so it can weight a
 *   list to fill what is left.
 * @param content The collapsed control's contents, in a row: `+icon` and
 *   `+"Filters"` in the control's own style.
 */
@Composable
fun NavExpandingSlot(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placement: NavExpandPlacement = NavExpandPlacement.AboveKeyboard,
    containerColour: Color = NavExpandingSlotDefaults.containerColour(),
    contentColour: Color = Theme.colours.content,
    shadow: Shadow = NavExpandingSlotDefaults.shadow(),
    dismissLabel: String = Theme.strings.close,
    interactionSource: MutableInteractionSource? = null,
    expandedContent: @Composable ColumnScope.() -> Unit,
    content: @Composable RowContentScope.() -> Unit,
) {
    CollapsedControl(
        onClick = { onExpandedChange(true) },
        modifier = modifier,
        enabled = enabled,
        containerColour = containerColour,
        contentColour = contentColour,
        shadow = shadow,
        interactionSource = interactionSource,
        content = content,
    )

    // Composed only while open, so the *host* is only looked up while open.
    // `LocalOverlayHost` throws when there is none rather than returning null —
    // deliberately, because an overlay with nowhere to go is a bug that looks
    // like a missing feature — but a collapsed control has nowhere it wants to
    // go yet. Reading it unconditionally meant merely *placing* one of these in
    // a surface outside a host crashed on composition, which caught the
    // gallery's own rail specimen.
    if (expanded) {
        ExpandedPanel(
            onCollapse = { onExpandedChange(false) },
            placement = placement,
            dismissLabel = dismissLabel,
            content = expandedContent,
        )
    }
}

/**
 * The control in the bar.
 *
 * A button, whatever it looks like. Putting a live text field here would take
 * focus and raise the keyboard *inside the navigation bar*, which is the thing
 * the expansion exists to avoid — and it would need its own handling for every
 * way a field can be reached, of which tapping is only the most obvious.
 * `Role.Button` is what it is: pressing it opens something.
 */
@Composable
private fun CollapsedControl(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    containerColour: Color,
    contentColour: Color,
    shadow: Shadow,
    interactionSource: MutableInteractionSource?,
    content: @Composable RowContentScope.() -> Unit,
) {
    val interactions = interactionSource ?: remember { MutableInteractionSource() }
    val shape = Theme.shapes.capsule

    Surface(
        modifier = modifier
            .minimumTouchTarget()
            .focusRing(interactions, shape, enabled = enabled)
            .pointerCursor(enabled = enabled)
            .clickable(
                interactionSource = interactions,
                // Wide, so it shrinks by the amount a wide button does rather
                // than the amount a glyph does — but it does shrink: it is a
                // control that opens something, and nothing else here says so.
                indication = kontourIndication(shape, io.kontour.ui.interaction.IndicationDefaults.PressScale),
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        shape = shape,
        containerColour = containerColour,
        contentColour = if (enabled) contentColour else Theme.colours.contentDisabled,
        shadow = shadow,
        // A recess in the bar, `surfaceSunken` on `surface`: 1.13:1 at the
        // enhanced tier, so the control was a word floating in the bar.
        border = contrastEdge(),
    ) {
        Row(
            modifier = Modifier
                .defaultMinSize(minHeight = Theme.sizing.controlHeightMedium)
                .padding(horizontal = Theme.spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProvideTextStyle(Theme.typography.bodyMedium) {
                contentScope(iconSize = Theme.sizing.iconMedium, maxLines = 1, content = content)
            }
        }
    }
}

/** The panel, in the overlay host. */
@Composable
private fun ExpandedPanel(
    onCollapse: () -> Unit,
    placement: NavExpandPlacement,
    dismissLabel: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    val host = LocalOverlayHost.current
    // Identity for the host's stack. `remember` rather than the content lambda,
    // which is a new object every recomposition and would push a fresh entry
    // each time instead of replacing the one already there.
    val key = remember { Any() }
    val latestPlacement by rememberUpdatedState(placement)
    val latestCollapse by rememberUpdatedState(onCollapse)
    val latestContent by rememberUpdatedState(content)

    DisposableEffect(key) {
        host.show(
            OverlayEntry(
                key = key,
                layer = OverlayLayer.Dialog,
                scrim = ScrimStyle.Dimmed,
                dismissOnOutside = true,
                dismissOnBack = true,
                trapFocus = true,
                dismissLabel = dismissLabel,
                onDismiss = { latestCollapse() },
                content = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            // The whole point of the two placements. A panel over
                            // the keyboard has to clear the keyboard, which is
                            // `sheetEdges`; one at the top has to clear the status
                            // bar and the cutout, which is `topEdges`. Both tokens
                            // already existed and already meant this.
                            .windowInsetsPadding(
                                when (latestPlacement) {
                                    NavExpandPlacement.AboveKeyboard ->
                                        WindowInsets.sheetEdges
                                    NavExpandPlacement.Top -> WindowInsets.topEdges
                                }
                            )
                            .padding(NavExpandingSlotDefaults.Margin),
                        contentAlignment = when (latestPlacement) {
                            NavExpandPlacement.AboveKeyboard -> Alignment.BottomCenter
                            NavExpandPlacement.Top -> Alignment.TopCenter
                        },
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm),
                            content = latestContent,
                        )
                    }
                },
            )
        )
        onDispose { host.hide(key) }
    }
}

