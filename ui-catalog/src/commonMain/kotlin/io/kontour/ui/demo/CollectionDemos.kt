package io.kontour.ui.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Bell
import com.composables.icons.tabler.outline.Bus
import com.composables.icons.tabler.outline.ChevronDown
import com.composables.icons.tabler.outline.Moon
import com.composables.icons.tabler.outline.Star
import com.composables.icons.tabler.outline.Trash
import io.kontour.ui.components.display.Tag
import io.kontour.ui.components.display.TagTone
import io.kontour.ui.components.list.ExpandingListItem
import io.kontour.ui.components.list.ListGroup
import io.kontour.ui.components.list.ListItem
import io.kontour.ui.components.list.ListItemPosition
import io.kontour.ui.components.list.ListSection
import io.kontour.ui.components.list.LoadMore
import io.kontour.ui.components.list.LoadMoreState
import io.kontour.ui.components.list.PullToRefresh
import io.kontour.ui.components.list.ReorderableItem
import io.kontour.ui.components.list.Scrollbar
import io.kontour.ui.components.list.SettingRow
import io.kontour.ui.components.list.SwipeAction
import io.kontour.ui.components.list.SwipeActions
import io.kontour.ui.components.list.SwipeToDismiss
import io.kontour.ui.components.list.fadingEdges
import io.kontour.ui.components.list.rememberReorderableState
import io.kontour.ui.components.list.settingValue
import io.kontour.ui.components.selection.Switch
import io.kontour.ui.foundation.Text
import io.kontour.ui.sheet.DragHandle
import io.kontour.ui.theme.Theme
import kotlinx.coroutines.delay

private val stops = listOf(
    "Perth Underground" to "Platform 2 · Joondalup line",
    "Elizabeth Quay" to "Platform 1 · Mandurah line",
    "Perth Busport" to "Stand 24 · Route 950",
    "McIver" to "Platform 1 · Midland line",
)

internal val ListItemDemo = ComponentDemo(slug = "list-item") {
    var current by remember { mutableStateOf(1) }
    ListGroup(spacing = 2.dp, modifier = Modifier.fillMaxWidth()) {
        stops.forEachIndexed { index, (name, detail) ->
            item(
                label = name,
                supporting = detail,
                icon = Tabler.Outline.Bus,
                selected = index == current,
                trailing = { Tag(tone = TagTone.Neutral) { +"${4 + index * 6} min" } },
                onClick = { current = index },
            )
        }
    }
}

internal val ExpandingListItemDemo = ComponentDemo(slug = "expanding-list-item") {
    var open by remember { mutableStateOf(false) }
    ExpandingListItem(
        expanded = open,
        onExpandedChange = { open = it },
        spacing = 2.dp,
        chevron = Tabler.Outline.ChevronDown,
        modifier = Modifier.fillMaxWidth(),
        header = {
            +"Perth Underground"
            supporting { +"4 platforms" }
            leading { +Tabler.Outline.Bus }
        },
    ) {
        item(label = "Platform 1", supporting = "Mandurah line")
        item(label = "Platform 2", supporting = "Joondalup line")
        item(label = "Platform 3", supporting = "Airport line")
    }
}

internal val ListSectionDemo = ComponentDemo(slug = "list-section") {
    var theme by remember { mutableStateOf(0) }
    val themes = listOf("Match system", "Always light", "Always dark")
    ListSection(
        modifier = Modifier.fillMaxWidth(),
        title = { +"Appearance" },
        description = { +"How the app looks on this device" },
    ) {
        SettingRow(
            position = ListItemPosition.First,
            onClick = { theme = (theme + 1) % themes.size },
        ) {
            +"Theme"
            leading { +Tabler.Outline.Moon }
            trailing { settingValue(themes[theme]) }
        }
        SettingRow(position = ListItemPosition.Last, onClick = { echo("Delay alerts") }) {
            +"Delay alerts"
            supporting { +"Only for favourited routes" }
            leading { +Tabler.Outline.Bell }
        }
    }
}

internal val SettingRowDemo = ComponentDemo(slug = "setting-row") {
    var theme by remember { mutableStateOf(0) }
    var notify by remember { mutableStateOf(true) }
    val themes = listOf("Match system", "Always light", "Always dark")
    Column(Modifier.fillMaxWidth()) {
        SettingRow(
            position = ListItemPosition.First,
            onClick = { theme = (theme + 1) % themes.size },
        ) {
            +"Theme"
            leading { +Tabler.Outline.Moon }
            trailing { settingValue(themes[theme]) }
        }
        SettingRow(position = ListItemPosition.Last, onClick = { notify = !notify }) {
            +"Delay alerts"
            supporting { +"Only for favourited routes" }
            leading { +Tabler.Outline.Bell }
            trailing { Switch(checked = notify, onCheckedChange = null) }
        }
    }
}

