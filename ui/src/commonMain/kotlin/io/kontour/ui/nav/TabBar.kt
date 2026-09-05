package io.kontour.ui.nav

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.LayoutScopeMarker
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import io.kontour.ui.a11y.minimumTouchTarget
import io.kontour.ui.components.display.Badge
import io.kontour.ui.foundation.HorizontalDivider
import io.kontour.ui.foundation.Icon
import io.kontour.ui.foundation.IndicatorEdge
import io.kontour.ui.foundation.IndicatorSizing
import io.kontour.ui.foundation.ProvideContentColour
import io.kontour.ui.foundation.ProvideTextStyle
import io.kontour.ui.foundation.RowContentScope
import io.kontour.ui.foundation.SelectionIndicatorBox
import io.kontour.ui.foundation.Surface
import io.kontour.ui.foundation.Text
import io.kontour.ui.foundation.contentScope
import io.kontour.ui.foundation.rememberSelectionIndicatorState
import io.kontour.ui.foundation.selectionIndicatorItem
import io.kontour.ui.input.focusRing
import io.kontour.ui.input.pointerCursor
import io.kontour.ui.interaction.FeedbackIntent
import io.kontour.ui.interaction.LocalFeedback
import io.kontour.ui.interaction.kontourIndication
import io.kontour.ui.interaction.rememberDetentTicker
import io.kontour.ui.theme.Theme

object TabBarDefaults {
    val Height: Dp = 48.dp

    /**
     * How far across the pane a [tabSwipe] drag goes per tab.
     *
     * A quarter, so a deliberate swipe crosses one tab and a long drag steps
     * through several at an even pace. Half would mean a flick that stops short
     * of the middle does nothing at all, which reads as the gesture not existing.
     */
    const val SwipeThreshold: Float = 0.25f
}

/**
 * Switches between views of the same thing.
 *
 * ```kotlin
 * TabBar {
 *     Tab(selected = tab == 0, onClick = { tab = 0 }, key = 0) { +"Departures" }
 *     Tab(selected = tab == 1, onClick = { tab = 1 }, key = 1) { +"Route map" }
 *     Tab(selected = tab == 2, onClick = { tab = 2 }, key = 2, badge = 2) { +"Alerts" }
 * }
 * ```
 *
 * **Not app navigation.** Tabs stay within one screen — the stop you are looking
 * at, seen three ways. Moving between the app's destinations is a
 * [NavigationSuiteScaffold]'s job, and a tab bar used for that leaves the user
 * with no back stack and no sense of where they are.
 *
 * The indicator is one bar that **slides** between tabs rather than each tab
 * drawing its own, which is what makes the row read as a single control with a
 * moving part — and what conveys selection without depending on colour. It is
 * the shared [SelectionIndicatorBox], the same mechanism the nav bar, rail,
 * drawer and [io.kontour.ui.components.selection.SegmentedControl] use.
 *
 * Note there is no `selectedIndex`: each [Tab] states its own `selected`, which
 * is the only source of truth. The bar used to take both, and keeping the two
 * orderings in step is exactly what broke when a tab was composed conditionally.
 *
 * @param scrollable For more tabs than fit. Off by default: a scrolling tab row
 *   hides options past the edge, and the user has no way to know how many there
 *   are.
 */
