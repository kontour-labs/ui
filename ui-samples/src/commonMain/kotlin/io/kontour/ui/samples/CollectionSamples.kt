package io.kontour.ui.samples

import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import io.kontour.icons.tabler.outline.HelpCircle
import io.kontour.ui.components.list.DisclosureListItem
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import io.kontour.icons.tabler.Tabler
import io.kontour.icons.tabler.outline.Bus
import io.kontour.icons.tabler.outline.ChevronDown
import io.kontour.icons.tabler.outline.ChevronRight
import io.kontour.icons.tabler.outline.Trash
import io.kontour.ui.components.list.ExpandingListItem
import io.kontour.ui.components.list.ListGroup
import io.kontour.ui.components.list.ListItem
import io.kontour.ui.components.list.ListItemDefaults
import io.kontour.ui.components.list.scrollbar
import io.kontour.ui.theme.Theme
import io.kontour.ui.components.list.indexedBy
import io.kontour.ui.components.list.listSection
import io.kontour.ui.components.list.IndexScroller
import androidx.compose.foundation.layout.PaddingValues
import io.kontour.ui.components.list.IndexScrollerDefaults
import io.kontour.ui.components.list.IndexAlphabet
import io.kontour.ui.foundation.GroupPosition
import io.kontour.ui.components.list.PullToRefresh
import io.kontour.ui.components.list.ReorderableItem
import io.kontour.ui.components.list.SwipeToDismiss
import io.kontour.ui.components.list.SwipeAction
import io.kontour.ui.components.list.SwipeActions
import io.kontour.icons.tabler.outline.Pin
import io.kontour.ui.components.list.rememberReorderableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.kontour.ui.components.list.ListSection
import io.kontour.ui.components.list.LoadMore
import io.kontour.ui.components.list.LoadMoreStatus
import io.kontour.ui.components.list.Scrollbar
import io.kontour.ui.components.selection.SelectionRow
import io.kontour.ui.components.selection.SegmentedControl
import io.kontour.ui.components.list.fadingEdges
import io.kontour.ui.foundation.Text
import io.kontour.ui.components.table.TableSort
import io.kontour.ui.components.table.TableAlign
import io.kontour.ui.components.table.Table
import io.kontour.ui.components.table.SortDirection

@Composable
fun ListItemBasics(stops: List<Stop>) {
    LazyColumn {
        // A section puts a list item's gap between the rows and hands each its
        // position, which rounds the outside corners of the group and leaves
        // the seams square, so a run of rows reads as one block.
        listSection(stops, key = { it.name }) { stop, position ->
            ListItem(
                onClick = { openStop(stop.name) },
                position = position,
            ) {
                leading { +Tabler.Outline.Bus }
                +stop.name
                supporting { +"${stop.routes} routes" }
                trailing { +Tabler.Outline.ChevronRight }
            }
        }
    }
}

@Composable
fun SwipeToDismissBasics(stop: Stop) {
    SwipeToDismiss(
        onDismissRequest = { remove(stop.name) },
        label = "Remove",
        icon = Tabler.Outline.Trash,
    ) {
        ListItem {
            +stop.name
            supporting { +"${stop.routes} routes" }
        }
    }
}

@Composable
fun SwipeActionOwnTick(stop: Stop) {
    SwipeActions(
        end = listOf(
            // Undone from the toast it raises, so it runs as the row arrives.
            SwipeAction(
                label = "Remove",
                icon = Tabler.Outline.Trash,
                onAction = { remove(stop.name) },
                background = Theme.colours.danger.solid,
                fullSwipeConfirmation = false,
                tapConfirmation = false,
            ),
            SwipeAction(
                label = "Pin",
                icon = Tabler.Outline.Pin,
                onAction = { pin(stop.name) },
                background = Theme.colours.accent.solid,
            ),
        ),
    ) {
        ListItem {
            +stop.name
            supporting { +"${stop.routes} routes" }
        }
    }
}

