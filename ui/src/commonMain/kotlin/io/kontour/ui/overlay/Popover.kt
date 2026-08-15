package io.kontour.ui.overlay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.display.Spinner
import io.kontour.ui.foundation.Surface
import io.kontour.ui.foundation.Text
import io.kontour.ui.theme.Theme

/**
 * A small panel of *content* attached to a control.
 *
 * ```
 * Box {
 *     IconButton(Tabler.Outline.InfoCircle, "About this route", onClick = { open = true })
 *     Popover(open, onDismissRequest = { open = false }) {
 *         Text("Route 950", style = Theme.typography.titleSmall)
 *         Text("Runs every 15 minutes until 11pm.")
 *     }
 * }
 * ```
 *
 * The difference from a [DropdownMenu] is what goes inside. A menu holds a list
 * of actions and behaves like one: arrow keys move between items, a click picks
 * one and closes. A popover holds arbitrary content — a legend, a filter form, a
 * summary — and stays open while the user works in it.
 *
 * The difference from a [Dialog] is weight. A popover points at the thing it is
 * about and leaves the rest of the screen alone; a dialog takes over. If the
 * content is a decision that must be made before anything else can happen, it is
 * a dialog.
 *
 * Carries an arrow, since a floating panel with no visible connection to its
 * trigger is just a small dialog in the wrong place.
 *
 * @param scrim [ScrimStyle.Transparent] by default: taps outside close it
 *   without dimming, which is right for something this light. Pass
 *   [ScrimStyle.Dimmed] when the popover holds a form worth protecting from a
 *   stray tap.
 */
@Composable
fun Popover(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    side: OverlaySide = OverlaySide.Bottom,
    alignment: OverlayAlignment = OverlayAlignment.Center,
    scrim: ScrimStyle = ScrimStyle.Transparent,
    showArrow: Boolean = true,
    maxWidth: Dp = 320.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val host = LocalOverlayHost.current
    val colors = Theme.colors
    val key = remember { Any() }
    var anchor by remember { mutableStateOf<Rect?>(null) }
    val dismiss by rememberUpdatedState(onDismissRequest)
    // Read live by the overlay's measure pass rather than captured when the
    // entry was built — see `AnchoredOverlayLayout`.
    val latestAnchor by rememberUpdatedState(anchor)
    val body by rememberUpdatedState(content)
    // As with the anchor: read live rather than captured when the entry was
    // published, so a modifier change reaches a popover that is already open.
    val latestModifier by rememberUpdatedState(modifier)

    Box(Modifier.parentBounds { anchor = it })

    DisposableEffect(Unit) { onDispose { host.hide(key) } }

    LaunchedEffect(expanded, anchor != null, side, alignment, scrim, showArrow, maxWidth) {
        if (!expanded || anchor == null) {
            host.hide(key)
            return@LaunchedEffect
        }

        host.show(
            OverlayEntry(
                key = key,
                layer = OverlayLayer.Menu,
                scrim = scrim,
                dismissLabel = "Close",
                onDismiss = { dismiss() },
                content = {
                    AnchoredOverlayLayout(
                        anchorInRoot = { latestAnchor },
                        fromScale = 0.94f,
                        side = side,
                        alignment = alignment,
                        gap = Theme.spacing.xxs,
                        margin = MenuDefaults.ScreenMargin,
                        arrow = if (showArrow) {
                            ArrowSpec(color = colors.surfaceRaised)
                        } else {
                            null
                        },
                    ) {
                        PopoverPanel(
                            modifier = latestModifier,
                            maxWidth = maxWidth,
                            border = !showArrow,
                            onDismissRequest = { dismiss() },
                            content = body,
                        )
                    }
                },
            )
        )
    }
}

@Composable
private fun PopoverPanel(
    modifier: Modifier,
    maxWidth: Dp,
    border: Boolean,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    OverlaySurface(
        modifier = modifier
            .widthIn(max = maxWidth)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                    onDismissRequest()
                    true
                } else {
                    false
                }
            },
        border = border,
    ) {
        Column(
            modifier = Modifier.padding(Theme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
            content = content,
        )
    }
}

/**
 * Blocks the screen while something the user started is finishing.
 *
 * ```
 * LoadingOverlay(visible = viewModel.isPlanning, label = "Planning your trip")
 * ```
 *
 * Deliberately hard to escape — [OverlayLayer.Critical], no outside dismissal,
 * no back dismissal. That is the point of it: it exists for the moments where
 * letting the user carry on would leave the app in a state nobody has thought
 * about.
 *
 * Which is also why it should be rare. Blocking the whole screen for something
 * that usually takes 200ms trades a brief wait for a flash of grey, and the
 * flash is worse. Prefer a [io.kontour.ui.components.display.Skeleton] where the
 * result will fill a known shape, an inline
 * [io.kontour.ui.components.display.Spinner] where one region is loading, and a
 * button's own `loading` state where the user pressed a button. This is for
 * whole-screen, must-not-interrupt work: submitting a payment, finalising a
 * booking.
 *
 * Announces itself as an assertive live region, because a sighted user can see
 * the screen has stopped responding and a screen-reader user cannot.
 *
 * @param label Say what is happening, not that something is. "Loading" tells the
 *   user nothing they had not already worked out.
 */
@Composable
fun LoadingOverlay(
    visible: Boolean,
    label: String,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
) {
    val host = LocalOverlayHost.current
    val key = remember { Any() }

    DisposableEffect(Unit) { onDispose { host.hide(key) } }

    LaunchedEffect(visible, label, showLabel) {
        if (!visible) {
            host.hide(key)
            return@LaunchedEffect
        }

        host.show(
            OverlayEntry(
                key = key,
                layer = OverlayLayer.Critical,
                scrim = ScrimStyle.Dimmed,
                dismissOnOutside = false,
                dismissOnBack = false,
                content = {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Surface(
                            modifier = modifier
                                .semantics(mergeDescendants = true) {
                                    liveRegion = LiveRegionMode.Assertive
                                    contentDescription = label
                                },
                            shape = Theme.shapes.large,
                            color = Theme.colors.surfaceRaised,
                            shadow = Theme.elevation.overlay,
                        ) {
                            Column(
                                modifier = Modifier.padding(Theme.spacing.lg),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm),
                            ) {
                                Spinner(size = Theme.sizing.iconLarge)
                                if (showLabel) {
                                    Text(
                                        text = label,
                                        style = Theme.typography.bodyMedium,
                                        color = Theme.colors.contentMuted,
                                    )
                                }
                            }
                        }
                    }
                },
            )
        )
    }
}
