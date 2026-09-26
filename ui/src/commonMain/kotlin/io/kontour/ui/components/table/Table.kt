package io.kontour.ui.components.table

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.LayoutScopeMarker
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.ScrollAxisRange
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.horizontalScrollAxisRange
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.scrollBy
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import io.kontour.ui.components.selection.CheckboxDefaults
import io.kontour.ui.foundation.ContentScope
import io.kontour.ui.theme.Theme
import kotlinx.coroutines.launch

/** Where a column's cells sit across their width. */
enum class TableAlign {
    /** Against the start edge. Text. */
    Start,

    /** In the middle. A status icon, a short code. */
    Centre,

    /** Against the end edge. Figures, so their units line up. */
    End,
}

/** Which rules a [Table] draws between its cells. */
enum class TableLines {
    /** None between rows or columns. The header and footer are still ruled off. */
    None,

    /** A hairline between rows. */
    Rows,

    /** Between rows and between columns: a grid. */
    Grid,
}

/** How a [Table]'s rows are picked. */
enum class TableSelection {
    /** Not picked. Rows in `selected` are still tinted, to mark a current one. */
    None,

    /** One at a time: a tap picks the row. */
    Single,

    /** Any number: a checkbox at the start of each row, and one in the header for all of them. */
    Multiple,
}

/** Which way a [TableSort] runs. */
enum class SortDirection {
    /** Smallest first: A to Z, earliest first, lowest first. */
    Ascending,

    /** Largest first. */
    Descending,
}

/**
 * Which column a [Table] is sorted by, and which way — as the caller sorts it.
 *
 * @param column The key of the column, as given to `column(key = …)`: its title,
 *   unless it was given another.
 */
@Immutable
data class TableSort(val column: Any, val direction: SortDirection = SortDirection.Ascending) {
    /**
     * The sort after a tap on [column]'s header: the other way round if it is
     * this column, ascending by it if it is another.
     */
    fun toggled(column: Any): TableSort = if (column == this.column) {
        copy(
            direction = if (direction == SortDirection.Ascending) SortDirection.Descending else SortDirection.Ascending,
        )
    } else {
        TableSort(column)
    }
}

/**
 * The colours of a [Table].
 *
 * @param container Behind the whole table.
 * @param header Behind the header and the footer.
 * @param headerContent The column titles.
 * @param content The cells.
 * @param stripe Every other row, when striped.
 * @param selected A selected row.
 * @param selectedContent A selected row's cells.
 * @param lines The rules between rows and columns.
 * @param headerLine The rules under the header and over the footer.
 */
@Immutable
data class TableColours(
    val container: Color,
    val header: Color,
    val headerContent: Color,
    val content: Color,
    val stripe: Color,
    val selected: Color,
    val selectedContent: Color,
    val lines: Color,
    val headerLine: Color,
)

object TableDefaults {
    /** Clear on the page, with the list rows' sunken ground for stripes and their accent for selection. */
    @Composable
    @ReadOnlyComposable
    fun colours(
        container: Color = Color.Transparent,
        header: Color = Color.Transparent,
        headerContent: Color = Theme.colours.contentMuted,
        content: Color = Theme.colours.content,
        stripe: Color = Theme.colours.surfaceSunken,
        selected: Color = Theme.colours.accent.container,
        selectedContent: Color = Theme.colours.accent.onContainer,
        lines: Color = Theme.colours.outlineSubtle,
        headerLine: Color = Theme.colours.outline,
    ): TableColours = TableColours(
        container, header, headerContent, content, stripe, selected, selectedContent, lines, headerLine,
    )

    /**
     * A row's floor: a control's height, so a row that does something is one a
     * finger can hit — and the same on every platform, because a row's height is
     * how the table looks rather than how big its target is.
     */
    val RowMinHeight: Dp
        @Composable @ReadOnlyComposable get() = Theme.sizing.controlHeightMedium

