package io.kontour.ui.components.table

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.list.Scrollbar
import io.kontour.ui.components.selection.Checkbox
import io.kontour.ui.components.selection.TriStateCheckbox
import io.kontour.ui.foundation.ContentSlot
import io.kontour.ui.foundation.Icon
import io.kontour.ui.foundation.ProvideContentColour
import io.kontour.ui.foundation.ProvideTextStyle
import io.kontour.ui.foundation.SystemIcons
import io.kontour.ui.foundation.Text
import io.kontour.ui.input.pointerCursor
import io.kontour.ui.input.rememberFocusRingVisible
import io.kontour.ui.interaction.LocalRowInteractionSource
import io.kontour.ui.interaction.kontourIndication
import io.kontour.ui.theme.Theme

/** Everything a row needs to know about the table it is in. */
internal data class TableEnv<T>(
    val columns: List<TableColumn<T>>,
    val leading: Boolean,
    val layout: TableColumnsLayout,
    val scroll: TableHorizontalScroll,
    val colours: TableColours,
    val lines: TableLines,
    val enabled: Boolean,
    val striped: Boolean,
    val sort: TableSort?,
    val onSortChange: ((TableSort) -> Unit)?,
    val selection: TableSelection,
    val selected: Set<Any>,
    val onSelectedChange: ((Set<Any>) -> Unit)?,
    val onRowClick: ((T) -> Unit)?,
    val rowAnnouncement: ((T) -> String)?,
    val keyOf: (T) -> Any,
    val key: ((T) -> Any)?,
    val rowMinHeight: Dp,
    val items: List<T>,
    val columnCount: Int,
    val rowCount: Int,
    val hasFooter: Boolean,
) {
    /** The declared column drawn at [index] of the layout, which counts the checkbox column first. */
    fun column(index: Int): TableColumn<T> = columns[if (leading) index - 1 else index]

    fun isCheckbox(index: Int): Boolean = leading && index == 0
}

/**
 * The header, the rows and the footer, one above the next.
 *
 * The header and footer are outside the list, so both stay put while the rows
 * scroll between them — in a table given a height. One that is not, inside a
 * page that scrolls, composes every row and scrolls with the page.
 */
@Composable
internal fun <T> TableFrame(env: TableEnv<T>, listState: LazyListState, boundedHeight: Boolean) {
    Column {
        HeaderRow(env)
        if (boundedHeight) {
            Box(Modifier.weight(1f, fill = false)) {
                LazyColumn(state = listState) {
                    itemsIndexed(
                        items = env.items,
                        key = env.key?.let { key -> { _: Int, item: T -> key(item) } },
                        contentType = { _, _ -> TableRowType },
                    ) { index, item ->
                        BodyRow(env, index, item)
                    }
                }
                Scrollbar(listState, Modifier.align(Alignment.CenterEnd))
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .width(with(LocalDensity.current) { env.layout.paneWidth.toDp() }),
                ) {
                    Scrollbar(env.scroll, orientation = Orientation.Horizontal)
                }
            }
        } else {
            env.items.forEachIndexed { index, item ->
                key(env.keyOf(item)) { BodyRow(env, index, item) }
            }
        }
        if (env.hasFooter) FooterRow(env)
    }
}

@Composable
private fun <T> HeaderRow(env: TableEnv<T>) {
    TableRowLayout(
        env,
        Modifier
            .background(env.colours.header)
            .tableRules(env, top = null, bottom = env.colours.headerLine),
    ) { index -> HeaderCell(env, index) }
}

@Composable
private fun <T> HeaderCell(env: TableEnv<T>, index: Int) {
    val strings = Theme.strings
    if (env.isCheckbox(index)) {
        val keys = env.items.map(env.keyOf)
        val all = keys.isNotEmpty() && keys.all { it in env.selected }
        val state = when {
            all -> ToggleableState.On
            keys.any { it in env.selected } -> ToggleableState.Indeterminate
            else -> ToggleableState.Off
        }
        Box(
            Modifier.semantics { collectionItemInfo = CollectionItemInfo(0, 1, index, 1) },
            contentAlignment = Alignment.Center,
        ) {
            TriStateCheckbox(
                state = state,
                // From part-way, a tap selects the rest; from all, it clears them.
                onClick = { env.onSelectedChange?.invoke(if (all) env.selected - keys.toSet() else env.selected + keys) },
                enabled = env.enabled && keys.isNotEmpty(),
                modifier = Modifier.semantics { contentDescription = strings.selectAll },
            )
        }
        return
    }
    val column = env.column(index)
    val onSortChange = env.onSortChange
    val sortable = onSortChange != null && column.sortable
    val sorted = env.sort?.takeIf { it.column == column.key }
    val interactions = remember { MutableInteractionSource() }
    HeaderCellContent(
        env,
        column,
        Modifier
            .semantics(mergeDescendants = true) {
                heading()
                collectionItemInfo = CollectionItemInfo(0, 1, index, 1)
                if (sorted != null) {
                    stateDescription = if (sorted.direction == SortDirection.Ascending) {
                        strings.sortedAscending
                    } else {
                        strings.sortedDescending
                    }
                }
            }
            .then(
                if (onSortChange != null && sortable) {
                    Modifier.pointerCursor(enabled = env.enabled).clickable(
                        interactionSource = interactions,
                        indication = kontourIndication(RectangleShape, pressScale = 1f),
                        enabled = env.enabled,
                        role = Role.Button,
                    ) { onSortChange(env.sort?.toggled(column.key) ?: TableSort(column.key)) }
                } else {
                    Modifier
                },
            )
            .insetFocusRing(interactions, enabled = sortable && env.enabled),
    )
}

