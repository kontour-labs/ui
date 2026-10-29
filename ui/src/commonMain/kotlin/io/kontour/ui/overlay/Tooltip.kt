package io.kontour.ui.overlay

import kotlin.math.min
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import io.kontour.ui.foundation.Icon
import io.kontour.ui.foundation.ProvideTextStyle
import io.kontour.ui.foundation.PassThroughSurface
import io.kontour.ui.foundation.Surface
import io.kontour.ui.foundation.Text
import io.kontour.ui.input.LocalInputModality
import io.kontour.ui.interaction.rememberLongPressFeedback
import io.kontour.ui.motion.ScrollDefaults
import io.kontour.ui.theme.Shadow
import io.kontour.ui.theme.Theme
import io.kontour.ui.theme.Tone
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import io.kontour.ui.input.onPressOutsideParent
import io.kontour.ui.input.onPressOutside
import io.kontour.ui.input.AlsoInside
import io.kontour.ui.input.alsoInside
import androidx.compose.ui.input.pointer.PointerEventPass

/** The timing [Modifier.tooltip] takes by default. */
object TooltipDefaults {
    /** How long a pointer must rest before a tooltip appears. */
    val HoverDelay: Duration = 500.milliseconds

    /** How long a touch-triggered tooltip stays before dismissing itself. */
    val TouchDuration: Duration = 4_000.milliseconds

    val MaxWidth: Dp
        @Composable @ReadOnlyComposable get() = Theme.componentDefaults.tooltipMaxWidth
}

/**
 * Shows [text] when the user asks what this control is.
 *
 * ```
 * IconButton(
 *     onClick = ::openFilters,
 *     icon = Tabler.Outline.Filter,
 *     contentDescription = "Filter routes",
 *     modifier = Modifier.tooltip("Filter routes"),
 * )
 * ```
 *
 * "Asks" means something different for each input, and the difference is the
 * whole point:
 *
 * | Modality | Trigger | Dismissed by |
 * |---|---|---|
 * | Mouse | Hovering for [TooltipDefaults.HoverDelay] | Moving away |
 * | Keyboard | Focus arriving | Focus leaving |
 * | Touch / stylus | A long press | A timeout, or a tap |
 *
 * Whatever opened it, a press anywhere but on the control or the tooltip puts it
 * away, and goes on to do what it was aimed at: a tap on the button beside it
 * presses the button too. A tap on the control itself puts away a touch tooltip
 * as well; a tap on the tooltip leaves it, as a plain tooltip does everywhere.
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
    alignment: OverlayAlignment = OverlayAlignment.Centre,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
): Modifier = tooltip(side, alignment, enabled, interactionSource) { +text }

/**
 * Shows [content] when the user asks what this control is: the same triggers as
 * the `text` form, with the bubble's own slots for what it says.
 *
 * ```kotlin
 * IconButton(
 *     onClick = ::openFilters,
 *     icon = Tabler.Outline.Filter,
 *     contentDescription = "Filter routes",
 *     modifier = Modifier.tooltip {
 *         +Tabler.Outline.Keyboard
 *         +"Filter routes (F)"
 *     },
 * )
 * ```
 *
 * The slots are a [Tooltip]'s: text with `+`, an icon or a painter beside it,
 * set at the bubble's own size and ink. Like the `text` form it sets no
 * semantics, and for the same reason: what the tooltip says, the control's own
 * `contentDescription` must say already.
 */
