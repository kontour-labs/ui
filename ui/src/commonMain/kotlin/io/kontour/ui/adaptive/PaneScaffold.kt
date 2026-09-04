package io.kontour.ui.adaptive

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.VerticalDivider
import io.kontour.ui.input.pointerCursor
import io.kontour.ui.interaction.FeedbackIntent
import io.kontour.ui.interaction.LocalFeedback
import io.kontour.ui.theme.Theme

/** Which pane a single-pane window is showing. */
enum class PaneFocus { List, Detail }

object PaneScaffoldDefaults {
    /** How much of a two-pane window the list takes. */
    const val ListWeight: Float = 0.38f

    val MinPaneWidth: Dp = 280.dp
    val HandleWidth: Dp = 12.dp
}

/**
 * A list beside a detail on a wide window, one at a time on a narrow one.
 *
 * ```kotlin
 * var selected by remember { mutableStateOf<Stop?>(null) }
 *
 * ListDetailPaneScaffold(
 *     focus = if (selected == null) PaneFocus.List else PaneFocus.Detail,
 *     onBack = { selected = null },
 *     list = { StopList(onSelectedChange = { selected = it }) },
 *     detail = { selected?.let { StopDetail(it) } ?: EmptyState { title { +"Pick a stop" } } },
 * )
 * ```
 *
 * The classic two-pane shape, and the reason `WindowAdaptiveInfo` exists: on a
 * phone it is a list that pushes to a detail and comes back; on a tablet both
 * are on screen and selecting a stop changes only the right-hand side.
 *
 * **The caller keeps the selection.** This decides layout, not state. That is
 * what makes back work: on one pane it clears the selection, on two panes there
 * is nothing to go back from and it does not appear.
 *
 * On two panes the detail keeps its **empty state** rather than collapsing, so
 * the layout does not reflow the instant a selection is made or cleared.
 *
 * @param twoPane Override the automatic choice. Consults input modality as well
 *   as width: a 900dp touchscreen held in the hands is not a 900dp desktop
 *   window, and a resize handle is a very different thing in each.
 */
@Composable
fun ListDetailPaneScaffold(
    focus: PaneFocus,
    onBack: () -> Unit,
    list: @Composable () -> Unit,
    detail: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    twoPane: Boolean = LocalWindowSizeClass.current.width.hasRoomForTwoPanes,
    listWeight: Float = PaneScaffoldDefaults.ListWeight,
    resizable: Boolean = false,
    showDivider: Boolean = true,
) {
    if (twoPane) {
        TwoPane(
            modifier = modifier,
            startWeight = listWeight,
            resizable = resizable,
            showDivider = showDivider,
            start = list,
            end = detail,
        )
    } else {
        SinglePane(focus = focus, modifier = modifier, list = list, detail = detail)
    }
}

/**
 * Content with a supporting pane beside it — a filter panel, a legend, a map.
 *
 * ```kotlin
 * SupportingPaneScaffold(
 *     supportingVisible = filtersOpen,
 *     onDismissSupporting = { filtersOpen = false },
 *     main = { Results() },
 *     supporting = { Filters() },
 * )
 * ```
 *
 * The difference from [ListDetailPaneScaffold] is which pane is the point. Here
 * the main pane is the screen and the supporting pane assists it, so on a narrow
 * window the supporting pane becomes a
 * [io.kontour.ui.sheet.ModalBottomSheet] over the content rather than replacing
 * it — the user is still working on the main thing.
 *
 * The supporting pane goes on the **trailing** side, unlike navigation. It is
 * about the content, not about where you can go.
 */
@Composable
fun SupportingPaneScaffold(
    main: @Composable () -> Unit,
    supporting: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    supportingVisible: Boolean = true,
    onDismissSupporting: () -> Unit = {},
    twoPane: Boolean = LocalWindowSizeClass.current.width.hasRoomForTwoPanes,
    supportingWeight: Float = 0.32f,
    showDivider: Boolean = true,
) {
    if (twoPane && supportingVisible) {
        TwoPane(
            modifier = modifier,
            startWeight = 1f - supportingWeight,
            resizable = false,
            showDivider = showDivider,
            start = main,
            end = supporting,
        )
    } else {
        Box(modifier.fillMaxSize()) {
            main()
            if (supportingVisible) {
                io.kontour.ui.sheet.ModalBottomSheet(
                    visible = true,
                    onDismissRequest = onDismissSupporting,
                ) {
                    supporting()
                }
            }
        }
    }
}