internal val SwipeActionsDemo = ComponentDemo(slug = "swipe-actions") {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm),
    ) {
        Text(
            "Drag a row sideways.",
            style = Theme.typography.bodySmall,
            color = Theme.colors.contentMuted,
        )
        SwipeActions(
            end = listOf(
                SwipeAction(
                    label = "Remove",
                    icon = Tabler.Outline.Trash,
                    onAction = { echo("Removed") },
                    background = Theme.colors.danger.solid,
                    isFullSwipeAction = true,
                ),
            ),
            start = listOf(
                SwipeAction(
                    label = "Favourite",
                    icon = Tabler.Outline.Star,
                    onAction = { echo("Favourited") },
                    background = Theme.colors.success.solid,
                ),
            ),
        ) {
            ListItem(onClick = { echo("Perth Busport") }) {
                +"Perth Busport"
                supporting { +"Swipe either way" }
            }
        }
        SwipeToDismiss(
            onDismissRequest = { echo("Dismissed") },
            label = "Remove",
            icon = Tabler.Outline.Trash,
        ) {
            ListItem {
                +"Elizabeth Quay Station"
                supporting { +"Fremantle line · Platform 2" }
            }
        }
    }
}

internal val ReorderableItemDemo = ComponentDemo(slug = "reorderable-item") {
    var order by remember { mutableStateOf(stops.map { it.first }) }
    val listState = rememberLazyListState()
    val state = rememberReorderableState(listState) { from, to ->
        order = order.toMutableList().apply { add(to, removeAt(from)) }
    }
    // A bounded height, because the component wants a `LazyColumn` and this
    // page is a `verticalScroll`. A lazy list measured at infinite height
    // throws, which is exactly how the site's landing page used to crash on a
    // phone — the same mistake is one careless demo away.
    Box(Modifier.fillMaxWidth().height(220.dp)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            itemsIndexed(order, key = { _, name -> name }) { index, name ->
                ReorderableItem(state = state, index = index, itemCount = order.size) {
                    ListItem(position = ListItemPosition.of(index, order.size)) {
                        +name
                        leading { +Tabler.Outline.Bus }
                    }
                }
            }
        }
    }
}

internal val PullToRefreshDemo = ComponentDemo(slug = "pull-to-refresh") {
    var refreshing by remember { mutableStateOf(false) }
    LaunchedEffect(refreshing) {
        if (refreshing) {
            delay(1_400)
            refreshing = false
        }
    }
    PullToRefresh(
        refreshing = refreshing,
        onRefresh = { refreshing = true },
        modifier = Modifier.fillMaxWidth().height(200.dp),
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            stops.forEachIndexed { index, (name, detail) ->
                ListItem(position = ListItemPosition.of(index, stops.size)) {
                    +name
                    supporting { +detail }
                }
            }
        }
    }
}

internal val LoadMoreDemo = ComponentDemo(slug = "load-more") {
    var state by remember { mutableStateOf(LoadMoreState.Idle) }
    LaunchedEffect(state) {
        if (state == LoadMoreState.Loading) {
            delay(1_200)
            state = LoadMoreState.Error
        }
    }
    LoadMore(
        state = state,
        onLoadMore = { state = LoadMoreState.Loading },
        errorLabel = "Couldn't load more departures",
        modifier = Modifier.fillMaxWidth(),
    )
}

internal val ScrollbarDemo = ComponentDemo(slug = "scrollbar") {
    val scroll = rememberScrollState()
    Box(Modifier.fillMaxWidth().height(160.dp)) {
        Column(Modifier.fillMaxWidth().verticalScroll(scroll)) {
            repeat(14) { Text("Departure ${it + 1}", style = Theme.typography.bodyMedium) }
        }
        // `alwaysVisible`, because a scrollbar hides itself unless the input can
        // hover — and on a touch device, or in a still render, it would never
        // appear at all.
        Scrollbar(
            state = scroll,
            modifier = Modifier.align(Alignment.CenterEnd),
            alwaysVisible = true,
        )
    }
}

internal val FadingEdgesDemo = ComponentDemo(slug = "modifier-fading-edges") {
    val scroll = rememberScrollState()
    Box(Modifier.fillMaxWidth().height(200.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fadingEdges(scroll)
                .verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            repeat(10) { index ->
                ListItem(position = ListItemPosition.of(index, 10)) {
                    +"Departure ${index + 1}"
                    supporting { +"Elizabeth Quay" }
                }
            }
        }
    }
}

internal val DragHandleDemo = ComponentDemo(slug = "drag-handle") {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm),
    ) {
        // No `LocalSheetState`, so this is the resting pill rather than the
        // draggable one — a handle is drawn by the sheet, not dragged on its own.
        DragHandle(state = null)
        Text(
            "Drawn by a sheet, not draggable on its own",
            style = Theme.typography.labelSmall,
            color = Theme.colors.contentMuted,
        )
    }
}

internal val collectionDemos = listOf(
    ListItemDemo,
    ExpandingListItemDemo,
    ListSectionDemo,
    SettingRowDemo,
    SwipeActionsDemo,
    ReorderableItemDemo,
    PullToRefreshDemo,
    LoadMoreDemo,
    ScrollbarDemo,
    FadingEdgesDemo,
    DragHandleDemo,
)
