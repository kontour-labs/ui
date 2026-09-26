package io.kontour.ui.components.list

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker1D
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.ui.a11y.contentColourFor
import io.kontour.ui.foundation.Icon
import io.kontour.ui.foundation.Surface
import io.kontour.ui.foundation.Text
import io.kontour.ui.input.pointerCursor
import io.kontour.ui.interaction.DetentTicker
import io.kontour.ui.interaction.DragClaim
import io.kontour.ui.interaction.FeedbackIntent
import io.kontour.ui.interaction.LocalFeedback
import io.kontour.ui.interaction.horizontalDragOwning
import io.kontour.ui.interaction.rememberDetentTicker
import io.kontour.ui.theme.Theme
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One action revealed by swiping a row. */
@Immutable
class SwipeAction(
    val label: String,
    val icon: ImageVector,
    val onAction: () -> Unit,
    val background: Color,
    /**
     * Whether swiping clear past the reveal commits this action.
     *
     * True by default. A swipe that carries the row the full width of the screen
     * is not something a thumb does by accident, and requiring every action to
     * opt in meant the gesture worked in one direction and silently did nothing
     * in the other — which reads as a broken control rather than as a setting.
     *
     * Set false for an action that should always need the deliberate second tap.
     */
    val isFullSwipeAction: Boolean = true,
)

/** Where a swiped row has settled. */
enum class SwipeValue {
    /** Start actions revealed, waiting for a tap. */
    Start,

    Resting,

    /** End actions revealed, waiting for a tap. */
    End,

    /** Swiped clear past the start actions — the first of them commits. */
    StartCommitted,

    /** Swiped clear past the end actions — the first of them commits. */
    EndCommitted,
}

@Stable
class SwipeActionsState internal constructor(
    internal val anchoredState: AnchoredDraggableState<SwipeValue>,
) {
    val currentValue: SwipeValue get() = anchoredState.settledValue
    val offset: Float get() = anchoredState.offset

    /**
     * Where the row should go once it has anchors.
     *
     * The same trap as [io.kontour.ui.sheet.SheetState]: anchors depend on the
     * row's measured width, so a request made from a `LaunchedEffect` arrives
     * before there is anywhere to move to and would be dropped silently.
     */
    internal var pending: SwipeValue? = null
        private set

    /** Slides back to rest. */
    suspend fun reset() = animateTo(SwipeValue.Resting)

    /**
     * Slides to [value].
     *
     * For revealing the actions without a gesture — a "here is what this row
     * does" hint on first run, or closing every other row when one is opened.
     */
    suspend fun animateTo(value: SwipeValue) {
        if (anchoredState.anchors.hasPositionFor(value)) {
            anchoredState.animateTo(value)
        } else {
            pending = value
        }
    }

    internal suspend fun deliverPending() {
        val target = pending ?: return
        pending = null
        if (anchoredState.anchors.hasPositionFor(target)) anchoredState.animateTo(target)
    }
}

/**
 * Remembers a [SwipeActionsState].
 *
 * @param initialValue Where the row starts. [SwipeActionsState.animateTo] is the
 *   way to move it *later*; this is the way to have it never be anywhere else,
 *   which is a different thing and the one that has no workaround. An animation
 *   needs frames to run in, and there are contexts that have none: a screenshot
 *   is a single moment, and a row restored from saved state should be where it
 *   was rather than sliding there. `rememberSheetState` takes `initialDetent`
 *   for the same reason; this factory was the odd one out.
 *
 *   The row starts here without animating, because the anchors are attached
 *   after the first measure and `updateAnchors` settles on the current value
 *   rather than travelling to it.
 */
@Composable
fun rememberSwipeActionsState(
    initialValue: SwipeValue = SwipeValue.Resting,
): SwipeActionsState {
    val anchored = remember {
        AnchoredDraggableState(initialValue = initialValue)
    }
    return remember { SwipeActionsState(anchored) }
}

object SwipeActionsDefaults {
    /** How wide one action's target is. Two side by side is 176dp of swipe. */
    val ActionWidth: Dp
        @Composable @ReadOnlyComposable get() = Theme.componentDefaults.swipeActionWidth

    /**
     * How far into the reveal a slow release has to be to open it, and how far
     * back out of it to close it.
     *
     * 0.35. It was 0.55, and the commit beyond it asked for four fifths of the rest
     * of the row, and between them a swipe was "way too hard to do" on iOS: a thumb
     * arcing across a list lost the sideways race to the list, and the part that
     * won had most of a row still to travel. A swipe is a shortcut for someone who
     * already knows it is there, and a shortcut that has to be performed carefully
     * is not one.
     *
     * A flick decides by direction instead, and nothing decides a commit but the
     * point of no return — see [SwipeActions].
     */
    val PositionalThreshold: (Float) -> Float
        @Composable @ReadOnlyComposable get() {
            val fraction = Theme.componentDefaults.swipePositionalThreshold
            return { distance -> distance * fraction }
        }

    /** Between two revealed actions, and between them and the row. */
    val Gap: Dp
        @Composable @ReadOnlyComposable get() = Theme.spacing.xs

    /** The most actions one side will take. See [SwipeActions]. */
    const val MaxActionsPerSide: Int = 3

    /**
     * How far one notch of sideways scroll moves the row.
     *
     * A scroll's delta is in notches — a mouse's click is 1, a trackpad sends
     * fractions of one — and this used to be three *pixels* a notch, on the
     * belief that it was pixels already: an 88dp action took dozens of clicks.
     * Compose's own lists move 10dp a notch on a Mac; a swipe is a coarser
     * gesture than a scroll, so a row moves further: two clicks open a single
     * action, and four bring the first of several out in full, which is where a
     * released row opens. A trackpad on a Mac pans instead, and follows the
     * fingers.
     */
    val ScrollStep: Dp = 24.dp

    /** How long after the last scroll event the row settles onto an anchor. */
    const val SettleAfterScroll: Long = 120L
}

