package io.kontour.ui.nav

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import io.kontour.ui.components.selection.SliderDefaults
import io.kontour.ui.foundation.IndicatorReach
import io.kontour.ui.foundation.SelectionIndicatorState
import io.kontour.ui.interaction.DetentTicker
import io.kontour.ui.interaction.DragClaim
import io.kontour.ui.interaction.FeedbackIntent
import io.kontour.ui.interaction.horizontalDragOwning
import io.kontour.ui.interaction.rememberDetentTicker
import io.kontour.ui.interaction.verticalDragOwning
import kotlinx.coroutines.CoroutineScope
import kotlin.math.abs

/**
 * Whether a selection marker — a [NavBar]'s, a [NavRail]'s, a [TabBar]'s — can
 * be dragged from one destination to another, and when a drag chooses one.
 *
 * Only while the row the marker is in has nothing to scroll. A drag along a
 * scroller is the one gesture it cannot share, so a rail whose destinations run
 * past its foot, or a scrollable tab bar whose tabs run past its edge, scrolls
 * instead, as it did before. The question is asked as the finger goes down, so a
 * window that grows or shrinks, or a destination added, answers it again.
 */
enum class NavMarkerDrag {
    /** The marker cannot be dragged: a destination is chosen by a tap alone. */
    Off,

    /**
     * The marker follows the finger, and the destination under it when it lifts
     * is chosen, once. The default: a destination is a page, and each one passed
     * over is not built and thrown away.
     */
    SelectOnRelease,

    /**
     * The marker follows the finger, and each destination is chosen as the
     * marker reaches it, for pages cheap enough to scrub across.
     */
    SelectWhileDragging,
}

/** A destination the marker can be dragged to. Written in composition's apply phase, read by the gesture. */
internal class DragTarget {
    var coordinates: LayoutCoordinates? = null
    var onClick: () -> Unit = {}
    var enabled: Boolean = true
    var selected: Boolean = false

    /**
     * A group, whose [onClick] opens its menu rather than going anywhere: the
     * marker can be dragged to it and left there, and it is never chosen on
     * the way past.
     */
    var opensMenu: Boolean = false
}

/** What a destination needs to start dragging the marker. Stable per row. */
@Immutable
internal data class DragWiring(val drag: MarkerDragState, val ticker: DetentTicker, val scope: CoroutineScope)

/**
 * The drag a row of destinations shares, told how it is set up this composition:
 * the [mode], the [indicator] it leans, and the [scroll] it must not fight.
 * Null wiring when the drag is [NavMarkerDrag.Off].
 */
@Composable
internal fun rememberMarkerDrag(
    vertical: Boolean,
    mode: NavMarkerDrag,
    indicator: SelectionIndicatorState,
    scroll: ScrollableState?,
): Pair<MarkerDragState, DragWiring?> {
    val drag = remember(vertical) { MarkerDragState(vertical) }
    val ticker = rememberDetentTicker(FeedbackIntent.Snap)
    val scope = rememberCoroutineScope()
    // The marker's lean toward the finger is the indicator's own, so it
    // stretches as it leans, as a press on another destination makes it — see
    // `SelectionIndicatorBox`. The drag tells it where the finger is.
    drag.indicator = indicator
    drag.scroll = scroll
    drag.selectsWhileDragging = mode == NavMarkerDrag.SelectWhileDragging
    val wiring = if (mode != NavMarkerDrag.Off) remember(drag, ticker, scope) { DragWiring(drag, ticker, scope) } else null
    return drag to wiring
}

/**
 * The marker's drag, started from this destination.
 *
 * On the destination rather than on the row: a gesture stays with the node it
 * went down on however far the finger then goes, so the drag carries on across
 * the others all the same — and the row is left with no pointer input of its
 * own, so a press *between* destinations still falls through to the surface's
 * blocker rather than stopping on the row and reaching the page. Along the row
 * only: a tap stays a tap, and a drag across a vertical rail is left to whatever
 * else wants it — a modal rail's dismissal. A slot — a search — has none, and
 * keeps its own gestures.
 *
 * **Declined while the row can scroll**, which is asked as the finger goes down
 * rather than kept in composition: the answer changes with every window resize
 * and every destination added, and read here it costs nothing while it does. A
 * declined press is left wholly alone, so the scroller under it has the drag as
 * though this were not here.
 */
