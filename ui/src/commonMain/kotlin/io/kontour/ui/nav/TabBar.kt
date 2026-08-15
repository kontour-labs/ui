package io.kontour.ui.nav

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
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
import io.kontour.ui.theme.Theme

object TabBarDefaults {
    val Height: Dp = 48.dp
}

/**
 * Switches between views of the same thing.
 *
 * ```kotlin
 * TabBar {
 *     Tab("Departures", selected = tab == 0, onClick = { tab = 0 })
 *     Tab("Route map", selected = tab == 1, onClick = { tab = 1 })
 *     Tab("Alerts", selected = tab == 2, onClick = { tab = 2 }, badge = 2)
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
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    badge: Int? = null,
    key: Any = label,
    interactionSource: MutableInteractionSource? = null,
) {
    val colors = Theme.colors
    val motion = Theme.motion
    val feedback = LocalFeedback.current
    val interactions = interactionSource ?: remember { MutableInteractionSource() }

    val content by animateColorAsState(
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
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                size = Theme.sizing.iconMedium,
                tint = content,
            )
        }
        Text(
            text = label,
            style = Theme.typography.labelLarge,
            color = content,
            maxLines = 1,
        )
        // Beside the label, not over it. `BadgedBox` overlays its badge on the
        // top-right of what it wraps, which is right for an icon and lands on
        // the last two letters of a word.
        if (badge != null) Badge(count = badge)
    }
}
