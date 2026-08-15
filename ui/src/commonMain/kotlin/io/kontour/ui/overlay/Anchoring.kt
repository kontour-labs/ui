package io.kontour.ui.overlay

import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.Surface
import io.kontour.ui.theme.Theme

/**
 * Which side of its anchor an overlay prefers to sit on.
 *
 * A *preference*, not an instruction. A menu asked to open below a button near
 * the bottom of the screen opens above it instead — see [positionAnchored].
 * [Start] and [End] follow the layout direction, so a submenu opens leftward in
 * an RTL locale without the caller doing anything.
 */
enum class OverlaySide { Top, Bottom, Start, End }

/** How an overlay lines up along the edge it sits on. */
enum class OverlayAlignment { Start, Center, End }

/** [OverlaySide] once the layout direction has been applied. */
internal enum class ResolvedSide { Above, Below, Left, Right }

@Immutable
internal data class AnchoredPlacement(
    val x: Int,
    val y: Int,
    val side: ResolvedSide,
)

/**
 * Works out where to put an overlay of [contentSize] next to [anchor].
 *
 * Pure, and separately tested, because this is where anchored overlays actually
 * go wrong: they look right in the middle of the screen and clip off the edge in
 * the corner, which is the one case nobody checks by hand.
 *
 * Two corrections, in this order:
 *
 * 1. **Flip.** If the preferred side has no room and the opposite side does, use
 *    the opposite side. If neither fits, keep whichever has more room — an
 *    overlay that must be clipped should at least be clipped at the end nobody
 *    reads first.
 * 2. **Shift.** Slide along the other axis until the whole thing is inside the
 *    container, keeping [margin] clear of the edges.
 *
 * Shifting is deliberately not constrained by the anchor: a menu aligned to the
 * start of a button in the far corner slides until it fits, ending up no longer
 * aligned with that button. That is correct. Alignment is a preference; being on
 * screen is not.
 *
 * @param gap Distance between the anchor and the overlay.
 * @param margin Minimum distance from the container's edges.
 */
internal fun positionAnchored(
    anchor: Rect,
    contentSize: IntSize,
    containerSize: IntSize,
    side: OverlaySide,
    alignment: OverlayAlignment,
    gap: Int,
    margin: Int,
    isRtl: Boolean,
): AnchoredPlacement {
    val preferred = when (side) {
        OverlaySide.Top -> ResolvedSide.Above
        OverlaySide.Bottom -> ResolvedSide.Below
        OverlaySide.Start -> if (isRtl) ResolvedSide.Right else ResolvedSide.Left
        OverlaySide.End -> if (isRtl) ResolvedSide.Left else ResolvedSide.Right
    }
    val opposite = when (preferred) {
        ResolvedSide.Above -> ResolvedSide.Below
        ResolvedSide.Below -> ResolvedSide.Above
        ResolvedSide.Left -> ResolvedSide.Right
        ResolvedSide.Right -> ResolvedSide.Left
    }

    fun roomOn(s: ResolvedSide): Int = when (s) {
        ResolvedSide.Above -> (anchor.top - margin - gap).toInt()
        ResolvedSide.Below -> (containerSize.height - margin - anchor.bottom - gap).toInt()
        ResolvedSide.Left -> (anchor.left - margin - gap).toInt()
        ResolvedSide.Right -> (containerSize.width - margin - anchor.right - gap).toInt()
    }

    fun needsOn(s: ResolvedSide): Int = when (s) {
        ResolvedSide.Above, ResolvedSide.Below -> contentSize.height
        ResolvedSide.Left, ResolvedSide.Right -> contentSize.width
    }

    val resolved = when {
        roomOn(preferred) >= needsOn(preferred) -> preferred
        roomOn(opposite) >= needsOn(opposite) -> opposite
        roomOn(opposite) > roomOn(preferred) -> opposite
        else -> preferred
    }

    fun shift(value: Int, size: Int, extent: Int): Int {
        val max = extent - margin - size
        // A container too small to hold the content at all: pin to the leading
        // edge rather than letting the clamp invert and push it off-screen.
        if (max < margin) return margin
        return value.coerceIn(margin, max)
    }

    return when (resolved) {
        ResolvedSide.Above, ResolvedSide.Below -> {
            val leading = if (isRtl) {
                when (alignment) {
                    OverlayAlignment.Start -> anchor.right - contentSize.width
                    OverlayAlignment.Center -> anchor.center.x - contentSize.width / 2f
                    OverlayAlignment.End -> anchor.left
                }
            } else {
                when (alignment) {
                    OverlayAlignment.Start -> anchor.left
                    OverlayAlignment.Center -> anchor.center.x - contentSize.width / 2f
                    OverlayAlignment.End -> anchor.right - contentSize.width
                }
            }
            val y = if (resolved == ResolvedSide.Above) {
                anchor.top - gap - contentSize.height
            } else {
                anchor.bottom + gap
            }
            AnchoredPlacement(
                x = shift(leading.toInt(), contentSize.width, containerSize.width),
                y = shift(y.toInt(), contentSize.height, containerSize.height),
                side = resolved,
            )
        }

        ResolvedSide.Left, ResolvedSide.Right -> {
            val top = when (alignment) {
                OverlayAlignment.Start -> anchor.top
                OverlayAlignment.Center -> anchor.center.y - contentSize.height / 2f
                OverlayAlignment.End -> anchor.bottom - contentSize.height
            }
            val x = if (resolved == ResolvedSide.Left) {
                anchor.left - gap - contentSize.width
            } else {
                anchor.right + gap
            }
            AnchoredPlacement(
                x = shift(x.toInt(), contentSize.width, containerSize.width),
                y = shift(top.toInt(), contentSize.height, containerSize.height),
                side = resolved,
            )
        }
    }
}