/**
 * Reveals actions when a row is swiped sideways.
 *
 * ```kotlin
 * SwipeActions(
 *     end = listOf(
 *         SwipeAction("Delete", Tabler.Outline.Trash, ::delete, Theme.colours.danger.solid,
 *             isFullSwipeAction = true),
 *     ),
 * ) {
 *     ListItem(onClick = { open(stop) }) { +stop.name }
 * }
 * ```
 *
 * **Nothing here may be the only way to reach an action.** A swipe is invisible,
 * has no keyboard equivalent and no pointer equivalent — it is a shortcut for
 * people who already know it is there. Every action also gets a *custom
 * accessibility action* on the row, so a screen reader can reach it, but that
 * covers assistive tech and not a sighted mouse user. Put the same actions in a
 * menu or a detail screen.
 *
 * ### What it looks like
 *
 * As the row slides, up to three separate squircles grow out of the edge it is
 * leaving, one per action, each in its own colour, with page showing between them.
 * **They grow one after another**: the one at the screen edge first, and the next
 * only once the row has moved far enough to make room for it — small and round
 * when each appears, full-height buttons by the time the row has uncovered them.
 * An icon grows in as its button does, and a label unfolds under it once the
 * button is tall enough to hold both. Carry the row on past its actions and, at the
 * point of no return, the outermost one takes over the whole strip while the
 * others fold into it: letting go then runs it, and the others stay folded until
 * the row is home. Asked for in those words — "an Apple-like animation where (up
 * to) three squircle shapes expand out of the side when you start swiping, and
 * then continuing to swipe across will select the rightmost one".
 *
 * A full swipe ends on a tick. The row holds at the far edge while the action's
 * icon gives way to a check mark, drawn in, and runs the action once it has been
 * seen — the moment the row used to spend there anyway, put to use. Off with
 * `fullSwipeConfirmation = false`.
 *
 * ### How it decides
 *
 * - **Whose gesture it is**, early and by direction: a drag within 45° of sideways
 *   is the row's from a few pixels in, and anything steeper is the list's. It used
 *   to wait for a full touch slop and race the list for it, which a thumb's arc
 *   lost — the iOS half of the report.
 * - **Past the point of no return, letting go commits**, at any speed. The point
 *   is a little way past the actions and at least half the row, and a buzz marks
 *   it — once on the way out, once more if the finger backs off it.
 * - **Short of it, a flick opens or closes** the actions by the way it was thrown.
 *   A slow release opens them once the first action is out in full — the moment
 *   its soft tick plays — or, for a single action, a third of the way in; it
 *   closes an open row a third of the way out. A flick never commits on its own:
 *   aimed at a reveal and thrown a little hard, it used to run the action — the
 *   Android half of the report.
 * - **A tap on a row showing its actions closes it**, and does not also reach the
 *   row's own click. A tap on one of the buttons runs that action and closes it.
 *
 * **Order runs from the screen edge in toward the row**, on both sides. So the
 * *first* action of a list is the one furthest from the row, and the last one is
 * the button that appears against its edge as the swipe opens. That is the
 * convention swipe rows use everywhere, and the reason is the full swipe:
 * carrying a row all the way runs the first action of the side, which is the one
 * at the edge the row is sliding onto.
 *
 * @param start Actions revealed by swiping toward the trailing edge, following
 *   the layout direction. Conventionally the constructive ones. **At most
 *   [SwipeActionsDefaults.MaxActionsPerSide]** — three 88dp targets is 264dp of
 *   travel, which is most of a phone's width already; a fourth cannot be
 *   reached on one and the row has run out of places to put it.
 * @param end Revealed by swiping toward the leading edge. Conventionally the
 *   destructive ones, since that is the direction people already flick to
 *   delete. Same limit.
 * @param fullSwipeConfirmation Whether a full swipe shows its action done before
 *   it runs it: the action's icon turns into a tick, drawn, and the row holds at
 *   the far edge a moment longer to show it. Off runs the action the moment the
 *   row arrives, for a list where the next thing on screen is confirmation
 *   enough.
 */
