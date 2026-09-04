package io.kontour.ui.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animate
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
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.action.Button
import io.kontour.ui.components.action.ButtonColours
import io.kontour.ui.components.action.ButtonSize
import io.kontour.ui.components.action.ButtonVariant
import io.kontour.ui.components.action.IconButton
import io.kontour.ui.foundation.Icon
import io.kontour.ui.foundation.Surface
import io.kontour.ui.foundation.SystemIcons
import io.kontour.ui.foundation.Text
import io.kontour.ui.adaptive.sheetEdges
import io.kontour.ui.adaptive.topEdges
import io.kontour.ui.theme.Theme
import kotlinx.coroutines.delay
import kotlin.math.abs

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
        durationMillis: Long =
            if (actionLabel != null) ToastDefaults.DurationWithAction else ToastDefaults.Duration,
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

/**
 * Which edge a stack of toasts is anchored to.
 *
 * [Bottom] by default, which is where they have always been. [Top] is what a
 * screen wants when the bottom is spoken for — a navigation bar, a sheet, a
 * persistent player — or simply when the thumb rests there and a toast under it
 * is a toast nobody reads.
 *
 * An enum rather than an `Alignment`, because the position decides two things
 * and an `Alignment` only carries one of them. The stack used to infer its
 * direction by casting the alignment to `BiasAlignment` and reading the vertical
 * bias, defaulting to "bottom" for anything else — so a custom `Alignment`
 * silently meant bottom, and `Alignment.Center` did too. Worse, the insets could
 * not follow: they were fixed at
 * [WindowInsets.sheetEdges][io.kontour.ui.adaptive.sheetEdges], which has no top
 * side at all, so a top-anchored toast drew underneath the status bar and the
 * display cutout.
 */
enum class ToastPosition {
    Top,
    Bottom,
    ;

    /** Where the stack sits in its host. */
    internal val alignment: Alignment
        get() = if (this == Top) Alignment.TopCenter else Alignment.BottomCenter

