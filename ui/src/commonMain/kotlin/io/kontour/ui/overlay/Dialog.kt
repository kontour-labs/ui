package io.kontour.ui.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.dialog
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.action.Button
import io.kontour.ui.components.action.ButtonVariant
import io.kontour.ui.foundation.Surface
import io.kontour.ui.foundation.Text
import io.kontour.ui.theme.Theme
import kotlinx.coroutines.CompletableDeferred

/**
 * A modal dialog.
 *
 * ```
 * Dialog(visible = showing, onDismiss = { showing = false }) {
 *     Text("Delete this favourite?", style = Theme.typography.titleMedium)
 *     …
 * }
 * ```
 *
 * Renders into the [OverlayHost], so it stacks correctly with sheets and menus
 * and shares one scrim with them. It scales in from slightly small rather than
 * sliding, because a dialog has no direction to come from — it is not somewhere
 * else on the screen, it is *on top of* the screen.
 *
 * @param dismissOnOutside Pass `false` for a decision the user must actually
 *   make. Use sparingly: a dialog that cannot be escaped is a trap, and most
 *   "are you sure" prompts are safe to cancel by tapping away.
 */
@Composable
fun Dialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    key: Any = remember { Any() },
    dismissOnOutside: Boolean = true,
    dismissLabel: String = "Dismiss",
    content: @Composable ColumnScope.() -> Unit,
) {
    val host = LocalOverlayHost.current
    val motion = Theme.motion

    LaunchedEffect(visible, key) {
        if (visible) {
            host.show(
                OverlayEntry(
                    key = key,
                    layer = OverlayLayer.Dialog,
                    dismissOnOutside = dismissOnOutside,
                    dismissLabel = dismissLabel,
                    onDismiss = onDismiss,
                    content = {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            AnimatedVisibility(
                                visible = true,
                                enter = fadeIn(motion.tweenFast()) +
                                    scaleIn(
                                        motion.springOrTween(motion.springDefault),
                                        initialScale = 0.92f,
                                    ),
                                exit = fadeOut(motion.tweenFast()) +
                                    scaleOut(motion.tweenFast(), targetScale = 0.92f),
                            ) {
                                Surface(
                                    modifier = modifier
                                        .padding(Theme.spacing.lg)
                                        .widthIn(max = 400.dp)
                                        .semantics { dialog() },
                                    shape = Theme.shapes.large,
                                    color = Theme.colors.surfaceRaised,
                                    shadow = Theme.elevation.overlay,
                                ) {
                                    Column(
                                        modifier = Modifier.padding(Theme.spacing.lg),
                                        verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm),
                                        content = content,
                                    )
                                }
                            }
                        }
                    },
                )
            )
        } else {
            host.hide(key)
        }
    }
}

/**
 * A dialog with a title, a message and up to two actions.
 *
 * ```
 * AlertDialog(
 *     visible = confirming,
 *     title = "Delete favourite?",
 *     message = "Perth Station will be removed from your list.",
 *     confirmLabel = "Delete",
 *     onConfirm = { viewModel.delete(); confirming = false },
 *     onDismiss = { confirming = false },
 *     destructive = true,
 * )
 * ```
 *
 * The confirm button is on the trailing edge and the cancel on its leading side,
 * which is the arrangement both platforms have converged on. Actions wrap onto
 * separate lines when the labels are long rather than truncating — a truncated
 * "Delete permanently" reading as "Delete perman…" is how people confirm things
 * they did not mean to.
 *
 * @param destructive Renders the confirm action in the danger tone. Set it for
 *   anything the user cannot undo.
 */
@Composable
fun AlertDialog(
    visible: Boolean,
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
    confirmLabel: String? = null,
    onConfirm: (() -> Unit)? = null,
    cancelLabel: String? = "Cancel",
    destructive: Boolean = false,
    dismissOnOutside: Boolean = true,
) {
    Dialog(
        visible = visible,
        onDismiss = onDismiss,
        modifier = modifier,
        dismissOnOutside = dismissOnOutside,
    ) {
        Text(title, style = Theme.typography.titleLarge)
        if (message != null) {
            Text(
                text = message,
                style = Theme.typography.bodyMedium,
                color = Theme.colors.contentMuted,
            )
        }

        FlowRow(
            modifier = Modifier.padding(top = Theme.spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(
                Theme.spacing.xs,
                Alignment.End,
            ),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
        ) {
            if (cancelLabel != null) {
                Button(
                    text = cancelLabel,
                    onClick = onDismiss,
                    variant = ButtonVariant.Ghost,
                )
            }
            if (confirmLabel != null && onConfirm != null) {
                Button(
                    text = confirmLabel,
                    onClick = onConfirm,
                    variant = if (destructive) {
                        ButtonVariant.Destructive
                    } else {
                        ButtonVariant.Primary
                    },
                )
            }
        }
    }
}

/**
 * A confirmation you can `await`.
 *
 * ```
 * val confirmations = rememberConfirmations()
 * ConfirmHost(confirmations)
 *
 * // anywhere with a coroutine scope:
 * if (confirmations.confirm("Delete favourite?", destructive = true)) {
 *     viewModel.delete()
 * }
 * ```
 *
 * The imperative shape matters more than it looks. A caller that has to hoist a
 * `showingDialog` flag, render a dialog, and thread the result back through a
 * callback ends up scattering one decision across three places. Suspending until
 * the user answers keeps it in one expression — which is the ergonomics
 * `admin/src/lib/modal.svelte.ts` already provides on the web, where it returns
 * a promise.
 */
@Stable
class ConfirmationController {
    internal var pending: PendingConfirmation? by mutableStateOf(null)
        private set

    internal class PendingConfirmation(
        val title: String,
        val message: String?,
        val confirmLabel: String,
        val cancelLabel: String,
        val destructive: Boolean,
        val result: CompletableDeferred<Boolean>,
    )

    /** Shows a confirmation and suspends until the user answers. */
    suspend fun confirm(
        title: String,
        message: String? = null,
        confirmLabel: String = "Confirm",
        cancelLabel: String = "Cancel",
        destructive: Boolean = false,
    ): Boolean {
        // Any confirmation already waiting is answered "no" rather than left
        // suspended forever — an abandoned coroutine is a leak that presents as
        // a screen that never responds.
        pending?.result?.complete(false)

        val deferred = CompletableDeferred<Boolean>()
        pending = PendingConfirmation(
            title = title,
            message = message,
            confirmLabel = confirmLabel,
            cancelLabel = cancelLabel,
            destructive = destructive,
            result = deferred,
        )
        return try {
            deferred.await()
        } finally {
            pending = null
        }
    }

    internal fun answer(value: Boolean) {
        pending?.result?.complete(value)
    }
}

@Composable
fun rememberConfirmations(): ConfirmationController = remember { ConfirmationController() }

/** Renders whatever [controller] is currently asking. Install once, near the root. */
@Composable
fun ConfirmHost(controller: ConfirmationController) {
    val pending = controller.pending

    AlertDialog(
        visible = pending != null,
        title = pending?.title.orEmpty(),
        message = pending?.message,
        confirmLabel = pending?.confirmLabel,
        cancelLabel = pending?.cancelLabel,
        destructive = pending?.destructive == true,
        onConfirm = { controller.answer(true) },
        onDismiss = { controller.answer(false) },
    )
}