@Composable
fun SwipeActions(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    start: List<SwipeAction> = emptyList(),
    end: List<SwipeAction> = emptyList(),
    state: SwipeActionsState = rememberSwipeActionsState(),
    shape: Shape = Theme.shapes.container,
    actionWidth: Dp = SwipeActionsDefaults.ActionWidth,
    fullSwipeConfirmation: Boolean = true,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    // Named in the message along with the parameter, which is the house rule the
    // degenerate-input sweep settled on: a precondition that says only "3" leaves
    // the reader to find out which of two lists it meant.
    require(start.size <= SwipeActionsDefaults.MaxActionsPerSide) {
        "SwipeActions: start has ${start.size} actions and takes at most " +
            "${SwipeActionsDefaults.MaxActionsPerSide}. Three 88dp targets is " +
            "264dp of travel and most of a phone's width; a fourth cannot be " +
            "reached. Put the rest in a menu or on the detail screen."
    }
    require(end.size <= SwipeActionsDefaults.MaxActionsPerSide) {
        "SwipeActions: end has ${end.size} actions and takes at most " +
            "${SwipeActionsDefaults.MaxActionsPerSide}. Three 88dp targets is " +
            "264dp of travel and most of a phone's width; a fourth cannot be " +
            "reached. Put the rest in a menu or on the detail screen."
    }

    val motion = Theme.motion
    val scope = rememberCoroutineScope()
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val revealShare = Theme.componentDefaults.swipePositionalThreshold

    var width by remember { mutableFloatStateOf(0f) }
    val actionWidthPx = with(density) { actionWidth.toPx() }
    val gapPx = with(density) { SwipeActionsDefaults.Gap.toPx() }
    val flickVelocityPx = with(density) { SwipeFlickVelocity.toPx() }
    val flickTravelPx = with(density) { SwipeFlickTravel.toPx() }
    val commitMarginPx = with(density) { SwipeCommitMargin.toPx() }
    val commitInsetPx = with(density) { SwipeCommitInset.toPx() }
    val hysteresisPx = with(density) { SwipeCommitHysteresis.toPx() }

    // **The offset is a *logical* one: positive is toward the trailing edge, in
    // both layout directions.** The drag below turns the finger's physical delta
    // into it, and `Modifier.offset {}` — `placeRelative` underneath — mirrors it
    // back on the way out. Nothing between those two ends looks at a physical
    // direction.
    val startTravel = start.size * actionWidthPx
    val endTravel = -end.size * actionWidthPx

    // A full swipe commits without waiting for a tap, and it is the **outermost**
    // action that runs — the one at the edge the row is sliding onto. Opting in is
    // per side: a side where nothing opted in has no full swipe at all.
    val fullStart = start.firstOrNull()?.takeIf { start.any { a -> a.isFullSwipeAction } }
    val fullEnd = end.firstOrNull()?.takeIf { end.any { a -> a.isFullSwipeAction } }

    LaunchedEffect(width, start.size, end.size, fullStart, fullEnd) {
        if (width <= 0f) return@LaunchedEffect
        state.anchoredState.updateAnchors(
            DraggableAnchors {
                SwipeValue.Resting at 0f
                if (start.isNotEmpty()) SwipeValue.Start at startTravel
                if (end.isNotEmpty()) SwipeValue.End at endTravel
                // Off the far edge, so the row can be carried all the way there.
                if (fullStart != null) SwipeValue.StartCommitted at width
                if (fullEnd != null) SwipeValue.EndCommitted at -width
            }
        )
        state.deliverPending()
    }

    val onFull by rememberUpdatedState { action: SwipeAction ->
        action.onAction()
    }

    /**
     * A full swipe, from the moment it is decided until the row is home again.
     *
     * The outermost action holds the whole strip and the others stay folded away
     * for all of it — the travel out, the tick and the return. It used to follow
     * `committing` alone, which letting go clears, so the inner actions opened back
     * up while the row was on its way to the edge.
     */
    var fullSwipe by remember { mutableStateOf(false) }

    /** How far the outermost action's icon has turned into a tick, 0 to 1. */
    val confirmation = remember { Animatable(0f) }
    val confirm by rememberUpdatedState(fullSwipeConfirmation)
    val reduceMotion by rememberUpdatedState(motion.reduceMotion)

    /**
     * The icon turning into a tick, from wherever it has got to.
     *
     * Cut back to the icon if it is interrupted — a finger taking the row again
     * on its way to the edge has undone the swipe, and a half-drawn tick on a row
     * that is not going anywhere is a promise nothing keeps.
     */
    suspend fun drawConfirmation() {
        if (reduceMotion) {
            confirmation.snapTo(1f)
            return
        }
        try {
            confirmation.animateTo(1f, tween(SwipeConfirmDrawMillis))
        } catch (e: CancellationException) {
            withContext(NonCancellable) { confirmation.snapTo(0f) }
            throw e
        }
    }

    /** The drawing a committed release set off, for the settle to wait on. */
    val confirmDraw = remember { ConfirmDraw() }

    // Fires on *settling*, not mid-drag, so nothing runs while the finger is still
    // down and could still take it back.
    //
    // **The tick is drawn before the action runs**, not after: an action that
    // removes the row — the usual one on a full swipe — would take the row and its
    // tick with it. So the moment the row arrives at the far edge is spent showing
    // what is about to happen, and then it happens.
    //
    // **But it is not started here.** It used to be, and the tick then waited on
    // the spring's last fraction of a pixel: `settledValue` changes only once the
    // row is within a hair of the edge, a third of a second after letting go,
    // when the eye had seen it arrive at about half that. Reported as too long
    // between the swipe finishing and the tick starting. So the release starts
    // the drawing — see `settle` — and the icon leaves while the row is still
    // travelling; what waits for the settle is only the *action*.
    val feedback = LocalFeedback.current

    /**
     * A full swipe let go of, done: as the drawn tick completes, or — with the
     * tick off — as the action runs. Only for a release; a row sent to the edge
     * in code has nobody holding it. One call for both, and [releasedToCommit]
     * is what says a release sent it.
     */
    fun confirmed() = feedback.perform(FeedbackIntent.Confirm)
    var releasedToCommit by remember { mutableStateOf(false) }

    LaunchedEffect(state) {
        snapshotFlow { state.anchoredState.settledValue }.collect { settledAt ->
            val action = when (settledAt) {
                SwipeValue.StartCommitted -> fullStart
                SwipeValue.EndCommitted -> fullEnd
                else -> null
            }
            if (action == null) {
                if (settledAt == SwipeValue.Resting) fullSwipe = false
                return@collect
            }
            fullSwipe = true
            if (confirm) {
                // The drawing the release set off, finished; a row sent to the
                // edge in code had no release, so its drawing starts here.
                confirmDraw.job?.join()
                if (confirmation.value < 1f) drawConfirmation()
                // Held on the frame clock, like the drawing before it, so the
                // pause is part of the animation rather than a timer beside it.
                Animatable(0f).animateTo(1f, tween(SwipeConfirmHoldMillis))
            } else if (releasedToCommit) {
                // Nothing drawn to finish on, so the hand hears it as the action
                // runs: a committed swipe with the tick off — a `SwipeToDismiss`
                // set up that way — used to report the threshold and no outcome.
                confirmed()
            }
            releasedToCommit = false
            onFull(action)
            state.reset()
            confirmation.snapTo(0f)
        }
    }

    /**
     * Whether letting go now would run the outermost action.
     *
     * A latch rather than a comparison, with [SwipeCommitHysteresis] between on and
     * off, so a finger resting on the line does not flicker the takeover. Set only
     * by the drag — never by an animation — so a row sent anywhere in code never
     * buzzes and never takes over.
     */
    var committing by remember { mutableStateOf(false) }

    /**
     * One report in the gesture, at the one moment that has a consequence: past
     * here, letting go runs the action. And one more if the finger backs out of it,
     * since that has a consequence too. The ticker is what makes it once each way.
     */
    val pointOfNoReturn = rememberDetentTicker(FeedbackIntent.DragThreshold, back = FeedbackIntent.DragThresholdBack)

    /**
     * A soft tick as each action reaches its full size under the finger: "a soft
     * tick to the swipe actions when each action grows to full size". The actions
     * are dealt out one after the other, and each one arriving is a place the row
     * could be let go with it showing. Growing only — an action shrinking back is
     * the finger's own doing and says nothing new.
     */
    val dealtTicker = rememberDetentTicker()
    val dealt = remember(dealtTicker) { ActionsDealt(dealtTicker) }

    fun commitAt(side: Float): Float {
        val reveal = if (side > 0f) startTravel else -endTravel
        return maxOf(reveal + commitMarginPx, width * SwipeFullShare)
            .coerceAtMost((width - commitInsetPx).coerceAtLeast(reveal))
    }

    fun updateCommitting(offset: Float) {
        val side = if (offset > 0f) fullStart else if (offset < 0f) fullEnd else null
        val next = when {
            side == null || width <= 0f -> false
            committing -> abs(offset) >= commitAt(offset) - hysteresisPx
            else -> abs(offset) >= commitAt(offset)
        }
        committing = next
        pointOfNoReturn.at(if (next) 1 else 0)
    }

    // The row's settle, one at a time: a new drag cancels a settle in flight.
    var settling by remember { mutableStateOf<Job?>(null) }
    val settleSpec: AnimationSpec<Float> = motion.springOrTween(motion.springDefault)
    val commitSpec: AnimationSpec<Float> = motion.springOrTween(motion.springSnappy)

    fun settle(velocity: Float) {
        val from = state.anchoredState.offset
        if (from.isNaN()) return
        val target = swipeTarget(
            offset = from,
            velocity = velocity,
            startedAt = state.anchoredState.settledValue,
            startReveal = if (start.isNotEmpty()) startTravel else Float.NaN,
            endReveal = if (end.isNotEmpty()) endTravel else Float.NaN,
            startCount = start.size,
            endCount = end.size,
            committing = committing,
            flickVelocity = flickVelocityPx,
            flickTravel = flickTravelPx,
            revealShare = revealShare,
        )
        val committed = target == SwipeValue.StartCommitted || target == SwipeValue.EndCommitted
        if (committed) fullSwipe = true
        committing = false
        pointOfNoReturn.reset()
        settling?.cancel()
        settling = scope.launch {
            val anchors = state.anchoredState.anchors
            val to = anchors.positionOf(target)
            if (to.isNaN()) return@launch
            // Out to the edge on the quicker spring: a committed row has somewhere
            // to be, and the soft one spent its last few pixels arriving.
            val spec = if (committed) commitSpec else settleSpec
            // The tick sets off with the row rather than after it: the icon
            // leaves while the row travels and the stroke starts as it arrives.
            // A child of the settle, so a finger taking the row back cancels it.
            //
            // And the hand gets the same news the eye does, as the tick
            // completes: the action is done. Only here, on a release — a row
            // sent to the edge in code has nobody holding it to feel anything.
            if (committed && confirm) {
                confirmDraw.job = launch {
                    drawConfirmation()
                    confirmed()
                }
            }
            if (committed && !confirm) releasedToCommit = true
            state.anchoredState.anchoredDrag(target) { _, _ ->
                animate(from, to, velocity, spec) { value, speed -> dragTo(value, speed) }
            }
        }
    }

    // The takeover: the outermost button widening to the whole strip past the
    // point of no return. Its own spring, so crossing the line is a motion rather
    // than a cut.
    val takeover = remember { Animatable(0f) }
    LaunchedEffect(committing, fullSwipe) {
        val target = if (committing || fullSwipe) 1f else 0f
        if (motion.reduceMotion) takeover.snapTo(target)
        else takeover.animateTo(target, motion.springOrTween(motion.springSnappy))
    }

    // Which side is showing, read as a derived value so the row recomposes when the
    // side changes and not on every pixel of the swipe.
    val side by remember(state) {
        derivedStateOf {
            val live = state.anchoredState.offset
            if (live.isNaN() || live == 0f) 0 else if (live > 0f) 1 else -1
        }
    }

    val swipeable = enabled && (start.isNotEmpty() || end.isNotEmpty())

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { width = it.width.toFloat() }
            .semantics {
                // The only route to these that does not require knowing the
                // gesture exists.
                customActions = (start + end).map { action ->
                    CustomAccessibilityAction(action.label) {
                        action.onAction()
                        true
                    }
                }
            }
    ) {
        // From the edge the row is leaving, inward.
        val fromEdge = when (side) {
            1 -> start
            -1 -> end
            else -> emptyList()
        }
        if (fromEdge.isNotEmpty()) {
            SwipeActionButtons(
                modifier = Modifier.matchParentSize(),
                actions = fromEdge,
                fromLeadingEdge = side > 0,
                shape = shape,
                gap = gapPx,
                reveal = { if (side > 0) startTravel else -endTravel },
                offset = { state.anchoredState.offset },
                takeover = { takeover.value },
                confirmation = { confirmation.value },
                reduceMotion = motion.reduceMotion,
                onClick = { action ->
                    action.onAction()
                    scope.launch { state.reset() }
                },
            )
        }

        Box(
            Modifier
                .offset {
                    val live = state.anchoredState.offset
                    IntOffset(if (live.isNaN()) 0 else live.roundToInt(), 0)
                }
                // The row itself carries the shape, so the page shows between it
                // and the buttons rather than the buttons sitting in a strip cut
                // out of one clipped box.
                .clip(shape)
                // **A tap on a row showing its actions puts it back**, rather than
                // opening whatever the row opens. The row is half off the screen;
                // what a tap on it asks for is the row, not the thing it links to.
                // Watched on the way in, so the content's own click never sees
                // the release — and given up on as soon as the finger travels, so
                // a drag still goes to the drag below.
                //
                // Always installed, and asking whether the row is open when the
                // finger lands. Installed only while open, it was a new pointer
                // node in front of the drag's the moment a swipe first moved the
                // row, and Compose matched it to the drag's own node and restarted
                // that one mid-gesture: no swipe got past its first pixel.
                .pointerInput(state, scope) {
                    val slop = viewConfiguration.touchSlop
                    awaitEachGesture {
                        val down = awaitFirstDown(
                            requireUnconsumed = false,
                            pass = PointerEventPass.Initial,
                        )
                        val live = state.anchoredState.offset
                        if (live.isNaN() || live == 0f) return@awaitEachGesture
                        var travelled = Offset.Zero
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                change.consume()
                                scope.launch { state.reset() }
                                break
                            }
                            travelled += change.position - change.previousPosition
                            if (travelled.getDistance() > slop) break
                        }
                    }
                }
                .horizontalDragOwning(
                    enabled = swipeable,
                    interactionSource = null,
                    scope = scope,
                    claimsOn = DragClaim.Direction,
                    onStart = {
                        settling?.cancel()
                        pointOfNoReturn.reset()
                        pointOfNoReturn.at(0)
                        // What is already showing when the finger lands is not
                        // news: a row that was open starts with its actions dealt.
                        dealt.arm(state.anchoredState.offset, actionWidthPx, start.size, end.size, hysteresisPx)
                    },
                    onDelta = { delta ->
                        // Physical to logical, once, here.
                        state.anchoredState.dispatchRawDelta(if (isRtl) -delta else delta)
                        updateCommitting(state.anchoredState.offset)
                        dealt.at(state.anchoredState.offset)
                    },
                    onRelease = { velocity -> settle(if (isRtl) -velocity else velocity) },
                    onEnd = {
                        dealt.reset()
                        // A cancelled gesture has no release; land it anyway.
                        if (settling?.isActive != true) settle(0f)
                    },
                )
                /**
                 * A sideways scroll is a swipe.
                 *
                 * On a desktop there is no finger to drag the row with, and a
                 * trackpad's two-finger sideways push is the gesture that means
                 * exactly this everywhere else on the platform. It never commits: a
                 * scroll has no point of no return to feel.
                 *
                 * **It was far too hard to move**, reported as wanting it "more
                 * sensitive to side scrolling" on a trackpad and a mouse, and for
                 * two reasons. A trackpad on a Mac no longer arrives as a scroll at
                 * all: Compose hands its two fingers over as a *pan*, in pixels that
                 * follow the fingers, and nothing here listened for one. And a
                 * scroll's delta is in notches, not pixels — a mouse's click is 1 —
                 * so three pixels a notch moved an 88dp action three pixels. Now a
                 * pan moves the row with the fingers, the way a finger's drag does,
                 * and a notch moves it [SwipeActionsDefaults.ScrollStep].
                 *
                 * **Sideways, or not at all.** Each gesture — a pan from its start,
                 * a burst of scroll from the first event after a quiet spell — is
                 * the row's if it begins mostly sideways, and the list's otherwise,
                 * for the whole of it. A vertical scroll with a little drift in it
                 * used to move the row and swallow the scroll.
                 *
                 * A pan ends with an event of its own and settles then, flicked by
                 * how it was moving; a scroll has no end, and settles once it has
                 * been quiet for [SwipeActionsDefaults.SettleAfterScroll].
                 */
                .then(
                    if (swipeable) {
                        // Keyed on the travel too: the settle and the dealing both
                        // read the actions, and a row given new ones starts afresh.
                        Modifier.pointerInput(state, scope, isRtl, startTravel, endTravel, actionWidthPx) {
                            val stepPx = SwipeActionsDefaults.ScrollStep.toPx()
                            val decidePx = viewConfiguration.touchSlop * SideScrollDecisionShare
                            var waiting: Job? = null
                            // The gesture's direction: undecided, the row's, or the list's.
                            var ours: Boolean? = null
                            var gathered = Offset.Zero
                            var lastScroll = 0L
                            var panned = 0f
                            val panVelocity = VelocityTracker1D(isDataDifferential = false)

                            fun move(physical: Float) {
                                settling?.cancel()
                                waiting?.cancel()
                                state.anchoredState.dispatchRawDelta(if (isRtl) -physical else physical)
                                dealt.at(state.anchoredState.offset)
                            }

                            fun decide(delta: Offset, threshold: Float): Boolean? {
                                if (ours == null) {
                                    gathered += delta
                                    if (gathered.getDistance() >= threshold) ours = abs(gathered.x) > abs(gathered.y)
                                }
                                return ours
                            }

                            fun begin() {
                                ours = null
                                gathered = Offset.Zero
                                dealt.arm(state.anchoredState.offset, actionWidthPx, start.size, end.size, hysteresisPx)
                            }

                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val changes = event.changes
                                    when (event.type) {
                                        PointerEventType.PanStart -> {
                                            begin()
                                            panned = 0f
                                            panVelocity.resetTracking()
                                        }
                                        PointerEventType.PanMove -> {
                                            val pan = changes.fold(Offset.Zero) { sum, it -> sum + it.panOffset }
                                            if (decide(pan, decidePx) != true) continue
                                            changes.forEach { it.consume() }
                                            // Pixels that follow the fingers: the row
                                            // goes with them, as it does with one.
                                            move(pan.x)
                                            panned += pan.x
                                            panVelocity.addDataPoint(changes.first().uptimeMillis, panned)
                                        }
                                        PointerEventType.PanEnd -> {
                                            if (ours == true) {
                                                changes.forEach { it.consume() }
                                                committing = false
                                                val velocity = panVelocity.calculateVelocity()
                                                settle(if (isRtl) -velocity else velocity)
                                            }
                                            dealt.reset()
                                            ours = null
                                        }
                                        PointerEventType.Scroll -> {
                                            val delta = changes.fold(Offset.Zero) { sum, it -> sum + it.scrollDelta }
                                            // Says nothing about direction, so decides nothing.
                                            if (delta == Offset.Zero) continue
                                            val now = changes.first().uptimeMillis
                                            if (now - lastScroll > SideScrollQuietMillis) begin()
                                            lastScroll = now
                                            // A notch is a whole decision on its own.
                                            if (decide(delta, 0f) != true || delta.x == 0f) continue
                                            changes.forEach { it.consume() }
                                            move(-delta.x * stepPx)
                                            waiting = scope.launch {
                                                delay(SwipeActionsDefaults.SettleAfterScroll)
                                                committing = false
                                                dealt.reset()
                                                settle(0f)
                                            }
                                        }
                                        else -> Unit
                                    }
                                }
                            }
                        }
                    } else {
                        Modifier
                    }
                )
        ) {
            content()
        }
    }
}

