package io.kontour.ui.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.dialog
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.kontour.ui.a11y.contrastEdge
import io.kontour.ui.adaptive.allEdges
import io.kontour.ui.components.action.Button
import io.kontour.ui.components.action.ButtonVariant
import io.kontour.ui.components.display.StateScope
import io.kontour.ui.components.display.stateSlots
import io.kontour.ui.foundation.ContentSlot
import io.kontour.ui.foundation.ProvideContentColor
import io.kontour.ui.foundation.ProvideTextStyle
import io.kontour.ui.foundation.Surface
import io.kontour.ui.foundation.Text
import io.kontour.ui.theme.Theme
import kotlinx.coroutines.CompletableDeferred

/**
 * A modal dialog.
 *
 * ```
 * Dialog(visible = showing, onDismissRequest = { showing = false }) {
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
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    key: Any = remember { Any() },
    dismissOnOutside: Boolean = true,
    dismissLabel: String = Theme.strings.dismiss,
    /**
     * What the dialog keeps clear of. Every edge including the keyboard — a
     * dialog is centred rather than pinned, so there is no side it can safely
     * ignore, and a confirmation with a text field in it is common.
     */
    windowInsets: WindowInsets = WindowInsets.allEdges,
    content: @Composable ColumnScope.() -> Unit,
) {
    val host = LocalOverlayHost.current
    val motion = Theme.motion
    // Read live by the entry's content rather than captured when it was
    // published. The effect below is keyed on `visible` and `key` only, so
    // anything else the content closes over is frozen at the moment the overlay
    // was shown — change the modifier of a dialog that is already up and nothing
    // happens until it is dismissed and reopened.
    val latestModifier by rememberUpdatedState(modifier)
    val latestInsets by rememberUpdatedState(windowInsets)
    val body by rememberUpdatedState(content)

    LaunchedEffect(visible, key) {
        if (visible) {
            host.show(
                OverlayEntry(
                    key = key,
                    layer = OverlayLayer.Dialog,
                    dismissOnOutside = dismissOnOutside,
                    dismissLabel = dismissLabel,
                    onDismiss = onDismissRequest,
                    content = {
                        Box(
                            Modifier
                                .fillMaxSize()
                                // On the full-size box rather than on the dialog
                                // itself: the fade needs a compositing buffer
                                // wide enough to hold the dialog's shadow, which
                                // bleeds ~70dp past its edge. See
                                // `Modifier.overlayAppearance`.
                                // Settles *down* into place rather than growing
                                // into it. A dialog that scales up reads as
                                // something being created; one that scales down
                                // reads as something arriving from in front of
                                // the screen, which is where it is.
                                .overlayAppearance(
                                    LocalOverlayProgress.current,
                                    fromScale = 1.06f,
                                )
                                .windowInsetsPadding(latestInsets),
                            contentAlignment = Alignment.Center,
                        ) {
                            // `AnimatedVisibility(visible = true, exit = …)` used
                            // to live here — with `visible` a literal, so the
                            // exit was unreachable code and the host tore the
                            // subtree out before it could ever run. The host's
                            // progress drives both directions now.
                            run {
                                Surface(
                                    modifier = latestModifier
                                        .padding(Theme.spacing.lg)
                                        .widthIn(max = 400.dp)
                                        .semantics { dialog() },
                                    shape = Theme.shapes.panel,
                                    color = Theme.colors.surfaceRaised,
                                    border = contrastEdge(),
                                    shadow = Theme.elevation.overlay,
                                ) {
                                    // Scrollable, because a dialog is centred in
                                    // the window and has no other way out of
                                    // being too tall for it.
                                    //
                                    // Without this the content is measured
                                    // unbounded and placed in a box the window
                                    // has clamped, so it does not merely spill —
                                    // it **overlaps itself**. A `DatePicker` in a
                                    // dialog on a landscape phone drew the last
                                    // two weeks of the month on top of each
                                    // other: "23" and "30" in the same cell, the
                                    // rest cut through the middle of the digits.
                                    // A date picker in a dialog is not an exotic
                                    // arrangement, and 360dp of height is a
                                    // phone turned sideways.
                                    //
                                    // A scroller wrapping content that fits costs
                                    // nothing and changes nothing: it takes its
                                    // content's height until there is not enough
                                    // room, and only then starts scrolling.
                                    Column(
                                        modifier = Modifier
                                            .verticalScroll(rememberScrollState())
                                            .padding(Theme.spacing.lg),
                                        verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm),
                                        content = body,
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
 * A dialog with a title, a message and up to three actions.
 *
 * ```
 * AlertDialog(
 *     visible = confirming,
 *     confirmLabel = "Delete",
 *     onConfirm = { viewModel.delete(); confirming = false },
 *     onDismissRequest = { confirming = false },
 *     destructive = true,
 * ) {
 *     title { +"Delete favourite?" }
 *     message { +"Perth Station will be removed from your list." }
 * }
 * ```
 *
 * The confirm button is on the trailing edge and the cancel on its leading side,
 * which is the arrangement both platforms have converged on. One or two actions
 * share a row as equal halves. A third breaks it: the two *answers* stay
 * together and cancel takes a full line beneath them, because three labels in a
 * third of a dialog each is where a button starts truncating its own verb — and
 * "Delete perman…" is how people confirm things they did not mean to.
 *
 * @param destructive Renders the confirm action in the danger tone. Set it for
 *   anything the user cannot undo.
 */
@Composable
fun AlertDialog(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    confirmLabel: String? = null,
    onConfirm: (() -> Unit)? = null,
    cancelLabel: String? = Theme.strings.cancel,
    /**
     * A third way out — "Don't save" beside "Save" and "Cancel".
     *
     * Rare, and worth resisting: a dialog with three answers is usually a
     * question that has not been decided yet. It exists because the one case
     * that genuinely needs it — discarding work — cannot be expressed as two.
     */
    neutralLabel: String? = null,
    onNeutral: (() -> Unit)? = null,
    destructive: Boolean = false,
    dismissOnOutside: Boolean = true,
    content: StateScope.() -> Unit,
) {
    // Reuses the state block's regions: a title, a body under it, and an action
    // area the component arranges. The actions stay parameters rather than slots
    // because the component owns their order, their widths and which of them is
    // drawn destructive — that arrangement is the thing an alert dialog *is*.
    val slots = stateSlots(content)
    Dialog(
        visible = visible,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        dismissOnOutside = dismissOnOutside,
    ) {
        slots.title?.let { title ->
            ProvideTextStyle(Theme.typography.titleLarge) {
                ContentSlot(content = title)
            }
        }
        slots.supporting?.let { message ->
            ProvideContentColor(Theme.colors.contentMuted) {
                ProvideTextStyle(Theme.typography.bodyMedium) {
                    ContentSlot(content = message)
                }
            }
        }

        // The buttons share the width rather than sizing to their labels.
        //
        // Content-sized and end-aligned is right for a desktop dialog and wrong
        // for the one shape this is nearly always in: a phone, where "Cancel"
        // and "Delete favourite" came out as a small target beside a large one,
        // both crowded into the trailing corner and neither near a thumb. Equal
        // halves make the choice read as a choice.
        //
        // ### Three answers break the row, and the break goes under *cancel*
        //
        // Three equal thirds put three labels in a third of a dialog each, which
        // is where a button starts ellipsising its own verb. So one of them
        // takes a line of its own — and it is cancel, because cancel is not one
        // of the answers. "Save" and "Don't save" are the question; "Cancel" is
        // declining to answer it, and putting it beside them made the dialog
        // read as a three-way choice rather than a two-way one with a way out.
        //
        // This used to break the other way, with the two ghosts sharing a row
        // and the confirm below them.
        val hasNeutral = neutralLabel != null && onNeutral != null
        val hasConfirm = confirmLabel != null && onConfirm != null
        val hasCancel = cancelLabel != null
        val threeWay = hasNeutral && hasConfirm && hasCancel

        val neutral: @Composable (Modifier) -> Unit = { buttonModifier ->
            Button(
                onClick = onNeutral ?: {},
                modifier = buttonModifier,
                variant = ButtonVariant.Secondary,
            ) { +(neutralLabel ?: "") }
        }
        val cancel: @Composable (Modifier) -> Unit = { buttonModifier ->
            Button(
                onClick = onDismissRequest,
                modifier = buttonModifier,
                variant = ButtonVariant.Ghost,
            ) { +(cancelLabel ?: "") }
        }
        val confirm: @Composable (Modifier) -> Unit = { buttonModifier ->
            Button(
                onClick = onConfirm ?: {},
                modifier = buttonModifier,
                variant = if (destructive) ButtonVariant.Destructive else ButtonVariant.Primary,
            ) { +(confirmLabel ?: "") }
        }

        Column(
            modifier = Modifier.padding(top = Theme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
        ) {
            if (hasNeutral || hasCancel || hasConfirm) Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
            ) {
                if (threeWay) {
                    // The two answers, together.
                    neutral(Modifier.weight(1f))
                    confirm(Modifier.weight(1f))
                } else {
                    if (hasNeutral) neutral(Modifier.weight(1f))
                    if (hasCancel) cancel(Modifier.weight(1f))
                    if (hasConfirm) confirm(Modifier.weight(1f))
                }
            }
            // The way out, on its own.
            if (threeWay) cancel(Modifier.fillMaxWidth())
        }
    }
}

/**
 * A confirmation you can `await`.
 *
 * ```
 * val confirmations = rememberConfirmationController()
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
        val confirmLabel: String?,
        val cancelLabel: String?,
        val destructive: Boolean,
        val result: CompletableDeferred<Boolean>,
    )

    /** Shows a confirmation and suspends until the user answers. */
    /**
     * @param confirmLabel `null` — the default — takes the theme's word for it.
     *   Resolved where the dialog is drawn rather than here, because this is a
     *   plain state holder: a `suspend fun` on a controller has no composition
     *   to read `Theme.strings` from, and giving the controller a copy of the
     *   theme to carry around would be the wrong end of the problem.
     */
    suspend fun confirm(
        title: String,
        message: String? = null,
        confirmLabel: String? = null,
        cancelLabel: String? = null,
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
fun rememberConfirmationController(): ConfirmationController = remember { ConfirmationController() }

/** Renders whatever [controller] is currently asking. Install once, near the root. */
@Composable
fun ConfirmHost(controller: ConfirmationController) {
    val pending = controller.pending

    AlertDialog(
        visible = pending != null,
        confirmLabel = pending?.let { it.confirmLabel ?: Theme.strings.confirm },
        cancelLabel = pending?.let { it.cancelLabel ?: Theme.strings.cancel },
        destructive = pending?.destructive == true,
        onConfirm = { controller.answer(true) },
        onDismissRequest = { controller.answer(false) },
    ) {
        +pending?.title.orEmpty()
        pending?.message?.let { message -> supporting { +message } }
    }
}
