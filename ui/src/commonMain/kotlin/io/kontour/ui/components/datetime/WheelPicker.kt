package io.kontour.ui.components.datetime

import androidx.compose.foundation.background
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.Text
import io.kontour.ui.interaction.Feedback
import io.kontour.ui.interaction.FeedbackIntent
import io.kontour.ui.theme.Theme
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
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
 * @param infinite Whether the drum wraps: past December comes January, and there
 *   is no end to reach.
 *
 *   Genuinely endless rather than a very long list. The usual trick is a lazy
 *   list of a few million copies, which runs out eventually, puts the drum
 *   somewhere absurd in its own scroll range, and has an end stop that a
 *   determined flick can still find. This keeps a scroll offset in pixels and
 *   takes it modulo the drum's circumference, so there is no end to reach and
 *   exactly [visibleItems] + 2 rows exist at any moment however long the user
 *   spins it.
 *
 *   Only worth it for a value that is genuinely cyclic — hours, minutes, months,
 *   compass points. A list of countries that wraps around is a list you can
 *   never tell you have read all of.
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
    infinite: Boolean = false,
) {
    require(visibleItems % 2 == 1) { "visibleItems must be odd so a row can sit in the centre" }
    if (items.isEmpty()) return

    if (infinite) {
        InfiniteWheel(
            items = items,
            selected = selected,
            onSelectedChange = onSelectedChange,
            label = label,
            modifier = modifier,
            visibleItems = visibleItems,
            itemHeight = itemHeight,
        )
        return
    }

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
            /**
             * The wheel stops here, whether or not the drum could use it.
             *
             * `containment` below catches everything the *list* dispatched and
             * did not consume, which is the whole story for a finger. A mouse
             * wheel is not: the list handles it in a pointer node of its own,
             * and at an end stop that node simply declines the event rather
             * than dispatching an unconsumed scroll. Nothing reaches nested
             * scroll, the event carries on out to the nearest scrollable
             * ancestor, and the page moves — which is the report, and why it
             * only showed up on desktop.
             *
             * This node is the list's parent, so on the main pass it runs
             * *after* the list has had its turn: a scroll the drum used is
             * already consumed and this changes nothing, and one it declined
             * stops here instead of going up.
             */
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Scroll) {
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
            }
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
        // Drawn before the rows, which is the whole point: the centred value sits
        // *on* the band at full contrast rather than under a wash of it. This
        // used to be an empty `Box` emitted after the list — a comment saying it
        // sat behind the text, over a component that drew nothing at all.
        Box(
            Modifier
                .fillMaxWidth()
                .height(itemHeight)
                .clip(Theme.shapes.medium)
                .background(Theme.colours.surfaceSunken)
        )

        LazyColumn(
            state = listState,
            flingBehavior = flingBehavior,
            contentPadding = PaddingValues(vertical = itemHeight * edgeItems),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(items.size) { index ->
                val distance = abs(index - centredIndex).toFloat()
                val fade = wheelFade(distance)
                val shrink = wheelShrink(distance)

                Box(
                    Modifier.fillMaxWidth().height(itemHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label(items[index]),
                        style = Theme.typography.titleLarge,
                        colour = Theme.colours.content,
                        modifier = Modifier.alpha(fade).scale(shrink),
                    )
                }
            }
        }

        // Carries the drum's value for a screen reader, and nothing else. The
        // band itself is drawn above, behind the rows.
        Box(
            Modifier
                .matchParentSize()
                .semantics {
                    stateDescription = label(items[centredIndex])
                }
        )
    }
}

/**
 * A drum with no ends.
 *
 * Not a lazy list of a great many copies, which is the usual answer and is what
 * the report asked for something better than: a few million rows still runs out,
 * puts the user somewhere meaningless in a scroll range, and leaves an end stop
 * that a hard enough flick finds. Here the *offset* wraps rather than the
 * content: the drum's position is a number of pixels taken modulo its
 * circumference, so there is nothing to reach, and the rows are drawn from that
 * number rather than kept in a list at all.
 *
 * The cost is fixed. [visibleItems] + 2 rows exist at any moment — one spare at
 * each end for the row half in view — whether the user has turned it once or for
 * a minute, and none of them are recycled because none of them are ever
 * destroyed. There is no lazy layout here and nothing to key.
 *
 * The offset is only read in `offset {}` and in the two derived values that
 * decide what the rows say, so turning the drum lays out rather than recomposing
 * the list.
 */