/**
 * Where a released swipe goes. Pure, so every rule in [SwipeActions]'s "How it
 * decides" is a line here and a case in `SwipeTargetTest`.
 *
 * @param offset The row's logical offset at release: positive toward the trailing
 *   edge.
 * @param velocity Logical, pixels a second.
 * @param startedAt Where the row rested when the gesture began.
 * @param startReveal Where the start actions are revealed, or `NaN` for none.
 * @param endReveal Where the end actions are revealed (negative), or `NaN`.
 * @param startCount How many actions the start side has.
 * @param endCount How many actions the end side has.
 * @param committing Whether the drag was past the point of no return when it ended.
 */
internal fun swipeTarget(
    offset: Float,
    velocity: Float,
    startedAt: SwipeValue,
    startReveal: Float,
    endReveal: Float,
    startCount: Int,
    endCount: Int,
    committing: Boolean,
    flickVelocity: Float,
    flickTravel: Float,
    revealShare: Float,
): SwipeValue {
    if (offset == 0f) return SwipeValue.Resting
    val onStart = offset > 0f
    if (committing) return if (onStart) SwipeValue.StartCommitted else SwipeValue.EndCommitted
    val reveal = if (onStart) startReveal else endReveal
    if (reveal.isNaN() || reveal == 0f) return SwipeValue.Resting
    val revealed = if (onStart) SwipeValue.Start else SwipeValue.End
    val span = abs(reveal)
    val wasOpen = startedAt == revealed
    val from = if (wasOpen) span else 0f
    val travelled = abs(abs(offset) - from)

    // A flick: by the way it was thrown, outward to open and inward to close.
    if (abs(velocity) >= flickVelocity && travelled >= flickTravel) {
        val outward = (velocity > 0f) == onStart
        return if (outward) revealed else SwipeValue.Resting
    }
    // Carried past the actions but short of the point of no return: open.
    if (abs(offset) >= span) return revealed
    return if (wasOpen) {
        if (abs(offset) <= span * (1f - revealShare)) SwipeValue.Resting else revealed
    } else {
        // With several actions, once the first is out in full: they are dealt out
        // one after the other, and a release at 35% of three used to open all of
        // them with the first still growing — and with two, before it had
        // finished. "The threshold for making them all appear when you let go is
        // once the first one has appeared." One action keeps the positional share.
        val count = if (onStart) startCount else endCount
        val opensAt = if (count >= 2) span / count else span * revealShare
        if (abs(offset) >= opensAt) revealed else SwipeValue.Resting
    }
}