    /** The space either side of a cell's content. */
    val CellPadding: Dp
        @Composable @ReadOnlyComposable get() = Theme.spacing.sm

    /** The narrowest a fitted or weighted column goes. */
    val MinColumnWidth: Dp get() = TableMinColumnWidth

    /** The checkbox column of a table with [TableSelection.Multiple]: a checkbox with a cell's padding either side. */
    val SelectionColumnWidth: Dp
        @Composable @ReadOnlyComposable get() = CheckboxDefaults.VisualSize + Theme.spacing.sm + Theme.spacing.sm

    /** An outlined table's corners: a card's. */
    val Shape: Shape
        @Composable @ReadOnlyComposable get() = Theme.shapes.container
}

/**
 * Where a [Table] is scrolled: down its rows, through [listState], and across its
 * columns.
 */
@Stable
class TableState internal constructor(
    /** Down the rows: which is first on screen, and how far it is scrolled. */
    val listState: LazyListState,
    initialHorizontalOffset: Int,
) {
    internal val horizontal = TableHorizontalScroll(initialHorizontalOffset)

    /** How far across the columns are scrolled, in px. */
    val horizontalOffset: Int get() = horizontal.value

    /** How far across they can scroll. Nought for a table that fits. */
    val maxHorizontalOffset: Int get() = horizontal.maxValue

    /** Scrolls the columns across to [offset] px, clamped to what there is. */
    suspend fun scrollHorizontallyTo(offset: Int) {
        horizontal.scroll { scrollBy((offset - horizontal.value).toFloat()) }
    }

    companion object {
        /** Saves both scroll positions, so a table comes back where it was left. */
        val Saver: Saver<TableState, *> = listSaver(
            save = {
                listOf(it.listState.firstVisibleItemIndex, it.listState.firstVisibleItemScrollOffset, it.horizontalOffset)
            },
            restore = { TableState(LazyListState(it[0], it[1]), it[2]) },
        )
    }
}

/** A [TableState] that survives recreation. */
@Composable
fun rememberTableState(): TableState = rememberSaveable(saver = TableState.Saver) {
    TableState(LazyListState(), 0)
}

/**
 * The columns of a [Table].
 *
 * ```kotlin
 * Table(items = departures) {
 *     column("Route", width = 72.dp) { +it.route }
 *     column("Destination", weight = 1f) { +it.destination }
 *     column("Departs", numeric = true) { +it.time }
 * }
 * ```
 *
 * **Collects rather than emits**, like `ListGroupScope`: a column's width depends
 * on the others', so nothing can be drawn until all of them are known. The
 * builder is plain Kotlin that runs in composition; the cells it names are
 * composables, run for each row as it is drawn.
 */
@Stable
@LayoutScopeMarker
class TableScope<T> internal constructor() {

    internal val columns = mutableListOf<TableColumn<T>>()

    /**
     * One column.
     *
     * Sized one of three ways: a fixed [width]; a [weight], for a share of any room
     * left once the others are sized, as a `Row`'s weights share it; or, given
     * neither, fitted to its title and its first rows' cells.
     *
     * @param title The header, which is also the key a sort names unless [key] says otherwise.
     * @param width A fixed width.
     * @param weight A share of the room left over. Nought for none.
     * @param minWidth The narrowest a weighted or fitted column goes.
     * @param maxWidth The widest it goes.
     * @param numeric Figures: set in tabular numerals, so a column of them lines
     *   up digit for digit, and against the end edge by default.
     * @param align Where the cells sit across the column.
     * @param sortable Whether a tap on the header sorts by this column, when the
     *   table takes `onSortChange`.
     * @param maxLines The most lines a cell wraps to before it is cut short.
     * @param key What a [TableSort] names this column by.
     * @param footer This column's cell in the footer row: a total, a count. The
     *   table has a footer when any column has one.
     * @param cell This column's cell for an item.
     */
    fun column(
        title: String,
        width: Dp = Dp.Unspecified,
        weight: Float = 0f,
        minWidth: Dp = Dp.Unspecified,
        maxWidth: Dp = Dp.Unspecified,
        numeric: Boolean = false,
        align: TableAlign = if (numeric) TableAlign.End else TableAlign.Start,
        sortable: Boolean = true,
        maxLines: Int = 1,
        key: Any = title,
        footer: (@Composable ContentScope.() -> Unit)? = null,
        cell: @Composable ContentScope.(item: T) -> Unit,
    ) {
        columns += TableColumn(title, width, weight, minWidth, maxWidth, numeric, align, sortable, maxLines, key, footer, cell)
    }
}

