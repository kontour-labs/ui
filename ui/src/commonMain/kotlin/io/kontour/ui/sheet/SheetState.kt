package io.kontour.ui.sheet

import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.snapTo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Velocity
import io.kontour.ui.interaction.RubberBand
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Where a sheet is, and where it is going.
 *
 * Built on foundation's `AnchoredDraggableState`, which already handles the
 * drag, the fling and the settle. What this adds is the part that is actually
 * specific to sheets: turning [SheetDetent]s into anchor positions as the
 * container and content resize, and handing scroll off between the sheet and
 * whatever scrolls inside it.
 *
 * ```kotlin
 * val sheet = rememberSheetState(
 *     detents = listOf(SheetDetent.Hidden, SheetDetent.peek(140.dp), SheetDetent.Expanded),
 *     initialDetent = SheetDetent.Hidden,
 * )
 *
 * BottomSheet(sheet) {
 *     SheetHeader(Modifier.sheetPeekAnchor()) { +"Perth Underground" }
 *     LazyColumn { … }
 * }
 * ```
 */
@Stable
class SheetState internal constructor(
    detents: List<SheetDetent>,
    initialDetent: SheetDetent,
    internal val confirmDetentChange: (SheetDetent) -> Boolean,
) {
    init {
        require(detents.isNotEmpty()) { EmptyDetents }
        require(initialDetent in detents) {
            "initialDetent $initialDetent is not in $detents"
        }
    }

    /**
     * The positions this sheet may rest at.
     *
     * Reassignable, because which detents apply depends on what is in the sheet:
     * the map's location sheet has only Hidden and a peek, while its trip sheet
     * has four. Filtering the list beats defining a second sheet.
     */
    var detents: List<SheetDetent>
        get() = detentList
        // Checked on assignment rather than where it is read. This is a public
        // `var` whose KDoc recommends filtering the list, and a filter can match
        // nothing — so `state.detents = allDetents.filter { … }` used to succeed
        // and take the frame down some frames later inside `DragHandle`, where
        // `SheetHeader` reads `detents.last()`. A stack trace from there names
        // neither the assignment nor the filter.
        set(value) {
            require(value.isNotEmpty()) { EmptyDetents }
            detentList = value
        }

    private var detentList: List<SheetDetent> by mutableStateOf(detents)

    internal val anchoredState = AnchoredDraggableState(initialValue = initialDetent)

    /**
     * The detents that currently have an anchor.
     *
     * [confirmDetentChange] used to be `AnchoredDraggableState`'s
     * `confirmValueChange`, a veto consulted on every attempted move. That is
     * deprecated, and the replacement upstream asks for is this: do not refuse a
     * position, do not give it one. A refused detent then has nowhere for the
     * sheet to go, so a drag towards it meets the end of its travel instead of
     * following the finger down and springing back — which is what iOS does, and
     * the difference is felt rather than seen.
     *
     * **The settled detent is always in here**, whatever the predicate says, and
     * that is not a loophole. An anchor set that omits where the sheet actually
     * *is* has no offset for it, and the sheet resolves to `NaN`. It also makes
     * the one case this parameter exists for come out right on its own: a sheet
     * that starts [SheetDetent.Hidden] and refuses it opens from hidden — the
     * position it is already at — and once it has settled somewhere else, hidden
     * stops being a place it can be dragged back to.
     *
     * Derived rather than filtered on each read: this is read from layout, and
     * `updateAnchors` guards a per-frame rebuild behind a comparison that an
     * allocation in front of it would undo.
     */
    internal val allowedDetents: List<SheetDetent> by derivedStateOf {
        val settled = anchoredState.settledValue
        detents.filter { it == settled || confirmDetentChange(it) }
    }

    /** Container height in pixels. Set by the sheet's layout. */
    internal var containerHeight by mutableFloatStateOf(0f)

    /**
     * Whether the user may put this sheet away themselves.
     *
     * Set by `ModalBottomSheet` from its `dismissible` parameter, and about the
     * *user* rather than the app: [hide] still works, because closing a sheet
     * the user must answer is exactly what the app does once they have answered
     * it. So [SheetDetent.Hidden] stays in the anchors and only [dragFloor]
     * knows the difference.
     *
     * It has to live here rather than in the sheet composable because the two
     * things that need it — the drag and the list inside it — both reach the
     * sheet through this object.
     */
    internal var userDismissible by mutableStateOf(true)

    /**
     * How far the sheet has been pulled **above** its tallest detent, in pixels.
     *
     * `anchoredDraggable` clamps to its anchor range, so without this a sheet at
     * its top detent does not move at all under a finger still travelling
     * upward. Measured rather than assumed: dragged 292px past the top, the
     * offset stayed at 352.0 for every frame of the drag. That is the "too
     * rigid" — the sheet stops dead at a boundary the finger cannot feel.
     *
     * Subtracted from the offset at layout, so it is a purely visual stretch:
     * the anchors, the settled detent and everything derived from them are
     * untouched, and letting go springs it back to zero rather than settling
     * anywhere new.
     *
     * A [RubberBand], which is the library's primitive for exactly this and whose
     * arithmetic was written here in the first place — *"lifted here so the sheet
     * and the wheel picker share one, rather than the second one to want it
     * growing a copy"*. The sheet went on carrying the copy, and that is not a
     * tidiness point: the copy was the **single Euler step** the primitive was
     * rewritten to get rid of, which gives a different curve at 120Hz than at 60.
     * The library runs at 120 on an iPhone, so the sheet's own stretch was one of
     * the *"slightly different rubber-banding animations between each platform"*
     * that was reported. See [RubberBand.pull].
     */
    private val band = RubberBand()

    /** See [band]. Positive is upward, and the layout subtracts it. */
    internal val overshoot: Float get() = band.offset

    /**
     * How far the sheet is *drawn* above its detent, in pixels.
     *
     * [overshoot] is what the finger has pulled past the stop; this is how much
     * of that the sheet is allowed to show. They differ for a sheet that already
     * fills its container, which absorbs the pull and does not move — see
     * [canOvershoot].
     */
    internal val drawnOvershoot: Float get() = if (canOvershoot) overshoot else 0f

    /**
     * Whether a stretch above the top detent can be *seen*.
     *
     * A sheet already as tall as its container has nowhere to go: moving it up
     * lifts its bottom edge off the bottom of the screen and leaves a band of
     * background under it, which is the one thing a bottom sheet must never
     * show.
     *
     * It still absorbs the pull. This used to gate the absorbing as well as the
     * drawing, and the difference is the reported defect: a full-height sheet
     * dragged upward did nothing with the drag at all, so the gesture stayed
     * live, and the few pixels a finger travels back down as it leaves the glass
     * were a downward flick of several hundred pixels a second — enough to clear
     * `anchoredDraggable`'s velocity threshold and settle the sheet a detent
     * lower. Measured: a `Full` sheet dragged 660px up and released with 24px of
     * roll-off went to `half`, where the same 24px on its own left it exactly
     * where it was, and an `Expanded` sheet — which can stretch, and therefore
     * had a stretch to pay back — was unmoved by twice that.
     */
    internal val canOvershoot: Boolean
        get() {
            val tallest = highestAnchored(allowedDetents)
            return !tallest.isNaN() && tallest > 0.5f
        }

    /**
     * The offsets of the detents that are actually **in** the anchors.
     *
     * `positionOf` answers `NaN` for a detent that is not anchored, and two
     * detents that resolve to the same height leave only one of them anchored —
     * `resolveAnchors` drops the duplicate deliberately, because two anchors at
     * one offset make `settledValue` ambiguous. So `NaN` is an ordinary answer
     * here rather than an error, and a `minOfOrNull` over a list containing one
     * is `NaN`: every comparison against it is false, so the whole reading
     * collapses.
     *
     * That is not hypothetical and it is not small. A sheet whose peek anchor was
     * its only always-present content resolved `Expanded` to the same offset as
     * its peek, lost `Expanded` to the dedupe, and then reported `NaN` from
     * [lowestRestingOffset] — which took the floating sheet's landing arithmetic
     * with it, and [dragFloor] with that, so an undismissable sheet lost the very
     * floor it exists to have. One dropped anchor, three silent reversions.
     *
     * [SheetDetent.Hidden] is skipped: both of these are asked about where the
     * sheet can *be*, and hidden is where it goes to stop being anywhere. It
     * makes no difference to [canOvershoot] — hidden is the largest offset, so
     * it was never the smallest — except for a sheet whose only detent is hidden,
     * which now reports that it cannot be stretched, which is true.
     */
    private fun highestAnchored(detents: List<SheetDetent>): Float =
        extremeAnchored(detents, highest = true)

    /** See [highestAnchored]. The largest offset, which is the lowest on screen. */
    private fun lowestAnchored(detents: List<SheetDetent>): Float =
        extremeAnchored(detents, highest = false)

    private fun extremeAnchored(detents: List<SheetDetent>, highest: Boolean): Float {
        val anchors = anchoredState.anchors
        var found = Float.NaN
        for (detent in detents) {
            if (detent == SheetDetent.Hidden) continue
            val at = anchors.positionOf(detent)
            if (at.isNaN()) continue
            if (found.isNaN() || (if (highest) at < found else at > found)) found = at
        }
        return found
    }

    /**
     * Where [detent] puts the sheet's top edge, anchored or not.
     *
     * The anchors are the first answer and the arithmetic is the fallback, which
     * is the whole point: `resolveAnchors` drops a detent that lands on an offset
     * already taken, so a perfectly meaningful detent can be missing from the
     * anchors — and `positionOf` then says `NaN`, which is indistinguishable from
     * "nonsense" to every caller that asks. Recomputed, it says what it has
     * always meant: the offset the sheet would sit at, which is *also* the offset
     * of whichever anchor swallowed it.
     *
     * `NaN` only before the first layout, where nothing has been measured and
     * there is genuinely no answer.
     */
    internal fun offsetOf(detent: SheetDetent): Float {
        val anchored = anchoredState.anchors.positionOf(detent)
        if (!anchored.isNaN()) return anchored
        val density = anchorDensity ?: return Float.NaN
        if (containerHeight <= 0f) return Float.NaN
        return detentOffset(
            detent = detent,
            containerHeight = containerHeight,
            sheetHeight = sheetHeight,
            peekHeight = peekHeight,
            density = density,
        )
    }

    /**
     * Whether the sheet is at [detent]'s height or above it, or on its way there.
     *
     * Compared by *position* rather than by index in the detent list, because a
     * list is written in whatever order a caller found convenient and two detents
     * can resolve to the same height.
     *
     * **Target, not settled**, so it answers as soon as a drag has committed
     * rather than once the sheet has stopped. What reads it is
     * [SheetContentScope.part], and only to decide whether the part is in the
     * assistive tree: a part's *pixels* are not gated on anything, because the
     * sheet's own bottom edge is what hides them.
     */
    internal fun willReach(detent: SheetDetent): Boolean {
        val here = offsetOf(anchoredState.targetValue)
        val there = offsetOf(detent)
        if (here.isNaN() || there.isNaN()) return false
        // Offsets grow downward, so a taller sheet is a smaller number.
        return here <= there
    }

    /**
     * The density the anchors were last built against.
     *
     * Kept so [offsetOf] can resolve a detent the anchors do not have. A detent
     * is a `Density.(container, sheet) -> Float`, and every other caller of one
     * is inside a layout block that has a density to hand; this object is not,
     * so it keeps the one its anchors were built with. Null before the first
     * layout, which is the one time there is nothing to resolve against.
     */
    private var anchorDensity: Density? = null

    /**
     * The offset of the lowest detent that is somewhere to *be*, in pixels.
     *
     * `NaN` before there are anchors, and for a sheet whose only detent is
     * [SheetDetent.Hidden].
     *
     * The line between "short" and "leaving". Everything above it is the sheet at
     * one of its sizes; everything below it is the sheet on its way out, because
     * the only anchor down there is hidden. [dragFloor] is the same measurement
     * for a different question — what a *drag* may reach — so it consults
     * [allowedDetents] and gives up for a dismissable sheet. This one is about
     * where the sheet is rather than about what the user may do, so it reads the
     * whole list and answers the same whoever is moving it.
     */
    internal val lowestRestingOffset: Float get() = lowestAnchored(detents)

    /**
     * How far through [morph] a floating sheet is with its top edge at [raw]: 0 at
     * or below `morph.from`, 1 at or above `morph.until`, and linear between.
     *
     * In offsets rather than detents so it reads the same for a finger, a spring and
     * a stretch above the top — whatever is moving the sheet, the morph is where the
     * sheet is. 0 for a null morph, before there are anchors, and for a sheet with
     * no step to morph across: `until` its lowest resting detent and `from` not
     * given, which leaves nothing below it to start from.
     */
    internal fun edgeMorphFraction(morph: SheetEdgeMorph?, raw: Float): Float {
        if (morph == null) return 0f
        val until = morph.until?.let(::offsetOf) ?: highestAnchored(detents)
        if (until.isNaN()) return 0f
        val from = morph.from?.let(::offsetOf) ?: restingBelow(until)
        if (from.isNaN()) return nearTheTop(morph, until)
        if (from <= until) return 0f
        return ((from - raw) / (from - until)).coerceIn(0f, 1f)
    }

    /**
     * The morph for a sheet with no step to morph across, decided by **where it
     * rests** — [resting], its one detent's offset — and not by where it is.
     *
     * Full if it rests at the top of the window, none if it rests further than
     * [SheetEdgeMorph.nearTop] below it, and between only for content that lands in
     * that last stretch. It used to follow the live offset over that stretch, which
     * was reported as the sheet "snapping" fully open near the top: a tall sheet
     * opened floating and changed shape in its last few frames. Decided by the rest,
     * it is one shape the whole way up and the whole way down.
     *
     * The top is [fullHeightTop], where a sheet whose content fills the window really
     * rests: below the status bar, which on a phone is well below [SheetTopGap] — the
     * number this used to measure from, so a full-height sheet rested half changed.
     */
    private fun nearTheTop(morph: SheetEdgeMorph, resting: Float): Float {
        val density = anchorDensity ?: return 0f
        val top = fullHeightTop.takeUnless { it.isNaN() } ?: with(density) { SheetTopGap.toPx() }
        val reach = with(density) { morph.nearTop.toPx() }
        // Half a pixel, so a sheet resting exactly at the top is fully there.
        if (reach <= 0f) return if (resting <= top + 0.5f) 1f else 0f
        return ((top + reach - resting) / reach).coerceIn(0f, 1f)
    }

    /**
     * Where a sheet whose content fills the window rests: the container less the
     * room the content is given. Written by the sheet's content measurement, which
     * is the one place that knows the insets. `NaN` until then.
     */
    internal var fullHeightTop by mutableFloatStateOf(Float.NaN)

    /**
     * The offset of the tallest resting detent, when **every** resting detent is
     * independent of the content — `Half`, a fraction, a height, a peek — and `NaN`
     * as soon as one is not.
     *
     * What the content is measured against. A sheet whose tallest detent is `Half`
     * shows half a window of content at most, and a list measured taller than that
     * has rows past the bottom of the window that no scrolling reaches. Only
     * detents that do not depend on the content can say so without a cycle — the
     * content's height is what `Expanded` resolves from — which is why one
     * content-dependent detent in the list turns this off.
     *
     * Tested rather than named: a detent is resolved against an empty sheet and a
     * window-tall one, and one that answers the same both times does not depend on
     * its content, whoever wrote it.
     */
    internal fun tallestFixedTop(): Float {
        val density = anchorDensity ?: return Float.NaN
        val container = containerHeight
        if (container <= 0f) return Float.NaN
        var top = Float.NaN
        for (detent in detents) {
            if (detent == SheetDetent.Hidden) continue
            val empty = detent.resolve(density, container, 0f)
            val full = detent.resolve(density, container, container)
            if (kotlin.math.abs(empty - full) > 0.5f) return Float.NaN
            val at = offsetOf(detent)
            if (at.isNaN()) return Float.NaN
            if (top.isNaN() || at < top) top = at
        }
        return top
    }

    /**
     * The nearest resting detent's offset below [offset] — the next larger one —
     * or `NaN` when there is none. [SheetDetent.Hidden] is not somewhere to rest.
     */
    private fun restingBelow(offset: Float): Float {
        val anchors = anchoredState.anchors
        var found = Float.NaN
        for (detent in detents) {
            if (detent == SheetDetent.Hidden) continue
            val at = anchors.positionOf(detent)
            // Half a pixel, so a detent that resolved onto the same offset as
            // `until` is not taken for the one below it.
            if (at.isNaN() || at <= offset + 0.5f) continue
            if (found.isNaN() || at < found) found = at
        }
        return found
    }

    /**
     * The furthest down a *drag* may take the sheet, in pixels of offset.
     *
     * `NaN` when there is nothing to stop at — which is the ordinary case, where
     * [SheetDetent.Hidden] is the bottom of the anchor range and a sheet dragged
     * to it is a sheet being put away.
     *
     * An undismissable sheet is the case this exists for. Hidden stays in the
     * anchors so [hide] still works, so `anchoredDraggable` will happily drag
     * the sheet to it — and what happened then is the whole of the report: the
     * sheet went all the way down, settled hidden, declined to tell the caller
     * because it is not dismissable, and was put back about a second later. Its
     * scrim faded out on the way, because the scrim follows [visibleFraction]
     * and the sheet really had gone. Measured: `vf` fell 1.0 → 0.36 across the
     * drag, reached 0.00005, reported `hidden`, and was back at 1.0 twenty
     * frames later without `onDismissRequest` ever being called.
     *
     * So the floor is the lowest detent a drag is *allowed* to settle at, and
     * past it the sheet stretches like it does above its top — same arithmetic,
     * other end.
     */
    internal val dragFloor: Float
        get() {
            if (userDismissible) return Float.NaN
            // Already away: nothing to hold it up, and a sheet mid-open would
            // otherwise be floored at wherever it happens to be.
            if (anchoredState.settledValue == SheetDetent.Hidden) return Float.NaN
            return lowestAnchored(allowedDetents)
        }

    /**
     * How much of a downward [delta] the sheet itself may take before [dragFloor].
     *
     * The rest is the caller's to stretch. Both the drag and the nested-scroll
     * connection ask, because a sheet with a form in it is dragged by its
     * content as often as by its handle and a floor only one of them respects is
     * not a floor.
     */
    internal fun roomBeforeFloor(delta: Float): Float {
        if (delta <= 0f) return delta
        val floor = dragFloor
        if (floor.isNaN()) return delta
        val current = anchoredState.offset
        if (current.isNaN()) return delta
        return minOf(delta, (floor - current).coerceAtLeast(0f))
    }

    /**
     * How far above the top detent the sheet may be pulled.
     *
     * A twelfth of the container: far enough to feel like the sheet answered the
     * finger, short enough that nobody mistakes it for a detent they have not
     * found yet.
     *
     * Approached rather than arrived at — a boundary that gives indefinitely is
     * not a boundary and one that arrives at a hard stop is the rigid boundary
     * again a few pixels further on. So it is the scale of the pull as much as its
     * ceiling: a finger travels about this far past the stop to get 63% of the way
     * out, twice that for 86%. See [RubberBand.pull].
     */
    internal val maxOvershoot: Float get() = containerHeight * OvershootShare

    /**
     * Takes [by] pixels of upward pull and returns how much of it was absorbed.
     *
     * Diminishing returns rather than a shorter track: each pixel of finger
     * moves the sheet less the further it has already been pulled, so the edge
     * feels like it is resisting. A linear stretch with a hard stop is the same
     * rigid boundary moved somewhere else.
     *
     * [maxOvershoot] is the stretch the sheet approaches and never reaches, which
     * is also the scale of the pull: a finger travels about that far past the stop
     * for 63% of it. It used to be a clamp the sheet arrived at, and an
     * exponential approach cannot overshoot — see [RubberBand.pull].
     */
    internal fun stretch(by: Float): Float {
        if (by <= 0f) return 0f
        return band.pull(by, maxOvershoot)
    }

    /**
     * The same, downward: [by] pixels of push below [dragFloor].
     *
     * [overshoot] is signed, and the layout subtracts it — so a negative one
     * moves the sheet down by exactly as much as a positive one moves it up, and
     * both ends spring back through the same [releaseOvershoot]. [by] is how hard
     * the finger is pushing, so it is positive here and the pull is not.
     */
    internal fun stretchDown(by: Float): Float {
        if (by <= 0f) return 0f
        return -band.pull(-by, maxOvershoot)
    }

    /**
     * Closes an open stretch at full rate, and returns how much of [by] it used.
     *
     * A finger coming back closes the gap it opened before the sheet itself
     * starts moving again; the other order slides the sheet away with the gap
     * still open, and one gesture produces two motions. Signed the way the
     * finger is: positive is downward.
     *
     * **The two frames are opposite**, which is the one thing to know about this
     * line. [overshoot] counts upward, because that is the direction a sheet is
     * stretched in and the layout subtracts it; a scroll delta counts downward,
     * because that is the direction a finger drags in. [RubberBand.payBack] works
     * in one frame throughout, so the delta is negated going in and the answer
     * negated coming out, and the sign convention this function documents is
     * unchanged for its callers.
     */
    internal fun payBackOvershoot(by: Float): Float = -band.payBack(-by)

    /** Springs the stretch back to nothing. */
    internal suspend fun releaseOvershoot(spec: AnimationSpec<Float>) = band.release(spec)

    /**
     * The content's own full height in pixels, for [SheetDetent.Expanded].
     *
     * **Every [SheetContentScope.part] is in it, at every detent.** A part used to
     * be left out while it was collapsed, and that closed a loop: `Expanded` is
     * this height, so a sheet whose parts were all gated resolved its tallest
     * detent to its *collapsed* height and could not be dragged any further. The
     * fix after that kept two numbers — what the column placed and what it would
     * place — so that revealing a part could not move an anchor. Both are gone
     * with the reveal: a part is laid out at its full height whatever the sheet is
     * doing, so this number does not depend on where the sheet is and there was
     * never anything for the second one to hold.
     */
    internal var sheetHeight by mutableFloatStateOf(0f)

    /**
     * How far down the sheet the peek anchor's *bottom edge* sits, in pixels.
     *
     * The distance from the top of the sheet, not the anchor's own height —
     * anything above the anchor counts too. The drag handle is the case that
     * makes the difference: a peek set to the header's height alone shows the
     * bottom of the sheet up to that height, which is the header minus the
     * handle, and the last line of the header is cut off.
     */
    internal val peekHeight: Float
        get() = if (peekContentHeight.isNaN()) 0f else peekContentHeight.coerceAtLeast(0f)

    /**
     * The peek anchor's bottom edge, in the **sheet's** coordinates.
     *
     * This used to be the difference between two positions in the root — the
     * anchor's bottom and the sheet's top — and that is a measurement of where
     * the sheet *is* as much as of how tall its header is. The two are reported
     * by separate `onGloballyPositioned` callbacks, and during a drag they are a
     * whole frame apart rather than a sub-pixel: the anchor fires first, with
     * the sheet's top from where the sheet was last frame, so the difference
     * came out short by however far the finger had travelled.
     *
     * A peek that changes is an [AnchorInputs] that changes, and every changed
     * input rebuilds the anchors — which pins the drag's target back to where
     * the gesture started. That was reported twice as a sheet hauled up past
     * every detent and dropping back to the lowest one on release.
     *
     * The sheet's offset is applied *above* the node these are measured
     * against, so in the sheet's own space the anchor does not move when the
     * sheet does. This is a measurement of the content and nothing else.
     */
    internal var peekContentHeight by mutableFloatStateOf(Float.NaN)

    /**
     * The two live layout nodes the peek is measured between.
     *
     * Held rather than sampled, because `onGloballyPositioned` fires
     * children-first and only when a position actually changes: the anchor
     * reports before the sheet it is inside, and on the first layout that is the
     * only report either of them makes. Sampling at the anchor's callback would
     * find the sheet unset and never get a second chance — which is how the peek
     * silently stayed at its fallback once before. `LayoutCoordinates` are live,
     * so whichever callback fires calls [measurePeek] and gets an answer taken
     * from the tree as it is now, not as it was when the other one fired.
     */
    internal var sheetCoordinates: LayoutCoordinates? = null
    internal var peekAnchorCoordinates: LayoutCoordinates? = null

    /**
     * Re-derives [peekContentHeight] from whichever of the two nodes is current.
     *
     * A no-op until both exist and both are still attached — a detached node
     * cannot be positioned against anything, and asking would throw.
     */
    internal fun measurePeek() {
        val sheet = sheetCoordinates ?: return
        val anchor = peekAnchorCoordinates ?: return
        if (!sheet.isAttached || !anchor.isAttached) return
        peekContentHeight =
            sheet.localPositionOf(anchor, Offset(0f, anchor.size.height.toFloat())).y
    }

    /** Where the sheet has settled. Equals [targetDetent] once it stops moving. */
    val currentDetent: SheetDetent get() = anchoredState.settledValue

    /** Where it is heading. Changes the instant a drag passes the threshold. */
    val targetDetent: SheetDetent get() = anchoredState.targetValue

    /** True while it is animating or being dragged. */
    val isMoving: Boolean get() = anchoredState.isAnimationRunning

    /**
     * True while a **finger** is moving the sheet, as opposed to code.
     *
     * The signal `FeedbackIntent.DragThreshold`'s own documentation said was
     * missing, in as many words: [targetDetent] "changes the instant a drag
     * passes the threshold", which is exactly the moment worth reporting, and
     * nothing distinguished it from the same field changing because someone
     * called [animateTo]. A sheet that buzzes when it is opened programmatically
     * is worse than one that is silent, so the tick waited for this.
     *
     * Fed from the drag's `MutableInteractionSource` rather than from a flag the
     * gesture sets, because that is the one place the distinction already
     * exists: `anchoredDraggable` emits `DragInteraction.Start` for a finger and
     * nothing at all for an animation.
     *
     * Covers the sheet's own drag — the handle, the surface — and not a list
     * inside it scrolled past its top, which reaches the sheet through the
     * nested-scroll connection. That is a scroll being handed on rather than a
     * sheet being dragged, and it is quiet for the same reason a flick down a
     * list is.
     */
    var draggedByHand: Boolean by mutableStateOf(false)
        internal set

    /** True when any part of the sheet is on screen. */
    val isVisible: Boolean get() = currentDetent != SheetDetent.Hidden ||
        targetDetent != SheetDetent.Hidden

    /**
     * Distance from the top of the container to the top of the sheet, in pixels.
     *
     * `NaN` until the sheet has been laid out. For driving something else off
     * the sheet's position — fading a map's controls as it rises, say — prefer
     * [visibleHeight], which is the same information without the inversion.
     */
    val offset: Float get() = anchoredState.offset

    /** How much of the sheet is showing, in pixels. 0 when hidden. */
    val visibleHeight: Float
        get() {
            val o = anchoredState.offset
            return if (o.isNaN()) 0f else (containerHeight - o).coerceAtLeast(0f)
        }

    /** Progress from [currentDetent] to [targetDetent], 0 to 1. */
    val progress: Float get() = anchoredState.progress(currentDetent, targetDetent)

    /**
     * How much of the sheet is on screen, 0..1 — what a scrim behind it matches.
     *
     * Not [progress], which measures travel between two detents and so reads 1
     * whenever the sheet has settled anywhere, including hidden. This is
     * absolute: 0 with the sheet off the bottom of the window, 1 with it fully
     * out. A modal sheet's scrim is exactly this dark, so the two move as one.
     *
     * A sheet with an intermediate detent therefore dims proportionally on the
     * way to it, which is the honest reading of "as dark as the sheet is
     * present" — and modal sheets here are Hidden-to-Expanded by default, so it
     * is 0 or 1 unless a caller asks for more.
     */
    val visibleFraction: Float
        get() {
            val height = sheetHeight
            return if (height <= 0f) 0f else (visibleHeight / height).coerceIn(0f, 1f)
        }

    /**
     * Where the sheet should go once it has anchors.
     *
     * A screen that starts with its sheet open calls [animateTo] from a
     * `LaunchedEffect`, which runs before the first layout — so there are no
     * anchors yet and the request would be dropped. Holding it until anchors
     * arrive makes "open on arrival" work without every caller having to wait
     * for a measurement it should not have to know about.
     */
    private var pendingDetent: SheetDetent? by mutableStateOf(null)

    /** Animates to [detent]. Suspends until it arrives. */
    suspend fun animateTo(detent: SheetDetent) {
        if (detent !in allowedDetents) return
        if (!hasAnchors) {
            pendingDetent = detent
            return
        }
        anchoredState.animateTo(anchoredEquivalent(detent))
    }

    /** Jumps to [detent] with no animation. For restoring state, not for interaction. */
    suspend fun snapTo(detent: SheetDetent) {
        if (detent !in allowedDetents) return
        if (!hasAnchors) {
            pendingDetent = detent
            return
        }
        anchoredState.snapTo(anchoredEquivalent(detent))
    }

    /**
     * The anchored detent that sits where [detent] would, which may not be
     * [detent] itself.
     *
     * A detent resolving to an offset another one already has is dropped from the
     * anchors — deliberately, since two anchors at one offset make `settledValue`
     * ambiguous — and `anchoredDraggable` asked to animate to a detent it has no
     * anchor for goes to `NaN`: the sheet reports itself settled at a position
     * that is not a number, and every derived reading collapses with it. That is
     * reachable from ordinary code, not from a mistake: `expand()` asks for the
     * last detent in the list, and on a sheet whose peek anchor is its only
     * always-present content, `Expanded` and `peek` are the same offset.
     *
     * Same offset, so the sheet goes exactly where it was asked to go; it simply
     * arrives under the name the anchors kept.
     */
    private fun anchoredEquivalent(detent: SheetDetent): SheetDetent {
        val anchors = anchoredState.anchors
        if (!anchors.positionOf(detent).isNaN()) return detent
        val wanted = offsetOf(detent)
        if (wanted.isNaN()) return detent
        return anchors.closestAnchor(wanted) ?: detent
    }

    private val hasAnchors: Boolean get() = anchoredState.anchors.size > 0

    /** Animates to the tallest available detent. */
    suspend fun expand() {
        allowedDetents.lastOrNull { it != SheetDetent.Hidden }?.let { animateTo(it) }
    }

    /** Animates to the shortest detent that still shows something. */
    suspend fun partialExpand() {
        allowedDetents.firstOrNull { it != SheetDetent.Hidden }?.let { animateTo(it) }
    }

    /** Closes the sheet. No-op if [SheetDetent.Hidden] is not one of its detents. */
    suspend fun hide() = animateTo(SheetDetent.Hidden)

    /**
     * Opens it far enough to be worth looking at — the second detent if there is
     * one, otherwise the first visible one.
     */
    suspend fun show() {
        val visible = allowedDetents.filter { it != SheetDetent.Hidden }
        val target = visible.getOrNull(1) ?: visible.firstOrNull() ?: return
        animateTo(target)
    }

    /**
     * Recomputes anchor positions.
     *
     * Called from layout whenever the container, the content or the detent list
     * changes. Detents that resolve to the same position as one already placed
     * are dropped: two anchors at the same offset make `settledValue`
     * ambiguous, and the sheet ends up flickering between two names for one
     * position.
     */
    /**
     * How many times [updateAnchors] has done the work.
     *
     * An increment and a field, so the test suite can ask how often a sheet
     * rebuilds its anchors per frame without the question needing a build flag
     * or a listener. It exists because "twice a frame while sliding" was
     * invisible until something counted it.
     */
    internal var anchorRebuilds: Int = 0
        private set

    /**
     * The measurements the current anchors were built from, in whole pixels.
     *
     * Anchors depend on the container, the content, the peek anchor and the
     * detent list. They do **not** depend on where the sheet currently is — so a
     * sheet that is merely moving needs no new anchors, and rebuilding them
     * anyway cost a map, a pairwise scan of it and a fresh `DraggableAnchors`
     * twice on every frame of every slide.
     *
     * Rounded to whole pixels before comparison. [peekHeight] is the distance
     * between two positions in the root, and both of them travel with the sheet
     * — the distance is constant, but it arrives with sub-pixel jitter that an
     * exact comparison would mistake for a change.
     */
    private var anchoredFor: AnchorInputs? = null

    private data class AnchorInputs(
        val container: Int,
        val sheet: Int,
        val peek: Int,
        val detents: List<SheetDetent>,
        val density: Float,
        val fontScale: Float,
    )

    /**
     * How tall the sheet's surface has to be, which is **not** the container.
     *
     * The surface is a constant size and merely translated, because a node whose
     * size changes cannot keep the drawing it recorded last frame — see
     * `SheetFramePressureTest`. That much is settled. What was not settled is
     * *which* constant, and `containerHeight` is the wrong one: the requirement
     * is that the surface's bottom reaches the window's at every detent, and
     * the tallest detent is the one that asks for the most. A sheet whose
     * tallest detent shows 220dp of a 900dp window needs 220dp of surface and
     * was given 900.
     *
     * **A shadow's blur is linear in the area it covers.** Measured on
     * `Elevation.overlay`'s two layers over a 420dp-wide surface: 1.2ms at
     * 200dp tall, 3.0 at 450, 5.9 at 900, 9.7 at 1800 — so four fifths of that
     * sheet's shadow was being blurred into the region hanging below the
     * window, where nothing can see it. On the frame a modal sheet mounts, all
     * of it is: the sheet is parked at `Hidden`, entirely off-screen, and the
     * blur is the single largest cost on the frame. `SheetMountCostDiagnostic`
     * has the ladder.
     *
     * Plus [maxOvershoot], because a sheet pulled above its top detent moves
     * **up** — so the bottom edge would lift off the window's by exactly the
     * stretch, and a band of background would open under it. That was the
     * original bug the container-tall surface was written to fix, and it is the
     * one this must not reintroduce.
     *
     * **Nothing at all before there are anchors to ask.** That is the frame a
     * modal sheet mounts on, and the reason this is a layout-phase read rather
     * than a flag in composition: the surface measures before its content, so on
     * that frame nobody knows how tall the sheet will be, and a fallback to the
     * container would blur a window's worth of shadow for a sheet that is parked
     * off-screen. Zero draws nothing, which is what is wanted there, and costs
     * no recomposition to arrive at — a `derivedStateOf` flag was tried and
     * reaches composition a frame late, which puts the blur on the frame the
     * sheet starts moving instead of the cheap one before it.
     *
     * Measured end to end on `SheetOpenCostDiagnostic`, worst frame of an open:
     * **12.5ms to 9.2 empty, 13.2 to 9.1 with a line of text, 20.3 to 16.0 with
     * twelve list rows.**
     */
    internal val surfaceHeight: Float
        get() {
            val container = containerHeight
            if (container <= 0f) return 0f
            val lowest = anchoredState.anchors.let { anchors ->
                var min = Float.NaN
                for (index in 0 until anchors.size) {
                    val at = anchors.positionAt(index)
                    if (!at.isNaN() && (min.isNaN() || at < min)) min = at
                }
                min
            }
            if (lowest.isNaN()) return 0f
            return (container - lowest + maxOvershoot).coerceIn(0f, container)
        }

    internal fun updateAnchors(density: Density) {
        anchorDensity = density
        val inputs = AnchorInputs(
            container = containerHeight.roundToInt(),
            sheet = sheetHeight.roundToInt(),
            peek = peekHeight.roundToInt(),
            detents = allowedDetents,
            density = density.density,
            fontScale = density.fontScale,
        )
        if (inputs == anchoredFor) return
        anchoredFor = inputs

        anchorRebuilds++
        val positions = resolveAnchors(
            detents = allowedDetents,
            containerHeight = containerHeight,
            sheetHeight = sheetHeight,
            peekHeight = peekHeight,
            density = density,
        )
        if (positions.isEmpty()) return

        val anchors = DraggableAnchors {
            positions.forEach { (detent, offset) -> detent at offset }
        }

        // The target here only ever keeps the sheet where it already is.
        //
        // A pending detent used to be resolved *as* this target, and that is
        // why a sheet snapped open the first time and animated every time
        // after: `updateAnchors` moves to its target immediately. The first
        // open is the one with no anchors yet, so it was the one that snapped.
        // Delivering it is [deliverPending]'s job now, and that animates.
        val newTarget = when {
            // Keep the sheet where it is, if that detent survives.
            anchoredState.targetValue in positions -> anchoredState.targetValue
            // And if it did not, fall to the nearest surviving position rather
            // than the first in the list, which would slam a half-open sheet
            // shut when its detent list changed underneath it.
            else -> {
                val current = anchoredState.offset
                if (current.isNaN()) {
                    positions.keys.first()
                } else {
                    positions.minBy { abs(it.value - current) }.key
                }
            }
        }
        anchoredState.updateAnchors(anchors, newTarget)
    }

    /** True once anchors exist and a detent is waiting to be animated to. */
    internal val hasPendingDelivery: Boolean
        get() = pendingDetent != null && hasAnchors

    /**
     * Animates to a detent that was requested before there were anchors.
     *
     * The counterpart to [updateAnchors] not consuming it. A sheet told to open
     * from a `LaunchedEffect` asks before the first layout, so the request waits
     * here; delivering it through `animateTo` is what makes the first open look
     * like every one after it.
     */
    internal suspend fun deliverPending() {
        val target = pendingDetent ?: return
        if (!hasAnchors) return
        pendingDetent = null
        if (target in allowedDetents) anchoredState.animateTo(target)
    }

    /**
     * Settles at the detent a flick was **aimed at**, not the one it was nearest
     * when the finger left.
     *
     * Reported: a sheet with two detents — full height and closed — cannot be
     * closed by flicking, however hard, and has to be dragged more than half way
     * down instead. That is exactly what the arithmetic said. `settle` animates
     * to the state's current `targetValue`, which is chosen by **position**
     * alone against a 0.5 positional threshold; and the velocity never reached
     * it anyway, because [nestedScrollConnection] handed a downward fling
     * straight through `onPreFling` and then settled positionally in
     * `onPostFling`. Every flick on a settings sheet starts on its content, so
     * every flick on a settings sheet was settled with no velocity at all.
     *
     * So the velocity is projected into a landing position and the nearest
     * anchor to *that* wins. Below a real flick the projection collapses to
     * where the finger already is and the behaviour is what it always was —
     * which is what keeps a slow haul stopping at the detent it reached.
     *
     * ### Why the decay and not a threshold
     *
     * A velocity threshold gives "one detent per flick, however hard", which is
     * what the handle's own `flingBehavior` already does above Compose's
     * 125dp/s — and is the behaviour that cannot skip a middle detent on a
     * three-detent sheet. A projection has no such ceiling: a hard throw lands
     * past everything and closes the sheet outright, a soft one lands next door.
     *
     * ### What it cannot fix
     *
     * The **handle's** fling still goes through `AnchoredDraggableDefaults`,
     * which takes a positional threshold and a snap spec and no velocity
     * parameter. Routing it through here would mean an `animateTo` inside the
     * fling that the drag's own mutex is holding, which is a deadlock waiting to
     * be found rather than a fix. Dragging the handle is the one gesture where
     * the finger is on the sheet itself and the positional answer is the
     * intuitive one, so the ceiling stays there and is documented on
     * `SheetDefaults`.
     */
    /**
     * Whether the scrollable inside the sheet moved during the current gesture.
     *
     * Held on the state rather than on the connection because the connection is
     * rebuilt on every recomposition of `BottomSheet` — it is not `remember`ed,
     * unlike the wheel picker's — so a field on it would be forgotten mid-drag.
     *
     * Set in `onPostScroll`, read and cleared in `onPostFling`. What it is for is
     * in `onPostFling`'s KDoc.
     */
    private var listScrolled: Boolean = false

    /**
     * How much of the last dispatch's delta the child was offered.
     *
     * Scratch for the pair above: `onPreScroll` writes it, `onPostScroll` reads it
     * one call later in the same dispatch. Not snapshot state — nothing composes
     * from it and it changes every frame of every drag.
     */
    private var offeredToChild: Float = 0f

    internal suspend fun settleWhereAimed(
        velocity: Float,
        spec: AnimationSpec<Float>,
        minVelocity: Float,
    ) {
        val aimed = detentAimedAt(velocity, minVelocity)
        if (aimed == null) anchoredState.settle(spec) else anchoredState.animateTo(aimed)
    }

    /**
     * Which detent [velocity] is aimed at, or null when there is nothing to aim.
     *
     * Split from [settleWhereAimed] because it is the whole of the behaviour and
     * none of the machinery: the decision is arithmetic over the anchors and can
     * be asserted directly, where animating to it needs a frame clock and a
     * composition. `SheetSettleTest` reads this; the animation is Foundation's
     * and is covered by the sheet's own gesture tests.
     *
     * Null means "no opinion" — no anchors, nothing that counts as a flick, or a
     * projection that lands nowhere allowed — and the caller falls back to
     * settling by position, which is what the sheet has always done.
     *
     * ### [minVelocity] is the whole of "a flick has to be one"
     *
     * The first version of this had no floor at all, and said so in as many
     * words: the only guard was `velocity == 0f`, exact equality. That is a
     * threshold of one pixel per second, and it is why the sheet became too easy
     * to close.
     *
     * The reason it was left out was sound and incomplete. A *velocity* threshold
     * of the usual kind gives "one detent per flick however hard", which is the
     * behaviour the projection exists to replace. But a floor on whether the
     * projection is consulted at all is a different thing: above it the response
     * is still continuous in the throw and still skips detents, and below it a
     * finger coming to rest settles by position exactly as it always did.
     *
     * What made the absence bite is that a flick's velocity is not the only thing
     * carrying the sheet. `onPostScroll` has already dragged it part of the way,
     * so the projection only has to cover the remainder — measured in
     * `SheetFlickTest`, 450px/s was enough once the finger had carried the sheet
     * 40% of the way, against the ~1260px/s the same sheet needs from rest.
     */
    internal fun detentAimedAt(velocity: Float, minVelocity: Float): SheetDetent? {
        val from = anchoredState.offset
        val anchors = anchoredState.anchors
        if (from.isNaN() || anchors.size == 0) return null
        if (abs(velocity) < minVelocity) return null

        val projected = SheetFlingDecay.calculateTargetValue(from, velocity)
        var aimed: SheetDetent? = null
        var best = Float.MAX_VALUE
        for (index in 0 until anchors.size) {
            val at = anchors.positionAt(index)
            if (at.isNaN()) continue
            val detent = anchors.anchorAt(index) ?: continue
            if (detent !in allowedDetents) continue
            val distance = abs(at - projected)
            if (distance < best) {
                best = distance
                aimed = detent
            }
        }
        return aimed
    }

    /**
     * Hands scroll between the sheet and whatever scrolls inside it.
     *
     * The rule: dragging *down* moves the inner list until it reaches its top,
     * then moves the sheet. Dragging *up* moves the sheet until it is fully
     * expanded, then moves the list. Without it, a sheet with a `LazyColumn`
     * inside is either undraggable or unscrollable, depending on which
     * modifier won.
     *
     * @param settleSpec How the sheet finishes its travel once a fling hands
     *   over. **The same spec the sheet's own `flingBehavior` uses**, and it is
     *   a parameter for that reason: two settling policies on one sheet is a
     *   sheet that arrives differently depending on whether the gesture started
     *   on the handle or in the list.
     *
     *   It also has to be passed rather than assumed, because the alternative is
     *   the overload that takes a velocity — and that one *throws* on a state
     *   built without positional and velocity thresholds, which this one is.
     *   Scrolling anything inside a sheet crashed on it.
     */
    internal fun nestedScrollConnection(
        settleSpec: AnimationSpec<Float>,
        flickVelocity: Float,
    ): NestedScrollConnection =
        object : NestedScrollConnection {

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                // A stretch the finger opened closes before anything else moves.
                val paid = payBackOvershoot(delta)
                val offered = delta - paid
                // Dragging up, sheet not yet expanded: the sheet takes it first.
                val taken = if (offered < 0f) anchoredState.dispatchRawDelta(offered) else 0f
                // What the child is about to be offered, remembered so the
                // callback below can tell how much of it the child wanted. The
                // two are one dispatch, so nothing can arrive in between.
                offeredToChild = offered - taken
                return (paid + taken).toOffset()
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                // Dragging down and the list had nothing left to give: the sheet
                // takes the remainder and starts to close.
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                val offered = available.y
                // **Did the list move?** The difference between what the child
                // was offered and what it declined, which is how the sheet knows
                // whose gesture this is when the fling arrives. See `onPostFling`.
                if (offeredToChild != offered) listScrolled = true
                // ...as far as it is allowed to go, and no further. A sheet with
                // a form in it is dragged by its content as often as by its
                // handle, so the floor has to hold here too.
                val toSheet = roomBeforeFloor(offered)
                val taken = anchoredState.dispatchRawDelta(toSheet)
                val leftOver = offered - taken
                val stretched = if (leftOver > 0f) stretchDown(leftOver) else 0f
                return (taken + stretched).toOffset()
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                // A fling that started in the list belongs to the sheet only
                // while the sheet is the thing that has been moving.
                val offset = anchoredState.offset
                // The tallest detent that has an anchor. `detents.last { … }`
                // named one the filtered anchor set may not carry, which reads
                // back as `NaN` — and threw outright on a sheet whose only
                // allowed position is hidden. The guard below already treats
                // `NaN` as "cannot tell", so an absent anchor lands there.
                val expanded = allowedDetents.lastOrNull { it != SheetDetent.Hidden }
                val expandedOffset = expanded
                    ?.let { anchoredState.anchors.positionOf(it) }
                    ?: Float.NaN
                return if (
                    available.y < 0 &&
                    !offset.isNaN() &&
                    !expandedOffset.isNaN() &&
                    offset > expandedOffset
                ) {
                    settleWhereAimed(available.y, settleSpec, flickVelocity)
                    available
                } else {
                    Velocity.Zero
                }
            }

            /**
             * What the inner scrollable's fling did not want.
             *
             * **A fling the list spent itself on is not a sheet flick**, and this
             * is where that had to be said. The callback above carries the rule
             * and four guards; this one, which is where *every downward flick
             * actually lands*, had none — it projected whatever velocity arrived,
             * from whoever.
             *
             * The velocity that arrives is large, too. Compose's fling behaviour
             * cancels its decay on the first frame a delta is not fully consumed
             * and hands up the *instantaneous* velocity, and this connection
             * declines everything that is not `UserInput` — so that frame
             * consumes nothing and a list passes on very nearly the whole throw.
             * `WheelPicker`'s containment records the same handoff from the other
             * side and names the sheet as what it leaked into.
             *
             * So [listScrolled] decides, and **not [consumed]**, which was the
             * first attempt and does not work: the decay loses a frame's worth of
             * velocity before it cancels, about 6% of the throw, so `consumed` is
             * non-zero even when the list absorbed nothing at all. It failed the
             * deliberate 3600px/s flick, which is the gesture this whole path
             * exists for.
             *
             * What separates the two cases is whether the **list moved**. A reader
             * who scrolled a list and let go was moving the list, and its
             * leftovers are not an instruction to the sheet. A reader whose list
             * was already at its top never moved it, and their flick is the
             * sheet's to answer — which is exactly the case the projection was
             * added for.
             */
            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity,
            ): Velocity {
                val wasTheList = listScrolled
                // The gesture is over either way, and `onDragStopped` always
                // reaches here — with a zero velocity if the finger simply
                // stopped — so this is the reliable place to forget it.
                listScrolled = false
                if (wasTheList) {
                    anchoredState.settle(settleSpec)
                } else {
                    settleWhereAimed(available.y, settleSpec, flickVelocity)
                }
                // **And the stretch this path opened has to come back.** A drag
                // on the *content* reaches `stretchDown` through `onPostScroll`
                // above, and until this line the only `releaseOvershoot` in the
                // library was `SheetOverscroll`'s — which is the handle's path.
                // So an undismissable sheet pushed below its floor by its own
                // list stayed there after the finger lifted: no detent had
                // changed, so nothing settled it, and the stretch is what the
                // layout subtracts. The docs promise the opposite in as many
                // words — *"a drag to the bottom, which springs back instead of
                // closing"*.
                //
                // After the settle rather than before it, for the reason
                // `SheetOverscroll.applyToFling` gives: a flick that carries the
                // sheet somewhere else should not be fighting a stretch
                // unwinding underneath it.
                releaseOvershoot(settleSpec)
                return available
            }

            private fun Float.toOffset() = Offset(0f, this)
        }
}

