package io.kontour.ui.dragdrop

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.min
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Scrolls [state] while something is carried near this element's ends, so a
 * card can be carried to a column off the side of a board, or a row to the far
 * end of a list.
 *
 * A [dropList] scrolls its own list already; this is for any other scrolling
 * thing a drag should be able to move — the `LazyRow` of columns, a
 * `verticalScroll` page of drop targets. The innermost region scrolls first: a
 * column scrolls before the board it sits in.
 *
 * A region starts only once the carried thing has been somewhere other than its
 * edge, so picking something up beside an edge does not throw the list.
 */
fun Modifier.dragAutoScroll(
    state: ScrollableState,
    orientation: Orientation,
    enabled: Boolean = true,
): Modifier = this then AutoScrollElement(state, orientation, enabled)

/** One scrolling region, as the host sees it. */
internal class AutoScrollRegion(
    var state: ScrollableState,
    var orientation: Orientation,
) {
    var enabled: Boolean = true
    var coordinates: LayoutCoordinates? = null
    var layoutDirection: LayoutDirection = LayoutDirection.Ltr

    /** Whether the carried thing has been anywhere but an edge, this drag. */
    var armed: Boolean = false

    /** When it came into the edge it is in, for the ramp; `null` while it is in neither. */
    var enteredAt: Duration? = null

    fun bounds(): Rect? = coordinates?.takeIf { it.isAttached }?.boundsInRoot()

    /**
     * A lazy list's own say, once it has been laid out: a `LazyRow`'s state
     * reports vertical until then, which is what a list read too early gets.
     */
    private val currentOrientation: Orientation
        get() = (state as? LazyListState)?.layoutInfo?.takeIf { it.totalItemsCount > 0 }?.orientation ?: orientation

    /** Whether [at] is in one of this region's edges, and the region is armed. */
    fun wantsToScroll(at: Offset, edge: Float): Boolean {
        val box = bounds() ?: return false
        if (!box.contains(at)) return false
        val zone = zoneOf(box, edge)
        val inEdge = edgeDistance(box, at) < zone
        if (!inEdge) armed = true
        return armed && inEdge
    }

    private fun zoneOf(box: Rect, edge: Float): Float {
        val length = if (currentOrientation == Orientation.Vertical) box.height else box.width
        return min(edge, length / 3f)
    }

    private fun edgeDistance(box: Rect, at: Offset): Float =
        if (currentOrientation == Orientation.Vertical) min(at.y - box.top, box.bottom - at.y) else min(at.x - box.left, box.right - at.x)

    /**
     * How far to scroll this frame, in pixels, with the carried thing's centre at
     * [at]; positive toward the end. Zero outside the edges, before it is armed,
     * and at the very start of the ramp.
     */
    fun step(at: Offset, edge: Float, maxSpeed: Float, ramp: Duration, now: Duration, frame: Duration): Float {
        val box = bounds() ?: return 0f
        val vertical = currentOrientation == Orientation.Vertical
        val start = if (vertical) box.top else box.left
        val end = if (vertical) box.bottom else box.right
        val position = if (vertical) at.y else at.x
        // Never more than a third of the region each, so a short one has a middle.
        val zone = min(edge, (end - start) / 3f)
        val towardEnd = end - position < zone
        val towardStart = position - start < zone
        if (!towardEnd && !towardStart) {
            armed = true
            enteredAt = null
            return 0f
        }
        if (!armed) return 0f
        val entered = enteredAt ?: now.also { enteredAt = it }
        val depth = if (towardEnd) (zone - (end - position)) / zone else (zone - (position - start)) / zone
        // Quadratic in how far in, so the edge of the zone barely moves and its
        // far side moves fast; ramped over the first moments in it.
        // Never quite nothing, so the first frame in an edge moves the list a
        // hair: a list that moved is one still able to.
        val rampedTo = if (ramp <= Duration.ZERO) 1f else ((now - entered) / ramp).toFloat().coerceIn(MinimumRamp, 1f)
        val speed = maxSpeed * depth.coerceIn(0f, 1f).let { it * it } * rampedTo
        val distance = speed * (frame / 1.seconds).toFloat()
        val forward = if (!vertical && layoutDirection == LayoutDirection.Rtl) !towardEnd else towardEnd
        return if (forward) distance else -distance
    }
}

private class AutoScrollElement(
    val state: ScrollableState,
    val orientation: Orientation,
    val enabled: Boolean,
) : ModifierNodeElement<AutoScrollNode>() {
    override fun create() = AutoScrollNode(AutoScrollRegion(state, orientation).also { it.enabled = enabled })

    override fun update(node: AutoScrollNode) {
        node.region.state = state
        node.region.orientation = orientation
        node.region.enabled = enabled
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "dragAutoScroll"
        properties["orientation"] = orientation
        properties["enabled"] = enabled
    }

    override fun equals(other: Any?) = other is AutoScrollElement &&
        other.state === state && other.orientation == orientation && other.enabled == enabled

    override fun hashCode() = (state.hashCode() * 31 + orientation.hashCode()) * 31 + enabled.hashCode()
}

private class AutoScrollNode(val region: AutoScrollRegion) :
    Modifier.Node(), GlobalPositionAwareModifierNode, CompositionLocalConsumerModifierNode {
    private var host: DragHost? = null

    override fun onAttach() {
        val found = currentValueOf(LocalDragHost) ?: return
        host = found
        region.layoutDirection = currentValueOf(LocalLayoutDirection)
        found.regions += region
    }

    override fun onDetach() {
        host?.regions?.remove(region)
        host = null
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        region.coordinates = coordinates
    }
}

/** Where the ramp starts: a twentieth of the speed the edge asks for. */
private const val MinimumRamp = 0.05f

/** Tells [read] the layout direction this element is laid out in. */
internal fun Modifier.readsLayoutDirection(read: (LayoutDirection) -> Unit): Modifier =
    this then ReadsLayoutDirectionElement(read)

private class ReadsLayoutDirectionElement(val read: (LayoutDirection) -> Unit) :
    ModifierNodeElement<ReadsLayoutDirectionNode>() {
    override fun create() = ReadsLayoutDirectionNode(read)

    override fun update(node: ReadsLayoutDirectionNode) {
        node.read = read
        node.tell()
    }

    override fun equals(other: Any?) = other is ReadsLayoutDirectionElement && other.read === read

    override fun hashCode() = read.hashCode()
}

private class ReadsLayoutDirectionNode(var read: (LayoutDirection) -> Unit) :
    Modifier.Node(), CompositionLocalConsumerModifierNode {
    override fun onAttach() = tell()

    fun tell() {
        if (isAttached) read(currentValueOf(LocalLayoutDirection))
    }
}