@Composable
fun Modifier.tooltip(
    side: OverlaySide = OverlaySide.Top,
    alignment: OverlayAlignment = OverlayAlignment.Centre,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable ContentScope.() -> Unit,
): Modifier {
    val interactions = interactionSource ?: remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    val focused by interactions.collectIsFocusedAsState()
    val modality = LocalInputModality.current
    val longPressed = rememberLongPressFeedback()

    var bounds by remember { mutableStateOf<Rect?>(null) }
    var showing by remember { mutableStateOf(false) }
    // The bubble is not elsewhere: a tap on it leaves it open.
    val bubble = remember { AlsoInside() }

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
                delay(TooltipDefaults.HoverDelay)
                showing = true
            }

            else -> showing = false
        }
    }

    TooltipOverlay(
        visible = showing,
        anchor = { bounds },
        content = content,
        // Nothing to pass: this entry point is a modifier on the *trigger*, and
        // the bubble it shows is not addressable from the call site. Use the
        // `Tooltip` composable when the bubble itself needs one.
        modifier = Modifier,
        side = side,
        alignment = alignment,
        onDismissRequest = { showing = false },
        bubble = bubble,
    )

    return this
        .anchorBounds { bounds = it }
        // A press anywhere else puts it away, and is not taken: it goes on to
        // what it was aimed at. A touch tooltip waited out its four seconds
        // or a tap on the control itself, and was in the way of whatever the
        // reader turned to next. Asked for for 0.5.1. Not a press on the
        // bubble, which leaves it open, as a plain tooltip anywhere does:
        // *"if you tap the tooltip itself, it disappears … let's change that"*.
        .onPressOutside(enabled = showing, alsoInside = bubble) { showing = false }
        .hoverable(interactions, enabled = enabled)
        .pointerInput(enabled, modality) {
            if (!enabled || !modality.needsLargeTargets) return@pointerInput
            awaitEachGesture {
                // **Ahead of the control, and taking nothing from it.** A
                // tooltip is nearly always on a button, and a button's press
                // stops there, so a long press looked for after it never came:
                // a tooltip on an `IconButton` or a `Button` could not be
                // opened by touch at all. Found for 0.5.1, photographing the
                // tap that puts one away. Watched in the initial pass, before
                // the control has it, as Compose's own `TooltipBox` does.
                if (showing) {
                    // A touch tooltip times out on its own; a press on the
                    // control puts it away early, and still presses it.
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    showing = false
                    return@awaitEachGesture
                }
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val slop = viewConfiguration.touchSlop
                // Lifted, moved off or gone before the long press: null only
                // for a finger held still that long.
                val ended = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    while (true) {
                        val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                        if (change == null || !change.pressed || (change.position - down.position).getDistance() > slop) break
                    }
                    true
                }
                if (ended != null) return@awaitEachGesture
                longPressed()
                showing = true
                // The lift that ends the long press is the tooltip's: let go,
                // it is not also a tap on the button under it.
                while (true) {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) {
                        change.consume()
                        break
                    }
                }
            }
        }
}

/**
 * A tooltip whose visibility the caller controls, anchored to its parent.
 *
 * ```
 * Box {
 *     IconButton(icon = Tabler.Outline.Route, contentDescription = "Show route", onClick = ::show)
 *     Tooltip(visible = firstRun, onDismissRequest = { firstRun = false }) { +"Tap to see the whole route" }
 * }
 * ```
 *
 * [Modifier.tooltip] is the one to reach for nearly always — it handles the
 * three triggers and their timings. This overload is for the cases where the
 * *app* decides: a tip shown once after a feature is used for the first time, or
 * a validation hint that appears while a field is wrong.
 *
 * A press anywhere but on the parent or the tooltip asks for it to go, through
 * [onDismissRequest], as the four seconds of a touch tooltip do: the press
 * itself is not taken, and goes on to what it was aimed at.
 *
 * For teaching a feature the user has not found at all, use
 * [Modifier.coachmark] instead — it goes through the [OverlayQueue] and so
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
    alignment: OverlayAlignment = OverlayAlignment.Centre,
    onDismissRequest: () -> Unit = {},
    /** What the bubble looks like: see [TooltipStyle]. */
    style: TooltipStyle = TooltipStyle.Plain,
    content: @Composable ContentScope.() -> Unit,
) {
    var bounds by remember { mutableStateOf<Rect?>(null) }
    // The bubble is not elsewhere: a tap on it leaves it open.
    val bubble = remember { AlsoInside() }
    Box(Modifier.parentBounds { bounds = it }.onPressOutsideParent(enabled = visible, alsoInside = bubble, onPress = onDismissRequest))

    TooltipOverlay(
        visible = visible,
        anchor = { bounds },
        content = content,
        modifier = modifier,
        side = side,
        alignment = alignment,
        onDismissRequest = onDismissRequest,
        style = style,
        bubble = bubble,
    )
}

/**
 * What a tooltip's bubble looks like.
 *
 * [Plain] for a word or a line about a control: the inverse surface, an arrow
 * to what it is about, small type. [Card] for something to read off: the
 * raised surface a menu is drawn on, edged and shadowed as one, standing a
 * little off what it is about and with no arrow, for a few lines with figures
 * in them, a chart's values at a place among them.
 */
enum class TooltipStyle {
    Plain,
    Card,
}

/**
 * The bubble itself, without the triggering. Shared by both entry points, and by
 * a component that decides for itself when and where its tooltip shows — an
 * activity calendar's, which moves from cell to cell under one pointer.
 */