/**
 * Creates a [SheetState].
 *
 * @param detents Where the sheet may rest. Order matters: [SheetState.expand]
 *   goes to the last, [SheetState.partialExpand] to the first visible one.
 * @param confirmDetentChange Which of [detents] the sheet may rest at. Return
 *   false and that detent gets no anchor, so there is nothing there for a drag
 *   to reach — the sheet meets the end of its travel rather than following the
 *   finger and springing back. The detent the sheet is currently settled at is
 *   always kept regardless, because a sheet with no anchor for its own position
 *   has no position at all.
 *
 *   The usual case is refusing [SheetDetent.Hidden] on a sheet the user must
 *   deal with, and it composes with `initialDetent = SheetDetent.Hidden`: the
 *   sheet opens from hidden because that is where it already is, and once it has
 *   settled anywhere else, hidden stops being somewhere it can be dragged back
 *   to. But see [ModalBottomSheet] before reaching for any of this — a sheet
 *   that cannot be dismissed is a trap and is nearly always the wrong answer.
 */
@Composable
fun rememberSheetState(
    detents: List<SheetDetent> = DefaultSheetDetents,
    initialDetent: SheetDetent = detents.firstDetent(),
    confirmDetentChange: (SheetDetent) -> Boolean = { true },
): SheetState {
    val state = remember {
        SheetState(detents, initialDetent, confirmDetentChange)
    }
    // The list is re-read every composition, so a screen can swap detents as its
    // content changes without rebuilding the state and losing the position.
    state.detents = detents
    return state
}

