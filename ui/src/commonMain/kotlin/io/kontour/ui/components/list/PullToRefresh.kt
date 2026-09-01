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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import io.kontour.ui.components.display.CircularProgress
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
        val resisted = if (offset > thresholdPx) {
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

    val connection = remember(state, enabled) {
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                // Pulling back down while the indicator is out belongs to the
                // indicator, not to the list.
                if (!enabled || refreshing) return Offset.Zero
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
                if (!enabled || refreshing) return Offset.Zero
                return if (available.y > 0 && source == NestedScrollSource.UserInput) {
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

    val indicatorOffset by animateFloatAsState(
        targetValue = when {
            refreshing -> with(density) { PullToRefreshDefaults.Threshold.toPx() }
            else -> state.offset
        },
        animationSpec = motion.springOrTween(motion.springDefault),
        label = "pullToRefresh",
    )

    Box(modifier.nestedScroll(connection)) {
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

    Surface(
        modifier = Modifier
            .size(IndicatorSize)
            .graphicsLayer {
                // Grows in as the pull begins rather than appearing at full
                // size, so a stray one-pixel drag does not flash a control.
                val scale = if (refreshing) 1f else pull
                scaleX = scale
                scaleY = scale
                alpha = scale
            },
        shape = Theme.shapes.pill,
        color = Theme.colors.surfaceRaised,
        shadow = Theme.elevation.high,
        contentAlignment = Alignment.Center,
    ) {
        if (refreshing) {
            Spinner(size = Theme.sizing.iconMedium, contentDescription = null)
        } else {
            CircularProgress(
                progress = pull,
                size = Theme.sizing.iconMedium,
                trackColor = Color.Transparent,
                strokeWidth = PullStroke,
                modifier = Modifier.rotate(if (reduceMotion) 0f else pull * PullTurn),
            )
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
                    color = Theme.colors.contentMuted,
                )
                Button(onClick = onRetry, variant = ButtonVariant.Ghost) { +retryLabel }
            }

            LoadMoreState.End -> if (endLabel != null) {
                Text(
                    text = endLabel,
                    style = Theme.typography.bodySmall,
                    color = Theme.colors.contentSubtle,
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
