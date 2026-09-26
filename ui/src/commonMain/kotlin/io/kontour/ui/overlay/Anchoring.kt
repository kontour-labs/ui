package io.kontour.ui.overlay

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
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
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.ui.adaptive.allEdges
import io.kontour.ui.foundation.Surface
import io.kontour.ui.theme.Theme
import kotlin.math.roundToInt

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

/** [side] with the layout direction applied. */
internal fun resolvedSide(side: OverlaySide, isRtl: Boolean): ResolvedSide = when (side) {
    OverlaySide.Top -> ResolvedSide.Above
    OverlaySide.Bottom -> ResolvedSide.Below
    OverlaySide.Start -> if (isRtl) ResolvedSide.Right else ResolvedSide.Left
    OverlaySide.End -> if (isRtl) ResolvedSide.Left else ResolvedSide.Right
}

/** The other candidate: the side an overlay flips to when this one has no room. */
internal val ResolvedSide.opposite: ResolvedSide
    get() = when (this) {
        ResolvedSide.Above -> ResolvedSide.Below
        ResolvedSide.Below -> ResolvedSide.Above
        ResolvedSide.Left -> ResolvedSide.Right
        ResolvedSide.Right -> ResolvedSide.Left
    }

/**
 * What an anchored overlay keeps clear of each container edge, beyond its margin.
 *
 * The container an overlay is measured in is the [OverlayHost], which fills the
 * window and applies **no insets at all** — deliberately, since the components that
 * need them apply their own. So without this, the room below an anchor near the
 * bottom of a phone included the navigation bar, and a popover asked to open below
 * such an anchor found "room" underneath the system's own chrome.
 *
 * Physical edges rather than start and end, because this is compared against
 * physical coordinates: `positionAnchored` works in the container's own space and
 * mirrors *alignment* for RTL, not the container.
 */
@Immutable
internal data class AnchorInsets(
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0,
) {
    internal companion object {
        /** No window chrome to avoid, which is every platform but a phone. */
        val None = AnchorInsets()
    }
}

/**
 * How much room there is beside [anchor] on [s], before any content is measured.
 *
 * **Independent of the content**, which is the property the whole fix rests on: it
 * can be worked out *before* measuring, so the content can be given the better
 * side's room as its budget rather than the whole container's and then be moved to
 * make it fit.
 */
