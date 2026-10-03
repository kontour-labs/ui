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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.kontour.ui.a11y.minimumTouchTarget
import io.kontour.ui.foundation.anticipatesSelection
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
 * @param showLabel Whether the label is the destination's name on screen: read
 *   as text, rather than announced as a description.
 * @param labelFade In a surface that resizes, how visible the label is, read
 *   frame by frame without recomposing. `null` fades it in and out on its own
 *   when [showLabel] changes.
 * @param labelDrawn Whether the label is composed at all, which under
 *   [labelFade] is longer than [showLabel]: from the first frame it has any
 *   opacity, while the surface is still too narrow to call it the name.
 * @param initialFade How visible the initial standing in for a missing icon is,
 *   where a label is taking its place.
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
    labelFade: (() -> Float)? = null,
    labelDrawn: Boolean = showLabel,
    initialFade: (() -> Float)? = null,
    /** The marker's shape, when a bar was given one; the size's own otherwise. */
    markerShape: Shape? = null,
    /**
     * The bar's marker covers the whole destination rather than its glyph, so
     * the press wash does too, in [markerShape]. Stacked destinations only.
     */
    markerCoversItem: Boolean = false,
    /** How far in from the destination's edges the bar's marker stops. */
    markerInset: Dp = 0.dp,
) {
    val motion = Theme.motion
    val interactions = interactionSource ?: remember { MutableInteractionSource() }
    val shape = markerShape ?: navItemShape(indicatorSize)
    val washesItem = markerCoversItem && layout == NavLayout.Stacked
    // **A labelled destination inside its marker**: the icon smaller, the gap to
    // its label two points, and the two centred in the touch target rather than
    // stacked under a 40dp glyph box. The bar was 82dp tall with labels against
    // 64 without, reported as "too much space between the icon and the text,
    // and it's making the navbar huge" — and its marker, round the whole
    // destination, had the label in its rounded bottom and a badge poking out of
    // its top. A circle behind the glyph keeps its label under it, as before.
    val compact = washesItem && showLabel
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
        // A press on another destination leans the marker toward it before the
        // selection moves. See `SelectionIndicatorBox`.
        .anticipatesSelection(markerKey, marked, interactions)
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
                            if (layout == NavLayout.Stacked && !washesItem) {
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

    // A row's label is one line, always, and ends in an ellipsis rather than at
    // a word: a rail growing past a two-word label showed it a word at a time,
    // the second arriving whole in one frame. Unwrapped, the row uncovers it as
    // it widens.
    val labelStyle = if (layout == NavLayout.Stacked) Theme.typography.labelSmall else Theme.typography.bodyMedium
    val inline = layout == NavLayout.Inline
    val labelText = @Composable {
        if (labelFade != null) {
            // Following the surface's width, so the word fades in as the rail
            // makes room for it rather than on a clock of its own.
            if (labelDrawn) {
                Text(
                    text = label,
                    style = labelStyle,
                    colour = content,
                    maxLines = 1,
                    softWrap = !inline,
                    overflow = if (inline || compact) TextOverflow.Ellipsis else TextOverflow.Clip,
                    modifier = Modifier
                        .graphicsLayer { alpha = labelFade() }
                        // Not yet the name: the destination announces it.
                        .then(if (showLabel) Modifier else Modifier.clearAndSetSemantics {}),
                )
            }
        } else {
            // Faded rather than inserted: a word that simply appears is a pop.
            // A bar's labels do not change after composition, so this is inert
            // there.
            AnimatedVisibility(
                visible = showLabel,
                enter = fadeIn(motion.tweenDefault()),
                exit = fadeOut(motion.tweenFast()),
            ) {
                Text(
                    text = label,
                    style = labelStyle,
                    colour = content,
                    maxLines = 1,
                    // One line inside the marker, ending in an ellipsis where
                    // the marker's rounded bottom runs out of room for it.
                    softWrap = !inline && !compact,
                    overflow = if (inline || compact) TextOverflow.Ellipsis else TextOverflow.Clip,
                )
            }
        }
    }

    DisableSelection {
        when {
            compact -> CompactDestination(
                modifier = interaction.indication(interactions, kontourIndication(shape, pressScale = 1f)),
                markerShape = shape,
                markerInset = markerInset,
                labelHeight = labelStyle.lineHeight,
                icon = {
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
                                size = Theme.sizing.iconMedium,
                                tint = content,
                            )
                        } else {
                            Text(
                                text = Theme.formats.let { it.uppercase(it.firstGrapheme(label.trim())) },
                                style = Theme.typography.titleSmall,
                                colour = content,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                modifier = Modifier.clearAndSetSemantics {},
                            )
                        }
                    }
                },
                label = labelText,
                badge = { badge?.invoke() },
            )

            layout == NavLayout.Stacked -> Column(
                // Air under the label and none above the glyph, so the marker sized
                // to the glyph lands on it.
                modifier = interaction
                    // A marker around the whole destination takes the press on
                    // the whole destination, where the eye expects it.
                    .then(if (washesItem) Modifier.indication(interactions, kontourIndication(shape, pressScale = 1f)) else Modifier)
                    // Around the whole destination the marker is seen to have
                    // a bottom as well as a top: as much air under the label as
                    // the glyph's box leaves over the icon.
                    .padding(
                        bottom = when {
                            !showLabel -> 0.dp
                            washesItem -> ((indicatorSize.height - Theme.sizing.iconLarge) / 2).coerceAtLeast(Theme.spacing.xxs)
                            else -> Theme.spacing.xxs
                        },
                    ),
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

            else -> Row(
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
                if (hasGlyph) {
                    if (icon == null && initialFade != null) {
                        // An initial giving way to the word it stands for.
                        Box(Modifier.graphicsLayer { alpha = initialFade() }) { glyph() }
                    } else {
                        glyph()
                    }
                }
                Box(Modifier.weight(1f)) { labelText() }
                // A text row has no glyph to hang its badge on, so it goes at the end.
                if (!hasGlyph && badge != null) badge()
                if (trailing != null) {
                    if (labelFade != null) {
                        if (labelDrawn) Box(Modifier.graphicsLayer { alpha = labelFade() }) { trailing() }
                    } else if (showLabel) {
                        trailing()
                    }
                }
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

/**
 * A labelled destination laid out inside the marker that covers it: [icon] over
 * [label], [CompactLabelGap] apart and centred together in the destination, with
 * at least [CompactPadding] above and below. The destination is the touch
 * target's height, or what its content needs at large type, whichever is more:
 * at the default type a labelled bar is the height of an unlabelled one.
 *
 * **The label is no wider than the marker is where the label sits.** The
 * marker's corners are round — a capsule's are its whole height — so its
 * bottom is narrower than its middle, and a label sized to the cell ran out of
 * the marker at both ends of its baseline. Measured to the marker's chord at
 * the label's bottom, it ends in an ellipsis where the shape does.
 *
 * **The badge is inside the marker.** Its centre sits on the icon's
 * top-trailing corner, as a glyph's does, and is then moved in as far as it
 * takes to keep the whole badge [BadgeMargin] inside the marker's rounded rect
 * — see [badgeCentreInside]. With the icon a few points from the marker's top,
 * an anchored badge poked out over the bar.
 */
@Composable
private fun CompactDestination(
    modifier: Modifier,
    markerShape: Shape,
    markerInset: Dp,
    labelHeight: androidx.compose.ui.unit.TextUnit,
    icon: @Composable () -> Unit,
    label: @Composable () -> Unit,
    badge: @Composable () -> Unit,
) {
    Layout(
        modifier = modifier,
        content = {
            icon()
            Box { label() }
            Box { badge() }
        },
    ) { measurables, constraints ->
        val pad = CompactPadding.roundToPx()
        val gap = CompactLabelGap.roundToPx()
        val inset = markerInset.toPx()
        val margin = BadgeMargin.toPx()
        val glyph = measurables[0].measure(Constraints())
        val lineHeight = labelHeight.toPx().roundToInt()
        val content = glyph.height + gap + lineHeight

        val natural = max(glyph.width, measurables[1].maxIntrinsicWidth(lineHeight)) + 2 * pad
        val width = if (constraints.hasBoundedWidth && constraints.minWidth == constraints.maxWidth) {
            constraints.maxWidth
        } else {
            natural.coerceIn(constraints.minWidth, constraints.maxWidth)
        }
        val height = (content + 2 * pad).coerceIn(constraints.minHeight, constraints.maxHeight)
        val top = (height - content) / 2

        // The marker's own rect, and the corner it rounds with: read from its
        // shape at this size, so a capsule's is half its height and a docked
        // bar's medium corner is its own.
        val marker = androidx.compose.ui.geometry.Rect(inset, inset, width - inset, height - inset)
        val corner = markerCorner(markerShape, marker.size, this)
        val labelBottom = (top + content).toFloat()
        val chord = marker.width - 2f * cornerInset(corner, marker.bottom - labelBottom) - 2f * margin
        val text = measurables[1].measure(Constraints(maxWidth = chord.roundToInt().coerceIn(0, width)))
        val mark = measurables[2].measure(Constraints())

        layout(width, height) {
            glyph.placeRelative((width - glyph.width) / 2, top)
            text.placeRelative((width - text.width) / 2, top + glyph.height + gap)
            if (mark.width > 0 && mark.height > 0) {
                val anchor = androidx.compose.ui.geometry.Offset(
                    x = (width + glyph.width) / 2f,
                    y = top.toFloat(),
                )
                val centre = badgeCentreInside(
                    anchor = anchor,
                    badge = androidx.compose.ui.geometry.Size(mark.width.toFloat(), mark.height.toFloat()),
                    container = marker,
                    radius = corner,
                    margin = margin,
                )
                mark.placeRelative((centre.x - mark.width / 2f).roundToInt(), (centre.y - mark.height / 2f).roundToInt())
            }
        }
    }
}

/** The larger of [shape]'s top and bottom corners at [size], or none for a shape with no corners to read. */
private fun markerCorner(shape: Shape, size: androidx.compose.ui.geometry.Size, density: androidx.compose.ui.unit.Density): Float {
    val corners = shape as? CornerBasedShape ?: return 0f
    val radius = maxOf(
        corners.topStart.toPx(size, density),
        corners.topEnd.toPx(size, density),
        corners.bottomEnd.toPx(size, density),
        corners.bottomStart.toPx(size, density),
    )
    return radius.coerceAtMost(min(size.width, size.height) / 2f)
}

/**
 * How far in from its side a rounded corner of [radius] is, [fromEdge] in from
 * the edge it rounds: none once past the corner, the whole radius at the edge.
 */
internal fun cornerInset(radius: Float, fromEdge: Float): Float {
    if (radius <= 0f || fromEdge >= radius) return 0f
    val d = radius - fromEdge.coerceAtLeast(0f)
    return radius - kotlin.math.sqrt((radius * radius - d * d).coerceAtLeast(0f))
}

/**
 * Where a badge of [badge] size goes, wanting its centre at [anchor] and kept
 * [margin] inside [container], a rect with corners of [radius].
 *
 * First inside the straight edges, then out of the corners: a badge whose
 * outer corner still lies past the arc of the container's corner is moved
 * along the diagonal toward that arc's centre until it does not. Pure, so it is
 * tested by numbers; the badge's own corners are taken as square, which is the
 * stricter case of a round one.
 */
internal fun badgeCentreInside(
    anchor: androidx.compose.ui.geometry.Offset,
    badge: androidx.compose.ui.geometry.Size,
    container: androidx.compose.ui.geometry.Rect,
    radius: Float,
    margin: Float,
): androidx.compose.ui.geometry.Offset {
    val inner = container.deflate(margin)
    val halfW = badge.width / 2f
    val halfH = badge.height / 2f
    var x = anchor.x.coerceIn(inner.left + halfW, max(inner.left + halfW, inner.right - halfW))
    var y = anchor.y.coerceIn(inner.top + halfH, max(inner.top + halfH, inner.bottom - halfH))
    val r = (radius - margin).coerceIn(0f, min(inner.width, inner.height) / 2f)
    if (r <= 0f) return androidx.compose.ui.geometry.Offset(x, y)
    // Each corner of the container, as its arc's centre and the signs that
    // point outward from it.
    for ((sx, sy) in listOf(1f to -1f, -1f to -1f, 1f to 1f, -1f to 1f)) {
        val arcX = if (sx > 0f) inner.right - r else inner.left + r
        val arcY = if (sy > 0f) inner.bottom - r else inner.top + r
        val cornerX = x + sx * halfW
        val cornerY = y + sy * halfH
        val dx = cornerX - arcX
        val dy = cornerY - arcY
        if (dx * sx <= 0f || dy * sy <= 0f) continue
        val distance = kotlin.math.sqrt(dx * dx + dy * dy)
        if (distance <= r) continue
        val over = distance - r
        x -= dx / distance * over
        y -= dy / distance * over
    }
    return androidx.compose.ui.geometry.Offset(x, y)
}

/** Above and below a labelled destination's icon and label, at the least. */
private val CompactPadding = 6.dp

/** Between a labelled destination's icon and its label. */
private val CompactLabelGap = 2.dp

/** How far inside the marker a badge and a label's ends are kept. */
private val BadgeMargin = 2.dp
