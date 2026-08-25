package io.kontour.ui.overlay

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.action.Button
import io.kontour.ui.components.action.ButtonSize
import io.kontour.ui.components.action.ButtonVariant
import io.kontour.ui.foundation.ContentScope
import io.kontour.ui.foundation.ContentSlot
import io.kontour.ui.foundation.ProvideTextStyle
import io.kontour.ui.foundation.Icon
import io.kontour.ui.foundation.Surface
import io.kontour.ui.foundation.Text
import io.kontour.ui.input.LocalInputModality
import io.kontour.ui.interaction.FeedbackIntent
import io.kontour.ui.interaction.LocalFeedback
import io.kontour.ui.theme.Theme
import kotlinx.coroutines.delay

object TooltipDefaults {
    /** How long a pointer must rest before a tooltip appears. */
    const val HoverDelayMillis: Long = 500

    /** How long a touch-triggered tooltip stays before dismissing itself. */
    const val TouchDurationMillis: Long = 4_000

    val MaxWidth: Dp = 280.dp
}

/**
 * Shows [text] when the user asks what this control is.
 *
 * ```
 * IconButton(
 *     icon = Tabler.Outline.Filter,
 *     contentDescription = "Filter routes",
 *     onClick = ::openFilters,
 *     modifier = Modifier.tooltip("Filter routes"),
 * )
 * ```
 *
 * "Asks" means something different for each input, and the difference is the
 * whole point:
 *
 * | Modality | Trigger | Dismissed by |
 * |---|---|---|
 * | Mouse | Hovering for [TooltipDefaults.HoverDelayMillis] | Moving away |
 * | Keyboard | Focus arriving | Focus leaving |
 * | Touch / stylus | A long press | A timeout |
 *
 * A hover-only tooltip is unreachable on a phone; a long-press-only tooltip
 * never fires for a mouse. Both are wired, and neither fires for the other's
 * modality.
 *
 * **A tooltip is not a label.** Anything the user needs in order to understand a
 * control belongs in its `contentDescription` — the tooltip repeats it for
 * sighted pointer users, it does not supply it. This modifier deliberately does
 * *not* set semantics for that reason: a screen reader would otherwise announce
 * the same string twice.
 *
 * @param enabled Turn it off rather than swapping the modifier out, so the
 *   hover and focus tracking are not torn down and rebuilt on every state
 *   change.
 */
@Composable
fun Modifier.tooltip(
    text: String,
    side: OverlaySide = OverlaySide.Top,
    alignment: OverlayAlignment = OverlayAlignment.Center,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
): Modifier {
    val interactions = interactionSource ?: remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    val focused by interactions.collectIsFocusedAsState()
    val modality = LocalInputModality.current
    val feedback = LocalFeedback.current

    var bounds by remember { mutableStateOf<Rect?>(null) }
    var showing by remember { mutableStateOf(false) }

    LaunchedEffect(hovered, focused, modality, enabled) {
        if (!enabled) {
            showing = false
            return@LaunchedEffect
        }
        when {
            focused && modality.showsFocusRing -> showing = true
            hovered && modality.supportsHover -> {
                // The delay is what stops a tooltip firing for every control a
                // pointer crosses on its way somewhere else.
                delay(TooltipDefaults.HoverDelayMillis)
                showing = true
            }

            else -> showing = false
        }
    }

    TooltipOverlay(
        visible = showing,
        anchor = bounds,
        content = { +text },
        // Nothing to pass: this entry point is a modifier on the *trigger*, and
        // the bubble it shows is not addressable from the call site. Use the
        // `Tooltip` composable when the bubble itself needs one.
        modifier = Modifier,
        side = side,
        alignment = alignment,
        onDismissRequest = { showing = false },
    )

    return this
        .anchorBounds { bounds = it }
        .hoverable(interactions, enabled = enabled)
        .pointerInput(enabled, modality) {
            if (!enabled || !modality.needsLargeTargets) return@pointerInput
            detectTapGestures(
                onLongPress = {
                    feedback.perform(FeedbackIntent.LongPress)
                    showing = true
                },
            )
        }
        .then(
            if (showing && modality.needsLargeTargets) {
                Modifier.pointerInput(Unit) {
                    // A touch tooltip times out on its own; this just lets a tap
                    // anywhere on the control dismiss it early.
                    detectTapGestures { showing = false }
                }
            } else {
                Modifier
            }
        )
}

