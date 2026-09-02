package io.kontour.ui.nav

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.kontour.ui.a11y.minimumTouchTarget
import io.kontour.ui.components.display.Badge
import io.kontour.ui.foundation.Icon
import io.kontour.ui.foundation.LocalSelectionIndicator
import io.kontour.ui.foundation.Text
import io.kontour.ui.foundation.elevation
import io.kontour.ui.foundation.selectionIndicatorItem
import io.kontour.ui.input.focusRing
import io.kontour.ui.interaction.FeedbackIntent
import io.kontour.ui.interaction.LocalFeedback
import io.kontour.ui.interaction.kontourIndication
import io.kontour.ui.theme.Shadow
import io.kontour.ui.theme.Theme
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Metrics shared by every navigation destination. */
object NavItemDefaults {
    /** The pill drawn behind a stacked item's icon when it is the destination. */
    val IndicatorWidth: Dp = 56.dp
    val IndicatorHeight: Dp = 32.dp

    /**
     * The glyph's box in a stacked item that also has a label.
     *
     * Shorter than [IndicatorHeight], and that is the whole point. The pill is
     * sized for an icon with nothing under it; when there is a label, the same
     * 32dp box puts 4dp of air under the icon and *then* the 4dp gap, so the
     * label reads as further from its icon than it is. 28dp is 2dp of air, and
     * the gap stays on the 4dp grid where it belongs.
     */
    val GlyphSize: DpSize = DpSize(IndicatorWidth, 28.dp)

    /**
     * The glyph's box in an inline item — a rail row, at any width.
     *
     * Square, and sized so an 88dp rail holds it without squeezing: 8dp of rail
     * padding and 12dp of item padding either side leave exactly 48. A box that
     * gets clamped is a box the icon is centred in differently at every width,
     * which is most of what made the rail's icons move as it grew.
     */
    val InlineGlyphSize: DpSize = DpSize(48.dp, 48.dp)

    /**
     * A destination that is a circle rather than a row in a bar.
     *
     * 40dp, not the touch target's 48. The circle is the *visible* destination
     * and `minimumTouchTarget()` reserves the rest — so five filled, elevated
     * 48dp discs became five 40dp ones with the same 48dp of layout around them,
     * and the bar reads lighter without a single pixel of tappable area lost.
     * The 4dp of transparent slack the touch target already provides is also
     * what the row's own vertical padding used to add a second time.
     */
    val CircleSize: DpSize = DpSize(40.dp, 40.dp)

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
    /** Icon above label. A bar. */
    Stacked,

