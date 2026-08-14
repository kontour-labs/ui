package io.kontour.ui.components.list

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** How opaque each edge's fade is, 0 to 1. */
@Immutable
data class ScrollFade(val start: Float, val end: Float) {
    val isVisible: Boolean get() = start > 0f || end > 0f
}

/**
 * Fades a scrolling container's content out at whichever edge it can scroll
 * toward.
 *
 * ```kotlin
 * val state = rememberLazyListState()
 * LazyColumn(Modifier.fadingEdges(state), state = state) { … }
 * ```
 *
 * Says "there is more this way" without adding a control, which matters most on
 * a list whose last row happens to end near the viewport edge — the case that
 * otherwise reads as "that is everything".
 *
 * The fade **erases** the content rather than painting a gradient of the
 * background colour over it, using `BlendMode.DstOut` in an offscreen layer.
 * Painting the background instead is the common shortcut and it fails the moment
 * anything is behind the list: an image, a map, a coloured panel. The offscreen
 * layer is what keeps the erase from taking the background with it.
 *
 * Works with `ScrollState`, `LazyListState`, `LazyGridState` and
 * `LazyStaggeredGridState`, each of which reports its position differently.
 * Anything else falls back to `canScrollBackward`/`canScrollForward` — correct,
 * but with no gradual fade near the ends.
 *
 * @param fadeLength Capped at 40% of the container, so a short list does not
 *   fade to nothing in the middle.
 */
@Composable
fun Modifier.fadingEdges(
    state: ScrollableState,
    orientation: Orientation = Orientation.Vertical,
    fadeLength: Dp = 24.dp,
): Modifier {
    val density = LocalDensity.current
    val fadeLengthPx = remember(density, fadeLength) { with(density) { fadeLength.toPx() } }

    val fade by remember(state, fadeLengthPx, orientation) {
        derivedStateOf { scrollFade(state, fadeLengthPx, orientation) }
    }

    return this
        // Isolate the layer, or DstOut erases whatever is painted behind the
        // list as well as the list itself.
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            if (!fade.isVisible) return@drawWithContent

            val extent = if (orientation == Orientation.Vertical) size.height else size.width
            if (extent <= 0f) return@drawWithContent
            val stop = (fadeLengthPx / extent).coerceAtMost(0.4f)

            if (fade.start > 0f) {
                drawRect(
                    brush = edgeBrush(
                        orientation = orientation,
                        from = 0f to Color.Black.copy(alpha = fade.start),
                        to = stop to Color.Transparent,
                    ),
                    blendMode = BlendMode.DstOut,
                )
            }
            if (fade.end > 0f) {
                drawRect(
                    brush = edgeBrush(
                        orientation = orientation,
                        from = (1f - stop) to Color.Transparent,
                        to = 1f to Color.Black.copy(alpha = fade.end),
                    ),
                    blendMode = BlendMode.DstOut,
                )
            }
        }
}

private fun edgeBrush(
    orientation: Orientation,
    from: Pair<Float, Color>,
    to: Pair<Float, Color>,
): Brush = if (orientation == Orientation.Vertical) {
    Brush.verticalGradient(from, to)
} else {
    Brush.horizontalGradient(from, to)
}

/**
 * How far each edge should be faded, given how far [state] has scrolled.
 *
 * Fully opaque once there is more than [fadeLengthPx] of content past the edge,
 * and ramps down over the last [fadeLengthPx] so the fade lifts as the list
 * reaches its end rather than snapping off.
 */
internal fun scrollFade(
    state: ScrollableState,
    fadeLengthPx: Float,
    orientation: Orientation,
): ScrollFade {
    if (fadeLengthPx <= 0f) return ScrollFade(0f, 0f)

    return when (state) {
        is ScrollState -> {
            // An unmeasured ScrollState reports Int.MAX_VALUE, which would fade
            // the trailing edge fully on the first frame — a flash of gradient
            // on every list before it has been laid out.
            val max = state.maxValue
            if (max <= 0 || max == Int.MAX_VALUE) {
                ScrollFade(0f, 0f)
            } else {
                ScrollFade(
                    start = (state.value / fadeLengthPx).coerceIn(0f, 1f),
                    end = ((max - state.value) / fadeLengthPx).coerceIn(0f, 1f),
                )
            }
        }

        is LazyListState -> {
            val info = state.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            ScrollFade(
                start = leadingFade(
                    state.firstVisibleItemIndex,
                    state.firstVisibleItemScrollOffset,
                    fadeLengthPx,
                ),
                end = when {
                    last == null -> 0f
                    last.index < info.totalItemsCount - 1 -> 1f
                    else -> trailingFade(
                        last.offset + last.size,
                        info.viewportEndOffset,
                        fadeLengthPx,
                    )
                },
            )
        }

        is LazyGridState -> {
            val info = state.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            val vertical = orientation == Orientation.Vertical
            ScrollFade(
                start = leadingFade(
                    state.firstVisibleItemIndex,
                    state.firstVisibleItemScrollOffset,
                    fadeLengthPx,
                ),
                end = when {
                    last == null -> 0f
                    last.index < info.totalItemsCount - 1 -> 1f
                    else -> trailingFade(
                        if (vertical) last.offset.y + last.size.height
                        else last.offset.x + last.size.width,
                        info.viewportEndOffset,
                        fadeLengthPx,
                    )
                },
            )
        }

        is LazyStaggeredGridState -> {
            val info = state.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            val vertical = orientation == Orientation.Vertical
            ScrollFade(
                start = leadingFade(
                    state.firstVisibleItemIndex,
                    state.firstVisibleItemScrollOffset,
                    fadeLengthPx,
                ),
                end = when {
                    last == null -> 0f
                    last.index < info.totalItemsCount - 1 -> 1f
                    else -> trailingFade(
                        if (vertical) last.offset.y + last.size.height
                        else last.offset.x + last.size.width,
                        info.viewportEndOffset,
                        fadeLengthPx,
                    )
                },
            )
        }

        // Anything else: on or off. Correct, but with no ramp at the ends,
        // because there is no position to ramp against.
        else -> ScrollFade(
            start = if (state.canScrollBackward) 1f else 0f,
            end = if (state.canScrollForward) 1f else 0f,
        )
    }
}

internal fun leadingFade(firstIndex: Int, firstOffset: Int, fadeLengthPx: Float): Float =
    if (firstIndex > 0) 1f else (firstOffset / fadeLengthPx).coerceIn(0f, 1f)

internal fun trailingFade(lastItemEnd: Int, viewportEnd: Int, fadeLengthPx: Float): Float =
    ((lastItemEnd - viewportEnd) / fadeLengthPx).coerceIn(0f, 1f)
