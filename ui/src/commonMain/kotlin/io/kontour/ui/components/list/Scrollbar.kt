package io.kontour.ui.components.list

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.kontour.ui.a11y.highContrast
import io.kontour.ui.input.Cursor
import io.kontour.ui.input.InputModality
import io.kontour.ui.input.LocalInputModality
import io.kontour.ui.input.pointerCursor
import io.kontour.ui.input.pointerIconFor
import io.kontour.ui.theme.Theme
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

/** How much of the track the thumb covers, and where it sits. 0 to 1. */
@Immutable
internal data class ScrollbarGeometry(
    val fraction: Float,
    val position: Float,
    /**
     * How far the container can still travel, in **content** pixels.
     *
     * The other two are proportions of the track; this is the number that turns
     * a distance dragged along the track into a distance to scroll, and it is
     * here because only the code that built the other two knows it. Zero when
     * there is nothing to scroll, which is also the degenerate a caller that
     * omits it gets.
     */
    val scrollable: Float = 0f,
) {
    val isUseful: Boolean get() = fraction < 1f && fraction > 0f
}

/** What a [Scrollbar] and [Modifier.scrollbar] take by default. */
object ScrollbarDefaults {
    val Thickness: Dp
        @Composable @ReadOnlyComposable get() = Theme.componentDefaults.scrollbarThickness
    val HoveredThickness: Dp
        @Composable @ReadOnlyComposable get() = Theme.componentDefaults.scrollbarHoveredThickness
    val MinThumbLength: Dp = 32.dp

    /**
     * How thick the bar is under touch, where it is an indicator rather than a
     * handle: thinner than a pointer's, as every phone's is.
     */
    val TouchThickness: Dp
        @Composable @ReadOnlyComposable get() = Theme.componentDefaults.scrollbarTouchThickness

    /**
     * A container's corner as a length, for [Scrollbar]'s `cornerInset`.
     *
     * `Shape` says nothing about corners, and a `CornerSize` cannot be resolved
     * without a size — but the corners that matter here are fixed `Dp`, and a
     * fixed corner ignores the size it is handed. So `Size.Zero` is enough to
     * read one, and anything proportional answers zero, which is the safe
     * degenerate: a track that clears nothing, exactly as before this existed.
     *
     * Trailing rather than leading, because that is the edge a vertical
     * scrollbar sits on and the two are equal on every shape in the scale.
     *
     * Lives here rather than beside a caller because both callers want the same
     * answer: a menu's panel and a documentation page's code block are the same
     * problem, and a scrollbar cannot work this out for itself — it measures
     * itself, not the container around it.
     */
    @Composable
    fun cornerInset(shape: Shape): Dp {
        val density = LocalDensity.current
        return remember(shape, density) {
            val corners = shape as? CornerBasedShape ?: return@remember 0.dp
            with(density) { corners.topEnd.toPx(Size.Zero, density).toDp() }
        }
    }
}

/**
 * Where a scroller is in its content, and a handle to move it by.
 *
 * ```kotlin
 * LazyColumn(Modifier.scrollbar(listState), state = listState) { … }
 * ```
 *
 * Usually written as [Modifier.scrollbar], on the scroller itself, which needs
 * nothing round it. This is the same bar as a component of its own, for a bar
 * that is not on the scroller's edge — beside it, or over a region the scroller
 * does not fill:
 *
 * ```kotlin
 * Box {
 *     LazyColumn(state = listState) { … }
 *     Scrollbar(listState, Modifier.align(Alignment.CenterEnd))
 * }
 * ```
 *
 * Either way it reads any [ScrollableState] that can say where it is — a
 * `ScrollState`, a lazy list, grid or staggered grid, a pager — across or down
 * as [orientation] says.
 *
 * **How it shows depends on the hand.**
 *
 * | | Touch, stylus | Mouse, keyboard |
 * |---|---|---|
 * | At rest | not drawn | drawn, half-faded |
 * | While scrolling | drawn, fading out 600ms after it stops | drawn |
 * | Dragged | no: the scroller's own | yes |
 *
 * Under touch it is an indicator, as every phone's is: it appears as the
 * content moves, says where it is and how much there is, and fades once it has
 * stopped. It takes no touches — a strip at the edge of a list that took them
 * would be a strip of the list that did not scroll. With a mouse or a keyboard
 * it is the bar every other window on the machine has, there all the time, and
 * a long list without one reads as broken. [alwaysVisible] draws it at rest
 * under touch too, for a scroller whose length is the point.
 *
 * **Dragging it scrolls the container**, at the rate the track's length says:
 * grab it anywhere and the content follows, rather than jumping to where it was
 * pressed. Raw rather than a fling — a scrollbar is a position control, and
 * letting go of one leaves the content where the thumb is.
 *
 * It is hidden from the accessibility tree: it conveys nothing a screen reader
 * cannot already get from the scroller itself, and the drag is not an action
 * anyone can take without a pointer.
 *
 * @param cornerInset How far the container's rounded corner intrudes on the
 *   track, taken off both ends. A scrollbar cannot discover this — inside a menu
 *   or a dialog the track otherwise runs the full height and its ends disappear
 *   behind the curve. [ScrollbarDefaults.cornerInset] reads it from a shape.
 *
 *   A `Dp` rather than the container's shape, because a corner size needs a size
 *   to resolve against. Every rounded host in the library uses a fixed-`Dp`
 *   corner — `container` is 22dp, `panel` 28 — so a length is exact for all of
 *   them.
 * @param alwaysVisible Drawn at rest under touch as well, half-faded as with a
 *   mouse. Not draggable by a finger even so: the scroller keeps its touches.
 */