/**
 * The actions behind a swiped row, as separate squircles growing out of the edge
 * the row is leaving.
 *
 * Laid out every frame from the live offset, in the layout phase, so a swipe costs
 * a measure and never a recomposition.
 *
 * **One after the other.** The outermost grows first, at the edge the row uncovers
 * first; the next starts beside it once it has its full width, and so on toward the
 * row — so a swipe reads as the actions being dealt out rather than all swelling at
 * once. Each is no taller than it is wide, so each begins as a small circle and
 * lengthens into a full-height button. Past the reveal they share the extra width
 * evenly, and [takeover] then widens the outermost over the whole strip and folds
 * the others away.
 */
@Composable
private fun SwipeActionButtons(
    modifier: Modifier,
    actions: List<SwipeAction>,
    fromLeadingEdge: Boolean,
    shape: Shape,
    gap: Float,
    reveal: () -> Float,
    offset: () -> Float,
    takeover: () -> Float,
    confirmation: () -> Float,
    reduceMotion: Boolean,
    onClick: (SwipeAction) -> Unit,
) {
    val count = actions.size
    Layout(
        modifier = modifier,
        content = {
            actions.forEachIndexed { edgeIndex, action ->
                SwipeActionButton(
                    action = action,
                    shape = shape,
                    arrival = {
                        val live = abs(offset().let { if (it.isNaN()) 0f else it })
                        val full = fullButtonWidth(reveal(), count, gap)
                        if (full <= 0f) {
                            1f
                        } else {
                            (grownWidth(edgeIndex, live, count, reveal(), gap) / full).coerceIn(0f, 1f)
                        }
                    },
                    // Only the outermost is ever the one a full swipe runs.
                    confirmation = if (edgeIndex == 0) confirmation else NoConfirmation,
                    reduceMotion = reduceMotion,
                    onClick = { onClick(action) },
                )
            }
        },
    ) { measurables, constraints ->
        val boxWidth = constraints.maxWidth
        val rowHeight = constraints.maxHeight
        val live = offset().let { if (it.isNaN()) 0f else abs(it) }.coerceAtMost(boxWidth.toFloat())
        val t = takeover().coerceIn(0f, 1f)
        val usable = (live - gap).coerceAtLeast(0f)
        val span = reveal()

        val widths = FloatArray(count) { i ->
            val grown = grownWidth(i, live, count, span, gap)
            if (i == 0) grown + (usable - grown) * t else grown * (1f - t)
        }
        val placeables = measurables.mapIndexed { i, measurable ->
            val w = widths[i].roundToInt().coerceAtLeast(0)
            val h = minOf(rowHeight.toFloat(), widths[i]).roundToInt().coerceAtLeast(0)
            measurable.measure(Constraints.fixed(w, h))
        }
        layout(boxWidth, rowHeight) {
            // From the edge inward, each after the last with a gap between — a gap
            // only once there is a button to have one, and folding away with the
            // buttons during a takeover.
            var along = gap / 2f
            placeables.forEachIndexed { i, placeable ->
                val y = (rowHeight - placeable.height) / 2
                val x = if (fromLeadingEdge) along else boxWidth - along - placeable.width
                placeable.placeRelative(x.roundToInt(), y)
                if (widths[i] > 0f) along += widths[i] + if (i == 0) gap else gap * (1f - t)
            }
        }
    }
}