/**
 * A tooltip whose visibility the caller controls, anchored to its parent.
 *
 * ```
 * Box {
 *     IconButton(Tabler.Outline.Route, "Show route", onClick = ::show)
 *     Tooltip(visible = firstRun) { +"Tap to see the whole route" }
 * }
 * ```
 *
 * [Modifier.tooltip] is the one to reach for nearly always — it handles the
 * three triggers and their timings. This overload is for the cases where the
 * *app* decides: a tip shown once after a feature is used for the first time, or
 * a validation hint that appears while a field is wrong.
 *
 * For teaching a feature the user has not found at all, use
 * [Modifier.coachMark] instead — it goes through the [OverlayQueue] and so
 * cannot collide with five other features having the same idea.
 */
@Composable
fun Tooltip(
    visible: Boolean,
    /**
     * Applied to the *bubble*, not to the trigger.
     *
     * This composable emits a zero-size probe where it is written, purely to
     * read its parent's bounds, so there was nowhere sensible for a modifier to
     * go and it did not take one — the one component in the library missing the
     * parameter every other one has. `Modifier.widthIn` on a long tooltip is the
     * case that made it worth resolving rather than documenting.
     */
    modifier: Modifier = Modifier,
    side: OverlaySide = OverlaySide.Top,
    alignment: OverlayAlignment = OverlayAlignment.Center,
    onDismissRequest: () -> Unit = {},
    content: @Composable ContentScope.() -> Unit,
) {
    var bounds by remember { mutableStateOf<Rect?>(null) }
    Box(Modifier.parentBounds { bounds = it })

    TooltipOverlay(
        visible = visible,
        anchor = bounds,
        content = content,
        modifier = modifier,
        side = side,
        alignment = alignment,
        onDismissRequest = onDismissRequest,
    )
}

/** The bubble itself, without the triggering. Shared by both entry points. */
@Composable
private fun TooltipOverlay(
    visible: Boolean,
    anchor: Rect?,
    content: @Composable ContentScope.() -> Unit,
    modifier: Modifier,
    side: OverlaySide,
    alignment: OverlayAlignment,
    onDismissRequest: () -> Unit,
) {
    val host = LocalOverlayHost.current
    val colors = Theme.colors
    val modality = LocalInputModality.current
    val key = remember { Any() }
    val dismiss by rememberUpdatedState(onDismissRequest)
    // Read live by the overlay's measure pass rather than captured when the
    // entry was built — see `AnchoredOverlayLayout`.
    val latestAnchor by rememberUpdatedState(anchor)
    val latestModifier by rememberUpdatedState(modifier)
    // Same reason as the anchor: the bubble's content is read when the overlay
    // composes, not captured when the entry was built. It also keeps the slot
    // out of the effect's keys below — a lambda is a new object every
    // recomposition, so keying on it would tear the entry down and rebuild it
    // on every frame.
    val latestContent by rememberUpdatedState(content)

    DisposableEffect(Unit) { onDispose { host.hide(key) } }

    LaunchedEffect(visible, anchor != null, side, alignment) {
        if (!visible || anchor == null) {
            host.hide(key)
            return@LaunchedEffect
        }

        host.show(
            OverlayEntry(
                key = key,
                layer = OverlayLayer.Tooltip,
                // Never blocks: a tooltip that swallows the click the user was
                // lining up is worse than no tooltip.
                scrim = ScrimStyle.None,
                dismissOnOutside = false,
                dismissOnBack = false,
                trapFocus = false,
                content = {
                    AnchoredOverlayLayout(
                        anchorInRoot = { latestAnchor },
                        // A little overshoot on the way in. A tooltip is one of
                        // the few places a flourish costs nothing — it is already
                        // transient, and nobody is waiting on it to finish before
                        // acting.
                        fromScale = 0.8f,
                        side = side,
                        alignment = alignment,
                        gap = 0.dp,
                        margin = MenuDefaults.ScreenMargin,
                        arrow = ArrowSpec(color = colors.surfaceInverse),
                    ) {
                        TooltipBubble(latestContent, latestModifier)
                    }
                },
            )
        )

        if (modality.needsLargeTargets) {
            delay(TooltipDefaults.TouchDurationMillis)
            dismiss()
        }
    }
}