@Composable
fun Scrollbar(
    state: ScrollableState,
    modifier: Modifier = Modifier,
    orientation: Orientation = Orientation.Vertical,
    colour: Color = Theme.colours.outlineStrong,
    thickness: Dp = ScrollbarDefaults.Thickness,
    hoveredThickness: Dp = ScrollbarDefaults.HoveredThickness,
    minThumbLength: Dp = ScrollbarDefaults.MinThumbLength,
    cornerInset: Dp = 0.dp,
    alwaysVisible: Boolean = false,
) {
    val pointer = scrollbarTakesAPointer()
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    val dragged by interactions.collectIsDraggedAsState()
    // A drag that has wandered off the bar is still a drag on it, and a
    // scrollbar that thinned out halfway through one would be reporting that
    // the grip had been lost.
    val active = pointer && (hovered || dragged)

    // Everything but the position, which changes on every frame of a scroll and
    // is read where it is used — in the thumb's placement. Composition only
    // needs what decides the thumb's size, and that holds still while a list of
    // even rows scrolls, so the bar does not recompose once a frame for it.
    val extent by remember(state) {
        derivedStateOf { scrollbarGeometry(state).copy(position = 0f) }
    }
    if (!extent.isUseful) return

    val look = rememberScrollbarLook(state, pointer, alwaysVisible, active, thickness, hoveredThickness)
    val width by look.width
    val alpha by look.alpha

    var trackLength by remember { mutableFloatStateOf(0f) }
    val minThumbPx = with(LocalDensity.current) {
        minThumbLength.toPx()
    }
    val thumbLength = (trackLength * extent.fraction).coerceAtLeast(minThumbPx)
    val travel = (trackLength - thumbLength).coerceAtLeast(0f)

    // A pixel along the track is `scrollable / travel` pixels of content: the
    // thumb crosses its whole travel exactly as the list crosses all of its.
    // Read live rather than captured — `rememberDraggableState` keeps the
    // current lambda, and both numbers change as the list is scrolled.
    val drag = rememberDraggableState { delta ->
        if (travel > 0f) state.dispatchRawDelta(delta * extent.scrollable / travel)
    }

    Box(
        modifier = modifier
            // Nothing to announce: the list already conveys its own position.
            .clearAndSetSemantics {}
            .then(
                if (pointer) {
                    Modifier
                        // Held rather than merely pressed, so the thumb looks
                        // taken hold of for the whole drag.
                        .pointerCursor(if (dragged) Cursor.Grabbing else Cursor.Grab)
                        .hoverable(interactions)
                        // The whole bar, not the thumb. Grabbing a thumb means
                        // hitting a 32dp-tall target inside a 6dp-wide one;
                        // grabbing the bar means hitting the bar.
                        .draggable(
                            state = drag,
                            orientation = orientation,
                            // A horizontal list runs from the right in right to
                            // left, and so does its thumb: dragging it left is
                            // dragging it forward.
                            reverseDirection = orientation == Orientation.Horizontal && LocalLayoutDirection.current == LayoutDirection.Rtl,
                            interactionSource = interactions,
                        )
                } else {
                    // Under touch: nothing that takes a pointer at all, so the
                    // strip the bar stands in is still the scroller's.
                    Modifier
                }
            )
            .then(
                if (orientation == Orientation.Vertical) {
                    Modifier.fillMaxHeight().width(hoveredThickness)
                } else {
                    Modifier.fillMaxWidth().height(hoveredThickness)
                }
            )
            .padding(Theme.spacing.xxs)
            // Clear the container's corner, if it has one. Applied here rather
            // than in the arithmetic above: `onSizeChanged` reports whatever is
            // left after the padding, so the track length, the thumb length, its
            // travel and its origin all follow from this one modifier.
            .then(
                if (orientation == Orientation.Vertical) {
                    Modifier.padding(vertical = cornerInset)
                } else {
                    Modifier.padding(horizontal = cornerInset)
                }
            )
            .onSizeChanged {
                trackLength = if (orientation == Orientation.Vertical) {
                    it.height.toFloat()
                } else {
                    it.width.toFloat()
                }
            }
    ) {
        if (alpha > 0f) Box(
            Modifier
                .offset {
                    val thumbOffset = travel * scrollbarGeometry(state).position
                    if (orientation == Orientation.Vertical) {
                        IntOffset(0, thumbOffset.roundToInt())
                    } else {
                        IntOffset(thumbOffset.roundToInt(), 0)
                    }
                }
                .then(
                    if (orientation == Orientation.Vertical) {
                        Modifier
                            .width(width)
                            .height(with(LocalDensity.current) {
                                thumbLength.toDp()
                            })
                    } else {
                        Modifier
                            .height(width)
                            .width(with(LocalDensity.current) {
                                thumbLength.toDp()
                            })
                    }
                )
                .background(colour.copy(alpha = alpha), Theme.shapes.pill)
        )
    }
}

