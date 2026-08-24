package io.kontour.ui.components.datetime

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.Text
import io.kontour.ui.interaction.Feedback
import io.kontour.ui.interaction.FeedbackIntent
import io.kontour.ui.theme.Theme
import kotlin.math.abs
import kotlinx.coroutines.launch

/**
 * A scrolling drum of values, snapping to the one in the middle.
 *
 * The control iOS users reach for when setting a time, and worth having even on
 * Android for the same reason: adjusting a value by a few steps is faster by
 * flick than by opening a menu.
 *
 * Items away from the centre fade and shrink, which is what makes the flat list
 * read as a curved drum rather than a scrolling list with a box drawn on it.
 * Each item passing the centre fires a tick haptic, so the control can be
 * operated by feel.
 *
 * ```
 * WheelPicker(
 *     items = (0..23).toList(),
 *     selected = hour,
 *     onSelectedChange = { hour = it },
 *     label = { it.toString().padStart(2, '0') },
 * )
 * ```
 *
 * @param visibleItems How many rows are shown. Odd numbers only — the selection
 *   sits in the middle row, which needs an equal number above and below.
 */
@Composable
fun <T> WheelPicker(
    items: List<T>,
    selected: Int,
    onSelectedChange: (Int) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    visibleItems: Int = 5,
    itemHeight: Dp = 40.dp,
) {
    require(visibleItems % 2 == 1) { "visibleItems must be odd so a row can sit in the centre" }
    if (items.isEmpty()) return

    val edgeItems = visibleItems / 2
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selected)
    val flingBehavior = rememberSnapFlingBehavior(listState)
    val feedback = Feedback
    val currentOnSelect by rememberUpdatedState(onSelectedChange)

    // The item under the centre line is the first visible one, because the list
    // is padded by exactly `edgeItems` rows at each end.
    val centredIndex by remember {
        derivedStateOf {
            (listState.firstVisibleItemIndex +
                if (listState.firstVisibleItemScrollOffset > 0) 1 else 0)
                .coerceIn(0, items.lastIndex)
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { centredIndex }.collect { index ->
            feedback.perform(FeedbackIntent.Tick)
            currentOnSelect(index)
        }
    }

    LaunchedEffect(selected) {
        if (selected != centredIndex && !listState.isScrollInProgress) {
            listState.scrollToItem(selected)
        }
    }

    /**
     * A drum you can grab with a mouse.
     *
     * A `LazyColumn` scrolls to a wheel and to a finger, and on desktop that is
     * the whole of it — dragging a list with the mouse is not a thing desktops
     * do, and Compose is right not to. A *drum* is the exception: nobody has
     * ever set a time by scrolling a picker with a wheel, they take hold of it
     * and turn it.
     *
     * Wrapped around the list rather than replacing its scrolling, which is what
     * makes this safe: a child gets the main pointer pass before its parent, so
     * the list still claims every touch drag itself and this only ever sees the
     * ones it declined. `dispatchRawDelta` rather than a coroutine per event —
     * there is one of these per pointer move, and `scrollBy` suspends.
     */
    val scope = rememberCoroutineScope()

    /**
     * Keeps the drum's scrolling to the drum.
     *
     * A `LazyColumn` is a nested-scroll child by default: whatever its fling
     * behaviour does not consume is handed up to the nearest scrollable
     * ancestor. A wheel holds twelve or twenty-four rows with padding at both
     * ends, so a flick reaches an end *routinely* rather than exceptionally —
     * and the leftover velocity went straight into the page behind it, or into
     * the sheet the picker was sitting in. Reported as "when I finish scrolling
     * a wheel, the velocity continues into the lazy list".
     *
     * Nothing here moves the wheel. Both overrides claim what they are given and
     * do nothing with it, which is the whole intent: a spinning drum is a
     * self-contained gesture, and a page that scrolls because a drum ran out of
     * numbers is a page nobody asked to scroll.
     */
    val containment = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset =
                // Over-scroll at either end, from a finger or from the drum's
                // own settling animation. Swallowed either way: the settle is
                // the wheel putting *itself* straight, and a page that scrolls
                // because a drum snapped to the nearest row is a page nobody
                // asked to scroll. Filtering to `UserInput` here left thirty
                // pixels of it escaping.
                available

            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity,
            ): Velocity = available
        }
    }

    Box(
        modifier
            .height(itemHeight * visibleItems)
            // Above the drag and the list, so it sees what either of them
            // declined and the page above sees neither.
            .nestedScroll(containment)
            .draggable(
                state = rememberDraggableState { delta -> listState.dispatchRawDelta(-delta) },
                orientation = Orientation.Vertical,
                // The list's own fling snaps; a raw drag has to be given back
                // to the nearest row itself, or the drum is left between two.
                onDragStopped = { scope.launch { listState.animateScrollToItem(centredIndex) } },
            ),
        contentAlignment = Alignment.Center,
    ) {
        LazyColumn(
            state = listState,
            flingBehavior = flingBehavior,
            contentPadding = PaddingValues(vertical = itemHeight * edgeItems),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(items.size) { index ->
                val distance = abs(index - centredIndex)
                // Falls away steeply: the neighbour is clearly secondary, and
                // anything two rows out is barely there.
                val fade = when (distance) {
                    0 -> 1f
                    1 -> 0.45f
                    else -> 0.2f
                }
                val shrink = when (distance) {
                    0 -> 1f
                    1 -> 0.86f
                    else -> 0.74f
                }

                Box(
                    Modifier.fillMaxWidth().height(itemHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label(items[index]),
                        style = Theme.typography.titleLarge,
                        color = Theme.colors.content,
                        modifier = Modifier.alpha(fade).scale(shrink),
                    )
                }
            }
        }

        // The selection band sits behind the text rather than over it, so the
        // centred value keeps full contrast.
        Box(
            Modifier
                .fillMaxWidth()
                .height(itemHeight)
                .semantics {
                    stateDescription = label(items[centredIndex])
                }
        )
    }
}