@Composable
fun TabBar(
    modifier: Modifier = Modifier,
    scrollable: Boolean = false,
    containerColour: Color = Color.Transparent,
    indicatorColour: Color = Theme.colours.accent.container,
    /**
     * A rule under the bar.
     *
     * Off, like [TopBar]'s. The travelling pill *is* the selection, and a
     * full-width rule beneath it is the other half of the sliding-underline bar
     * this deliberately is not — a tab row that draws both reads as a Material
     * component with a pill bolted on, which is exactly how it was reported.
     * Callers who need the bar separated from what is under it can still ask.
     */
    showDivider: Boolean = false,
    /**
     * Controls at the trailing edge — an overflow menu, a filter.
     *
     * Outside `selectableGroup()`, deliberately. Inside it a menu button is
     * announced as "tab 4 of 4" and counted in the set the user is choosing
     * from, which is a lie about what pressing it does. It is also outside the
     * indicator box, so the travelling bar cannot decide to slide underneath it.
     */
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable TabBarScope.() -> Unit,
) {
    val indicator = rememberSelectionIndicatorState()
    // Through `Surface` rather than a bare `Modifier.background`, which is what
    // `NavBar`, `NavRail`, `NavDrawer` and `Scaffold` all do with their own
    // container colour. The difference is `LocalContentColour`: a bar given a
    // solid ground has to recolour the tabs sitting on it, and a background
    // modifier paints the colour and tells the content nothing.
    Surface(modifier = modifier, colour = containerColour) {
        Column {
            // The indicator box sits *inside* the scroll container, so the anchor
            // and the tabs scroll together and the scroll offset never enters the
            // arithmetic. Wrapping the scroll container instead is how the old
            // implementation drifted away from its tabs as the row scrolled.
            Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                if (scrollable) {
                    Modifier.horizontalScroll(rememberScrollState()).weight(1f, fill = false)
                } else {
                    Modifier.weight(1f)
                }
            ) {
                SelectionIndicatorBox(
                    state = indicator,
                    // A pill behind the tab rather than a bar under it. The
                    // sliding underline plus a full-width rule beneath was the
                    // most Material thing in the library and had no counterpart
                    // on any other platform; a travelling pill is the same
                    // mechanism — the shared indicator box, the same anchor
                    // arithmetic — saying the same thing in the library's own
                    // vocabulary, which is already full of pills.
                    // Inset by one grid step from the tab, which is the rail's
                    // and the drawer's rule and now means something here: a tab
                    // fills the bar's height, so the pill is 40dp inside a 48dp
                    // bar on every platform.
                    //
                    // It used to be `Inset(2.dp, 6.dp)` from a tab that was
                    // only as tall as its own label plus padding, or as tall as
                    // `minimumTouchTarget()` made it — whichever is more. And
                    // that floor is `platformMinTouchTarget`: 24dp on the JVM,
                    // 44 on iOS and web, 48 on Android. So
                    // the marker came out 24dp tall on desktop, 32 on a phone
                    // browser and 36 on Android, in a bar that is 48dp on all
                    // three. Measured on the desktop showcase: a 97×24dp
                    // lozenge, four times as wide as it was tall, floating in
                    // the middle of a bar twice its height, with barely 2dp of
                    // it below the label's ink. Every other marker in the
                    // library is either sized to a constant (the bar's
                    // `Fixed(56, 32)`) or exactly the row it marks (the rail's
                    // and drawer's `Inset(vertical = 0.dp)`); this was the only
                    // one whose proportions were a platform constant.
                    sizing = IndicatorSizing.Inset(
                        horizontal = Theme.spacing.xxs,
                        vertical = Theme.spacing.xxs,
                    ),
                    indicator = {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .clip(Theme.shapes.pill)
                                .background(indicatorColour)
                        )
                    },
                ) {
                    Row(
                        modifier = Modifier
                            .then(if (scrollable) Modifier else Modifier.fillMaxWidth())
                            .height(TabBarDefaults.Height)
                            .selectableGroup(),
                        horizontalArrangement = if (scrollable) {
                            Arrangement.Start
                        } else {
                            Arrangement.SpaceEvenly
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TabBarScope(row = this, fixed = !scrollable).content()
                    }
                }
            }

                if (actions != null) {
                    Row(
                        modifier = Modifier
                            .height(TabBarDefaults.Height)
                            .padding(end = Theme.spacing.xs),
                        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xxs),
                        verticalAlignment = Alignment.CenterVertically,
                        content = actions,
                    )
                }
            }

            if (showDivider) HorizontalDivider()
        }
    }
}

/**
 * Receiver for [TabBar]'s content, so a [Tab] can only exist inside one.
 *
 * Carries no index. The old version handed each tab a composition-order counter
 * and reset it on every selection change, so a conditionally-composed tab was
 * given a duplicate index and overwrote another tab's bounds. Nothing here
 * counts, so nothing can be miscounted.
 */
@LayoutScopeMarker
@Stable
class TabBarScope internal constructor(
    /** The row the tabs are laid out in, so a fixed bar can weight them. */
    internal val row: RowScope,
    /**
     * Whether the bar divides its width between the tabs rather than scrolling.
     *
     * A fixed row lays its children out in composition order and gives each the
     * width it asks for, so the last tab gets whatever is left — which on a
     * phone was nothing: three tabs and an overflow button in 312dp left
     * "Alerts" showing as a single "A" with its badge clipped away. Splitting
     * the row evenly instead makes every tab the same width and lets the labels
     * ellipsise, which is legible and, unlike starvation, symmetrical.
     *
     * A scrolling bar keeps intrinsic widths: there is no width to divide.
     */
    internal val fixed: Boolean,
)

/**
 * One tab.
 *
 * Takes its own `selected` rather than reading an index from the bar, so a
 * caller whose tabs are not a simple 0..n — a conditional tab, a tab keyed on an
 * enum — does not have to keep two orderings in step.
 */
