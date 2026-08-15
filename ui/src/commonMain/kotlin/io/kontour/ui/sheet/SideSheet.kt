package io.kontour.ui.sheet

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.Surface
import io.kontour.ui.overlay.LocalOverlayHost
import io.kontour.ui.overlay.OverlayEntry
import io.kontour.ui.overlay.LocalOverlayProgress
import io.kontour.ui.overlay.OverlayLayer
import io.kontour.ui.overlay.ScrimStyle
import io.kontour.ui.adaptive.allEdges
import io.kontour.ui.theme.Theme
import io.kontour.ui.theme.mirrorHorizontally
import kotlin.math.roundToInt

/** Which edge a [SideSheet] comes in from. Follows the layout direction. */
enum class SheetSide { Start, End }

/**
 * A sheet that slides in from the side.
 *
 * ```kotlin
 * SideSheet(visible = filtersOpen, onDismissRequest = { filtersOpen = false }) {
 *     SheetHeader("Filters")
 *     …
 * }
 * ```
 *
 * The wide-screen counterpart of [ModalBottomSheet]. On a phone a bottom sheet
 * is right because the content is near the thumb and the screen is taller than
 * it is wide; on a tablet or desktop the same sheet becomes a short letterbox
 * across a very wide window, and a side sheet uses the shape of the screen
 * instead.
 *
 * Slides rather than dragging through detents. A side sheet has one useful
 * position — open — and a horizontal drag on a wide screen usually means
 * something else was intended. Dismissal is the scrim, the close button or a
 * back gesture.
 *
 * @param side Which edge it enters from, following the layout direction.
 *   [SheetSide.End] is the default: the sheet is supplementary to the content,
 *   and supplementary things belong on the trailing side.
 */
@Composable
fun SideSheet(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    side: SheetSide = SheetSide.End,
    width: Dp = 400.dp,
    shape: CornerBasedShape = Theme.shapes.sideSheet,
    containerColor: Color = Theme.colors.surfaceRaised,
    contentColor: Color = Theme.colors.content,
    scrim: ScrimStyle = ScrimStyle.Dimmed,
    dismissOnOutside: Boolean = true,
    dismissLabel: String = "Close",
    paneTitle: String? = null,
    /**
     * What the sheet's *content* keeps clear of. Every edge including the
     * keyboard: a side sheet is full height, so it meets the status bar and the
     * gesture bar at once, and it holds forms as often as a bottom sheet does.
     */
    windowInsets: WindowInsets = WindowInsets.allEdges,
    content: @Composable ColumnScope.() -> Unit,
) {
    val host = LocalOverlayHost.current
    val key = remember { Any() }
    val dismiss by rememberUpdatedState(onDismissRequest)
    val body by rememberUpdatedState(content)

    DisposableEffect(Unit) { onDispose { host.hide(key) } }

    LaunchedEffect(visible, side, width, scrim) {
        if (!visible) {
            host.hide(key)
            return@LaunchedEffect
        }

        host.show(
            OverlayEntry(
                key = key,
                layer = OverlayLayer.Sheet,
                scrim = scrim,
                dismissOnOutside = dismissOnOutside,
                dismissLabel = dismissLabel,
                onDismiss = { dismiss() },
                content = {
                    SideSheetPanel(
                        modifier = modifier,
                        side = side,
                        width = width,
                        shape = shape,
                        containerColor = containerColor,
                        contentColor = contentColor,
                        paneTitle = paneTitle,
                        // Captured as an object, not a measurement: the modifier
                        // reads the live inset at layout time, so the sheet still
                        // lifts when the keyboard opens after it was shown.
                        windowInsets = windowInsets,
                        content = body,
                    )
                },
            )
        )
    }
}

@Composable
private fun SideSheetPanel(
    modifier: Modifier,
    side: SheetSide,
    width: Dp,
    shape: CornerBasedShape,
    containerColor: Color,
    contentColor: Color,
    paneTitle: String?,
    windowInsets: WindowInsets,
    content: @Composable ColumnScope.() -> Unit,
) {
    val motion = Theme.motion
    val density = LocalDensity.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    // Driven by the host, in both directions — a panel that set its own
    // `appeared` flag on first composition could only ever run 0 -> 1, which is
    // why nothing in the library animated *out*.
    val progress = LocalOverlayProgress.current

    // Physical edge, once the layout direction has been applied.
    val fromRight = (side == SheetSide.End) != isRtl
    val alignment = if (fromRight) Alignment.CenterEnd else Alignment.CenterStart
    val travel = with(density) { width.toPx() }

    Box(Modifier.fillMaxSize()) {
        Box(
            modifier = modifier
                .align(alignment)
                .width(width)
                .fillMaxHeight()
                .offset {
                    val hidden = travel * (1f - progress)
                    IntOffset(
                        x = (if (fromRight) hidden else -hidden).roundToInt(),
                        y = 0,
                    )
                }
                .semantics {
                    isTraversalGroup = true
                    if (paneTitle != null) this.paneTitle = paneTitle
                }
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                // Mirrored for a start-side sheet, so the rounded edge is always
                // the one facing the content rather than the window edge.
                shape = if (fromRight) shape else shape.mirrorHorizontally(),
                color = containerColor,
                contentColor = contentColor,
                shadow = Theme.elevation.overlay,
            ) {
                CompositionLocalProvider(LocalSheetState provides null) {
                    Column(
                        // Inside the surface, so the sheet's colour still runs
                        // to the edges of the window.
                        Modifier.fillMaxSize().windowInsetsPadding(windowInsets),
                        content = content,
                    )
                }
            }
        }
    }
}