/**
 * The same bar as [Scrollbar], drawn on the scroller itself: over its content,
 * at its trailing edge, or along its foot when [orientation] is horizontal.
 *
 * ```kotlin
 * LazyColumn(Modifier.scrollbar(listState), state = listState) { … }
 *
 * Column(Modifier.scrollbar(scroll).verticalScroll(scroll)) { … }
 *
 * LazyRow(Modifier.scrollbar(rowState, Orientation.Horizontal), state = rowState) { … }
 * ```
 *
 * Nothing to wrap it in and nothing to align: it is on the scroller's edge
 * because it is on the scroller, as `fadingEdges` is.
 *
 * **Before `verticalScroll` or `horizontalScroll`** in the chain, not after: a
 * modifier after the scroll is inside the scrolled content, and the bar would
 * scroll away with it. And **before `fadingEdges`**, or the fade takes the bar
 * with the content it fades.
 *
 * Drawn and shown exactly as [Scrollbar] is — see it for the table. With a mouse
 * it is dragged from a strip [ScrollbarDefaults.HoveredThickness] wide at the
 * edge, which thickens under the pointer; a press anywhere else is the
 * content's. Under touch it takes nothing.
 */
@Composable
fun Modifier.scrollbar(
    state: ScrollableState,
    orientation: Orientation = Orientation.Vertical,
    colour: Color = Theme.colours.outlineStrong,
    thickness: Dp = ScrollbarDefaults.Thickness,
    hoveredThickness: Dp = ScrollbarDefaults.HoveredThickness,
    minThumbLength: Dp = ScrollbarDefaults.MinThumbLength,
    cornerInset: Dp = 0.dp,
    alwaysVisible: Boolean = false,
): Modifier {
    val pointer = scrollbarTakesAPointer()
    val grip = remember { ScrollbarGrip() }
    val active = pointer && (grip.hovered || grip.dragging)
    val look = rememberScrollbarLook(state, pointer, alwaysVisible, active, thickness, hoveredThickness)
    val density = LocalDensity.current
    val margin = with(density) { Theme.spacing.xxs.toPx() }
    val strip = with(density) { hoveredThickness.toPx() } + 2 * margin
    val minThumb = with(density) { minThumbLength.toPx() }
    val corner = with(density) { cornerInset.toPx() }
    val track = remember { ScrollbarTrack() }
    track.update(orientation, LocalLayoutDirection.current, margin, strip, minThumb, corner)

    return this
        .then(
            if (pointer) {
                // There all along and the arrow, overriding nothing, until the
                // pointer is on the bar: Compose asks the nodes under a press
                // at the moment it goes down. See `heldCursor`.
                Modifier
                    .pointerHoverIcon(
                        icon = pointerIconFor(
                            when {
                                grip.dragging -> Cursor.Grabbing
                                grip.hovered -> Cursor.Grab
                                else -> Cursor.Default
                            }
                        ),
                        overrideDescendants = grip.hovered || grip.dragging,
                    )
                    .pointerInput(state, track) { gripBar(state, track, grip) }
            } else {
                Modifier
            }
        )
        .drawWithContent {
            drawContent()
            val alpha = look.alpha.value
            if (alpha <= 0f) return@drawWithContent
            val geometry = scrollbarGeometry(state)
            if (!geometry.isUseful) return@drawWithContent
            val thumb = track.thumb(size, layoutDirection, geometry, look.width.value.toPx()) ?: return@drawWithContent
            drawRoundRect(
                color = colour.copy(alpha = colour.alpha * alpha),
                topLeft = thumb.topLeft,
                size = thumb.size,
                cornerRadius = CornerRadius(minOf(thumb.width, thumb.height) / 2f),
            )
        }
}

