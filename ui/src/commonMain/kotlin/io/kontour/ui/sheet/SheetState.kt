package io.kontour.ui.sheet

import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.snapTo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
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
 *     SheetHeader(Modifier.sheetPeekAnchor()) {
           +"Perth Underground"
       }
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

    internal val anchoredState = AnchoredDraggableState(
        initialValue = initialDetent,
        confirmValueChange = { confirmDetentChange(it) },
    )

    /** Container height in pixels. Set by the sheet's layout. */
    internal var containerHeight by mutableFloatStateOf(0f)

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
        if (detent !in detents) return
        if (!hasAnchors) {
            pendingDetent = detent
            return
        }
        anchoredState.animateTo(detent)
    }

    /** Jumps to [detent] with no animation. For restoring state, not for interaction. */
    suspend fun snapTo(detent: SheetDetent) {
        if (detent !in detents) return
        if (!hasAnchors) {
            pendingDetent = detent
            return
        }
        anchoredState.snapTo(detent)
    }

    private val hasAnchors: Boolean get() = anchoredState.anchors.size > 0

    /** Animates to the tallest available detent. */
    suspend fun expand() {
        detents.lastOrNull { it != SheetDetent.Hidden }?.let { animateTo(it) }
    }

    /** Animates to the shortest detent that still shows something. */
    suspend fun partialExpand() {
        detents.firstOrNull { it != SheetDetent.Hidden }?.let { animateTo(it) }
    }

    /** Closes the sheet. No-op if [SheetDetent.Hidden] is not one of its detents. */
    suspend fun hide() = animateTo(SheetDetent.Hidden)

    /**
     * Opens it far enough to be worth looking at — the second detent if there is
     * one, otherwise the first visible one.
     */
    suspend fun show() {
        val visible = detents.filter { it != SheetDetent.Hidden }
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
    internal fun updateAnchors(density: Density) {
        val positions = resolveAnchors(
            detents = detents,
            containerHeight = containerHeight,
            sheetHeight = sheetHeight,
            peekHeight = peekHeight,
            density = density,
        )
        if (positions.isEmpty()) return

        val anchors = DraggableAnchors {
            positions.forEach { (detent, offset) -> detent at offset }
        }

        // One call, one target. `updateAnchors` ignores a target when the
        // anchors it is given are unchanged, so anything decided afterwards is
        // silently dropped — which is how a sheet asked to open before layout
        // ended up staying shut.
        val pending = pendingDetent
        val newTarget = when {
            // A detent requested before there were anchors to move to.
            pending != null && pending in positions -> pending
            // Otherwise keep the sheet where it is, if that detent survives.
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
        if (pending != null && pending in positions) pendingDetent = null
        anchoredState.updateAnchors(anchors, newTarget)
    }

    /**
     * Hands scroll between the sheet and whatever scrolls inside it.
     *
     * The rule: dragging *down* moves the inner list until it reaches its top,
     * then moves the sheet. Dragging *up* moves the sheet until it is fully
     * expanded, then moves the list. Without it, a sheet with a `LazyColumn`
     * inside is either undraggable or unscrollable, depending on which
     * modifier won.
     */
    internal fun nestedScrollConnection(): NestedScrollConnection =
        object : NestedScrollConnection {

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                // Dragging up, sheet not yet expanded: the sheet takes it first.
                return if (delta < 0 && source == NestedScrollSource.UserInput) {
                    anchoredState.dispatchRawDelta(delta).toOffset()
                } else {
                    Offset.Zero
                }
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                // Dragging down and the list had nothing left to give: the sheet
                // takes the remainder and starts to close.
                return if (source == NestedScrollSource.UserInput) {
                    anchoredState.dispatchRawDelta(available.y).toOffset()
                } else {
                    Offset.Zero
                }
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                // A fling that started in the list belongs to the sheet only
                // while the sheet is the thing that has been moving.
                val offset = anchoredState.offset
                val expandedOffset = anchoredState.anchors
                    .positionOf(detents.last { it != SheetDetent.Hidden })
                return if (
                    available.y < 0 &&
                    !offset.isNaN() &&
                    !expandedOffset.isNaN() &&
                    offset > expandedOffset
                ) {
                    anchoredState.settle(available.y)
                    available
                } else {
                    Velocity.Zero
                }
            }

            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity,
            ): Velocity {
                anchoredState.settle(available.y)
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
 * @param confirmDetentChange Return false to refuse a position. The usual case
 *   is refusing [SheetDetent.Hidden] on a sheet the user must deal with — but
 *   see [ModalBottomSheet] before reaching for it, since a sheet that cannot be
 *   dismissed is a trap and is nearly always the wrong answer.
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
