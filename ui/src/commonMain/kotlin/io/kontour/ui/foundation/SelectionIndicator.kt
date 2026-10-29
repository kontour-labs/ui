package io.kontour.ui.foundation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
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
import kotlin.math.abs
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

    /**
     * The item's bounds, inset per axis.
     *
     * Two values rather than one because the nav rail and drawer want a pill
     * that is narrower than the row and exactly as tall as it. Inset on both
     * axes, the marker behind a 48dp destination came out 40dp high with the
     * label sitting hard against its edge — no vertical padding at all, which
     * reads as a pill that is too small for what it is marking rather than as
     * one that is snug.
     */
    data class Inset(val horizontal: Dp, val vertical: Dp) : IndicatorSizing {
        constructor(by: Dp) : this(by, by)
    }

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
            // Never inset past nothing: a 4dp inset on a 6dp item would otherwise
            // produce a negative rect, which draws as a flicker or not at all.
            val horizontal = sizing.horizontal.toPx().coerceAtMost(item.width / 2f)
            val vertical = sizing.vertical.toPx().coerceAtMost(item.height / 2f)
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

    /**
     * Where the indicator is *now*, in [anchor]'s space — the animated rect
     * rather than the one it is heading for.
     *
     * Published for the callers that draw something relative to the indicator
     * while it is still moving. A segmented control's thumb leans toward the
     * finger, and it has to lean from where it actually is: measured from the
     * selected segment's resting centre instead, the lean flips sign the instant
     * the selection changes, while the indicator underneath is still travelling.
     * The thumb jumps backwards past the segment it just left and then chases
     * the finger from there.
     *
     * **Worked out as it is read**, by [drawnNow], from the marker's own
     * animation — and read only where the thumb is drawn and where its drag is
     * hit-tested, so a travel redraws the thumb and composes nothing.
     *
     * It was a state, written from a `SideEffect`, and writing it meant reading
     * the travelling rect in composition: the whole group composed again on
     * every frame of every slide, every segment with it — 736 scopes for two
     * changes of a segmented control's selection, about eight a frame. Raised
     * 2026-10-29 for 0.5.1. The same choice of rect, from the same values, so
     * the thumb leans from the same place on every frame;
     * `IndicatorTravelRecompositionTest`.
     */
    internal val drawn: Rect get() = drawnNow()

    /** What [drawn] asks: set by the [SelectionIndicatorBox] this state belongs to. */
    internal var drawnNow: () -> Rect = { Rect.Zero }

    /**
     * Where the marker is reaching toward before it moves, in [anchor]'s space,
     * and how far of the way: a finger dragging it, or an item being pressed.
     * Null at rest. See [SelectionIndicatorBox]'s anticipation.
     */
    internal var reach: IndicatorReach? by mutableStateOf(null)

    /** The pressed item [reach] belongs to, when it is a press, so only its release lets it go. */
    internal var reachKey: Any? = null

    internal fun report(key: Any, bounds: Rect) {
        targetKey = key
        target = bounds
    }

    internal fun clear() {
        target = null
    }
}

/**
 * Set by a container whose content is on the way out, so the marker lets go.
 *
 * A collapsing container does not remove its content: it keeps it composed and
 * clipped while the exit animation runs, and every frame of that the rows inside
 * report new, smaller, higher positions. The marker follows them faithfully —
 * and it is drawn by [SelectionIndicatorBox] at the *list's* level, outside the
 * clip that is hiding the rows. So the rows vanish and the marker slides up over
 * whatever is above them, which was reported twice as a pill flying up the list
 * when a drawer group closed on the current page.
 *
 * Measured: a marker on a sub-item of a sidebar group travelled from row 402
 * to row 222 across fourteen frames of the collapse before fading out at all.
 *
 * The rows moving is real, and every row *below* a collapsing group moves too —
 * that motion should be followed. What must not be followed is a row moving
 * because it is being clipped away. Only the container knows the difference, so
 * only the container can say, and this is how it says it.
 *
 * Provide `true` around content that is animating out. Descendants stop
 * reporting, and any that owns the marker clears it, so the marker takes the
 * "stopped reporting" branch in [SelectionIndicatorBox] and fades where it
 * stands.
 */
internal val LocalSelectionIndicatorLeaving = compositionLocalOf { false }