/**
 * Turns a list of detents into anchor offsets, in pixels from the top.
 *
 * Pure, and tested directly, because this is where a sheet's positions actually
 * come from and the failures are arithmetic rather than visual — a detent
 * resolving off-screen, or two detents landing on the same offset.
 *
 * Two things happen here beyond the arithmetic:
 *
 * - **The measured peek wins over the detent's fallback.** [SheetDetent.peek]
 *   carries a fixed height so a sheet has somewhere to sit before its header has
 *   been measured; once it has, the real height replaces it.
 * - **Duplicates are dropped.** Two anchors at the same offset make
 *   `settledValue` ambiguous, and the sheet flickers between two names for one
 *   position. It happens easily: on a short screen `Half` and `Expanded` can
 *   both resolve to the full container height.
 */
internal fun resolveAnchors(
    detents: List<SheetDetent>,
    containerHeight: Float,
    sheetHeight: Float,
    peekHeight: Float,
    density: Density,
): LinkedHashMap<SheetDetent, Float> {
    val positions = LinkedHashMap<SheetDetent, Float>()
    if (containerHeight <= 0f) return positions

    for (detent in detents) {
        val offset = detentOffset(
            detent = detent,
            containerHeight = containerHeight,
            sheetHeight = sheetHeight,
            peekHeight = peekHeight,
            density = density,
        )
        if (positions.values.none { abs(it - offset) < 0.5f }) {
            positions[detent] = offset
        }
    }
    return positions
}

