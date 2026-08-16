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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.LayoutScopeMarker
import androidx.compose.runtime.Stable
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.ui.a11y.minimumTouchTarget
import io.kontour.ui.components.display.Badge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.vector.ImageVector
import io.kontour.ui.foundation.IndicatorEdge
import io.kontour.ui.foundation.IndicatorSizing
import io.kontour.ui.foundation.SelectionIndicatorBox
import io.kontour.ui.foundation.rememberSelectionIndicatorState
import io.kontour.ui.foundation.selectionIndicatorItem
import io.kontour.ui.foundation.HorizontalDivider
import io.kontour.ui.foundation.Icon
import io.kontour.ui.foundation.Surface
import io.kontour.ui.foundation.Text
import io.kontour.ui.input.focusRing
import io.kontour.ui.interaction.FeedbackIntent
import io.kontour.ui.interaction.LocalFeedback
import io.kontour.ui.interaction.kontourIndication
import io.kontour.ui.foundation.RowContentScope
import io.kontour.ui.foundation.contentScope
import io.kontour.ui.foundation.ProvideContentColor
import io.kontour.ui.foundation.ProvideTextStyle
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
    containerColor: Color = Color.Transparent,
    indicatorColor: Color = Theme.colors.accent.solid,
    showDivider: Boolean = true,
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
    val scope = remember { TabBarScope() }

    // Through `Surface` rather than a bare `Modifier.background`, which is what
    // `NavBar`, `NavRail`, `NavDrawer` and `Scaffold` all do with their own
    // container colour. The difference is `LocalContentColor`: a bar given a
    // solid ground has to recolour the tabs sitting on it, and a background
    // modifier paints the colour and tells the content nothing.
    Surface(modifier = modifier, color = containerColor) {
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
                    sizing = IndicatorSizing.Edge(
                        edge = IndicatorEdge.Bottom,
                        thickness = Theme.sizing.selectionIndicator,
                    ),
                    indicator = {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .clip(Theme.shapes.pill)
                                .background(indicatorColor)
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
                        scope.content()
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
class TabBarScope internal constructor()

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
    val colors = Theme.colors
    val motion = Theme.motion
    val feedback = LocalFeedback.current
    val interactions = interactionSource ?: remember { MutableInteractionSource() }

    val contentColor by animateColorAsState(
        targetValue = when {
            !enabled -> colors.contentDisabled
            selected -> colors.accent.solid
            else -> colors.contentMuted
        },
        animationSpec = motion.tweenFast(),
        label = "tabContent",
    )

    Row(
        modifier = modifier
            .selectionIndicatorItem(key, selected)
            .minimumTouchTarget()
            .focusRing(interactions, Theme.shapes.small)
            .clip(Theme.shapes.small)
            .selectable(
                selected = selected,
                interactionSource = interactions,
                indication = kontourIndication(Theme.shapes.small, pressScale = 1f),
                enabled = enabled,
                role = Role.Tab,
                onClick = {
                    feedback.perform(FeedbackIntent.Selection)
                    onClick()
                },
            )
            .padding(horizontal = Theme.spacing.md, vertical = Theme.spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProvideTextStyle(Theme.typography.labelLarge) {
            ProvideContentColor(contentColor) {
                contentScope(maxLines = 1, content = content)
            }
        }
        // Beside the label, not over it. `BadgedBox` overlays its badge on the
        // top-right of what it wraps, which is right for an icon and lands on
        // the last two letters of a word.
        if (badge != null) Badge(count = badge)
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

    return this
        .onSizeChanged { width = it.width.toFloat() }
        .draggable(
            state = rememberDraggableState { delta ->
                if (width <= 0f) return@rememberDraggableState
                travelled += if (isRtl) -delta else delta
                val threshold = width * TabBarDefaults.SwipeThreshold

                // Dragging left goes forward, the way a page does.
                while (travelled <= -threshold && index < count - 1) {
                    travelled += threshold
                    index += 1
                    feedback.perform(FeedbackIntent.Selection)
                    currentChange(index)
                }
                while (travelled >= threshold && index > 0) {
                    travelled -= threshold
                    index -= 1
                    feedback.perform(FeedbackIntent.Selection)
                    currentChange(index)
                }
                // Pinned at the ends, so a drag past the last tab does not bank
                // travel that then has to be undone before the first step back.
                travelled = travelled.coerceIn(-threshold, threshold)
            },
            orientation = Orientation.Horizontal,
            onDragStarted = {
                index = selected
                travelled = 0f
            },
            onDragStopped = { travelled = 0f },
        )
}
