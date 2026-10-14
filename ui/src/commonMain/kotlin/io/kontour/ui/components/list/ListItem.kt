package io.kontour.ui.components.list

import io.kontour.ui.platform.webRole
import io.kontour.ui.platform.WebRole
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.HorizontalAlignmentLine
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutIdParentData
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.offset
import io.kontour.ui.a11y.contrastEdge
import io.kontour.ui.a11y.minimumTouchTarget
import io.kontour.ui.a11y.optionalBorder
import io.kontour.ui.a11y.selectionEdge
import io.kontour.ui.foundation.ContentScope
import io.kontour.ui.foundation.ContentSlot
import io.kontour.ui.foundation.ExcludeFromSelection
import io.kontour.ui.foundation.GroupPosition
import io.kontour.ui.foundation.Icon
import io.kontour.ui.foundation.ProvideContentColour
import io.kontour.ui.foundation.ProvideTextStyle
import io.kontour.ui.foundation.Text
import io.kontour.ui.foundation.shape
import io.kontour.ui.input.focusRing
import io.kontour.ui.input.pointerCursor
import io.kontour.ui.interaction.LocalRowEnabled
import io.kontour.ui.interaction.LocalRowInteractionSource
import io.kontour.ui.interaction.kontourIndication
import io.kontour.ui.theme.Theme
import io.kontour.ui.a11y.LocalNamedByContent
import kotlin.math.max
import kotlin.math.min

/** What a [ListItem] takes by default. */
object ListItemDefaults {
    /** Corner radius on a group's outside edges. */
    val Shape: CornerBasedShape @Composable @ReadOnlyComposable get() = Theme.shapes.container

    /** Gap between items in a group. */
    val Spacing: Dp
        @Composable @ReadOnlyComposable get() = Theme.componentDefaults.listItemSpacing

    /**
     * How far in from a row's start edge its content begins — the leading icon,
     * or the label where there is none.
     *
     * Also where a [SectionHeader] starts, and a [ListSection]'s title and footer
     * beside rows, so the words down a section make one column.
     */
    val ContentInset: Dp
        @Composable @ReadOnlyComposable get() = Theme.spacing.md

    /**
     * The rounding on the two corners that face each other across a seam.
     *
     * This used to be `= Spacing`, on the argument that the two were one
     * decision: a notch as deep as the gap it describes. That is a real
     * relationship and it is not the only one — a seam also has to be *visible*,
     * and at 2dp on a row 300dp wide it was a rounding error rather than a
     * corner. A group of rows read as one uncut slab.
     *
     * 4dp is the smallest value where the notch reads as deliberate at this
     * width, and it is deliberately no longer derived: tying it back to [Spacing]
     * would mean widening every gap in every list to buy a corner, which is a
     * much larger change than the one being asked for. The two are related, not
     * equal, and the KDoc is the place that says so.
     *
     * `ButtonGroupDefaults.InnerCorner` is the same decision at a different size
     * and lands on a different number — see its own note.
     */
    val InnerCorner: Dp = 4.dp

    /**
     * The floor for a one-line row, and for a two-line one.
     *
     * These are what make a row as tall as it is — not the padding, which is the
     * thing that gets blamed. At 12dp vertical a single line of text came to
     * 44dp and the 56dp floor lifted it the rest of the way, so trimming the
     * padding to 8dp changed the row's height by *nothing*: it only ever showed
     * up on a three-region row, where the content finally cleared the floor.
     *
     * 56 and 72 are Material's numbers, and they are sized for a row with a
     * 40dp avatar in it. The lists this is for are mostly a line of text and a
     * time, so they come down to the touch-target floor and one step above it,
     * which is where the padding starts doing the work instead.
     */
    val MinHeight: Dp
        @Composable @ReadOnlyComposable get() = Theme.componentDefaults.listItemMinHeight
    val TwoLineMinHeight: Dp
        @Composable @ReadOnlyComposable get() = Theme.componentDefaults.listItemTwoLineMinHeight
}