@Composable
fun ReorderableList(initial: List<Stop>) {
    var stops by remember { mutableStateOf(initial) }
    val listState = rememberLazyListState()
    val reorder = rememberReorderableState(listState) { from, to ->
        stops = stops.toMutableList().apply { add(to, removeAt(from)) }
    }

    // One group: a list item's gap between the rows, each in its place.
    LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(ListItemDefaults.Spacing)) {
        itemsIndexed(stops, key = { _, stop -> stop.name }) { index, stop ->
            ReorderableItem(state = reorder, index = index, itemCount = stops.size) {
                ListItem(position = GroupPosition.of(index, stops.size)) { +stop.name }
            }
        }
    }
}

@Composable
fun PullToRefreshBasics(refreshing: Boolean, stops: List<Stop>) {
    val rows = rememberLazyListState()
    PullToRefresh(refreshing = refreshing, onRefresh = { refresh() }, scrollState = rows) {
        LazyColumn(state = rows) {
            listSection(stops) { stop, position ->
                ListItem(position = position) { +stop.name }
            }
        }
    }
}

/** A row of the caller's own data, so the samples have something to list. */
data class Stop(val name: String, val routes: Int)

@Composable
fun ExpandingListItemBasics() {
    var open by remember { mutableStateOf(false) }

    // One group: the expanding row takes its place in it like any other.
    ListGroup {
        item(label = "Elizabeth Quay", supporting = "3 routes")
        item { position ->
            ExpandingListItem(
                expanded = open,
                onExpandedChange = { open = it },
                position = position,
                chevron = Tabler.Outline.ChevronDown,
                header = {
                    leading { +Tabler.Outline.Bus }
                    +"Perth Underground"
                    supporting { +"4 platforms" }
                },
            ) {
                item(label = "Platform 1", supporting = "Mandurah line")
                item(label = "Platform 2", supporting = "Joondalup line")
            }
        }
    }
}

@Composable
fun DisclosureListItemBasics() {
    var open by remember { mutableStateOf(false) }

    // The row grows to show the answer inside itself: one card, nothing new
    // appearing underneath it.
    DisclosureListItem(
        expanded = open,
        onExpandedChange = { open = it },
        header = {
            leading { +Tabler.Outline.HelpCircle }
            +"Why is my train late?"
        },
    ) {
        Text("Trains give way to freight between Midland and Bellevue. A delay there reaches every stop after it.")
    }
}

@Composable
fun ListSectionBasics() {
    // The section owns the rounding: `position` tells each row whether it is the
    // first, the last, both or neither, so a group reads as one card rather than
    // as a stack of separate ones.
    ListSection(
        title = { +"Appearance" },
        supporting = { +"How the app looks on this device" },
        // Above the rows is what the group *is*; below them is what it *does*.
        footer = { +"Always dark keeps the screen dark even when the system is light." },
    ) {
        SelectionRow(onClick = { save() }, position = GroupPosition.First) {
            +"Theme"
            supporting { +"Match system" }
        }
        SelectionRow(onClick = { save() }, position = GroupPosition.Last) {
            +"Text size"
            supporting { +"Default" }
        }
    }
}