@Composable
internal fun Modifier.markerDrag(wiring: DragWiring, target: DragTarget, enabled: Boolean): Modifier {
    val drag = wiring.drag
    val ticker = wiring.ticker
    return if (drag.vertical) {
        verticalDragOwning(
            enabled = enabled,
            interactionSource = null,
            scope = wiring.scope,
            claimsOn = DragClaim.Direction,
            accepts = { drag.fits() },
            onStart = { at -> drag.begin(drag.inRow(target, at), ticker) },
            onDelta = { delta -> drag.move(delta, ticker) },
            onRelease = { drag.released = true },
            onEnd = { drag.end(ticker) },
        )
    } else {
        horizontalDragOwning(
            enabled = enabled,
            interactionSource = null,
            scope = wiring.scope,
            claimsOn = DragClaim.Direction,
            accepts = { drag.fits() },
            onStart = { at -> drag.begin(drag.inRow(target, at), ticker) },
            onDelta = { delta -> drag.move(delta, ticker) },
            onRelease = { drag.released = true },
            onEnd = { drag.end(ticker) },
        )
    }
}

/**
 * A row's marker, dragged: which destination it is over and where the finger
 * is, as a segmented control's thumb is. Across a bar or a tab bar, and down a
 * rail when [vertical].
 *
 * **By default the choice waits for the finger to lift.** A segmented control
 * changes its value at every boundary it crosses, because a value is cheap to
 * change and change back. A destination is a page: choosing each one the finger
 * passed over would build and throw away every screen on the way. So the
 * marker, and the colours that go with it, follow the finger at once, a tick
 * marks each crossing, and the destination under the finger when it lifts has
 * its `onClick` run, once. A drag that ends where it started, or is taken away
 * by something else, changes nothing.
 *
 * **Or as the marker reaches each one**, under [NavMarkerDrag.SelectWhileDragging]
 * — asked for as an option: "when you drag the navbar selection indicator, it
 * can switch tabs as you're dragging". For pages that are cheap to show, where
 * scrubbing across them is the point: each destination's `onClick` runs as the
 * marker reaches it, once, and the lift chooses nothing more.
 *
 * **A group is somewhere to drag it too.** Let go over one and its menu opens,
 * inviting a pick, and the group keeps the marker for as long as the menu is
 * open; closed without a pick, the marker goes back to where it was. Asked
 * for: *"when you drag the selection indicator onto an option that's a
 * dropdown, the dropdown expands, inviting the user to pick an option"*. Only
 * on the lift, under either rule: a menu that opened under a finger on its way
 * past would be a menu in the way of the next destination.
 *
 * **Only while the row cannot scroll.** It was the bar's alone once, because a
 * rail scrolls, and a drag along a scroller is the one gesture it cannot share.
 * But a rail of three destinations on a tall window does not scroll, and nor
 * does a scrollable tab bar whose tabs fit: *"any use where the underlying
 * composable can't be scrolled in the given container"*. So [fits] is asked as
 * each drag begins, of the [scroll] the row is in.
 */
@Stable
internal class MarkerDragState(val vertical: Boolean) {
    /** The destinations, by key. Not state: read when a finger moves, never in composition. */
    val targets = LinkedHashMap<Any, DragTarget>()

    /** The row's coordinates: the finger's, and the marker's before its lean. */
    var row: LayoutCoordinates? = null

    /** The scroller the row is in, or null for a row that never scrolls. Read at the press. */
    var scroll: ScrollableState? = null

    /**
     * Run once a drag has chosen a destination, after the choice: a modal rail
     * closes then, rather than as each destination is reached on the way.
     */
    var afterChoice: (() -> Unit)? = null

    /** Whether the row has nothing to scroll, so a drag along it is the marker's. */
    fun fits(): Boolean {
        val scroll = scroll ?: return true
        return !scroll.canScrollForward && !scroll.canScrollBackward
    }

    /** The destination under the finger while it drags the marker, or null. */
    var over: Any? by mutableStateOf(null)
        private set

    /** Whether a finger is dragging the marker. */
    var dragging: Boolean by mutableStateOf(false)
        private set

    /** The drag ended with the finger lifting, rather than being taken away. */
    var released: Boolean = false

    /** Whether each destination is chosen as the marker reaches it, rather than on the lift. */
    var selectsWhileDragging: Boolean = false

    /**
     * The destination last chosen during this drag, by [selectsWhileDragging]:
     * the one that was current when it began, and then each one reached. Its
     * own record rather than each target's `selected`, which catches up a
     * composition later — a finger back over the first destination in the same
     * frame would otherwise find it still marked as current and not choose it.
     */
    private var chosen: Any? = null

    /** Where this drag began, so [afterChoice] runs only for one that went somewhere. */
    private var startedOn: Any? = null

    /**
     * Where the finger is along the row, or NaN before the first drag. Kept
     * after it lifts, so the lean relaxes out of where it was rather than
     * losing the number.
     */
    private var fingerAt by mutableFloatStateOf(Float.NaN)

    /**
     * The row's indicator, told where the finger is so the marker leans and
     * stretches toward it, by [SliderDefaults.DetentPull] of the way: far enough
     * to read as straining toward the next destination, never so far that it
     * is closer to that one than to its own.
     */
    var indicator: SelectionIndicatorState? = null

