package io.kontour.ui.foundation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import io.kontour.ui.theme.Theme
import kotlin.math.roundToInt

/**
 * Which side of an item an [IndicatorSizing.Edge] bar is pinned to.
 *
 * [Start] and [End] are layout-relative and resolve to a physical side; [Top] and
 * [Bottom] are already physical.
 */
enum class IndicatorEdge { Top, Bottom, Start, End }

/**
 * How the indicator is sized and placed relative to the item it marks.
 *
 * Pure geometry, resolved by [resolveIndicatorBounds], so the arithmetic is unit
 * tested without rendering anything.
 */
@Immutable
sealed interface IndicatorSizing {

    /** Exactly the item's bounds. A segmented control, a drawer row. */
    data object Fill : IndicatorSizing

    /** The item's bounds, inset on every side. */
    data class Inset(val by: Dp) : IndicatorSizing

    /**
     * A fixed size, placed against the item's box. The nav bar's pill behind an
     * icon.
     *
     * @param verticalBias Where in the item's height it sits: `0.5` centred,
     *   `0f` against the top. A destination that is an icon and nothing else is
     *   centred, and a destination with a label underneath is not — the marker
     *   belongs on the glyph, and centring it on the whole item drops it into the
     *   gap between the icon and the word.
     */
    data class Fixed(
        val width: Dp,
        val height: Dp,
        val verticalBias: Float = 0.5f,
    ) : IndicatorSizing

    /**
     * A bar of [thickness] pinned to [edge], spanning the item's other axis.
     *
     * The tab bar's underline, and the rail and drawer's leading-edge marker.
     */
    data class Edge(
        val edge: IndicatorEdge,
        val thickness: Dp,
        val inset: Dp = 0.dp,
    ) : IndicatorSizing
}

/**
 * The indicator's rect, given the item's rect in the anchor's coordinate space.
 *
 * **Physical in, physical out.** Everything above this point has already been
 * mirrored by the row or column that laid the items out, so mirroring again here
 * is the whole of the old RTL bug. [layoutDirection] is consulted for exactly one
 * thing: resolving [IndicatorEdge.Start] and [IndicatorEdge.End] to a side.
 */
internal fun resolveIndicatorBounds(
    sizing: IndicatorSizing,
    item: Rect,
    density: Density,
    layoutDirection: LayoutDirection,
): Rect = with(density) {
    when (sizing) {
        IndicatorSizing.Fill -> item

        is IndicatorSizing.Inset -> {
            val by = sizing.by.toPx()
            // Never inset past nothing: a 4dp inset on a 6dp item would otherwise
            // produce a negative rect, which draws as a flicker or not at all.
            val horizontal = by.coerceAtMost(item.width / 2f)
            val vertical = by.coerceAtMost(item.height / 2f)
            Rect(
                left = item.left + horizontal,
                top = item.top + vertical,
                right = item.right - horizontal,
                bottom = item.bottom - vertical,
            )
        }

        is IndicatorSizing.Fixed -> {
            val width = sizing.width.toPx()
            val height = sizing.height.toPx()
            // The bias picks a point in the slack, so 0.5 is the centre and 0 is
            // flush with the top whatever the item turns out to be.
            val top = item.top + (item.height - height) * sizing.verticalBias
            Rect(
                left = item.center.x - width / 2f,
                top = top,
                right = item.center.x + width / 2f,
                bottom = top + height,
            )
        }

        is IndicatorSizing.Edge -> {
            val thickness = sizing.thickness.toPx()
            val inset = sizing.inset.toPx()
            val physical = when (sizing.edge) {
                IndicatorEdge.Top, IndicatorEdge.Bottom -> sizing.edge
                IndicatorEdge.Start ->
                    if (layoutDirection == LayoutDirection.Rtl) IndicatorEdge.End
                    else IndicatorEdge.Start
                IndicatorEdge.End ->
                    if (layoutDirection == LayoutDirection.Rtl) IndicatorEdge.Start
                    else IndicatorEdge.End
            }
            when (physical) {
                IndicatorEdge.Top -> Rect(
                    left = item.left + inset,
                    top = item.top,
                    right = item.right - inset,
                    bottom = item.top + thickness,
                )
                IndicatorEdge.Bottom -> Rect(
                    left = item.left + inset,
                    top = item.bottom - thickness,
                    right = item.right - inset,
                    bottom = item.bottom,
                )
                // Start is the left edge once resolved above.
                IndicatorEdge.Start -> Rect(
                    left = item.left,
                    top = item.top + inset,
                    right = item.left + thickness,
                    bottom = item.bottom - inset,
                )
                IndicatorEdge.End -> Rect(
                    left = item.right - thickness,
                    top = item.top + inset,
                    right = item.right,
                    bottom = item.bottom - inset,
                )
            }
        }
    }
}

