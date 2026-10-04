package io.kontour.ui.overlay

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.foundation.layout.LayoutScopeMarker
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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.semantics.dialog
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import io.kontour.ui.a11y.contrastEdge
import io.kontour.ui.adaptive.ContainerGaps
import io.kontour.ui.adaptive.OverlapInsets
import io.kontour.ui.adaptive.allEdges
import io.kontour.ui.adaptive.measuresGaps
import io.kontour.ui.components.action.Button
import io.kontour.ui.components.action.ButtonVariant
import io.kontour.ui.components.display.StateScope
import io.kontour.ui.components.display.stateSlots
import io.kontour.ui.components.list.fadingEdges
import io.kontour.ui.foundation.ContentScope
import io.kontour.ui.foundation.ContentSlot
import io.kontour.ui.foundation.ProvideContentColour
import io.kontour.ui.foundation.ProvideTextStyle
import io.kontour.ui.foundation.Surface
import io.kontour.ui.foundation.Text
import io.kontour.ui.interaction.Feedback
import io.kontour.ui.interaction.FeedbackIntent
import io.kontour.ui.motion.ScrollDefaults
import io.kontour.ui.theme.Theme
import kotlinx.coroutines.CompletableDeferred
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * A modal dialog.
 *
 * ```
 * Dialog(
 *     visible = showing,
 *     onDismissRequest = { showing = false },
 *     title = { +"Filter departures" },
 *     actions = {
 *         cancel()
 *         confirm("Apply") { apply(); showing = false }
 *     },
 * ) {
 *     …
 * }
 * ```
 *
 * Renders into the [OverlayHost], so it stacks correctly with sheets and menus
 * and shares one scrim with them. It scales in from slightly small rather than
 * sliding, because a dialog has no direction to come from — it is not somewhere
 * else on the screen, it is *on top of* the screen.
 *
 * @param title A heading above the content, in `titleLarge` as an [AlertDialog]'s
 *   is, and announced as a heading: `title = { +"Filter departures" }`. The
 *   body below it is any column of content.
 * @param actions The buttons along the foot — `actions = { cancel(); confirm("Save") { … } }`
 *   — laid out as an [AlertDialog]'s are and kept on screen while the content
 *   scrolls. See [DialogActionsScope].
 * @param dismissible Pass `false` for a decision the user must actually
 *   make. Use sparingly: a dialog that cannot be escaped is a trap, and most
 *   "are you sure" prompts are safe to cancel by tapping away.
 */
