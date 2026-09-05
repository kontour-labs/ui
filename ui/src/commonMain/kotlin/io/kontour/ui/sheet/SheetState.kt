package io.kontour.ui.sheet

import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animate
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Velocity
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
        require(detents.isNotEmpty()) { "A sheet needs at least one detent" }
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
    var detents: List<SheetDetent> by mutableStateOf(detents)

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
     */
    internal var overshoot by mutableFloatStateOf(0f)

    /**
     * Whether there is anything above the top detent to stretch into.
     *
     * A sheet already as tall as its container has nowhere to go, and stretching
     * one would pull its top edge off the screen and leave a band of background
     * under it — which is the one thing a bottom sheet must never show.
     */
    internal val canOvershoot: Boolean
        get() {
            val tallest = allowedDetents.minOfOrNull { detent ->
                anchoredState.anchors.positionOf(detent)
            } ?: return false
            return !tallest.isNaN() && tallest > 0.5f
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
            val lowest = allowedDetents
                .filter { it != SheetDetent.Hidden }
                .maxOfOrNull { anchoredState.anchors.positionOf(it) }
                ?: return Float.NaN
            return if (lowest.isNaN()) Float.NaN else lowest
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
     */
    internal val maxOvershoot: Float get() = containerHeight * OvershootShare

    /**
     * Takes [by] pixels of upward pull and returns how much of it was absorbed.
     *
     * Diminishing returns rather than a shorter track: each pixel of finger
     * moves the sheet less the further it has already been pulled, so the edge
     * feels like it is resisting. A linear stretch with a hard stop is the same
     * rigid boundary moved somewhere else.
     */
    internal fun stretch(by: Float): Float {
        val max = maxOvershoot
        if (max <= 0f || by <= 0f) return 0f
        val resistance = 1f - (abs(overshoot) / max).coerceIn(0f, 1f)
        val gained = by * resistance
        overshoot = (overshoot + gained).coerceIn(-max, max)
        return gained
    }

    /**
     * The same, downward: [by] pixels of push below [dragFloor].
     *
     * [overshoot] is signed, and the layout subtracts it — so a negative one
     * moves the sheet down by exactly as much as a positive one moves it up, and
     * both ends spring back through the same [releaseOvershoot].
     */
    internal fun stretchDown(by: Float): Float {
        val max = maxOvershoot
        if (max <= 0f || by <= 0f) return 0f
        val resistance = 1f - (abs(overshoot) / max).coerceIn(0f, 1f)
        val gained = by * resistance
        overshoot = (overshoot - gained).coerceIn(-max, max)
        return gained
    }

    /**
     * Closes an open stretch at full rate, and returns how much of [by] it used.
     *
     * A finger coming back closes the gap it opened before the sheet itself
     * starts moving again; the other order slides the sheet away with the gap
     * still open, and one gesture produces two motions. Signed the way the
     * finger is: positive is downward.
     */
    internal fun payBackOvershoot(by: Float): Float {
        if (overshoot == 0f || by == 0f) return 0f
        // Same sign means the finger is opening the gap wider, not closing it.
        if ((overshoot > 0f) == (by < 0f)) return 0f
        val paid = minOf(abs(overshoot), abs(by))
        overshoot += paid * if (overshoot > 0f) -1f else 1f
        return paid * if (by > 0f) 1f else -1f
    }

    /** Springs the stretch back to nothing. */
    internal suspend fun releaseOvershoot(spec: AnimationSpec<Float>) {
        if (overshoot == 0f) return
        animate(
            initialValue = overshoot,
            targetValue = 0f,
            animationSpec = spec,
        ) { value, _ -> overshoot = value }
    }

    /** The content's own full height in pixels, for [SheetDetent.Expanded]. */
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
        get() = if (peekAnchorBottomInRoot.isNaN() || sheetTopInRoot.isNaN()) {
            0f
        } else {
            (peekAnchorBottomInRoot - sheetTopInRoot).coerceAtLeast(0f)
        }

    /**
     * The two measurements the peek is derived from, kept separately on purpose.
     *
     * `onGloballyPositioned` fires children-first, so the anchor reports before
     * the sheet it is inside, and it only fires again when a position actually
     * changes. Computing the difference at the anchor's callback would find the
     * sheet's top still unset and never get a second chance — which is how the
     * peek silently stayed at its fallback. Storing both and deriving means
     * whichever fires last completes the pair.
     */
    internal var peekAnchorBottomInRoot by mutableFloatStateOf(Float.NaN)
    internal var sheetTopInRoot by mutableFloatStateOf(Float.NaN)

    /** Where the sheet has settled. Equals [targetDetent] once it stops moving. */
    val currentDetent: SheetDetent get() = anchoredState.settledValue

    /** Where it is heading. Changes the instant a drag passes the threshold. */
    val targetDetent: SheetDetent get() = anchoredState.targetValue

    /** True while it is animating or being dragged. */
    val isMoving: Boolean get() = anchoredState.isAnimationRunning

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
        anchoredState.animateTo(detent)
    }

    /** Jumps to [detent] with no animation. For restoring state, not for interaction. */
    suspend fun snapTo(detent: SheetDetent) {
        if (detent !in allowedDetents) return
        if (!hasAnchors) {
            pendingDetent = detent
            return
        }
        anchoredState.snapTo(detent)
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

    internal fun updateAnchors(density: Density) {
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
                    anchoredState.settle(settleSpec)
                    available
                } else {
                    Velocity.Zero
                }
            }

            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity,
            ): Velocity {
                anchoredState.settle(settleSpec)
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
    initialDetent: SheetDetent = detents.first(),
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
        val visible = if (detent.id == PeekDetentId && peekHeight > 0f) {
            peekHeight
        } else {
            with(density) { detent.resolve(this, containerHeight, sheetHeight) }
        }
        val offset = containerHeight - visible.coerceIn(0f, containerHeight)
        if (positions.values.none { abs(it - offset) < 0.5f }) {
            positions[detent] = offset
        }
    }
    return positions
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
        state.peekAnchorBottomInRoot =
            coordinates.positionInRoot().y + coordinates.size.height
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
