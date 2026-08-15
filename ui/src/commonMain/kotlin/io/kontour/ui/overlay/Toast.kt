package io.kontour.ui.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.action.Button
import io.kontour.ui.components.action.ButtonSize
import io.kontour.ui.components.action.ButtonVariant
import io.kontour.ui.foundation.Icon
import io.kontour.ui.foundation.Surface
import io.kontour.ui.foundation.Text
import io.kontour.ui.adaptive.sheetEdges
import io.kontour.ui.theme.Theme
import kotlinx.coroutines.delay

/** What a toast is reporting. */
enum class ToastTone { Neutral, Success, Warning, Danger, Accent }

/** One queued toast. */
@Stable
class Toast internal constructor(
    val id: Long,
    val message: String,
    val tone: ToastTone,
    val icon: ImageVector?,
    val actionLabel: String?,
    val onAction: (() -> Unit)?,
    val durationMillis: Long,
)

/**
 * Shows short confirmations of things the user just did.
 *
 * ```
 * val toasts = rememberToasts()
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
 */
@Stable
class ToastHostState {
    private var nextId = 0L
    internal val queue = mutableStateListOf<Toast>()

    /** The toast currently showing, if any. One at a time — see [ToastHost]. */
    internal val current: Toast? get() = queue.firstOrNull()

    /**
     * Queues a toast.
     *
     * @param durationMillis How long before it dismisses itself. Longer when
     *   there is an action, since the user has to read it *and* decide. **Zero
     *   means it stays** until [dismissCurrent] or [clear] — for a toast whose
     *   action is the point and which the user must actually answer. Reach for
     *   it rarely: a confirmation that will not go away is a banner that has
     *   been put in the wrong place, and [io.kontour.ui.components.display.Banner]
     *   is the component for that.
     */
    fun show(
        message: String,
        tone: ToastTone = ToastTone.Neutral,
        icon: ImageVector? = null,
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null,
        durationMillis: Long = if (actionLabel != null) 8_000 else 4_000,
    ) {
        queue.add(
            Toast(
                id = nextId++,
                message = message,
                tone = tone,
                icon = icon,
                actionLabel = actionLabel,
                onAction = onAction,
                durationMillis = durationMillis,
            )
        )
    }

    /** Dismisses the current toast and moves to the next. */
    fun dismissCurrent() {
        if (queue.isNotEmpty()) queue.removeAt(0)
    }

    /** Clears everything queued. For navigating away from the context they belong to. */
    fun clear() = queue.clear()
}

@Composable
fun rememberToasts(): ToastHostState = remember { ToastHostState() }

/**
 * Renders whatever [state] has queued. Install once, near the root.
 *
 * Shows **one at a time** rather than stacking. A stack of toasts covers the
 * interface they are reporting on, and by the third one nobody is reading them;
 * queueing means each is actually seen.
 *
 * Toasts sit in [OverlayLayer.Toast] with [ScrimStyle.None], so they never dim
 * or block what is underneath — the user must be able to keep working while one
 * is showing.
 */
@Composable
fun ToastHost(
    state: ToastHostState,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.BottomCenter,
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
    val motion = Theme.motion
    val current = state.current
    val key = remember { Any() }

    LaunchedEffect(current?.id) {
        val toast = current
        if (toast == null) {
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
                content = {
                    Box(
                        Modifier.fillMaxSize().windowInsetsPadding(windowInsets),
                        contentAlignment = alignment,
                    ) {
                        AnimatedVisibility(
                            visible = true,
                            enter = slideInVertically(motion.tweenDefault()) { it / 2 } +
                                fadeIn(motion.tweenFast()),
                            exit = slideOutVertically(motion.tweenFast()) { it / 2 } +
                                fadeOut(motion.tweenFast()),
                        ) {
                            ToastSurface(
                                toast = toast,
                                modifier = modifier.padding(Theme.spacing.md),
                                onAction = {
                                    toast.onAction?.invoke()
                                    state.dismissCurrent()
                                },
                            )
                        }
                    }
                },
            )
        )

        // Zero means it stays: there is nothing to wait for, so there is nothing
        // to schedule. That is also what keeps a pinned toast out of a test's
        // clock — a `delay` here is advanced frame by frame, and one pinned for
        // ten minutes is thirty-seven thousand frames of the page under it.
        if (toast.durationMillis <= 0) return@LaunchedEffect

        delay(toast.durationMillis)
        state.dismissCurrent()
    }
}

@Composable
private fun ToastSurface(
    toast: Toast,
    modifier: Modifier,
    onAction: () -> Unit,
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
            .widthIn(max = 420.dp)
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
                end = if (toast.actionLabel != null) Theme.spacing.xs else Theme.spacing.md,
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
        }
    }
}