/**
 * Where the travelling selection indicator is, and where it is heading.
 *
 * Holds **one** rect rather than a map of them, because only the selected item
 * reports. There is therefore no index to assign and no composition-order counter
 * to reset — the duplicate-index bug that the old tab bar had is not fixed here,
 * it is unrepresentable.
 */
@Stable
class SelectionIndicatorState internal constructor() {

    /** The node every reported rect is expressed relative to. */
    internal var anchor: LayoutCoordinates? by mutableStateOf(null)

    /** The selected item's rect, in [anchor]'s space. */
    internal var target: Rect? by mutableStateOf(null)
        private set

    /** Which item that is. Only ever compared for equality. */
    internal var targetKey: Any? by mutableStateOf(null)
        private set

    internal fun report(key: Any, bounds: Rect) {
        targetKey = key
        target = bounds
    }

    internal fun clear() {
        target = null
    }
}

@Composable
fun rememberSelectionIndicatorState(): SelectionIndicatorState =
    remember { SelectionIndicatorState() }

/**
 * The state a [selectionIndicatorItem] reports into, or `null` outside a group.
 *
 * Read through a composition local rather than threaded as a parameter so an item
 * nested two containers deep — a drawer row inside a collapsible group — reports
 * without every container in between having to pass it along.
 */
internal val LocalSelectionIndicator = staticCompositionLocalOf<SelectionIndicatorState?> { null }

/**
 * A group of selectable items with one indicator that travels between them.
 *
 * ```kotlin
 * val indicator = rememberSelectionIndicatorState()
 *
 * SelectionIndicatorBox(
 *     state = indicator,
 *     sizing = IndicatorSizing.Edge(IndicatorEdge.Bottom, 3.dp),
 *     indicator = { Box(Modifier.fillMaxSize().background(Theme.colors.accent.solid, Theme.shapes.pill)) },
 * ) {
 *     Row(Modifier.fillMaxWidth().selectableGroup()) {
 *         items.forEachIndexed { index, item ->
 *             val selected = index == current
 *             Box(
 *                 Modifier
 *                     .selectionIndicatorItem(item.id, selected)
 *                     .selectable(selected, onClick = { current = index }, role = Role.Tab)
 *             ) { Text(item.label) }
 *         }
 *     }
 * }
 * ```
 *
 * Why one travelling marker rather than each item drawing its own is on the
 * navigation page: `docs/using/components/navigation.md`.
 *
 * ### Put it inside the scroll container, not around it
 *
 * The anchor and the items then scroll together, so a scroll offset never enters
 * the arithmetic. Wrapping the scroll container instead is how the old tab bar's
 * indicator drifted away from its tabs as the row scrolled.
 *
 * The indicator is drawn behind the content, reports **zero size** so it cannot
 * influence the group's measurement, and is cleared from the semantics tree — it
 * is decoration. Selection is carried by each item's own `selectable(selected =)`.
 */