/**
 * Rows of records compared across columns: departures by route, platform and
 * time; trips by date, distance and fare.
 *
 * ```kotlin
 * Table(items = departures, stickyColumns = 1, striped = true) {
 *     column("Route", width = 72.dp) { +it.route }
 *     column("Destination", weight = 1f) { +it.destination }
 *     column("Departs", align = TableAlign.End) { +it.time }
 * }
 * ```
 *
 * ### Scrolling, and what stays put
 *
 * The rows scroll down under a **header that stays**, lazily, so a table of
 * thousands composes the few rows on screen. Columns wider than the table
 * scroll across together — header, rows and footer as one — while the first
 * [stickyColumns] stay pinned at the start, so a row keeps its name however far
 * across it is read. A footer row, if any column has one, stays at the bottom.
 *
 * ### Sorting is the caller's
 *
 * A tap on a sortable header calls [onSortChange] with the new [TableSort] —
 * this column the other way round, or ascending by a new one — and the header
 * shows an arrow for [sort]. The table sorts nothing itself: the caller sorts
 * [items] and passes them back, which is where a sort belongs when the data is
 * paged or comes from a query.
 *
 * ### Picking rows
 *
 * [selection] says whether rows are picked, and [selected] which are, by [key].
 * [TableSelection.Multiple] adds a checkbox column, pinned when it fits, with a
 * checkbox in the header for all of them. [onRowClick] opens a row whatever the
 * selection mode.
 *
 * @param items The rows, in the order they are shown.
 * @param enabled Whether rows and headers can be used. The table still scrolls.
 * @param key Each item's identity, for selection and for keeping a row's place as
 *   the items change. Null uses the item itself for selection and its index for
 *   place.
 * @param state Where the table is scrolled.
 * @param stickyColumns How many columns stay pinned at the start as the rest
 *   scroll across. Pinned columns never cover more than half the table; past that
 *   the last of them scroll too.
 * @param striped Every other row on a quiet ground, for reading across a wide row.
 * @param lines The rules between rows and between columns.
 * @param outlined A border round the whole table, with a card's corners.
 * @param sort The current sort, which the header shows.
 * @param onSortChange Makes headers sortable, and is handed the sort a tap asks for.
 * @param selection How rows are picked.
 * @param selected The keys of the selected rows.
 * @param onSelectedChange Handed the keys selected after a row or checkbox is tapped.
 * @param onRowClick A row's action, in any selection mode.
 * @param rowAnnouncement What a screen reader says for a row, in place of its cells
 *   read in order — for a row whose cells mean little without their headers.
 * @param colours The table's colours.
 * @param rowMinHeight The least a row is tall.
 * @param content The columns.
 */
