package io.kontour.ui.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.action.Button
import io.kontour.ui.components.action.ButtonSize
import io.kontour.ui.components.action.ButtonVariant
import io.kontour.ui.components.action.IconButton
import io.kontour.ui.foundation.Icon
import io.kontour.ui.foundation.Surface
import io.kontour.ui.foundation.SystemIcons
import io.kontour.ui.foundation.Text
import io.kontour.ui.adaptive.sheetEdges
import io.kontour.ui.theme.Theme
import kotlinx.coroutines.delay

/** What a toast is reporting. */
enum class ToastTone { Neutral, Success, Warning, Danger, Accent }

/** One live toast. */
@Stable
class Toast internal constructor(
    val id: Long,
    val message: String,
    val tone: ToastTone,
    val icon: ImageVector?,
    val actionLabel: String?,
    val onAction: (() -> Unit)?,
    val durationMillis: Long,
) {
    /**
     * Whether this toast is on screen, and whether it has finished arriving or
     * leaving.
     *
     * A toast removed from the list the instant it is dismissed cannot animate
     * out — the node is gone before the transition starts. So dismissing sets
     * `targetState = false` and the card removes the toast for real once the
     * transition reports itself idle. This is the shape `AnimatedVisibility`
     * documents for exactly this case, and it is why the flag lives on the model
     * rather than in the card.
     */
    internal val presence = MutableTransitionState(false).apply { targetState = true }
}

/**
 * Shows short confirmations of things the user just did.
 *
 * ```
 * val toasts = rememberToastHostState()
 * ToastHost(toasts)
 *
 * toasts.show("Added to favourites")
 * toasts.show("Couldn't save", tone = ToastTone.Danger, actionLabel = "Retry", onAction = ::retry)
 * ```
 *
 * A toast is for feedback on an *action*. For something about the state of the
 * screen the user is looking at, use a
 * [io.kontour.ui.components.display.Banner] — it stays put, and a toast that
 * carries important information will be missed by anyone who looked away.
 *
 * Never put the only copy of something important in a toast, and never put a
 * control in one that is not also available elsewhere: an action that vanishes
 * after four seconds is unusable for anyone who reads slowly.
 *
 * ### They stack, and each one runs its own clock
 *
 * This used to hold a queue and show its head, so a toast pinned for an answer
 * stopped every later one from being seen at all, and four rapid confirmations
 * took sixteen seconds to get through. Now up to [ToastDefaults.MaxVisible] are
 * on screen at once, newest in front, older ones scaled and offset behind — and
 * each carries its own timer, so a long one and a short one expire when they
 * were each told to.
 */
@Stable
class ToastHostState {
    private var nextId = 0L

    /** Oldest first, which is back to front on screen. */
    internal val toasts = mutableStateListOf<Toast>()

    /**
     * Queues a toast, and returns its id so it can be [dismiss]ed by name.
     *
     * @param durationMillis How long before it dismisses itself. Longer when
     *   there is an action, since the user has to read it *and* decide. **Zero
     *   means it stays** until [dismiss], [dismissCurrent] or [clear] — for a
     *   toast whose action is the point and which the user must actually answer.
     *   Reach for it rarely: a confirmation that will not go away is a banner
     *   that has been put in the wrong place, and
     *   [io.kontour.ui.components.display.Banner] is the component for that. A
     *   pinned toast no longer blocks the ones behind it, but it does hold a
     *   place in the stack.
     */
    fun show(
        message: String,
        tone: ToastTone = ToastTone.Neutral,
        icon: ImageVector? = null,
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null,
        durationMillis: Long = if (actionLabel != null) 8_000 else 4_000,
    ): Long {
        val id = nextId++
        toasts.add(
            Toast(
                id = id,
                message = message,
                tone = tone,
                icon = icon,
                actionLabel = actionLabel,
                onAction = onAction,
                durationMillis = durationMillis,
            )
        )
        return id
    }

    /** Starts [id] on its way out. It leaves the list once it has animated away. */
    fun dismiss(id: Long) {
        toasts.firstOrNull { it.id == id }?.presence?.targetState = false
    }

