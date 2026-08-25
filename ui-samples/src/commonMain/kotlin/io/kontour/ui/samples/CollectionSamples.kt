package io.kontour.ui.samples

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Bus
import com.composables.icons.tabler.outline.ChevronDown
import com.composables.icons.tabler.outline.ChevronRight
import com.composables.icons.tabler.outline.Trash
import io.kontour.ui.components.list.ExpandingListItem
import io.kontour.ui.components.list.ListGroup
import io.kontour.ui.components.list.ListItem
import io.kontour.ui.components.list.ListItemPosition
import io.kontour.ui.components.list.PullToRefresh
import io.kontour.ui.components.list.ReorderableItem
import io.kontour.ui.components.list.SwipeToDismiss
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
import io.kontour.ui.components.list.LoadMoreState
import io.kontour.ui.components.list.Scrollbar
import io.kontour.ui.components.list.SettingRow
import io.kontour.ui.components.list.fadingEdges
import io.kontour.ui.foundation.Text

@Composable
fun ListItemBasics(stops: List<Stop>) {
    LazyColumn {
        itemsIndexed(stops) { index, stop ->
            ListItem(
                onClick = { openStop(stop.name) },
                // Rounds the outside corners of the group and leaves the seams
                // square, so a run of rows reads as one block.
                position = ListItemPosition.of(index, stops.size),
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
fun ReorderableList(initial: List<Stop>) {
    var stops by remember { mutableStateOf(initial) }
    val listState = rememberLazyListState()
    val reorder = rememberReorderableState(listState) { from, to ->
        stops = stops.toMutableList().apply { add(to, removeAt(from)) }
    }

    LazyColumn(state = listState) {
        itemsIndexed(stops, key = { _, stop -> stop.name }) { index, stop ->
            ReorderableItem(state = reorder, index = index, itemCount = stops.size) {
                ListItem { +stop.name }
            }
        }
    }
}

@Composable
fun PullToRefreshBasics(refreshing: Boolean, stops: List<Stop>) {
    PullToRefresh(refreshing = refreshing, onRefresh = { refresh() }) {
        LazyColumn {
            items(stops) { stop ->
                ListItem { +stop.name }
            }
        }
    }
}

/** A row of the caller's own data, so the samples have something to list. */
data class Stop(val name: String, val routes: Int)

@Composable
fun ExpandingListItemBasics() {
    var open by remember { mutableStateOf(false) }

    ListGroup {
        item(label = "Elizabeth Quay", supporting = "3 routes")
    }
    ExpandingListItem(
        expanded = open,
        onExpandedChange = { open = it },
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

@Composable
fun ListSectionBasics() {
    // The section owns the rounding: `position` tells each row whether it is the
    // first, the last, both or neither, so a group reads as one card rather than
    // as a stack of separate ones.
    ListSection(
        title = { +"Appearance" },
        description = { +"How the app looks on this device" },
    ) {
        SettingRow(position = ListItemPosition.First, onClick = { save() }) {
            +"Theme"
            supporting { +"Match system" }
        }
        SettingRow(position = ListItemPosition.Last, onClick = { save() }) {
            +"Text size"
            supporting { +"Default" }
        }
    }
}

@Composable
fun SettingRowBasics() {
    SettingRow(onClick = { save() }) {
        +"Live vehicle positions"
        supporting { +"Uses more data" }
    }
}

@Composable
fun LoadMoreBasics() {
    var state by remember { mutableStateOf(LoadMoreState.Idle) }

    // One component for all four states — idle, loading, failed and the end of
    // the list. The failure is the one that gets skipped when a screen rolls its
    // own, and it is the one a user on a train actually meets.
    LoadMore(
        state = state,
        onLoadMore = { state = LoadMoreState.Loading },
        errorLabel = "Couldn't load more departures",
        endLabel = "That's everything",
    )
}

@Composable
fun ScrollbarBasics() {
    val scroll = rememberScrollState()

    Box {
        Column(Modifier.fillMaxWidth().verticalScroll(scroll)) { Screen() }
        // It hides itself unless the pointer can hover, so it costs a touch user
        // nothing. `alwaysVisible` is for a pane where the scroll is the point.
        Scrollbar(state = scroll, modifier = Modifier.align(Alignment.CenterEnd))
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
