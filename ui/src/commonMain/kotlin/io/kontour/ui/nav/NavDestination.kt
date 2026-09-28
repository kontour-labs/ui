package io.kontour.ui.nav

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.kontour.ui.a11y.minimumTouchTarget
import io.kontour.ui.foundation.Icon
import io.kontour.ui.foundation.LocalSelectionIndicator
import io.kontour.ui.foundation.Text
import io.kontour.ui.foundation.selectionIndicatorItem
import io.kontour.ui.input.focusRing
import io.kontour.ui.input.pointerCursor
import io.kontour.ui.interaction.kontourIndication
import io.kontour.ui.theme.Theme
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** How a destination arranges its icon and its label. */
internal enum class NavLayout {
    /** Icon above label: a bar's circle. */
    Stacked,

    /** Icon beside label: a rail's row, at every width. */
    Inline,
}

/** What pressing a destination does, which decides what it announces. */
internal sealed interface NavAction {
    /** Goes somewhere: `Role.Tab`, selectable. */
    data object Destination : NavAction

    /** Opens and closes a run of destinations under it: a `Role.Button` that says which. */
    data class Disclosure(val expanded: Boolean) : NavAction

    /** Opens a menu of destinations: a `Role.Button`. */
    data object Menu : NavAction
}

/**
 * One destination, drawn for whichever surface it is in: a bar's circle, a
 * rail's row. It used to be two near-identical functions and a third written
 * separately for the drawer, which drifted from both.
 *
 * ### Selection is shape first, colour second
 *
 * Inside a [io.kontour.ui.foundation.SelectionIndicatorBox] the marker is a
 * single pill that **travels** between destinations, so the movement carries the
 * meaning and the tint is a second cue. Outside one there is nothing to travel
 * within, so the destination draws its own pill.
 *
 * @param marked Whether the travelling marker lands here: [selected] for a
 *   destination, and for a group, whether a destination hidden inside it is.
 */