    /**
     * Dismisses the one in front, which is the newest.
     *
     * It used to be the *oldest*, because there was only ever one showing and it
     * was the head of a queue. In a stack the front one is the one the user is
     * looking at, and that is the only reading of "current" that still means
     * anything.
     */
    fun dismissCurrent() {
        toasts.lastOrNull { it.presence.targetState }?.presence?.targetState = false
    }

    /** Clears everything. For navigating away from the context they belong to. */
    fun clear() {
        toasts.forEach { it.presence.targetState = false }
    }

    internal fun remove(toast: Toast) {
        toasts.remove(toast)
    }
}

@Composable
fun rememberToastHostState(): ToastHostState = remember { ToastHostState() }

object ToastDefaults {
    /**
     * How many toasts are on screen at once.
     *
     * Three. Past that the stack is taller than the thing it is reporting on,
     * and the ones at the back are a stripe of colour rather than a message.
     */
    const val MaxVisible: Int = 3

    /**
     * How far each toast behind the front one peeks out.
     *
     * Bigger than it first looks like it should be, because a toast is a *pill*.
     * At 10dp the card behind added eighteen pixels of rounded top to a shape
     * that was already round, and the stack read as one toast with a thick edge
     * rather than as two. The gap has to clear the curve.
     */
    val Peek: Dp = 16.dp

    /**
     * How much smaller each one behind the front one is drawn.
     *
     * Enough to be visible at the sides of the one in front, which is the other
     * half of reading as a stack: same width and it is a silhouette, narrower
     * and it is a card behind a card.
     */
    const val DepthScale: Float = 0.07f

    /** How much of the screen's width a toast may take, at most. */
    val MaxWidth: Dp = 420.dp

    /**
     * How far a toast has to be dragged toward the edge before it goes.
     *
     * A third of its own height. Short enough that a flick is enough, long
     * enough that a scroll started on top of one does not throw it away.
     */
    const val SwipeAway: Float = 0.33f
}

/**
 * Renders whatever [state] has queued. Install once, near the root.
 *
 * Toasts sit in [OverlayLayer.Toast] with [ScrimStyle.None], so they never dim
 * or block what is underneath — the user must be able to keep working while one
 * is showing.
 *
 * @param maxVisible How many are drawn. Extras stay in the state and take their
 *   turn as the ones in front expire; their timers run either way, which is what
 *   stops a backlog from outliving its usefulness.
 * @param showClose Puts a close control on every toast. Off by default, because
 *   a toast that dismisses itself in four seconds does not need one — turn it on
 *   where toasts are pinned, or where they carry an action worth reading twice.
 */
@Composable
fun ToastHost(
    state: ToastHostState,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.BottomCenter,
    maxVisible: Int = ToastDefaults.MaxVisible,
    showClose: Boolean = false,
    closeLabel: String = Theme.strings.dismiss,
    /**
     * What a toast keeps clear of. The gesture bar, the cutout and the keyboard —
     * a confirmation of what the user just typed, hidden behind the keyboard they
     * typed it with, is the one place it is guaranteed not to be read.
     *
     * It does **not** account for a navigation bar: that is a component, not an
     * inset, and a screen with one should pass a
     * `WindowInsets(bottom = barHeight)` union of its own.
     */
    windowInsets: WindowInsets = WindowInsets.sheetEdges,
) {
    val host = LocalOverlayHost.current
    val key = remember { Any() }
    val occupied = state.toasts.isNotEmpty()

    // Everything the entry's content reads has to be read *live*: the entry is
    // pushed once, when the stack goes from empty to occupied, and composed by
    // the host from then on.
    val latest by rememberUpdatedState(
        ToastHostConfig(modifier, alignment, maxVisible, showClose, closeLabel, windowInsets)
    )

    LaunchedEffect(occupied) {
        if (!occupied) {
            host.hide(key)
            return@LaunchedEffect
        }
        host.show(
            OverlayEntry(
                key = key,
                layer = OverlayLayer.Toast,
                scrim = ScrimStyle.None,
                // Back should dismiss the screen, not a transient confirmation.
                dismissOnBack = false,
                dismissOnOutside = false,
                trapFocus = false,
                content = { ToastStack(state, latest) },
            )
        )
    }
}