@Composable
private fun SinglePane(
    focus: PaneFocus,
    modifier: Modifier,
    list: @Composable () -> Unit,
    detail: @Composable () -> Unit,
) {
    val motion = Theme.motion

    AnimatedContent(
        targetState = focus,
        modifier = modifier.fillMaxSize(),
        transitionSpec = {
            // The detail arrives from the trailing edge and the list leaves
            // toward the leading one, which is the direction the user's mental
            // model already runs in.
            val forward = targetState == PaneFocus.Detail
            val enter = slideInHorizontally(motion.tweenDefault()) { full ->
                if (forward) full / 3 else -full / 3
            } + fadeIn(motion.tweenFast())
            val exit = slideOutHorizontally(motion.tweenDefault()) { full ->
                if (forward) -full / 3 else full / 3
            } + fadeOut(motion.tweenFast())
            enter togetherWith exit
        },
        label = "pane",
    ) { current ->
        when (current) {
            PaneFocus.List -> list()
            PaneFocus.Detail -> detail()
        }
    }
}

@Composable
private fun TwoPane(
    modifier: Modifier,
    startWeight: Float,
    resizable: Boolean,
    showDivider: Boolean,
    start: @Composable () -> Unit,
    end: @Composable () -> Unit,
) {
    var weight by remember { mutableFloatStateOf(startWeight) }
    var totalWidth by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val minWidthPx = with(density) { PaneScaffoldDefaults.MinPaneWidth.toPx() }

    Row(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { totalWidth = it.width.toFloat() }
    ) {
        Box(Modifier.weight(weight).fillMaxHeight()) { start() }

        if (resizable) {
            ResizeHandle(
                onDelta = { delta ->
                    if (totalWidth <= 0f) return@ResizeHandle
                    // Half at most, so the clamp cannot invert. Two panes each
                    // wanting a 360dp minimum in a 700dp window ask for more
                    // than there is, and `coerceIn` *throws* on an inverted
                    // range — mid-drag, which is the worst moment to find out.
                    // Pinned to the middle is the honest answer: neither pane
                    // can have its minimum, so neither gets preference.
                    val minWeight = (minWidthPx / totalWidth).coerceAtMost(0.5f)
                    weight = (weight + delta / totalWidth)
                        .coerceIn(minWeight, 1f - minWeight)
                },
                fraction = weight,
            )
        } else if (showDivider) {
            VerticalDivider()
        }

        Box(Modifier.weight(1f - weight).fillMaxHeight()) { end() }
    }
}

/**
 * The draggable seam between two panes.
 *
 * Widens on hover — the only hint a pointer user gets that a divider is a
 * control. It is also a real accessibility target: a drag is not a gesture a
 * screen reader can perform, so the handle reports its position as a progress
 * range and accepts `setProgress`, which is how a keyboard or switch user
 * resizes a pane at all.
 */
@Composable
private fun ResizeHandle(onDelta: (Float) -> Unit, fraction: Float) {
    val colours = Theme.colours
    val motion = Theme.motion
    val feedback = LocalFeedback.current
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()

    val thickness by animateFloatAsState(
        targetValue = if (hovered) 3f else 1f,
        animationSpec = motion.tweenFast(),
        label = "resizeHandle",
    )

    Box(
        modifier = Modifier
            .width(PaneScaffoldDefaults.HandleWidth)
            .fillMaxHeight()
            .pointerCursor()
            .hoverable(interactions)
            .semantics {
                contentDescription = "Resize panes"
                progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0.2f..0.8f)
                setProgress { target ->
                    onDelta((target - fraction) * 1000f)
                    true
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { feedback.perform(FeedbackIntent.DragThreshold) },
                    onDragEnd = { feedback.perform(FeedbackIntent.GestureEnd) },
                ) { change, amount ->
                    change.consume()
                    onDelta(amount.x)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .width(thickness.dp)
                .fillMaxHeight()
                .background(if (hovered) colours.accent.solid else colours.outline)
        )
    }
}
