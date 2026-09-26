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
import com.composables.icons.tabler.outline.Archive
import com.composables.icons.tabler.outline.Bell
import com.composables.icons.tabler.outline.Bus
import com.composables.icons.tabler.outline.ChevronDown
import com.composables.icons.tabler.outline.GripVertical
import com.composables.icons.tabler.outline.Moon
import com.composables.icons.tabler.outline.Pin
import com.composables.icons.tabler.outline.Star
import com.composables.icons.tabler.outline.Trash
import io.kontour.ui.components.display.Tag
import io.kontour.ui.theme.Tone
import io.kontour.ui.components.list.ExpandingListItem
import io.kontour.ui.components.list.ListGroup
import io.kontour.ui.components.list.ListItem
import io.kontour.ui.foundation.GroupPosition
import io.kontour.ui.components.list.ListSection
import io.kontour.ui.components.list.LoadMore
import io.kontour.ui.components.list.LoadMoreStatus
import io.kontour.ui.components.list.PullToRefresh
import io.kontour.ui.components.list.ReorderHandleSide
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
import io.kontour.ui.components.table.SortDirection
import io.kontour.ui.components.table.Table
import io.kontour.ui.components.table.TableLines
import io.kontour.ui.components.table.TableSelection
import io.kontour.ui.components.table.TableSort
import io.kontour.ui.foundation.Text
import io.kontour.ui.theme.Theme
import kotlinx.coroutines.delay

private val stops = listOf(
    "Perth Underground" to "Platform 2 · Joondalup line",
    "Elizabeth Quay" to "Platform 1 · Mandurah line",
    "Perth Busport" to "Stand 24 · Route 950",
    "McIver" to "Platform 1 · Midland line",
)

// `ListGroup` derives each row's position, which is the ordinary way to use
// this and hides the parameter completely. The single row underneath is where
// a reader can see what one `position` does on its own — `Only` is fully
// rounded, `Middle` is nearly square, and the two ends round one way each.
private val listItemPosition = Knob.Choice("Position", GroupPosition.entries.toList())