@Composable
fun ListSectionOtherContent() {
    var units by remember { mutableStateOf(0) }

    // Not rows: a paragraph and a control. The title and footer start where
    // they do, at the edge, rather than in where a row's words would be.
    ListSection(
        title = { +"Planning" },
        footer = { +"Used to work out how long the walks between stops take." },
        // Separate things rather than one card, so the room a form has.
        spacing = Theme.spacing.md,
    ) {
        Text("Trips are planned at the speed you walk, in the units you read distances in.")
        SegmentedControl(
            options = listOf("Kilometres", "Miles"),
            selectedIndex = units,
            onSelectedIndexChange = { units = it },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun ListSectionLazy(favourites: List<Stop>, nearby: List<Stop>) {
    // Sections in a lazy list. Each draws its heading, its rows a hairline
    // apart with their corners worked out, and the space before the next —
    // so the `LazyColumn` sets no `verticalArrangement` of its own.
    LazyColumn {
        listSection(favourites, key = { it.name }, sectionKey = "favourites", title = { +"Favourites" }) { stop, position ->
            ListItem(onClick = { save() }, position = position) { +stop.name }
        }
        listSection(
            nearby,
            key = { it.name },
            sectionKey = "nearby",
            title = { +"Nearby" },
            footer = { +"Within a ten-minute walk." },
        ) { stop, position ->
            ListItem(onClick = { save() }, position = position) {
                +stop.name
                supporting { +"${stop.routes} routes" }
            }
        }
    }
}

@Composable
fun LoadMoreBasics() {
    var status by remember { mutableStateOf(LoadMoreStatus.Idle) }

    // One component for all four states — idle, loading, failed and the end of
    // the list. The failure is the one that gets skipped when a screen rolls its
    // own, and it is the one a user on a train actually meets.
    LoadMore(
        status = status,
        onLoadMore = { status = LoadMoreStatus.Loading },
        errorMessage = "Couldn't load more departures",
        endLabel = "That's everything",
    )
}

@Composable
fun ScrollbarBasics() {
    val scroll = rememberScrollState()
    // On the scroller itself, before its scroll. Under touch it appears while
    // the content moves and fades after; with a mouse or a keyboard it is there
    // all along. Either way it can be dragged while it is there.
    Column(Modifier.fillMaxWidth().scrollbar(scroll).verticalScroll(scroll)) { Screen() }

    // Any scroller foundation ships — a lazy list, a grid, a pager — across or
    // down: a row's runs along its foot.
    val row = rememberLazyListState()
    LazyRow(Modifier.scrollbar(row, Orientation.Horizontal), state = row) {
        items(stops) { Text(it.name) }
    }
}

@Composable
fun IndexScrollerBasics(stops: List<Stop>) {
    val formats = Theme.formats
    val alphabet = IndexAlphabet.forLocale(Theme.locale)
    // Grouped under the reader's alphabet, with where each group starts in
    // the list: one header item and its rows.
    val indexed = remember(stops, alphabet, formats) { stops.indexedBy(alphabet, formats) { it.name } }
    val list = rememberLazyListState()

    Box {
        // Room at the end for the letters, so no row runs under them.
        LazyColumn(state = list, contentPadding = PaddingValues(end = IndexScrollerDefaults.Width)) {
            // A section a letter: a header item and its rows, grouped, which
            // is what `indexedBy` counted.
            indexed.groups.forEach { group ->
                listSection(group.items, key = { it.name }, title = { +group.label }) { stop, position ->
                    ListItem(position = position) { +stop.name }
                }
            }
        }
        IndexScroller(list, indexed.sections, Modifier.align(Alignment.CenterEnd), alphabet = alphabet)
    }
}

@Composable
fun FadingEdgesBasics() {
    val scroll = rememberScrollState()

    // A hard edge at the top of a scrolling list reads as the end of the
    // content. The fade says there is more, and it appears only on the side
    // that has any.
    Column(
        Modifier
            .fadingEdges(scroll, orientation = Orientation.Vertical)
            .verticalScroll(scroll),
    ) {
        Text("Departures")
    }
}

@Composable
fun TableBasics() {
    var sort by remember { mutableStateOf<TableSort?>(null) }
    val rows = remember(sort) {
        // The table says what the reader asked for; the caller does the sorting.
        val by = compareBy<Service> { if (sort?.column == "Route") it.route else it.time }
        services.sortedWith(if (sort?.direction == SortDirection.Descending) by.reversed() else by)
    }

    Table(
        items = rows,
        modifier = Modifier.height(240.dp),
        stickyColumns = 1,
        striped = true,
        sort = sort,
        onSortChange = { sort = it },
        onRowClick = { openService(it) },
    ) {
        column("Route", width = 72.dp) { +it.route }
        column("Destination", weight = 1f) { +it.destination }
        column("Departs", align = TableAlign.End) { +it.time }
    }
}