@Composable
internal fun NavDestination(
    label: String,
    icon: ImageVector?,
    selected: Boolean,
    onClick: () -> Unit,
    markerKey: Any,
    colours: NavColours,
    layout: NavLayout,
    showLabel: Boolean,
    indicatorSize: DpSize,
    modifier: Modifier = Modifier,
    marked: Boolean = selected,
    action: NavAction = NavAction.Destination,
    enabled: Boolean = true,
    selectedIcon: ImageVector? = null,
    contentDescription: String? = null,
    badge: (@Composable () -> Unit)? = null,
    interactionSource: MutableInteractionSource? = null,
    labelGap: Dp = Theme.spacing.xxs,
    indent: Dp = 0.dp,
    trailing: (@Composable () -> Unit)? = null,
) {
    val motion = Theme.motion
    val interactions = interactionSource ?: remember { MutableInteractionSource() }
    val shape = navItemShape(indicatorSize)
    val grouped = LocalSelectionIndicator.current != null

    val emphasis by animateFloatAsState(
        targetValue = if (marked) 1f else 0f,
        animationSpec = motion.springOrTween(motion.springBouncy),
        label = "navItemEmphasis",
    )
    // Inside a group the travelling marker carries the selection, so this is
    // `Transparent` whatever happens, and animating it would allocate an
    // `Animatable` and a coroutine per destination to hold a constant.
    val ownMarker = if (grouped) {
        Color.Transparent
    } else {
        val animated by animateColorAsState(
            targetValue = if (marked) colours.indicator else Color.Transparent,
            animationSpec = motion.tweenFast(),
            label = "navItemContainer",
        )
        animated
    }
    val content by animateColorAsState(
        targetValue = when {
            !enabled -> colours.contentDisabled
            marked -> colours.selectedContent
            else -> colours.content
        },
        animationSpec = motion.tweenFast(),
        label = "navItemContent",
    )

    // The label is the destination's name whether or not it is on screen, so it
    // becomes the description exactly when it stops being text. Otherwise
    // hiding labels turns a bar of named places into a row of unlabelled
    // buttons — WCAG 4.1.2.
    val announcement = contentDescription ?: label.takeIf { !showLabel }
    val strings = Theme.strings

    val target = when (action) {
        NavAction.Destination -> Modifier.pointerCursor(enabled = enabled).selectable(
            selected = selected,
            interactionSource = interactions,
            // Drawn on the marker's own shape instead, below.
            indication = null,
            enabled = enabled,
            role = Role.Tab,
            onClick = onClick,
        )

        else -> Modifier.pointerCursor(enabled = enabled).clickable(
            interactionSource = interactions,
            indication = null,
            enabled = enabled,
            role = Role.Button,
            onClick = onClick,
        )
    }

    val interaction = modifier
        .padding(start = indent)
        .semantics(mergeDescendants = true) {
            announcement?.let { this.contentDescription = it }
            if (action is NavAction.Disclosure) {
                stateDescription = if (action.expanded) strings.expanded else strings.collapsed
            }
        }
        .selectionIndicatorItem(markerKey, marked)
        // `fill`, so the row that lights up under a press is the whole 48dp the
        // marker covers, not the shorter content centred in it.
        .minimumTouchTarget(fill = true)
        .focusRing(interactions, shape, enabled = enabled)
        .then(target)

    // No glyph at all for a text row: an item with no icon, where its label shows.
    val hasGlyph = icon != null || !showLabel || layout == NavLayout.Stacked

    val glyph = @Composable {
        NavGlyph(
            size = indicatorSize,
            marker = {
                Box(
                    Modifier
                        .fillMaxSize()
                        // The press belongs on the circle a stacked item is marked
                        // by; an inline one is marked by a pill around its whole
                        // row, which takes the press instead, below.
                        .then(
                            if (layout == NavLayout.Stacked) {
                                Modifier.indication(interactions, kontourIndication(shape, pressScale = 1f))
                            } else {
                                Modifier
                            }
                        )
                ) {
                    // The per-item marker, for a destination outside a group. The
                    // node stays inside one so its size does not change between
                    // the two paths.
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = 0.5f + 0.5f * emphasis
                                alpha = if (grouped) 0f else emphasis
                            }
                            .background(ownMarker, shape)
                    )
                }
            },
            badge = { badge?.invoke() },
        ) {
            val selectedScale = NavDestinationDefaults.SelectedIconScale
            Box(
                modifier = Modifier.graphicsLayer {
                    val scale = 1f + (selectedScale - 1f) * emphasis
                    scaleX = scale
                    scaleY = scale
                },
                contentAlignment = Alignment.Center,
            ) {
                val glyphIcon = if (selected) selectedIcon ?: icon else icon
                if (glyphIcon != null) {
                    Icon(
                        imageVector = glyphIcon,
                        contentDescription = null,
                        size = Theme.sizing.iconLarge,
                        tint = content,
                    )
                } else {
                    // The label's initial, where there is no icon and no room
                    // for the word. Hidden from a screen reader, which hears the
                    // whole label from the destination instead.
                    Text(
                        text = Theme.formats.let { it.uppercase(it.firstGrapheme(label.trim())) },
                        style = Theme.typography.titleMedium,
                        colour = content,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.clearAndSetSemantics {},
                    )
                }
            }
        }
    }

    // Faded rather than inserted: a rail turns its labels on partway through an
    // expansion, and a word that simply appears is a pop. A bar's labels do
    // not change after composition, so this is inert there.
    val labelText = @Composable {
        AnimatedVisibility(
            visible = showLabel,
            enter = fadeIn(motion.tweenDefault()),
            exit = fadeOut(motion.tweenFast()),
        ) {
            Text(
                text = label,
                style = if (layout == NavLayout.Stacked) {
                    Theme.typography.labelSmall
                } else {
                    Theme.typography.bodyMedium
                },
                colour = content,
                maxLines = 1,
            )
        }
    }

    DisableSelection {
        when (layout) {
            NavLayout.Stacked -> Column(
                // Air under the label and none above the glyph, so the marker sized
                // to the glyph lands on it.
                modifier = interaction.padding(bottom = if (showLabel) Theme.spacing.xxs else 0.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                // Centred in what the touch target leaves. With a label the two
                // fill it and this is the top, as before; without one the glyph
                // used to sit at the top of its 48dp target with the slack under
                // it, a few points above the marker centred on the same target.
                verticalArrangement = Arrangement.spacedBy(labelGap, Alignment.CenterVertically),
            ) {
                glyph()
                labelText()
            }

            NavLayout.Inline -> Row(
                // Two paddings, split so this node is the marker's rect in between:
                // the pill is inset `xxs` from the row and exactly as tall, and the
                // press wash has to be that box.
                modifier = interaction
                    .padding(horizontal = Theme.spacing.xxs)
                    .indication(interactions, kontourIndication(Theme.shapes.capsule, pressScale = 1f))
                    .padding(
                        horizontal = Theme.spacing.sm - Theme.spacing.xxs,
                        // `sm` whether or not there is a glyph, a drawer row's
                        // padding: the glyph box is an icon tall now, so an icon
                        // row and a text row are one height, and a sidebar that
                        // mixes them keeps an even rhythm.
                        vertical = Theme.spacing.sm,
                    ),
                // `xs`, and it is arithmetic: the icon is centred in a glyph box
                // wider than it, so the box's own margin plus `xs` puts the label
                // `md` from the icon's edge — a drawer row's gap. It was `lg`,
                // which put the label 32dp away and read as two columns. A
                // collapsed rail needs no room kept: a label only appears once
                // the rail is past halfway to its expanded width.
                horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (hasGlyph) glyph()
                Box(Modifier.weight(1f)) { labelText() }
                // A text row has no glyph to hang its badge on, so it goes at the end.
                if (!hasGlyph && badge != null) badge()
                if (showLabel && trailing != null) trailing()
            }
        }
    }
}