internal fun roomBeside(
    anchor: Rect,
    containerSize: IntSize,
    s: ResolvedSide,
    gap: Int,
    margin: Int,
    insets: AnchorInsets = AnchorInsets.None,
): Int = when (s) {
    ResolvedSide.Above -> (anchor.top - margin - insets.top - gap).toInt()
    ResolvedSide.Below ->
        (containerSize.height - margin - insets.bottom - anchor.bottom - gap).toInt()
    ResolvedSide.Left -> (anchor.left - margin - insets.left - gap).toInt()
    ResolvedSide.Right ->
        (containerSize.width - margin - insets.right - anchor.right - gap).toInt()
}

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
 *    container, keeping [margin] and [insets] clear of the edges.
 *
 * Shifting is deliberately not constrained by the anchor: a menu aligned to the
 * start of a button in the far corner slides until it fits, ending up no longer
 * aligned with that button. That is correct. Alignment is a preference; being on
 * screen is not.
 *
 * ### What fixes "Bottom did not mean bottom"
 *
 * Reported from a phone: *"setting it to 'bottom' on android doesn't seem to make
 * it not show on the top side"*. Two mechanisms can do that and only one of them
 * is the documented flip.
 *
 * The other is this function's clamp, reached through a content size that was
 * measured against the wrong thing. `shift` bounds the overlay to the container,
 * and on the side's *own* axis that bound can be tighter than the anchor: with a
 * container 800 tall, an anchor bottom at 400 and content 600 high, both sides have
 * 381 of room, neither fits, `resolved` stays `Below` — and then `y` is clamped from
 * 411 down to 192, which is above `anchor.top`. The panel is drawn entirely above
 * its anchor while reporting `Below`, so `arrowPath` puts the pointer on the panel's
 * top edge, aimed away from the control it belongs to.
 *
 * **The fix is upstream of this function**: `AnchoredOverlayLayout` budgets the
 * content to `roomBeside` the better side *before* measuring it, so content that can
 * fit does fit, and the clamp has nothing left to do. See [Constraints.withinSideRoom].
 *
 * Guaranteeing it here as well — a third correction pushing the overlay back onto
 * its side whatever the shift decided — was tried and reverted, and the reason is
 * worth keeping. An anchor can be the whole container: a `DropdownMenu` inside a
 * `Box(Modifier.fillMaxSize())` has no room on any side, gets no budget, and would
 * then be pinned to `anchor.bottom + gap` — off the bottom of the window, with its
 * items unreachable. A panel that overlaps its anchor is readable and a panel that
 * is off screen is not, so when nothing fits, being on screen wins.
 *
 * @param gap Distance between the anchor and the overlay.
 * @param margin Minimum distance from the container's edges.
 * @param insets What the container's own edges are occupied by — a status bar, a
 *   navigation bar, a keyboard — on top of [margin]. See [AnchorInsets].
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
    insets: AnchorInsets = AnchorInsets.None,
    /** A side to stay on whatever the room says — an overlay on its way out. */
    keepSide: ResolvedSide? = null,
): AnchoredPlacement {
    val preferred = resolvedSide(side, isRtl)
    val opposite = preferred.opposite

    fun roomOn(s: ResolvedSide): Int =
        roomBeside(anchor, containerSize, s, gap, margin, insets)

    fun needsOn(s: ResolvedSide): Int = when (s) {
        ResolvedSide.Above, ResolvedSide.Below -> contentSize.height
        ResolvedSide.Left, ResolvedSide.Right -> contentSize.width
    }

    val resolved = when {
        keepSide != null -> keepSide
        roomOn(preferred) >= needsOn(preferred) -> preferred
        roomOn(opposite) >= needsOn(opposite) -> opposite
        roomOn(opposite) > roomOn(preferred) -> opposite
        else -> preferred
    }

    fun shift(value: Int, size: Int, extent: Int, lead: Int, trail: Int): Int {
        val max = extent - trail - size
        // A container too small to hold the content at all: pin to the leading
        // edge rather than letting the clamp invert and push it off-screen.
        if (max < lead) return lead
        return value.coerceIn(lead, max)
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
                x = shift(
                    value = leading.toInt(),
                    size = contentSize.width,
                    extent = containerSize.width,
                    lead = margin + insets.left,
                    trail = margin + insets.right,
                ),
                y = shift(
                    value = y.toInt(),
                    size = contentSize.height,
                    extent = containerSize.height,
                    lead = margin + insets.top,
                    trail = margin + insets.bottom,
                ),
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
                x = shift(
                    value = x.toInt(),
                    size = contentSize.width,
                    extent = containerSize.width,
                    lead = margin + insets.left,
                    trail = margin + insets.right,
                ),
                y = shift(
                    value = top.toInt(),
                    size = contentSize.height,
                    extent = containerSize.height,
                    lead = margin + insets.top,
                    trail = margin + insets.bottom,
                ),
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
    insets: AnchorInsets = AnchorInsets.None,
): Constraints {
    val maxWidth = container.width.lessEdges(margin * 2 + insets.left + insets.right)
    val maxHeight = container.height.lessEdges(margin * 2 + insets.top + insets.bottom)
    return Constraints(
        minWidth = if (maxWidth == Constraints.Infinity) minWidth else minWidth.coerceIn(0, maxWidth),
        maxWidth = maxWidth,
        maxHeight = maxHeight,
    )
}

/**
 * [overlayConstraints], further bounded to the room the overlay will actually be
 * placed in.
 *
 * **This is the half of the popover fix that stops the problem happening**, rather
 * than the half that stops it being drawn wrongly. Content used to be measured
 * against the whole container less its margins, and then placed beside an anchor
 * that might have a fraction of that beside it. A tall panel therefore reported
 * "does not fit below" whatever was actually below it, went through the flip, found
 * the other side did not fit either, and was clamped — which on a phone, where the
 * window is half a desktop's height and every touch target is twice as tall, is the
 * common case rather than the corner one.
 *
 * [room] is `roomBeside` the better of the two candidate sides, which is knowable
 * without the content. Anything the content does within that budget fits on the
 * side it is given, so the flip decides on real numbers and the clamp never bites.
 *
 * **The panel has to be able to scroll**, or this loses content instead of placing
 * it. That is not a caveat, it is the other half: the first version of this bounded
 * the panel and nothing else, and what a reader got was a popover with its last
 * lines cut off — reported in those words. `MenuPanel` already scrolled;
 * `PopoverPanel` does now.
 *
 * A [room] of zero or less means the caller found no side worth budgeting to, and
 * the container's own bound stands — which is what this did before any of it.
 * `positionAnchored`'s clamp then keeps the overlay on screen, and being readable on
 * top of its anchor beats being correctly placed and a sliver. See
 * [AnchoredOverlayDefaults.MinimumPanel] for where that line is drawn.
 */
internal fun Constraints.withinSideRoom(room: Int, vertical: Boolean): Constraints {
    if (room <= 0) return this
    return if (vertical) {
        if (!hasBoundedHeight) return this
        copy(maxHeight = maxHeight.coerceAtMost(room).coerceAtLeast(minHeight))
    } else {
        if (!hasBoundedWidth) return this
        // `minWidth` wins where the two disagree: a select's menu matching the
        // width of its field is a promise this must not quietly break, and
        // `Constraints` throws rather than clamping if it did.
        copy(maxWidth = maxWidth.coerceAtMost(room).coerceAtLeast(minWidth))
    }
}

/** Numbers every anchored overlay shares. */
internal object AnchoredOverlayDefaults {
    /**
     * How little room a side may have and still be the side the overlay opens on.
     *
     * **This is the whole of "`Bottom` should mean bottom".** Measured on the
     * popover demo at a Pixel's size: the panel is about 88dp and a trigger 130dp
     * from the bottom edge has about 90dp under it, which would be enough — except
     * that the margin, the gap and the arrow want twenty more than there are. So it
     * flipped, for the sake of twenty pixels, and was reported as `Bottom` not
     * meaning bottom.
     *
     * With the panel bounded to its side and able to scroll, that case opens below
     * and scrolls the last sliver, which is what was asked for. What the floor is
     * still for is the case where there is no side at all: a `DropdownMenu` declared
     * inside a `Box(Modifier.fillMaxSize().padding(24.dp))` leaves *twelve pixels*
     * beside it, and a panel bounded to twelve pixels is a strip with its rows
     * unreachable. Two of the library's own menu tests found that within a minute.
     *
     * 64dp is a panel's own vertical padding and a line of text — the point below
     * which there is nothing a reader could act on, whether or not it scrolls.
     */
    val MinimumPanel: Dp = 64.dp
}

/** This extent less what the edges take, or [Constraints.Infinity] if it had none. */
private fun Int.lessEdges(edges: Int): Int =
    if (this == Constraints.Infinity) Constraints.Infinity else (this - edges).coerceAtLeast(0)

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
 * Reads [bounds] in a scope of its own and hands the result to [content].
 *
 * An anchor's bounds change on every frame its trigger scrolls, and the
 * composable that owns the trigger is the wrong place to read them: it
 * recomposes, every frame, for the sake of a menu that is usually closed. Read
 * here, only this and the overlay it wraps do — in the same pass, the same
 * frame, with the same value.
 */
@Composable
internal fun WithAnchor(bounds: () -> Rect?, content: @Composable (Rect?) -> Unit) {
    content(bounds())
}

/**
 * Captures the bounds of this composable's *parent*, in root coordinates.
 *
 * Lets a dropdown be declared next to the control it belongs to and still know
 * where that control is:
 *
 * ```
 * Box {
 *     Button(onClick = { visible = true }) { +"Sort" }
 *     DropdownMenu(visible, onDismissRequest = { visible = false }) { … }
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

/**
 * How far an arrow's base is buried in the panel it points from.
 *
 * Enough to swallow two antialiased edges at any scale the appearance animation
 * passes through, small enough that a panel one dp shorter would look identical —
 * see [arrowPath].
 */
private val ArrowOverlap: Dp = 1.dp

/**
 * The window insets an anchored overlay keeps clear of, when they are not the
 * window's own — which is only ever a test: a desktop scene has no status bar or
 * home indicator to report, and the cases worth testing are the ones a phone has.
 */
internal val LocalOverlaySafeArea = compositionLocalOf<WindowInsets?> { null }

/** A pointer showing which element an overlay belongs to. */
@Immutable
data class ArrowSpec(
    val colour: Color,
    val width: Dp = 14.dp,
    val height: Dp = 7.dp,
)

/** Carries the arrow geometry from the measure pass to the draw pass. */
private class ArrowPath {
    var path: Path? = null
}

/**
 * The last anchor the overlay was actually placed against.
 *
 * A caller whose anchor *is* the state that opens the overlay — `ContextMenuArea`
 * holds a nullable `Rect` and clears it to dismiss — leaves this layout with
 * nothing to measure against for the whole of the exit animation, and
 * `Rect.Zero` is the window's top-left corner. So the menu spent its exit
 * sliding to the corner of the screen.
 *
 * Not snapshot state: this is read during measure, and a `mutableStateOf`
 * written in the pass that reads it invalidates that pass. Same reason
 * [ArrowPath] is a plain holder.
 */
private class AnchorMemory {
    var last: Rect? = null
}

/**
 * The side an overlay was last placed on while it was showing, and what it was
 * measured against there — held so it can leave from where it was.
 *
 * Plain rather than snapshot state, for [AnchorMemory]'s reason: written in the
 * measure pass that reads it.
 */
private class PlacedSide {
    var side: ResolvedSide? = null
    var constraints: Constraints? = null
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
    /**
     * The least room a side may have and still be the side it opens on — capped,
     * on the axis the panel opens along, at the panel's own natural size, so a
     * panel that fits whole in less still opens where it was asked to. See
     * [AnchoredOverlayDefaults.MinimumPanel] for the default and a menu's
     * `MenuMinimumRoom` for why a list wants more.
     */
    minimumPanel: Dp = AnchoredOverlayDefaults.MinimumPanel,
    content: @Composable () -> Unit,
) {
    val host = LocalOverlayHost.current
    val density = LocalDensity.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val geometry = remember { ArrowPath() }
    val anchorMemory = remember { AnchorMemory() }
    val placedSide = remember { PlacedSide() }
    val leaving = LocalOverlayLeaving.current
    // The host applies no insets of its own, so without this the room "below" an
    // anchor near the bottom of a phone includes the navigation bar. The keyboard
    // is in there for the same reason a dialog includes it: a popover is as likely
    // to hold a text field.
    //
    // Held as the `WindowInsets` rather than resolved here, so the pixels are read
    // in the measure pass and an animating keyboard re-places the overlay instead of
    // recomposing it.
    val safeArea = LocalOverlaySafeArea.current ?: WindowInsets.allEdges

    val gapPx = with(density) { gap.roundToPx() }
    val marginPx = with(density) { margin.roundToPx() }
    val arrowWidthPx = with(density) { (arrow?.width ?: 0.dp).toPx() }
    val arrowHeightPx = with(density) { (arrow?.height ?: 0.dp).toPx() }
    val arrowOverlapPx = with(density) { ArrowOverlap.toPx() }
    val minWidthPx = with(density) {
        if (minWidth == Dp.Unspecified) 0 else minWidth.roundToPx()
    }
    val minimumPanelPx = with(density) { minimumPanel.roundToPx() }
    val defaultPanelPx = with(density) { AnchoredOverlayDefaults.MinimumPanel.roundToPx() }

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
                if (path != null && arrow != null) drawPath(path, arrow.colour)
            },
        content = content,
    ) { measurables, constraints ->
        val container = IntSize(constraints.maxWidth, constraints.maxHeight)
        // The arrow takes up part of the gap, so the surface sits back far
        // enough for the tip to reach the anchor instead of overlapping it.
        val effectiveGap = gapPx + arrowHeightPx.toInt()
        // **Only the part of each inset that reaches into this host.** The insets
        // are the window's, and a host is not always the window: the catalog's
        // stages, a card with its own host, a pane. Taking a phone's 47dp status
        // bar and 34dp home indicator off the edges of a 260dp card half-way down
        // the page left less than the 64dp floor on either side of an anchor in
        // its middle, and the panel was shifted back up over the control it was
        // meant to point at — reported as the popover appearing in the wrong
        // place. An edge that is further from the window's than the inset is deep
        // has nothing to keep clear of.
        val fromRoot = host.edgesFromRoot
        val insets = AnchorInsets(
            left = (safeArea.getLeft(this, layoutDirection) - fromRoot.left).coerceAtLeast(0),
            top = (safeArea.getTop(this) - fromRoot.top).coerceAtLeast(0),
            right = (safeArea.getRight(this, layoutDirection) - fromRoot.right).coerceAtLeast(0),
            bottom = (safeArea.getBottom(this) - fromRoot.bottom).coerceAtLeast(0),
        )

        // **The anchor is read before the content is measured**, which is the whole
        // of the popover fix. The room beside an anchor does not depend on the
        // content, so it can be a budget — and a budget is the difference between
        // "measure against the window and then find somewhere to put it" and
        // "measure against the space it is going in".
        //
        // Falls back to the last real anchor rather than to the origin — see
        // `AnchorMemory`.
        val anchorRect = anchorInRoot()?.also { anchorMemory.last = it } ?: anchorMemory.last
        val anchorInHost = (anchorRect ?: Rect.Zero).translate(-host.originInRoot)

        val preferred = resolvedSide(side, isRtl)
        val vertical = preferred == ResolvedSide.Above || preferred == ResolvedSide.Below

        // **The side that was asked for, if it is a side at all.**
        //
        // Budgeting to the *better* of the two was the first version, and it left
        // `side` as weak as it was: a panel sized to the roomier side still does not
        // fit the preferred one, so it still flips. Taking the preferred side's own
        // room is what makes the preference hold — the panel is bounded to it, fits
        // there by construction, and scrolls whatever did not.
        //
        // The fallbacks are in order of how much they give up: the opposite side, and
        // then nothing at all, which is the container's own bound and the behaviour
        // this has always had.
        // A floor above the default is a caller asking for *enough to use*, and
        // enough to use can never be more than the whole panel: a two-item menu
        // that fits in the room below opens below, whatever the floor says. Read
        // as an intrinsic, before measuring, because the room it decides is what
        // the panel is measured against — and only when it can matter, since
        // intrinsics are a second walk of the content.
        val floor = if (minimumPanelPx > defaultPanelPx) {
            val natural = measurables.maxOfOrNull { measurable ->
                val across = overlayConstraints(container, marginPx, minWidthPx, insets)
                if (vertical) {
                    measurable.maxIntrinsicHeight(across.maxWidth)
                } else {
                    measurable.maxIntrinsicWidth(across.maxHeight)
                }
            } ?: 0
            minOf(minimumPanelPx, natural).coerceAtLeast(defaultPanelPx)
        } else {
            minimumPanelPx
        }
        val preferredRoom =
            roomBeside(anchorInHost, container, preferred, effectiveGap, marginPx, insets)
        val oppositeRoom =
            roomBeside(anchorInHost, container, preferred.opposite, effectiveGap, marginPx, insets)
        val room = when {
            preferredRoom >= floor -> preferredRoom
            oppositeRoom >= floor -> oppositeRoom
            else -> 0
        }

        // **Leaving from where it was.** Reported on a combobox: it opens above the
        // field when the keyboard leaves no room below, and tapping elsewhere closes
        // the keyboard and the menu together — so for the length of the menu's exit
        // the room below came back, the flip undid itself, and the menu jumped back
        // under the field on its way out. Once an overlay is leaving it keeps the
        // side it was on and the size it had there, and still follows its anchor,
        // which may be moving as the keyboard goes.
        val keptSide = if (leaving()) placedSide.side else null
        val measuredAgainst = placedSide.constraints.takeIf { keptSide != null }
            ?: overlayConstraints(container, marginPx, minWidthPx, insets).withinSideRoom(room, vertical)
        val placeables = measurables.map { it.measure(measuredAgainst) }
        val contentSize = IntSize(
            placeables.maxOfOrNull { it.width } ?: 0,
            placeables.maxOfOrNull { it.height } ?: 0,
        )

        val placement = positionAnchored(
            anchor = anchorInHost,
            contentSize = contentSize,
            containerSize = container,
            side = side,
            alignment = alignment,
            gap = effectiveGap,
            margin = marginPx,
            isRtl = isRtl,
            insets = insets,
            keepSide = keptSide,
        )
        if (keptSide == null) {
            placedSide.side = placement.side
            placedSide.constraints = measuredAgainst
        }

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
                overlap = arrowOverlapPx,
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
 * The triangle, in host coordinates, with its base buried just inside the
 * resolved edge of the surface and its tip toward the anchor.
 *
 * Kept clear of the surface's corners by half the arrow's width, since an arrow
 * growing out of a rounded corner reads as a rendering fault rather than a
 * pointer.
 *
 * ### Why the base sits *inside* the panel rather than against it
 *
 * A base exactly on the panel's edge puts two antialiased edges on the same line
 * with nothing behind them. Where that line falls on a fractional pixel, neither
 * shape covers it fully and the two half-covered edges composite to something
 * paler than either — a hairline of background between the arrow and the panel it
 * belongs to.
 *
 * Static, that is a faint line nobody mentions. Animated it is worse than it
 * sounds: `overlayAppearance` scales the whole thing, so the seam sweeps through
 * fractional positions and blinks on and off, and the arrow reads as coming away
 * from its bubble. Reported against the exit rather than the entry because the
 * `exit` easing holds opacity high while the scale is already visibly small,
 * while the entry spring is through that range in a frame or two at low alpha.
 *
 * [overlap] carries the base past the edge so the two shapes share coverage
 * instead of meeting at it. The tip does not move and the arrow is drawn over the
 * panel in the same colour, so nothing about the silhouette changes.
 */
private fun arrowPath(
    placement: AnchoredPlacement,
    contentSize: IntSize,
    anchor: Rect,
    width: Float,
    height: Float,
    overlap: Float,
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
                    val base = placement.y.toFloat() + overlap
                    moveTo(cx, placement.y.toFloat() - height)
                    lineTo(cx + half, base)
                    lineTo(cx - half, base)
                } else {
                    val edge = (placement.y + contentSize.height).toFloat()
                    val base = edge - overlap
                    moveTo(cx, edge + height)
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
                    val base = placement.x.toFloat() + overlap
                    moveTo(placement.x.toFloat() - height, cy)
                    lineTo(base, cy - half)
                    lineTo(base, cy + half)
                } else {
                    val edge = (placement.x + contentSize.width).toFloat()
                    val base = edge - overlap
                    moveTo(edge + height, cy)
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
    shape: Shape = Theme.shapes.container,
    colour: Color = Theme.colours.surfaceRaised,
    contentColour: Color = Theme.colours.content,
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
        colour = colour,
        contentColour = contentColour,
        border = if (border) {
            BorderStroke(Theme.sizing.borderWidth, Theme.colours.outlineSubtle)
        } else {
            null
        },
        shadow = Theme.elevation.overlay,
        content = content,
    )
}

/**
 * Records where the host sits, so root-space anchors can be made host-local and
 * window insets host-sized.
 */
internal fun Modifier.trackHostOrigin(state: OverlayHostState): Modifier =
    onGloballyPositioned { coordinates ->
        val origin = coordinates.positionInRoot()
        state.originInRoot = origin
        val root = coordinates.findRootCoordinates().size
        state.edgesFromRoot = AnchorInsets(
            left = origin.x.roundToInt().coerceAtLeast(0),
            top = origin.y.roundToInt().coerceAtLeast(0),
            right = (root.width - origin.x - coordinates.size.width).roundToInt().coerceAtLeast(0),
            bottom = (root.height - origin.y - coordinates.size.height).roundToInt().coerceAtLeast(0),
        )
    }
