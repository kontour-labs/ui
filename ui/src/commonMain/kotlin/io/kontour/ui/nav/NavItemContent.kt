package io.kontour.ui.nav

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
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
import io.kontour.ui.foundation.LocalSelectionIndicator
import io.kontour.ui.foundation.Text
import io.kontour.ui.foundation.selectionIndicatorItem
import io.kontour.ui.input.focusRing
import io.kontour.ui.interaction.FeedbackIntent
import io.kontour.ui.interaction.LocalFeedback
import io.kontour.ui.interaction.kontourIndication
import io.kontour.ui.theme.Theme

/** Metrics shared by every navigation destination. */
object NavItemDefaults {
    /** The pill drawn behind a stacked item's icon when it is the destination. */
    val IndicatorWidth: Dp = 56.dp
    val IndicatorHeight: Dp = 32.dp

    /**
     * How much the current destination's icon grows.
     *
     * Ported from the app's `ToolbarButton`, where the selected button's weight
     * animates to 1.2 on a bouncy spring. Small, but it is what makes the bar feel
     * like it responded rather than redrew.
     */
    const val SelectedIconScale: Float = 1.08f
}

/** How a destination arranges its icon and its label. */
enum class NavItemLayout {
    /** Icon above label. A bar, and a collapsed rail. */
    Stacked,

    /** Icon beside label. An expanded rail, a drawer row. */
    Inline,
}

/**
 * One navigation destination, shared by [NavBarItem] and [NavRailItem].
 *
 * These were near-identical sixty-line functions differing only in padding and
 * their animation label strings, which meant every change to how selection looks
 * had to be made twice — and the drawer, which was written separately again, drifted
 * further than either.
 *
 * ### Selection is shape first, colour second
 *
 * Inside a [io.kontour.ui.foundation.SelectionIndicatorBox] the marker is a single
 * pill that **travels** between destinations, so the movement carries the meaning
 * and the accent tint is a second, redundant cue rather than the only one — which
 * is what WCAG 1.4.1 asks for.
 *
 * Outside one, there is no group to travel within, so the item falls back to
 * drawing its own pill. That is not a lesser path to tolerate: it is what lets a
 * single `NavBarItem` render standalone in the contract suite, or in a caller's
 * own layout, and still show which one is current.
 */
@Composable
internal fun NavDestinationItem(
    item: NavItem,
    selected: Boolean,
    modifier: Modifier = Modifier,
    layout: NavItemLayout = NavItemLayout.Stacked,
    showLabel: Boolean = true,
    /**
     * Drawn behind this one destination.
     *
     * Transparent by default — the bar or rail behind the whole row is the
     * surface. Set for [NavBarItemStyle.Separate], where each destination is its
     * own shape and there is no row surface at all.
     */
    containerColor: Color = Color.Transparent,
    indicatorKey: Any = item.label,
    interactionSource: MutableInteractionSource? = null,
) {
    val colors = Theme.colors
    val motion = Theme.motion
    val feedback = LocalFeedback.current
    val interactions = interactionSource ?: remember { MutableInteractionSource() }
    val shape = Theme.shapes.pill

    // Null when this item is not inside an indicator group, which is what decides
    // whether it draws its own pill or lets the shared one travel to it.
    val grouped = LocalSelectionIndicator.current != null

    val emphasis by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = motion.springOrTween(motion.springBouncy),
        label = "navItemEmphasis",
    )
    val container by animateColorAsState(
        targetValue = if (selected && !grouped) colors.accent.container else Color.Transparent,
        animationSpec = motion.tweenFast(),
        label = "navItemContainer",
    )
    val content by animateColorAsState(
        targetValue = when {
            !item.enabled -> colors.contentDisabled
            selected -> colors.accent.onContainer
            else -> colors.contentMuted
        },
        animationSpec = motion.tweenFast(),
        label = "navItemContent",
    )

    val interaction = modifier
        .semantics(mergeDescendants = true) {
            item.contentDescription?.let { contentDescription = it }
        }
        .selectionIndicatorItem(indicatorKey, selected)
        .minimumTouchTarget()
        .focusRing(interactions, shape, enabled = item.enabled)
        .clip(shape)
        .background(containerColor, shape)
        // The standalone fallback marker, for an item rendered outside a group.
        // Around the whole destination when it has a label, for the same reason
        // the travelling one is: a pill sized to the icon leaves the label
        // outside it.
        .then(
            if (!grouped && showLabel) Modifier.background(container, shape) else Modifier
        )
        .selectable(
            selected = selected,
            interactionSource = interactions,
            // A whole destination flinching is too much movement for something
            // tapped this often; the travelling pill is the feedback.
            indication = kontourIndication(shape, pressScale = 1f),
            enabled = item.enabled,
            role = Role.Tab,
            onClick = {
                feedback.perform(FeedbackIntent.Selection)
                item.onClick()
            },
        )

    val glyph = @Composable {
        Box(contentAlignment = Alignment.Center) {
            // The per-item fallback pill. Inside a group this is transparent and
            // the shared indicator does the work; the node stays so the item's
            // height does not change between the two paths.
            Box(
                Modifier
                    .graphicsLayer {
                        scaleX = 0.5f + 0.5f * emphasis
                        // Drawn only when this item is on its own *and* has no
                        // label — otherwise the marker is the whole-item
                        // background above, or the group's travelling pill.
                        alpha = if (grouped || showLabel) 0f else emphasis
                    }
                    .size(NavItemDefaults.IndicatorWidth, NavItemDefaults.IndicatorHeight)
                    .background(container, shape)
            )

            Box(
                modifier = Modifier.graphicsLayer {
                    val scale = 1f + (NavItemDefaults.SelectedIconScale - 1f) * emphasis
                    scaleX = scale
                    scaleY = scale
                },
                contentAlignment = Alignment.Center,
            ) {
                BadgedBox(badge = { if (item.badge != null) Badge(count = item.badge) }) {
                    Icon(
                        imageVector = item.iconFor(selected),
                        contentDescription = null,
                        size = Theme.sizing.iconLarge,
                        tint = content,
                    )
                }
            }
        }
    }

    val label = @Composable {
        if (showLabel) {
            Text(
                text = item.label,
                style = Theme.typography.labelSmall,
                color = content,
                maxLines = 1,
            )
        }
    }

    when (layout) {
        NavItemLayout.Stacked -> Column(
            modifier = interaction.padding(vertical = Theme.spacing.xxs),
            horizontalAlignment = Alignment.CenterHorizontally,
            // On the 4dp grid. The old literal 2dp was not.
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.xxs),
        ) {
            glyph()
            label()
        }

        NavItemLayout.Inline -> Row(
            modifier = interaction.padding(
                horizontal = Theme.spacing.sm,
                vertical = Theme.spacing.xxs,
            ),
            horizontalArrangement = Arrangement.spacedBy(Theme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            glyph()
            label()
        }
    }
}