internal val ListItemDemo = ComponentDemo(
    slug = "list-item",
    knobs = listOf(listItemPosition),
) {
    var current by remember { mutableStateOf(1) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.md),
    ) {
        ListGroup(spacing = 2.dp, modifier = Modifier.fillMaxWidth()) {
            stops.forEachIndexed { index, (name, detail) ->
                item(
                    label = name,
                    supporting = detail,
                    icon = Tabler.Outline.Bus,
                    selected = index == current,
                    trailing = { Tag(tone = Tone.Neutral) { +"${4 + index * 6} min" } },
                    onClick = { current = index },
                )
            }
        }
        ListItem(position = this@ComponentDemo[listItemPosition]) {
            +"On its own, at ${this@ComponentDemo[listItemPosition]}"
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

/**
 * The sentence under the rows, which is the slot the page explains and the
 * demo did not show.
 *
 * A knob rather than always on, because the two readings are different: with a
 * footer the section is a setting *and* its consequence, and without one it is
 * a group of rows. `description` sits above and says what the group is; this
 * sits below and says what it does.
 */
private val sectionFooter = Knob.Flag("Footer", initial = true)

internal val ListSectionDemo = ComponentDemo(
    slug = "list-section",
    knobs = listOf(sectionFooter),
) {
    var theme by remember { mutableStateOf(0) }
    val themes = listOf("Match system", "Always light", "Always dark")
    ListSection(
        modifier = Modifier.fillMaxWidth(),
        title = { +"Appearance" },
        supporting = { +"How the app looks on this device" },
        footer = if (this[sectionFooter]) {
            { +"Always dark keeps the screen dark even when the system is light." }
        } else {
            null
        },
    ) {
        SettingRow(
            position = GroupPosition.First,
            onClick = { theme = (theme + 1) % themes.size },
        ) {
            +"Theme"
            leading { +Tabler.Outline.Moon }
            trailing { settingValue(themes[theme]) }
        }
        SettingRow(position = GroupPosition.Last, onClick = { echo("Delay alerts") }) {
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
            position = GroupPosition.First,
            onClick = { theme = (theme + 1) % themes.size },
        ) {
            +"Theme"
            leading { +Tabler.Outline.Moon }
            trailing { settingValue(themes[theme]) }
        }
        SettingRow(position = GroupPosition.Last, onClick = { notify = !notify }) {
            +"Delay alerts"
            supporting { +"Only for favourited routes" }
            leading { +Tabler.Outline.Bell }
            trailing { Switch(checked = notify, onCheckedChange = null) }
        }
    }
}

/**
 * How many actions the trailing side offers, up to the cap.
 *
 * Three is `SwipeActionsDefaults.MaxActionsPerSide` and a fourth now throws at
 * composition — a cap worth having, because one target is 88dp and three of them
 * are 264dp of a phone's width. Nothing in the repository drew more than one
 * until this knob, so the strip the cap exists to bound had never been looked at:
 * the same gap `handleIcon` was in for two rounds.
 *
 * Starts at one, which is both the common case and the safe one — the semantics
 * sweep floors at nine actions across this family with a `>=`, so more is always
 * fine and fewer is the only way to break it.
 */
private val swipeActionCount = Knob.Choice("Actions", listOf(1, 2, 3), 1, name = { "$it" })

/**
 * Whether a full swipe shows a tick before it runs its action.
 *
 * Asked for as optional, so both are one press apart: on, the row holds at the
 * far edge while the icon turns into a drawn check mark; off, the action runs the
 * moment the row gets there.
 */
private val swipeConfirmation = Knob.Flag("Tick on full swipe", initial = true)

internal val SwipeActionsDemo = ComponentDemo(
    slug = "swipe-actions",
    knobs = listOf(swipeActionCount, swipeConfirmation),
) {
    val confirmation = this[swipeConfirmation]
    // The first stays the full-swipe action whatever the count: a full swipe
    // runs the *first* action of the side, so moving it would change two things
    // at once.
    val trailing = listOf(
        SwipeAction(
            label = "Remove",
            icon = Tabler.Outline.Trash,
            onAction = { echo("Removed") },
            background = Theme.colours.danger.solid,
            fullSwipe = true,
        ),
        SwipeAction(
            label = "Archive",
            icon = Tabler.Outline.Archive,
            onAction = { echo("Archived") },
            background = Theme.colours.warning.solid,
        ),
        SwipeAction(
            label = "Pin",
            icon = Tabler.Outline.Pin,
            onAction = { echo("Pinned") },
            background = Theme.colours.accent.solid,
        ),
    )
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm),
    ) {
        Text(
            "Drag a row sideways.",
            style = Theme.typography.bodySmall,
            colour = Theme.colours.contentMuted,
        )
        SwipeActions(
            end = trailing.take(this@ComponentDemo[swipeActionCount]),
            fullSwipeConfirmation = confirmation,
            start = listOf(
                SwipeAction(
                    label = "Favourite",
                    icon = Tabler.Outline.Star,
                    onAction = { echo("Favourited") },
                    background = Theme.colours.success.solid,
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
            fullSwipeConfirmation = confirmation,
        ) {
            ListItem {
                +"Elizabeth Quay Station"
                supporting { +"Fremantle line · Platform 2" }
            }
        }
    }
}

/**
 * The handle, which had never been rendered anywhere until this knob.
 *
 * `handleIcon` shipped with zero call sites — not a demo, not a sample, not the
 * registry — so the first time anything drew one was the round the reporter
 * asked to see them. It is not only decoration: with a handle the drag starts
 * **immediately** from the grip, and without one a touch has to long-press the
 * row first. Two different gestures behind one nullable parameter, which is why
 * it is worth a knob rather than a sentence in the docs.
 */
private val reorderHandles = Knob.Flag("Drag handles")

/**
 * Which end the grip sits at.
 *
 * Swept because `ReorderHandleSide` was one of the component parameter enums no
 * knob touched — the site named it in the generated table and showed one of its
 * two values. It has an effect only while [reorderHandles] is on, which is
 * honest rather than awkward: that is the component's own behaviour, and a
 * reader who turns the side over with no handle showing learns it.
 */
private val reorderHandleSide =
    Knob.Choice("Handle side", ReorderHandleSide.entries.toList(), ReorderHandleSide.End)

internal val ReorderableItemDemo = ComponentDemo(
    slug = "reorderable-item",
    knobs = listOf(reorderHandles, reorderHandleSide),
) {
    // Read out here rather than inside `itemsIndexed`, where `this` is the
    // `LazyItemScope` and the `DemoScope` has no label to reach back to.
    val handle = if (this[reorderHandles]) Tabler.Outline.GripVertical else null
    val handleAt = this[reorderHandleSide]
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
                ReorderableItem(
                    state = state,
                    index = index,
                    itemCount = order.size,
                    handleIcon = handle,
                    handleSide = handleAt,
                ) {
                    ListItem(position = GroupPosition.of(index, order.size)) {
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
                ListItem(position = GroupPosition.of(index, stops.size)) {
                    +name
                    supporting { +detail }
                }
            }
        }
    }
}

// Pressing it walks Idle → Loading → Error, which is the sequence worth
// feeling; the knob is how a reader reaches `Done` and the end label without
// having to guess that a fourth state exists.
private val loadMoreState = Knob.Choice("Status", LoadMoreStatus.entries.toList())

internal val LoadMoreDemo = ComponentDemo(
    slug = "load-more",
    knobs = listOf(loadMoreState),
) {
    val picked = this[loadMoreState]
    var status by remember { mutableStateOf(picked) }
    LaunchedEffect(picked) { status = picked }
    LaunchedEffect(status) {
        if (status == LoadMoreStatus.Loading) {
            delay(1_200)
            status = LoadMoreStatus.Error
        }
    }
    LoadMore(
        status = status,
        onLoadMore = { status = LoadMoreStatus.Loading },
        errorMessage = "Couldn't load more departures",
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
                ListItem(position = GroupPosition.of(index, 10)) {
                    +"Departure ${index + 1}"
                    supporting { +"Elizabeth Quay" }
                }
            }
        }
    }
}