@Composable
fun <T> Table(
    items: List<T>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    key: ((item: T) -> Any)? = null,
    state: TableState = rememberTableState(),
    stickyColumns: Int = 0,
    striped: Boolean = false,
    lines: TableLines = TableLines.Rows,
    outlined: Boolean = false,
    sort: TableSort? = null,
    onSortChange: ((TableSort) -> Unit)? = null,
    selection: TableSelection = TableSelection.None,
    selected: Set<Any> = emptySet(),
    onSelectedChange: ((Set<Any>) -> Unit)? = null,
    onRowClick: ((item: T) -> Unit)? = null,
    rowAnnouncement: ((item: T) -> String)? = null,
    colours: TableColours = TableDefaults.colours(),
    rowMinHeight: Dp = TableDefaults.RowMinHeight,
    content: TableScope<T>.() -> Unit,
) {
    val columns = TableScope<T>().apply(content).columns.toList()
    val leading = selection == TableSelection.Multiple
    val hasFooter = columns.any { it.footer != null }
    val columnCount = columns.size + if (leading) 1 else 0
    val rowCount = items.size + 1 + if (hasFooter) 1 else 0
    val layoutDirection = LocalLayoutDirection.current
    val scope = rememberCoroutineScope()
    val fitted = remember { FittedWidths() }
    val selectionWidth = TableDefaults.SelectionColumnWidth
    val minColumn = TableDefaults.MinColumnWidth
    val shape = TableDefaults.Shape
    val borderWidth = Theme.sizing.borderWidth
    val keyOf: (T) -> Any = key ?: { it as Any }

    SubcomposeLayout(
        modifier = modifier
            .then(if (outlined) Modifier.clip(shape).border(borderWidth, colours.lines, shape) else Modifier)
            .background(colours.container)
            .semantics {
                isTraversalGroup = true
                collectionInfo = CollectionInfo(rowCount = rowCount, columnCount = columnCount)
                horizontalScrollAxisRange = ScrollAxisRange(
                    value = { state.horizontal.value.toFloat() },
                    maxValue = { state.horizontal.maxValue.toFloat() },
                )
                scrollBy { x, _ ->
                    if (x == 0f) return@scrollBy false
                    scope.launch { state.horizontal.animateScrollBy(x) }
                    true
                }
            }
            // One sideways scroll for the whole table: a drag on any row moves
            // every row, and a fling on one is not stopped by another.
            .scrollable(
                state = state.horizontal,
                orientation = Orientation.Horizontal,
                enabled = state.horizontal.maxValue > 0,
                reverseDirection = ScrollableDefaults.reverseDirection(layoutDirection, Orientation.Horizontal, false),
            ),
    ) { constraints ->
        fitted.forget(density, fontScale)
        val env = TableEnv(
            columns = columns,
            leading = leading,
            layout = EmptyColumns,
            scroll = state.horizontal,
            colours = colours,
            lines = lines,
            enabled = enabled,
            striped = striped,
            sort = sort,
            onSortChange = onSortChange,
            selection = selection,
            selected = selected,
            onSelectedChange = onSelectedChange,
            onRowClick = onRowClick,
            rowAnnouncement = rowAnnouncement,
            keyOf = keyOf,
            key = key,
            rowMinHeight = rowMinHeight,
            items = items,
            columnCount = columnCount,
            rowCount = rowCount,
            hasFooter = hasFooter,
        )

        // A fitted column is as wide as the widest of its title and its first
        // rows' cells, measured with the cells themselves so padding and type
        // are exactly what the rows will draw. Remembered, and only ever widened,
        // so a column does not jump as rows scroll in or the items are re-sorted.
        fun fittedWidth(column: TableColumn<T>): Int {
            val known = fitted.width(column.key, items.isNotEmpty())
            if (known != null) return known
            val sample = items.take(TableFitSample)
            // Measured, never placed — and never read out: without their
            // semantics cleared these copies were a second, invisible header
            // and row for a screen reader to find.
            val measured = subcompose(FitSlot(column.key)) {
                HeaderCellContent(env, column, Unread)
                sample.forEach { item -> BodyCellContent(env, column, item, Unread) }
                if (column.footer != null) FooterCellContent(env, column, Unread)
            }.maxOfOrNull { it.maxIntrinsicWidth(Constraints.Infinity) } ?: 0
            return fitted.remember(column.key, measured, items.isNotEmpty())
        }

        fun titleWidth(column: TableColumn<T>): Int =
            subcompose(TitleSlot(column.key)) { HeaderCellContent(env, column, Unread) }
                .maxOfOrNull { it.maxIntrinsicWidth(Constraints.Infinity) } ?: 0

        val floor = minColumn.roundToPx()
        val sizings = buildList {
            if (leading) {
                val width = selectionWidth.roundToPx()
                add(ColumnSizing(width, 0f, width, width))
            }
            columns.forEach { column ->
                val least = if (column.minWidth.isSpecified) column.minWidth.roundToPx() else floor
                val most = if (column.maxWidth.isSpecified) column.maxWidth.roundToPx() else Int.MAX_VALUE
                add(
                    when {
                        column.width.isSpecified -> {
                            val width = column.width.roundToPx()
                            ColumnSizing(width, 0f, minOf(least, width), maxOf(most, width))
                        }
                        column.weight > 0f -> {
                            val title = maxOf(least, titleWidth(column))
                            ColumnSizing(title, column.weight, title, most)
                        }
                        else -> ColumnSizing(fittedWidth(column), 0f, least, most)
                    },
                )
            }
        }
        val natural = sizings.sumOf { it.base }
        val viewport = if (constraints.hasBoundedWidth) constraints.maxWidth else natural
        val layout = resolveTableColumns(
            columns = sizings,
            viewport = viewport,
            pinnedRequested = stickyColumns.coerceAtLeast(0) + if (leading) 1 else 0,
            pinCap = TablePinCap,
        )
        state.horizontal.updateBounds(maxValue = layout.maxScroll, viewport = layout.paneWidth)

        val frame = subcompose(FrameSlot) {
            TableFrame(env.copy(layout = layout), state.listState, boundedHeight = constraints.hasBoundedHeight)
        }.map { it.measure(constraints.copy(minWidth = viewport, maxWidth = viewport)) }
        layout(viewport, frame.maxOfOrNull { it.height } ?: 0) {
            frame.forEach { it.place(0, 0) }
        }
    }
}