/** Metrics shared by every destination. */
internal object NavDestinationDefaults {
    /**
     * How much the current destination's icon grows. Small, but it is what makes
     * a bar feel like it responded rather than redrew.
     */
    val SelectedIconScale: Float
        @Composable get() = Theme.componentDefaults.navSelectedIconScale
}

/**
 * A destination's own corner: a circle where its glyph box is square, a capsule
 * where it is not. One function because the bar's marker and the glyph drew the
 * same box with different corners once.
 */
@Composable
internal fun navItemShape(indicatorSize: DpSize): CornerBasedShape =
    if (indicatorSize.width == indicatorSize.height) {
        Theme.shapes.pill
    } else {
        Theme.shapes.capsule
    }

/**
 * A destination's own shape, its icon and its badge, in a box of exactly [size].
 *
 * The badge's centre sits on the glyph's top-trailing corner, straddling its
 * edge, and it **never changes what the destination measures** — which is why
 * this is not `BadgedBox`, whose reserved overhang centred the padding and left
 * a badged destination out of step with its neighbours.
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
            Box { badge() }
        },
    ) { measurables, constraints ->
        val glyph = measurables[1].measure(Constraints())
        val mark = measurables[2].measure(Constraints())

        val width = max(size.width.roundToPx(), glyph.width).coerceAtMost(constraints.maxWidth)
        val height = max(size.height.roundToPx(), glyph.height).coerceAtMost(constraints.maxHeight)
        val fallback = measurables[0].measure(Constraints.fixed(width, height))

        layout(width, height) {
            fallback.placeRelative(0, 0)
            glyph.placeRelative((width - glyph.width) / 2, (height - glyph.height) / 2)
            val x = ((width + glyph.width) / 2f).clampWithin(width, mark.width)
            val y = ((height - glyph.height) / 2f).clampWithin(height, mark.height)
            mark.placeRelative((x - mark.width / 2f).roundToInt(), (y - mark.height / 2f).roundToInt())
        }
    }
}

/**
 * A badge centre kept within half a badge of an [extent]-long edge. A helper
 * because `coerceIn` throws on the inverted range a badge wider than its box
 * gives it.
 */
private fun Float.clampWithin(extent: Int, badge: Int): Float {
    val half = badge / 2f
    val middle = extent / 2f
    return coerceIn(min(half, middle), max(extent - half, middle))
}