@Composable
internal fun TooltipOverlay(
    visible: Boolean,
    anchor: () -> Rect?,
    content: @Composable ContentScope.() -> Unit,
    modifier: Modifier,
    side: OverlaySide,
    alignment: OverlayAlignment,
    onDismissRequest: () -> Unit,
    style: TooltipStyle = TooltipStyle.Plain,
    /**
     * The bubble's identity in the host. One per call by default. A caller
     * that composes a fresh one for each gesture passes its own, kept across
     * them, so a bubble shown again while the last is leaving turns that one
     * around rather than appearing beside it — an index scroller's, pressed
     * again before the last letter has faded.
     */
    key: Any = remember { Any() },
    /** Where the bubble is laid out, for a press on it to count as not elsewhere. */
    bubble: AlsoInside? = null,
) {
    val host = LocalOverlayHost.current
    val colours = Theme.colours
    val cardGap = Theme.spacing.xs
    val cardRoom = TooltipDefaults.MaxWidth
    val modality = LocalInputModality.current
    val dismiss by rememberUpdatedState(onDismissRequest)
    // Not read by the caller: the bounds change on every frame the trigger
    // scrolls, and read by the caller — `Modifier.tooltip` is part of whatever
    // composable it is written in — that recomposed the whole screen around a
    // tooltip that was not even showing.
    //
    // **Nor here, beyond whether there is one.** Reading the rectangle here
    // moved the recomposition in from the caller and kept it: every row of a
    // list with tooltips on it composed its closed tooltip again on every frame
    // of a scroll. The overlay's measure pass reads the rectangle itself — see
    // `AnchoredOverlayLayout` — and composition only has to know whether to
    // show, which a scroll does not change.
    val latestAnchor by rememberUpdatedState(anchor)
    val anchored by remember { derivedStateOf { latestAnchor() != null } }
    val latestModifier by rememberUpdatedState(modifier)
    // Same reason as the anchor: the bubble's content is read when the overlay
    // composes, not captured when the entry was built. It also keeps the slot
    // out of the effect's keys below — a lambda is a new object every
    // recomposition, so keying on it would tear the entry down and rebuild it
    // on every frame.
    val latestContent by rememberUpdatedState(content)

    DisposableEffect(Unit) { onDispose { host.hide(key) } }

    LaunchedEffect(visible, anchored, side, alignment, style) {
        if (!visible || !anchored) {
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
                        anchorInRoot = { latestAnchor() },
                        // A little overshoot on the way in. A tooltip is one of
                        // the few places a flourish costs nothing — it is already
                        // transient, and nobody is waiting on it to finish before
                        // acting.
                        fromScale = if (style == TooltipStyle.Card) 0.95f else 0.8f,
                        side = side,
                        alignment = alignment,
                        // A card stands off what it is about, where an arrow
                        // would have joined the two.
                        gap = if (style == TooltipStyle.Card) cardGap else 0.dp,
                        margin = MenuDefaults.ScreenMargin,
                        // A card opens on its side only where it fits whole,
                        // and on the other where it does not: squeezed into
                        // the last of the room by a chart's end, its rows
                        // broke their names over two lines.
                        minimumPanel = if (style == TooltipStyle.Card) cardRoom else AnchoredOverlayDefaults.MinimumPanel,
                        // Whole on any side before scrolled on the one asked for.
                        fitWhole = true,
                        arrow = if (style == TooltipStyle.Card) null else ArrowSpec(colour = colours.surfaceInverse, panelShape = Theme.shapes.container),
                    ) {
                        TooltipBubble(latestContent, if (bubble != null) latestModifier.alsoInside(bubble) else latestModifier, style)
                    }
                },
            )
        )

        if (modality.needsLargeTargets) {
            delay(TooltipDefaults.TouchDuration)
            dismiss()
        }
    }
}

