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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.ui.unit.Density
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
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.IntSize

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
 * | Dragged | by its thumb, while it is drawn | anywhere along it |
 *
 * Under touch it is an indicator, as every phone's is: it appears as the
 * content moves, says where it is and how much there is, and fades once it has
 * stopped. **While it is drawn, a finger on its thumb takes hold of it**, and
 * the thumb thickens under the finger to say so. Only the thumb, give or take a
 * fingertip: the rest of the strip at the edge of a list is still the list's,
 * for a tap on a row or a scroll, and so is all of it while the bar is not
 * drawn. It took no touches at all, so a bar on a phone could be seen and not
 * moved — *"if the scrollbar is visible (even on mobile), then dragging it should
 * do what you expect"*. With a mouse or a keyboard it is the bar every other
 * window on the machine has, there all the time, and a long list without one
 * reads as broken. [alwaysVisible] draws it at rest under touch too, for a
 * scroller whose length is the point.
 *
 * **Dragging it scrolls the container**, at the rate the track's length says:
 * take hold of it and the content follows, rather than jumping to where it was
 * pressed. Raw rather than a fling — a scrollbar is a position control, and
 * letting go of one leaves the content where the thumb is. A finger that takes
 * hold of a moving list's thumb stops the list, as a finger on the list does.
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
 *   mouse, and so there to take hold of all along.
 * @param contentPadding What is over the ends of the scroller — a top bar, a
 *   floating tab bar or nav bar — for the track to stop short of, as the
 *   scroller's own `contentPadding` keeps its first and last rows clear of
 *   them. Usually the same padding. The track keeps clear of the top and the
 *   foot, and the end it sits at; a horizontal one of the start, the end and
 *   the foot.
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
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val pointer = scrollbarTakesAPointer()
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    val dragged by interactions.collectIsDraggedAsState()
    val touch = remember { ScrollbarTouchGrip() }
    // A drag that has wandered off the bar is still a drag on it, and a
    // scrollbar that thinned out halfway through one would be reporting that
    // the grip had been lost.
    val active = (pointer && (hovered || dragged)) || touch.held

    // Everything but the position, which changes on every frame of a scroll and
    // is read where it is used — in the thumb's placement. Composition only
    // needs what decides the thumb's size, and that holds still while a list of
    // even rows scrolls, so the bar does not recompose once a frame for it.
    val extent by remember(state) {
        derivedStateOf { scrollbarGeometry(state).copy(position = 0f) }
    }
    if (!extent.isUseful) return

    val look = rememberScrollbarLook(state, pointer, alwaysVisible, active, thickness, hoveredThickness, touch.held)
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

    // Where a finger finds the thumb, in the bar's own box: across the whole of
    // it, which is narrow, and along the thumb and a fingertip either side. The
    // box is padded by `xxs` and then by the corner before the track starts.
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val lead = with(density) { (Theme.spacing.xxs + cornerInset).toPx() }
    val slop = with(density) { TouchGripSlop.toPx() }
    touch.update(
        state = state,
        orientation = orientation,
        // A horizontal track runs from the right in right to left.
        sign = if (orientation == Orientation.Horizontal && rtl) -1f else 1f,
        shown = { look.alpha.value > 0f },
        rate = { _ -> if (travel > 0f) extent.scrollable / travel else 0f },
        target = { size ->
            val along = travel * scrollbarGeometry(state).position
            if (orientation == Orientation.Vertical) {
                Rect(0f, lead + along - slop, size.width.toFloat(), lead + along + thumbLength + slop)
            } else {
                val start = if (rtl) size.width - lead - along - thumbLength else lead + along
                Rect(start - slop, 0f, start + thumbLength + slop, size.height.toFloat())
            }
        },
    )

    Box(
        modifier = modifier
            // Nothing to announce: the list already conveys its own position.
            .clearAndSetSemantics {}
            // Clear of what is over the scroller's ends, and outside everything
            // that takes a press: what is there is the bar's or tab's over it.
            .padding(trackPadding(contentPadding, orientation, LocalLayoutDirection.current))
            // A finger on the thumb, while it is drawn. There whatever the hand,
            // and ahead of the mouse's nodes, so that the tap that turns a
            // mouse's bar into a finger's, and takes those nodes away, is not
            // cancelled with them. Under touch a press anywhere else on the
            // strip goes on to the scroller under it, as if the bar were not
            // there; with a mouse the strip is the bar's.
            .then(ScrollbarTouchGripElement(touch, shareWithSiblings = !pointer))
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
 * content's. Under touch a finger takes hold of the thumb while it is drawn,
 * within a fingertip of it, and anything else is the content's.
 *
 * **Under a floating bar**, give it the padding the content keeps clear of, and
 * the track stops short of the bar rather than running under it:
 *
 * ```kotlin
 * Scaffold(topBar = { TopBar { +"Stops" } }) { padding ->
 *     LazyColumn(
 *         Modifier.scrollbar(listState, contentPadding = padding),
 *         state = listState,
 *         contentPadding = padding,
 *     ) { … }
 * }
 * ```
 *
 * @param contentPadding What is over the ends of the scroller — a top bar, a
 *   floating tab bar or nav bar — for the track to stop short of, as the
 *   scroller's own `contentPadding` keeps its first and last rows clear of
 *   them. Usually the same padding. The track keeps clear of the top and the
 *   foot, and the end it sits at; a horizontal one of the start, the end and
 *   the foot.
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
    contentPadding: PaddingValues = PaddingValues(0.dp),
): Modifier {
    val pointer = scrollbarTakesAPointer()
    val grip = remember { ScrollbarGrip() }
    val touch = remember { ScrollbarTouchGrip() }
    val active = (pointer && (grip.hovered || grip.dragging)) || touch.held
    val look = rememberScrollbarLook(state, pointer, alwaysVisible, active, thickness, hoveredThickness, touch.held)
    val density = LocalDensity.current
    val margin = with(density) { Theme.spacing.xxs.toPx() }
    val strip = with(density) { hoveredThickness.toPx() } + 2 * margin
    val minThumb = with(density) { minThumbLength.toPx() }
    val corner = with(density) { cornerInset.toPx() }
    val track = remember { ScrollbarTrack() }
    val direction = LocalLayoutDirection.current
    val clear = trackClearance(contentPadding, orientation, direction, density)
    track.update(orientation, direction, margin, strip, minThumb, corner, clear)
    val reach = with(density) { TouchGripReach.toPx() }
    val slop = with(density) { TouchGripSlop.toPx() }
    touch.update(
        state = state,
        orientation = orientation,
        sign = if (orientation == Orientation.Horizontal && direction == LayoutDirection.Rtl) -1f else 1f,
        shown = { look.alpha.value > 0f },
        rate = { size ->
            val geometry = scrollbarGeometry(state)
            val (travel, _) = track.travel(Size(size.width.toFloat(), size.height.toFloat()), geometry)
            if (travel > 0f) geometry.scrollable / travel else 0f
        },
        target = { size ->
            val geometry = scrollbarGeometry(state)
            if (!geometry.isUseful) null else track.touchTarget(Size(size.width.toFloat(), size.height.toFloat()), geometry, reach, slop)
        },
    )

    // The same nodes whatever the hand, each minding only its own: a mouse's
    // and a finger's. Not chosen by the hand, as they were, because the hand is
    // read from the press — a tap is what turns a mouse's bar into a finger's —
    // and a node taken off the scroller mid-press cancels the press on
    // everything inside it: the row, or the menu item, the tap was for.
    return this
        // There all along and the arrow, overriding nothing, until the pointer
        // is on the bar: Compose asks the nodes under a press at the moment it
        // goes down. See `heldCursor`.
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
        // On the scroller itself, so it sees a finger before the content does,
        // and takes only the one on the thumb.
        .then(ScrollbarTouchGripElement(touch, shareWithSiblings = false))
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
    held: Boolean,
): ScrollbarLook {
    val motion = Theme.motion
    val shown = pointer || alwaysVisible || rememberShownWhileScrolling(state, held)
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
            // Held by a finger as well as under a mouse: the thumb thickens to
            // say it has been taken hold of.
            active -> hoveredThickness
            !pointer -> ScrollbarDefaults.TouchThickness
            else -> thickness
        },
        animationSpec = motion.tweenFast(),
        label = "scrollbarThickness",
    )
    return remember(alpha, width) { ScrollbarLook(width, alpha) }
}