/**
 * Whether the bar is for a pointer — drawn at rest and dragged — or an
 * indicator under a finger. A keyboard counts as a pointer here: it is a desk,
 * with a mouse or a trackpad beside it, and the bar was documented as there for
 * it all along while drawing nothing.
 */
@Composable
@ReadOnlyComposable
private fun scrollbarTakesAPointer(): Boolean = when (LocalInputModality.current) {
    InputModality.Mouse, InputModality.Keyboard -> true
    InputModality.Touch, InputModality.Stylus -> false
}

/** How the bar looks now: how wide its thumb is and how strongly it is drawn. Read where they are used. */
private class ScrollbarLook(val width: State<Dp>, val alpha: State<Float>)

@Composable
private fun rememberScrollbarLook(
    state: ScrollableState,
    pointer: Boolean,
    alwaysVisible: Boolean,
    active: Boolean,
    thickness: Dp,
    hoveredThickness: Dp,
): ScrollbarLook {
    val motion = Theme.motion
    val shown = pointer || alwaysVisible || rememberShownWhileScrolling(state)
    val alpha = animateFloatAsState(
        targetValue = when {
            !shown -> 0f
            // Half-faded at rest, except at the enhanced tier, where a thumb
            // the reader has to find is not the thing to make fainter.
            active || highContrast() -> 1f
            else -> 0.5f
        },
        animationSpec = if (shown) motion.tweenFast() else motion.tweenDefault(),
        label = "scrollbarAlpha",
    )
    val width = animateDpAsState(
        targetValue = when {
            !pointer -> ScrollbarDefaults.TouchThickness
            active -> hoveredThickness
            else -> thickness
        },
        animationSpec = motion.tweenFast(),
        label = "scrollbarThickness",
    )
    return remember(alpha, width) { ScrollbarLook(width, alpha) }
}

/**
 * Whether a touch scroller's bar is up: while it scrolls, and for
 * [LingerTime] after.
 */
@Composable
private fun rememberShownWhileScrolling(state: ScrollableState): Boolean {
    val scrolling = state.isScrollInProgress
    var lingering by remember { mutableStateOf(false) }
    LaunchedEffect(scrolling) {
        if (scrolling) {
            lingering = true
        } else if (lingering) {
            delay(LingerTime)
            lingering = false
        }
    }
    return scrolling || lingering
}

/**
 * How long a touch scroller's bar stays after the scroll stops, before it
 * fades: long enough to read where the content came to rest.
 */
private val LingerTime: Duration = 600.milliseconds

/** Whether a mouse is over a [Modifier.scrollbar]'s strip, or holding it. */
@Stable
private class ScrollbarGrip {
    var hovered by mutableStateOf(false)
    var dragging by mutableStateOf(false)
}

/** Where a [Modifier.scrollbar]'s track is in its scroller, and where the thumb is on it. All in pixels. */
private class ScrollbarTrack {
    var orientation = Orientation.Vertical
        private set
    var direction = LayoutDirection.Ltr
        private set
    private var margin = 0f
    private var strip = 0f
    private var minThumb = 0f
    private var corner = 0f