@Composable
private fun TooltipBubble(content: @Composable ContentScope.() -> Unit, modifier: Modifier, style: TooltipStyle) {
    val card = style == TooltipStyle.Card
    // A surface a press goes through: the bubble only says what is under the
    // pointer, and a tap on it is a tap on what it is over.
    PassThroughSurface(
        // The caller's modifier first, so a `widthIn` of their own wins the
        // constraint rather than being clamped by the default behind it.
        modifier = modifier
            .widthIn(max = TooltipDefaults.MaxWidth),
        shape = Theme.shapes.container,
        containerColour = if (card) Theme.colours.surfaceRaised else Theme.colours.surfaceInverse,
        contentColour = if (card) Theme.colours.content else Theme.colours.onSurfaceInverse,
        // A card's edge and shadow are an overlay panel's, so it sits over a
        // page as a menu does: see `OverlaySurface`.
        border = if (card) BorderStroke(Theme.sizing.borderWidth, Theme.colours.outlineSubtle) else null,
        shadow = if (card) Theme.elevation.overlay else Shadow.None,
    ) {
        // Scrolls for the same reason `PopoverPanel` does: an anchored overlay is
        // bounded to the room on its own side now, and a bound without a scroller is
        // a bound that loses text. A tooltip is two lines and will almost never use
        // it — almost never is not never, at 200% type against a short side.
        //
        // A scroller only once its words overflow. A scroller is a pointer
        // target wherever it is, and disabled still is one, so the bubble
        // swallowed taps aimed at what was under it — another sector of a
        // chart, the next bar — when it is the one overlay meant never to
        // block. Until then its words are measured as tall as they are, and
        // the bubble is no target at all.
        val scroll = rememberScrollState()
        var overflows by remember { mutableStateOf(false) }
        Box(
            Modifier
                .then(
                    if (overflows) {
                        Modifier.verticalScroll(scroll, flingBehavior = ScrollDefaults.flingBehavior())
                    } else {
                        Modifier.layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints.copy(maxHeight = Constraints.Infinity))
                            if (constraints.hasBoundedHeight && placeable.height > constraints.maxHeight) overflows = true
                            val height = if (constraints.hasBoundedHeight) min(placeable.height, constraints.maxHeight) else placeable.height
                            layout(placeable.width, height) { placeable.placeRelative(0, 0) }
                        }
                    }
                )
                .padding(
                    horizontal = Theme.spacing.sm,
                    vertical = if (card) Theme.spacing.xs else Theme.spacing.xxs,
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
 *     onClick = ::save,
 *     icon = Tabler.Outline.Bookmark,
 *     contentDescription = "Save this trip",
 *     modifier = Modifier.coachmark(
 *         id = "save-trip",
 *         title = "Save this trip",
 *         message = "Trips you save show up on the home screen.",
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
 * @param tone The bubble's colour: the accent by default, any [Tone] the tip is
 *   better said in.
 */
@Composable
fun Modifier.coachmark(
    id: String,
    title: String,
    message: String,
    icon: ImageVector? = null,
    side: OverlaySide = OverlaySide.Bottom,
    alignment: OverlayAlignment = OverlayAlignment.Centre,
    tone: Tone = Tone.Accent,
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
    //
    // **The same condition every time, not a new one.** The queue keeps its
    // conditions in snapshot state, and `queue.current` below reads them, so a
    // fresh lambda written after each composition was a change to something
    // this composition had just read: it composed again, wrote again, and the
    // screen round a pending coach mark recomposed on every frame for as long
    // as it was pending. One remembered lambda, reading [enabled] live, puts the
    // value that is already there.
    //
    // Whether there are bounds rather than what they are, for the same reason:
    // the condition is read in this composition, and the rectangle moves on
    // every frame the anchor scrolls.
    val latestEnabled by rememberUpdatedState(enabled)
    val placed by remember { derivedStateOf { bounds != null } }
    val condition = remember { { latestEnabled && placed } }
    SideEffect { queue.request(request, condition) }
    DisposableEffect(id) { onDispose { queue.withdraw(id) } }

    CoachMarkOverlay(
        visible = queue.current?.id == id,
        anchor = { bounds },
        title = title,
        message = message,
        icon = icon,
        side = side,
        alignment = alignment,
        tone = tone,
        dismissLabel = dismissLabel,
        onDismiss = { queue.dismiss(id) },
    )

    return this.anchorBounds { bounds = it }
}

@Composable
private fun CoachMarkOverlay(
    visible: Boolean,
    anchor: () -> Rect?,
    title: String,
    message: String,
    icon: ImageVector?,
    side: OverlaySide,
    alignment: OverlayAlignment,
    tone: Tone,
    dismissLabel: String,
    onDismiss: () -> Unit,
) {
    val host = LocalOverlayHost.current
    val colours = solidTone(tone)
    val key = remember { Any() }
    val dismissNow by rememberUpdatedState(onDismiss)
    // Read by the overlay's measure pass, and here only for whether there is
    // one — see [TooltipOverlay].
    val latestAnchor by rememberUpdatedState(anchor)
    val anchored by remember { derivedStateOf { latestAnchor() != null } }

    DisposableEffect(Unit) { onDispose { host.hide(key) } }

    LaunchedEffect(visible, anchored, title, message, side, alignment, tone) {
        if (!visible || !anchored) {
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
                        anchorInRoot = { latestAnchor() },
                        fromScale = 0.85f,
                        side = side,
                        alignment = alignment,
                        gap = Theme.spacing.xxs,
                        margin = MenuDefaults.ScreenMargin,
                        arrow = ArrowSpec(colour = colours.container, panelShape = Theme.shapes.container),
                        fitWhole = true,
                    ) {
                        CoachMarkBubble(
                            title = title,
                            message = message,
                            icon = icon,
                            colours = colours,
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
    message: String,
    icon: ImageVector?,
    colours: SolidTone,
    dismissLabel: String,
    onDismiss: () -> Unit,
) {
    // The tour's bubble, with the one way on a lone mark has: acknowledging
    // rather than ending, and filled, as the tour's is.
    CoachmarkPanel(
        title = title,
        message = message,
        icon = icon,
        colours = colours,
        said = Theme.strings.sentences(listOf(title, message)),
    ) {
        Box(Modifier.weight(1f))
        CoachmarkOnward(label = dismissLabel, colours = colours, onClick = onDismiss)
    }
}