/**
 * How many actions a drag has brought to their full size, reported once each as
 * it arrives: button `i` from the edge is full once the row has travelled
 * `i + 1` action widths — see [grownWidth] — so the count is whole widths
 * uncovered, up to the side's number of actions.
 *
 * Latched with [release] of slack on the way back, as an end stop is, so a
 * finger resting on the moment one fills does not tick it again and again; and
 * the index handed to the ticker only climbs, so shrinking says nothing.
 */
private class ActionsDealt(private val ticker: DetentTicker) {
    private var each = 0f
    private var startCount = 0
    private var endCount = 0
    private var release = 0f
    private var side = 0
    private var dealt = 0
    private var hits = 0

    fun arm(offset: Float, each: Float, startCount: Int, endCount: Int, release: Float) {
        this.each = each
        this.startCount = startCount
        this.endCount = endCount
        this.release = release
        ticker.reset()
        hits = 0
        ticker.at(hits)
        side = sideOf(offset)
        dealt = reached(offset)
    }

    fun at(offset: Float) {
        if (each <= 0f || offset.isNaN()) return
        val now = sideOf(offset)
        // Across the middle to the other side's actions: none of those is dealt.
        if (now != side) {
            side = now
            dealt = 0
        }
        val reached = reached(offset)
        when {
            reached > dealt -> {
                dealt = reached
                hits++
                ticker.at(hits)
            }
            reached < dealt && abs(offset) <= dealt * each - release -> dealt = reached
        }
    }

