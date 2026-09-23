package io.kontour.ui.demo

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.ui.adaptive.WindowSizeClassProvider
import io.kontour.ui.adaptive.WindowWidthClass
import io.kontour.ui.foundation.Text
import io.kontour.ui.input.Cursor
import io.kontour.ui.input.pointerCursor
import io.kontour.ui.theme.Theme
import kotlin.math.roundToInt

/**
 * A window inside the page, whose width the reader drags.
 *
 * Adaptive components answer to the *window*, and a demo card is not one — so
 * until this, the only way to see a pane scaffold collapse was a tablet, and the
 * only way to see a window class change was to resize the whole browser. This is
 * a bordered box that re-provides [io.kontour.ui.adaptive.LocalWindowSizeClass]
 * for its content, with a grip on its trailing edge and a readout underneath.
 *
 * **It always opens at [initialWidth]**, whatever the page is — a constant rather
 * than a fraction of the card — so the first frame is the same picture on a phone
 * and a desktop and a golden of it does not depend on the canvas.
 *
 * **The clamp cannot throw.** The width is the request held *at most* to what
 * there is, and never `coerceIn` a range: a card narrower than the minimum
 * inverts that range, and `coerceIn` throws mid-layout on an inverted one.
 *
 * **The readout reads the frame, not the size class.** `WindowSizeClass` leaves
 * its dp out of `equals` so that a window resized within one class does not
 * recompose everything under it — which is right for an app and means a readout
 * of `windowSizeClass.widthDp` would stand still while the frame moved. The class
 * name comes from the same width the provider will measure.
 */
@Composable
internal fun AdaptiveFrame(
    modifier: Modifier = Modifier,
    initialWidth: Dp = AdaptiveFrameDefaults.InitialWidth,
    height: Dp = AdaptiveFrameDefaults.Height,
    content: @Composable () -> Unit,
) {
    var requested by remember(initialWidth) { mutableStateOf(initialWidth) }

    BoxWithConstraints(modifier.fillMaxWidth()) {
        // The grip first, or at a phone's width the frame takes the whole row
        // and the grip is measured at nothing.
        val available = (maxWidth - AdaptiveFrameDefaults.GripWidth).coerceAtLeast(0.dp)
        val width = requested.coerceAtMost(available)

        Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs)) {
            Row(Modifier.height(height)) {
                Box(
                    Modifier
                        .width(width)
                        .fillMaxHeight()
                        .border(Theme.sizing.borderWidth, Theme.colours.outline, Theme.shapes.medium)
                        .clip(Theme.shapes.medium),
                ) {
                    WindowSizeClassProvider(Modifier.fillMaxSize()) { content() }
                }
                FrameGrip(
                    width = width,
                    available = available,
                    onResize = { requested = it },
                )
            }
            Text(
                "${WindowWidthClass.of(width).name} · ${width.value.roundToInt()}dp",
                style = Theme.typography.labelSmall,
                colour = Theme.colours.contentMuted,
            )
        }
    }
}

internal object AdaptiveFrameDefaults {
    /** A phone in portrait: the width most people will first see a layout at. */
    val InitialWidth: Dp = 360.dp

    val Height: Dp = 280.dp

    /** Narrow enough for a compact window, wide enough to still hold a list. */
    val MinWidth: Dp = 240.dp

    val GripWidth: Dp = 16.dp
}

/**
 * The trailing edge, dragged sideways.
 *
 * A real control rather than a decoration: the resize cursor under a mouse, and a
 * progress range with `setProgress` for a screen reader, which cannot drag — the
 * same bargain the pane scaffold's own splitter makes.
 */
@Composable
private fun FrameGrip(width: Dp, available: Dp, onResize: (Dp) -> Unit) {
    val density = LocalDensity.current
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    val dragged by interactions.collectIsDraggedAsState()
    // Growing is toward the end, which is leftward in a right-to-left layout.
    val toward = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f

    // Read at the moment of each delta, not captured: several can arrive between
    // two frames, and each has to start from where the last one left the frame.
    val latestAvailable by rememberUpdatedState(available)
    var live by remember { mutableStateOf(width) }
    val colour by animateColorAsState(
        if (hovered || dragged) Theme.colours.accent.solid else Theme.colours.outlineStrong,
        label = "frameGrip",
    )
    val minimum = AdaptiveFrameDefaults.MinWidth.coerceAtMost(available)

    Box(
        Modifier
            .width(AdaptiveFrameDefaults.GripWidth)
            .fillMaxHeight()
            .pointerCursor(Cursor.ResizeColumn)
            .hoverable(interactions)
            .draggable(
                state = rememberDraggableState { delta ->
                    live = (live + with(density) { (delta * toward).toDp() })
                        .coerceAtMost(latestAvailable)
                        .coerceAtLeast(AdaptiveFrameDefaults.MinWidth.coerceAtMost(latestAvailable))
                    onResize(live)
                },
                orientation = Orientation.Horizontal,
                interactionSource = interactions,
                onDragStarted = { live = width },
            )
            .semantics {
                contentDescription = "Frame width"
                stateDescription = "${width.value.roundToInt()}dp"
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = width.value,
                    range = minimum.value..available.value,
                )
                setProgress { target ->
                    onResize(target.dp.coerceAtMost(available).coerceAtLeast(minimum))
                    true
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .width(4.dp)
                .height(40.dp)
                .background(colour, Theme.shapes.capsule),
        )
    }
}
