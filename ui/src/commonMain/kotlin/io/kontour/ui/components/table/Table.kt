package io.kontour.ui.components.table

import io.kontour.ui.theme.clipProxy
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
import androidx.compose.ui.graphics.vector.ImageVector
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
import io.kontour.ui.motion.ScrollDefaults
import io.kontour.ui.motion.scrollSpec
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

/** What a [Table] takes by default. */
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
 * @param narrowStickyColumns Whether the pinned columns narrow a little as the
 *   rest are scrolled across, giving the rest more room: to about two thirds of
 *   their width over the first stretch of the scroll, cut and faded at their
 *   ends rather than wrapped, and back as the table returns to its start.
 * @param striped Every other row on a quiet ground, for reading across a wide row.
 * @param lines The rules between rows and between columns.
 * @param outlined A border round the whole table, with a card's corners.
 * @param sort The current sort, which the header shows.
 * @param onSortChange Makes headers sortable, and is handed the sort a tap asks for.
 * @param sortAscendingIcon The arrow on a column sorted smallest first,
 *   `Theme.icons.sortAscending` by default.
 * @param sortDescendingIcon The arrow on a column sorted largest first.
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
    narrowStickyColumns: Boolean = false,
    striped: Boolean = false,
    lines: TableLines = TableLines.Rows,
    outlined: Boolean = false,
    sort: TableSort? = null,
    onSortChange: ((TableSort) -> Unit)? = null,
    sortAscendingIcon: ImageVector = Theme.icons.sortAscending,
    sortDescendingIcon: ImageVector = Theme.icons.sortDescending,
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
    val hairline = Theme.sizing.dividerThickness
    val columnCount = columns.size + if (leading) 1 else 0
    val rowCount = items.size + 1 + if (hasFooter) 1 else 0
    val layoutDirection = LocalLayoutDirection.current
    val scope = rememberCoroutineScope()
    val motion = Theme.motion
    val fitted = remember { FittedWidths() }
    val selectionWidth = TableDefaults.SelectionColumnWidth
    val minColumn = TableDefaults.MinColumnWidth
    val shape = TableDefaults.Shape
    val borderWidth = Theme.sizing.borderWidth
    val keyOf: (T) -> Any = key ?: { it as Any }

    SubcomposeLayout(
        modifier = modifier
            .then(if (outlined) Modifier.clip(shape.clipProxy()).border(borderWidth, colours.lines, shape) else Modifier)
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
                    scope.launch { state.horizontal.animateScrollBy(x, motion.scrollSpec()) }
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
                flingBehavior = ScrollDefaults.flingBehavior(),
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
            sortIcons = sortAscendingIcon to sortDescendingIcon,
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
            hairline = hairline,
        )

        // A fitted column is as wide as the widest of its title and a sample of
        // its cells spread through the rows (see `tableFitSample`), measured
        // with the cells themselves so padding and type are exactly what the
        // rows will draw. Remembered for these items, so scrolling never moves
        // a column; measured again for new ones, and widened only, so new data
        // can widen a column once and nothing narrows one under the reader.
        fun fittedWidth(column: TableColumn<T>): Int {
            val known = fitted.width(column.key, items)
            if (known != null) return known
            val sample = tableFitSample(items.size).map { items[it] }
            // Measured, never placed — and never read out: without their
            // semantics cleared these copies were a second, invisible header
            // and row for a screen reader to find.
            val measured = subcompose(FitSlot(column.key)) {
                HeaderCellContent(env, column, Unread)
                sample.forEach { item -> BodyCellContent(env, column, item, Unread) }
                if (column.footer != null) FooterCellContent(env, column, Unread)
            }.maxOfOrNull { it.maxIntrinsicWidth(Constraints.Infinity) } ?: 0
            return fitted.remember(column.key, measured, items)
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
            narrowTo = if (narrowStickyColumns) StickyNarrowTo else 1f,
            narrowFloor = StickyNarrowest.roundToPx(),
            narrowRange = StickyNarrowOver.roundToPx(),
            // The checkbox column keeps its width: a checkbox cut is no checkbox.
            keepWidth = if (leading) 1 else 0,
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
 *
 * Each remembers the list it was measured against, by identity: the same list
 * scrolled is the same width, and a new list — a page in, a refresh, a sort —
 * is measured again and kept at the wider of the two.
 */
private class FittedWidths {
    private class Fit(val width: Int, val items: List<*>)

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

    /** The width known for [key], if it was measured against these very [items]. */
    fun width(key: Any, items: List<*>): Int? =
        widths[key]?.takeIf { it.items === items }?.width

    fun remember(key: Any, measured: Int, items: List<*>): Int {
        val width = maxOf(measured, widths[key]?.width ?: 0)
        widths[key] = Fit(width, items)
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

/** How much of its width a pinned column keeps, narrowed: see `narrowStickyColumns`. */
private const val StickyNarrowTo: Float = 0.7f

/** The narrowest a pinned column is made, if it was wider: a word or two of a name still shows. */
private val StickyNarrowest = 64.dp

/** How far the table scrolls across while its pinned columns narrow: the first stretch, as a top bar collapses over a page's first. */
private val StickyNarrowOver = 64.dp

/**
 * Which rows a fitted column is measured against: all of them up to fifty, and
 * past that the first twenty, the last ten and twenty spread evenly between.
 *
 * The first fifty alone, which it was, fitted a column to what is on screen when
 * the table opens and to nothing after it: a date column cut "30 Sep 2026" short
 * as it scrolled. The same fifty measurements, spread, see the end of a list —
 * where a page loaded in, or the newest row, lands — and the middle of it.
 */
internal fun tableFitSample(size: Int): List<Int> {
    if (size <= TableFitSample) return List(size) { it }
    val between = TableFitSample - TableFitHead - TableFitTail
    val from = TableFitHead
    val until = size - TableFitTail
    val spread = List(between) { i -> from + ((i + 0.5f) * (until - from) / between).toInt() }
    return ((0 until TableFitHead) + spread + (until until size)).distinct()
}

/** How many rows a fitted column is measured against, and how they are spread. */
private const val TableFitSample: Int = 50
private const val TableFitHead: Int = 20
private const val TableFitTail: Int = 10

private val TableMinColumnWidth: Dp = 48.dp