/**
 * A column's title, and the arrow that says which way it is sorted — whose room
 * is kept even while it is not, so sorting by a column does not widen it.
 */
@Composable
internal fun <T> HeaderCellContent(env: TableEnv<T>, column: TableColumn<T>, modifier: Modifier = Modifier) {
    val sortable = env.onSortChange != null && column.sortable
    val sorted = env.sort?.takeIf { it.column == column.key }
    val colour = if (sorted != null) env.colours.content else env.colours.headerContent
    val arrow: @Composable () -> Unit = {
        Box(Modifier.size(Theme.sizing.iconSmall), contentAlignment = Alignment.Center) {
            if (sorted != null) {
                Icon(
                    imageVector = if (sorted.direction == SortDirection.Ascending) {
                        SystemIcons.SortAscending
                    } else {
                        SystemIcons.SortDescending
                    },
                    contentDescription = null,
                    tint = colour,
                    size = Theme.sizing.iconSmall,
                )
            }
        }
    }
    Box(modifier.cellPadding(), contentAlignment = column.align.alignment) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Before the title in a column set against its end, so the title's
            // end lines up with the figures under it.
            if (sortable && column.align == TableAlign.End) arrow()
            Text(
                column.title,
                colour = colour,
                style = Theme.typography.labelMedium,
                maxLines = HeaderMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
            if (sortable && column.align != TableAlign.End) arrow()
        }
    }
}

@Composable
private fun <T> BodyRow(env: TableEnv<T>, index: Int, item: T) {
    val interactions = remember { MutableInteractionSource() }
    val key = env.keyOf(item)
    val isSelected = key in env.selected
    val colours = env.colours
    val onRowClick = env.onRowClick
    val onSelectedChange = env.onSelectedChange
    val ground = when {
        isSelected -> colours.selected
        env.striped && index % 2 == 1 -> colours.stripe
        else -> Color.Transparent
    }
    val contentColour = when {
        !env.enabled -> Theme.colours.contentDisabled
        isSelected -> colours.selectedContent
        else -> colours.content
    }
    val indication = kontourIndication(RectangleShape, pressScale = 1f)
    val toggle = { onSelectedChange?.invoke(if (isSelected) env.selected - key else env.selected + key) }
    // A row that does nothing has no click modifier at all; one that does keeps
    // its modifier while disabled, so it still announces as disabled.
    val click = when {
        env.selection == TableSelection.Single -> Modifier.pointerCursor(enabled = env.enabled).selectable(
            selected = isSelected,
            interactionSource = interactions,
            indication = indication,
            enabled = env.enabled,
            role = null,
        ) {
            onSelectedChange?.invoke(setOf(key))
            onRowClick?.invoke(item)
        }
        env.selection == TableSelection.Multiple && onRowClick == null -> Modifier
            .pointerCursor(enabled = env.enabled)
            .toggleable(
                value = isSelected,
                interactionSource = interactions,
                indication = indication,
                enabled = env.enabled,
                role = Role.Checkbox,
            ) { toggle() }
        onRowClick != null -> Modifier.pointerCursor(enabled = env.enabled).clickable(
            interactionSource = interactions,
            indication = indication,
            enabled = env.enabled,
            role = Role.Button,
        ) { onRowClick(item) }
        else -> null
    }
    val announcement = env.rowAnnouncement?.invoke(item)

    // Published so the checkbox at the start of the row shows the row's press.
    CompositionLocalProvider(LocalRowInteractionSource provides interactions) {
        ProvideContentColour(contentColour) {
            TableRowLayout(
                env,
                Modifier
                    .semantics(mergeDescendants = true) {
                        collectionItemInfo = CollectionItemInfo(index + 1, 1, 0, env.columnCount)
                        if (announcement != null) contentDescription = announcement
                    }
                    .background(ground)
                    .then(click ?: Modifier)
                    .insetFocusRing(interactions, enabled = click != null && env.enabled)
                    .tableRules(
                        env,
                        top = null,
                        bottom = if (env.lines != TableLines.None && index < env.items.lastIndex) colours.lines else null,
                    ),
            ) { column ->
                if (env.isCheckbox(column)) {
                    Box(contentAlignment = Alignment.Center) {
                        if (onRowClick == null) {
                            // The row is the control; this only shows its state.
                            Checkbox(checked = isSelected, onCheckedChange = null, enabled = env.enabled)
                        } else {
                            // The row opens; the checkbox picks, and needs a name.
                            val name = announcement ?: Theme.strings.selectRow
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { toggle() },
                                enabled = env.enabled,
                                modifier = Modifier.semantics { contentDescription = name },
                            )
                        }
                    }
                } else {
                    BodyCellContent(
                        env,
                        env.column(column),
                        item,
                        if (announcement != null) Modifier.clearAndSetSemantics {} else Modifier,
                    )
                }
            }
        }
    }
}