/**
 * What an anchored overlay may measure itself against.
 *
 * The room the container has, less [margin] on each side — and a minimum width
 * *clamped to that*, which is the whole reason this is a function rather than
 * three lines inline. A select asks its menu to match the field's width; a
 * full-width field on a phone is as wide as the window; and the window less two
 * margins is narrower than the field. Passing that pair straight to `Constraints`
 * throws, so a full-width select would crash the first time it was opened.
 *
 * ### An unbounded axis stays unbounded
 *
 * A host inside a scrolling parent is measured with `Constraints.Infinity` on the
 * scroll axis. Subtracting the margin from that gives `Int.MAX_VALUE - 16` — a
 * *finite* number too large for `Constraints` to bit-pack, so it throws with the
 * memorable `can't represent a width of 384 and height of 2147483631` rather than
 * anything that points at a scroll container.
 *
 * "Infinity minus sixteen" is not a size, so there is nothing to subtract from:
 * an unbounded axis is passed through unbounded and the overlay's own
 * `heightIn(max = …)` does the limiting instead.
 */
internal fun overlayConstraints(
    container: IntSize,
    margin: Int,
    minWidth: Int,
): Constraints {
    val maxWidth = container.width.lessMargin(margin)
    val maxHeight = container.height.lessMargin(margin)
    return Constraints(
        minWidth = if (maxWidth == Constraints.Infinity) minWidth else minWidth.coerceIn(0, maxWidth),
        maxWidth = maxWidth,
        maxHeight = maxHeight,
    )
}

/** This extent less a margin on each side, or [Constraints.Infinity] if it had none. */
private fun Int.lessMargin(margin: Int): Int =
    if (this == Constraints.Infinity) Constraints.Infinity else (this - margin * 2).coerceAtLeast(0)

/** This extent, or [fallback] when there is no extent to speak of. */
private fun Int.orContent(fallback: Int): Int =
    if (this == Constraints.Infinity) fallback else this

/**
 * Captures the bounds of the composable this modifier is applied to, in root
 * coordinates, so an overlay can be anchored to it.
 *
 * Reports `null` once the node detaches, which is what stops a tooltip pointing
 * at a list item that has been scrolled away.
 */
fun Modifier.anchorBounds(onBounds: (Rect?) -> Unit): Modifier =
    onGloballyPositioned { onBounds(if (it.isAttached) it.boundsInRoot() else null) }

/**
 * Captures the bounds of this composable's *parent*, in root coordinates.
 *
 * Lets a dropdown be declared next to the control it belongs to and still know
 * where that control is:
 *
 * ```
 * Box {
 *     Button("Sort", onClick = { expanded = true })
 *     DropdownMenu(expanded, onDismissRequest = { expanded = false }) { … }
 * }
 * ```
 *
 * The alternative — making every caller hoist a `Rect` — puts positioning
 * plumbing into every call site to save one modifier here.
 */
internal fun Modifier.parentBounds(onBounds: (Rect?) -> Unit): Modifier =
    onGloballyPositioned { coordinates ->
        val parent: LayoutCoordinates? = coordinates.parentLayoutCoordinates
        onBounds(if (parent != null && parent.isAttached) parent.boundsInRoot() else null)
    }

