package io.kontour.ui.components.list

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
    /**
     * Whether swiping clear past the reveal commits this action.
     *
     * True by default. A swipe that carries the row the full width of the screen
     * is not something a thumb does by accident, and requiring every action to
     * opt in meant the gesture worked in one direction and silently did nothing
     * in the other — which reads as a broken control rather than as a setting.
     *
     * Set false for an action that should always need the deliberate second tap.
     */
    val isFullSwipeAction: Boolean = true,
)

/** Where a swiped row has settled. */
enum class SwipeValue {
    /** Start actions revealed, waiting for a tap. */
    Start,

    Resting,

    /** End actions revealed, waiting for a tap. */
    End,

    /** Swiped clear past the start actions — the first of them commits. */
    StartCommitted,

    /** Swiped clear past the end actions — the first of them commits. */
    EndCommitted,
}

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

/**
 * Remembers a [SwipeActionsState].
 *
 * @param initialValue Where the row starts. [SwipeActionsState.animateTo] is the
 *   way to move it *later*; this is the way to have it never be anywhere else,
 *   which is a different thing and the one that has no workaround. An animation
 *   needs frames to run in, and there are contexts that have none: a screenshot
 *   is a single moment, and a row restored from saved state should be where it
 *   was rather than sliding there. `rememberSheetState` takes `initialDetent`
 *   for the same reason; this factory was the odd one out.
 *
 *   The row starts here without animating, because the anchors are attached
 *   after the first measure and `updateAnchors` settles on the current value
 *   rather than travelling to it.
 */
@Composable
fun rememberSwipeActionsState(
    initialValue: SwipeValue = SwipeValue.Resting,
): SwipeActionsState {
    val anchored = remember {
        AnchoredDraggableState(initialValue = initialValue)
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
 *     ListItem(onClick = { open(stop) }) { +stop.name }
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

    // A full swipe commits without waiting for a tap.
    val fullStart = start.firstOrNull { it.isFullSwipeAction }
    val fullEnd = end.firstOrNull { it.isFullSwipeAction }

    LaunchedEffect(width, start.size, end.size, isRtl, fullStart, fullEnd) {
        if (width <= 0f) return@LaunchedEffect
        // Past the reveal, and off the far edge. There has to be an *anchor*
        // out there for the drag to reach it: the commit threshold used to be
        // measured against the row's width while the anchors only spanned the
        // reveal (88dp per action), so `AnchoredDraggableState` clamped the
        // offset long before the threshold and the commit could never fire on
        // anything wider than about 147dp. Which is every list row.
        val commit = width * (if (isRtl) -1f else 1f)
        state.anchoredState.updateAnchors(
            DraggableAnchors {
                SwipeValue.Resting at 0f
                if (start.isNotEmpty()) SwipeValue.Start at startTravel
                if (end.isNotEmpty()) SwipeValue.End at endTravel
                if (fullStart != null) SwipeValue.StartCommitted at commit
                if (fullEnd != null) SwipeValue.EndCommitted at -commit
            }
        )
        state.deliverPending()
    }

    val onFull by rememberUpdatedState { action: SwipeAction ->
        feedback.perform(FeedbackIntent.Confirm)
        action.onAction()
    }

    // Fires on *settling*, not mid-drag. The old version watched the raw offset
    // and committed the instant it crossed a threshold, so an action ran while
    // the user's finger was still down and could still have been dragged back.
    LaunchedEffect(state) {
        snapshotFlow { state.anchoredState.settledValue }.collect { settledAt ->
            val action = when (settledAt) {
                SwipeValue.StartCommitted -> fullStart
                SwipeValue.EndCommitted -> fullEnd
                else -> null
            } ?: return@collect
            onFull(action)
            state.reset()
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
            // The whole revealed area in the outermost action's colour, under
            // the buttons rather than instead of them.
            //
            // The strip is only `n × 88dp` wide and pinned to one edge, so
            // everything beyond it — including the space behind the row's own
            // rounded corners, and the entire travel of a committing swipe —
            // used to be bare page. The colour appeared to stop at the corner
            // because there was nothing behind the corner to see.
            Box(
                Modifier
                    .matchParentSize()
                    .background(revealed.last().background)
            )

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
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxHeight()
                .clickableAction(onClick, action.label)
                .padding(Theme.spacing.xs),
            contentAlignment = Alignment.Center,
        ) {
            // The label goes when there is no room for it, rather than being
            // clipped to a stripe of its own ascenders.
            //
            // This was clipped on every single-line row in the library — a
            // `ListItem` is 48dp and icon + gap + label + padding wants 59dp —
            // and nothing had ever seen it: the actions are only drawn once the
            // row is swiped, and no test or render had ever swiped one. The
            // first picture of a revealed row showed a red panel with a star and
            // four pixels of "Remove" under it.
            //
            // Measured rather than compared against a magic dp, so it stays
            // right at 200% type, where the label is twice as tall and the icon
            // is not.
            val labelHeight = with(LocalDensity.current) {
                Theme.typography.labelSmall.lineHeight.toDp()
            }
            val roomForLabel =
                maxHeight >= Theme.sizing.iconLarge + Theme.spacing.xxs + labelHeight

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
                // Dropping it costs nothing a screen reader can tell: the label
                // still reaches `CustomAccessibilityAction` on the row and
                // `onClickLabel` on this button, which is where it was always
                // doing the accessibility work.
                if (roomForLabel) {
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
 *     ListItem { +favourite.name }
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
