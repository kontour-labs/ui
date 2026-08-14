package io.kontour.ui.components.datetime

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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.Text
import io.kontour.ui.interaction.Feedback
import io.kontour.ui.interaction.FeedbackIntent
import io.kontour.ui.theme.Theme
import kotlin.math.abs

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
 *     selectedIndex = hour,
 *     onSelect = { hour = it },
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
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    visibleItems: Int = 5,
    itemHeight: Dp = 40.dp,
) {
    require(visibleItems % 2 == 1) { "visibleItems must be odd so a row can sit in the centre" }
    if (items.isEmpty()) return

    val edgeItems = visibleItems / 2
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    val flingBehavior = rememberSnapFlingBehavior(listState)
    val feedback = Feedback
    val currentOnSelect by rememberUpdatedState(onSelect)

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

    LaunchedEffect(selectedIndex) {
        if (selectedIndex != centredIndex && !listState.isScrollInProgress) {
            listState.scrollToItem(selectedIndex)
        }
    }

    Box(modifier.height(itemHeight * visibleItems), contentAlignment = Alignment.Center) {
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
