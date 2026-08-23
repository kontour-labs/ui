package io.kontour.ui.nav

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.kontour.ui.a11y.minimumTouchTarget
import io.kontour.ui.adaptive.sheetEdges
import io.kontour.ui.adaptive.topEdges
import io.kontour.ui.foundation.Surface
import io.kontour.ui.input.focusRing
import io.kontour.ui.interaction.FeedbackIntent
import io.kontour.ui.interaction.LocalFeedback
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
 * A [NavRail]'s `header` and `action`, a [NavDrawer]'s `header` and `footer`,
 * and a [NavBar]'s `search` and `action` all sit inside a surface whose width is
 * not theirs to choose — 88dp of rail one moment and 280dp the next, or a
 * hundred-odd dp between two pairs of destinations. Slot content that wants to
 * respond to that has had no way to know.
 *
 * It is deliberately not about search. A profile row that shows an avatar alone
 * when narrow and an avatar with a name when wide, a "New trip" button that
 * drops its label, a filter chip that becomes an icon — all the same question,
 * and all previously answerable only by the caller threading its own copy of the
 * rail's `expanded` flag down by hand.
 *
 * ```kotlin
 * NavRail(items, selectedIndex = current, header = {
 *     val room = LocalNavExpansion.current
 *     Row { Avatar(user); if (room.expanded) Text(user.name) }
 * })
 * ```
 *
 * @property expanded Whether there is room for a label beside an icon. What a
 *   rail decides from its animated width, so it flips partway through an
 *   expansion rather than on the frame the toggle was pressed.
 * @property progress 0 when the surface is at its narrowest and 1 at its widest,
 *   for content that wants to interpolate rather than switch. Always 1 in a
 *   surface that does not resize.
 * Constructible, because the surfaces in this package are not the only things
 * that can host a slot: an app with its own navigation chrome should be able to
 * publish the same answer, and content written against this local then works
 * inside it without knowing it is not a [NavRail].
 *
 * @property onSurface Whether there is a panel behind this slot. True in a rail
 *   or a drawer, which are `Surface`s; false in a bar, which is free-standing
 *   circles over the content with nothing behind them. It decides which way a
 *   control has to go to be *seen*: raised and light over content, recessed
 *   against a panel. A control that picks one and keeps it is invisible in the
 *   other, which is how a search pill ended up the same colour as the page it
 *   was drawn on.
 */
@Immutable
class NavExpansion(
    val expanded: Boolean,
    val progress: Float,
    val onSurface: Boolean,
)

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
object NavExpandDefaults {
    /** How far the expanded panel sits in from the window's sides. */
    val Margin = 16.dp
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
 * @param containerColor The collapsed control's ground. Chosen from
 *   [LocalNavExpansion] by default — raised `surface` in a bar, where this
 *   floats over the content beside destinations that are themselves raised
 *   circles, and recessed `surfaceSunken` in a rail or drawer, where there is a
 *   panel behind it and a raised pill would be the panel's own colour.
 * @param expandedContent The panel. Given a `ColumnScope` so it can weight a
 *   list to fill what is left.
 * @param content The collapsed control's contents, in a row.
 */
@Composable
fun NavExpandingSlot(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placement: NavExpandPlacement = NavExpandPlacement.AboveKeyboard,
    containerColor: Color = navSlotContainerColor(),
    contentColor: Color = Theme.colors.content,
    shadow: io.kontour.ui.theme.Shadow = navSlotShadow(),
    dismissLabel: String = Theme.strings.close,
    expandedContent: @Composable ColumnScope.() -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    CollapsedControl(
        onClick = { onExpandedChange(true) },
        modifier = modifier,
        enabled = enabled,
        containerColor = containerColor,
        contentColor = contentColor,
        shadow = shadow,
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
    containerColor: Color,
    contentColor: Color,
    shadow: io.kontour.ui.theme.Shadow,
    content: @Composable RowScope.() -> Unit,
) {
    val feedback = LocalFeedback.current
    val interactions = remember { MutableInteractionSource() }
    val shape = Theme.shapes.pill

    Surface(
        modifier = modifier
            .minimumTouchTarget()
            .focusRing(interactions, shape, enabled = enabled)
            .clickable(
                interactionSource = interactions,
                // Wide, so it shrinks by the amount a wide button does rather
                // than the amount a glyph does — but it does shrink: it is a
                // control that opens something, and nothing else here says so.
                indication = kontourIndication(shape, io.kontour.ui.interaction.DefaultPressScale),
                enabled = enabled,
                role = Role.Button,
                onClick = {
                    feedback.perform(FeedbackIntent.Selection)
                    onClick()
                },
            ),
        shape = shape,
        color = containerColor,
        contentColor = if (enabled) contentColor else Theme.colors.contentDisabled,
        shadow = shadow,
    ) {
        Row(
            modifier = Modifier
                .defaultMinSize(minHeight = Theme.sizing.controlHeightMedium)
                .padding(horizontal = Theme.spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
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
                            .padding(NavExpandDefaults.Margin),
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

/**
 * What a control in a navigation slot should stand on to be seen.
 *
 * Public because the choice is not [NavExpandingSlot]'s alone: anything a caller
 * puts in a `header`, `action` or `footer` faces the same question, and the
 * answer depends on which surface it landed in rather than on what it is.
 */
@Composable
fun navSlotContainerColor(): Color =
    if (LocalNavExpansion.current.onSurface) {
        Theme.colors.surfaceSunken
    } else {
        Theme.colors.surface
    }

/** The shadow that goes with [navSlotContainerColor]. A recess casts none. */
@Composable
fun navSlotShadow(): io.kontour.ui.theme.Shadow =
    if (LocalNavExpansion.current.onSurface) Shadow.None else Theme.elevation.low