@Composable
fun TabBarScope.Tab(
    selected: Boolean,
    onClick: () -> Unit,
    key: Any,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    badge: Int? = null,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowContentScope.() -> Unit,
) {
    val colours = Theme.colours
    val motion = Theme.motion
    val feedback = LocalFeedback.current
    val interactions = interactionSource ?: remember { MutableInteractionSource() }

    val contentColour by animateColorAsState(
        targetValue = when {
            !enabled -> colours.contentDisabled
            // On the indicator pill rather than over a bare bar, so the tone's
            // own on-container colour rather than its solid one.
            selected -> colours.accent.onContainer
            else -> colours.contentMuted
        },
        animationSpec = motion.tweenFast(),
        label = "tabContent",
    )

    Row(
        modifier = modifier
            .then(if (fixed) with(row) { Modifier.weight(1f) } else Modifier)
            // The bar's full height, and this is what makes the indicator a
            // fixed size: the marker is inset from *this* node, and without the
            // fill it was inset from whatever `minimumTouchTarget` happened to
            // reserve on the platform — 24dp on the JVM, 48 on Android. It also
            // makes the whole 48dp band tappable on desktop rather than the
            // 36dp the label occupied.
            .fillMaxHeight()
            .selectionIndicatorItem(key, selected)
            // Kept for a tab measured outside the bar's fixed-height row, where
            // the fill above has no bounded height to take.
            .minimumTouchTarget()
            .focusRing(interactions, Theme.shapes.control)
            .clip(Theme.shapes.control)
            .pointerCursor(enabled = enabled)
            .selectable(
                selected = selected,
                interactionSource = interactions,
                // Deliberately no shrink, unlike the buttons: the indicator
                // slides to the tab you pressed, and a tab that also flinched
                // would be two answers to one tap. Same bargain
                // `SegmentedControl` states — the moving thumb *is* the
                // feedback.
                indication = kontourIndication(Theme.shapes.control, pressScale = 1f),
                enabled = enabled,
                role = Role.Tab,
                onClick = {
                    feedback.perform(FeedbackIntent.Selection)
                    onClick()
                },
            )
            // Horizontal only: the tab's height is the bar's, and the label is
            // centred in it by the arrangement below.
            .tabPadding(Theme.spacing.md),
        // Centred, because a fixed tab is now as wide as its share of the bar
        // rather than as wide as its label: laid out from the start edge, three
        // tabs of one width each read as a row shoved to the left, with the
        // indicator under a label that is no longer above it.
        //
        // `Arrangement.Center` with the gap moved onto the badge, and not
        // `spacedBy(xs, CenterHorizontally)` which says the same thing more
        // neatly. That form makes this row — itself a weighted child of the bar
        // — never reach an idle frame: `ComponentContractTest` spins in
        // `waitForIdle` until its one-minute deadline, on all six contracts at
        // once. Swapping only the arrangement fixes it, so the cause is in
        // there somewhere; it has not been chased further than that.
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The label yields to the badge rather than the other way round. A
        // weighted row measures its unweighted children first, so the count
        // keeps its full size and the label gets what is left — without this
        // the badge was the child that ran out, and a tab reading "Alerts"
        // with its 2 shaved down to a red sliver is worse than a shorter word.
        Row(
            modifier = Modifier.weight(1f, fill = false),
            horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProvideTextStyle(Theme.typography.labelLarge) {
                ProvideContentColour(contentColour) {
                    // Ellipsis rather than the scope's default clip: a fixed
                    // bar divides its width evenly, so a long label on a narrow
                    // screen is *expected* to run out of room, and "Departures"
                    // cut to "Depart" reads as a different word rather than a
                    // shortened one.
                    contentScope(
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        content = content,
                    )
                }
            }
        }
        // Beside the label, not over it. `BadgedBox` overlays its badge on the
        // top-right of what it wraps, which is right for an icon and lands on
        // the last two letters of a word.
        // The gap the row's arrangement would normally provide — see above.
        if (badge != null) Badge(count = badge, modifier = Modifier.padding(start = Theme.spacing.xs))
    }
}

/**
 * Horizontal padding that gives way when the tab is narrower than its own air.
 *
 * A tab's `md` either side exists to give the travelling pill room around the
 * label. Fixed, it is 32dp the label can never reclaim — and on a bar squeezed
 * to 48dp that leaves 16dp, which is narrower than the ellipsis the label
 * would truncate to, so **nothing was drawn at all**. `WidthSweepTest` asks
 * every component to draw something from 48dp up, and this one did not; the
 * hairline rule under the bar was the only ink, which is why turning that off
 * by default is what exposed this rather than caused it.
 *
 * The rule is that **the padding never takes more than the label keeps**, which
 * needs no number of its own and stops binding at 64dp — twice the padding —
 * so every width anybody ships is untouched. Measured at 48dp: nothing at all
 * with the full padding, the truncated label with this.
 */
