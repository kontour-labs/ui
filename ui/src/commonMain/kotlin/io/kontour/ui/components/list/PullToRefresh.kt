package io.kontour.ui.components.list

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.action.Button
import io.kontour.ui.components.action.ButtonVariant
import io.kontour.ui.components.display.Spinner
import io.kontour.ui.foundation.Surface
import io.kontour.ui.foundation.Text
import io.kontour.ui.interaction.FeedbackIntent
import io.kontour.ui.interaction.LocalFeedback
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import io.kontour.ui.components.display.SpinnerDefaults
import io.kontour.ui.foundation.LocalContentColour
import io.kontour.ui.theme.Theme
import kotlin.math.roundToInt

object PullToRefreshDefaults {
    /** How far the user has to pull before letting go refreshes. */
    val Threshold: Dp = 80.dp

    /**
     * How much of a pull past the threshold actually moves the indicator.
     *
     * Resistance, so the gesture has a bottom to it. Without it the indicator
     * follows the finger indefinitely and the user has no sense of having pulled
     * far enough.
     */
    const val Resistance: Float = 0.4f
}

@Stable
class PullToRefreshState internal constructor(
    private val thresholdPx: Float,
) {
    internal var offset by mutableFloatStateOf(0f)
    internal var settling by mutableStateOf(false)

    /** How far through the gesture the user is, 0 to 1 and beyond. */
    val progress: Float get() = if (thresholdPx <= 0f) 0f else offset / thresholdPx

    /** True once a release would trigger a refresh. */
    val willRefresh: Boolean get() = progress >= 1f

    /**
     * Pulls the indicator down by [delta] pixels, returning what it consumed.
     *
     * Public for the same reason as [ReorderableState.start]: the pulled and
     * mid-refresh states were unreachable from outside, so neither could be
     * tested or photographed. Pair with [release].
     */
    fun drag(delta: Float): Float {
        val previous = offset
        // Resistance on the way **out** only.
        //
        // Applied to a returning finger as well, it consumed four tenths of the
        // upward movement and handed the other six to the list, which scrolled
        // — so putting the indicator away slowly took the page with it, which is
        // not something the gesture ever offered to do. Coming back closes the
        // gap at full rate and consumes the whole of it, which is
        // `RubberBand.payBack` under another name and for the same reason.
        val resisted = if (delta > 0f && offset > thresholdPx) {
            delta * PullToRefreshDefaults.Resistance
        } else {
            delta
        }
        offset = (offset + resisted).coerceAtLeast(0f)
        return offset - previous
    }

    /** Lets go. Returns whether the pull passed the threshold and refreshing began. */
    fun release(): Boolean {
        val triggered = willRefresh
        offset = 0f
        return triggered
    }
}

@Composable
fun rememberPullToRefreshState(
    threshold: Dp = PullToRefreshDefaults.Threshold,
): PullToRefreshState {
    val thresholdPx = with(LocalDensity.current) { threshold.toPx() }
    return remember(thresholdPx) { PullToRefreshState(thresholdPx) }
}

/**
 * Pull down at the top of a list to reload it.
 *
 * ```kotlin
 * PullToRefresh(refreshing = uiState.refreshing, onRefresh = viewModel::refresh) {
 *     LazyColumn { … }
 * }
 * ```
 *
 * **Not a substitute for a visible refresh control.** It is invisible, has no
 * keyboard or pointer equivalent, and is unreachable for anyone who cannot make
 * a sustained drag. On a screen where reloading matters, put a refresh action in
 * the toolbar as well; this is the shortcut, not the route.
 *
 * The pull resists past its threshold rather than tracking the finger all the
 * way down, so the gesture has a bottom to it — a pull that keeps following
 * gives the user no sense of having gone far enough. A haptic fires at the
 * moment the threshold is crossed, which is the actual signal that letting go
 * will do something.
 *
 * Announces its state as a polite live region while refreshing, because a
 * spinner sliding in is not something a screen reader user can see.
 */
