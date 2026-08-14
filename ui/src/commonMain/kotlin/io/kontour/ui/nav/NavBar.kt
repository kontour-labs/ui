package io.kontour.ui.nav

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
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

/** How a [NavBar] sits on the screen. */
enum class NavBarStyle {
    /**
     * A pill floating just above the bottom edge, inset from the sides.
     *
     * What the app uses today. It leaves the content visible behind it, which
     * matters over a map — the bottom strip of the map is still there, partly
     * covered rather than cut off.
     */
    Floating,

    /**
     * Attached to the bottom edge, full width, rounded along its top only.
     *
     * The right shape when the content beneath is a list rather than something
     * spatial: a floating bar over a list always covers a row, and the user
     * cannot tell which.
     */
    Docked,
}

object NavBarDefaults {
    val Height: Dp = 64.dp
    val FloatingInset: Dp = 16.dp

    /** A floating bar wider than this is a stripe; keep it a pill and centre it. */
    val FloatingMaxWidth: Dp = 560.dp
}

/**
 * The primary navigation surface on a phone. **Goes at the bottom of the
 * screen.**
 *
 * Not a website header. On a phone the destinations belong within thumb reach,
 * which is the bottom edge — the app's own toolbar sits there today, and
 * [NavigationSuiteScaffold] places this one there for you rather than leaving it
 * to each screen.
 *
 * ```kotlin
 * NavBar(
 *     items = destinations,
 *     selectedIndex = current,
 *     action = { FloatingActionButton(Tabler.Outline.Search, "Search", ::search) },
 * )
 * ```
 *
 * Three to five destinations. Two is a [TabBar]; six is a [NavDrawer]. The
 * items are a [NavItem] list rather than a slot, so the same declaration serves
 * [NavRail] and [NavDrawer] on wider windows without being written out three
 * times.
 *
 * The selected item grows slightly and its tonal pill springs in behind it —
 * ported from the app's `ToolbarButton`, where the weight animates to 1.2 on a
 * bouncy spring. It is a control people tap dozens of times a session, and it is
 * worth two frames of personality.
 *
 * @param action An optional trailing control — the app attaches its search FAB
 *   here. Sits outside the destination group, so a screen reader does not
 *   announce it as "4 of 4" among the destinations.
 * @param style [NavBarStyle.Floating] by default, matching the app and the site.
 */
@Composable
fun NavBar(
    items: List<NavItem>,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    style: NavBarStyle = NavBarStyle.Floating,
    showLabels: Boolean = true,
    containerColor: Color = Theme.colors.surfaceRaised,
    contentColor: Color = Theme.colors.content,
    action: (@Composable () -> Unit)? = null,
) {
    val shape: Shape = when (style) {
        NavBarStyle.Floating -> Theme.shapes.pill
        NavBarStyle.Docked -> Theme.shapes.sheet
    }

    val bar = @Composable {
        Surface(
            modifier = Modifier
                .then(
                    if (style == NavBarStyle.Floating) {
                        Modifier.widthIn(max = NavBarDefaults.FloatingMaxWidth)
                    } else {
                        Modifier.fillMaxWidth()
                    }
                )
                .height(NavBarDefaults.Height),
            shape = shape,
            color = containerColor,
            contentColor = contentColor,
            shadow = if (style == NavBarStyle.Floating) {
                Theme.elevation.high
            } else {
                Theme.elevation.low
            },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Theme.spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xxs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xxs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items.forEachIndexed { index, item ->
                        NavBarItem(
                            item = item,
                            selected = index == selectedIndex,
                            showLabel = showLabels,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                action?.invoke()
            }
        }
    }

    when (style) {
        NavBarStyle.Floating -> Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(NavBarDefaults.FloatingInset),
            contentAlignment = Alignment.Center,
        ) {
            bar()
        }

        NavBarStyle.Docked -> Box(modifier.fillMaxWidth()) { bar() }
    }
}

/**
 * One destination in a [NavBar].
 *
 * Exposed for a bar that needs a destination the [NavItem] model does not cover.
 * Prefer passing items to [NavBar]: it is what keeps the bar, the rail and the
 * drawer showing the same list.
 */
@Composable
fun NavBarItem(
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

    // Ported from the app's ToolbarButton: the current destination grows a
    // little on a bouncy spring. Small, but it is what makes the bar feel like
    // it responded rather than redrawn.
    val emphasis by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = motion.springOrTween(motion.springBouncy),
        label = "navItemEmphasis",
    )
    val container by animateColorAsState(
        targetValue = if (selected) colors.accentContainer else Color.Transparent,
        animationSpec = motion.tweenFast(),
        label = "navItemContainer",
    )
    val content by animateColorAsState(
        targetValue = when {
            !item.enabled -> colors.contentDisabled
            selected -> colors.onAccentContainer
            else -> colors.contentMuted
        },
        animationSpec = motion.tweenFast(),
        label = "navItemContent",
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
            .padding(vertical = Theme.spacing.xxs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            // The tonal pill, behind the icon rather than above it. It grows out
            // of nothing rather than fading, so the selection reads as arriving
            // rather than developing.
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