    private fun reachFinger() {
        val state = indicator ?: return
        // The drag's now, not the press it started as: a press that the drag
        // takes over is cancelled, and must not take the lean with it.
        state.reachKey = null
        state.reach = when {
            fingerAt.isNaN() -> null
            vertical -> IndicatorReach(Offset(Float.NaN, fingerAt), SliderDefaults.DetentPull)
            else -> IndicatorReach(Offset(fingerAt, Float.NaN), SliderDefaults.DetentPull)
        }
    }

    /**
     * The group a drag was let go over, which keeps the marker while its menu
     * is open. Cleared by the group, with [letGo], once the menu has closed.
     */
    var holding: Any? by mutableStateOf(null)
        private set

    /** The group whose menu has closed gives the marker back. */
    fun letGo(key: Any) {
        if (holding == key) holding = null
    }

    fun marks(key: Any, selected: Boolean): Boolean = over?.let { it == key } ?: holding?.let { it == key } ?: selected

    private fun boundsOf(target: DragTarget): Rect? {
        val row = row ?: return null
        val at = target.coordinates ?: return null
        if (!row.isAttached || !at.isAttached) return null
        return row.localBoundingBoxOf(at, clipBounds = false)
    }

    private val Rect.along: Float get() = if (vertical) center.y else center.x

    private val length: Float get() = row?.size?.let { if (vertical) it.height else it.width }?.toFloat() ?: 0f

    /** The enabled destinations, in the order they are drawn along the row. */
    private fun ordered(): List<Pair<Any, Rect>> = targets.mapNotNull { (key, target) ->
        if (!target.enabled) null else boundsOf(target)?.let { key to it }
    }.sortedBy { it.second.along }

    /** [at], in [target]'s coordinates, along the row. */
    fun inRow(target: DragTarget, at: Offset): Float {
        val own = if (vertical) at.y else at.x
        val row = row ?: return own
        val coordinates = target.coordinates ?: return own
        if (!row.isAttached || !coordinates.isAttached) return own
        val there = row.localPositionOf(coordinates, at)
        return if (vertical) there.y else there.x
    }

    private fun pick(ticker: DetentTicker, all: List<Pair<Any, Rect>> = ordered()) {
        if (all.isEmpty()) return
        val index = all.indices.minBy { abs(all[it].second.along - fingerAt) }
        ticker.at(index)
        val key = all[index].first
        over = key
        // A group is never chosen on the way past: its menu waits for the lift.
        if (selectsWhileDragging && key != chosen && targets[key]?.opensMenu != true) {
            chosen = key
            targets[key]?.takeIf { it.enabled }?.onClick?.invoke()
        }
    }

    /**
     * The delta that arrives with the claim, already counted: the drag claims
     * where the finger *is* once it has decided the direction, and then hands
     * over the movement it gathered deciding — which [begin] has in its
     * position. Counted twice it put the finger a touch slop ahead of itself.
     */
    private var claimed = false

    fun begin(at: Float, ticker: DetentTicker) {
        released = false
        claimed = true
        chosen = targets.entries.firstOrNull { it.value.selected }?.key
        startedOn = chosen
        fingerAt = at.coerceIn(0f, length)
        dragging = true
        reachFinger()
        val all = ordered()
        // **Armed on the marker's destination, not the one under the finger.** A
        // drag that starts on another destination has already crossed to it, and
        // that jump is the first detent. Armed under the finger, the first pick
        // only armed, so pressing the third destination and dragging on moved
        // the marker there in silence. With nothing selected in the row — the
        // current page is in a group, say — it arms where the finger is, as
        // before.
        ticker.reset()
        all.indexOfFirst { it.first == chosen }.takeIf { it >= 0 }?.let(ticker::at)
        pick(ticker, all)
    }

    fun move(delta: Float, ticker: DetentTicker) {
        if (claimed) {
            claimed = false
            return
        }
        fingerAt = (fingerAt + delta).coerceIn(0f, length)
        reachFinger()
        pick(ticker)
    }

    fun end(ticker: DetentTicker) {
        val key = over
        val target = key?.let { targets[it] }
        val lifted = released
        released = false
        dragging = false
        indicator?.reach = null
        ticker.reset()
        // Let go over a group: its menu opens, and the marker stays on it while
        // the menu is open — set in the same breath as `over` is cleared, so it
        // never sets off back to the destination it came from.
        if (lifted && target != null && target.opensMenu && target.enabled && !target.selected) {
            holding = key
            over = null
            target.onClick()
            return
        }
        // In one breath with the app's own change, so the marker heads straight
        // for the destination rather than back to the old one first.
        over = null
        // Chosen already, as the marker reached it, when it selects while dragging.
        if (selectsWhileDragging) {
            if (chosen != startedOn) afterChoice?.invoke()
            return
        }
        if (lifted && target != null && target.enabled && !target.selected) {
            target.onClick()
            afterChoice?.invoke()
        }
    }
}