/** A cell: the column's content for [item], in the table's type — tabular figures for a numeric column. */
@Composable
internal fun <T> BodyCellContent(env: TableEnv<T>, column: TableColumn<T>, item: T, modifier: Modifier = Modifier) {
    Box(modifier.cellPadding(), contentAlignment = column.align.alignment) {
        ProvideTextStyle(Theme.typography.bodyMedium.figures(column.numeric)) {
            ContentSlot(iconSize = Theme.sizing.iconSmall, maxLines = column.maxLines, overflow = TextOverflow.Ellipsis) {
                column.cell(this, item)
            }
        }
    }
}

@Composable
private fun <T> FooterRow(env: TableEnv<T>) {
    ProvideContentColour(env.colours.content) {
        TableRowLayout(
            env,
            Modifier
                .semantics(mergeDescendants = true) {
                    collectionItemInfo = CollectionItemInfo(env.rowCount - 1, 1, 0, env.columnCount)
                }
                .background(env.colours.header)
                .tableRules(env, top = env.colours.headerLine, bottom = null),
        ) { index ->
            if (env.isCheckbox(index)) Box(Modifier) else FooterCellContent(env, env.column(index))
        }
    }
}

@Composable
internal fun <T> FooterCellContent(env: TableEnv<T>, column: TableColumn<T>, modifier: Modifier = Modifier) {
    val footer = column.footer
    Box(modifier.cellPadding(), contentAlignment = column.align.alignment) {
        if (footer != null) {
            ProvideTextStyle(Theme.typography.bodyMedium.figures(column.numeric).copy(fontWeight = FontWeight.SemiBold)) {
                ContentSlot(iconSize = Theme.sizing.iconSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, content = footer)
            }
        }
    }
}

/**
 * A row: the pinned columns at the start, and the rest in a pane beside them
 * that slides under nothing — it is clipped to its own width — as the table
 * scrolls across. Every cell is as tall as the row, so a row's ground and its
 * press reach from edge to edge, and a cell's content sits in its middle.
 */
@Composable
private fun TableRowLayout(env: TableEnv<*>, modifier: Modifier, cell: @Composable (column: Int) -> Unit) {
    val layout = env.layout
    val scroll = env.scroll
    val pinnedPolicy = remember(layout) {
        CellGroupPolicy(
            widths = IntArray(layout.pinned) { layout.widths[it] },
            starts = IntArray(layout.pinned) { layout.starts[it] },
            scroll = null,
        )
    }
    val panePolicy = remember(layout, scroll) {
        val first = layout.pinned
        CellGroupPolicy(
            widths = IntArray(layout.count - first) { layout.widths[first + it] },
            starts = IntArray(layout.count - first) { layout.starts[first + it] - layout.pinnedWidth },
            scroll = scroll,
        )
    }
    val minHeight = env.rowMinHeight
    Layout(
        content = {
            Layout(content = { for (column in 0 until layout.pinned) cell(column) }, measurePolicy = pinnedPolicy)
            Layout(
                content = { for (column in layout.pinned until layout.count) cell(column) },
                modifier = Modifier.clipToBounds(),
                measurePolicy = panePolicy,
            )
        },
        modifier = modifier,
    ) { measurables, _ ->
        val height = maxOf(
            minHeight.roundToPx(),
            measurables[0].maxIntrinsicHeight(layout.pinnedWidth),
            measurables[1].maxIntrinsicHeight(layout.paneWidth),
        )
        val pinned = measurables[0].measure(Constraints.fixed(layout.pinnedWidth, height))
        val pane = measurables[1].measure(Constraints.fixed(layout.paneWidth, height))
        layout(layout.viewport, height) {
            pinned.placeRelative(0, 0)
            pane.placeRelative(layout.pinnedWidth, 0)
        }
    }
}