/**
 * Whether a touch scroller's bar is up: while it scrolls or a finger holds it,
 * and for [LingerTime] after.
 */
@Composable
private fun rememberShownWhileScrolling(state: ScrollableState, held: Boolean): Boolean {
    val scrolling = state.isScrollInProgress || held
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

/**
 * What a track keeps clear of, in pixels: [lead] before it and [trail] after it
 * along its axis, in the reading direction, and [side] in from the edge it sits
 * on — the end, or the foot.
 */
@Immutable
internal data class TrackClearance(val lead: Float, val trail: Float, val side: Float) {
    companion object {
        val None = TrackClearance(0f, 0f, 0f)
    }
}

/**
 * The parts of [padding] a track along [orientation] keeps clear of: the top
 * and the foot, and the end it sits at, for a bar down the side; the start and
 * the end, and the foot, for one along the foot. The other edge is the
 * content's, and nothing of the bar's is near it.
 */
internal fun trackClearance(
    padding: PaddingValues,
    orientation: Orientation,
    direction: LayoutDirection,
    density: Density,
): TrackClearance = with(density) {
    when (orientation) {
        Orientation.Vertical -> TrackClearance(
            lead = padding.calculateTopPadding().toPx(),
            trail = padding.calculateBottomPadding().toPx(),
            side = padding.calculateEndPadding(direction).toPx(),
        )
        Orientation.Horizontal -> TrackClearance(
            lead = padding.calculateStartPadding(direction).toPx(),
            trail = padding.calculateEndPadding(direction).toPx(),
            side = padding.calculateBottomPadding().toPx(),
        )
    }
}

/** [trackClearance] as padding, for a [Scrollbar] of its own. */
private fun trackPadding(padding: PaddingValues, orientation: Orientation, direction: LayoutDirection): PaddingValues =
    when (orientation) {
        Orientation.Vertical -> PaddingValues(
            top = padding.calculateTopPadding(),
            bottom = padding.calculateBottomPadding(),
            end = padding.calculateEndPadding(direction),
        )
        Orientation.Horizontal -> PaddingValues(
            start = padding.calculateStartPadding(direction),
            end = padding.calculateEndPadding(direction),
            bottom = padding.calculateBottomPadding(),
        )
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
    private var clear = TrackClearance.None

    fun update(
        orientation: Orientation,
        direction: LayoutDirection,
        margin: Float,
        strip: Float,
        minThumb: Float,
        corner: Float,
        clear: TrackClearance,
    ) {
        this.orientation = orientation
        this.direction = direction
        this.margin = margin
        this.strip = strip
        this.minThumb = minThumb
        this.corner = corner
        this.clear = clear
    }

    private fun length(size: Size): Float =
        ((if (orientation == Orientation.Vertical) size.height else size.width) -
            2 * (margin + corner) - clear.lead - clear.trail).coerceAtLeast(0f)

    /** The distance the thumb can travel, and its length, for [geometry] in a scroller [size]. */
    fun travel(size: Size, geometry: ScrollbarGeometry): Pair<Float, Float> {
        val length = length(size)
        val thumb = (length * geometry.fraction).coerceAtLeast(minThumb).coerceAtMost(length)
        return (length - thumb) to thumb
    }

    /** Whether [at] is on the strip the bar is grabbed by. */
    fun onStrip(at: Offset, size: Size, direction: LayoutDirection): Boolean {
        val (from, to) = alongRange(size)
        return when (orientation) {
            Orientation.Vertical -> at.y in from..to && if (direction == LayoutDirection.Rtl) {
                at.x >= clear.side && at.x <= clear.side + strip
            } else {
                at.x <= size.width - clear.side && at.x >= size.width - clear.side - strip
            }
            Orientation.Horizontal -> {
                // Along the track from where it starts: the right, in right
                // to left.
                val x = if (direction == LayoutDirection.Rtl) size.width - at.x else at.x
                x in from..to && at.y <= size.height - clear.side && at.y >= size.height - clear.side - strip
            }
        }
    }

    /** Where the track starts and ends along its axis, from its start in the reading direction. */
    private fun alongRange(size: Size): Pair<Float, Float> {
        val full = if (orientation == Orientation.Vertical) size.height else size.width
        return clear.lead to (full - clear.trail)
    }

    /**
     * Where a finger takes hold of the thumb: [reach] in from the edge, and along
     * the thumb with [slop] either side. Null where there is no thumb.
     */
    fun touchTarget(size: Size, geometry: ScrollbarGeometry, reach: Float, slop: Float): Rect? {
        val thumb = thumb(size, direction, geometry, 0f) ?: return null
        return when (orientation) {
            Orientation.Vertical -> {
                val x = if (direction == LayoutDirection.Rtl) clear.side else size.width - clear.side - reach
                Rect(x, thumb.top - slop, x + reach, thumb.bottom + slop)
            }
            Orientation.Horizontal -> {
                val y = size.height - clear.side
                Rect(thumb.left - slop, y - reach, thumb.right + slop, y)
            }
        }
    }

    /** The thumb's rectangle, [width] across, or null where there is no room for one. */
    fun thumb(size: Size, direction: LayoutDirection, geometry: ScrollbarGeometry, width: Float): Rect? {
        val (travel, thumb) = travel(size, geometry)
        if (thumb <= 0f) return null
        val along = clear.lead + margin + corner + travel * geometry.position
        return when (orientation) {
            Orientation.Vertical -> {
                val x = if (direction == LayoutDirection.Rtl) clear.side + margin else size.width - clear.side - margin - width
                Rect(x, along, x + width, along + thumb)
            }
            // From the visual start, which is the right in right to left, as a
            // horizontal scroller's offset is.
            Orientation.Horizontal -> {
                val y = size.height - clear.side - margin - width
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
 * How far in from its edge a finger takes hold of a bar's thumb on a scroller:
 * the half of a touch target that is on the screen, the other half being past
 * the edge the bar is on.
 */
private val TouchGripReach: Dp = 24.dp

/**
 * How far past either end of the thumb a finger still takes hold of it. The
 * shortest thumb, [ScrollbarDefaults.MinThumbLength], is then a touch target
 * long.
 */
private val TouchGripSlop: Dp = 8.dp

/**
 * A finger on a bar, and what the bar knows about where its thumb is. Written
 * from composition, read when a finger goes down, so the node that takes the
 * finger never holds a number that has gone stale.
 */
@Stable
private class ScrollbarTouchGrip {
    /** Whether a finger has hold of the thumb now. */
    var held by mutableStateOf(false)

    var state: ScrollableState? = null
        private set
    var orientation: Orientation = Orientation.Vertical
        private set
    var sign: Float = 1f
        private set
    var shown: () -> Boolean = { false }
        private set
    /** Pixels of content for a pixel along the track, in a box [IntSize] big. */
    var rate: (IntSize) -> Float = { 0f }
        private set
    /** Where a finger takes hold, in a box [IntSize] big, or null where it cannot. */
    var target: (IntSize) -> Rect? = { null }
        private set

    fun update(
        state: ScrollableState,
        orientation: Orientation,
        sign: Float,
        shown: () -> Boolean,
        rate: (IntSize) -> Float,
        target: (IntSize) -> Rect?,
    ) {
        this.state = state
        this.orientation = orientation
        this.sign = sign
        this.shown = shown
        this.rate = rate
        this.target = target
    }
}

private data class ScrollbarTouchGripElement(
    private val grip: ScrollbarTouchGrip,
    private val shareWithSiblings: Boolean,
) : ModifierNodeElement<ScrollbarTouchGripNode>() {
    override fun create() = ScrollbarTouchGripNode(grip, shareWithSiblings)

    override fun update(node: ScrollbarTouchGripNode) {
        node.grip = grip
        node.shareWithSiblings = shareWithSiblings
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "scrollbarTouchGrip"
    }
}

/**
 * A finger on a shown bar's thumb: the press is the bar's, and its moves scroll
 * the content at the track's rate until it lifts. Seen before the content sees
 * anything, so the content neither scrolls under it nor takes it for a tap. A
 * finger anywhere else, a bar that is not drawn, and a mouse — which has
 * `gripBar` — are left entirely alone.
 *
 * The moves go to the scroller raw, as a mouse's on the bar do. Not through a
 * `scroll` of its own, which was the first draft and moved nothing: the
 * scroller stops whatever user scroll is running when a finger goes down on
 * it, this one included, consumed or not — which is also what stops a fling
 * the finger caught. The bar stays up while it is held because it is held,
 * [ScrollbarTouchGrip.held], rather than because a scroll is running.
 *
 * A node rather than `pointerInput` for [shareWithSiblings]: a [Scrollbar] laid
 * over its list is the list's sibling, and only a node can let a press it does
 * not want go on to the sibling underneath.
 */
private class ScrollbarTouchGripNode(
    var grip: ScrollbarTouchGrip,
    var shareWithSiblings: Boolean,
) : Modifier.Node(), PointerInputModifierNode {
    private var finger: PointerId? = null

    override fun onPointerEvent(pointerEvent: PointerEvent, pass: PointerEventPass, bounds: IntSize) {
        if (pass != PointerEventPass.Initial) return
        val held = finger
        if (held == null) {
            if (pointerEvent.type != PointerEventType.Press) return
            val change = pointerEvent.changes.firstOrNull { it.changedToDown() } ?: return
            if (change.type == PointerType.Mouse) return
            val state = grip.state ?: return
            if (!grip.shown()) return
            val target = grip.target(bounds) ?: return
            if (!target.contains(change.position)) return
            change.consume()
            finger = change.id
            grip.held = true
        } else {
            val change = pointerEvent.changes.firstOrNull { it.id == held } ?: return
            if (!change.pressed) {
                change.consume()
                letGo()
                return
            }
            val moved = change.position - change.previousPosition
            val along = if (grip.orientation == Orientation.Vertical) moved.y else moved.x
            if (along != 0f) grip.state?.dispatchRawDelta(grip.sign * along * grip.rate(bounds))
            change.consume()
        }
    }

    private fun letGo() {
        finger = null
        grip.held = false
    }

    override fun onCancelPointerInput() = letGo()

    override fun onDetach() = letGo()

    override fun sharePointerInputWithSiblings(): Boolean = shareWithSiblings
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