/**
 * Where one detent puts the sheet's top edge, in pixels from the container's top.
 *
 * Pulled out of [resolveAnchors] so that [SheetState.offsetOf] can ask the same
 * question about a detent the anchors do not have — which is the ordinary state
 * of a detent whose offset another one got to first, since a duplicate is dropped
 * above. Pure, and takes everything it needs, so the two answers cannot drift.
 */
internal fun detentOffset(
    detent: SheetDetent,
    containerHeight: Float,
    sheetHeight: Float,
    peekHeight: Float,
    density: Density,
): Float {
    val visible = if (detent.id == PeekDetentId && peekHeight > 0f) {
        peekHeight
    } else {
        with(density) { detent.resolve(this, containerHeight, sheetHeight) }
    }
    // **Nothing reaches the very top.** A sheet whose top edge lands on
    // pixel zero has no page above it for its rounded corners to read
    // against, and on a phone it also puts the drag handle and the header
    // under the status bar — which is what was reported. `SheetTopGap` is
    // the same 12dp the backdrop already insets a receding page by, so a
    // sheet at full height and a page behind a sheet leave the same margin.
    //
    // Applied here rather than inside `Full` and `Expanded` because it is a
    // fact about how far a sheet may travel, not about what either detent
    // means: `Full` still resolves to "the whole container" and says so, and
    // a caller's own `fraction(1f)` gets the same treatment without knowing
    // about it.
    //
    // A container with no room for the gap does not get one. `minOf(gap,
    // container)` is the obvious guard and it is the wrong one: on a 6px
    // container it makes the gap the whole container, so every detent
    // resolves to "entirely hidden" and the sheet has nowhere to be. A
    // container that small is a measurement in progress rather than a
    // window, and leaving it exactly the offsets it had is what does least
    // harm to it.
    val gap = with(density) { SheetTopGap.toPx() }
    val raw = containerHeight - visible.coerceIn(0f, containerHeight)
    return if (containerHeight > gap) raw.coerceAtLeast(gap) else raw
}