@Composable
fun PullToRefresh(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    state: PullToRefreshState = rememberPullToRefreshState(),
    refreshingLabel: String = Theme.strings.refreshing,
    pullLabel: String = Theme.strings.pullToRefresh,
    releaseLabel: String = Theme.strings.releaseToRefresh,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val motion = Theme.motion
    val feedback = LocalFeedback.current
    val refresh by rememberUpdatedState(onRefresh)

    var crossedThreshold by remember { mutableStateOf(false) }
    LaunchedEffect(state.willRefresh) {
        if (state.willRefresh && !crossedThreshold) {
            // The one moment worth a haptic: it is what tells the user that
            // letting go now will do something.
            feedback.perform(FeedbackIntent.DragThreshold)
        }
        crossedThreshold = state.willRefresh
    }

    // Read through `State` rather than captured.
    //
    // The connection below is remembered, and an anonymous object takes a copy
    // of the parameters it closes over — so a connection built while
    // `refreshing` was false went on believing that for the whole of the
    // refresh, and a scroll arriving mid-refresh started a second pull behind
    // the spinner already on screen.
    val pullable by rememberUpdatedState(enabled)
    val busy by rememberUpdatedState(refreshing)

    /**
     * Whether the content still has its top edge showing.
     *
     * Only a gesture that starts at the top of the list is a pull, and the
     * nested-scroll path never has to ask: a scroll only reaches [connection]
     * unconsumed *because* the list had nowhere left to go. The drag path has no
     * such luck — `PullToRefresh` holds its content as an opaque composable and
     * cannot see how far down it is — so the answer is kept from what the scrolls
     * say as they go past. The child taking a forward scroll means it is not at
     * the top; the child declining a backward one means it is.
     */
    var atTop by remember { mutableStateOf(true) }

    /**
     * Whether a finger or a button is currently down.
     *
     * The gate on item 23d, and the enum is not it. `NestedScrollSource` does
     * carry a distinct `Wheel`, and gating on `UserInput` reads like it settles
     * the question — but measured, a wheel notch at the top of a list arrives
     * here as `UserInput` all the same, and scrolling *down* a list and then
     * back up past its top pulled the indicator to fifteen per cent of its
     * threshold with that gate in place. The first version of this test started
     * at the top, where a wheel is declined outright and nothing reaches nested
     * scroll, so it passed against a component that had the bug.
     *
     * What actually separates the two is not where the scroll came from but
     * whether anybody is holding on. A pull is a sustained gesture: you press,
     * you drag, you decide, you let go. A wheel notch is a request to read what
     * is further up, and at the top there is nothing further up, so the right
     * answer is to do nothing.
     *
     * Watched on the **initial** pass, which runs parents first, so this sees
     * the press whatever the list does with it afterwards. Nothing is consumed
     * here.
     */
    var holding by remember { mutableStateOf(false) }

    val connection = remember(state) {
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                // Pulling back down while the indicator is out belongs to the
                // indicator, not to the list.
                if (!pullable || busy) return Offset.Zero
                return if (available.y < 0 && state.offset > 0f) {
                    Offset(0f, state.drag(available.y))
                } else {
                    Offset.Zero
                }
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (consumed.y < 0f) atTop = false
                if (available.y > 0f) atTop = true
                if (!pullable || busy) return Offset.Zero
                // A held pointer, not a source — see `holding` above for why
                // the obvious `source == Wheel` test does not work.
                return if (available.y > 0 && holding && source == NestedScrollSource.UserInput) {
                    Offset(0f, state.drag(available.y))
                } else {
                    Offset.Zero
                }
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (state.offset <= 0f) return Velocity.Zero
                if (state.release()) refresh()
                return Velocity.Zero
            }
        }
    }

    /**
     * A pull you can perform with a mouse.
     *
     * The gesture was nested-scroll and nothing else, and a `LazyColumn` does
     * not drag with a mouse — desktops do not drag lists and Compose is right
     * not to — so on desktop and the web pulling down did precisely nothing.
     * The list is a child and claims the main pointer pass first, so this only
     * ever sees a drag the list declined, which on a phone is none of them.
     *
     * Off unless the content is at its top, so a mouse drag halfway down a list
     * is still a mouse drag halfway down a list.
     */
    val pullDrag = rememberDraggableState { delta -> state.drag(delta) }

    val indicatorOffset by animateFloatAsState(
        targetValue = when {
            refreshing -> with(density) { PullToRefreshDefaults.Threshold.toPx() }
            else -> state.offset
        },
        animationSpec = motion.springOrTween(motion.springDefault),
        label = "pullToRefresh",
    )

    Box(
        modifier
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        holding = event.changes.any { it.pressed }
                    }
                }
            }
            .nestedScroll(connection)
            .draggable(
                state = pullDrag,
                orientation = Orientation.Vertical,
                enabled = enabled && atTop && !refreshing,
                onDragStopped = { if (state.release()) refresh() },
            )
    ) {
        // The content itself comes down, and the indicator is revealed in the
        // gap behind it.
        //
        // It used to stay put while a floating circle slid down over the top of
        // it, which is a control appearing *in front of* the list rather than
        // the list being pulled away from its top edge. Moving the content is
        // what makes the gesture feel like it is moving the page — the thing
        // your finger is actually on — and it is what iOS does.
        //
        // In a layer, so the list is not re-laid-out on every frame of a drag.
        Box(Modifier.offsetY(indicatorOffset)) {
            content()
        }

        if (indicatorOffset > 0.5f || refreshing) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    // Centred in the gap it is revealed in rather than riding
                    // the content's top edge: at rest there is no gap and no
                    // indicator, and at full pull it sits in the middle of the
                    // space the list has vacated.
                    .offsetY((indicatorOffset - with(density) { IndicatorSize.toPx() }) / 2f)
                    .semantics {
                        liveRegion = LiveRegionMode.Polite
                        contentDescription = when {
                            refreshing -> refreshingLabel
                            state.willRefresh -> releaseLabel
                            else -> pullLabel
                        }
                    },
            ) {
                RefreshIndicator(
                    progress = state.progress,
                    refreshing = refreshing,
                    reduceMotion = motion.reduceMotion,
                )
            }
        }
    }
}

