package io.kontour.ui.components.list

import io.kontour.ui.a11y.highContrast
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.drawContentErasing

/** How opaque each edge's fade is, 0 to 1. */
@Immutable
internal data class ScrollFade(val start: Float, val end: Float) {
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
 * background colour over it, using `BlendMode.DstOut` in a layer of the list's
 * own. Painting the background instead is the common shortcut and it fails the
 * moment anything is behind the list: an image, a map, a coloured panel. The
 * layer is what keeps the erase from taking the background with it.
 *
 * Works with `ScrollState`, `LazyListState`, `LazyGridState` and
 * `LazyStaggeredGridState`, each of which reports its position differently.
 * Anything else falls back to `canScrollBackward`/`canScrollForward` — correct,
 * but with no gradual fade near the ends.
 *
 * At high contrast there is no fade: nothing that is text fades at that tier,
 * and a row half-erased at the edge is text a reader has asked to see whole. The
 * list's own edge, and its scrollbar, still say there is more.
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
    if (highContrast()) return this
    val density = LocalDensity.current
    val fadeLengthPx = remember(density, fadeLength) { with(density) { fadeLength.toPx() } }

    val fade by remember(state, fadeLengthPx, orientation) {
        derivedStateOf { scrollFade(state, fadeLengthPx, orientation) }
    }

    return this
        // The brushes are built in the cache and kept while the fade holds still,
        // which is most of a scroll: the list redraws on every frame it moves,
        // and a gradient built in the draw was two new shaders a frame for a
        // fade that had not changed. Reading `fade` here is what rebuilds them
        // when it does.
        .drawWithCache {
            val edges = fade
            val extent = if (orientation == Orientation.Vertical) size.height else size.width
            val stop = (fadeLengthPx / extent).coerceAtMost(0.4f)
            // Right to left, a horizontal scroller's earlier content is off its
            // right edge, so the two fades change sides. They used not to, and
            // faded the newest end of a right-to-left row.
            val mirrored = orientation == Orientation.Horizontal && layoutDirection == LayoutDirection.Rtl
            val before = if (mirrored) edges.end else edges.start
            val after = if (mirrored) edges.start else edges.end
            val drawn = edges.isVisible && extent > 0f
            val beforeBrush = if (drawn && before > 0f) {
                edgeBrush(
                    orientation = orientation,
                    from = 0f to Color.Black.copy(alpha = before),
                    to = stop to Color.Transparent,
                )
            } else {
                null
            }
            val afterBrush = if (drawn && after > 0f) {
                edgeBrush(
                    orientation = orientation,
                    from = (1f - stop) to Color.Transparent,
                    to = 1f to Color.Black.copy(alpha = after),
                )
            } else {
                null
            }
            onDrawWithContent {
                // A layer of the list's own round the rows and the erase, or
                // DstOut erases whatever is painted behind the list as well:
                // the surface it is on, and through it whatever is under that.
                //
                // Only while there is a fade to draw. The layer is not a flag:
                // every draw of the list renders into a buffer of its own and is
                // composited back, and a list scrolled to the top, or one short
                // enough never to scroll, would pay that every frame for a blend
                // that is not happening.
                //
                // Opened here, in the draw that erases, rather than by switching
                // a `graphicsLayer`'s compositing on while there is a fade. The
                // two fell out of step, and a sheet over a map showed the map
                // through its list's top edge. See `drawContentErasing`.
                //
                // **And only over the fades.** See [drawFadedAtEdges].
                if (beforeBrush == null && afterBrush == null) {
                    drawContent()
                } else {
                    drawFadedAtEdges(
                        vertical = orientation == Orientation.Vertical,
                        strip = stop * extent,
                        before = beforeBrush,
                        after = afterBrush,
                    )
                }
            }
        }
}

/**
 * The list with its edges erased by [before] and [after], each [strip] deep,
 * with a layer of its own under each fade and none anywhere else.
 *
 * Raised 2026-10-29 for 0.5.1. The layer the erase needs was the whole list:
 * every frame of a scroll mid-list, which is every frame a fade shows, drew
 * every row into a buffer the size of the list and composited it back. A phone's
 * GPU pays that in fill, and the software renderer here took a frame from about
 * 4ms to about 50ms. The erase only ever touches the two strips the fades are
 * in, so only they are drawn into layers, 24dp deep each, and the middle of the
 * list straight onto the canvas as if there were no fade.
 *
 * **Where the two meet.** Desktop, iOS and the web clip with antialiased edges,
 * so a strip and the middle sharing an edge at a fractional pixel — a list in a
 * sheet as it slides, say — would each cover part of that pixel, and a faint
 * line of whatever is behind would show through between them. So each strip
 * reaches [StripOverlap] past its edge, under the middle: the fade has finished
 * erasing there, so the strip only adds cover, and no pixel is short of it. What
 * differs is a glyph's antialiased edge in that one row, drawn twice over, a
 * fraction of a shade darker. Measured before this went in; see
 * `FadingEdgesStripTest`.
 */
private fun ContentDrawScope.drawFadedAtEdges(vertical: Boolean, strip: Float, before: Brush?, after: Brush?) {
    val extent = if (vertical) size.height else size.width
    // The list's own ends, and its sides, are left to whatever already clips
    // it — a scroller clips its content — and not clipped here again: at a
    // fractional position a second antialiased edge on the list's own takes
    // its edge pixels' cover twice, and the ground shows through them. So the
    // pieces reach well past the list everywhere but where they meet.
    val near = -extent
    val far = extent * 2f
    val from = if (before != null) strip else near
    val to = if (after != null) extent - strip else far
    if (to > from) {
        val middle = span(vertical, from, to)
        clipRect(middle.left, middle.top, middle.right, middle.bottom) { this@drawFadedAtEdges.drawContent() }
    }
    if (before != null) erasedStrip(span(vertical, near, minOf(extent, strip + StripOverlap)), before)
    if (after != null) erasedStrip(span(vertical, maxOf(0f, extent - strip - StripOverlap), far), after)
}

/** The content in [area], in a layer of [area]'s own, with [fade] erased from it. */
private fun ContentDrawScope.erasedStrip(area: Rect, fade: Brush) {
    clipRect(area.left, area.top, area.right, area.bottom) {
        this@erasedStrip.drawContentErasing(area) { drawRect(brush = fade, blendMode = BlendMode.DstOut) }
    }
}

/** [from]..[to] along the scroll, and well past the list either side across it. See [drawFadedAtEdges]. */
private fun DrawScope.span(vertical: Boolean, from: Float, to: Float): Rect =
    if (vertical) Rect(-size.width, from, size.width * 2f, to) else Rect(from, -size.height, to, size.height * 2f)

/** How far a fade's layer reaches under the unfaded middle of the list. See [drawFadedAtEdges]. */
private const val StripOverlap = 1f

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