/** What [ToastHost] was called with, so the overlay entry can read it fresh. */
private data class ToastHostConfig(
    val modifier: Modifier,
    val alignment: Alignment,
    val maxVisible: Int,
    val showClose: Boolean,
    val closeLabel: String,
    val windowInsets: WindowInsets,
)

@Composable
private fun ToastStack(state: ToastHostState, config: ToastHostConfig) {
    // The stack grows away from the edge it is anchored to: a bottom-aligned
    // stack recedes upward, a top-aligned one downward. Read from the alignment
    // rather than taken as a parameter, because it is not a separate decision.
    val towardEdge = ((config.alignment as? BiasAlignment)?.verticalBias ?: 1f) >= 0f
    val visible = state.toasts.takeLast(config.maxVisible)

    Box(
        Modifier.fillMaxSize().windowInsetsPadding(config.windowInsets),
        contentAlignment = config.alignment,
    ) {
        visible.forEachIndexed { index, toast ->
            key(toast.id) {
                ToastCard(
                    toast = toast,
                    state = state,
                    // Zero is the front. The list is oldest first, so the last
                    // one in it is the one on top.
                    depth = visible.lastIndex - index,
                    towardEdge = towardEdge,
                    showClose = config.showClose,
                    closeLabel = config.closeLabel,
                    modifier = config.modifier,
                )
            }
        }
    }
}

@Composable
private fun ToastCard(
    toast: Toast,
    state: ToastHostState,
    depth: Int,
    towardEdge: Boolean,
    showClose: Boolean,
    closeLabel: String,
    modifier: Modifier,
) {
    val motion = Theme.motion

    // Its own clock. The whole point of the stack: a toast pinned for an answer
    // used to stop every later one from being shown at all.
    LaunchedEffect(toast.id) {
        if (toast.durationMillis <= 0) return@LaunchedEffect
        delay(toast.durationMillis)
        state.dismiss(toast.id)
    }

    // Gone for real once it has finished leaving, which is what lets it leave at
    // all — see `Toast.presence`.
    //
    // The `targetState` guard is load-bearing. A freshly built
    // `MutableTransitionState` reports `isIdle == true` until something starts
    // its transition, and its `currentState` is still `false` at that point — so
    // a card that only asked "idle and not showing?" removed itself on its own
    // first frame, racing `AnimatedVisibility` for which ran first. One toast
    // survived and the one behind it vanished, which is a stack of one.
    LaunchedEffect(toast.presence.targetState, toast.presence.isIdle, toast.presence.currentState) {
        if (toast.presence.targetState) return@LaunchedEffect
        if (toast.presence.isIdle && !toast.presence.currentState) state.remove(toast)
    }

    val depthOffset by animateDpAsState(
        targetValue = ToastDefaults.Peek * depth * (if (towardEdge) -1 else 1),
        animationSpec = motion.springOrTween(motion.springDefault),
        label = "toastDepthOffset",
    )
    val depthScale by animateFloatAsState(
        targetValue = 1f - ToastDefaults.DepthScale * depth,
        animationSpec = motion.springOrTween(motion.springDefault),
        label = "toastDepthScale",
    )
    /** How far the front toast has been dragged toward the edge. */
    var swipe by remember { mutableFloatStateOf(0f) }
    var height by remember { mutableFloatStateOf(0f) }

    AnimatedVisibility(
        visibleState = toast.presence,
        enter = slideInVertically(motion.tweenDefault()) { if (towardEdge) it / 2 else -it / 2 } +
            fadeIn(motion.tweenFast()) +
            scaleIn(motion.tweenFast(), initialScale = 0.94f),
        exit = slideOutVertically(motion.tweenFast()) { if (towardEdge) it / 2 else -it / 2 } +
            fadeOut(motion.tweenFast()) +
            scaleOut(motion.tweenFast(), targetScale = 0.94f),
    ) {
        ToastSurface(
            toast = toast,
            showClose = showClose && depth == 0,
            closeLabel = closeLabel,
            modifier = modifier
                .padding(Theme.spacing.md)
                // Measured in the layout phase, not read out of the draw phase:
                // writing state from inside `graphicsLayer` is a write during
                // draw, and the recomposition it schedules can loop.
                .onSizeChanged { height = it.height.toFloat() }
                .graphicsLayer {
                    translationY = depthOffset.toPx() + swipe
                    scaleX = depthScale
                    scaleY = depthScale
                    // No alpha. Fading the ones behind made them *translucent*
                    // rather than distant: the card in front showed through the
                    // card behind it, and the text of all three overlapped into
                    // a smear. Offset and scale already say "behind", and they
                    // say it without letting anything show through.
                }
                // Swipe it away, toward whichever edge it came from. Only the
                // front one: the others are behind it and cannot be reached.
                .then(
                    if (depth == 0) {
                        Modifier.draggable(
                            state = rememberDraggableState { delta ->
                                // Toward the edge only. Dragging the other way
                                // would lift the toast off the side of the
                                // screen it is anchored to, which is a direction
                                // nothing about it suggests.
                                val next = swipe + delta
                                swipe = if (towardEdge) {
                                    next.coerceAtLeast(0f)
                                } else {
                                    next.coerceAtMost(0f)
                                }
                            },
                            orientation = Orientation.Vertical,
                            onDragStopped = {
                                val far = height * ToastDefaults.SwipeAway
                                if (kotlin.math.abs(swipe) >= far) {
                                    state.dismiss(toast.id)
                                } else {
                                    swipe = 0f
                                }
                            },
                        )
                    } else {
                        Modifier
                    }
                ),
            onAction = {
                toast.onAction?.invoke()
                state.dismiss(toast.id)
            },
            onClose = { state.dismiss(toast.id) },
        )
    }
}