/** A pointer showing which element an overlay belongs to. */
@Immutable
data class ArrowSpec(
    val color: Color,
    val width: Dp = 14.dp,
    val height: Dp = 7.dp,
)

/** Carries the arrow geometry from the measure pass to the draw pass. */
private class ArrowPath {
    var path: Path? = null
}

/**
 * Places [content] beside [anchorInRoot], flipping and shifting to stay on
 * screen.
 *
 * Fills the overlay host and positions within itself, so it belongs inside an
 * [OverlayEntry]'s content — that is where the container bounds it measures
 * against come from.
 *
 * @param anchorInRoot The anchor in *root* coordinates, read fresh on every
 *   measure — the host's own offset is subtracted, so a host that is not itself
 *   at the root still positions correctly.
 *
 *   A lambda, not a `Rect`, and that is the whole fix for anchored overlays
 *   lagging behind a scroll. The caller's `Rect` lives in state that
 *   `onGloballyPositioned` updates as the anchor moves; passing the value
 *   snapshotted it into the overlay entry's content lambda, so a new position
 *   could only reach the screen by re-running a `LaunchedEffect`, rebuilding
 *   the entry and replacing it in the host — several frames, every frame of the
 *   scroll. Reading it here puts the read inside the measure pass, so the
 *   overlay re-places in the same pass the anchor moved in, exactly as
 *   `host.originInRoot` already did.
 * @param arrow Draws a pointer against the resolved side. Give it the same
 *   colour as the content's surface — it is a sibling of the surface rather than
 *   part of it, so it does not pick up the surface's shadow or border.
 * @param minWidth Useful for a select, whose menu should be at least as wide as
 *   the field it drops from.
 */
@Composable
internal fun AnchoredOverlayLayout(
    anchorInRoot: () -> Rect?,
    side: OverlaySide,
    alignment: OverlayAlignment,
    gap: Dp,
    margin: Dp,
    modifier: Modifier = Modifier,
    arrow: ArrowSpec? = null,
    minWidth: Dp = Dp.Unspecified,
    /** How small the whole thing starts. A tooltip can afford more than a menu. */
    fromScale: Float = 0.9f,
    content: @Composable () -> Unit,
) {
    val host = LocalOverlayHost.current
    val density = LocalDensity.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val geometry = remember { ArrowPath() }

    val gapPx = with(density) { gap.roundToPx() }
    val marginPx = with(density) { margin.roundToPx() }
    val arrowWidthPx = with(density) { (arrow?.width ?: 0.dp).toPx() }
    val arrowHeightPx = with(density) { (arrow?.height ?: 0.dp).toPx() }
    val minWidthPx = with(density) {
        if (minWidth == Dp.Unspecified) 0 else minWidth.roundToPx()
    }

    // The appearance transform lives here rather than on the panel inside, for
    // two reasons. The arrow is drawn by *this* node, so a panel scaling in its
    // own layer came away from a pointer that stayed put. And this node fills the
    // host, which is the room `overlayAppearance` needs for the panel's shadow to
    // stay inside its compositing buffer.
    //
    // Origin is the anchor, so the panel and its arrow grow out of the control
    // they belong to rather than out of their own middle. Written during measure
    // and read at draw, which is the right order within a frame.
    var origin by remember { mutableStateOf(TransformOrigin.Center) }

    Layout(
        modifier = modifier
            .overlayAppearance(LocalOverlayProgress.current, fromScale, origin)
            .drawWithContent {
                drawContent()
                val path = geometry.path
                if (path != null && arrow != null) drawPath(path, arrow.color)
            },
        content = content,
    ) { measurables, constraints ->
        val container = IntSize(constraints.maxWidth, constraints.maxHeight)
        // The arrow takes up part of the gap, so the surface sits back far
        // enough for the tip to reach the anchor instead of overlapping it.
        val effectiveGap = gapPx + arrowHeightPx.toInt()

        val placeables = measurables.map {
            it.measure(overlayConstraints(container, marginPx, minWidthPx))
        }
        val contentSize = IntSize(
            placeables.maxOfOrNull { it.width } ?: 0,
            placeables.maxOfOrNull { it.height } ?: 0,
        )

        val anchorRect = anchorInRoot()
        val anchorInHost = (anchorRect ?: Rect.Zero).translate(-host.originInRoot)
        val placement = positionAnchored(
            anchor = anchorInHost,
            contentSize = contentSize,
            containerSize = container,
            side = side,
            alignment = alignment,
            gap = effectiveGap,
            margin = marginPx,
            isRtl = isRtl,
        )

        val width = container.width.orContent(contentSize.width).coerceAtLeast(1)
        val height = container.height.orContent(contentSize.height).coerceAtLeast(1)
        origin = TransformOrigin(
            pivotFractionX = (anchorInHost.center.x / width).coerceIn(0f, 1f),
            pivotFractionY = (anchorInHost.center.y / height).coerceIn(0f, 1f),
        )

        geometry.path = arrow?.let {
            arrowPath(
                placement = placement,
                contentSize = contentSize,
                anchor = anchorInHost,
                width = arrowWidthPx,
                height = arrowHeightPx,
            )
        }

        // Fill the container, except on an axis that has no size to fill —
        // laying out at `Constraints.Infinity` throws exactly like measuring at
        // it does. An unbounded axis takes the content's own extent instead.
        layout(width, height) {
            placeables.forEach { it.place(placement.x, placement.y) }
        }
    }
}