@Composable
fun Dialog(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * The overlay's identity in the host, and the one thing two of these shown
     * from the same place need to differ in. The default is one per call site,
     * remembered, which is right unless a loop shows several.
     */
    key: Any = remember { Any() },
    dismissible: Boolean = true,
    dismissLabel: String = Theme.strings.dismiss,
    /**
     * What the dialog keeps clear of. Every edge including the keyboard — a
     * dialog is centred rather than pinned, so there is no side it can safely
     * ignore, and a confirmation with a text field in it is common.
     */
    windowInsets: WindowInsets = WindowInsets.allEdges,
    title: (@Composable ContentScope.() -> Unit)? = null,
    actions: (@Composable DialogActionsScope.() -> Unit)? = null,
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
    val latestTitle by rememberUpdatedState(title)
    val latestActions by rememberUpdatedState(actions)
    val latestDismiss = rememberUpdatedState(onDismissRequest)

    LaunchedEffect(visible, key) {
        if (visible) {
            host.show(
                OverlayEntry(
                    key = key,
                    layer = OverlayLayer.Dialog,
                    // A dialog owns the screen: focus must not wander back to
                    // the form behind it. The default, said out loud.
                    trapFocus = true,
                    dismissOnOutside = dismissible,
                    // Back takes the same answer as a tap outside. A dialog that
                    // may not be dismissed still takes the gesture — it dims the
                    // page — and refuses it; see `OverlayHostState.backTarget`.
                    dismissOnBack = dismissible,
                    dismissLabel = dismissLabel,
                    onDismiss = onDismissRequest,
                    content = {
                        // Only the part of an inset over the overlay's own
                        // container: see `ContainerGaps`. Measured outside the
                        // appearance's scale, which would move it.
                        val gaps = remember { ContainerGaps() }
                        val insets = remember(latestInsets, gaps) { OverlapInsets(latestInsets, gaps) }
                        Box(
                            Modifier
                                .fillMaxSize()
                                .measuresGaps(gaps)
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
                                .windowInsetsPadding(insets),
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
                                    containerColour = Theme.colours.surfaceRaised,
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
                                    val scroll = rememberScrollState()
                                    val pinned = latestActions
                                    if (pinned == null) {
                                        Column(
                                            modifier = Modifier
                                                .fadingEdges(scroll)
                                                .verticalScroll(scroll, flingBehavior = ScrollDefaults.flingBehavior())
                                                .padding(Theme.spacing.lg),
                                            verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm),
                                        ) {
                                            latestTitle?.let { OverlayTitle(Theme.typography.titleLarge, it) }
                                            body()
                                        }
                                    } else {
                                        // The buttons under the scroller rather than in
                                        // it, so a form taller than the window keeps its
                                        // Save in reach. The scroller takes what is left
                                        // and no more: content that fits leaves the
                                        // dialog exactly as tall as it would have been.
                                        //
                                        // The gap above the row is the content's spacing
                                        // and the row's own `sm`, as it was when the row
                                        // was the last thing in the scroller, so an alert
                                        // dialog came out of this move pixel for pixel.
                                        val scope = remember(latestDismiss) { DialogActionsScope { latestDismiss.value() } }
                                        Column {
                                            Column(
                                                modifier = Modifier
                                                    .weight(1f, fill = false)
                                                    .fillMaxWidth()
                                                    .fadingEdges(scroll)
                                                    .verticalScroll(scroll, flingBehavior = ScrollDefaults.flingBehavior())
                                                    .padding(start = Theme.spacing.lg, top = Theme.spacing.lg, end = Theme.spacing.lg),
                                                verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm),
                                            ) {
                                                latestTitle?.let { OverlayTitle(Theme.typography.titleLarge, it) }
                                                body()
                                            }
                                            DialogActionRow(
                                                scope = scope,
                                                modifier = Modifier.padding(
                                                    start = Theme.spacing.lg,
                                                    top = Theme.spacing.sm + Theme.spacing.sm,
                                                    end = Theme.spacing.lg,
                                                    bottom = Theme.spacing.lg,
                                                ),
                                                actions = pinned,
                                            )
                                        }
                                    }
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
 * @param hapticWarning Buzzes once as the dialog opens. Defaults to
 *   [destructive], and does nothing on a dialog that is not.
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
    /**
     * A physical warning as the dialog arrives.
     *
     * The one place in this library where a haptic fires for something that has
     * not happened yet. Everything else reports a value changing, a detent
     * crossed or an outcome; this reports a *question*, and it is the case the
     * haptics audit kept precisely because the rest went — feedback is worth
     * having where it says something the eye is not already being told, and "the
     * thing you are about to confirm cannot be undone" is that.
     *
     * Defaulted from [destructive] rather than to `true`, so it is opt-*out* on
     * the dialogs that want it and absent everywhere else: a "Save changes?"
     * never buzzes, whatever this is set to, because a non-destructive alert has
     * nothing to warn about. Set it `false` on a destructive dialog that opens
     * often enough for the warning to become wallpaper.
     */
    hapticWarning: Boolean = destructive,
    dismissible: Boolean = true,
    content: StateScope.() -> Unit,
) {
    // Once per question: not again when the screen is recreated with the same
    // question up, and again for a second question asked in the same dialog —
    // it used to be keyed on `visible` alone, which did both of those wrong.
    val feedback = Feedback
    val question = LocalAlertQuestion.current
    var warned by rememberSaveable(question) { mutableStateOf(false) }
    LaunchedEffect(visible, question) {
        if (!visible) {
            warned = false
        } else if (destructive && hapticWarning && !warned) {
            warned = true
            feedback.perform(FeedbackIntent.Warn)
        }
    }

    // Reuses the state block's regions: a title and a body under it. The actions
    // stay parameters rather than slots because the component owns their order,
    // their widths and which of them is drawn destructive — that arrangement is
    // the thing an alert dialog *is*. They are drawn by the same row as a
    // `Dialog`'s `actions`, pinned under the message, so a long message on a short
    // window scrolls and the answers stay where a thumb can reach them.
    val slots = stateSlots(content)
    val hasNeutral = neutralLabel != null && onNeutral != null
    val hasConfirm = confirmLabel != null && onConfirm != null
    Dialog(
        visible = visible,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        dismissible = dismissible,
        title = slots.title,
        actions = {
            // In the order they are read. The row places them by what they are;
            // this decides only which a screen reader and the Tab key reach
            // first — the two answers before the way out when cancel is on a
            // line of its own under them, and left to right otherwise.
            val threeWay = hasNeutral && hasConfirm && cancelLabel != null
            if (hasNeutral) neutral(neutralLabel, onClick = onNeutral)
            if (cancelLabel != null && !threeWay) cancel(cancelLabel)
            if (hasConfirm) confirm(confirmLabel, destructive = destructive, onClick = onConfirm)
            if (threeWay) cancel(cancelLabel)
        },
    ) {
        slots.message?.let { message ->
            ProvideContentColour(Theme.colours.contentMuted) {
                ProvideTextStyle(Theme.typography.bodyMedium) {
                    ContentSlot(content = message)
                }
            }
        }
    }
}

/**
 * The buttons along a [Dialog]'s foot.
 *
 * ```
 * Dialog(
 *     visible = editing,
 *     onDismissRequest = { editing = false },
 *     title = { +"Rename favourite" },
 *     actions = {
 *         cancel()
 *         confirm("Save", enabled = name.text.isNotBlank()) { rename(name.text); editing = false }
 *     },
 * ) {
 *     TextField(name, label = "Name")
 * }
 * ```
 *
 * The same row an [AlertDialog] draws, for a dialog whose body is more than a
 * message: a form, a picker, a field to type a name into before deleting it.
 * It sits under the content rather than in it, so a form taller than the window
 * scrolls and its Save stays on screen.
 *
 * Each is at most once, and the row places them by what they are, not by the
 * order they are called in: the confirm on the trailing edge and the cancel
 * beside it, sharing the width as equal halves — or, with a [neutral] as well,
 * the two answers together and the cancel on a line of its own beneath them.
 * The order of the calls is the order a screen reader and the Tab key take them
 * in, so write them in the order they should be read. A second call of the
 * same one is not drawn.
 *
 * Composable, so a label can come from `Theme.strings` or a string resource
 * where it is written.
 */
@LayoutScopeMarker
@Stable
class DialogActionsScope internal constructor(private val dismiss: () -> Unit) {

    /**
     * The way out: asks the dialog to close, as a tap outside it or back does —
     * it calls the dialog's `onDismissRequest`. Drawn as a ghost button.
     */
    @Composable
    fun cancel(label: String = Theme.strings.cancel, enabled: Boolean = true) {
        Button(
            onClick = dismiss,
            modifier = Modifier.layoutId(DialogActionRole.Cancel),
            enabled = enabled,
            variant = ButtonVariant.Ghost,
        ) { +label }
    }

    /**
     * The answer the dialog is asking for — "Save", "Delete", "Send".
     *
     * @param enabled `false` until the form can be submitted. A Save that cannot
     *   be pressed says the form is not done yet; one that can be pressed and
     *   then refuses says it only after the user has reached for it.
     * @param destructive In the danger tone, for an answer that cannot be undone.
     * @param loading A spinner in place of the label while the answer is being
     *   acted on, and no second press meanwhile.
     */
    @Composable
    fun confirm(
        label: String,
        enabled: Boolean = true,
        destructive: Boolean = false,
        loading: Boolean = false,
        onClick: () -> Unit,
    ) {
        Button(
            onClick = onClick,
            modifier = Modifier.layoutId(DialogActionRole.Confirm),
            enabled = enabled,
            variant = if (destructive) ButtonVariant.Destructive else ButtonVariant.Primary,
            loading = loading,
        ) { +label }
    }

    /**
     * A second answer beside the confirm — "Don't save" beside "Save". Rare: a
     * dialog with three answers is usually a question not yet decided.
     */
    @Composable
    fun neutral(label: String, enabled: Boolean = true, onClick: () -> Unit) {
        Button(
            onClick = onClick,
            modifier = Modifier.layoutId(DialogActionRole.Neutral),
            enabled = enabled,
            variant = ButtonVariant.Secondary,
        ) { +label }
    }
}

private enum class DialogActionRole { Neutral, Cancel, Confirm }

/**
 * The row of a dialog's buttons, placed by role.
 *
 * The buttons share the width rather than sizing to their labels. Content-sized
 * and end-aligned is right for a desktop dialog and wrong for the one shape this
 * is nearly always in: a phone, where "Cancel" and "Delete favourite" came out
 * as a small target beside a large one, both crowded into the trailing corner
 * and neither near a thumb. Equal halves make the choice read as a choice.
 *
 * ### Three answers break the row, and the break goes under *cancel*
 *
 * Three equal thirds put three labels in a third of a dialog each, which is
 * where a button starts ellipsising its own verb. So one of them takes a line of
 * its own — and it is cancel, because cancel is not one of the answers. "Save"
 * and "Don't save" are the question; "Cancel" is declining to answer it, and
 * putting it beside them made the dialog read as a three-way choice rather than
 * a two-way one with a way out.
 *
 * A layout rather than a `Row` in a `Column` because the calls come in any
 * order and the places do not. The shares are a weighted `Row`'s to the pixel —
 * see [equalShares] — so the alert dialogs drawn by a `Row` before this came
 * out of the change unmoved.
 */
@Composable
private fun DialogActionRow(
    scope: DialogActionsScope,
    modifier: Modifier,
    actions: @Composable DialogActionsScope.() -> Unit,
) {
    val gap = Theme.spacing.xs
    Layout(content = { scope.actions() }, modifier = modifier) { measurables, constraints ->
        val neutral = measurables.firstOrNull { it.layoutId == DialogActionRole.Neutral }
        val cancel = measurables.firstOrNull { it.layoutId == DialogActionRole.Cancel }
        val confirm = measurables.firstOrNull { it.layoutId == DialogActionRole.Confirm }
        val threeWay = neutral != null && cancel != null && confirm != null
        val row = if (threeWay) listOfNotNull(neutral, confirm) else listOfNotNull(neutral, cancel, confirm)

        val gapPx = gap.roundToPx()
        val width = if (constraints.hasBoundedWidth) {
            constraints.maxWidth
        } else {
            // Nothing in a dialog measures it this way; a row as wide as its
            // widest button, times the buttons, is the honest answer if one does.
            row.maxOfOrNull { it.maxIntrinsicWidth(constraints.maxHeight) }?.let { it * row.size + gapPx * (row.size - 1) } ?: 0
        }
        val shares = equalShares(width - gapPx * (row.size - 1), row.size)
        val placed = row.mapIndexed { index, measurable ->
            measurable.measure(Constraints(minWidth = shares[index], maxWidth = shares[index], maxHeight = constraints.maxHeight))
        }
        val rowHeight = placed.maxOfOrNull { it.height } ?: 0
        val under = if (threeWay) {
            cancel.measure(Constraints(minWidth = width, maxWidth = width, maxHeight = constraints.maxHeight))
        } else {
            null
        }
        val height = rowHeight + (under?.let { gapPx + it.height } ?: 0)
        layout(width, height) {
            var x = 0
            placed.forEach {
                it.placeRelative(x, 0)
                x += it.width + gapPx
            }
            under?.placeRelative(0, rowHeight + gapPx)
        }
    }
}

/**
 * [total] pixels in [count] equal parts, rounded as a `Row` rounds its weights:
 * each the nearest whole share, and what that leaves over or under taken a
 * pixel at a time from the first.
 */
internal fun equalShares(total: Int, count: Int): IntArray {
    if (count <= 0) return IntArray(0)
    val each = (total.coerceAtLeast(0).toFloat() / count).roundToInt()
    var remainder = total.coerceAtLeast(0) - each * count
    return IntArray(count) {
        val unit = remainder.sign
        remainder -= unit
        (each + unit).coerceAtLeast(0)
    }
}

/**
 * A confirmation you can `await`.
 *
 * ```
 * val confirmations = rememberConfirmHostState()
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
 * the user answers keeps it in one expression, the way a promise-returning
 * confirm does on the web.
 *
 * Named as the other hosts' states are — `ToastHostState`, `OverlayHostState` —
 * and dismissed the same way.
 */
@Stable
class ConfirmHostState internal constructor() {
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

    /**
     * Shows a confirmation and suspends until the user answers.
     *
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

    /**
     * Answers whatever is being asked with "no", as the user would by cancelling.
     * Nothing happens if nothing is being asked.
     */
    fun dismiss() {
        answer(false)
    }

    internal fun answer(value: Boolean) {
        pending?.result?.complete(value)
    }
}

/** Remembers a [ConfirmHostState], to hand to one [ConfirmHost] and to `confirm` on. */
@Composable
fun rememberConfirmHostState(): ConfirmHostState = remember { ConfirmHostState() }

/** Renders whatever [state] is currently asking. Install once, near the root. */
@Composable
fun ConfirmHost(state: ConfirmHostState, modifier: Modifier = Modifier) {
    val pending = state.pending

    // Each question its own, so a destructive one asked straight after another
    // — in a dialog that never closed between them — still warns.
    CompositionLocalProvider(LocalAlertQuestion provides pending) {
        AlertDialog(
            visible = pending != null,
            confirmLabel = pending?.let { it.confirmLabel ?: Theme.strings.confirm },
            cancelLabel = pending?.let { it.cancelLabel ?: Theme.strings.cancel },
            destructive = pending?.destructive == true,
            onConfirm = { state.answer(true) },
            onDismissRequest = { state.answer(false) },
            modifier = modifier,
        ) {
            +pending?.title.orEmpty()
            pending?.message?.let { text -> message { +text } }
        }
    }
}

/**
 * Which question an [AlertDialog] is asking, for a host that asks one after
 * another without closing it: a new one is a new destructive warning. Null — one
 * question for the dialog's life — everywhere else.
 */
private val LocalAlertQuestion = staticCompositionLocalOf<Any?> { null }
