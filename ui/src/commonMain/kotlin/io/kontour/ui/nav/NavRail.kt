package io.kontour.ui.nav

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.ui.adaptive.leadingEdges
import io.kontour.ui.components.action.IconButton
import io.kontour.ui.foundation.IndicatorEdge
import io.kontour.ui.foundation.IndicatorSizing
import io.kontour.ui.foundation.SelectionIndicatorBox
import io.kontour.ui.foundation.Surface
import io.kontour.ui.foundation.SystemIcons
import io.kontour.ui.foundation.rememberSelectionIndicatorState
import io.kontour.ui.motion.ChevronTurn
import io.kontour.ui.theme.Theme

object NavRailDefaults {
    val CollapsedWidth: Dp = 88.dp

    /**
     * Matches [NavDrawerDefaults.Width].
     *
     * An expanded rail *is* a drawer's width, and lining the two up is what stops
     * the switch between them reading as a jump.
     */
    val ExpandedWidth: Dp = 280.dp
}

/**
 * The primary navigation surface on a window with room beside the content.
 * **Goes down the leading edge.**
 *
 * ```kotlin
 * Row(Modifier.fillMaxSize()) {
 *     NavRail(
 *         items = destinations,
 *         selectedIndex = current,
 *         expanded = railOpen,
 *         onExpandedChange = { railOpen = it },
 *     )
 *     Content(Modifier.weight(1f))
 * }
 * ```
 *
 * Pass [onExpandedChange] to get the toggle; leave it null for a rail fixed at
 * whatever [expanded] says. The state is hoisted, matching [NavDrawerGroup] —
 * the app decides whether the rail is open because the user asked or because
 * the current destination implies it, and the component cannot know which.
 *
 * A collapsed *expandable* rail drops its labels rather than stacking them
 * under the icons. Stacked-then-inline would move the label to a different side
 * of the icon mid-animation, which is a pop nothing hides; icon-only keeps the
 * icon still and slides the label out from behind it. A rail with no toggle
 * keeps its stacked labels.
 *
 * @param itemAlignment Where the destinations sit vertically. `Top` by default;
 *   `Center` for a rail whose few destinations look stranded at the top of a tall
 *   window.
 */