    fun reset() {
        ticker.reset()
        each = 0f
        dealt = 0
        hits = 0
    }

    private fun sideOf(offset: Float) = if (offset > 0f) 1 else if (offset < 0f) -1 else 0

    /** Whole action widths uncovered, give or take half a pixel, up to the side's actions. */
    private fun reached(offset: Float): Int {
        if (each <= 0f || offset.isNaN()) return 0
        val count = if (offset > 0f) startCount else if (offset < 0f) endCount else 0
        return ((abs(offset) + 0.5f) / each).toInt().coerceIn(0, count)
    }
}

/** A button's width once it has all of it, at the full reveal. */
private fun fullButtonWidth(reveal: Float, count: Int, gap: Float): Float =
    if (count <= 0) 0f else (reveal / count - gap).coerceAtLeast(0f)

/**
 * Button [index]'s width, counted from the edge, with [live] of the strip uncovered:
 * dealt out one after the other up to the reveal, and sharing the strip evenly past
 * it. The two agree at the reveal, where every button is exactly full.
 */
private fun grownWidth(index: Int, live: Float, count: Int, reveal: Float, gap: Float): Float {
    val full = fullButtonWidth(reveal, count, gap)
    if (live >= reveal) return (live / count - gap).coerceAtLeast(0f)
    return (live - index * (full + gap) - gap).coerceIn(0f, full)
}

private val NoConfirmation: () -> Float = { 0f }