/**
 * One row of a list.
 *
 * ```kotlin
 * ListItem(
 *     onClick = { open(stop) },
 *     position = GroupPosition.of(index, stops.size),
 * ) {
 *     leading { +Tabler.Outline.Train }
 *     +"Perth Underground"
 *     supporting { +"Platform 2 · Joondalup line" }
 *     trailing { +"4 min" }
 * }
 * ```
 *
 * The row owns the interaction, not the controls inside it. A bare control with
 * a `Text` beside it gives the user a small target and gives a screen reader two
 * nodes for one choice — the same reasoning as
 * [io.kontour.ui.components.selection.SelectionRow], which is the specialisation
 * of this for a row that toggles something.
 *
 * @param position Which corners round. See [GroupPosition]; a group of rows
 *   should read as one object, not a stack of separate cards.
 * @param selected Marks the current row in a list that picks one. Requires
 *   [role] to say which kind of choice it is.
 * @param role What a screen reader announces. `Role.Button` for a row that
 *   navigates, `Role.RadioButton` for one of a set, `Role.Checkbox` for one that
 *   toggles.
 */
@Composable
fun ListItem(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    selected: Boolean = false,
    role: Role = Role.Button,
    position: GroupPosition = GroupPosition.Only,
    shape: Shape = position.shape(ListItemDefaults.Shape, ListItemDefaults.InnerCorner, Orientation.Vertical),
    /**
     * The row's own ground. Sunken rather than [io.kontour.ui.theme.ColourScheme.surface]
     * by default, because in this scheme `surface` and `background` are the same
     * white — a row drawn on `surface` is invisible on a page, and a group of
     * them reads as loose text rather than as one object with rows in it.
     */
    containerColour: Color = Theme.colours.surfaceSunken,
    selectedContainerColour: Color = Theme.colours.accent.container,
    contentColour: Color = Theme.colours.content,
    /**
     * Unspecified derives it from the content: a row with an [ListItemScope.overline]
     * or [ListItemScope.supporting] line is taller. It used to be a default
     * expression over the string parameters, which a builder cannot be — nothing
     * knows what the row holds until the builder has run.
     */
    minHeight: Dp = Dp.Unspecified,
    interactionSource: MutableInteractionSource? = null,
    content: ListItemScope.() -> Unit,
) {
    ListItemImpl(
        modifier = modifier,
        enabled = enabled,
        onClick = onClick,
        selected = selected,
        role = role,
        shape = shape,
        containerColour = containerColour,
        selectedContainerColour = selectedContainerColour,
        contentColour = contentColour,
        minHeight = minHeight,
        interactionSource = interactionSource,
        slots = listItemSlots(content),
    )
}

/**
 * [ListItem], with the few things a row drawn *by another component* needs and a
 * caller does not: room at its start for something drawn beside the content
 * (a timeline's rail), a hook on the label (where that rail's node lines up), and
 * a ground that can be clear even when the row is disabled.
 *
 * Every default is what [ListItem] draws, so a `ListItem` is exactly this.
 *
 * @param startPadding The space before the first region, where the row would
 *   otherwise have [ListItemDefaults.ContentInset].
 * @param labelModifier Wraps the label's region, when there is one.
 * @param edged Whether the row outlines itself at the contrast tier that asks
 *   for edges. Off for a row with no ground of its own to outline.
 * @param disabledContainerColour The ground while disabled.
 */
