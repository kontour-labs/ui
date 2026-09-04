package io.kontour.ui.components.list

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyItemScope
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import io.kontour.ui.a11y.minimumTouchTarget
import io.kontour.ui.foundation.Icon
import io.kontour.ui.input.LocalInputModality
import io.kontour.ui.interaction.FeedbackDispatcher
import io.kontour.ui.interaction.FeedbackIntent
import io.kontour.ui.interaction.LocalFeedback
import io.kontour.ui.theme.Theme
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

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
 *             ListItem { +favourite.name }
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
    private val onReorder: () -> Unit = {},
) {
    /** The index being dragged, or null. */
    var draggingIndex: Int? by mutableStateOf(null)
        private set

    internal var dragOffset by mutableFloatStateOf(0f)

    private var draggingKey: Any? = null

    /**
     * Begins a drag on the row at [index], as a long press would.
     *
     * Public because a state a caller cannot reach is a state nobody can
     * verify. Until this was, the only way into a drag was the gesture, so the
     * lifted row could not be tested, photographed for the documentation, or
     * driven from a keyboard affordance an app wanted to add. Pair with [stop].
     */
    fun start(index: Int) {
        draggingIndex = index
        draggingKey = itemAt(index)?.key
        dragOffset = 0f
    }

    /**
     * Moves an in-progress drag by [delta] pixels. Reorders as it crosses rows.
     *
     * A move can happen more than once in a single drag — that is the whole
     * point — and each one is reported through [onReorder] so the caller can
     * click. The row crossing another is the moment the user is waiting to
     * feel; the pickup and the drop are the ones they can already see.
     */
    fun drag(delta: Float) {
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
        onReorder()
    }

    /** Ends the drag and settles the row. */
    fun stop() {
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
    val feedback = LocalFeedback.current
    return remember(listState) {
        ReorderableState(
            listState = listState,
            onMove = { from, to -> move(from, to) },
            // Every row crossed clicks, the way a stepped slider's detents do.
            // A reorder is a discrete event happening under a finger that is
            // not looking for it — the user is watching the row they are
            // holding, not the gap it just left.
            onReorder = { feedback.perform(FeedbackIntent.Tick) },
        )
    }
}

/** Which side of a row its drag handle sits on. */
enum class ReorderHandleSide { Start, End }

/**
 * Wraps one row so it can be dragged to a new position.
 *
 * ```kotlin
 * LazyColumn(state = listState) {
 *     itemsIndexed(favourites, key = { _, it -> it.id }) { index, favourite ->
 *         ReorderableItem(reorder, index, itemCount = favourites.size) {
 *             ListItem { +favourite.name }
 *         }
 *     }
 * }
 * ```
 *
 * **A `LazyItemScope` extension**, and it always was in everything but its
 * signature: it reads the row's position out of a `LazyListState`, which knows
 * nothing about a row that is not in the list. Being one is what lets it call
 * `animateItem()` on the rows that are *not* being dragged, which is how the
 * gap left behind gets filled by neighbours sliding into it rather than
 * teleporting.
 *
 * ### How a drag starts
 *
 * With a [handleIcon], from the handle, immediately. Without one, from a **long
 * press** on the row — on touch. A mouse gets neither: it presses and drags,
 * because that is what a mouse does, and holding a button still for half a
 * second to pick up a row is a gesture nobody has ever tried. Dragging a lazy
 * list with a mouse is not a thing desktops do, so there is nothing for an
 * immediate drag to fight with there.
 *
 * The dragged row lifts — scaled slightly, shadowed, and above its neighbours —
 * because a row that moves without lifting reads as the list glitching rather
 * than as the user holding something.
 *
 * **Move up and move down are also custom accessibility actions.** A drag is not
 * a gesture a screen reader can perform, and reordering with no alternative
 * makes a whole feature unreachable. Pass [itemCount] so "move down" can be
 * withheld on the last row.
 *
 * @param shape The row's own shape, for the shadow it casts while lifted. A
 *   `graphicsLayer` shadow is drawn to the *layer's* shape, which is a rectangle
 *   unless it is told otherwise — so a rounded row, or the rounded top and
 *   bottom of a grouped list, cast a square shadow with corners sticking out
 *   past the row. Pass whatever the content is clipped to.
 * @param handleIcon A grip to drag from. Null leaves the whole row draggable,
 *   which is the better default on touch and the worse one anywhere a row also
 *   has a tap action of its own.
 * @param handleSide Which end the handle sits at. [ReorderHandleSide.End] by
 *   default: a handle on the leading edge competes with whatever the row leads
 *   with, which is usually an icon or an avatar.
 */
@Composable
fun LazyItemScope.ReorderableItem(
    state: ReorderableState,
    index: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    itemCount: Int = Int.MAX_VALUE,
    shape: Shape = RectangleShape,
    handleIcon: ImageVector? = null,
    handleSide: ReorderHandleSide = ReorderHandleSide.End,
    handleLabel: String = Theme.strings.moveUp,
    moveUpLabel: String = Theme.strings.moveUp,
    moveDownLabel: String = Theme.strings.moveDown,
    content: @Composable () -> Unit,
) {
    val feedback = LocalFeedback.current
    val motion = Theme.motion
    val modality = LocalInputModality.current
    val dragging = state.draggingIndex == index

    /**
     * The row's index, read at gesture time rather than captured.
     *
     * This is the whole of the "it drops after one position" report. The
     * gesture used to be keyed on `index` — and reordering *changes* a row's
     * index, which is the gesture succeeding. So the first move restarted the
     * `pointerInput` node, which cancelled the drag that had just caused it. One
     * position, then dropped, every time, and never on the second attempt
     * because by then the finger was already down.
     */
    val currentIndex by rememberUpdatedState(index)

    val lift by animateFloatAsState(
        targetValue = if (dragging) 1f else 0f,
        animationSpec = motion.springOrTween(motion.springSnappy),
        label = "reorderLift",
    )

    val drags = Modifier.reorderDrag(
        state = state,
        enabled = enabled,
        immediate = handleIcon != null || !modality.needsLargeTargets,
        currentIndex = { currentIndex },
        feedback = feedback,
    )

    Box(
        modifier = modifier
            // Not on the row being dragged: it is following a finger, and a
            // placement animation would have it chasing itself. Every other row
            // is being *moved* by the reorder, which is exactly what this is
            // for — the gap the dragged row left gets filled by its neighbours
            // sliding into it rather than appearing there.
            .then(if (dragging) Modifier else Modifier.animateItem())
            .zIndex(if (dragging) 1f else 0f)
            .graphicsLayer {
                translationY = state.offsetFor(index)
                // A small lift, not a large one. The row is still in the list.
                scaleX = 1f + 0.02f * lift
                scaleY = 1f + 0.02f * lift
                shadowElevation = 8f * lift
                // See `shape`: without this the shadow is a rectangle whatever
                // the row is.
                this.shape = shape
                clip = false
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
            // Only when there is no handle. With one, the row itself stays
            // free — which is the point of asking for a handle: a row that is
            // also a link cannot afford to swallow a long press.
            .then(if (handleIcon == null) drags else Modifier)
    ) {
        if (handleIcon == null) {
            content()
            return@Box
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (handleSide == ReorderHandleSide.Start) {
                ReorderGrip(handleIcon, handleLabel, enabled, drags)
            }
            Box(Modifier.weight(1f)) { content() }
            if (handleSide == ReorderHandleSide.End) {
                ReorderGrip(handleIcon, handleLabel, enabled, drags)
            }
        }
    }
}

/**
 * The grip itself: a target, not a picture.
 *
 * `minimumTouchTarget` around a small glyph, because a handle is the one part of
 * a reorderable row that has to be hit deliberately — and it is `clearAndSet`
 * rather than merged, because the row already carries move-up and move-down as
 * custom actions and a screen reader has no use for a third route that needs a
 * drag.
 */
@Composable
private fun ReorderGrip(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    drags: Modifier,
) {
    Box(
        modifier = Modifier
            .minimumTouchTarget()
            .clearAndSetSemantics { }
            .then(drags),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (enabled) Theme.colours.contentMuted else Theme.colours.contentDisabled,
            size = Theme.sizing.iconMedium,
        )
    }
}

/**
 * The drag gesture, on the row or on its handle.
 *
 * [immediate] is the difference between a mouse and a fingertip, and between a
 * handle and a bare row. A long press exists to keep a drag from stealing a
 * scroll; neither a mouse over a lazy list nor a press on a dedicated grip has a
 * scroll to steal, so neither should have to wait half a second for one.
 */
private fun Modifier.reorderDrag(
    state: ReorderableState,
    enabled: Boolean,
    immediate: Boolean,
    currentIndex: () -> Int,
    feedback: FeedbackDispatcher,
): Modifier = if (!enabled) {
    this
} else {
    // Keyed on the state and on which gesture to use, and *not* on the index —
    // see `currentIndex`.
    this.pointerInput(state, immediate) {
        val onStart: (Offset) -> Unit = {
            feedback.perform(FeedbackIntent.LongPress)
            state.start(currentIndex())
        }
        val onDrag: (PointerInputChange, Offset) -> Unit = { change, amount ->
            change.consume()
            state.drag(amount.y)
        }
        val onEnd: () -> Unit = {
            feedback.perform(FeedbackIntent.GestureEnd)
            state.stop()
        }
        if (immediate) {
            detectDragGestures(
                onDragStart = onStart,
                onDrag = onDrag,
                onDragEnd = onEnd,
                onDragCancel = { state.stop() },
            )
        } else {
            detectDragGesturesAfterLongPress(
                onDragStart = onStart,
                onDrag = onDrag,
                onDragEnd = onEnd,
                onDragCancel = { state.stop() },
            )
        }
    }
}
