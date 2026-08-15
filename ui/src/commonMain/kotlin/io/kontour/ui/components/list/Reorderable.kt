package io.kontour.ui.components.list

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import io.kontour.ui.interaction.FeedbackIntent
import io.kontour.ui.interaction.LocalFeedback
import io.kontour.ui.theme.Theme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Drives drag-to-reorder over a `LazyColumn`.
 *
 * ```kotlin
 * val listState = rememberLazyListState()
 * val reorder = rememberReorderableState(listState) { from, to ->
 *     viewModel.move(from, to)
 * }
 *
 * LazyColumn(state = listState) {
 *     itemsIndexed(favourites, key = { _, it -> it.id }) { index, favourite ->
 *         ReorderableItem(reorder, index) {
 *             ListItem(headline = favourite.name)
 *         }
 *     }
 * }
 * ```
 *
 * [onMove] fires *during* the drag, every time the dragged row passes another —
 * so the list reorders live under the finger rather than snapping into place on
 * release. That means the caller's list is the source of truth throughout, and
 * there is no separate "pending order" to reconcile.
 */
@Stable
class ReorderableState internal constructor(
    internal val listState: LazyListState,
    private val onMove: (from: Int, to: Int) -> Unit,
) {
    /** The index being dragged, or null. */
    var draggingIndex: Int? by mutableStateOf(null)
        private set

    internal var dragOffset by mutableFloatStateOf(0f)

    private var draggingKey: Any? = null

    internal fun start(index: Int) {
        draggingIndex = index
        draggingKey = itemAt(index)?.key
        dragOffset = 0f
    }

    internal fun drag(delta: Float) {
        val index = draggingIndex ?: return
        dragOffset += delta

        val dragged = itemAt(index) ?: return
        val draggedCentre = dragged.offset + dragOffset + dragged.size / 2f

        // The row the dragged one is now sitting over.
        val target = listState.layoutInfo.visibleItemsInfo.firstOrNull { candidate ->
            candidate.index != index &&
                draggedCentre >= candidate.offset &&
                draggedCentre <= candidate.offset + candidate.size
        } ?: return

        onMove(index, target.index)
        // The list has reordered underneath us; the dragged row is now where
        // the target was, so the visual offset shrinks by the distance moved.
        dragOffset -= (target.offset - dragged.offset)
        draggingIndex = target.index
    }

    internal fun stop() {
        draggingIndex = null
        draggingKey = null
        dragOffset = 0f
    }

    internal fun offsetFor(index: Int): Float =
        if (index == draggingIndex) dragOffset else 0f

    private fun itemAt(index: Int): LazyListItemInfo? =
        listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }

    /** Moves the item at [index] one place toward the start. For assistive tech. */
    internal fun moveUp(index: Int): Boolean {
        if (index <= 0) return false
        onMove(index, index - 1)
        return true
    }

    /** Moves the item at [index] one place toward the end. */
    internal fun moveDown(index: Int, count: Int): Boolean {
        if (index >= count - 1) return false
        onMove(index, index + 1)
        return true
    }
}

@Composable
fun rememberReorderableState(
    listState: LazyListState,
    onMove: (from: Int, to: Int) -> Unit,
): ReorderableState {
    val move by rememberUpdatedState(onMove)
    return remember(listState) { ReorderableState(listState) { from, to -> move(from, to) } }
}

/**
 * Wraps one row so it can be dragged to a new position.
 *
 * ```kotlin
 * ReorderableItem(reorder, index, itemCount = favourites.size) {
 *     ListItem(headline = favourite.name)
 * }
 * ```
 *
 * A **long press** starts the drag, not a touch on a dedicated handle. A handle
 * is a smaller target and one more thing to discover; a long press is the
 * gesture people already try. It fires a haptic on pickup, which is what
 * distinguishes "I am now dragging" from "nothing happened".
 *
 * The dragged row lifts — scaled slightly, shadowed, and above its neighbours —
 * because a row that moves without lifting reads as the list glitching rather
 * than as the user holding something.
 *
 * **Move up and move down are also custom accessibility actions.** A drag is not
 * a gesture a screen reader can perform, and reordering with no alternative
 * makes a whole feature unreachable. Pass [itemCount] so "move down" can be
 * withheld on the last row.
 */
@Composable
fun ReorderableItem(
    state: ReorderableState,
    index: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    itemCount: Int = Int.MAX_VALUE,
    moveUpLabel: String = "Move up",
    moveDownLabel: String = "Move down",
    content: @Composable () -> Unit,
) {
    val feedback = LocalFeedback.current
    val motion = Theme.motion
    val dragging = state.draggingIndex == index

    val lift by animateFloatAsState(
        targetValue = if (dragging) 1f else 0f,
        animationSpec = motion.springOrTween(motion.springSnappy),
        label = "reorderLift",
    )

    Box(
        modifier = modifier
            .zIndex(if (dragging) 1f else 0f)
            .graphicsLayer {
                translationY = state.offsetFor(index)
                // A small lift, not a large one. The row is still in the list.
                scaleX = 1f + 0.02f * lift
                scaleY = 1f + 0.02f * lift
                shadowElevation = 8f * lift
            }
            .semantics {
                customActions = buildList {
                    if (index > 0) {
                        add(CustomAccessibilityAction(moveUpLabel) { state.moveUp(index) })
                    }
                    if (index < itemCount - 1) {
                        add(
                            CustomAccessibilityAction(moveDownLabel) {
                                state.moveDown(index, itemCount)
                            }
                        )
                    }
                }
            }
            .then(
                if (enabled) {
                    Modifier.pointerInput(state, index) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                feedback.perform(FeedbackIntent.LongPress)
                                state.start(index)
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                state.drag(amount.y)
                            },
                            onDragEnd = {
                                feedback.perform(FeedbackIntent.GestureEnd)
                                state.stop()
                            },
                            onDragCancel = { state.stop() },
                        )
                    }
                } else {
                    Modifier
                }
            )
    ) {
        content()
    }
}