    /** Icon beside label. A rail at any width, and a drawer row. */
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
     * Transparent by default — the rail or drawer behind the whole row is the
     * surface. Set by [NavBar], where each destination is its own free-standing
     * circle and there is no row surface at all.
     */
    containerColour: Color = Color.Transparent,
    /** An unselected destination's icon and label. */
    contentColour: Color = Theme.colours.contentMuted,
    /**
     * Cast by this one destination, when it is a shape standing on its own.
     *
     * [Shadow.None] in a rail or a drawer: the surface behind the row is what is
     * raised, and a per-item shadow would be a second one inside it.
     */
    shadow: Shadow = Shadow.None,
    /**
     * The box the glyph sits in, and the size of this item's own marker when it
     * has one.
     *
     * Set per bar style rather than fixed, because it is what separates a row of
     * destinations in a bar from a row of free-standing circles — and, in the
     * ordinary case, what decides how far the label sits from its icon.
     */
    indicatorSize: DpSize = NavItemDefaults.GlyphSize,
    /** Between the glyph and the label, in a stacked item. */
    labelGap: Dp = Theme.spacing.xxs,
    indicatorKey: Any = item.label,
    interactionSource: MutableInteractionSource? = null,
) {
    val colours = Theme.colours
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
    // Same as the drawer's: inside a group this is `Transparent` whatever
    // happens, so animating it allocates an `Animatable` and launches a
    // coroutine per destination to hold a constant.
    val container = if (grouped) {
        Color.Transparent
    } else {
        val animated by animateColorAsState(
            targetValue = if (selected) colours.accent.container else Color.Transparent,
            animationSpec = motion.tweenFast(),
            label = "navItemContainer",
        )
        animated
    }
    val content by animateColorAsState(
        targetValue = when {
            !item.enabled -> colours.contentDisabled
            selected -> colours.accent.onContainer
            else -> contentColour
        },
        animationSpec = motion.tweenFast(),
        label = "navItemContent",
    )

    // A destination drawn as an icon and nothing else still has to say what it
    // is. The label is the name whether or not it is on screen, so it becomes the
    // description exactly when it stops being text — otherwise turning labels off
    // turns a bar of named destinations into a row of unlabelled buttons, which
    // is WCAG 4.1.2 and the reason `showLabel` cannot be a purely visual switch.
    val announcement = item.contentDescription ?: item.label.takeIf { !showLabel }

    val interaction = modifier
        .semantics(mergeDescendants = true) {
            announcement?.let { contentDescription = it }
        }
        .selectionIndicatorItem(indicatorKey, selected)
        .minimumTouchTarget()
        .focusRing(interactions, shape, enabled = item.enabled)
        .selectable(
            selected = selected,
            interactionSource = interactions,
            // Drawn on the circle instead, further down. The whole destination is
            // the target — a label is not a decoration you have to aim past — but
            // its *shape* is the circle, and a tonal wash the height of an icon
            // and a word under it would tint a lozenge that is not there.
            indication = null,
            enabled = item.enabled,
            role = Role.Tab,
            onClick = {
                feedback.perform(FeedbackIntent.Selection)
                item.onClick()
            },
        )

    val glyph = @Composable {
        NavGlyph(
            size = indicatorSize,
            // The destination's own shape is the circle behind its glyph, not a
            // box around the glyph *and* the label. A label belongs under the
            // circle, on the page, the way a word under an app icon does — and a
            // lozenge tall enough to hold both is a bar again, one destination at
            // a time.
            marker = {
                Box(
                    Modifier
                        .fillMaxSize()
                        // A whole destination flinching is too much movement for
                        // something tapped this often; the travelling circle is
                        // the feedback, and this is the press under it.
                        // No shrink here either, and for the tab bar's
                        // reason: the selection pill travels to the destination
                        // that was pressed.
                        .indication(interactions, kontourIndication(shape, pressScale = 1f))
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            // Cast by something you can see: in a rail or a
                            // drawer the container is transparent, the surface
                            // behind the whole row is what is raised, and a
                            // shadow here would fall from nothing.
                            .then(
                                if (containerColour.alpha > 0f) {
                                    Modifier.elevation(shadow, shape)
                                } else {
                                    Modifier
                                }
                            )
                            .background(containerColour, shape)
                    )
                    // The per-item fallback marker, for a destination rendered
                    // outside a group. Inside one this is transparent and the
                    // shared indicator does the work; the node stays so the
                    // item's size does not change between the two paths.
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = 0.5f + 0.5f * emphasis
                                alpha = if (grouped) 0f else emphasis
                            }
                            .background(container, shape)
                    )
                }
            },
            badge = { if (item.badge != null) Badge(count = item.badge) },
        ) {
            Box(
                modifier = Modifier.graphicsLayer {
                    val scale = 1f + (NavItemDefaults.SelectedIconScale - 1f) * emphasis
                    scaleX = scale
                    scaleY = scale
                },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = item.iconFor(selected),
                    contentDescription = null,
                    size = Theme.sizing.iconLarge,
                    tint = content,
                )
            }
        }
    }

    // Faded rather than inserted. A rail turns its labels on partway through an
    // expansion, once it is wide enough to hold them, and a word that simply
    // exists on one frame having not existed on the previous one is a pop — the
    // one piece of the rail's old jumpiness that survived making the icons hold
    // still. Opacity only, no `expandHorizontally`: the label is the last thing
    // in the row and the space is already there, so growing into it would move
    // nothing and cost a re-measure a frame.
    //
    // A bar's `showLabels` does not change after composition, so this is inert
    // there: `AnimatedVisibility` does not animate a state it started in.
    val label = @Composable {
        AnimatedVisibility(
            visible = showLabel,
            enter = fadeIn(motion.tweenDefault()),
            exit = fadeOut(motion.tweenFast()),
        ) {
            Text(
                text = item.label,
                style = Theme.typography.labelSmall,
                colour = content,
                maxLines = 1,
            )
        }
    }

    when (layout) {
        NavItemLayout.Stacked -> Column(
            // Air under the *label*, and none above the glyph. The destination's
            // box then starts exactly where its glyph does, which is what lets a
            // marker sized to the glyph land on it — and what keeps a
            // destination that is only an icon a circle rather than a stadium
            // eight dp taller than it is wide.
            modifier = interaction.padding(
                bottom = if (showLabel) Theme.spacing.xxs else 0.dp,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
            // On the 4dp grid. The old literal 2dp was not — the air that made
            // the label read as distant came out of the glyph's box instead.
            verticalArrangement = Arrangement.spacedBy(labelGap),
        ) {
            glyph()
            label()
        }

        NavItemLayout.Inline -> Row(
            modifier = interaction.padding(
                horizontal = Theme.spacing.sm,
                vertical = Theme.spacing.xxs,
            ),
            // `lg`, and it is arithmetic rather than taste. A rail uses this
            // layout at *every* width now, so a collapsed one has to end before
            // its labels begin or it shows a sliver of each first letter. The
            // label starts at `railPadding + itemPadding + glyph + gap`, and the
            // rail's trailing edge is at `2×railPadding + 2×itemPadding + glyph`
            // — so the gap has to clear one of each padding, which is 20dp, and
            // 24 is the token above it. At `sm` the labels poked out.
            horizontalArrangement = Arrangement.spacedBy(Theme.spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            glyph()
            label()
        }
    }
}

/**
 * A destination's own shape, its icon and its badge, in a box of exactly [size].
 *
 * The badge's centre sits on the glyph's top-trailing corner, so it straddles the
 * circle's edge — and it **never changes what the destination measures**.
 *
 * ### Why this is not [io.kontour.ui.components.display.BadgedBox]
 *
 * `BadgedBox` reports a size that *includes* the overhang and places its content
 * off-centre inside it. That is right for a toolbar icon standing on its own and
 * wrong for a destination, because centring an asymmetrically padded node centres
 * the **padding**: with an 18dp badge the icon landed 4.5dp towards the leading
 * side and 4.5dp below the centre of the very circle drawn to mark it, and the
 * one badged destination sat visibly out of step with its neighbours.
 *
 * Reserving that space is the right answer for a toolbar, where an ancestor is
 * usually clipping and the badge would be bitten into. A destination is a shape
 * with air around it, so the badge can hang over its edge the way a badge should,
 * and every destination measures the same whether it carries one or not.
 */
@Composable
private fun NavGlyph(
    size: DpSize,
    marker: @Composable () -> Unit,
    badge: @Composable () -> Unit,
    icon: @Composable () -> Unit,
) {
    Layout(
        content = {
            marker()
            icon()
            // Wrapped, so there is a node to measure whether or not this
            // destination has a badge: a `Layout`'s children are addressed by
            // index, and an empty box measures 0×0 and places harmlessly.
            Box { badge() }
        },
    ) { measurables, constraints ->
        // Unbounded, both of them: a glyph squashed into a box narrower than
        // itself would be cropped, and a badge squeezed to fit the thing it
        // annotates is how a count ends up ellipsised.
        val glyph = measurables[1].measure(Constraints())
        val mark = measurables[2].measure(Constraints())

        val width = max(size.width.roundToPx(), glyph.width)
            .coerceAtMost(constraints.maxWidth)
        val height = max(size.height.roundToPx(), glyph.height)
            .coerceAtMost(constraints.maxHeight)
        val fallback = measurables[0].measure(Constraints.fixed(width, height))

        layout(width, height) {
            fallback.placeRelative(0, 0)
            glyph.placeRelative((width - glyph.width) / 2, (height - glyph.height) / 2)

            // The badge's centre goes on the glyph's top-trailing corner. Taken
            // from the glyph's measured box rather than from a constant, so a dot
            // and a "9+" straddle the same point instead of one floating clear of
            // the corner and the other sitting mostly on the icon.
            //
            // Kept within half a badge of the box's own edges, which is the only
            // thing that stops a badge bigger than the destination from hanging
            // off it entirely. It never binds at the sizes a nav bar uses.
            val x = ((width + glyph.width) / 2f).clampWithin(width, mark.width)
            val y = ((height - glyph.height) / 2f).clampWithin(height, mark.height)

            // Relative, so the badge is on the reading-order trailing corner
            // rather than always the right-hand one.
            mark.placeRelative(
                (x - mark.width / 2f).roundToInt(),
                (y - mark.height / 2f).roundToInt(),
            )
        }
    }
}

/**
 * A badge centre kept within half a badge of an [extent]-long edge.
 *
 * Written as a helper because the naive `coerceIn` is an exception waiting to
 * happen: a badge wider than the box it is annotating inverts the range, and
 * `coerceIn` throws rather than clamping. That is not hypothetical — it is what a
 * destination squeezed by a tight parent constraint does.
 */
private fun Float.clampWithin(extent: Int, badge: Int): Float {
    val half = badge / 2f
    val middle = extent / 2f
    return coerceIn(min(half, middle), max(extent - half, middle))
}
