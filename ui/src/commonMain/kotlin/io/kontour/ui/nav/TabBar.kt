package io.kontour.ui.nav

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.a11y.minimumTouchTarget
import io.kontour.ui.components.display.Badge
import io.kontour.ui.foundation.HorizontalDivider
import io.kontour.ui.foundation.Icon
import io.kontour.ui.foundation.Text
import io.kontour.ui.input.focusRing
import io.kontour.ui.interaction.FeedbackIntent
import io.kontour.ui.interaction.LocalFeedback
import io.kontour.ui.interaction.kontourIndication
import io.kontour.ui.theme.Theme

object TabBarDefaults {
    val Height: Dp = 48.dp
    val IndicatorThickness: Dp = 3.dp
}

/**
 * Switches between views of the same thing.
 *
 * ```kotlin
 * TabBar(selectedIndex = tab) {
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
 * moving part. Same reasoning as
 * [io.kontour.ui.components.selection.SegmentedControl] — and the choice between
 * the two is about what changes: a segmented control filters *what* is shown, a
 * tab bar changes *how*.
 *
 * @param scrollable For more tabs than fit. Off by default: a scrolling tab row
 *   hides options past the edge, and the user has no way to know how many there
 *   are.
 */
@Composable
fun TabBar(
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    scrollable: Boolean = false,
    containerColor: Color = Color.Transparent,
    indicatorColor: Color = Theme.colors.accent,
    showDivider: Boolean = true,
    content: @Composable TabBarScope.() -> Unit,
) {
    val motion = Theme.motion
    val density = LocalDensity.current
    var tabBounds by remember { mutableStateOf<Map<Int, Pair<Dp, Dp>>>(emptyMap()) }

    val target = tabBounds[selectedIndex]
    val indicatorOffset by animateDpAsState(
        targetValue = target?.first ?: 0.dp,
        animationSpec = motion.springOrTween(motion.springDefault),
        label = "tabIndicatorOffset",
    )
    val indicatorWidth by animateDpAsState(
        targetValue = target?.second ?: 0.dp,
        animationSpec = motion.springOrTween(motion.springDefault),
        label = "tabIndicatorWidth",
    )

    val scope = remember(selectedIndex) {
        TabBarScope { index, x, width ->
            tabBounds = tabBounds + (index to (x to width))
        }
    }

    Column(modifier.background(containerColor)) {
        Box {
            Row(
                modifier = Modifier
                    .then(
                        if (scrollable) {
                            Modifier.horizontalScroll(rememberScrollState())
                        } else {
                            Modifier.fillMaxWidth()
                        }
                    )
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

            if (indicatorWidth > 0.dp) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .offset(x = indicatorOffset)
                        .width(indicatorWidth)
                        .height(TabBarDefaults.IndicatorThickness)
                        .clip(Theme.shapes.pill)
                        .background(indicatorColor)
                )
            }
        }

        if (showDivider) HorizontalDivider()
    }
}

/** Receiver for [TabBar]'s content, so a [Tab] can report where it landed. */
class TabBarScope internal constructor(
    internal val onTabPositioned: (index: Int, x: Dp, width: Dp) -> Unit,
) {
    internal var nextIndex = 0
}

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
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    badge: Int? = null,
    enabled: Boolean = true,
) {
    val index = remember { nextIndex++ }
    val colors = Theme.colors
    val motion = Theme.motion
    val density = LocalDensity.current
    val feedback = LocalFeedback.current
    val interactions = remember { MutableInteractionSource() }

    val content by animateColorAsState(
        targetValue = when {
            !enabled -> colors.contentDisabled
            selected -> colors.accent
            else -> colors.contentMuted
        },
        animationSpec = motion.tweenFast(),
        label = "tabContent",
    )

    Row(
        modifier = modifier
            .onGloballyPositioned { coordinates ->
                with(density) {
                    onTabPositioned(
                        index,
                        coordinates.positionInParent().x.toDp(),
                        coordinates.size.width.toDp(),
                    )
                }
            }
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