    /**
     * True when the stack recedes *away* from the viewer's edge — which is up
     * for a bottom stack and down for a top one. Drives the peek offsets, the
     * enter and exit slide, and which way a toast is swiped away.
     */
    internal val towardEdge: Boolean get() = this == Bottom
}

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
     * Extra air between the message and the action, on top of the row's own gap.
     *
     * The row already spaces its children by `xs`, which is the gap between an
     * icon and the words it belongs to. An action is not part of the sentence,
     * and at that distance it read as though it were. This doubles it.
     *
     * Doubling and not more, because the gap comes out of the message: a toast
     * is capped at [MaxWidth], and at 12dp the catalog's own "Couldn't reach the
     * timetable service" went from one line to two. The action has a ground
     * under it now, which is most of what tells it apart; the gap only has to
     * finish the job.
     */
    val ActionGap: Dp = 8.dp

    /**
     * How much of the toast's content colour sits under its action's label.
     *
     * Enough to be a shape you could press, faint enough not to compete with
     * the message — the button is the second thing on a toast, not the first.
     */
    const val ActionGround: Float = 0.16f

    /** A disabled action, in the toast's own content colour. */
    const val DisabledContent: Float = 0.38f

    /**
     * How long a toast stays before dismissing itself.
     *
     * Two and a half seconds — long enough to read a confirmation, short enough
     * not to sit there once it has been read. It was four, which is a long time
     * to look at "Saved" and was reported as such.
     *
     * These were inline literals on [ToastHostState.show]'s signature, the one
     * pair of tunables in this file that were not here.
     */
    const val Duration: Long = 2_500

    /**
     * How long a toast with an action stays.
     *
     * Twice the plain one, because an action has to be read, decided on *and*
     * reached — and a control that vanishes as the finger arrives is worse than
     * one that lingers.
     */
    const val DurationWithAction: Long = 5_000

    /**
     * How far a toast has to be dragged toward the edge before it goes.
     *
     * A third of its own height. Short enough that a flick is enough, long
     * enough that a scroll started on top of one does not throw it away.
     */
    const val SwipeAway: Float = 0.33f

    /**
     * How much of a wrong-way drag actually moves the toast.
     *
     * A third. Enough that the card acknowledges the finger, little enough that
     * it is plainly refusing — the usual rubber band. Zero, which is what this
     * used to be, is indistinguishable from a control that has hung.
     */
    const val Resistance: Float = 0.33f
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
    position: ToastPosition = ToastPosition.Bottom,
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
     *
     * Follows [position] by default, which is the whole reason that parameter is
     * an enum: a top-anchored stack needs the status bar and the cutout, and
     * `sheetEdges` has no top side, so it used to draw under both.
     */
    windowInsets: WindowInsets =
        if (position == ToastPosition.Top) WindowInsets.topEdges else WindowInsets.sheetEdges,
) {
    val host = LocalOverlayHost.current
    val key = remember { Any() }
    val occupied = state.toasts.isNotEmpty()

    // Everything the entry's content reads has to be read *live*: the entry is
    // pushed once, when the stack goes from empty to occupied, and composed by
    // the host from then on.
    val latest by rememberUpdatedState(
        ToastHostConfig(modifier, position, maxVisible, showClose, closeLabel, windowInsets)
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
    val position: ToastPosition,
    val maxVisible: Int,
    val showClose: Boolean,
    val closeLabel: String,
    val windowInsets: WindowInsets,
)

@Composable
private fun ToastStack(state: ToastHostState, config: ToastHostConfig) {
    // The stack grows away from the edge it is anchored to: a bottom-anchored
    // stack recedes upward, a top-anchored one downward.
    val towardEdge = config.position.towardEdge
    val visible = state.toasts.takeLast(config.maxVisible)
    val density = LocalDensity.current

    /**
     * One size for the whole stack: the widest and tallest any card needs.
     *
     * **One silhouette is what makes a stack read as a stack.** Every card used
     * to size to its own message, so "Saved" arriving in front of "Couldn't
     * reach the server" left the older, wider card sticking out at both sides:
     * three pills of three widths, which is a pile rather than a deck. Matching
     * them leaves depth — offset and scale — as the only thing telling them
     * apart, which is the thing depth is supposed to say.
     *
     * The **widest**, and not the front one's, because a width is a floor rather
     * than a value here. Narrowing a card re-wraps the message inside it: the
     * first version of this imposed the front card's width and turned "Couldn't
     * reach the server just now" behind a "Saved" into a five-line tower. Only
     * ever widening cannot re-wrap anything.
     *
     * Height for the same reason and a sharper one. The cards are bottom-aligned
     * and then lifted by [ToastDefaults.Peek] each, so the peek is measured from
     * the front card's *top* — and a two-line message in front of a one-line one
     * swallowed the whole stack. Three toasts, and the picture was of one.
     *
     * It ratchets, and only within one stack: a card that has been grown reports
     * its grown size, so the maximum never comes back down while toasts keep
     * arriving. That is the behaviour worth having anyway — a stack that resizes
     * under the message being read is worse than one slightly larger than it
     * needs — and it resets when the stack empties, which is when the host drops
     * the entry this lives in.
     */
    var stackWidthPx by remember { mutableIntStateOf(0) }
    var stackHeightPx by remember { mutableIntStateOf(0) }
    val stackWidth = if (stackWidthPx > 0) with(density) { stackWidthPx.toDp() } else Dp.Unspecified
    val stackHeight = if (stackHeightPx > 0) with(density) { stackHeightPx.toDp() } else Dp.Unspecified

    Box(
        Modifier.fillMaxSize().windowInsetsPadding(config.windowInsets),
        contentAlignment = config.position.alignment,
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
                    stackWidth = stackWidth,
                    stackHeight = stackHeight,
                    onMeasured = { size ->
                        stackWidthPx = maxOf(stackWidthPx, size.width)
                        stackHeightPx = maxOf(stackHeightPx, size.height)
                    },
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
    /** The stack's shared size — see `ToastStack`. Unspecified before it has one. */
    stackWidth: Dp,
    stackHeight: Dp,
    onMeasured: (IntSize) -> Unit,
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

    /**
     * The message on the cards behind, which is not for reading.
     *
     * All three cards are the same width and offset by [ToastDefaults.Peek], so
     * what shows of the ones behind is a band along their top edge — and the
     * text sat right in it. Three messages at once, each sliced through the
     * middle by the card in front, which is worse than the mismatched widths
     * this stack started with.
     *
     * Not the same thing as fading the *card*, which was tried and is in the
     * `graphicsLayer` below as a note: a translucent card lets the one in front
     * show through it and smears the two together. The card stays opaque and
     * only what is written on it goes, so the band behind is a clean plate of
     * colour.
     *
     * Animated so a card coming forward brings its message with it rather than
     * having it appear, on the same spring as the movement that brings it.
     */
    val contentAlpha by animateFloatAsState(
        targetValue = if (depth == 0) 1f else 0f,
        animationSpec = motion.springOrTween(motion.springDefault),
        label = "toastContentAlpha",
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
            contentAlpha = contentAlpha,
            modifier = modifier
                // The gesture is the outermost thing, above the padding — and
                // that is the point. Below it the swipe area was exactly the
                // visible pill and the 16dp ring around it was dead space, on a
                // target that is small to begin with. Up here the ring is live,
                // which is 16dp of slop on every side.
                //
                // Only the front one is draggable: the others are behind it and
                // cannot be reached.
                .then(
                    if (depth == 0) {
                        Modifier.draggable(
                            state = rememberDraggableState { delta ->
                                val next = swipe + delta
                                val awayFromEdge =
                                    if (towardEdge) next < 0f else next > 0f
                                // The wrong way still moves, and resists.
                                // Clamping it outright meant a drag away from
                                // the anchored edge did nothing at all, which
                                // reads as a control that has stopped
                                // responding rather than one that will not go
                                // that way.
                                swipe = if (awayFromEdge) next * ToastDefaults.Resistance else next
                            },
                            orientation = Orientation.Vertical,
                            onDragStopped = {
                                val far = height * ToastDefaults.SwipeAway
                                val towardTheEdge =
                                    if (towardEdge) swipe > 0f else swipe < 0f
                                if (towardTheEdge && abs(swipe) >= far) {
                                    state.dismiss(toast.id)
                                } else {
                                    // Sprung, not snapped. Letting go below the
                                    // threshold used to put the card back in a
                                    // single frame, which looks like a glitch
                                    // rather than like a control returning.
                                    val from = swipe
                                    animate(
                                        initialValue = from,
                                        targetValue = 0f,
                                        animationSpec = motion.springOrTween(motion.springSnappy),
                                    ) { value, _ -> swipe = value }
                                }
                            },
                        )
                    } else {
                        Modifier
                    }
                )
                .padding(Theme.spacing.md)
                // Measured in the layout phase, not read out of the draw phase:
                // writing state from inside `graphicsLayer` is a write during
                // draw, and the recomposition it schedules can loop.
                .onSizeChanged {
                    height = it.height.toFloat()
                    onMeasured(it)
                }
                // Inside the padding, so the depth scale shrinks the *card* and
                // not the card plus its slop — the stack's geometry is tuned to
                // `Peek` against the card, and scaling a bigger box moves every
                // number in it.
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
                // Every card at the stack's width — see `ToastStack`. A
                // minimum rather than an exact size, because narrowing a card
                // re-wraps the message inside it and widening one cannot.
                //
                // Below the `graphicsLayer`, so the depth scale still applies on
                // top: the cards behind are the same width *before* being pushed
                // back, which is what makes them read as further away rather
                // than merely narrower.
                .then(
                    if (stackWidth != Dp.Unspecified) {
                        Modifier.widthIn(min = stackWidth).heightIn(min = stackHeight)
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
    /** How much of the message is drawn — see `ToastCard`. */
    contentAlpha: Float,
    modifier: Modifier,
    onAction: () -> Unit,
    onClose: () -> Unit,
) {
    val colours = Theme.colours
    val container = when (toast.tone) {
        ToastTone.Neutral -> colours.surfaceInverse
        ToastTone.Success -> colours.success.solid
        ToastTone.Warning -> colours.warning.solid
        ToastTone.Danger -> colours.danger.solid
        ToastTone.Accent -> colours.accent.solid
    }
    val content = when (toast.tone) {
        ToastTone.Neutral -> colours.onSurfaceInverse
        ToastTone.Success -> colours.success.onSolid
        ToastTone.Warning -> colours.warning.onSolid
        ToastTone.Danger -> colours.danger.onSolid
        ToastTone.Accent -> colours.accent.onSolid
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
        colour = container,
        contentColour = content,
        shadow = Theme.elevation.high,
    ) {
        Row(
            modifier = Modifier.alpha(contentAlpha).padding(
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
                // Two things were making "Retry" read as the end of the
                // sentence rather than as a button, and both had to go.
                //
                // It sat eight pixels from the message, which is the gap
                // between an icon and the words it belongs to — so it looked
                // like it belonged to them. And a ghost button has no ground
                // until you hover it, which on a solid toast leaves the label
                // in exactly the colour and weight of the text beside it.
                Spacer(Modifier.width(ToastDefaults.ActionGap))
                Button(
                    onClick = onAction,
                    variant = ButtonVariant.Ghost,
                    size = ButtonSize.XSmall,
                    // Derived from the toast's own content colour rather than
                    // taken from the palette: a toast is a solid tone, and an
                    // accent-coloured button on a red one is two brands
                    // arguing. A wash of the colour the text is already in
                    // gives it a ground at every tone without introducing a
                    // second hue.
                    colours = ButtonColours(
                        container = content.copy(alpha = ToastDefaults.ActionGround),
                        content = content,
                        border = null,
                        disabledContainer = Color.Transparent,
                        disabledContent = content.copy(alpha = ToastDefaults.DisabledContent),
                        disabledBorder = null,
                    ),
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