/**
 * The sheet the content is inside, or null outside one.
 *
 * Lets content react to its own sheet — collapsing a header as it expands,
 * disabling a control while it is closing — without the screen threading the
 * state down by hand.
 */
val LocalSheetState = staticCompositionLocalOf<SheetState?> { null }

/**
 * Marks the part of a sheet that [SheetDetent.peek] shows.
 *
 * Put it on the header. The sheet then peeks exactly far enough to show that,
 * whatever it turns out to be — which is what a fixed peek height cannot do,
 * since a header's height changes with the user's font scale and with what is
 * actually in it. A stop header and a trip header are different heights, and
 * both change again at 200% type.
 *
 * Inert outside a sheet, and inert if the sheet has no `peek` detent.
 */
@Composable
fun Modifier.sheetPeekAnchor(): Modifier {
    val state = LocalSheetState.current ?: return this
    val density = LocalDensity.current
    return onGloballyPositioned { coordinates ->
        state.peekAnchorCoordinates = coordinates
        state.measurePeek()
        state.updateAnchors(density)
    }
}

/** The orientation every sheet in this library drags along. */
internal val SheetOrientation = Orientation.Vertical

/**
 * How far above its top detent a sheet may be pulled, as a share of the window.
 *
 * A twelfth. Chosen against the two failures either side of it: much less and
 * the stretch is indistinguishable from the rigid stop it replaces, much more
 * and the gap reads as a detent the sheet forgot to settle at.
 */
