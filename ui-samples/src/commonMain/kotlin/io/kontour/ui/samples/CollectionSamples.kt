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
import com.composables.icons.tabler.outline.ChevronRight
import com.composables.icons.tabler.outline.Trash
import io.kontour.ui.components.list.ListItem
import io.kontour.ui.components.list.ListItemPosition
import io.kontour.ui.components.list.PullToRefresh
import io.kontour.ui.components.list.ReorderableItem
import io.kontour.ui.components.list.SwipeToDismiss
import io.kontour.ui.components.list.rememberReorderableState

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
