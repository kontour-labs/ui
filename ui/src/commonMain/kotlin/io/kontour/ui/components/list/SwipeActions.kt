package io.kontour.ui.components.list

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animate
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
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
import io.kontour.ui.interaction.DragClaim
import io.kontour.ui.interaction.FeedbackIntent
import io.kontour.ui.interaction.horizontalDragOwning
import io.kontour.ui.interaction.rememberDetentTicker
import io.kontour.ui.theme.Theme
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
     * How many pixels of row one pixel of sideways scroll moves.
     *
     * More than one: a trackpad reports fine deltas and a swipe is a coarse
     * gesture, so at parity crossing a 176dp action strip is a long push. Three
     * makes a flick of the fingers reach the actions and a deliberate push stop
     * anywhere in between.
     */
    const val ScrollStep: Float = 3f

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
 * leaving, one per action, each in its own colour, with page showing between them
 * — small and round at the first pixel of the swipe, full-height buttons by the
 * time the row has uncovered them. Carry the row on past its actions and, at the
 * point of no return, the outermost one takes over the whole strip while the
 * others fold into it: letting go then runs it. Asked for in those words — "an
 * Apple-like animation where (up to) three squircle shapes expand out of the side
 * when you start swiping, and then continuing to swipe across will select the
 * rightmost one".
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
 * - **Short of it, a flick opens or closes** the actions by the way it was thrown,
 *   and a slow release does so a third of the way in. A flick never commits on its
 *   own: aimed at a reveal and thrown a little hard, it used to run the action —
 *   the Android half of the report.
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

    // Fires on *settling*, not mid-drag, so nothing runs while the finger is still
    // down and could still take it back.
    LaunchedEffect(state) {
        snapshotFlow { state.anchoredState.settledValue }.collect { settledAt ->
            val action = when (settledAt) {
                SwipeValue.StartCommitted -> fullStart
                SwipeValue.EndCommitted -> fullEnd
                else -> null
            } ?: return@collect
            onFull(action)
            state.reset()
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
    val pointOfNoReturn = rememberDetentTicker(FeedbackIntent.DragThreshold)

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

    fun settle(velocity: Float) {
        val from = state.anchoredState.offset
        if (from.isNaN()) return
        val target = swipeTarget(
            offset = from,
            velocity = velocity,
            startedAt = state.anchoredState.settledValue,
            startReveal = if (start.isNotEmpty()) startTravel else Float.NaN,
            endReveal = if (end.isNotEmpty()) endTravel else Float.NaN,
            committing = committing,
            flickVelocity = flickVelocityPx,
            flickTravel = flickTravelPx,
            revealShare = revealShare,
        )
        committing = false
        pointOfNoReturn.reset()
        settling?.cancel()
        settling = scope.launch {
            val anchors = state.anchoredState.anchors
            val to = anchors.positionOf(target)
            if (to.isNaN()) return@launch
            state.anchoredState.anchoredDrag(target) { _, _ ->
                animate(from, to, velocity, settleSpec) { value, speed -> dragTo(value, speed) }
            }
        }
    }

    // The takeover: the outermost button widening to the whole strip past the
    // point of no return. Its own spring, so crossing the line is a motion rather
    // than a cut.
    val takeover = remember { Animatable(0f) }
    LaunchedEffect(committing) {
        val target = if (committing) 1f else 0f
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
                    },
                    onDelta = { delta ->
                        // Physical to logical, once, here.
                        state.anchoredState.dispatchRawDelta(if (isRtl) -delta else delta)
                        updateCommitting(state.anchoredState.offset)
                    },
                    onRelease = { velocity -> settle(if (isRtl) -velocity else velocity) },
                    onEnd = {
                        // A cancelled gesture has no release; land it anyway.
                        if (settling?.isActive != true) settle(0f)
                    },
                )
                /**
                 * A sideways scroll is a swipe.
                 *
                 * On a desktop there is no finger to drag the row with, and a
                 * trackpad's two-finger sideways push is the gesture that means
                 * exactly this everywhere else on the platform. No end event exists
                 * for a scroll, so the row settles on a short timer after the last
                 * one. It never commits: a scroll has no point of no return to feel.
                 */
                .then(
                    if (swipeable) {
                        Modifier.pointerInput(state, scope) {
                            var waiting: Job? = null
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    if (event.type != PointerEventType.Scroll) continue
                                    val sideways = event.changes.sumOf {
                                        it.scrollDelta.x.toDouble()
                                    }.toFloat()
                                    if (sideways == 0f) continue
                                    event.changes.forEach { it.consume() }
                                    settling?.cancel()
                                    state.anchoredState.dispatchRawDelta(
                                        -sideways * SwipeActionsDefaults.ScrollStep
                                    )
                                    waiting?.cancel()
                                    waiting = scope.launch {
                                        delay(SwipeActionsDefaults.SettleAfterScroll)
                                        committing = false
                                        settle(0f)
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
 * @param committing Whether the drag was past the point of no return when it ended.
 */
internal fun swipeTarget(
    offset: Float,
    velocity: Float,
    startedAt: SwipeValue,
    startReveal: Float,
    endReveal: Float,
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
        if (abs(offset) >= span * revealShare) revealed else SwipeValue.Resting
    }
}

/**
 * The actions behind a swiped row, as separate squircles growing out of the edge
 * the row is leaving.
 *
 * Laid out every frame from the live offset, in the layout phase, so a swipe costs
 * a measure and never a recomposition. With `W` the strip the row has vacated and
 * `n` actions, each has a slot `W / n` wide and a button in it a gap narrower and
 * no taller than it is wide — so the first pixels of a swipe are small circles, and
 * they lengthen into full-height buttons as the row uncovers them. The shape is the
 * row's own, which a squircle clamps to its size on the way.
 *
 * [takeover] widens the outermost button over the whole strip and folds the
 * others away, past the point of no return.
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
    reduceMotion: Boolean,
    onClick: (SwipeAction) -> Unit,
) {
    val count = actions.size
    Layout(
        modifier = modifier,
        content = {
            actions.forEachIndexed { edgeIndex, action ->
                // Counted from the row outward, so the one the row uncovers first
                // arrives first.
                val fromRow = count - 1 - edgeIndex
                SwipeActionButton(
                    action = action,
                    shape = shape,
                    arrival = {
                        val w = abs(offset().let { if (it.isNaN()) 0f else it })
                        val r = reveal()
                        if (r <= 0f) 1f else staggered(w / r, fromRow, count)
                    },
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
        val slot = live / count
        val natural = (slot - gap).coerceAtLeast(0f)
        val usable = (live - gap).coerceAtLeast(0f)

        val widths = FloatArray(count) { i ->
            if (i == 0) natural + (usable - natural) * t else natural * (1f - t)
        }
        val placeables = measurables.mapIndexed { i, measurable ->
            val w = widths[i].roundToInt().coerceAtLeast(0)
            val h = minOf(rowHeight.toFloat(), widths[i]).roundToInt().coerceAtLeast(0)
            measurable.measure(Constraints.fixed(w, h))
        }
        layout(boxWidth, rowHeight) {
            // From the edge inward, each after the last with a gap between, the
            // gaps folding away with the buttons during a takeover.
            var along = gap / 2f
            placeables.forEachIndexed { i, placeable ->
                val y = (rowHeight - placeable.height) / 2
                val x = if (fromLeadingEdge) along else boxWidth - along - placeable.width
                placeable.placeRelative(x.roundToInt(), y)
                along += widths[i] + if (i == 0) gap else gap * (1f - t)
            }
        }
    }
}

/**
 * How far into its entrance a button is, for a swipe [progress] of the way to its
 * reveal: the one nearest the row first, the others a little behind in turn.
 */
private fun staggered(progress: Float, fromRow: Int, count: Int): Float {
    val lag = SwipeStagger * fromRow
    val room = (1f - SwipeStagger * (count - 1)).coerceAtLeast(0.1f)
    return ((progress - lag) / room).coerceIn(0f, 1f)
}

@Composable
private fun SwipeActionButton(
    action: SwipeAction,
    shape: Shape,
    /** How far into its entrance the icon and label are, 0 to 1, read in the layer. */
    arrival: () -> Float,
    reduceMotion: Boolean,
    onClick: () -> Unit,
) {
    val content = contentColourFor(action.background)
    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = shape,
        colour = action.background,
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
            if (roomForIcon) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Theme.spacing.xxs),
                ) {
                    Icon(
                        imageVector = action.icon,
                        contentDescription = null,
                        size = Theme.sizing.iconLarge,
                        tint = content,
                    )
                    // Dropping it costs nothing a screen reader can tell: the label
                    // still reaches `CustomAccessibilityAction` on the row and
                    // `onClickLabel` on this button.
                    if (roomForLabel) {
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

/** How far behind the one before it each button's entrance runs. */
private const val SwipeStagger: Float = 0.15f

/** The size a button's icon and label grow from. */
private const val SwipeContentFrom: Float = 0.6f

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
        content = content,
    )
}