@Composable
private fun <T> InfiniteWheel(
    items: List<T>,
    selected: Int,
    onSelectedChange: (Int) -> Unit,
    label: (T) -> String,
    modifier: Modifier,
    visibleItems: Int,
    itemHeight: Dp,
) {
    val density = LocalDensity.current
    val itemPx = with(density) { itemHeight.toPx() }
    val feedback = Feedback
    val motion = Theme.motion
    val currentOnSelect by rememberUpdatedState(onSelectedChange)
    val scope = rememberCoroutineScope()

    /**
     * How far the drum has turned, in pixels, with the selected row centred.
     *
     * Unbounded on purpose. It is only ever read modulo the circumference, so
     * nothing depends on its absolute value and it has no ends to be clamped
     * to — which is the whole trick, and the only place a "really long list"
     * would have had to keep a range.
     */
    val offset = remember { Animatable(selected * itemPx) }

    /** The row the centre line is nearest, wrapped into the list. */
    val centredIndex by remember(items.size) {
        derivedStateOf { wrap((offset.value / itemPx).roundToInt(), items.size) }
    }

    LaunchedEffect(items.size) {
        snapshotFlow { centredIndex }.collect { index ->
            feedback.perform(FeedbackIntent.Tick)
            currentOnSelect(index)
        }
    }

    // A caller setting the value moves the drum by the *short* way round, which
    // on a wheel that wraps is a thing that has to be chosen rather than
    // falling out of the arithmetic: 23:00 to 00:00 is one row forward, not
    // twenty-three back.
    LaunchedEffect(selected) {
        if (selected != centredIndex && !offset.isRunning) {
            val turns = shortestTurn(centredIndex, selected, items.size)
            offset.animateTo(
                targetValue = offset.value + turns * itemPx,
                animationSpec = motion.springOrTween(motion.springSnappy),
            )
        }
    }

    val scrollState = rememberScrollableState { delta ->
        // Every pixel, always. There is no end to over-scroll past, so nothing
        // is ever left over for a parent to take — which is the other half of
        // the wheel-escaping report, answered by construction rather than by a
        // nested-scroll connection that has to catch it.
        scope.launch { offset.snapTo(offset.value - delta) }
        delta
    }

    // Settle onto a row when the finger and the fling are both done. The drum
    // is never left between two numbers.
    LaunchedEffect(scrollState.isScrollInProgress) {
        if (!scrollState.isScrollInProgress) {
            val nearest = (offset.value / itemPx).roundToInt() * itemPx
            if (offset.value != nearest) {
                offset.animateTo(nearest, motion.springOrTween(motion.springSnappy))
            }
        }
    }

    val halfVisible = visibleItems / 2
    val rows = visibleItems + 2

    Box(
        modifier
            .height(itemHeight * visibleItems)
            .scrollable(
                state = scrollState,
                orientation = Orientation.Vertical,
                // Reversed: dragging up turns the drum forwards, the way it does
                // on every wheel in the library and on the platform's own.
                reverseDirection = true,
            ),
        contentAlignment = Alignment.TopStart,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(itemHeight)
                .align(Alignment.Center)
                .clip(Theme.shapes.medium)
                .background(Theme.colours.surfaceSunken)
        )

        Column(
            Modifier
                .fillMaxWidth()
                .offset {
                    val turned = offset.value / itemPx
                    val first = floor(turned).toInt() - halfVisible - 1
                    IntOffset(
                        x = 0,
                        y = ((first - turned) * itemPx + halfVisible * itemPx).roundToInt(),
                    )
                }
        ) {
            // Read here rather than in `offset {}` so the rows recompose only
            // when the drum has actually turned past a row, not on every pixel.
            val turned = offset.value / itemPx
            val first = floor(turned).toInt() - halfVisible - 1

            for (row in 0 until rows) {
                val position = first + row
                val index = wrap(position, items.size)
                val distance = abs(position - turned)

                Box(
                    Modifier.fillMaxWidth().height(itemHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label(items[index]),
                        style = Theme.typography.titleLarge,
                        colour = Theme.colours.content,
                        modifier = Modifier.alpha(wheelFade(distance)).scale(wheelShrink(distance)),
                    )
                }
            }
        }

        Box(
            Modifier
                .matchParentSize()
                .semantics { stateDescription = label(items[centredIndex]) }
        )
    }
}

/** Positive modulo: `-1` in a list of twelve is the last row, not a crash. */
private fun wrap(index: Int, size: Int): Int = ((index % size) + size) % size

/**
 * How many rows forward — or back, as a negative — from [from] to [to] the short
 * way round a drum of [size].
 *
 * Ten past two on a twelve-hour drum is two rows back, not ten forward.
 */
private fun shortestTurn(from: Int, to: Int, size: Int): Int {
    val forward = wrap(to - from, size)
    return if (forward * 2 > size) forward - size else forward
}

/**
 * How much a row fades with its distance, in rows, from the centre.
 *
 * Falls away steeply: the neighbour is clearly secondary and anything two rows
 * out is barely there. Three anchors — 1, 0.45, 0.2 — interpolated rather than
 * stepped, because [InfiniteWheel] knows a row's distance to a fraction and a
 * drum that dimmed in three steps as it turned would read as a list with a
 * filter on it rather than as a curved surface. The finite wheel passes whole
 * numbers and lands exactly on the anchors, so the two look the same at rest.
 */
internal fun wheelFade(distance: Float): Float = when {
    distance <= 1f -> lerp(1f, 0.45f, distance)
    else -> lerp(0.45f, 0.2f, (distance - 1f).coerceAtMost(1f))
}

/** See [wheelFade]. The same three anchors, for scale. */
internal fun wheelShrink(distance: Float): Float = when {
    distance <= 1f -> lerp(1f, 0.86f, distance)
    else -> lerp(0.86f, 0.74f, (distance - 1f).coerceAtMost(1f))
}

private fun lerp(start: Float, stop: Float, fraction: Float): Float =
    start + (stop - start) * fraction.coerceIn(0f, 1f)