    fun update(orientation: Orientation, direction: LayoutDirection, margin: Float, strip: Float, minThumb: Float, corner: Float) {
        this.orientation = orientation
        this.direction = direction
        this.margin = margin
        this.strip = strip
        this.minThumb = minThumb
        this.corner = corner
    }

    private fun length(size: Size): Float =
        ((if (orientation == Orientation.Vertical) size.height else size.width) - 2 * (margin + corner)).coerceAtLeast(0f)

    /** The distance the thumb can travel, and its length, for [geometry] in a scroller [size]. */
    fun travel(size: Size, geometry: ScrollbarGeometry): Pair<Float, Float> {
        val length = length(size)
        val thumb = (length * geometry.fraction).coerceAtLeast(minThumb).coerceAtMost(length)
        return (length - thumb) to thumb
    }

    /** Whether [at] is on the strip the bar is grabbed by. */
    fun onStrip(at: Offset, size: Size, direction: LayoutDirection): Boolean = when (orientation) {
        Orientation.Vertical ->
            if (direction == LayoutDirection.Rtl) at.x <= strip else at.x >= size.width - strip
        Orientation.Horizontal -> at.y >= size.height - strip
    }

    /** The thumb's rectangle, [width] across, or null where there is no room for one. */
    fun thumb(size: Size, direction: LayoutDirection, geometry: ScrollbarGeometry, width: Float): Rect? {
        val (travel, thumb) = travel(size, geometry)
        if (thumb <= 0f) return null
        val along = margin + corner + travel * geometry.position
        return when (orientation) {
            Orientation.Vertical -> {
                val x = if (direction == LayoutDirection.Rtl) margin else size.width - margin - width
                Rect(x, along, x + width, along + thumb)
            }
            // From the visual start, which is the right in right to left, as a
            // horizontal scroller's offset is.
            Orientation.Horizontal -> {
                val y = size.height - margin - width
                val x = if (direction == LayoutDirection.Rtl) size.width - along - thumb else along
                Rect(x, y, x + thumb, y + width)
            }
        }
    }
}

/**
 * A mouse on a [Modifier.scrollbar]: over the strip, it is hovering the bar;
 * pressed on it, the press is the bar's and its moves scroll the content at the
 * track's rate. Seen before the content sees anything, so a press on the bar
 * never reaches a row under it. A finger, and a press off the strip, are left
 * entirely alone.
 */
private suspend fun PointerInputScope.gripBar(state: ScrollableState, track: ScrollbarTrack, grip: ScrollbarGrip) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull() ?: continue
            if (change.type != PointerType.Mouse) continue
            val bounds = Size(size.width.toFloat(), size.height.toFloat())
            when (event.type) {
                PointerEventType.Exit -> grip.hovered = false
                PointerEventType.Enter, PointerEventType.Move -> {
                    grip.hovered = track.onStrip(change.position, bounds, track.direction) &&
                        scrollbarGeometry(state).isUseful
                }
                PointerEventType.Press -> {
                    if (!event.buttons.isPrimaryPressed || !grip.hovered) continue
                    change.consume()
                    grip.dragging = true
                    val vertical = track.orientation == Orientation.Vertical
                    // A horizontal track runs from the right in right to left.
                    val sign = if (!vertical && track.direction == LayoutDirection.Rtl) -1f else 1f
                    try {
                        while (true) {
                            val moved = awaitPointerEvent(PointerEventPass.Initial)
                            val drag = moved.changes.firstOrNull { it.id == change.id } ?: break
                            if (!drag.pressed) {
                                drag.consume()
                                break
                            }
                            val delta = drag.position - drag.previousPosition
                            val geometry = scrollbarGeometry(state)
                            val (travel, _) = track.travel(bounds, geometry)
                            if (travel > 0f) {
                                val along = if (vertical) delta.y else delta.x
                                state.dispatchRawDelta(sign * along * geometry.scrollable / travel)
                            }
                            drag.consume()
                        }
                    } finally {
                        grip.dragging = false
                        grip.hovered = track.onStrip(change.position, bounds, track.direction)
                    }
                }
                else -> Unit
            }
        }
    }
}

