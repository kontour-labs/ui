package io.kontour.ui.adaptive

import kotlin.math.abs
import io.kontour.ui.interaction.rememberEndStopLatch
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
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
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
import io.kontour.ui.a11y.minimumTouchTarget
import io.kontour.ui.foundation.VerticalDivider
import io.kontour.ui.input.Cursor
import io.kontour.ui.input.pointerCursor
import io.kontour.ui.theme.Theme

/** Which pane a single-pane window is showing. */
enum class PaneFocus { List, Detail }

/** What [ListDetailPaneScaffold] and [SupportingPaneScaffold] take by default. */
object PaneScaffoldDefaults {
    /** How much of a two-pane window the list takes. */
    const val ListWeight: Float = 0.38f

    /** How much of a two-pane window the supporting pane takes, once open. */
    const val SupportingWeight: Float = 0.32f

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
 * @param twoPane Override the automatic choice, which is the window's width
 *   alone — two panes from 840dp. This used to claim it consulted the input
 *   modality as well, and never did; it should not start. The modality is
 *   learned from the first pointer event and assumed to be touch until then, so
 *   a default that read it would open every desktop window on one pane and
 *   reflow to two at the first mouse movement. What the modality does change is
 *   the resize handle, which takes a full touch target when there is no
 *   pointer to aim it.
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
    // The same list in both layouts, not two lists that happen to look alike: a
    // window crossing 840dp used to drop the list's scroll position, because
    // one pane and two are different branches and a composable that changes
    // branch starts again.
    val listPane = rememberPane(list)
    val detailPane = rememberPane(detail)
    if (twoPane) {
        TwoPane(
            modifier = modifier,
            startWeight = listWeight,
            resizable = resizable,
            showDivider = showDivider,
            start = listPane,
            end = detailPane,
        )
    } else {
        SinglePane(focus = focus, modifier = modifier, list = listPane, detail = detailPane)
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
 *
 * On two panes it slides in and out from that side, and [supporting] goes on
 * being called until it has gone — so content that is only there while the pane
 * is wanted has to be kept by the caller for the length of the slide. The main
 * pane is the same composable open or closed, and keeps its state.
 */
@Composable
fun SupportingPaneScaffold(
    main: @Composable () -> Unit,
    supporting: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    supportingVisible: Boolean = true,
    onDismissSupporting: () -> Unit = {},
    twoPane: Boolean = LocalWindowSizeClass.current.width.hasRoomForTwoPanes,
    supportingWeight: Float = PaneScaffoldDefaults.SupportingWeight,
    /**
     * Whether the seam between the panes can be dragged, as
     * [ListDetailPaneScaffold]'s can. Two panes only: a sheet is resized by its
     * own drag.
     */
    resizable: Boolean = false,
    showDivider: Boolean = true,
) {
    // The main pane is one pane in every layout. Opening the supporting pane
    // used to move `main` into a different branch — a `Row` instead of a `Box` —
    // and a composable that changes branch starts again: scroll positions,
    // text being typed, anything remembered, gone each time a filter panel
    // opened. Inside Navigation 3 the entries were movable already, which hid it
    // there and nowhere else.
    val mainPane = rememberPane(main)

    // `supporting` keeps being called while the pane slides out, so it has to
    // have something to draw after `supportingVisible` has gone false. Keeping the
    // last lambda here would not do it: the compiler hands a composable lambda
    // the same object on every recomposition and swaps its body in place, so the
    // "old" one runs the new body. A caller whose content is gone by then — a
    // Navigation 3 entry popped off the back stack — keeps the content itself,
    // which `SupportingPaneScene` does.
    if (twoPane) {
        TwoPaneSupporting(
            modifier = modifier,
            visible = supportingVisible,
            supportingWeight = supportingWeight,
            resizable = resizable,
            showDivider = showDivider,
            main = mainPane,
            supporting = supporting,
        )
    } else {
        // Composed from the first time it opens, so that closing it is the sheet
        // sliding down rather than the sheet ceasing to exist mid-frame — and not
        // before, so a scaffold whose supporting pane never opens does not need
        // an `OverlayHost` it would never draw in.
        var opened by remember { mutableStateOf(supportingVisible) }
        if (supportingVisible) opened = true
        Box(modifier.fillMaxSize()) {
            mainPane()
            if (opened) {
                io.kontour.ui.sheet.ModalBottomSheet(
                    visible = supportingVisible,
                    onDismissRequest = onDismissSupporting,
                ) {
                    supporting()
                }
            }
        }
    }
}

/**
 * [content] as movable content, so it keeps its state wherever the layout puts it.
 *
 * Both scaffolds put the same panes in different places depending on the window —
 * a `Row` of two, or one at a time — and a composable that moves to a different
 * branch is a new composable. Movable content is the same one, moved.
 */
@Composable
private fun rememberPane(content: @Composable () -> Unit): @Composable () -> Unit {
    val latest by rememberUpdatedState(content)
    return remember { movableContentOf { latest() } }
}

/**
 * The main pane with the supporting pane beside it, sliding in and out.
 *
 * One `Row` whether the supporting pane is open or not, so opening it is the pane
 * arriving rather than the layout being rebuilt. Its share of the width animates
 * between nothing and [supportingWeight], and while it moves its content is laid
 * out at the *full* share and clipped, so it slides in from the trailing edge
 * rather than being squeezed narrower on every frame.
 *
 * Critically damped, so the share never passes zero on the way down — a weight
 * of zero or less is not a layout Compose can make.
 */
@Composable
private fun TwoPaneSupporting(
    modifier: Modifier,
    visible: Boolean,
    supportingWeight: Float,
    resizable: Boolean,
    showDivider: Boolean,
    main: @Composable () -> Unit,
    supporting: @Composable () -> Unit,
) {
    // The caller's weight until the seam is dragged; a new weight from the
    // caller starts again from it.
    var weight by remember(supportingWeight) { mutableFloatStateOf(supportingWeight) }
    val motion = Theme.motion
    val share by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = motion.springOrTween(motion.springGentle),
        label = "supportingPane",
    )
    val fraction = share.coerceIn(0f, 1f)
    var totalWidth by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val minWidthPx = with(density) { PaneScaffoldDefaults.MinPaneWidth.toPx() }

    Row(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { totalWidth = it.width.toFloat() }
    ) {
        Box(Modifier.weight(1f - weight * fraction).fillMaxHeight()) { main() }

        // Out of composition once it has gone, which is what lets a Navigation 3
        // entry that was popped to close it finally be cleaned up.
        if (fraction > 0f) {
            if (resizable) {
                Box(Modifier.alpha(fraction)) {
                    ResizeHandle(
                        onDelta = { delta ->
                            if (totalWidth <= 0f) return@ResizeHandle false
                            // The same clamp as `TwoPane`'s, on the other pane:
                            // dragging the seam towards the end grows the main
                            // pane, so it shrinks this one.
                            val minWeight = (minWidthPx / totalWidth).coerceAtMost(0.5f)
                            val asked = weight - delta / totalWidth
                            weight = asked.coerceIn(minWeight, 1f - minWeight)
                            asked != weight
                        },
                        fraction = 1f - weight,
                    )
                }
            } else if (showDivider) {
                VerticalDivider(Modifier.alpha(fraction))
            }
            Box(
                Modifier
                    .weight(weight * fraction)
                    .fillMaxHeight()
                    .clipToBounds()
            ) {
                val full = with(density) { (totalWidth * weight).toDp() }
                Box(
                    if (fraction < 1f && totalWidth > 0f) {
                        Modifier
                            .fillMaxHeight()
                            .wrapContentWidth(Alignment.Start, unbounded = true)
                            .requiredWidth(full)
                    } else {
                        Modifier.fillMaxSize()
                    }
                ) { supporting() }
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
                    if (totalWidth <= 0f) return@ResizeHandle false
                    // Half at most, so the clamp cannot invert. Two panes each
                    // wanting a 360dp minimum in a 700dp window ask for more
                    // than there is, and `coerceIn` *throws* on an inverted
                    // range — mid-drag, which is the worst moment to find out.
                    // Pinned to the middle is the honest answer: neither pane
                    // can have its minimum, so neither gets preference.
                    val minWeight = (minWidthPx / totalWidth).coerceAtMost(0.5f)
                    val asked = weight + delta / totalWidth
                    weight = asked.coerceIn(minWeight, 1f - minWeight)
                    // Whether the drag ran into a pane's minimum.
                    asked != weight
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
 *
 * @param onDelta Moves the divider by that many pixels, and says whether it ran
 *   into a pane's minimum.
 */
@Composable
private fun ResizeHandle(onDelta: (Float) -> Boolean, fraction: Float) {
    val colours = Theme.colours
    val label = Theme.strings.resizePanes
    val motion = Theme.motion
    // 12dp is plenty for a mouse and a miss for a thumb, and this is the control
    // `isPrecise` names as its first example. Under touch the handle reserves
    // the platform's minimum, with the line still drawn down its middle.
    val precise = windowAdaptiveInfo.isPrecise
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()

    val thickness by animateFloatAsState(
        targetValue = if (hovered) 3f else 1f,
        animationSpec = motion.tweenFast(),
        label = "resizeHandle",
    )

    // The divider stops where a pane would go under its minimum, and a drag that
    // runs into that says so once, as a slider's end does. It has a touch target,
    // and a Mac's trackpad feels it too, so it is not the mouse-only control it
    // was written off as. The caller knows when it clamped; the stop re-arms once
    // the drag has come a little way clear.
    val stop = rememberEndStopLatch()
    val releasePx = with(LocalDensity.current) { PaneStopRelease.toPx() }

    Box(
        modifier = Modifier
            .then(if (precise) Modifier else Modifier.minimumTouchTarget())
            .width(PaneScaffoldDefaults.HandleWidth)
            .fillMaxHeight()
            .pointerCursor(Cursor.ResizeColumn)
            .hoverable(interactions)
            .semantics {
                contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0.2f..0.8f)
                setProgress { target ->
                    onDelta((target - fraction) * 1000f)
                    true
                }
            }
            .pointerInput(Unit) {
                var clear = 0f
                detectDragGestures(
                    onDragStart = { stop.arm(); clear = 0f },
                    onDragEnd = { stop.reset() },
                    onDragCancel = { stop.reset() },
                ) { change, amount ->
                    change.consume()
                    if (onDelta(amount.x)) {
                        stop.reached(if (amount.x > 0f) 1 else -1)
                        clear = 0f
                    } else {
                        clear += abs(amount.x)
                        if (clear >= releasePx) stop.clear()
                    }
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

/** How far a divider drag has to come clear of a pane's minimum before meeting it again counts. */
private val PaneStopRelease = 12.dp