private fun Modifier.tabPadding(each: Dp): Modifier = layout { measurable, constraints ->
    val wanted = each.roundToPx() * 2
    val taken = wanted.coerceAtMost((constraints.maxWidth - wanted).coerceAtLeast(0))
    val placeable = measurable.measure(constraints.offset(horizontal = -taken))
    val width = (placeable.width + taken).coerceIn(constraints.minWidth, constraints.maxWidth)
    layout(width, placeable.height) {
        placeable.place((width - placeable.width) / 2, 0)
    }
}

/**
 * Changes tab when the pane under the bar is dragged sideways.
 *
 * ```kotlin
 * TabBar {
 *     Tab(selected = tab == 0, onClick = { tab = 0 }, key = 0) { +"Departures" }
 *     Tab(selected = tab == 1, onClick = { tab = 1 }, key = 1) { +"Route map" }
 * }
 * Box(Modifier.tabSwipe(selected = tab, count = 2, onSelectedChange = { tab = it })) {
 *     when (tab) { 0 -> Departures(); else -> RouteMap() }
 * }
 * ```
 *
 * Applied to the **content**, not to the bar. Tabs are the one navigation
 * control where the gesture and the indicator live in different places: nobody
 * swipes the bar, they swipe the thing the bar is describing.
 *
 * ### It commits as you go, not when you let go
 *
 * Every [TabBarDefaults.SwipeThreshold] of the pane's width moves one tab, while
 * the finger is still down — so the indicator travels with the drag rather than
 * appearing at the far end once it is over, and a long drag steps through
 * several. That is also what makes this testable without a state object: what
 * the gesture does is change the selection, and the selection is the caller's.
 *
 * ### It does not steal from what it wraps
 *
 * This is an *ancestor* of the pane's content, and a child gets the main pointer
 * pass first. A carousel, a horizontally scrolling row or a map inside the tab
 * claims its own drags and this never sees them; it only picks up what nothing
 * inside wanted. Which is the right rule and the one that needs no parameter.
 *
 * @param count How many tabs there are. Below two there is nothing to swipe to
 *   and this returns the receiver untouched.
 */
@Composable
fun Modifier.tabSwipe(
    selected: Int,
    count: Int,
    onSelectedChange: (Int) -> Unit,
    enabled: Boolean = true,
): Modifier {
    if (!enabled || count <= 1) return this

    val feedback = LocalFeedback.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val currentChange by rememberUpdatedState(onSelectedChange)

    var width by remember { mutableFloatStateOf(0f) }
    // The tab this gesture believes it is on. Held here rather than read back
    // from `selected`, because several steps can fall inside one frame and the
    // parameter does not refresh until the next composition — a fast flick
    // would then commit the same step three times.
    var index by remember { mutableIntStateOf(selected) }
    var travelled by remember { mutableFloatStateOf(0f) }
    val ticker = rememberDetentTicker()

    return this
        .onSizeChanged { width = it.width.toFloat() }
        .draggable(
            state = rememberDraggableState { delta ->
                if (width <= 0f) return@rememberDraggableState
                travelled += if (isRtl) -delta else delta
                val threshold = width * TabBarDefaults.SwipeThreshold

                // Dragging left goes forward, the way a page does.
                //
                // `Tick` rather than `Selection` on each step. They are two
                // different things and this was firing the wrong one: a tick is
                // a detent crossed, which is what a step through a row of tabs
                // is, and a selection is a value being chosen — which on this
                // gesture happens once, when the finger lifts. Firing the heavier
                // one per step made a three-tab drag feel like three decisions.
                while (travelled <= -threshold && index < count - 1) {
                    travelled += threshold
                    index += 1
                    ticker.at(index)
                    currentChange(index)
                }
                while (travelled >= threshold && index > 0) {
                    travelled -= threshold
                    index -= 1
                    ticker.at(index)
                    currentChange(index)
                }
                // Pinned at the ends, so a drag past the last tab does not bank
                // travel that then has to be undone before the first step back.
                //
                // Deliberately *not* rubber-banded, unlike the swipe row. A
                // resistance curve needs something that moves to apply it to;
                // `travelled` is an accumulator that draws nothing, so easing it
                // would only bank the travel this line exists to throw away.
                travelled = travelled.coerceIn(-threshold, threshold)
            },
            orientation = Orientation.Horizontal,
            onDragStarted = {
                index = selected
                travelled = 0f
                ticker.reset()
                ticker.at(index)
            },
            onDragStopped = {
                travelled = 0f
                ticker.reset()
                // Once, at the end — the thing a `Selection` was being spent on
                // per step now marks the gesture actually finishing.
                feedback.perform(FeedbackIntent.Selection)
            },
        )
}
