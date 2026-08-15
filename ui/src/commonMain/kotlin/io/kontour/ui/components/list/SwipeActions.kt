package io.kontour.ui.components.list

import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.ui.a11y.contentColorFor
import io.kontour.ui.foundation.Icon
import io.kontour.ui.foundation.Surface
import io.kontour.ui.foundation.Text
import io.kontour.ui.interaction.FeedbackIntent
import io.kontour.ui.interaction.LocalFeedback
import io.kontour.ui.theme.Theme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** One action revealed by swiping a row. */
@Immutable
class SwipeAction(
    val label: String,
    val icon: ImageVector,
    val onAction: () -> Unit,
    val background: Color,
    /**
     * Fires when the row is swiped nearly all the way, without waiting for a
     * tap. Only one action per side may be, and it should be the one a full
     * swipe obviously means — delete on a delete row.
     */
    val isFullSwipeAction: Boolean = false,
)

/** Where a swiped row has settled. */
enum class SwipeValue { Start, Resting, End }

@Stable
class SwipeActionsState internal constructor(
    internal val anchoredState: AnchoredDraggableState<SwipeValue>,
) {
    val currentValue: SwipeValue get() = anchoredState.settledValue
    val offset: Float get() = anchoredState.offset

    /**
     * Where the row should go once it has anchors.
     *
     * The same trap as [io.kontour.ui.sheet.SheetState]: anchors depend on the
     * row's measured width, so a request made from a `LaunchedEffect` arrives
     * before there is anywhere to move to and would be dropped silently.
     */
    internal var pending: SwipeValue? = null
        private set

    /** Slides back to rest. */
    suspend fun reset() = animateTo(SwipeValue.Resting)

    /**
     * Slides to [value].
     *
     * For revealing the actions without a gesture — a "here is what this row
     * does" hint on first run, or closing every other row when one is opened.
     */
    suspend fun animateTo(value: SwipeValue) {
        if (anchoredState.anchors.hasPositionFor(value)) {
            anchoredState.animateTo(value)
        } else {
            pending = value
        }
    }

    internal suspend fun deliverPending() {
        val target = pending ?: return
        pending = null
        if (anchoredState.anchors.hasPositionFor(target)) anchoredState.animateTo(target)
    }
}

@Composable
fun rememberSwipeActionsState(): SwipeActionsState {
    val anchored = remember {
        AnchoredDraggableState(initialValue = SwipeValue.Resting)
    }
    return remember { SwipeActionsState(anchored) }
}

object SwipeActionsDefaults {
    /** How wide one action's target is. Two side by side is 176dp of swipe. */
    val ActionWidth: Dp = 88.dp

    /** Fraction of the row's width past which a full swipe fires. */
    const val FullSwipeThreshold: Float = 0.6f
}

/**
 * Reveals actions when a row is swiped sideways.
 *
 * ```kotlin
 * SwipeActions(
 *     end = listOf(
 *         SwipeAction("Delete", Tabler.Outline.Trash, ::delete, Theme.colors.danger.solid,
 *             isFullSwipeAction = true),
 *     ),
 * ) {
 *     ListItem(headline = stop.name, onClick = { open(stop) })
 * }
 * ```
 *
 * **Nothing here may be the only way to reach an action.** A swipe is invisible,
 * has no keyboard equivalent and no pointer equivalent — it is a shortcut for
 * people who already know it is there. Every action also gets a *custom
 * accessibility action* on the row, so a screen reader can reach it, but that
 * covers assistive tech and not a sighted mouse user. Put the same actions in a
 * menu or a detail screen.
 *
 * Built on the same `AnchoredDraggableState` as
 * [io.kontour.ui.sheet.SheetState], so a swipe and a sheet drag behave the same
 * way — same thresholds, same settle, same fling.
 *
 * @param start Actions revealed by swiping toward the trailing edge, following
 *   the layout direction. Conventionally the constructive ones.
 * @param end Revealed by swiping toward the leading edge. Conventionally the
 *   destructive ones, since that is the direction people already flick to
 *   delete.
 */