@Composable
private fun TooltipBubble(content: @Composable ContentScope.() -> Unit, modifier: Modifier) {
    Surface(
        // The caller's modifier first, so a `widthIn` of their own wins the
        // constraint rather than being clamped by the default behind it.
        modifier = modifier
            .widthIn(max = TooltipDefaults.MaxWidth),
        shape = Theme.shapes.small,
        color = Theme.colors.surfaceInverse,
        contentColor = Theme.colors.onSurfaceInverse,
    ) {
        Box(
            Modifier.padding(
                horizontal = Theme.spacing.sm,
                vertical = Theme.spacing.xxs,
            )
        ) {
            ProvideTextStyle(Theme.typography.labelMedium) {
                ContentSlot(iconSize = Theme.sizing.iconSmall, content = content)
            }
        }
    }
}

/**
 * A tooltip that shows itself, once, to teach a feature the user has not found.
 *
 * ```
 * IconButton(
 *     icon = Tabler.Outline.Bookmark,
 *     contentDescription = "Save this trip",
 *     onClick = ::save,
 *     modifier = Modifier.coachMark(
 *         id = "save-trip",
 *         title = "Save this trip",
 *         text = "Trips you save show up on the home screen.",
 *         priority = 40,
 *         minSessions = 3,
 *     ),
 * )
 * ```
 *
 * Unlike [tooltip], nothing the user does triggers it — the app decides, which
 * is why it goes through the [OverlayQueue] rather than carrying its own timer.
 * The queue is what stops six features each deciding independently that today is
 * the day to introduce themselves.
 *
 * It also inherits the queue's suppression: while any dialog, sheet or menu is
 * open, no coach mark fires. A tip pointing at a button behind a modal is worse
 * than no tip at all.
 *
 * Requires an [OverlayQueue] in [LocalOverlayQueue]; without one the modifier is
 * inert rather than throwing, since a coach mark is by definition optional.
 *
 * @param id Stable across releases — it is what "already seen this" is keyed on.
 * @param priority Higher shows first. See [OverlayRequest].
 * @param prerequisites Other coach-mark ids that must be dismissed first, for
 *   teaching a sequence in order.
 */
@Composable
fun Modifier.coachMark(
    id: String,
    title: String,
    text: String,
    icon: ImageVector? = null,
    side: OverlaySide = OverlaySide.Bottom,
    alignment: OverlayAlignment = OverlayAlignment.Center,
    priority: Int = 0,
    minSessions: Int = 0,
    prerequisites: Set<String> = emptySet(),
    dismissLabel: String = Theme.strings.gotIt,
    enabled: Boolean = true,
): Modifier {
    val queue = LocalOverlayQueue.current ?: return this
    var bounds by remember { mutableStateOf<Rect?>(null) }

    val request = remember(id, priority, minSessions, prerequisites) {
        OverlayRequest(
            id = id,
            priority = priority,
            prerequisites = prerequisites,
            minSessions = minSessions,
        )
    }

    // Re-registered on every composition, which is safe by design — and is what
    // withdraws the request when the anchor scrolls out of view, since `bounds`
    // goes null and the condition stops holding.
    // Re-registered after every composition rather than during it, so a
    // composition that gets discarded does not leave a request behind.
    SideEffect { queue.request(request) { enabled && bounds != null } }
    DisposableEffect(id) { onDispose { queue.withdraw(id) } }

    CoachMarkOverlay(
        visible = queue.current?.id == id,
        anchor = bounds,
        title = title,
        text = text,
        icon = icon,
        side = side,
        alignment = alignment,
        dismissLabel = dismissLabel,
        onDismiss = { queue.dismiss(id) },
    )

    return this.anchorBounds { bounds = it }
}