/** One column as declared. */
internal class TableColumn<T>(
    val title: String,
    val width: Dp,
    val weight: Float,
    val minWidth: Dp,
    val maxWidth: Dp,
    val numeric: Boolean,
    val align: TableAlign,
    val sortable: Boolean,
    val maxLines: Int,
    val key: Any,
    val footer: (@Composable ContentScope.() -> Unit)?,
    val cell: @Composable ContentScope.(item: T) -> Unit,
)

/**
 * The widths fitted columns have been given, by column key, for as long as the
 * type size and density are the same.
 */
private class FittedWidths {
    private class Fit(val width: Int, val withItems: Boolean)

    private val widths = HashMap<Any, Fit>()
    private var density = 0f
    private var fontScale = 0f

    fun forget(density: Float, fontScale: Float) {
        if (density != this.density || fontScale != this.fontScale) {
            widths.clear()
            this.density = density
            this.fontScale = fontScale
        }
    }

    /** The width known for [key], unless it was measured with no rows and now there are some. */
    fun width(key: Any, withItems: Boolean): Int? =
        widths[key]?.takeIf { it.withItems || !withItems }?.width

    fun remember(key: Any, measured: Int, withItems: Boolean): Int {
        val width = maxOf(measured, widths[key]?.width ?: 0)
        widths[key] = Fit(width, withItems)
        return width
    }
}

private data class FitSlot(val key: Any)

/** For a cell composed only to be measured. */
private val Unread: Modifier = Modifier.clearAndSetSemantics {}
private data class TitleSlot(val key: Any)
private object FrameSlot

private val EmptyColumns = resolveTableColumns(emptyList(), 0, 0, 1f)

/** How much of the table pinned columns may cover before the last of them scrolls too. */
private const val TablePinCap: Float = 0.5f

/** How many rows a fitted column is measured against. */
private const val TableFitSample: Int = 50

private val TableMinColumnWidth: Dp = 48.dp