@Composable
fun SwipeActions(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    start: List<SwipeAction> = emptyList(),
    end: List<SwipeAction> = emptyList(),
    state: SwipeActionsState = rememberSwipeActionsState(),
    shape: Shape = Theme.shapes.medium,
    actionWidth: Dp = SwipeActionsDefaults.ActionWidth,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val motion = Theme.motion
    val scope = rememberCoroutineScope()
    val feedback = LocalFeedback.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    var width by remember { mutableFloatStateOf(0f) }
    val actionWidthPx = with(density) { actionWidth.toPx() }

    // Physical direction: "start" actions are revealed by dragging toward the
    // trailing edge, which is +x in LTR and -x in RTL.
    val startTravel = start.size * actionWidthPx * (if (isRtl) -1f else 1f)
    val endTravel = -end.size * actionWidthPx * (if (isRtl) -1f else 1f)

    LaunchedEffect(width, start.size, end.size, isRtl) {
        if (width <= 0f) return@LaunchedEffect
        state.anchoredState.updateAnchors(
            DraggableAnchors {
                SwipeValue.Resting at 0f
                if (start.isNotEmpty()) SwipeValue.Start at startTravel
                if (end.isNotEmpty()) SwipeValue.End at endTravel
            }
        )
        state.deliverPending()
    }

    // A full swipe commits without waiting for a tap.
    val fullStart = start.firstOrNull { it.isFullSwipeAction }
    val fullEnd = end.firstOrNull { it.isFullSwipeAction }
    val onFull by rememberUpdatedState { action: SwipeAction ->
        feedback.perform(FeedbackIntent.Confirm)
        action.onAction()
    }

    LaunchedEffect(state, width) {
        if (width <= 0f) return@LaunchedEffect
        val threshold = width * SwipeActionsDefaults.FullSwipeThreshold
        snapshotFlow { state.anchoredState.offset }.collect { offset ->
            if (offset.isNaN()) return@collect
            val past = kotlin.math.abs(offset) >= threshold
            if (!past) return@collect
            val action = if (offset > 0f) fullStart else fullEnd
            if (action != null) {
                onFull(action)
                state.reset()
            }
        }
    }

    val fling = AnchoredDraggableDefaults.flingBehavior(
        state = state.anchoredState,
        positionalThreshold = { distance -> distance * 0.4f },
        animationSpec = motion.springOrTween(motion.springDefault),
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .onSizeChanged { width = it.width.toFloat() }
            .semantics {
                // The only route to these that does not require knowing the
                // gesture exists.
                customActions = (start + end).map { action ->
                    CustomAccessibilityAction(action.label) {
                        action.onAction()
                        true
                    }
                }
            }
    ) {
        val offset = state.anchoredState.offset
        val settled = if (offset.isNaN()) 0f else offset

        // Whichever side is being revealed, drawn beneath the row.
        val revealed = when {
            settled > 0f -> start
            settled < 0f -> end
            else -> emptyList()
        }
        if (revealed.isNotEmpty()) {
            Row(
                modifier = Modifier.matchParentSize(),
                horizontalArrangement = if (settled > 0f) {
                    Arrangement.Start
                } else {
                    Arrangement.End
                },
            ) {
                revealed.forEach { action ->
                    SwipeActionButton(
                        action = action,
                        width = actionWidth,
                        onClick = {
                            action.onAction()
                            scope.launch { state.reset() }
                        },
                    )
                }
            }
        }

        Box(
            Modifier
                .offset { IntOffset(settled.roundToInt(), 0) }
                .anchoredDraggable(
                    state = state.anchoredState,
                    orientation = Orientation.Horizontal,
                    enabled = enabled && (start.isNotEmpty() || end.isNotEmpty()),
                    flingBehavior = fling,
                )
        ) {
            content()
        }
    }
}

@Composable
private fun RowScope.SwipeActionButton(
    action: SwipeAction,
    width: Dp,
    onClick: () -> Unit,
) {
    val content = contentColorFor(action.background)

    Surface(
        modifier = Modifier
            .width(width)
            .fillMaxHeight(),
        color = action.background,
        contentColor = content,
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .clickableAction(onClick, action.label)
                .padding(Theme.spacing.xs),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Theme.spacing.xxs),
            ) {
                Icon(
                    imageVector = action.icon,
                    contentDescription = null,
                    size = Theme.sizing.iconLarge,
                    tint = content,
                )
                Text(
                    text = action.label,
                    style = Theme.typography.labelSmall,
                    color = content,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Clickable without a role, because the row above already exposes this as a
 * custom accessibility action — announcing it twice makes one action sound like
 * two.
 */
private fun Modifier.clickableAction(onClick: () -> Unit, label: String): Modifier =
    clickable(onClickLabel = label, onClick = onClick)

/**
 * A row that can be swiped away entirely.
 *
 * ```kotlin
 * SwipeToDismiss(
 *     onDismissRequest = { viewModel.remove(favourite) },
 *     label = "Remove favourite",
 *     icon = Tabler.Outline.Trash,
 * ) {
 *     ListItem(headline = favourite.name)
 * }
 * ```
 *
 * A [SwipeActions] with one destructive action that fires on a full swipe. The
 * separate name is worth it because the two mean different things to the user:
 * swipe-to-reveal is a menu, swipe-to-dismiss is a commitment.
 *
 * Give the user a way back. A dismissal with no undo is a data-loss bug wearing
 * a gesture — pair it with a
 * [io.kontour.ui.overlay.Toast] carrying an undo action.
 */
@Composable
fun SwipeToDismiss(
    onDismissRequest: () -> Unit,
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    background: Color = Theme.colors.danger.solid,
    state: SwipeActionsState = rememberSwipeActionsState(),
    shape: Shape = Theme.shapes.medium,
    content: @Composable () -> Unit,
) {
    SwipeActions(
        modifier = modifier,
        end = listOf(
            SwipeAction(
                label = label,
                icon = icon,
                onAction = onDismissRequest,
                background = background,
                isFullSwipeAction = true,
            )
        ),
        state = state,
        enabled = enabled,
        shape = shape,
        content = content,
    )
}