@Composable
private fun SwipeActionButton(
    action: SwipeAction,
    shape: Shape,
    /** How far into its entrance the icon and label are, 0 to 1, read in the layer. */
    arrival: () -> Float,
    /** How far the icon has turned into a tick, 0 to 1, read in draw. */
    confirmation: () -> Float,
    reduceMotion: Boolean,
    onClick: () -> Unit,
) {
    val content = contentColourFor(action.background)
    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = shape,
        containerColour = action.background,
        contentColour = content,
        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints(
            // `fillMaxSize`, so the whole button is the target and not only the
            // icon and label in the middle of it.
            modifier = Modifier
                .fillMaxSize()
                .clickableAction(onClick, action.label)
                .graphicsLayer {
                    val shown = arrival()
                    alpha = shown
                    if (!reduceMotion) {
                        val scale = SwipeContentFrom + (1f - SwipeContentFrom) * shown
                        scaleX = scale
                        scaleY = scale
                    }
                }
                .padding(Theme.spacing.xxs),
            contentAlignment = Alignment.Center,
        ) {
            // The label goes when there is no room for it, rather than being
            // clipped to a stripe of its own ascenders; measured rather than
            // compared against a magic dp, so it stays right at 200% type.
            val labelHeight = with(LocalDensity.current) {
                Theme.typography.labelSmall.lineHeight.toDp()
            }
            val roomForLabel = maxHeight >= Theme.sizing.iconLarge + Theme.spacing.xxs + labelHeight &&
                maxWidth >= Theme.sizing.iconLarge * 2
            val roomForIcon = maxWidth >= Theme.sizing.iconMedium && maxHeight >= Theme.sizing.iconMedium
            // **Arriving, not appearing.** Both came in on the frame there was
            // room for them, which on a swipe is a pop partway through a smooth
            // motion — reported as "a little bit janky when the text first
            // appears". They fade and grow in as the room opens instead, and out
            // the same way. A fade alone under reduced motion.
            AnimatedVisibility(
                visible = roomForIcon,
                enter = if (reduceMotion) fadeIn() else fadeIn() + scaleIn(initialScale = SwipeContentFrom),
                exit = if (reduceMotion) fadeOut() else fadeOut() + scaleOut(targetScale = SwipeContentFrom),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Theme.spacing.xxs),
                ) {
                    ConfirmingIcon(action.icon, content, confirmation, reduceMotion)
                    // Dropping it costs nothing a screen reader can tell: the label
                    // still reaches `CustomAccessibilityAction` on the row and
                    // `onClickLabel` on this button.
                    AnimatedVisibility(
                        visible = roomForLabel,
                        enter = if (reduceMotion) fadeIn() else fadeIn() + expandVertically(),
                        exit = if (reduceMotion) fadeOut() else fadeOut() + shrinkVertically(),
                    ) {
                        Text(
                            text = action.label,
                            style = Theme.typography.labelSmall,
                            colour = content,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The action's icon, turning into a tick as [confirmation] runs from 0 to 1: the
 * icon shrinks and fades over the first part, and the tick is drawn stroke by
 * stroke over the rest.
 */
@Composable
private fun ConfirmingIcon(
    icon: ImageVector,
    tint: Color,
    confirmation: () -> Float,
    reduceMotion: Boolean,
) {
    val stroke = with(LocalDensity.current) { SwipeTickStroke.toPx() }
    Box(
        modifier = Modifier
            .size(Theme.sizing.iconLarge)
            .drawWithCache {
                val w = size.width
                val h = size.height
                val tick = Path().apply {
                    moveTo(w * TickStartX, h * TickStartY)
                    lineTo(w * TickTurnX, h * TickTurnY)
                    lineTo(w * TickEndX, h * TickEndY)
                }
                val measure = PathMeasure().apply { setPath(tick, false) }
                val length = measure.length
                val drawn = Path()
                val style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
                onDrawWithContent {
                    val c = confirmation()
                    if (c < 1f) drawContent()
                    val p = ((c - TickFrom) / (1f - TickFrom)).coerceIn(0f, 1f)
                    if (p > 0f) {
                        drawn.rewind()
                        measure.getSegment(0f, length * p, drawn, true)
                        drawPath(drawn, tint, style = style)
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            size = Theme.sizing.iconLarge,
            tint = tint,
            modifier = Modifier.graphicsLayer {
                val gone = (confirmation() / IconOutBy).coerceIn(0f, 1f)
                alpha = 1f - gone
                if (!reduceMotion) {
                    val scale = 1f - (1f - SwipeContentFrom) * gone
                    scaleX = scale
                    scaleY = scale
                }
            },
        )
    }
}

/**
 * Clickable without a role, because the row above already exposes this as a
 * custom accessibility action — announcing it twice makes one action sound like
 * two.
 */
@Composable
private fun Modifier.clickableAction(onClick: () -> Unit, label: String): Modifier =
    pointerCursor().clickable(onClickLabel = label, onClick = onClick)

/**
 * How fast a release has to be before it counts as a flick, per second. The number
 * `BottomSheet` measured for the same question, a little lower: a row is lighter
 * than a sheet, and "too fiddly" was a flick that did not register.
 */
private val SwipeFlickVelocity: Dp = 400.dp

/** How far a flick has to have travelled for its direction to mean anything. */
private val SwipeFlickTravel: Dp = 12.dp

/**
 * The point of no return is at least this far past the actions, so a row carried
 * just past them has somewhere to be before letting go deletes something.
 */
private val SwipeCommitMargin: Dp = 48.dp

/** And never closer to the far edge than this, so it can always be reached. */
private val SwipeCommitInset: Dp = 24.dp

/** How far back from the point of no return a finger has to come to undo it. */
private val SwipeCommitHysteresis: Dp = 16.dp

/** The point of no return is at least this share of the row. */
private const val SwipeFullShare: Float = 0.55f

/** The size a button's icon and label grow from. */
private const val SwipeContentFrom: Float = 0.6f

/**
 * How long the tick takes to turn up and be drawn, from the moment of letting go.
 *
 * It runs alongside the row's travel to the edge, so its first part — the icon
 * leaving — is spent while the row is still moving, and the stroke begins about
 * as the row arrives.
 */
private const val SwipeConfirmDrawMillis: Int = 300

/** How long the finished tick is held before the action runs and the row returns. */
private const val SwipeConfirmHoldMillis: Int = 320

/** The tick's stroke. */
private val SwipeTickStroke: Dp = 2.5.dp

/** The share of the confirmation the icon takes to leave, and the tick waits for. */
private const val IconOutBy: Float = 0.35f
private const val TickFrom: Float = 0.3f

// The tick, in the icon's own box: down to the turn, then up to the end.
private const val TickStartX: Float = 0.2f
private const val TickStartY: Float = 0.52f
private const val TickTurnX: Float = 0.42f
private const val TickTurnY: Float = 0.72f
private const val TickEndX: Float = 0.8f
private const val TickEndY: Float = 0.3f

/**
 * A row that can be swiped away entirely.
 *
 * ```kotlin
 * SwipeToDismiss(
 *     onDismissRequest = { viewModel.remove(favourite) },
 *     label = "Remove favourite",
 *     icon = Tabler.Outline.Trash,
 * ) {
 *     ListItem { +favourite.name }
 * }
 * ```
 *
 * A [SwipeActions] with one destructive action that fires on a full swipe. The
 * separate name is worth it because the two mean different things to the user:
 * swipe-to-reveal is a menu, swipe-to-dismiss is a commitment.
 *
 * Give the user a way back. A dismissal with no undo is a data-loss bug wearing
 * a gesture — pair it with a
 * [io.kontour.ui.overlay.Toast] carrying an undo action.
 *
 * @param fullSwipeConfirmation Whether the row shows a tick at the far edge before
 *   it is dismissed, as [SwipeActions] does. Off dismisses it the moment it gets
 *   there.
 */
@Composable
fun SwipeToDismiss(
    onDismissRequest: () -> Unit,
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    background: Color = Theme.colours.danger.solid,
    state: SwipeActionsState = rememberSwipeActionsState(),
    shape: Shape = Theme.shapes.container,
    fullSwipeConfirmation: Boolean = true,
    content: @Composable () -> Unit,
) {
    SwipeActions(
        modifier = modifier,
        end = listOf(
            SwipeAction(
                label = label,
                icon = icon,
                onAction = onDismissRequest,
                background = background,
                isFullSwipeAction = true,
            )
        ),
        state = state,
        enabled = enabled,
        shape = shape,
        fullSwipeConfirmation = fullSwipeConfirmation,
        content = content,
    )
}

/** The drawing a committed release started. See `confirmDraw` in [SwipeActions]. */
private class ConfirmDraw {
    var job: Job? = null
}

/**
 * How much of the touch slop a trackpad's pan gathers before the row decides
 * whether it is sideways — the share a finger's drag uses, for the same reason.
 */
private const val SideScrollDecisionShare = 0.4f

/** A scroll quiet for this long has ended, and the next is a new gesture to decide. */
private const val SideScrollQuietMillis = 250L