private const val OvershootShare = 1f / 12f

/**
 * What both an empty [SheetState] and an emptied one say.
 *
 * One message, because they are the same mistake arriving by two routes and a
 * caller should not have to recognise two.
 */
private const val EmptyDetents: String =
    "SheetState was given no detents. A sheet needs at least one position it can rest " +
        "at, and `detents` is often a filtered list — `rememberSheetState(detents = " +
        "all.filter { … })` — so an empty one usually means the filter matched nothing. " +
        "Keep SheetDetent.Hidden if nothing else applies."

/**
 * [List.first] with that message instead of `NoSuchElementException`.
 *
 * `initialDetent` defaults to `detents.first()`, and a default argument is
 * evaluated at the **call site** — before the function body, and so before the
 * `require` inside [SheetState] that exists to catch exactly this. An empty list
 * therefore failed with "List is empty." and a stack trace pointing at
 * `rememberSheetState`, which is true and useless.
 */
private fun List<SheetDetent>.firstDetent(): SheetDetent =
    firstOrNull() ?: throw IllegalArgumentException(EmptyDetents)

/**
 * How far a flick carries a sheet past where the finger left it.
 *
 * A plain exponential decay, and the friction is the whole tuning. At the
 * default of 1 a 3000px/s throw — a brisk flick on a phone — projects about
 * 700px, which closes a full-height sheet on most devices and leaves a gentle
 * 500px/s one, projecting about 120px, exactly where it was. Lower friction
 * makes flicks carry further; there is no threshold anywhere, so the response is
 * continuous in the throw rather than stepped.
 *
 * Process-wide rather than per-sheet: it is a fact about how a thrown thing
 * slows down, not a property of any one sheet, and building one per state would
 * be an allocation on every recomposition of every sheet in an app.
 */