@Composable
internal fun ListItemImpl(
    modifier: Modifier,
    enabled: Boolean,
    onClick: (() -> Unit)?,
    selected: Boolean,
    role: Role,
    shape: Shape,
    containerColour: Color,
    selectedContainerColour: Color,
    contentColour: Color,
    minHeight: Dp,
    interactionSource: MutableInteractionSource?,
    slots: ListItemScope,
    startPadding: Dp = ListItemDefaults.ContentInset,
    labelModifier: Modifier = Modifier,
    edged: Boolean = true,
    disabledContainerColour: Color = Theme.colours.surfaceSunken.copy(alpha = 0.5f),
    /**
     * A caller's own interaction in place of [onClick]'s — a
     * [io.kontour.ui.components.selection.SelectionRow]'s `toggleable` or
     * `selectable` — applied where the click would be, and counted as one: the
     * row takes a control's touch target and focus ring and keeps its words out
     * of a text selection, as a row with an `onClick` does.
     */
    interaction: Modifier? = null,
) {
    val colours = Theme.colours
    val interactions = interactionSource ?: remember { MutableInteractionSource() }

    val container = when {
        !enabled -> disabledContainerColour
        selected -> selectedContainerColour
        else -> containerColour
    }
    val content = when {
        !enabled -> colours.contentDisabled
        selected -> colours.accent.onContainer
        else -> contentColour
    }

    // One indication for the life of the row, whatever its shape does. See
    // `rememberLiveShape`: a row whose corners morph lost its held press wash.
    val indicationShape = rememberLiveShape(shape)

    val controls = onClick != null || interaction != null
    val interactive = controls && enabled
    // Note the two conditions. A row with no `onClick` is not a control and gets
    // no click modifier at all; a row *with* one that is disabled is still a
    // control, and keeps the modifier with `enabled = false`. Dropping it would
    // block the callback silently — the row would announce as plain text, with
    // no role and no "disabled", and a screen-reader user would keep trying it.
    val clickModifier = when {
        interaction != null -> interaction
        onClick == null -> Modifier
        selected || role == Role.RadioButton -> Modifier.pointerCursor(enabled = enabled)
            .webRole(role, selected)
            .selectable(
                selected = selected,
                interactionSource = interactions,
                // A whole row flinching is too much movement; the tonal wash is the
                // feedback here.
                indication = kontourIndication(indicationShape, pressScale = 1f),
                enabled = enabled,
                role = role,
                onClick = onClick,
            )

        else -> Modifier.pointerCursor(enabled = enabled).clickable(
            interactionSource = interactions,
            indication = kontourIndication(indicationShape, pressScale = 1f),
            enabled = enabled,
            role = role,
            onClick = onClick,
        )
    }

    val resolvedMinHeight = when {
        minHeight != Dp.Unspecified -> minHeight
        slots.isMultiLine -> ListItemDefaults.TwoLineMinHeight
        else -> ListItemDefaults.MinHeight
    }

    // Published so a control in a slot can show the row's press — a `Switch` in
    // a trailing slot has no callback of its own and would otherwise never see
    // the tap that the row just handled.
    // And the row is one node, named by what it says: an avatar in its leading
    // slot stays quiet rather than becoming that name. See `LocalNamedByContent`.
    CompositionLocalProvider(
        LocalRowInteractionSource provides interactions,
        LocalRowEnabled provides enabled,
        LocalNamedByContent provides true,
    ) {
        // A row with an `onClick` is a control, and its text is chrome; one
        // without is content — a stop's address, a fare — and stays selectable
        // inside a `SelectableText`.
        ExcludeFromSelection(excluded = controls) {
            Row(
                // Outside the caller's modifier, so a row given a margin is still
                // found as a row by the section it sits in.
                modifier = ListRowEdges
                    .then(modifier)
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) {}
                    .defaultMinSize(minHeight = resolvedMinHeight)
                    // Keyed on being a control at all, not on being enabled — a row that
                    // changed height when it greyed out would jump the list around it.
                    .then(if (controls) Modifier.minimumTouchTarget() else Modifier)
                    .focusRing(interactions, shape, enabled = interactive)
                    .clip(shape)
                    .background(container, shape)
                    // `surfaceSunken` on `background` is **1.06:1** at the dark
                    // enhanced tier and **1.14** at light's — a group of rows reads as
                    // loose text rather than as one object with rows in it, which is
                    // the exact thing the sunken ground exists to prevent. A well is
                    // deliberately quiet, so at the tier that asks for more it is the
                    // edge rather than the fill that has to say where a row ends.
                    .then(if (edged) contrastEdge()?.let { Modifier.border(it, shape) } ?: Modifier else Modifier)
                    // A selected row is a tint 1.25:1 from the page at the
                    // enhanced tier; there it is outlined in the accent as well,
                    // over whatever edge the row already has.
                    .optionalBorder(if (selected && enabled) selectionEdge() else null, shape)
                    .then(clickModifier)
                    // `xs`, not `sm`. The 12dp version put 24dp of air around a
                    // single line of text inside a row whose minimum is already 56dp,
                    // so the padding was never what set the height — it only pushed a
                    // two-line row taller than it needed to be. `MinHeight` holds the
                    // floor for the one-line case, which is what it is for.
                    .padding(start = startPadding, end = Theme.spacing.md)
                    .padding(vertical = Theme.spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(Theme.spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val muted = if (enabled) colours.contentMuted else colours.contentDisabled

                slots.leading?.let { leading ->
                    Box(contentAlignment = Alignment.Center) {
                        // Muted and `iconLarge`, which is what the old `leadingIcon`
                        // parameter did. Anything that is not a bare glyph — an avatar, a
                        // checkbox — sets its own colours and is unaffected.
                        ProvideContentColour(muted) {
                            ContentSlot(iconSize = Theme.sizing.iconLarge, content = leading)
                        }
                    }
                }

                Column(Modifier.weight(1f)) {
                    slots.overline?.let { overline ->
                        ProvideContentColour(muted) {
                            ProvideTextStyle(Theme.typography.labelSmall) {
                                ContentSlot(maxLines = 1, content = overline)
                            }
                        }
                    }
                    slots.label?.let { label ->
                        ProvideContentColour(content) {
                            ProvideTextStyle(Theme.typography.bodyMedium) {
                                if (labelModifier == Modifier) {
                                    ContentSlot(maxLines = 2, content = label)
                                } else {
                                    Box(labelModifier) { ContentSlot(maxLines = 2, content = label) }
                                }
                            }
                        }
                    }
                    slots.supporting?.let { supporting ->
                        ProvideContentColour(muted) {
                            ProvideTextStyle(Theme.typography.bodySmall) {
                                ContentSlot(maxLines = 2, content = supporting)
                            }
                        }
                    }
                }

                slots.trailing?.let { trailing ->
                    Box(contentAlignment = Alignment.Center) {
                        // A value — "4 min", "Kilometres" — in the row's muted
                        // body style, dimming with the row. A control in the slot
                        // sets its own colours and is unaffected.
                        ProvideContentColour(muted) {
                            ProvideTextStyle(Theme.typography.bodyMedium) {
                                ContentSlot(maxLines = 1, content = trailing)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * A titled group of [ListItem]s, or of anything else.
 *
 * ```kotlin
 * ListSection(title = { +"Notifications" }) {
 *     SelectionRow(delays, viewModel::setDelays, Role.Switch) { +"Delays" }
 *     SelectionRow(cancels, viewModel::setCancels, Role.Switch) { +"Cancellations" }
 * }
 * ```
 *
 * Spaces its children and marks the title as a heading, so a screen reader can
 * jump between sections. It does *not* set each child's [GroupPosition] —
 * doing that would mean walking the composed children, which Compose has no way
 * to do. Use [listPositions] or [GroupPosition.of] at the call site.
 *
 * ### Where the title starts
 *
 * Over rows, the title, its supporting line and the footer start
 * [ListItemDefaults.ContentInset] in, where the rows' own content does, so the
 * words make one column down the section. Over anything else — a paragraph, a
 * field, a chart, a card, a row of buttons — there is no such column, and a
 * title 16dp in from a paragraph at the edge reads as a mistake. So the title
 * lines up with whatever is first under it, and the footer with whatever is
 * last above it: in from a row, flush with anything else.
 *
 * A [ListItem] is a row, and so is anything built on one — `SelectionRow`,
 * `DisclosureListItem`, `ExpandingListItem`, a row in `SwipeActions`. A row
 * inside a padded card is not, because the card is what is first. It is a
 * measurement rather than a guess from the types: a row reports its own top and
 * bottom to the layout, and the section asks whether its first child starts
 * with one and its last child ends with one. [textInset] sets the inset outright
 * where that reading is not the one wanted.
 *
 * @param supporting Sits under the title, above the rows. For what the group
 *   *is*.
 * @param footer Sits under the rows. For what the setting *does* — the sentence
 *   a settings screen puts below a switch to explain the consequence of it. A
 *   slot like [supporting] because it is the same kind of thing, and the two
 *   should not read as different mechanisms.
 * @param action Holds *controls* rather than the section's own text, which is
 *   why it is a plain `RowScope` and the three above are content slots.
 * @param spacing The gap between the children, and under the heading. The
 *   default is the hairline that makes a group of rows one object. Form fields
 *   are not rows: give them `Theme.spacing.md`, or they stack edge to edge.
 * @param textInset How far in from the section's start edge the title, its
 *   supporting line and the footer start. Unspecified lines each up with the
 *   content beside it, as above: [ListItemDefaults.ContentInset] next to a row,
 *   and none next to anything else. `0.dp` is flush whatever the content is.
 */
@Composable
fun ListSection(
    modifier: Modifier = Modifier,
    title: (@Composable ContentScope.() -> Unit)? = null,
    supporting: (@Composable ContentScope.() -> Unit)? = null,
    footer: (@Composable ContentScope.() -> Unit)? = null,
    action: (@Composable RowScope.() -> Unit)? = null,
    spacing: Dp = ListItemDefaults.Spacing,
    textInset: Dp = Dp.Unspecified,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Read here, because the measure block below is not composable.
    val rowInset = ListItemDefaults.ContentInset
    Layout(
        modifier = modifier.fillMaxWidth(),
        content = {
            if (title != null || action != null) {
                SectionHeaderImpl(
                    modifier = Modifier.layoutId(SectionPart.Header),
                    supporting = supporting,
                    action = action,
                    // The section places the header at its inset instead, once
                    // it has seen what is under it.
                    startPadding = 0.dp,
                    // An action with no title used to draw an empty `Text`, which
                    // took a line's height to say nothing. An empty slot draws
                    // nothing at all.
                    title = title ?: {},
                )
            }
            // The children in a `Column` of their own, so `weight` and `align`
            // in the content still mean what they do in any column.
            Column(
                modifier = Modifier.layoutId(SectionPart.Body).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(spacing),
                content = content,
            )
            if (footer != null) {
                SectionFooter(Modifier.layoutId(SectionPart.Footer), startPadding = 0.dp, content = footer)
            }
        },
        measurePolicy = remember(spacing, textInset, rowInset) { SectionMeasurePolicy(spacing, textInset, rowInset) },
    )
}

/**
 * How a [ListSection] stacks its header, its body and its footer, and how far
 * in it starts the header's and the footer's words: see `ListSection`.
 */
private class SectionMeasurePolicy(
    private val spacing: Dp,
    private val textInset: Dp,
    private val rowInset: Dp,
) : MeasurePolicy {

    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
        val headerPart = measurables.firstOrNull { it.part == SectionPart.Header }
        val bodyPart = measurables.first { it.part == SectionPart.Body }
        val footerPart = measurables.firstOrNull { it.part == SectionPart.Footer }
        val gap = spacing.roundToPx()
        val given = if (textInset.isSpecified) textInset.roundToPx() else null
        val beside = rowInset.roundToPx()
        val loose = constraints.copy(minHeight = 0)

        // **The body has what the header and the footer leave it**, where there
        // is a height to leave. It was measured first, against the whole of
        // it, so a `weight(1f)` in a section of a fixed height took all of it,
        // the header and footer were stacked on top, and the footer was drawn
        // past the section's bottom over whatever was under it. The two are
        // measured after the body, since where their words start depends on
        // it; what they will take is asked first, at the narrower of the two
        // widths they can have, so it is never less than they take.
        val bodyConstraints = if (constraints.hasBoundedHeight) {
            val width = constraints.maxWidth.narrowedBy(given ?: beside)
            val reserved = listOfNotNull(headerPart, footerPart).sumOf { it.heightAt(width) + gap }
            loose.copy(maxHeight = (constraints.maxHeight - reserved).coerceAtLeast(0))
        } else {
            loose
        }
        val body = bodyPart.measure(bodyConstraints)

        val headerInset = given ?: if (body.startsWithRow()) beside else 0
        val footerInset = given ?: if (body.endsWithRow()) beside else 0

        val header = headerPart?.measure(loose.offset(horizontal = -headerInset))
        val footer = footerPart?.measure(loose.offset(horizontal = -footerInset))

        // The gaps `Arrangement.spacedBy` put between the three when they were
        // children of one column, so a section of rows is where it always was.
        // An empty body is not a child: it would have been none at all.
        val stacked = listOfNotNull(header, body.takeIf { it.height > 0 }, footer)
        val height = stacked.sumOf { it.height } + gap * max(stacked.size - 1, 0)
        val width = max(
            body.width,
            max((header?.width ?: 0) + headerInset, (footer?.width ?: 0) + footerInset),
        )

        return layout(constraints.constrainWidth(width), constraints.constrainHeight(height)) {
            var y = 0
            header?.let {
                it.placeRelative(headerInset, y)
                y += it.height + gap
            }
            if (body.height > 0) {
                body.placeRelative(0, y)
                y += body.height + gap
            }
            footer?.placeRelative(footerInset, y)
        }
    }

    // **Intrinsics assume the header and the footer are beside rows**, unless
    // told the inset. Left to the measure above, they were worked out with no
    // alignment lines to read — an intrinsic measurement has none — so every
    // body read as not a row and the words as flush: a title that wraps only
    // at the rows' inset was counted a line short, and a section under
    // `IntrinsicSize.Min` was cut off at its bottom. The inset is the narrower
    // case, and an intrinsic height is a promise of room, so it is the one to
    // promise.
    override fun IntrinsicMeasureScope.minIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int =
        stackedHeight(measurables, width) { child, w -> child.minIntrinsicHeight(w) }

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int =
        stackedHeight(measurables, width) { child, w -> child.maxIntrinsicHeight(w) }

    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int =
        widest(measurables) { child -> child.minIntrinsicWidth(height) }

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int =
        widest(measurables) { child -> child.maxIntrinsicWidth(height) }

    /** The inset an intrinsic assumes: the one given, or a row's. */
    private fun IntrinsicMeasureScope.assumedInset(): Int =
        if (textInset.isSpecified) textInset.roundToPx() else rowInset.roundToPx()

    private inline fun IntrinsicMeasureScope.stackedHeight(
        measurables: List<IntrinsicMeasurable>,
        width: Int,
        heightOf: (IntrinsicMeasurable, Int) -> Int,
    ): Int {
        val text = width.narrowedBy(assumedInset())
        val header = measurables.firstOrNull { it.part == SectionPart.Header }?.let { heightOf(it, text) }
        val body = heightOf(measurables.first { it.part == SectionPart.Body }, width).takeIf { it > 0 }
        val footer = measurables.firstOrNull { it.part == SectionPart.Footer }?.let { heightOf(it, text) }
        val stacked = listOfNotNull(header, body, footer)
        return stacked.sum() + spacing.roundToPx() * max(stacked.size - 1, 0)
    }

    private inline fun IntrinsicMeasureScope.widest(
        measurables: List<IntrinsicMeasurable>,
        widthOf: (IntrinsicMeasurable) -> Int,
    ): Int {
        val inset = assumedInset()
        return measurables.maxOfOrNull { child ->
            widthOf(child) + if (child.part == SectionPart.Body) 0 else inset
        } ?: 0
    }
}

/**
 * Which of a section's parts this is. `layoutId` reads it off a `Measurable`
 * only, and an intrinsic measurement is handed the parts as less than that.
 */
private val IntrinsicMeasurable.part: Any?
    get() = (parentData as? LayoutIdParentData)?.layoutId

/** This width less [inset], or still unbounded where it was. */
private fun Int.narrowedBy(inset: Int): Int =
    if (this == Constraints.Infinity) this else (this - inset).coerceAtLeast(0)

/**
 * How tall this would be at [width], for the room a [ListSection] leaves its
 * body; nothing where it cannot say. A `SubcomposeLayout` — a
 * `BoxWithConstraints` in a section's action — refuses an intrinsic
 * measurement outright, and a section that threw for one would be worse than
 * one whose weighted content runs on as it used to.
 */
private fun IntrinsicMeasurable.heightAt(width: Int): Int =
    try {
        maxIntrinsicHeight(width)
    } catch (_: IllegalStateException) {
        0
    }

/** Which of [ListSection]'s three parts a measurable is. */
private enum class SectionPart { Header, Body, Footer }

/**
 * The top and bottom edges of a row's ground, as a row reports them to whatever
 * lays it out.
 *
 * This is how a [ListSection] tells a row from a paragraph without knowing
 * anything about either. An alignment line is passed up through every layout
 * between the row and the one that asks — a `SwipeActions`, a caller's `Column`
 * — offset to where the row sits in it, so the section only has to ask its own
 * column whether a row's top is at its top. Across siblings the topmost top and
 * the bottommost bottom win, which is the first and the last row.
 *
 * Its top and bottom rather than its start edge, deliberately: a row being
 * swiped moves sideways, and a section reading where the row starts would have
 * moved its title along with the finger.
 */
private val ListRowTop = HorizontalAlignmentLine(::min)
private val ListRowBottom = HorizontalAlignmentLine(::max)

/**
 * Reports its node's top and bottom as a row's — see [ListRowTop]. On every
 * [ListItem], and on the cards that hold one with more under it, so a section
 * still reads one as a row while it is open.
 *
 * One instance for the whole library, with nothing captured, so it compares
 * equal across recompositions and never makes a row's modifiers look changed.
 */
internal val ListRowEdges: Modifier = Modifier.layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(
        placeable.width,
        placeable.height,
        mapOf(ListRowTop to 0, ListRowBottom to placeable.height),
    ) {
        placeable.placeRelative(0, 0)
    }
}

/** Whether a row is the first thing in this placeable, flush with its top. */
private fun Placeable.startsWithRow(): Boolean {
    val top = this[ListRowTop]
    return top != AlignmentLine.Unspecified && top <= 0
}

/** Whether a row is the last thing in this placeable, flush with its bottom. */
private fun Placeable.endsWithRow(): Boolean {
    val bottom = this[ListRowBottom]
    return bottom != AlignmentLine.Unspecified && bottom >= height
}

/**
 * The sentence under a section's rows: [ListSection]'s `footer`, and
 * `listSection`'s in a lazy list, drawn one way for both.
 *
 * @param startPadding In from the start edge, where [ListSection] does not
 *   place it at an inset of its own.
 */
@Composable
internal fun SectionFooter(
    modifier: Modifier = Modifier,
    startPadding: Dp = ListItemDefaults.ContentInset,
    content: @Composable ContentScope.() -> Unit,
) {
    Box(
        modifier.padding(
            start = startPadding,
            end = Theme.spacing.md,
            top = Theme.spacing.xxs,
        )
    ) {
        ProvideTextStyle(Theme.typography.bodySmall) {
            ProvideContentColour(Theme.colours.contentMuted) {
                ContentSlot(content = content)
            }
        }
    }
}

/**
 * A heading above a group of rows.
 *
 * Starts [ListItemDefaults.ContentInset] in, where the rows' content does.
 * Inside a [ListSection] that is decided by what the section holds — see its
 * `textInset`.
 */
@Composable
fun SectionHeader(
    modifier: Modifier = Modifier,
    supporting: (@Composable ContentScope.() -> Unit)? = null,
    action: (@Composable RowScope.() -> Unit)? = null,
    title: @Composable ContentScope.() -> Unit,
) {
    SectionHeaderImpl(
        modifier = modifier,
        supporting = supporting,
        action = action,
        startPadding = ListItemDefaults.ContentInset,
        title = title,
    )
}

/** [SectionHeader], with its start padding left to the caller. */
@Composable
private fun SectionHeaderImpl(
    modifier: Modifier,
    supporting: (@Composable ContentScope.() -> Unit)?,
    action: (@Composable RowScope.() -> Unit)?,
    startPadding: Dp,
    title: @Composable ContentScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = startPadding,
                end = Theme.spacing.xs,
                top = Theme.spacing.md,
                bottom = Theme.spacing.xxs,
            ),
        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            // `mergeDescendants` so the heading node carries the title's text,
            // the way it did when the title was a `Text` wearing the modifier
            // itself. A heading a screen reader can find but not read is worse
            // than no heading.
            Box(Modifier.semantics(mergeDescendants = true) { heading() }) {
                ProvideTextStyle(Theme.typography.labelMedium) {
                    ProvideContentColour(Theme.colours.contentMuted) {
                        ContentSlot(content = title)
                    }
                }
            }
            if (supporting != null) {
                ProvideTextStyle(Theme.typography.bodySmall) {
                    ProvideContentColour(Theme.colours.contentSubtle) {
                        ContentSlot(content = supporting)
                    }
                }
            }
        }
        action?.invoke(this)
    }
}

/**
 * The positions for a list of [count] items.
 *
 * ```kotlin
 * val positions = listPositions(stops.size)
 * stops.forEachIndexed { index, stop ->
 *     ListItem(position = positions[index], …) { +stop.name }
 * }
 * ```
 *
 * Equivalent to calling [GroupPosition.of] per item; useful when the list is
 * short enough to build eagerly and you would rather not repeat the size.
 */
fun listPositions(count: Int): List<GroupPosition> =
    List(count) { index -> GroupPosition.of(index, count) }