@Composable
private fun ToastSurface(
    toast: Toast,
    showClose: Boolean,
    closeLabel: String,
    modifier: Modifier,
    onAction: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = Theme.colors
    val container = when (toast.tone) {
        ToastTone.Neutral -> colors.surfaceInverse
        ToastTone.Success -> colors.success.solid
        ToastTone.Warning -> colors.warning.solid
        ToastTone.Danger -> colors.danger.solid
        ToastTone.Accent -> colors.accent.solid
    }
    val content = when (toast.tone) {
        ToastTone.Neutral -> colors.onSurfaceInverse
        ToastTone.Success -> colors.success.onSolid
        ToastTone.Warning -> colors.warning.onSolid
        ToastTone.Danger -> colors.danger.onSolid
        ToastTone.Accent -> colors.accent.onSolid
    }

    Surface(
        modifier = modifier
            .widthIn(max = ToastDefaults.MaxWidth)
            .semantics {
                // Assertive for failures the user needs to know about now;
                // polite for confirmations they can hear when convenient.
                liveRegion = if (toast.tone == ToastTone.Danger) {
                    LiveRegionMode.Assertive
                } else {
                    LiveRegionMode.Polite
                }
            },
        shape = Theme.shapes.pill,
        color = container,
        contentColor = content,
        shadow = Theme.elevation.high,
    ) {
        Row(
            modifier = Modifier.padding(
                start = Theme.spacing.md,
                end = if (toast.actionLabel != null || showClose) Theme.spacing.xs else Theme.spacing.md,
                top = Theme.spacing.xs,
                bottom = Theme.spacing.xs,
            ),
            horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (toast.icon != null) {
                Icon(toast.icon, contentDescription = null, size = Theme.sizing.iconSmall)
            }
            Column(Modifier.weight(1f, fill = false)) {
                Text(toast.message, style = Theme.typography.bodySmall)
            }
            if (toast.actionLabel != null) {
                Button(
                    onClick = onAction,
                    variant = ButtonVariant.Ghost,
                    size = ButtonSize.XSmall,
                ) { +toast.actionLabel }
            }
            if (showClose) {
                IconButton(
                    icon = SystemIcons.Close,
                    contentDescription = closeLabel,
                    onClick = onClose,
                    variant = ButtonVariant.Ghost,
                    size = ButtonSize.XSmall,
                )
            }
        }
    }
}
