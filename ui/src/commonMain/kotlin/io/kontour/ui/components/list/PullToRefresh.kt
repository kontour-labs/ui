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

    internal fun drag(delta: Float): Float {
        val previous = offset
        val resisted = if (offset > thresholdPx) {
            delta * PullToRefreshDefaults.Resistance
        } else {
            delta
        }
        offset = (offset + resisted).coerceAtLeast(0f)
        return offset - previous
    }

    internal fun release(): Boolean {
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
    refreshingLabel: String = "Refreshing",
    pullLabel: String = "Pull to refresh",
    releaseLabel: String = "Release to refresh",
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
        content()

        if (indicatorOffset > 0.5f || refreshing) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offsetY(indicatorOffset)
                    .padding(top = Theme.spacing.sm)
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
                )
            }
        }
    }
}

@Composable
private fun RefreshIndicator(progress: Float, refreshing: Boolean) {
    Surface(
        modifier = Modifier
            .size(40.dp)
            .graphicsLayer {
                // Grows in as the pull begins rather than appearing at full
                // size, so a stray one-pixel drag does not flash a control.
                val scale = if (refreshing) 1f else progress.coerceIn(0f, 1f)
                scaleX = scale
                scaleY = scale
                alpha = scale
            },
        shape = Theme.shapes.pill,
        color = Theme.colors.surfaceRaised,
        shadow = Theme.elevation.high,
        contentAlignment = Alignment.Center,
    ) {
        Spinner(
            size = Theme.sizing.iconMedium,
            contentDescription = null,
        )
    }
}

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
    loadingLabel: String = "Loading more",
    errorLabel: String = "Couldn't load more",
    retryLabel: String = "Try again",
    endLabel: String? = null,
) {
    val load by rememberUpdatedState(onLoadMore)

    LaunchedEffect(state) {
        if (state == LoadMoreState.Idle) load()
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
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
                Button(retryLabel, onClick = onRetry, variant = ButtonVariant.Ghost)
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