@Composable
private fun CoachMarkOverlay(
    visible: Boolean,
    anchor: Rect?,
    title: String,
    text: String,
    icon: ImageVector?,
    side: OverlaySide,
    alignment: OverlayAlignment,
    dismissLabel: String,
    onDismiss: () -> Unit,
) {
    val host = LocalOverlayHost.current
    val colors = Theme.colors
    val key = remember { Any() }
    val dismissNow by rememberUpdatedState(onDismiss)
    // Read live by the overlay's measure pass rather than captured when the
    // entry was built — see `AnchoredOverlayLayout`.
    val latestAnchor by rememberUpdatedState(anchor)

    DisposableEffect(Unit) { onDispose { host.hide(key) } }

    LaunchedEffect(visible, anchor != null, title, text, side, alignment) {
        if (!visible || anchor == null) {
            host.hide(key)
            return@LaunchedEffect
        }

        host.show(
            OverlayEntry(
                key = key,
                layer = OverlayLayer.Tooltip,
                // Transparent rather than dimmed: the user is being shown a
                // thing *in* the interface, so the interface has to stay legible
                // around it. But it does block, because a stray tap on the very
                // control being explained is the likeliest accident here.
                scrim = ScrimStyle.Transparent,
                dismissLabel = dismissLabel,
                onDismiss = { dismissNow() },
                trapFocus = false,
                content = {
                    AnchoredOverlayLayout(
                        anchorInRoot = { latestAnchor },
                        fromScale = 0.85f,
                        side = side,
                        alignment = alignment,
                        gap = Theme.spacing.xxs,
                        margin = MenuDefaults.ScreenMargin,
                        arrow = ArrowSpec(color = colors.accent.solid),
                    ) {
                        CoachMarkBubble(
                            title = title,
                            text = text,
                            icon = icon,
                            dismissLabel = dismissLabel,
                            onDismiss = { dismissNow() },
                        )
                    }
                },
            )
        )
    }
}

@Composable
private fun CoachMarkBubble(
    title: String,
    text: String,
    icon: ImageVector?,
    dismissLabel: String,
    onDismiss: () -> Unit,
) {
    val colors = Theme.colors
    Surface(
        modifier = Modifier
            .widthIn(max = 320.dp)
            .semantics(mergeDescendants = true) { contentDescription = "$title. $text" },
        shape = Theme.shapes.container,
        color = colors.accent.solid,
        contentColor = colors.accent.onSolid,
        shadow = Theme.elevation.overlay,
    ) {
        Column(
            modifier = Modifier.padding(Theme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.xxs),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        size = Theme.sizing.iconMedium,
                    )
                }
                Text(title, style = Theme.typography.titleSmall)
            }
            Text(text, style = Theme.typography.bodySmall)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Theme.spacing.xxs),
                horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xs, Alignment.End),
            ) {
                Button(
                    onClick = onDismiss,
                    variant = ButtonVariant.Ghost,
                    // Ghost takes its label from LocalContentColor, which the
                    // bubble's Surface has already set to `accent.onSolid`.
                    size = ButtonSize.Small,
                ) { +dismissLabel }
            }
        }
    }
}