/** How big the indicator is once the pull is complete. */
private val IndicatorSize = 40.dp

/**
 * The loader, which does not spin until it has something to say.
 *
 * ### Rotation is the pull, spinning is the work
 *
 * It span from the first pixel of the drag, which meant the control claimed to
 * be loading while the user was still deciding whether to load anything — and
 * left nothing to signal that the threshold had been crossed except a colour
 * nobody was looking at.
 *
 * So the arc *grows and turns with the finger* while pulling: it is a progress
 * indicator, and the progress it shows is how close the gesture is to
 * committing. Only once the drag is released past the threshold does it become
 * a [Spinner] and start spinning on its own clock, which is the moment the
 * meaning changes from "keep pulling" to "I have it". Material gets this right
 * and it is worth copying.
 *
 * Under reduced motion the pull arc does not rotate — it still fills, so the
 * threshold is as visible as ever, without the spin.
 */
@Composable
private fun RefreshIndicator(progress: Float, refreshing: Boolean, reduceMotion: Boolean) {
    val pull = progress.coerceIn(0f, 1f)

    // The fade needs room around the circle, or it cuts the circle's own shadow
    // into a square.
    //
    // `alpha < 1` composites offscreen into a buffer sized to the layer's own
    // rectangle. This layer used to sit on the `Surface` itself — exactly
    // `IndicatorSize` — while `elevation.high` reaches 28dp further out, so
    // every frame of the pull drew a hard-edged grey rectangle around a round
    // indicator. `scale < 1` is what made it obvious, shrinking the white circle
    // inside bounds that had not moved.
    //
    // So the layer goes on a box that is bigger by exactly the shadow's reach,
    // and the negative offset puts the circle back where it was — the growth is
    // for the buffer, not for the layout. Same fix as `overlayAppearance`, and
    // `Shadow.bleed` is where the two get the number from.
    val room = Theme.elevation.high.bleed

    Box(
        modifier = Modifier
            .offset(y = -room)
            .graphicsLayer {
                // Grows in as the pull begins rather than appearing at full
                // size, so a stray one-pixel drag does not flash a control.
                val scale = if (refreshing) 1f else pull
                scaleX = scale
                scaleY = scale
                alpha = scale
            }
            .padding(room),
    ) {
    Surface(
        modifier = Modifier.size(IndicatorSize),
        shape = Theme.shapes.pill,
        colour = Theme.colours.surfaceRaised,
        shadow = Theme.elevation.high,
        contentAlignment = Alignment.Center,
    ) {
        if (refreshing) {
            Spinner(size = Theme.sizing.iconMedium, contentDescription = null)
        } else {
            // Drawn here rather than handed to `CircularProgress`, because a
            // progress ring closes the circle and this one must not.
            //
            // At a full pull the ring was a complete circle and the [Spinner]
            // that replaced it opens at `OpeningSweep` — a third of one — so the
            // moment the gesture committed, the thing the user was watching fill
            // up emptied. Reported as the ring filling rather than stopping at
            // an arc and continuing from there.
            //
            // Same length, same stroke, same colour and the same head-and-tail
            // construction as the spinner: `startAngle = head - sweep`, so the
            // arc's leading end is what travels and the tail follows it, which
            // is the one thing that makes a growing arc read as turning rather
            // than as unrolling from a fixed point.
            val colour = LocalContentColour.current
            Canvas(Modifier.size(Theme.sizing.iconMedium)) {
                val stroke = PullStroke.toPx()
                val inset = stroke / 2f
                val sweep = pull * SpinnerDefaults.OpeningSweep
                val head = -90f + if (reduceMotion) 0f else pull * PullTurn
                drawArc(
                    color = colour,
                    startAngle = head - sweep,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
    }
    }
}

/** Matches [Spinner]'s stroke at the same size, so the swap is not a step. */
private val PullStroke = 2.5.dp

/** How far the arc turns over a full pull. Most of a revolution, not all of it. */
private const val PullTurn = 300f

private fun Modifier.offsetY(y: Float): Modifier =
    offset { IntOffset(0, y.roundToInt()) }

/**
 * The row at the end of a list that loads the next page.
 *
 * ```kotlin
 * item {
 *     LoadMore(
 *         state = uiState.paging,
 *         onLoadMore = viewModel::loadNextPage,
 *         onRetry = viewModel::loadNextPage,
 *     )
 * }
 * ```
 *
 * Loads automatically when it comes into view, and falls back to a button when
 * that fails. Automatic loading alone is a trap: when the request errors, an
 * invisible retry means an infinite list that silently stops, and the user has
 * no way to ask again.
 */
@Composable
fun LoadMore(
    state: LoadMoreState,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = onLoadMore,
    loadingLabel: String = Theme.strings.loadingMore,
    errorLabel: String = Theme.strings.loadMoreFailed,
    retryLabel: String = Theme.strings.retry,
    endLabel: String? = null,
) {
    val load by rememberUpdatedState(onLoadMore)

    LaunchedEffect(state) {
        if (state == LoadMoreState.Idle) load()
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            // Polite, and for the state changes rather than for the spinner.
            //
            // This is the foot of an infinite list: the user scrolls, something
            // loads, and either more rows arrive or it fails. Without a live
            // region none of that is announced — the failure in particular, which
            // is the state a user on a train actually meets and the one where the
            // retry button they need has appeared silently below them.
            //
            // `PullToRefresh` above has had one since it was written; this half
            // of the same file did not.
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(Theme.spacing.md),
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            LoadMoreState.Idle, LoadMoreState.Loading -> Spinner(
                size = Theme.sizing.iconLarge,
                contentDescription = loadingLabel,
            )

            LoadMoreState.Error -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
            ) {
                Text(
                    text = errorLabel,
                    style = Theme.typography.bodySmall,
                    colour = Theme.colours.contentMuted,
                )
                Button(onClick = onRetry, variant = ButtonVariant.Ghost) { +retryLabel }
            }

            LoadMoreState.End -> if (endLabel != null) {
                Text(
                    text = endLabel,
                    style = Theme.typography.bodySmall,
                    colour = Theme.colours.contentSubtle,
                )
            }
        }
    }
}

/** What [LoadMore] should be doing. */
enum class LoadMoreState {
    /** Not started. Coming into view triggers a load. */
    Idle,

    /** In flight. */
    Loading,

    /** Failed. Shows a retry. */
    Error,

    /** Nothing left to load. */
    End,
}