/** Remembers a [SelectionIndicatorState] for one group of selectable items. */
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
 *     indicator = { Box(Modifier.fillMaxSize().background(Theme.colours.accent.solid, Theme.shapes.capsule)) },
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
 * navigation page: `ui-docs/content/components/navigation.md`.
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
    val arrival = remember { Arrival() }

    // Two keys, because one cannot answer both questions asked of it.
    //
    // `settledKey` is where the marker last *arrived*. `headingTo` is where an
    // in-flight travel is aimed, set before the spring and cleared only when it
    // completes — so a cancelled travel leaves it set, which is the whole point.
    //
    // There used to be one key, advanced after the travel, and it could not
    // tell an interrupted travel from a resize. Tapping the segment the marker
    // was travelling *away from* — the "changed my mind" tap, and the commonest
    // interruption there is — produced a target equal to the settled key,
    // which is indistinguishable from "same item, new rect", and took the
    // snap branch. The marker teleported back.
    var settledKey by remember { mutableStateOf<Any?>(null) }
    var headingTo by remember { mutableStateOf<Any?>(null) }

    // **Anticipation**: the marker leaning, and stretching, toward where it is
    // about to go before it goes. Asked for: "if you tap another option, the
    // indicator stretches/moves a little bit towards it ... a little bit like
    // the animation before the segmented control moves. same animation goes for
    // dragging it". A press on another item, or a finger dragging the marker,
    // sets [SelectionIndicatorState.reach]; this is how much of it is applied,
    // springing in and out, and none under reduced motion, where the marker
    // still goes where it is sent without reaching for it.
    val reaching by remember(state) { derivedStateOf { state.reach != null } }
    val engaged = remember { Animatable(0f) }
    LaunchedEffect(reaching, motion.reduceMotion) {
        if (!reaching && engaged.value > 0f && !motion.reduceMotion) {
            // A press let go of is usually about to select the item it leant
            // toward, and the travel takes the lean over from where it is
            // drawn — see the travel below. The selection arrives a few frames
            // after the release, by way of the caller's state, so the lean
            // holds for them rather than springing back first and handing the
            // travel less than it had.
            repeat(HandoverFrames) { withFrameNanos { } }
        }
        val lean = if (reaching && !motion.reduceMotion) 1f else 0f
        // Not where it is already, at rest: under reduced motion the spring is
        // a tween, and a tween from 0 to 0 asked for eleven frames whenever a
        // group was composed, to draw nothing. Moving, it springs on from where
        // it is, as before.
        if (engaged.value != lean || engaged.velocity != 0f) {
            engaged.animateTo(lean, motion.springOrTween(motion.springSnappy))
        }
    }
    // The last reach, kept once it is let go so the marker relaxes out of
    // where it was leaning rather than losing the number mid-spring.
    val leaning = remember { LastReach() }

    LaunchedEffect(resolved, state.targetKey) {
        val target = resolved
        if (target == null || target.width <= 0f || target.height <= 0f) {
            // The selected item stopped reporting — a drawer group collapsed over
            // it, say. Fade out where it stands rather than flying to the origin.
            // From visible only, for the same reason as the fade in below.
            if (alpha.value != 0f) alpha.animateTo(0f, motion.tweenFast())
            return@LaunchedEffect
        }

        // In flight, whoever the finger has since chosen.
        val travelling = headingTo != null
        val movedItem = state.targetKey != settledKey

        // A marker that is not on screen has no position worth keeping.
        //
        // Two of the three things reported about a collapsing list come from
        // treating one that is faded out as though it were still standing
        // somewhere. It travels from wherever it happened to be when it went —
        // which after a group closed over it is a rect the list no longer
        // contains, hence a pill "flying far above the list" — and because the
        // travel is awaited before the fade, it arrives invisibly and only then
        // fades in, which is the "appears a split second late".
        //
        // Faded out is the same situation as never drawn, so it takes the same
        // branch: put it where it is going and fade it up there.
        // Faded *out*, not merely mid-fade.
        //
        // This read `alpha.value < 1f`, which is true for the whole of the
        // fade-in that follows every travel — so a selection made in that
        // window snapped, for a marker the user could already see most of.
        // Gone is what this branch is about, and gone is near zero.
        val hidden = alpha.value < FadedOut

        when {
            // First appearance, or a return from nothing. Neither should slide
            // in from somewhere the user was not looking.
            !measured || hidden -> {
                bounds.snapTo(target)
                // Arrived with the group: it has been drawn opaque from the
                // group's first frame, see [Arrival], and the fade below must
                // find it already there.
                if (!measured && !arrival.drawnBare) alpha.snapTo(1f)
                measured = true
            }
            // Same item, new rect, and nothing in flight: the window resized or
            // the type scale changed. A spring chasing a resize reads as lag,
            // not as motion.
            //
            // `!travelling` is what makes this safe to snap. A rect that
            // changes *during* a travel is the same statement about the
            // destination and not a reason to abandon the spring, and a target
            // that changes back to where the marker started is a new
            // destination however much it looks like the old one.
            !travelling && !movedItem -> bounds.snapTo(target)
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
            else -> {
                headingTo = state.targetKey
                // **From the lean, when it was leaning here.** A press leans the
                // marker toward an item, and its release selects it. Travelling
                // from the marker's resting rect while the lean sprang back out
                // on its own left the marker's leading edge going *back* for the
                // first frames — the slow start of this spring against the
                // quick end of that one — and then across: the jiggle reported
                // on every click of a segmented control. So the travel starts
                // from the rect the lean draws, stretch and all, and the lean
                // stops there.
                val reach = leaning.value
                if (reach != null && leaning.key == state.targetKey && engaged.value > 0f) {
                    leanOf(bounds.value, leaning.width, leaning.height, reach, engaged.value)
                        ?.let { bounds.snapTo(it.applied(bounds.value)) }
                    engaged.snapTo(0f)
                }
                bounds.animateTo(target, motion.springOrTween(motion.springDefault))
            }
        }

        // **After** the travel, not before it.
        //
        // `settledKey` answers "has this item already been arrived at", and the
        // branch above spends the whole length of a spring inside `animateTo`.
        // This effect restarts whenever the target rect changes — a rail
        // expanding, a window resizing, a container re-measuring under the
        // marker — so a rect arriving mid-flight cancels the travel and
        // relaunches. Advanced up front, the relaunch found the key already
        // claimed, computed `movedItem = false`, took the resize branch, and
        // **abandoned the spring**: the marker covered the rest of the distance
        // in a single frame.
        //
        // `headingTo` is cleared here for the mirror-image reason. It answers
        // "is a travel in flight", which is the question that keeps the resize
        // branch away from an interruption, and a travel is only finished once
        // the spring returns.
        //
        // Measured with a row that changes width three frames into a travel:
        // 858px in one frame, against 103px in the fastest frame of the same
        // travel left alone. See `IndicatorTravelInterruptedTest`.
        //
        // A cancelled coroutine never reaches these lines, which is exactly the
        // property wanted: a travel that did not finish has neither claimed its
        // key nor stopped being in flight.
        settledKey = state.targetKey
        headingTo = null
        // Only from short of opaque. A marker that arrived with its group, or
        // travelled without fading, is opaque already, and a tween from 1 to 1
        // still asks for a frame on each of its steps: every group asked for
        // ten frames on its first appearance, and again at the end of every
        // travel, to draw nothing new. Counted 2026-10-29 for 0.5.1: a tab
        // bar was still asking 13 frames after it first composed.
        // `SelectionIndicatorIdleTest`.
        if (alpha.value != 1f) alpha.animateTo(1f, motion.tweenFast())
    }

    /**
     * Where the marker is drawn this frame.
     *
     * The `Animatable` is consulted only while it is actually carrying the
     * marker between two items. Everything else — a rail expanding, a window
     * resizing, the type scale changing — is drawn from the rect that arrived
     * *this* frame, and that is worth a frame of latency on its own.
     *
     * It used to be `bounds.value` unconditionally, which put a coroutine in the
     * middle of a resize: the row reports its new rect during frame N's layout,
     * `SelectionIndicatorBox` recomposes on N+1, `LaunchedEffect(resolved, …)`
     * cancels and relaunches to run one `snapTo`, that writes the animatable,
     * and the marker is finally measured and placed on N+2 at frame N's
     * geometry. Measured on a rail expanding from 88dp to 280dp: the pill's
     * right edge trailed the rail's by **77px at worst, against 39px of growth
     * in the fastest frame** — two frames, exactly as traced. The size lagged
     * with the position, because the marker's constraints come from the same
     * rect, so the pill was the wrong width as well as in the wrong place.
     *
     * The three cases are not interchangeable and the middle one is why this is
     * a `when` rather than a flag:
     *
     *  - **travelling** — the spring owns the position, and a rect arriving
     *    mid-flight is a destination it is already heading for;
     *  - **a new item, before the effect has run** — `resolved` is the *other*
     *    item's rect. Drawing it here would put the marker at its destination
     *    for one frame and then start the spring from where it used to be,
     *    which is a flicker rather than a saving;
     *  - **the item already drawn** — a resize, and the only case that can take
     *    the short road.
     */
    val visible = measured

    // See `SelectionIndicatorState.drawn`: chosen when it is read, from the
    // rect composition last resolved, rather than here.
    val lastResolved = rememberUpdatedState(resolved)
    state.drawnNow = remember(state) {
        {
            when {
                bounds.isRunning -> bounds.value
                state.targetKey == settledKey -> lastResolved.value ?: bounds.value
                else -> bounds.value
            }
        }
    }

    CompositionLocalProvider(LocalSelectionIndicator provides state) {
        Layout(
            contents = listOf(
                {
                    // In the layer rather than through `Modifier.alpha`, which
                    // reads the fade in composition and recomposed the group on
                    // every frame of it.
                    //
                    // Its alpha laid on each thing drawn, not on an offscreen
                    // copy: an offscreen layer is the marker's own size, and a
                    // marker's shadow is drawn outside it, so for the whole of a
                    // fade the shadow was cut away and arrived on its last frame
                    // — a segmented control's thumb under reduced motion, or
                    // coming back after fading out. The same late shadow as
                    // `SegmentedShadowArrivalTest`'s, in the cases [Arrival]
                    // leaves to the fade. Each thing faded on its own, the
                    // shadow shows faintly through the thumb for the fade's
                    // fraction of a second, which is what a fade of a raised
                    // thing looks like. `SelectionMarkerFadeShadowTest`.
                    Box(
                        Modifier
                            .graphicsLayer {
                                // Opaque until the effect has run, for a marker
                                // that arrived with its group. See [Arrival].
                                val faded = if (!measured && !arrival.drawnBare) 1f else alpha.value
                                this.alpha = faded
                                compositingStrategy = CompositingStrategy.ModulateAlpha
                            }
                            .clearAndSetSemantics {}
                    ) { indicator() }
                },
                { Box(content = content) },
            ),
            modifier = modifier.then(arrival.watch),
        ) { (indicatorMeasurables, contentMeasurables), constraints ->
            // Read here rather than taken from composition, which is the last
            // frame of the lag. The row reports its rect from
            // `onGloballyPositioned`, during the layout pass; a rect captured in
            // composition cannot reach the marker until the next one, but a rect
            // *read here* only invalidates layout, and a layout invalidation
            // raised during the layout pass is serviced before the frame draws.
            //
            // Arriving with the group, see [Arrival]: drawn from the rect its
            // item reported in this same frame, before the effect has run.
            val arriving = !visible && !arrival.drawnBare
            val live = when {
                arriving ->
                    state.target?.let {
                        resolveIndicatorBounds(sizing, it, density, layoutDirection)
                    } ?: bounds.value
                bounds.isRunning -> bounds.value
                state.targetKey == settledKey ->
                    state.target?.let {
                        resolveIndicatorBounds(sizing, it, density, layoutDirection)
                    } ?: bounds.value
                else -> bounds.value
            }

            // The content alone decides the group's size. The indicator is placed
            // into that space afterwards, so a marker sitting 300dp along a row
            // cannot make the row 300dp wider.
            val body = contentMeasurables.map { it.measure(constraints) }
            val width = body.maxOfOrNull { it.width } ?: constraints.minWidth
            val height = body.maxOfOrNull { it.height } ?: constraints.minHeight

            val indicatorWidth = live.width.roundToInt()
            val indicatorHeight = live.height.roundToInt()
            val drawIndicator = (visible || arriving) && indicatorWidth > 0 && indicatorHeight > 0
            arrival.placedMarker = drawIndicator
            val marker = if (drawIndicator) {
                indicatorMeasurables.map {
                    it.measure(Constraints.fixed(indicatorWidth, indicatorHeight))
                }
            } else {
                indicatorMeasurables.map { it.measure(Constraints.fixed(0, 0)) }
            }

            leaning.width = width.toFloat()
            leaning.height = height.toFloat()
            layout(width, height) {
                // The anchor, taken while this node is placed rather than
                // once the pass is over. `onGloballyPositioned` fires
                // children-first, so the items' reports in this group's first
                // pass found no anchor and gave up, and the first rect arrived
                // a frame later, by way of the items' effect. Placement comes
                // before every one of those callbacks, so now the first pass
                // reports, and the marker is laid out again before the frame
                // is drawn. `coordinates` is null only while alignment lines
                // are being worked out, which places nothing.
                coordinates?.let { state.anchor = it }
                // `place`, not `placeRelative`. The reported rect is already
                // physical — the row that laid the items out did whatever
                // mirroring RTL required — so a layout-relative placement here
                // would mirror it a second time. Placing both children from this
                // node's own origin is what keeps one coordinate space.
                if (drawIndicator) {
                    marker.forEach {
                        it.placeWithLayer(live.left.roundToInt(), live.top.roundToInt()) {
                            lean(live, width.toFloat(), height.toFloat(), state, engaged.value, leaning, motion.reduceMotion)
                        }
                    }
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
 * bar destination render standalone — in the contract suite, or in a caller's own
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

    // A plain holder, deliberately not snapshot state.
    //
    // This is written from the layout phase, on every pass, by every item in the
    // group. It used to be a `mutableStateOf` alongside a `layoutVersion` counter
    // that the effect below took as a key — so each of those writes invalidated a
    // composition scope, and `onGloballyPositioned` fires on every frame of a
    // scroll. A 122-item navigation drawer therefore did 122 recompositions and
    // 122 coroutine restarts *per scrolled frame*, none of which produced a
    // different indicator. Nothing reads this during composition, so nothing
    // needs to be told when it changes.
    val coordinates = remember { LastCoordinates() }

    // A container that is closing over this item is not a layout change to
    // follow. See [LocalSelectionIndicatorLeaving].
    val leaving = LocalSelectionIndicatorLeaving.current

    // Two things can move the indicator and only one of them is a layout event.
    // Selection moving between two items that did not move is not, and neither is
    // the anchor arriving late. It used to on every first pass, because
    // `onGloballyPositioned` fires children-first; the box takes it while it is
    // placed now, which is before any item reports (see [Arrival]), but a new
    // anchor is still a reason to report again. Both need an effect; a scroll
    // does not.
    LaunchedEffect(state, key, selected, state.anchor, leaving) {
        if (leaving) {
            if (state.targetKey == key) state.clear()
        } else {
            state.reportIfPossible(key, selected, coordinates.value)
        }
    }

    // Leaving is a report too, and it was the one nobody made.
    //
    // Only the selected item reports, and it reports a rect. When that item
    // stops existing — a drawer group collapsing over it is the case that
    // found this — the rect it last reported stayed on the state, so the
    // marker went on being drawn at coordinates that now belong to whatever
    // took the item's place. Collapsing the group containing the current page
    // painted the pill across the group's own header, and opening a different
    // group painted it across a row of that one.
    //
    // `SelectionIndicatorBox` has always had the branch for this — "the
    // selected item stopped reporting … fade out where it stands" — and
    // `clear()` has always existed. Neither had a caller.
    //
    // Guarded on still being the reported item, not on `selected`, because
    // selection moving from one item to another disposes the old one *and*
    // composes the new one, in an order this cannot depend on: if the arriving
    // item has already reported, `targetKey` is its key and the departing item
    // must keep its hands off. Only a departure that leaves nothing behind
    // clears.
    DisposableEffect(state, key) {
        onDispose { if (state.targetKey == key) state.clear() }
    }

    return onGloballyPositioned {
        coordinates.value = it
        if (leaving) return@onGloballyPositioned
        // Straight from the layout phase. `report` writes snapshot state, and
        // setting it to an equal value is a no-op, so an item that scrolled with
        // its own anchor — which is where the indicator lives — reports the same
        // rect and invalidates nothing at all.
        state.reportIfPossible(key, selected, it)
    }
}

/**
 * Whether a marker is arriving with its group, or after it.
 *
 * Reported 2026-10-21 for 0.5.0: on the desktop gallery a segmented control's
 * shadow "takes a few frames to load in" after it is scrolled into view. The
 * gallery's pages are lazy, so a control is composed on the frame it scrolls
 * in, and its thumb was treated as appearing: not drawn at all until the
 * effect had run, two frames on, and then faded up from nothing over
 * `Motion.fast` in a layer that clips while it is not opaque. The thumb's fill
 * is white on a `#F6F6F6` well, so the fade itself barely shows; what showed
 * was the shadow, cut away by that clip for the whole of the fade and
 * arriving all at once on its last frame, twelve frames after the control.
 * The phone was most likely spared because a finger brings a control in a few
 * pixels at a time and the fade is over before much of it is on screen, where
 * a wheel notch brings the whole of it in at once. Ten of the catalog's
 * goldens had been recorded in the middle of that fade, the thumb's shadow cut
 * square or missing, and a nav bar's or rail's marker a shade pale.
 *
 * A marker whose item is selected when the group first lays out is not
 * appearing, though: the group is, and the marker is part of it. So it is
 * drawn opaque, where its item is, on the group's first frame. One that turns
 * up later, in a group already drawn without it, is appearing, and still fades
 * up where it is going — its shadow with it, since the fade is laid on each
 * thing drawn rather than on an offscreen copy the marker's own size.
 *
 * Told apart in the draw phase, by whether the group has been drawn without a
 * marker. Plain fields, not snapshot state: they are written from layout and
 * draw, and nothing needs telling when they change.
 * `SegmentedShadowArrivalTest` holds it: the thumb, its shadow and all, is on the
 * first frame a scrolled-in control is.
 */
private class Arrival {
    /** Whether the last layout placed the marker. */
    var placedMarker = false

    /** Whether the group has ever been drawn without one. Never goes back. */
    var drawnBare = false

    /** Writes [drawnBare] as the group draws. Behind everything, and drawing nothing. */
    val watch = Modifier.drawBehind { if (!placedMarker) drawnBare = true }
}

/** The last position an item was laid out at. Not observed; see above. */
private class LastCoordinates {
    var value: LayoutCoordinates? = null
}

/** Reports [item]'s bounds relative to the group's anchor, when there are both. */
private fun SelectionIndicatorState.reportIfPossible(
    key: Any,
    selected: Boolean,
    item: LayoutCoordinates?,
) {
    val anchor = anchor
    if (!selected || item == null || anchor == null) return
    if (!item.isAttached || !anchor.isAttached) return
    report(
        key = key,
        bounds = Rect(
            offset = anchor.localPositionOf(item, Offset.Zero),
            size = item.size.toSize(),
        ),
    )
}

/**
 * Below this the marker counts as gone rather than as arriving.
 *
 * `alpha` animates to 1 after every travel, so "not fully opaque" describes the
 * tail of a perfectly normal transition as well as a marker that has faded out.
 * Treating the two alike made a selection landing in that window snap instead of
 * travel, for a marker the user could already see most of. A tenth is well
 * inside the fade's first frames and well above the zero a faded-out marker
 * rests at.
 */
private const val FadedOut = 0.1f

/** A point the marker reaches toward before it moves, and how far of the way. */
@Immutable
internal class IndicatorReach(
    /** In the anchor's space. A NaN on one axis means the reach is along the other alone. */
    val at: Offset,
    /** The share of the distance the marker leans. */
    val pull: Float,
    /**
     * Whether the distance counts for at most the marker's own span: a press
     * three items away leans as far as one on the next, a nudge rather than a
     * trip. A finger dragging the marker is followed however far it is.
     */
    val withinSpan: Boolean = false,
)

/**
 * The last reach, held while the marker relaxes out of it, the pressed item it
 * was for, and the group's size as last laid out, which a lean stays inside.
 * Not observed.
 */
private class LastReach {
    var value: IndicatorReach? = null
    var key: Any? = null
    var width: Float = 0f
    var height: Float = 0f
}

/**
 * How long a let-go lean waits for the selection it was reaching for, in
 * frames, before it relaxes. A click's selection has reached the group and set
 * the travel going within three frames of the release; one more is the margin.
 */
private const val HandoverFrames = 4

/** A lean: [by] pixels along x when [across], else along y, stretched by [stretch] from the edge it leaves. */
private class Lean(val across: Boolean, val by: Float, val stretch: Float) {
    /** The edge the stretch is anchored on: the one the marker is leaving. */
    val pivot: Float get() = if (by >= 0f) 0f else 1f

    /** Where a marker laid out at [rect] is drawn, leaning. */
    fun applied(rect: Rect): Rect = if (across) {
        val left = rect.left + by + pivot * rect.width * (1f - stretch)
        Rect(left, rect.top, left + rect.width * stretch, rect.bottom)
    } else {
        val top = rect.top + by + pivot * rect.height * (1f - stretch)
        Rect(rect.left, top, rect.right, top + rect.height * stretch)
    }
}

/**
 * How the marker at [live] leans toward [reach] by [engaged] of it, in a group
 * [width] by [height]; null when it does not.
 *
 * **From where the marker is**, not from the item it belongs to: a press lets
 * go into a selection that sends the marker travelling, and measured from the
 * new item the lean would flip on that frame and throw it backwards.
 *
 * **Stretched from the edge it is leaving**, as a segmented control's thumb is,
 * so it reaches toward where it is going: by the lean's share of its own span,
 * at most [MaxAnticipationStretch]. Never past the group's own edges.
 *
 * A press leans [AnticipationPull] of the way toward the item, counting at most
 * one span of the distance: 12% of its width, and 12% wider, toward an item next
 * to it or at the far end alike.
 */
private fun leanOf(live: Rect, width: Float, height: Float, reach: IndicatorReach, engaged: Float): Lean? {
    val centre = live.center
    val dx = if (reach.at.x.isNaN()) 0f else reach.at.x - centre.x
    val dy = if (reach.at.y.isNaN()) 0f else reach.at.y - centre.y
    val across = abs(dx) >= abs(dy)
    val span = if (across) live.width else live.height
    if (span <= 0f) return null
    val distance = if (across) dx else dy
    val raw = (if (reach.withinSpan) distance.coerceIn(-span, span) else distance) * reach.pull * engaged
    val by = if (across) {
        raw.coerceIn(-live.left, (width - live.right).coerceAtLeast(-live.left))
    } else {
        raw.coerceIn(-live.top, (height - live.bottom).coerceAtLeast(-live.top))
    }
    return Lean(across, by, 1f + (abs(by) / span).coerceAtMost(MaxAnticipationStretch))
}

/**
 * Leans the marker drawn at [live] toward the reach, by [engaged] of it, and
 * stretches it along the same axis: see [leanOf].
 */
private fun GraphicsLayerScope.lean(
    live: Rect,
    width: Float,
    height: Float,
    state: SelectionIndicatorState,
    engaged: Float,
    last: LastReach,
    reduceMotion: Boolean,
) {
    val now = state.reach
    if (now != null) {
        last.value = now
        last.key = state.reachKey
    }
    val reach = last.value ?: return
    // `engaged` already stays at 0 under reduced motion; said here as well, so
    // a marker that was leaning when the setting changed stops at once.
    if (engaged <= 0f || reduceMotion) return
    val lean = leanOf(live, width, height, reach, engaged) ?: return
    if (lean.across) {
        translationX = lean.by
        scaleX = lean.stretch
        transformOrigin = TransformOrigin(lean.pivot, 0.5f)
    } else {
        translationY = lean.by
        scaleY = lean.stretch
        transformOrigin = TransformOrigin(0.5f, lean.pivot)
    }
}

/**
 * Reports a press on this item to the enclosing indicator group, so the marker
 * leans toward it before the press lets go and the selection moves: see
 * [SelectionIndicatorBox]. Only a press on an item that is not already
 * selected, and only from [interactionSource], which is the item's own.
 */
@Composable
internal fun Modifier.anticipatesSelection(key: Any, selected: Boolean, interactionSource: InteractionSource): Modifier {
    val state = LocalSelectionIndicator.current ?: return this
    val coordinates = remember { LastCoordinates() }
    LaunchedEffect(state, key, selected, interactionSource) {
        if (selected) return@LaunchedEffect
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    val anchor = state.anchor
                    val item = coordinates.value
                    if (anchor != null && item != null && anchor.isAttached && item.isAttached) {
                        val bounds = Rect(anchor.localPositionOf(item, Offset.Zero), item.size.toSize())
                        state.reachKey = key
                        state.reach = IndicatorReach(bounds.center, AnticipationPull, withinSpan = true)
                    }
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> letGo(state, key)
            }
        }
    }
    DisposableEffect(state, key) {
        onDispose { letGo(state, key) }
    }
    return onGloballyPositioned { coordinates.value = it }
}

private fun letGo(state: SelectionIndicatorState, key: Any) {
    if (state.reachKey == key) {
        state.reachKey = null
        state.reach = null
    }
}

/**
 * How far toward a pressed item the marker leans: a little, a promise of where
 * it is about to go rather than the trip itself.
 */
internal const val AnticipationPull: Float = 0.12f

/** The most a leaning marker stretches, as a share of its own span. */
private const val MaxAnticipationStretch: Float = 0.2f