private val SheetFlingDecay: DecayAnimationSpec<Float> = exponentialDecay()

/**
 * How fast a gesture has to be leaving the glass to count as a flick.
 *
 * Per second, so it is a velocity written as the distance one second of it would
 * cover. Below this a release settles by `SheetDefaults.PositionalThreshold`
 * exactly as it always has; above it the velocity is projected and the sheet goes
 * where the throw was aimed, skipping detents on the way if the throw was hard
 * enough. See [SheetState.detentAimedAt].
 *
 * **500dp/s, and the number is a correction rather than a first guess.** The
 * projection shipped with no floor at all — the only guard was an exact comparison
 * against zero — and a sheet became closable by a gesture that was barely one.
 * Reported as flicking being too sensitive, *"especially if I've just scrolled to
 * the top of a list within the sheet"*, which is the case where the drag has
 * already carried the sheet most of the way and the velocity only has to cover
 * what is left.
 *
 * Three measurements put it here. `SheetFlickTest` records 225dp/s closing a sheet
 * the finger had already carried 40% of the way, which had to stop — that pace is
 * now the test's slow control. The same file's deliberate flick is 1800dp/s and
 * has to go on working. And Compose's own `AnchoredDraggableMinFlingVelocity`, the
 * bar for "this was thrown rather than released", is 125dp/s — so this is four
 * times what the platform calls a fling, which is about the difference between a
 * finger leaving the glass and a finger throwing something.
 *
 * Here rather than on `SheetDefaults`, for the reason [SheetTopGap] is: it is a
 * fact about how a hand moves, not a number a brand restyles, and the literals
 * ratchet in `check-components.py` counts the ones that sit in a `Defaults`
 * object as knobs somebody is expected to reach for.
 */
internal val SheetFlickVelocity: Dp = 500.dp

/**
 * How far short of the screen's edge a sheet stops.
 *
 * Two reasons, and the second is the one that was reported. A sheet whose top
 * edge is at pixel zero has nothing above it for its rounded corners to read
 * against, so the corners stop being corners; and on a phone it puts the drag
 * handle and the header under the status bar and the notch.
 *
 * The chrome's own inset is the other half of that second one and is applied
 * separately, in `BottomSheet` — this gap is smaller than a status bar and is
 * not trying to clear it. What it does is leave the sheet looking like a sheet.
 *
 * 12dp, which is `ComponentDefaults.backdropInset`: a receding page behind a
 * sheet already stops that far short of the window on all four sides, so a
 * full-height sheet and the page behind it now leave the same margin. Not read
 * from the theme, because anchors are resolved against a `Density` and nothing
 * else — a sheet's reach is not a thing a brand restyles.
 */
internal val SheetTopGap: Dp = 12.dp
