package io.kontour.ui.nav

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.a11y.minimumTouchTarget
import io.kontour.ui.components.display.Badge
import io.kontour.ui.components.display.BadgedBox
import io.kontour.ui.foundation.Icon
import io.kontour.ui.foundation.Surface
import io.kontour.ui.foundation.Text
import io.kontour.ui.input.focusRing
import io.kontour.ui.interaction.FeedbackIntent
import io.kontour.ui.interaction.LocalFeedback
import io.kontour.ui.interaction.kontourIndication
import io.kontour.ui.theme.Theme

object NavRailDefaults {
    val Width: Dp = 88.dp
}

/**
 * The primary navigation surface on a window with room beside the content.
 *
 * ```kotlin
 * Row(Modifier.fillMaxSize()) {
 *     NavRail(items = destinations, selectedIndex = current, action = { … })
 *     Content(Modifier.weight(1f))
 * }
 * ```
 *
 * The same [NavItem] list as [NavBar] — so a window that grows past
 * [io.kontour.ui.adaptive.WindowWidthClass.Medium] changes where the
 * destinations are drawn, not what they are. [NavigationSuiteScaffold] makes
 * that switch for you.
 *
 * A rail rather than a wider bar because a horizontal bar on a landscape window
 * eats the dimension there is least of. The app's `MainNavigationRail` already
 * does this; the difference here is that the item list is not written out twice.
 *
 * @param header Above the destinations — a logo, or an account avatar.
 * @param action Below them, pushed to the bottom. The app puts its search FAB
 *   here, which is why the destinations and the action are separate slots rather
 *   than one list.
 */
@Composable
fun NavRail(
    items: List<NavItem>,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    showLabels: Boolean = true,
    width: Dp = NavRailDefaults.Width,
    containerColor: Color = Theme.colors.surface,
    contentColor: Color = Theme.colors.content,
    header: (@Composable ColumnScope.() -> Unit)? = null,
    action: (@Composable ColumnScope.() -> Unit)? = null,
) {
    Surface(
        modifier = modifier
            .width(width)
            .fillMaxHeight(),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = Theme.spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
        ) {
            header?.invoke(this)

            Column(
                modifier = Modifier.selectableGroup(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
            ) {
                items.forEachIndexed { index, item ->
                    NavRailItem(
                        item = item,
                        selected = index == selectedIndex,
                        showLabel = showLabels,
                    )
                }
            }

            if (action != null) {
                Spacer(Modifier.weight(1f))
                action(this)
            }
        }
    }
}

/** One destination in a [NavRail]. */
@Composable
fun NavRailItem(
    item: NavItem,
    selected: Boolean,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
) {
    val colors = Theme.colors
    val motion = Theme.motion
    val feedback = LocalFeedback.current
    val interactions = remember { MutableInteractionSource() }
    val shape = Theme.shapes.pill

    val emphasis by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = motion.springOrTween(motion.springBouncy),
        label = "navRailEmphasis",
    )
    val container by animateColorAsState(
        targetValue = if (selected) colors.accentContainer else Color.Transparent,
        animationSpec = motion.tweenFast(),
        label = "navRailContainer",
    )
    val content by animateColorAsState(
        targetValue = when {
            !item.enabled -> colors.contentDisabled
            selected -> colors.onAccentContainer
            else -> colors.contentMuted
        },
        animationSpec = motion.tweenFast(),
        label = "navRailContent",
    )

    Column(
        modifier = modifier
            .semantics(mergeDescendants = true) {
                item.contentDescription?.let { contentDescription = it }
            }
            .minimumTouchTarget()
            .focusRing(interactions, shape)
            .clip(shape)
            .selectable(
                selected = selected,
                interactionSource = interactions,
                indication = kontourIndication(shape, pressScale = 1f),
                enabled = item.enabled,
                role = Role.Tab,
                onClick = {
                    feedback.perform(FeedbackIntent.Selection)
                    item.onClick()
                },
            )
            .padding(horizontal = Theme.spacing.xs, vertical = Theme.spacing.xxs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .graphicsLayer {
                        scaleX = 0.5f + 0.5f * emphasis
                        alpha = emphasis
                    }
                    .size(width = 56.dp, height = 32.dp)
                    .background(container, shape)
            )
            Box(
                modifier = Modifier.graphicsLayer {
                    scaleX = 1f + 0.08f * emphasis
                    scaleY = 1f + 0.08f * emphasis
                },
                contentAlignment = Alignment.Center,
            ) {
                BadgedBox(
                    badge = {
                        val count = item.badge
                        if (count != null) Badge(count = count)
                    },
                ) {
                    Icon(
                        imageVector = item.iconFor(selected),
                        contentDescription = if (showLabel) null else item.label,
                        size = Theme.sizing.iconLarge,
                        tint = content,
                    )
                }
            }
        }

        if (showLabel) {
            Text(
                text = item.label,
                style = Theme.typography.labelSmall,
                color = content,
                maxLines = 1,
            )
        }
    }
}
