package io.kontour.ui.components.list

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
import androidx.compose.runtime.Immutable
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
import io.kontour.ui.input.LocalInputModality
import io.kontour.ui.input.pointerCursor
import io.kontour.ui.theme.Theme
import kotlin.math.roundToInt

/** How much of the track the thumb covers, and where it sits. 0 to 1. */
@Immutable
data class ScrollbarGeometry(val fraction: Float, val position: Float) {
    val isUseful: Boolean get() = fraction < 1f && fraction > 0f
}

object ScrollbarDefaults {
    val Thickness: Dp = 6.dp
    val HoveredThickness: Dp = 10.dp
    val MinThumbLength: Dp = 32.dp

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
 * A scroll position indicator, for pointers.
 *
 * ```kotlin
 * Box {
 *     LazyColumn(state = listState) { … }
 *     Scrollbar(listState, Modifier.align(Alignment.CenterEnd))
 * }
 * ```
 *
 * **Drawn only when a pointer that can hover is in use.** A permanent scrollbar
 * on a touchscreen is wrong twice over: it is not draggable with a finger at any
 * sensible width, and it takes space from content on the screens with least of
 * it. On desktop and web it is the opposite — a long list with no scrollbar
 * reads as broken, because every other window on the machine has one.
 *
 * The same reasoning as `LocalInputModality` everywhere else: the platform is a
 * poor proxy, since a Chromebook is Android with a trackpad and a phone browser
 * is "web" but touch-first.
 *
 * Purely an indicator — it does not consume input. Dragging a 6dp target is not
 * how anyone scrolls a list, and making it draggable would mean widening it to
 * the point where it competes with the content. It is also hidden from the
 * accessibility tree: it conveys nothing a screen reader cannot already get from
 * the list itself.
 *
 * @param cornerInset How far the container's rounded corner intrudes on the
 *   track, taken off both ends. A scrollbar measures itself, not its container,
 *   so it cannot discover this — inside a menu or a dialog the track otherwise
 *   runs the full height and its ends disappear behind the curve.
 *
 *   A `Dp` rather than the container's shape, because a corner size needs a size
 *   to resolve against and the only size here is the scrollbar's own. Every
 *   rounded host in the library uses a fixed-`Dp` corner — `container` is 22dp,
 *   `panel` 28 — so a length is exact for all of them; a proportional corner
 *   could not be expressed, and no scrollbar host has one.
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
    val modality = LocalInputModality.current
    if (!alwaysVisible && !modality.supportsHover) return

    val motion = Theme.motion
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()

    val geometry = scrollbarGeometry(state)
    if (!geometry.isUseful) return

    val width by animateFloatAsState(
        targetValue = if (hovered) hoveredThickness.value else thickness.value,
        animationSpec = motion.tweenFast(),
        label = "scrollbarThickness",
    )
    val alpha by animateFloatAsState(
        targetValue = if (hovered) 1f else 0.5f,
        animationSpec = motion.tweenFast(),
        label = "scrollbarAlpha",
    )

    var trackLength by remember { mutableFloatStateOf(0f) }
    val minThumbPx = with(LocalDensity.current) {
        minThumbLength.toPx()
    }
    val thumbLength = (trackLength * geometry.fraction).coerceAtLeast(minThumbPx)
    val travel = (trackLength - thumbLength).coerceAtLeast(0f)
    val thumbOffset = travel * geometry.position

    Box(
        modifier = modifier
            // Nothing to announce: the list already conveys its own position.
            .clearAndSetSemantics {}
            .pointerCursor()
            .hoverable(interactions)
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
        Box(
            Modifier
                .offset {
                    if (orientation == Orientation.Vertical) {
                        IntOffset(0, thumbOffset.roundToInt())
                    } else {
                        IntOffset(thumbOffset.roundToInt(), 0)
                    }
                }
                .then(
                    if (orientation == Orientation.Vertical) {
                        Modifier
                            .width(width.dp)
                            .height(with(LocalDensity.current) {
                                thumbLength.toDp()
                            })
                    } else {
                        Modifier
                            .height(width.dp)
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
 * How much of a list is on screen, and how far down it is.
 *
 * Pure, and tested, because a scrollbar with the wrong arithmetic is a thumb
 * that runs off the end of its track or never reaches it — and the error is a
 * few pixels at one extreme, which is exactly what a glance at a screenshot
 * misses.
 *
 * `LazyListState` reports items, not pixels, so the fraction is estimated from
 * the average visible item size. It drifts on a list with wildly uneven rows,
 * which is a cost worth paying: the alternative is measuring every item, and a
 * lazy list exists precisely so that does not happen.
 */
internal fun scrollbarGeometry(state: ScrollableState): ScrollbarGeometry = when (state) {
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

    // No position to report. Hidden rather than drawn wrong.
    else -> ScrollbarGeometry(1f, 0f)
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
    )
}