/**
 * The triangle, in host coordinates, with its base flush against the resolved
 * edge of the surface and its tip toward the anchor.
 *
 * Kept clear of the surface's corners by half the arrow's width, since an arrow
 * growing out of a rounded corner reads as a rendering fault rather than a
 * pointer.
 */
private fun arrowPath(
    placement: AnchoredPlacement,
    contentSize: IntSize,
    anchor: Rect,
    width: Float,
    height: Float,
): Path {
    val half = width / 2f
    val inset = width

    fun centre(target: Float, lo: Float, hi: Float): Float =
        if (hi < lo) (lo + hi) / 2f else target.coerceIn(lo, hi)

    return Path().apply {
        when (placement.side) {
            ResolvedSide.Below, ResolvedSide.Above -> {
                val cx = centre(
                    target = anchor.center.x,
                    lo = placement.x + inset,
                    hi = placement.x + contentSize.width - inset,
                )
                if (placement.side == ResolvedSide.Below) {
                    val base = placement.y.toFloat()
                    moveTo(cx, base - height)
                    lineTo(cx + half, base)
                    lineTo(cx - half, base)
                } else {
                    val base = (placement.y + contentSize.height).toFloat()
                    moveTo(cx, base + height)
                    lineTo(cx - half, base)
                    lineTo(cx + half, base)
                }
            }

            ResolvedSide.Right, ResolvedSide.Left -> {
                val cy = centre(
                    target = anchor.center.y,
                    lo = placement.y + inset,
                    hi = placement.y + contentSize.height - inset,
                )
                if (placement.side == ResolvedSide.Right) {
                    val base = placement.x.toFloat()
                    moveTo(base - height, cy)
                    lineTo(base, cy - half)
                    lineTo(base, cy + half)
                } else {
                    val base = (placement.x + contentSize.width).toFloat()
                    moveTo(base + height, cy)
                    lineTo(base, cy + half)
                    lineTo(base, cy - half)
                }
            }
        }
        close()
    }
}

/**
 * The raised panel that menus, popovers and pickers share.
 *
 * @param border Draws a hairline round the edge, which gives a light panel
 *   definition against a light ground. Pass `false` whenever the panel carries
 *   an [ArrowSpec]: the border would run straight across the arrow's base and
 *   sever it from the panel, turning a speech bubble into a panel with a torn
 *   corner. Arrow-bearing panels lean on the shadow instead.
 */
@Composable
internal fun OverlaySurface(
    modifier: Modifier = Modifier,
    shape: Shape = Theme.shapes.medium,
    color: Color = Theme.colors.surfaceRaised,
    contentColor: Color = Theme.colors.content,
    border: Boolean = true,
    /**
     * Hands the panel's settled width down to its content.
     *
     * A menu sizes itself from its rows' intrinsic width but holds a minimum,
     * so without this the rows come out narrower than the panel and the hover
     * wash and dividers stop short of its edge.
     */
    propagateMinConstraints: Boolean = false,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = shape,
        propagateMinConstraints = propagateMinConstraints,
        color = color,
        contentColor = contentColor,
        border = if (border) {
            BorderStroke(Theme.sizing.borderWidth, Theme.colors.outlineSubtle)
        } else {
            null
        },
        shadow = Theme.elevation.overlay,
        content = content,
    )
}

/** Records where the host sits, so root-space anchors can be made host-local. */
internal fun Modifier.trackHostOrigin(state: OverlayHostState): Modifier =
    onGloballyPositioned { state.originInRoot = it.positionInRoot() }