@Composable
fun SelectionIndicatorBox(
    state: SelectionIndicatorState,
    sizing: IndicatorSizing,
    modifier: Modifier = Modifier,
    indicator: @Composable () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val motion = Theme.motion
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current

    val resolved = state.target?.let {
        resolveIndicatorBounds(sizing, it, density, layoutDirection)
    }

    val bounds = remember { Animatable(Rect.Zero, Rect.VectorConverter) }
    val alpha = remember { Animatable(0f) }
    var measured by remember { mutableStateOf(false) }
    var lastKey by remember { mutableStateOf<Any?>(null) }

    LaunchedEffect(resolved, state.targetKey) {
        val target = resolved
        if (target == null || target.width <= 0f || target.height <= 0f) {
            // The selected item stopped reporting — a drawer group collapsed over
            // it, say. Fade out where it stands rather than flying to the origin.
            alpha.animateTo(0f, motion.tweenFast())
            return@LaunchedEffect
        }

        val movedItem = state.targetKey != lastKey
        lastKey = state.targetKey

        when {
            // First appearance. Nothing should slide in from the origin.
            !measured -> {
                bounds.snapTo(target)
                measured = true
            }
            // Same item, new rect: the window resized or the type scale changed.
            // A spring chasing a resize reads as lag, not as motion.
            !movedItem -> bounds.snapTo(target)
            // Reduced motion asks for opacity instead of travel, which is exactly
            // what a bar sliding the width of the screen is. Documented in
            // `Motion`: "transition presets swap movement for opacity".
            motion.reduceMotion -> {
                alpha.snapTo(0f)
                bounds.snapTo(target)
            }
            // `springDefault`, not `springSnappy`. Snappy is near-critically
            // damped — right for a segmented control's thumb moving a few dozen
            // pixels, too clinical for a pill crossing a whole bar. Default
            // carries a little overshoot, which is what makes the marker read as
            // arriving rather than being assigned.
            //
            // Deliberately not `springBouncy`: its own token doc says it is
            // suppressed entirely under reduced motion, and a selection marker
            // should not change character that much between the two settings.
            else -> bounds.animateTo(target, motion.springOrTween(motion.springDefault))
        }
        alpha.animateTo(1f, motion.tweenFast())
    }

    val rect = bounds.value
    val visible = measured

    CompositionLocalProvider(LocalSelectionIndicator provides state) {
        Layout(
            contents = listOf(
                { Box(Modifier.alpha(alpha.value).clearAndSetSemantics {}) { indicator() } },
                { Box(content = content) },
            ),
            modifier = modifier.onGloballyPositioned { state.anchor = it },
        ) { (indicatorMeasurables, contentMeasurables), constraints ->
            // The content alone decides the group's size. The indicator is placed
            // into that space afterwards, so a marker sitting 300dp along a row
            // cannot make the row 300dp wider.
            val body = contentMeasurables.map { it.measure(constraints) }
            val width = body.maxOfOrNull { it.width } ?: constraints.minWidth
            val height = body.maxOfOrNull { it.height } ?: constraints.minHeight

            val indicatorWidth = rect.width.roundToInt()
            val indicatorHeight = rect.height.roundToInt()
            val drawIndicator = visible && indicatorWidth > 0 && indicatorHeight > 0
            val marker = if (drawIndicator) {
                indicatorMeasurables.map {
                    it.measure(Constraints.fixed(indicatorWidth, indicatorHeight))
                }
            } else {
                indicatorMeasurables.map { it.measure(Constraints.fixed(0, 0)) }
            }

            layout(width, height) {
                // `place`, not `placeRelative`. The reported rect is already
                // physical — the row that laid the items out did whatever
                // mirroring RTL required — so a layout-relative placement here
                // would mirror it a second time. Placing both children from this
                // node's own origin is what keeps one coordinate space.
                if (drawIndicator) {
                    marker.forEach { it.place(rect.left.roundToInt(), rect.top.roundToInt()) }
                }
                body.forEach { it.place(0, 0) }
            }
        }
    }
}

/**
 * Reports this node as the selected item of the enclosing indicator group.
 *
 * Does nothing outside a [SelectionIndicatorBox], which is what lets a single
 * `NavBarItem` render standalone — in the contract suite, or in a caller's own
 * layout — without an indicator group around it.
 *
 * Measurement and reporting are deliberately separate: every item measures
 * itself, and only the selected one reports. That way selection moving between
 * two items whose positions did not change still updates the indicator, which a
 * report driven purely by `onGloballyPositioned` would miss.
 *
 * @param key Which item this is. Compared only for equality — a change means the
 *   selection moved and the indicator travels; the same key with a new rect means
 *   the world resized and it snaps. Caller-supplied and stable; never a
 *   composition-order counter.
 */
@Composable
fun Modifier.selectionIndicatorItem(key: Any, selected: Boolean): Modifier {
    val state = LocalSelectionIndicator.current ?: return this
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    // Bumped on every layout pass. `LayoutCoordinates` is compared by identity and
    // the same instance is reused as a node moves, so without this the effect
    // below would not re-run when an item changes position.
    var layoutVersion by remember { mutableIntStateOf(0) }

    // Keyed on the anchor as well as the item, because `onGloballyPositioned`
    // fires children-first: on the very first pass an item reports before the
    // enclosing box has captured the anchor, and would otherwise never report
    // again — nothing has moved, so nothing calls it back.
    LaunchedEffect(state, key, selected, layoutVersion, state.anchor) {
        val item = coordinates
        val anchor = state.anchor
        if (!selected || item == null || anchor == null) return@LaunchedEffect
        if (!item.isAttached || !anchor.isAttached) return@LaunchedEffect
        state.report(
            key = key,
            bounds = Rect(
                offset = anchor.localPositionOf(item, Offset.Zero),
                size = item.size.toSize(),
            ),
        )
    }

    return onGloballyPositioned {
        coordinates = it
        layoutVersion++
    }
}