/**
 * How much of a list is on screen, and how far down it is.
 *
 * Pure, and tested, because a scrollbar with the wrong arithmetic is a thumb
 * that runs off the end of its track or never reaches it — and the error is a
 * few pixels at one extreme, which is exactly what a glance at a screenshot
 * misses.
 *
 * Foundation's `scrollIndicatorState` first, which every scroller it ships
 * reports; the library's own `ScrollExtent`, and estimates from what a state
 * does say, where it does not.
 */
internal fun scrollbarGeometry(state: ScrollableState): ScrollbarGeometry {
    // Foundation's own account, where the state gives one: every scroller it
    // ships — `ScrollState`, lazy lists, grids and staggered grids, pagers —
    // reports its offset, its content and its viewport in pixels, reverse
    // layouts and right to left already accounted for. Grids, staggered grids
    // and pagers used to fall to the end of the `when` below and draw nothing.
    state.scrollIndicatorState?.let { indicator ->
        val offset = indicator.scrollOffset
        val content = indicator.contentSize
        val viewport = indicator.viewportSize
        val known = offset != Int.MAX_VALUE && content != Int.MAX_VALUE && viewport != Int.MAX_VALUE
        if (known && content > 0 && viewport > 0) {
            return scrollbarGeometry(viewport.toFloat(), content.toFloat(), offset.toFloat())
        }
    }
    return reportedGeometry(state)
}

/** For a state foundation has no account of: worked out from what it does report. */
private fun reportedGeometry(state: ScrollableState): ScrollbarGeometry = when (state) {
    is ScrollState -> {
        val max = state.maxValue
        if (max <= 0 || max == Int.MAX_VALUE) {
            ScrollbarGeometry(1f, 0f)
        } else {
            val viewport = state.viewportSize.toFloat()
            scrollbarGeometry(
                viewport = viewport,
                contentLength = viewport + max,
                scrolled = state.value.toFloat(),
            )
        }
    }

    is LazyListState -> {
        val info = state.layoutInfo
        val visible = info.visibleItemsInfo
        val total = info.totalItemsCount
        if (visible.isEmpty() || total == 0) {
            ScrollbarGeometry(1f, 0f)
        } else {
            // Items, not pixels — so the content length is estimated from the
            // average visible item. It drifts on a list with wildly uneven rows,
            // which is the cost of not measuring every item in a list that
            // exists precisely so that does not happen.
            val averageSize = visible.sumOf { it.size }.toFloat() / visible.size
            scrollbarGeometry(
                viewport = (info.viewportEndOffset - info.viewportStartOffset).toFloat(),
                contentLength = averageSize * total,
                scrolled = state.firstVisibleItemIndex * averageSize +
                    state.firstVisibleItemScrollOffset,
            )
        }
    }

    is ScrollExtent -> if (state.extentMax <= 0) {
        ScrollbarGeometry(1f, 0f)
    } else {
        val viewport = state.extentViewport.toFloat()
        scrollbarGeometry(
            viewport = viewport,
            contentLength = viewport + state.extentMax,
            scrolled = state.extentValue.toFloat(),
        )
    }

    // No position to report. Hidden rather than drawn wrong.
    else -> ScrollbarGeometry(1f, 0f)
}

/**
 * A scrollable state of the library's own that can say where it is, in pixels —
 * a table's sideways scroll, which is not a `ScrollState` because its bounds are
 * set by the table rather than by a scroll modifier.
 */
internal interface ScrollExtent {
    val extentValue: Int
    val extentMax: Int
    val extentViewport: Int
}

/**
 * The arithmetic, separated from where the numbers come from.
 *
 * Tested directly: `ScrollState`'s own `maxValue` and `viewportSize` cannot be
 * set from outside foundation, so a test that went through the state could only
 * exercise the empty case — which is the one that already works.
 */
internal fun scrollbarGeometry(
    viewport: Float,
    contentLength: Float,
    scrolled: Float,
): ScrollbarGeometry {
    if (contentLength <= 0f) return ScrollbarGeometry(1f, 0f)
    val scrollable = (contentLength - viewport).coerceAtLeast(0f)
    return ScrollbarGeometry(
        fraction = (viewport / contentLength).coerceIn(0f, 1f),
        position = if (scrollable <= 0f) 0f else (scrolled / scrollable).coerceIn(0f, 1f),
        scrollable = scrollable,
    )
}