@Composable
fun NavRail(
    items: List<NavItem>,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
    onExpandedChange: ((Boolean) -> Unit)? = null,
    showLabels: Boolean = true,
    itemAlignment: Alignment.Vertical = Alignment.Top,
    collapsedWidth: Dp = NavRailDefaults.CollapsedWidth,
    expandedWidth: Dp = NavRailDefaults.ExpandedWidth,
    containerColour: Color = Theme.colours.surface,
    contentColour: Color = Theme.colours.content,
    indicatorColour: Color = Theme.colours.accent.container,
    expandLabel: String = Theme.strings.expandNavigation,
    collapseLabel: String = Theme.strings.collapseNavigation,
    header: (@Composable ColumnScope.() -> Unit)? = null,
    action: (@Composable ColumnScope.() -> Unit)? = null,
    /**
     * What the rail's *content* keeps clear of. The status bar, the gesture bar
     * and the display cutout on the leading side by default — a landscape phone
     * puts the cutout exactly where the rail is.
     */
    windowInsets: WindowInsets = WindowInsets.leadingEdges,
) {
    val indicator = rememberSelectionIndicatorState()
    val motion = Theme.motion
    val expandable = onExpandedChange != null

    val width by animateDpAsState(
        targetValue = if (expandable && expanded) expandedWidth else collapsedWidth,
        // `springGentle` — its token doc says "sheets and large surfaces", and a
        // rail growing from 88dp to 280dp is a large surface.
        animationSpec = motion.springOrTween(motion.springGentle),
        label = "navRailWidth",
    )

    /**
     * Whether the rail is *currently wide enough* to show a label.
     *
     * Not `expanded`, which is where it is heading. The width animates over a
     * few hundred milliseconds, and a label composed on the frame the flag flips
     * appears in a rail with no room for it.
     *
     * This is now the *only* thing that changes across an expansion. It used to
     * have company: the destinations switched from a stacked column to an inline
     * row, and the rail's own `horizontalAlignment` switched from centred to
     * leading. Both of those moved the icons — the swap on the first frame of
     * the animation, and the alignment at the halfway mark, where it also
     * teleported the chevron and the action from the middle of the rail to its
     * edge. Worse in reverse: collapsing flipped the layout back to stacked
     * while the rail was still 240dp wide, so every icon jumped to the centre of
     * that and slid back. Measured at 121dp of travel, against the 4dp the two
     * resting states differ by.
     *
     * So the destinations are inline at every width and leading-aligned at every
     * width, the glyph box is the same box throughout, and the growing rail
     * reveals the labels rather than inserting them. Pinned by
     * `NavRailStillnessTest`.
     */
    val roomForLabels = width > (collapsedWidth + expandedWidth) / 2

    // Labels when there is room for them, and not otherwise — whether or not
    // this rail can be expanded at all. It used to be `expandable &&`, which
    // meant a *fixed* 88dp rail still stacked a word under every icon; the
    // destinations are inline at every width now, so that word would be a
    // sliver of its first letter against the rail's edge. It also makes
    // icons-only the ordinary rail rather than a mode: this is what
    // [NavigationSuiteScaffold] renders at Medium, and what the labels are
    // revealed *from* when it grows.
    val itemLabels = showLabels && roomForLabels

    // Published to the slots, so a header, an action or anything else the caller
    // puts in one can answer the same question the destinations do. Without it
    // the only way for slot content to know how wide the rail currently is was
    // for the caller to thread its own copy of `expanded` down by hand — and it
    // would be the target flag rather than the animated width, which is the
    // mistake this component spent a round unlearning.
    val room = NavExpansion(
        expanded = roomForLabels,
        progress = if (expandedWidth == collapsedWidth) {
            1f
        } else {
            ((width - collapsedWidth) / (expandedWidth - collapsedWidth)).coerceIn(0f, 1f)
        },
        onSurface = true,
    )

    CompositionLocalProvider(LocalNavExpansion provides room) {
    Surface(
        modifier = modifier.width(width).fillMaxHeight(),
        colour = containerColour,
        contentColour = contentColour,
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                // Inside the surface, so the rail's colour still reaches the edge
                // of the window and the cutout sits on the rail rather than on a
                // strip of whatever is behind it.
                .windowInsetsPadding(windowInsets)
                // Horizontal padding as well as vertical: the destinations run
                // full-width so the leading-edge marker sits at a consistent x,
                // and without this that x is the rail's own rounded edge, which
                // clips the marker in half.
                .padding(horizontal = Theme.spacing.xs, vertical = Theme.spacing.md),
            // Leading-aligned at every width. The destinations run full width
            // and lay their contents out from the leading edge, so anything
            // centred beside them — the toggle, a header, an action — is only
            // lined up with them by accident. It used to be centred while
            // narrow and leading once grown, which lined nothing up at either
            // end and moved everything at the halfway frame.
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
        ) {
            if (onExpandedChange != null) {
                // Centred on the destinations' icons rather than merely
                // leading-aligned with them. Everything in this column starts at
                // the same x now, but the toggle and a destination's glyph are
                // different widths, so "same start" is not "same centre" — and
                // the chevron sitting eight dp inside the icons under it is the
                // sort of thing you notice without being able to say why. The
                // box is a destination's leading padding and its glyph.
                Box(
                    modifier = Modifier
                        .padding(start = Theme.spacing.sm)
                        .width(NavItemDefaults.InlineGlyphSize.width),
                    contentAlignment = Alignment.Center,
                ) {
                    RailToggle(
                        expanded = expanded,
                        onExpandedChange = onExpandedChange,
                        expandLabel = expandLabel,
                        collapseLabel = collapseLabel,
                    )
                }
            }

            // Caller slots get the destinations' *content* edge, not the
            // column's — a header starting 12dp left of every icon under it is
            // the same untidiness the toggle had. Their width is still their
            // own: a slot is not something the rail should be sizing, and an
            // action wider or narrower than a glyph will not share the icons'
            // centre. The rail owns its leading edge; a caller who wants a
            // different one has `ColumnScope` to say so with.
            Box(Modifier.padding(start = Theme.spacing.sm)) { header?.invoke(this@Column) }

            // One box for all the room between the header and the action, with
            // the destinations scrolling inside it.
            //
            // It was a `Spacer(weight(1f))` either side of the destinations,
            // which is fine until the rail is shorter than they are: a `Column`
            // measures its children in order against the room that is left, so
            // the ones at the end are measured against nothing. Measured on a
            // 300px rail with nine destinations, three were drawn — at 45, 41
            // and 41 pixels — and the other six were not drawn at all. A
            // destination shorter than the one above it reads as a rendering
            // fault; one that is missing cannot be reached.
            //
            // A box takes exactly the leftover and aligns what is inside it, so
            // [itemAlignment] still means what it did. The scroll only engages
            // when the destinations are taller than that leftover, which is the
            // case that was broken and the only one where anything changes.
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = when (itemAlignment) {
                    Alignment.Bottom -> Alignment.BottomStart
                    Alignment.CenterVertically -> Alignment.CenterStart
                    else -> Alignment.TopStart
                },
            ) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    SelectionIndicatorBox(
                        state = indicator,
                        // A pill around the whole row, travelling between destinations.
                        // The bar's pill is sized to its icon; a rail row is wider than
                        // that, so the marker follows the row instead.
                        // Narrower than the row, and exactly as tall. Inset on both
                        // axes the pill lost 8dp of its height and the label sat hard
                        // against its edge.
                        sizing = IndicatorSizing.Inset(
                            horizontal = Theme.spacing.xxs,
                            vertical = 0.dp,
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
                        Column(
                            modifier = Modifier.selectableGroup(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
                        ) {
                            items.forEachIndexed { index, item ->
                                NavRailItem(
                                    item = item,
                                    selected = index == selectedIndex,
                                    showLabel = itemLabels,
                                    // Full width whether stacked or inline, so the
                                    // leading-edge marker sits at the same x for every
                                    // destination. Sized to content instead, the bar
                                    // would shift sideways as it moved between a short
                                    // label and a long one.
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
            Box(Modifier.padding(start = Theme.spacing.sm)) { action?.invoke(this@Column) }
        }
    }
    }
}

/**
 * The rail's expand/collapse control.
 *
 * `Role.Button`, not `Role.Tab` — expanding the rail does not navigate anywhere,
 * which is the same argument [NavDrawerGroup] makes for itself. The chevron
 * points the way the rail will grow, which is the trailing direction, so it has
 * to follow the layout direction rather than always pointing right.
 */
@Composable
private fun RailToggle(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    expandLabel: String,
    collapseLabel: String,
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    // One chevron, turned round — not two swapped for each other.
    //
    // Swapping the glyph made the control *change* rather than *move*, and a
    // control that changes has nothing to say about what it just did. The select
    // chevron has always rotated; this now does the same, and the button itself
    // stays where it is while the rail grows past it.
    IconButton(
        icon = if (rtl) SystemIcons.ChevronLeft else SystemIcons.ChevronRight,
        contentDescription = if (expanded) collapseLabel else expandLabel,
        onClick = { onExpandedChange(!expanded) },
        // A target, not an angle — see `SplitButton`. `IconButton` owns the
        // spring, and it is the same one every other arrow in the library turns
        // on.
        rotation = if (expanded) ChevronTurn else 0f,
        modifier = Modifier.semantics {
            stateDescription = if (expanded) "Expanded" else "Collapsed"
        },
    )
}

/**
 * One destination in a [NavRail].
 *
 * Icon beside label at every width, in a glyph box that does not change size —
 * so a rail growing from 88dp to 280dp reveals the label rather than rearranging
 * around it, and the icon does not move. There is no `expanded` parameter for
 * the same reason: there is nothing left for it to switch.
 */
@Composable
fun NavRailItem(
    item: NavItem,
    selected: Boolean,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
) {
    NavDestinationItem(
        item = item,
        selected = selected,
        modifier = modifier,
        layout = NavItemLayout.Inline,
        showLabel = showLabel,
        indicatorSize = NavItemDefaults.InlineGlyphSize,
        interactionSource = interactionSource,
    )
}