/** A departure board's row: the same forty on every run. */
private class DemoDeparture(
    val id: Int,
    val route: String,
    val destination: String,
    val platform: Int,
    val departs: String,
    val fare: Int,
)

private val demoDepartures = List(40) { index ->
    val destinations = listOf(
        "Elizabeth Quay", "Fremantle", "Joondalup", "Midland", "Armadale",
        "Mandurah", "Perth Airport", "Ellenbrook", "Scarborough Beach", "Cannington",
    )
    DemoDeparture(
        id = index,
        route = listOf("950", "T1", "103", "Y", "86", "30", "FRM", "MAN")[index % 8],
        destination = destinations[(index * 7) % destinations.size],
        platform = 1 + (index * 5) % 9,
        departs = "${8 + index / 6}:${((index * 10) % 60).toString().padStart(2, '0')}",
        fare = 330 + (index % 4) * 145,
    )
}

private val tableLines = Knob.Choice("Lines", TableLines.entries.toList(), TableLines.Rows)

private val tableSelection = Knob.Choice("Selection", TableSelection.entries.toList())

private val tableStriped = Knob.Flag("Striped", initial = true)

private val tableOutlined = Knob.Flag("Outlined", initial = false)

/** The route column pinned at the start while the rest scroll across. */
private val tablePinRoute = Knob.Flag("Pin route", initial = true)

private val tableFooter = Knob.Flag("Footer", initial = true)

internal val TableDemo = ComponentDemo(
    slug = "table",
    knobs = listOf(tableLines, tableSelection, tableStriped, tableOutlined, tablePinRoute, tableFooter),
) {
    var sort by remember { mutableStateOf<TableSort?>(null) }
    var selected by remember { mutableStateOf(emptySet<Any>()) }
    // Sorting is the caller's: the table says what was asked for, and this puts
    // the rows in that order.
    val rows = remember(sort) {
        val by: Comparator<DemoDeparture> = when (sort?.column) {
            "Route" -> compareBy { it.route }
            "Destination" -> compareBy { it.destination }
            "Platform" -> compareBy { it.platform }
            "Fare" -> compareBy { it.fare }
            else -> compareBy { it.id }
        }
        demoDepartures.sortedWith(if (sort?.direction == SortDirection.Descending) by.reversed() else by)
    }
    val footer = this[tableFooter]
    Table(
        items = rows,
        modifier = Modifier.fillMaxWidth().height(320.dp),
        key = { it.id },
        stickyColumns = if (this[tablePinRoute]) 1 else 0,
        striped = this[tableStriped],
        lines = this[tableLines],
        outlined = this[tableOutlined],
        sort = sort,
        onSortChange = { sort = it },
        selection = this[tableSelection],
        selected = selected,
        onSelectedChange = { selected = it },
        onRowClick = { echo("Opened the ${it.departs} to ${it.destination}") },
    ) {
        column("Route", width = 72.dp) { +it.route }
        column("Destination", weight = 1f, minWidth = 160.dp) { +it.destination }
        column("Platform", numeric = true) { +"${it.platform}" }
        column("Departs", numeric = true, sortable = false, footer = if (footer) ({ +"${rows.size} services" }) else null) {
            +it.departs
        }
        column("Fare", numeric = true, footer = if (footer) ({ +dollars(rows.sumOf { it.fare }) }) else null) {
            +dollars(it.fare)
        }
    }
}

private fun dollars(cents: Int): String = "$${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"

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
    TableDemo,
)
