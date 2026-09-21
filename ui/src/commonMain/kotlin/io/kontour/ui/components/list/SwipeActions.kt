package io.kontour.ui.components.list

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.TargetedFlingBehavior
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.ui.a11y.contentColourFor
import io.kontour.ui.foundation.Icon
import io.kontour.ui.foundation.Surface
import io.kontour.ui.foundation.Text
import io.kontour.ui.input.pointerCursor
import io.kontour.ui.interaction.FeedbackIntent
import io.kontour.ui.interaction.LocalFeedback
import io.kontour.ui.theme.Theme
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
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
     * Fires when the row is swiped nearly all the way, without waiting for a
     * tap. Only one action per side may be, and it should be the one a full
     * swipe obviously means — delete on a delete row.
     */
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
     * How far between two anchors a release has to be to carry on to the next.
     *
     * 0.55, from 0.4, and it was the first answer to "the swipe is too fiddly". At
     * 0.4 a release two fifths of the way anywhere carried on, so a row revealed its
     * actions on a gesture that was half a mind to. Apple's mail asks for a
     * deliberate distance, and just past half is what deliberate measures.
     *
     * **It applies to the reveal now, and no longer to the commit.** Raising it used
     * to be the only lever there is, because
     * `AnchoredDraggableDefaults.flingBehavior` takes a positional threshold and a
     * snap spec and nothing else — and worse, Foundation's own resolution stops
     * consulting the positional threshold at all above a private 125dp/s velocity
     * floor, which is slower than any swipe anybody makes on purpose. So a row that
     * had passed its actions committed on the next ordinary flick, whatever this
     * number said.
     *
     * `SwipeSettle` is the lever that was missing. This number still decides the
     * reveal; a commit is earned by a firm flick aimed past the row or by carrying it
     * `CommitShare` of the way there.
     */
    val PositionalThreshold: (Float) -> Float
        @Composable @ReadOnlyComposable get() {
            val fraction = Theme.componentDefaults.swipePositionalThreshold
            return { distance -> distance * fraction }
        }

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
 * Built on the same `AnchoredDraggableState` as
 * [io.kontour.ui.sheet.SheetState], so a swipe and a sheet drag behave the same
 * way — same thresholds, same settle, same fling.
 *
 * **Order runs from the screen edge in toward the row**, on both sides. So the
 * *first* action of a list is the one furthest from the row, and the last one is
 * the panel that appears against its edge as the swipe opens. That is the
 * convention swipe rows use everywhere, and the reason is the full swipe:
 * carrying a row all the way runs the first action of the side, and what a full
 * swipe looks like is that action growing from the edge until it has the whole
 * row.
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
    val feedback = LocalFeedback.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    // Read once. Both the fling and the haptic's point of no return use it, and
    // they have to be the same number or the buzz stops marking the commit.
    val threshold = SwipeActionsDefaults.PositionalThreshold

    var width by remember { mutableFloatStateOf(0f) }
    val actionWidthPx = with(density) { actionWidth.toPx() }

    // **The offset is a *logical* one: positive is toward the trailing edge, in
    // both layout directions.**
    //
    // Both ends of it are already mirrored by the framework, which is the part
    // that was missed. `anchoredDraggable` reverses a horizontal drag's deltas
    // under RTL, and `Modifier.offset {}` — `placeRelative` underneath — mirrors
    // the placement it is given. So the value that arrives here has been through
    // two flips and is measured from the leading edge either way.
    //
    // These lines flipped it a third time. The anchors came out mirrored against
    // everything that reads them, so in RTL a swipe toward the trailing edge
    // settled on `SwipeValue.End` while the drawing — correctly reading the
    // logical sign — looked for the `start` actions, found none, and drew
    // nothing at all. A row swiped in Arabic vacated a strip of bare page.
    val startTravel = start.size * actionWidthPx
    val endTravel = -end.size * actionWidthPx

    // A full swipe commits without waiting for a tap, and it is the **outermost**
    // action that runs.
    //
    // This took the first action to *opt in*, which is not the same thing and was
    // reported as not being: *"when swiping/flicking to trigger the action, please
    // make sure it's the outermost one that gets triggered"*. Order runs edge-inward,
    // so a row whose inner action set the flag committed the inner one — against
    // this component's own documented convention, three paragraphs up, that
    // "carrying a row all the way runs the first action of the side".
    //
    // Opting in is still a per-side decision and still the caller's: a side where
    // nothing opted in has no full swipe at all. What is no longer the caller's is
    // *which* action a full swipe runs, because there is only one sensible answer —
    // the one the row is sliding onto.
    val fullStart = start.firstOrNull()?.takeIf { start.any { a -> a.isFullSwipeAction } }
    val fullEnd = end.firstOrNull()?.takeIf { end.any { a -> a.isFullSwipeAction } }

    LaunchedEffect(width, start.size, end.size, fullStart, fullEnd) {
        if (width <= 0f) return@LaunchedEffect
        // Past the reveal, and off the far edge. There has to be an *anchor*
        // out there for the drag to reach it: the commit threshold used to be
        // measured against the row's width while the anchors only spanned the
        // reveal (88dp per action), so `AnchoredDraggableState` clamped the
        // offset long before the threshold and the commit could never fire on
        // anything wider than about 147dp. Which is every list row.
        val commit = width
        state.anchoredState.updateAnchors(
            DraggableAnchors {
                SwipeValue.Resting at 0f
                if (start.isNotEmpty()) SwipeValue.Start at startTravel
                if (end.isNotEmpty()) SwipeValue.End at endTravel
                if (fullStart != null) SwipeValue.StartCommitted at commit
                if (fullEnd != null) SwipeValue.EndCommitted at -commit
            }
        )
        state.deliverPending()
    }

    val onFull by rememberUpdatedState { action: SwipeAction ->
        action.onAction()
    }

    // Fires on *settling*, not mid-drag. The old version watched the raw offset
    // and committed the instant it crossed a threshold, so an action ran while
    // the user's finger was still down and could still have been dragged back.
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
     * One haptic in this gesture, at the one moment that has a consequence.
     *
     * There were four. A tick per `actionWidth` uncovered, a `DragThreshold` at
     * the commit point, a `Confirm` when the action ran, and a `GestureEnd` when
     * the row settled — so a full swipe that deleted something fired a short
     * burst, and a swipe that was thought better of still fired two. The
     * reported effect was a row that "goes way too crazy", which is what a
     * pattern reads as when it is not describing anything.
     *
     * The tick was the least defensible of the four: it was written to match the
     * sliders and the pickers, but `actionWidth` is not a detent. Nothing snaps
     * there and nothing rests there — the anchors are rest, revealed and
     * committed — so it was a click for a boundary that only existed in the
     * arithmetic. The `Confirm` and the `GestureEnd` reported outcomes the user
     * was already watching happen.
     *
     * What is left is the threshold: past this point, letting go deletes the
     * row. It is the only thing in the gesture the user cannot see coming, and
     * it is the only thing still worth a buzz.
     */

    /**
     * Whether the swipe has gone far enough to commit.
     *
     * Hoisted out of the effect below so the *colour* can read it as well as
     * the haptics. A vibration is a good signal and a lonely one: it says
     * nothing to a user who has haptics off, nothing on a desktop, and nothing
     * at all if the phone is in a pocket. The strip brightening at the same
     * moment is the same statement in a channel everybody has.
     */
    var pastThreshold by remember { mutableStateOf(false) }

    /**
     * How far past the reveal the row has been carried, as a fraction of the way to
     * the commit.
     *
     * Zero everywhere a commit is not on offer — at rest, on a side with no
     * full-swipe action, before the row has been measured — so everything keyed on it
     * is inert on a row that cannot commit.
     *
     * A function of the live offset rather than a value, because every caller reads
     * it inside a `drawBehind` or a `layout` and the row must not recompose sixty
     * times a second to animate a colour and three widths.
     */
    fun expansion(live: Float): Float {
        if (live.isNaN() || live == 0f || width <= 0f) return 0f
        val committing = if (live > 0f) fullStart else fullEnd
        if (committing == null) return 0f
        val revealedPx = if (live > 0f) startTravel else -endTravel
        if (revealedPx <= 0f) return 0f
        val room = (width - revealedPx).coerceAtLeast(1f)
        return ((abs(live) - revealedPx) / room).coerceIn(0f, 1f)
    }

    /**
     * How wide [action]'s panel is drawn, at the row's current position.
     *
     * The other half of *"that outermost action should expand to fill all actions"*.
     * Past the reveal the committing action grows toward the whole revealed width
     * while its siblings give theirs up, so the set's total is `actionWidth` times the
     * count at every point and the arrangement never shifts — the outermost simply
     * swallows the rest of the strip.
     *
     * Read in the layout phase, which is what lets three panels change width per
     * frame without a recomposition.
     */
    fun panelWidth(action: SwipeAction): Float {
        val live = state.anchoredState.offset
        val grown = expansion(live)
        if (grown == 0f) return actionWidthPx
        val committing = if (live > 0f) fullStart else fullEnd
        val revealedPx = if (live > 0f) startTravel else -endTravel
        return if (action === committing) {
            actionWidthPx + grown * (abs(revealedPx) - actionWidthPx)
        } else {
            actionWidthPx * (1f - grown)
        }
    }


    LaunchedEffect(state, actionWidthPx, width) {
        snapshotFlow { state.anchoredState.offset }.collect { offset ->
            if (offset.isNaN() || actionWidthPx <= 0f) return@collect

            // Where the commit actually becomes inevitable, rather than a
            // number beside it.
            //
            // This was `width * 0.6f`, a constant of its own, and it described
            // nothing: the commit is decided by `AnchoredDraggableState`, which
            // settles onto the committed anchor once a release is
            // `PositionalThreshold` of the way from the revealed one to it. So
            // the buzz fired at six tenths of the row while the point of no
            // return moved with the action count and the row's width, and on a
            // narrow row with two actions the two were a long way apart.
            //
            // Derived, they are the same point by construction, which is the
            // only way a haptic that means "past here, letting go deletes it"
            // can keep meaning it.
            val revealed = maxOf(abs(startTravel), abs(endTravel))
            val commitAt = revealed + threshold(width - revealed)
            val past = width > 0f && abs(offset) >= commitAt
            if (past && !pastThreshold) feedback.perform(FeedbackIntent.DragThreshold)
            pastThreshold = past
        }
    }

    // **The swipe's own settle, because the shared one cannot be asked for a firm
    // flick.**
    //
    // `PositionalThreshold`'s own KDoc used to say raising it was the only lever
    // there is, and that `BottomSheet` recorded the same gap for the same reason.
    // The sheet has since closed it, and this is that fix arriving here — but not by
    // the same route, because the two gestures are shaped differently. A sheet's
    // flick arrives through nested scroll, where the sheet owns the settle already;
    // a swipe *is* the `anchoredDraggable`, so the settle is its fling behaviour and
    // there is nowhere else to put the rule.
    //
    // What was actually wrong is sharper than "too easy". `AnchoredDraggableDefaults`
    // resolves a fling through Foundation's `computeTarget`, which takes a velocity
    // threshold as well as a positional one, and above that threshold — 125dp/s, a
    // private constant — **direction decides and distance stops mattering**. 125dp/s
    // is slower than any swipe anybody makes deliberately. So once the row had passed
    // the reveal, the next anchor in the direction of travel was the committed one,
    // and any ordinary flick past the actions ran the action: *"I sometimes end up
    // triggering the action"*.
    //
    // Two rules replace it, and only the commit is affected:
    //
    // - **A firm flick goes where it is aimed.** Above `SwipeFlickVelocity` the
    //   release velocity is projected through a decay and the nearest anchor to the
    //   landing wins, so a hard throw commits and a quick flick of the fingers
    //   reveals. Ported from `SheetState.detentAimedAt`, including the reason the
    //   floor exists: below it the projection collapses onto where the finger already
    //   is, and the behaviour is the positional one it has always been.
    // - **A slow drag has to earn a commit.** The reveal keeps
    //   `swipePositionalThreshold` exactly; the commit asks for `CommitShare` of the
    //   way from the reveal to it, because revealing is free and reversible and
    //   committing deletes a row.
    //
    // Built here rather than in `SwipeActionsState` so it can see the theme's motion
    // and the density, and remembered on everything it closes over.
    val flickVelocityPx = with(density) { SwipeFlickVelocity.toPx() }
    val settleSpec: AnimationSpec<Float> = motion.springOrTween(motion.springDefault)
    val fling = remember(state, settleSpec, threshold, flickVelocityPx) {
        SwipeSettle(
            state = state.anchoredState,
            snap = settleSpec,
            positional = threshold,
            flickVelocity = flickVelocityPx,
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
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
        val offset = state.anchoredState.offset
        val settled = if (offset.isNaN()) 0f else offset

        // Whichever side is being revealed, drawn beneath the row — and the
        // trailing set **backwards**.
        //
        // The declared order runs from the screen edge in toward the row, on
        // both sides. On the leading side that is already what a `Row` does:
        // the set is packed against the row, so the first action lands at the
        // container's edge and the last one against the row. On the trailing
        // side the same packing runs the other way round, so the list has to be
        // reversed to say the same thing.
        //
        // It is the convention every platform's swipe rows use, and the reason
        // is the full swipe. Carrying a row all the way runs the *first* action
        // of the side, and a full swipe is that action taking over the whole
        // row — so the first action is the one at the far edge, growing toward
        // the finger as the gesture goes on. With the order the other way up,
        // the action a full swipe commits to was the one hard against the row
        // and the one at the edge was the one it would never run.
        //
        // `asReversed` rather than `reversed`: a view, not a copy, so this is
        // not three allocations per frame of a drag.
        val revealed = when {
            settled > 0f -> start
            settled < 0f -> end.asReversed()
            else -> emptyList()
        }
        if (revealed.isNotEmpty()) {
            // The action's own colour, at full strength, from the first pixel of
            // the drag.
            //
            // It used to darken while letting go would do nothing and lighten as
            // the row crossed its threshold, on the argument that the user is
            // asking "will letting go do the thing" and that has a yes and a no.
            // The answer arrives, but it arrives as a colour change on a box
            // that is also sliding, growing and being tracked by a finger — so
            // what it reads as is the box flickering partway through the swipe.
            // Reported exactly that way, and the feedback it was carrying is
            // already carried better: the threshold fires a distinct
            // `DragThreshold` tick, felt rather than watched, on the frame it is
            // crossed.
            // **The nearest action's colour, not the furthest.**
            //
            // The ground behind the row is the strip the row is sliding off, so
            // it is the colour of whatever it is sliding *onto* — the action
            // closest to it. Which end of `revealed` that is depends on the
            // side, because the two sides pack against the row from opposite
            // directions: the leading set ends at the row, the trailing set
            // starts at it.
            //
            // This took `last()` for both, which is the furthest action on one
            // side and the nearest on the other. Trailing actions are the common
            // arrangement, so what a reader saw was a row sliding off one colour
            // onto a band of another.
            val nearColour = if (settled > 0f) revealed.last().background
            else revealed.first().background

            // **And the committing action's colour once the row is past the reveal.**
            //
            // Reported with the expansion it goes with: *"when you do trigger by
            // swiping/flicking, that outermost action should expand to fill all
            // actions, and the background colour should be of that action"*. Past the
            // reveal the only thing that can still happen is the commit, so the strip
            // stops being the ground the row is sliding onto and becomes the action
            // that is about to run.
            //
            // Crossed over the reveal-to-commit distance rather than switched at a
            // point. A colour that changes on one frame, on a box that is also
            // sliding and growing and being tracked by a finger, reads as a flicker —
            // which is exactly what the old threshold-dimming did and was reported
            // as, twenty lines above.
            val farColour = (if (settled > 0f) fullStart else fullEnd)?.background
                ?: nearColour

            // The area the row has vacated, plus just enough tucked under the
            // row to fill the wedge its rounded corner cuts away.
            //
            // Both extremes of this are wrong, and each one is a defect that has
            // actually been reported. Filling the whole box puts the action's
            // colour behind *all four* of the row's corners, so the first pixel
            // of a drag draws a coloured outline around a row that has barely
            // moved — worst in dark mode, where a bright action sits against a
            // dark row. Stopping dead at the row's straight edge leaves the page
            // showing through the quarter-circle behind the corner, which at
            // full reveal is a gap between the row and the strip rather than a
            // card sitting over it.
            //
            // So the tuck is bounded by the reveal itself: at two pixels of drag
            // it is two pixels, which cannot reach the far corners; at rest it is
            // the corner radius, which is all the wedge needs. Half the height is
            // an upper bound on any corner radius a row-shaped container can
            // have — a capsule's is exactly that, and every rung below it less.
            //
            // Drawn rather than sized, so the strip follows the offset in the
            // draw phase instead of recomposing the row sixty times a second.
            Box(
                Modifier
                    .matchParentSize()
                    .drawBehind {
                        val live = state.anchoredState.offset
                        if (live.isNaN() || live == 0f) return@drawBehind
                        val revealedWidth = abs(live).coerceAtMost(size.width)
                        val tuck = revealedWidth.coerceAtMost(size.height / 2f)
                        val painted = (revealedWidth + tuck).coerceAtMost(size.width)
                        // The one place the logical offset has to become a
                        // physical one: a `DrawScope` is not mirrored, so this
                        // has to say which side of the canvas the row vacated
                        // rather than which side of itself.
                        //
                        // `live > 0f` means the row moved toward the trailing
                        // edge and left the *leading* one bare — the left in
                        // LTR and the right in RTL, which is what the second
                        // half of this comparison carries.
                        drawRect(
                            color = lerp(nearColour, farColour, expansion(live)),
                            topLeft = Offset(
                                x = if ((live > 0f) != isRtl) 0f else size.width - painted,
                                y = 0f,
                            ),
                            size = Size(painted, size.height),
                        )
                    }
            )

            // **The panels travel with the row, rather than waiting at the edge
            // of the screen for it to arrive.**
            //
            // They used to be pinned to the container: `matchParentSize` with
            // the whole set packed against the far edge. At full reveal that
            // looks right — the first action ends up against the row — but on
            // the way there the row uncovers the container's edge *first*, so
            // the panel a half-open swipe shows is the one furthest from the
            // row. Swiping a row onto Remove, Archive and Pin showed Pin until
            // the gesture was nearly complete.
            //
            // Anchored to the row's own edge instead, so the set slides in
            // behind it and the first thing uncovered is the panel the row is
            // about to reach. The offset is read in the layout phase from the
            // same live position the row uses, so the two cannot drift apart by
            // a frame.
            //
            // Which panel that is, is `revealed`'s business rather than this
            // block's — see the reversal above.
            Row(
                modifier = Modifier
                    .matchParentSize()
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val live = state.anchoredState.offset
                        val width = constraints.maxWidth.toFloat()
                        val x = when {
                            live.isNaN() || live == 0f -> 0f
                            // Trailing: the set starts where the row's trailing
                            // edge now is, and runs off the far side.
                            live < 0f -> width + live
                            // Leading: the set *ends* where the row's leading
                            // edge now is.
                            else -> live - width
                            // Both measured from the container's *leading* edge,
                            // which is what `placeRelative` below wants.
                        }
                        layout(placeable.width, placeable.height) {
                            // `placeRelative`, not `place`. The offset above is
                            // logical — see `startTravel` — and `place` is the
                            // one that does *not* mirror, so the set travelled
                            // the right distance in the wrong direction under
                            // RTL and left the row's edge entirely.
                            placeable.placeRelative(x.roundToInt(), 0)
                        }
                    },
                // Packed against the row, which is the opposite edge from the
                // one the offset above has moved the whole set past.
                horizontalArrangement = if (settled > 0f) {
                    Arrangement.End
                } else {
                    Arrangement.Start
                },
            ) {
                revealed.forEach { action ->
                    SwipeActionButton(
                        action = action,
                        background = action.background,
                        width = actionWidth,
                        liveWidth = { panelWidth(action) },
                        onClick = {
                            action.onAction()
                            scope.launch { state.reset() }
                        },
                    )
                }
            }
        }

        val swipeable = enabled && (start.isNotEmpty() || end.isNotEmpty())

        Box(
            Modifier
                .offset { IntOffset(settled.roundToInt(), 0) }
                .anchoredDraggable(
                    state = state.anchoredState,
                    orientation = Orientation.Horizontal,
                    enabled = swipeable,
                    flingBehavior = fling,
                )
                /**
                 * A sideways scroll is a swipe.
                 *
                 * On a desktop there is no finger to drag the row with, and a
                 * trackpad's two-finger sideways push is the gesture that means
                 * exactly this everywhere else on the platform. It was doing
                 * nothing at all: `anchoredDraggable` answers drags, and a wheel
                 * is not one.
                 *
                 * No end event exists for a scroll — the platform never says
                 * "they stopped" — so the row settles on a short timer after the
                 * last one, which is also what makes a run of notches read as
                 * one gesture rather than as a dozen tiny ones each snapping
                 * back.
                 */
                .then(
                    if (swipeable) {
                        Modifier.pointerInput(state, scope) {
                            var settling: Job? = null
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    if (event.type != PointerEventType.Scroll) continue
                                    val sideways = event.changes.sumOf {
                                        it.scrollDelta.x.toDouble()
                                    }.toFloat()
                                    if (sideways == 0f) continue
                                    event.changes.forEach { it.consume() }
                                    state.anchoredState.dispatchRawDelta(
                                        -sideways * SwipeActionsDefaults.ScrollStep
                                    )
                                    settling?.cancel()
                                    settling = scope.launch {
                                        delay(SwipeActionsDefaults.SettleAfterScroll)
                                        val anchors = state.anchoredState.anchors
                                        val nearest = anchors.closestAnchor(
                                            state.anchoredState.offset
                                        )
                                        if (nearest != null) state.anchoredState.animateTo(nearest)
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
 * How fast a release has to be before it counts as a flick, per second.
 *
 * Written as the distance one second of it would cover, so it scales with the
 * display the way every other gesture constant here does. 500dp/s is the number
 * `BottomSheet` measured on a device for the same question, and it is borrowed
 * rather than re-derived: both are a row-or-panel-sized object thrown by a thumb,
 * and two different answers to "was that a flick" on one screen is worse than one
 * answer that is approximately right twice.
 *
 * Below it a release settles by position exactly as it always did. Above it the
 * velocity is projected and the row goes where it was aimed, which is what lets a
 * hard throw commit and a quick flick of the fingers stop at the actions.
 */
private val SwipeFlickVelocity: Dp = 500.dp

/**
 * How far from the reveal toward the commit a **slow** drag has to travel.
 *
 * Four fifths, against the 0.55 the reveal itself asks for, and the asymmetry is the
 * point: revealing actions is free and undone by letting go, and committing deletes
 * a row. A gesture that has gone four fifths of the way across a list row with the
 * action's colour filling in behind it is not a gesture anybody made by accident.
 *
 * Only a slow drag is held to it. A firm flick is judged by where it is aimed, which
 * is the other half of `SwipeSettle`.
 */
private const val CommitShare: Float = 0.8f

/** The decay a flick's landing is projected through. As `SheetFlingDecay`. */
private val SwipeFlingDecay: DecayAnimationSpec<Float> = exponentialDecay()

/** Whether this anchor is one that runs an action. */
private val SwipeValue.isCommitted: Boolean
    get() = this == SwipeValue.StartCommitted || this == SwipeValue.EndCommitted

/**
 * Where a released swipe settles.
 *
 * Only ever moves the row with `ScrollScope.scrollBy`, which is the whole of why
 * this is safe to write. `BottomSheet` records the trap: a settle that calls
 * `animateTo` from inside a fling is asking for the drag mutex the fling is already
 * holding, and the failure mode is a control that stops responding. Nothing here
 * touches the state except to read it — the anchor it lands on is whichever one the
 * final offset is nearest, which is how Foundation's own behaviour reports its
 * answer too.
 */
private class SwipeSettle(
    private val state: AnchoredDraggableState<SwipeValue>,
    private val snap: AnimationSpec<Float>,
    private val positional: (Float) -> Float,
    private val flickVelocity: Float,
) : TargetedFlingBehavior {

    override suspend fun ScrollScope.performFling(
        initialVelocity: Float,
        onRemainingDistanceUpdated: (Float) -> Unit,
    ): Float {
        val anchors = state.anchors
        val from = state.offset
        if (from.isNaN() || anchors.size == 0) return 0f
        val target = aimedAt(anchors, from, initialVelocity) ?: return 0f
        val to = anchors.positionOf(target)
        if (to.isNaN()) return 0f

        var last = from
        animate(
            initialValue = from,
            targetValue = to,
            initialVelocity = initialVelocity,
            animationSpec = snap,
        ) { value, _ ->
            scrollBy(value - last)
            last = value
            onRemainingDistanceUpdated(abs(to - value))
        }
        // Everything the throw carried has been spent getting to an anchor. A row
        // does not hand leftover velocity anywhere — there is nothing past the
        // committed anchor to hand it to.
        return 0f
    }

    /**
     * Which anchor a release is going to, or null when there is nothing to decide.
     *
     * The positions are known: rest at zero, the reveal at the actions' width, the
     * commit at the row's. Both sides are signed, so the arithmetic below is one
     * expression rather than two mirrored ones.
     */
    private fun aimedAt(
        anchors: DraggableAnchors<SwipeValue>,
        from: Float,
        velocity: Float,
    ): SwipeValue? {
        if (abs(velocity) >= flickVelocity) {
            // Aimed: the nearest anchor to where the throw would have come to rest.
            // No distance requirement, because a flick hard enough to land past the
            // row *is* the deliberate gesture the commit is asking for.
            val projected = SwipeFlingDecay.calculateTargetValue(from, velocity)
            var aimed: SwipeValue? = null
            var best = Float.MAX_VALUE
            for (index in 0 until anchors.size) {
                val at = anchors.positionAt(index)
                if (at.isNaN()) continue
                val value = anchors.anchorAt(index) ?: continue
                val distance = abs(at - projected)
                if (distance < best) {
                    best = distance
                    aimed = value
                }
            }
            return aimed
        }

        // Slow: the pair of anchors the row is between, and the threshold between
        // them — which is exactly what Foundation does below its own velocity floor.
        var lower: SwipeValue? = null
        var lowerAt = -Float.MAX_VALUE
        var upper: SwipeValue? = null
        var upperAt = Float.MAX_VALUE
        for (index in 0 until anchors.size) {
            val at = anchors.positionAt(index)
            if (at.isNaN()) continue
            val value = anchors.anchorAt(index) ?: continue
            if (at <= from && at > lowerAt) {
                lowerAt = at
                lower = value
            }
            if (at >= from && at < upperAt) {
                upperAt = at
                upper = value
            }
        }
        val below = lower ?: return upper
        val above = upper ?: return below

        val crossed = lowerAt + positional(upperAt - lowerAt)
        val chosen = if (from >= crossed) above else below
        val chosenAt = if (from >= crossed) upperAt else lowerAt
        if (!chosen.isCommitted) return chosen

        // The commit is the one anchor a slow gesture has to earn. Falling short of
        // it means the other end of the pair, which is the reveal the row came from.
        val inner = if (chosen == above) lowerAt else upperAt
        val earned = inner + (chosenAt - inner) * CommitShare
        return if (abs(from) >= abs(earned)) chosen else if (chosen == above) below else above
    }
}

@Composable
private fun RowScope.SwipeActionButton(
    action: SwipeAction,
    /** [SwipeAction.background], dimmed while the swipe would not commit. */
    background: Color,
    width: Dp,
    /**
     * The panel's width right now, in pixels, read in the layout phase.
     *
     * [width] is still what it measures at rest and is still the touch target the
     * accessibility story rests on; this is the deformation on top, for the commit
     * the outermost action grows into. A lambda rather than a value because the whole
     * point is that a frame of it costs a measure and not a recomposition.
     */
    liveWidth: () -> Float,
    onClick: () -> Unit,
) {
    // From the action's own colour rather than from the dimmed one: the label
    // has to stay legible while the ground behind it darkens, and picking the
    // content colour off a moving background is how a white label ends up black
    // for two frames in the middle of a gesture.
    val content = contentColourFor(action.background)

    Surface(
        modifier = Modifier
            // `layout` rather than `width`, because the width is a per-frame value.
            // The Surface clips to its own shape, so a panel shrinking out of the way
            // takes its icon with it rather than leaving one floating over the
            // action that is swallowing it.
            .layout { measurable, constraints ->
                val wide = liveWidth().roundToInt().coerceIn(0, constraints.maxWidth)
                val placeable = measurable.measure(
                    constraints.copy(minWidth = wide, maxWidth = wide)
                )
                layout(wide, placeable.height) { placeable.place(0, 0) }
            }
            .fillMaxHeight(),
        colour = background,
        contentColour = content,
        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints(
            // `fillMaxSize`, not `fillMaxHeight`. Without the width this box
            // wrapped the icon and its label — about 40dp of it — and sat
            // centred in an 88dp panel that was coloured, obviously tappable and
            // dead down both sides. A press near the edge of a revealed action
            // did nothing, which reads as the swipe having failed rather than as
            // the target being narrower than the paint.
            modifier = Modifier
                .fillMaxSize()
                .clickableAction(onClick, action.label)
                .padding(Theme.spacing.xs),
            contentAlignment = Alignment.Center,
        ) {
            // The label goes when there is no room for it, rather than being
            // clipped to a stripe of its own ascenders.
            //
            // This was clipped on every single-line row in the library — a
            // `ListItem` is 48dp and icon + gap + label + padding wants 59dp —
            // and nothing had ever seen it: the actions are only drawn once the
            // row is swiped, and no test or render had ever swiped one. The
            // first picture of a revealed row showed a red panel with a star and
            // four pixels of "Remove" under it.
            //
            // Measured rather than compared against a magic dp, so it stays
            // right at 200% type, where the label is twice as tall and the icon
            // is not.
            val labelHeight = with(LocalDensity.current) {
                Theme.typography.labelSmall.lineHeight.toDp()
            }
            val roomForLabel =
                maxHeight >= Theme.sizing.iconLarge + Theme.spacing.xxs + labelHeight

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
                // `onClickLabel` on this button, which is where it was always
                // doing the accessibility work.
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

/**
 * Clickable without a role, because the row above already exposes this as a
 * custom accessibility action — announcing it twice makes one action sound like
 * two.
 */
@Composable
private fun Modifier.clickableAction(onClick: () -> Unit, label: String): Modifier =
    pointerCursor().clickable(onClickLabel = label, onClick = onClick)

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