/**
 * Lays one group of a row's cells side by side, each at its column's width and
 * the group's height; the pane's shifted back by how far the table is scrolled,
 * which is read here, when the cells are placed, so scrolling moves them without
 * composing or measuring anything.
 */
private class CellGroupPolicy(
    private val widths: IntArray,
    private val starts: IntArray,
    private val scroll: TableHorizontalScroll?,
) : MeasurePolicy {

    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
        val height = if (constraints.hasFixedHeight) {
            constraints.maxHeight
        } else {
            measurables.mapIndexed { index, it -> it.maxIntrinsicHeight(widths[index]) }
                .maxOrNull()?.coerceAtLeast(constraints.minHeight) ?: constraints.minHeight
        }
        val placeables = measurables.mapIndexed { index, it -> it.measure(Constraints.fixed(widths[index], height)) }
        return layout(constraints.maxWidth, height) {
            val shift = scroll?.value ?: 0
            placeables.forEachIndexed { index, it -> it.placeRelative(starts[index] - shift, 0) }
        }
    }

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int =
        measurables.mapIndexed { index, it -> it.maxIntrinsicHeight(widths[index]) }.maxOrNull() ?: 0

    override fun IntrinsicMeasureScope.minIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int =
        measurables.mapIndexed { index, it -> it.minIntrinsicHeight(widths[index]) }.maxOrNull() ?: 0
}

/**
 * The rules a row draws over itself: along its top or bottom edge, between its
 * columns in a grid, and at the pinned columns' edge whenever something has
 * scrolled under it — read in draw, so scrolling redraws the rules and nothing
 * else.
 */
private fun Modifier.tableRules(env: TableEnv<*>, top: Color?, bottom: Color?): Modifier = drawWithContent {
    drawContent()
    val hair = HairlineWidth.toPx()
    val layout = env.layout
    val shift = env.scroll.value
    val rtl = layoutDirection == LayoutDirection.Rtl
    fun x(at: Float) = if (rtl) size.width - at else at
    fun vertical(at: Float) = drawLine(env.colours.lines, Offset(x(at), 0f), Offset(x(at), size.height), hair)
    if (top != null) drawLine(top, Offset(0f, hair / 2f), Offset(size.width, hair / 2f), hair)
    if (bottom != null) {
        drawLine(bottom, Offset(0f, size.height - hair / 2f), Offset(size.width, size.height - hair / 2f), hair)
    }
    if (env.lines == TableLines.Grid) {
        for (index in 0 until layout.count - 1) {
            if (index == layout.pinned - 1) continue
            if (index < layout.pinned) {
                vertical(layout.end(index).toFloat())
            } else {
                val end = (layout.end(index) - shift).toFloat()
                if (end > layout.pinnedWidth && end < layout.viewport) vertical(end)
            }
        }
    }
    if (layout.pinned > 0 && (env.lines == TableLines.Grid || shift > 0)) vertical(layout.pinnedWidth.toFloat())
}

/**
 * A focus ring drawn inside the row rather than around it: around it, the next
 * row down would paint over its bottom edge.
 */
@Composable
private fun Modifier.insetFocusRing(interactions: InteractionSource, enabled: Boolean): Modifier {
    val visible = rememberFocusRingVisible(interactions) && enabled
    if (!visible) return this
    val colour = Theme.colours.focusRing
    val width = Theme.sizing.focusRingWidth
    return drawWithContent {
        drawContent()
        val stroke = width.toPx()
        drawRect(
            colour,
            topLeft = Offset(stroke / 2f, stroke / 2f),
            size = Size(size.width - stroke, size.height - stroke),
            style = Stroke(stroke),
        )
    }
}

@Composable
private fun Modifier.cellPadding(): Modifier =
    padding(horizontal = TableDefaults.CellPadding, vertical = Theme.spacing.xs)

private val TableAlign.alignment: Alignment
    get() = when (this) {
        TableAlign.Start -> Alignment.CenterStart
        TableAlign.Center -> Alignment.Center
        TableAlign.End -> Alignment.CenterEnd
    }

/** Tabular figures for a numeric column, so its digits line up down the rows. */
private fun androidx.compose.ui.text.TextStyle.figures(numeric: Boolean) =
    if (numeric) copy(fontFeatureSettings = listOfNotNull(fontFeatureSettings, TabularFigures).joinToString(",")) else this

private const val TabularFigures = "tnum"
private const val HeaderMaxLines = 2
private const val TableRowType = "tableRow"
private val HairlineWidth: Dp = 1.dp
