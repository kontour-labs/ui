package io.kontour.ui.demo

import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import io.kontour.icons.tabler.outline.Bike
import io.kontour.icons.tabler.outline.HelpCircle
import io.kontour.ui.components.list.DisclosureListItem
import io.kontour.ui.components.list.ListItemScope
import io.kontour.ui.components.list.ListItemSplit
import io.kontour.ui.components.list.SplitListItem
import io.kontour.icons.tabler.outline.Train
import androidx.compose.foundation.gestures.Orientation
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.kontour.icons.tabler.Tabler
import io.kontour.icons.tabler.outline.Archive
import io.kontour.icons.tabler.outline.Bell
import io.kontour.icons.tabler.outline.Bus
import io.kontour.icons.tabler.outline.ChevronDown
import io.kontour.icons.tabler.outline.GripVertical
import io.kontour.icons.tabler.outline.Moon
import io.kontour.icons.tabler.outline.Pin
import io.kontour.icons.tabler.outline.Star
import io.kontour.icons.tabler.outline.Trash
import io.kontour.ui.components.display.Tag
import io.kontour.ui.components.list.rememberScrollbarListState
import io.kontour.ui.components.list.scrollbar
import io.kontour.ui.motion.ScrollDefaults
import io.kontour.ui.theme.Tone
import io.kontour.ui.components.list.ExpandingListItem
import io.kontour.ui.components.list.ListGroup
import io.kontour.ui.components.list.ListItem
import io.kontour.ui.components.list.ListItemDefaults
import io.kontour.ui.components.selection.SegmentedControl
import androidx.compose.foundation.layout.fillMaxSize
import io.kontour.ui.components.list.indexedBy
import io.kontour.ui.components.list.listSection
import io.kontour.ui.components.list.IndexScroller
import androidx.compose.foundation.layout.PaddingValues
import io.kontour.ui.components.list.IndexScrollerDefaults
import io.kontour.ui.components.list.IndexAlphabet
import io.kontour.ui.foundation.GroupPosition
import io.kontour.ui.components.list.ListSection
import io.kontour.ui.components.list.LoadMore
import io.kontour.ui.components.list.LoadMoreStatus
import io.kontour.ui.components.list.PullToRefresh
import io.kontour.ui.components.list.ReorderHandleSide
import io.kontour.ui.components.list.ReorderableItem
import io.kontour.ui.components.list.Scrollbar
import io.kontour.ui.components.list.SwipeAction
import io.kontour.ui.components.list.SwipeActions
import io.kontour.ui.components.list.SwipeToDismiss
import io.kontour.ui.components.list.fadingEdges
import io.kontour.ui.components.list.rememberReorderableState
import io.kontour.ui.components.selection.Switch
import androidx.compose.ui.semantics.Role
import io.kontour.ui.components.selection.SelectionRow
import io.kontour.ui.components.table.SortDirection
import io.kontour.ui.components.table.Table
import io.kontour.ui.components.table.TableLines
import io.kontour.ui.components.table.TableSelection
import io.kontour.ui.components.table.TableSort
import io.kontour.ui.foundation.Text
import io.kontour.ui.foundation.shape
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
        ListGroup(modifier = Modifier.fillMaxWidth()) {
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

/** Its question's glyph before the words, and the body lined up under the words. */
private val disclosureIcon = Knob.Flag("Leading icon", initial = true)

internal val DisclosureListItemDemo = ComponentDemo(slug = "disclosure-list-item", knobs = listOf(disclosureIcon)) {
    var first by remember { mutableStateOf(true) }
    var second by remember { mutableStateOf(false) }
    val icon = this[disclosureIcon]
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ListItemDefaults.Spacing)) {
        DisclosureListItem(
            expanded = first,
            onExpandedChange = { first = it },
            position = GroupPosition.First,
            header = {
                +"Why is my train late?"
                if (icon) leading { +Tabler.Outline.HelpCircle }
            },
        ) {
            Text("Trains give way to freight between Midland and Bellevue. A delay there reaches every stop after it.")
        }
        DisclosureListItem(
            expanded = second,
            onExpandedChange = { second = it },
            position = GroupPosition.Last,
            header = {
                +"Can I take my bike?"
                if (icon) leading { +Tabler.Outline.Bike }
            },
        ) {
            Text("Outside the peaks, in the marked carriage, as long as it does not block a door.")
        }
    }
}

/** How the two halves share a row, by the names the page gives them. */
private val splitPreset = Knob.Choice(
    "Split",
    listOf(
        "50/50" to ListItemSplit.Halves,
        "Auto/square" to ListItemSplit.AutoSquare,
        "Square/auto" to ListItemSplit.SquareAuto,
        "2/1" to ListItemSplit.TwoToOne,
        "1/2" to ListItemSplit.OneToTwo,
    ),
    name = { it.first },
)

internal val SplitListItemDemo = ComponentDemo(slug = "split-list-item", knobs = listOf(splitPreset)) {
    val split = this[splitPreset].second
    val square = split == ListItemSplit.AutoSquare || split == ListItemSplit.SquareAuto
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ListItemDefaults.Spacing)) {
        if (square) {
            // A stop, and beside it the one thing to do with it: a tile that
            // saves it, as wide as the row is tall.
            var saved by remember { mutableStateOf(setOf("Perth Underground")) }
            val stops = listOf(
                "Perth Underground" to "Platform 2 · Joondalup line",
                "Elizabeth Quay" to "Stand C · Route 950",
            )
            stops.forEachIndexed { index, (stop, where) ->
                SplitListItem(split = split, position = GroupPosition.of(index, stops.size)) {
                    val words: ListItemScope.() -> Unit = {
                        leading { +Tabler.Outline.Train }
                        +stop
                        supporting { +where }
                    }
                    val star: ListItemScope.() -> Unit = { leading { +Tabler.Outline.Star } }
                    val toggle = { saved = if (stop in saved) saved - stop else saved + stop }
                    if (split == ListItemSplit.SquareAuto) {
                        start(onClick = toggle, selected = stop in saved, role = Role.Checkbox, contentDescription = "Save $stop", content = star)
                        end(onClick = { echo(stop) }, content = words)
                    } else {
                        start(onClick = { echo(stop) }, content = words)
                        end(onClick = toggle, selected = stop in saved, role = Role.Checkbox, contentDescription = "Save $stop", content = star)
                    }
                }
            }
        } else {
            // Two of a kind to a row, each a place to go: short words, as a
            // third of a phone's width has room for.
            val modes = listOf(
                ("Trains" to "Next in 4 min") to ("Buses" to "Next in 11 min"),
                ("Ferries" to "Next in 25 min") to ("Bikes" to "3 docks free"),
            )
            modes.forEachIndexed { index, (first, second) ->
                SplitListItem(split = split, position = GroupPosition.of(index, modes.size)) {
                    start(onClick = { echo(first.first) }) {
                        +first.first
                        supporting { +first.second }
                    }
                    end(onClick = { echo(second.first) }) {
                        +second.first
                        supporting { +second.second }
                    }
                }
            }
        }
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

/**
 * What the section holds: rows, or a paragraph and a control.
 *
 * The second is how a good many sections are really used, and the one where the
 * title's inset is decided by what is under it — flush with the paragraph, not
 * 16dp in over a column of rows that is not there.
 */
private val sectionContent = Knob.Choice("Content", listOf("Rows", "Other"))

internal val ListSectionDemo = ComponentDemo(
    slug = "list-section",
    knobs = listOf(sectionFooter, sectionContent),
) {
    var theme by remember { mutableStateOf(0) }
    var alerts by remember { mutableStateOf(true) }
    var units by remember { mutableStateOf(0) }
    val themes = listOf("Match system", "Always light", "Always dark")
    val rows = this[sectionContent] == "Rows"
    ListSection(
        modifier = Modifier.fillMaxWidth(),
        title = { +if (rows) "Appearance" else "Planning" },
        supporting = if (rows) {
            { +"How the app looks on this device" }
        } else {
            null
        },
        footer = when {
            !this[sectionFooter] -> null
            rows -> {
                { +"Always dark keeps the screen dark even when the system is light." }
            }
            else -> {
                { +"Used to work out how long the walks between stops take." }
            }
        },
        // Separate things rather than one card, so the room a form has.
        spacing = if (rows) ListItemDefaults.Spacing else Theme.spacing.md,
    ) {
        if (rows) {
            // One of each: a row that opens a choice, and a row that is one.
            SelectionRow(
                onClick = { theme = (theme + 1) % themes.size },
                position = GroupPosition.First,
            ) {
                +"Theme"
                leading { +Tabler.Outline.Moon }
                trailing { +themes[theme] }
            }
            SelectionRow(
                selected = alerts,
                onSelectedChange = { alerts = it },
                role = Role.Switch,
                position = GroupPosition.Last,
            ) {
                +"Delay alerts"
                supporting { +"Only for favourited routes" }
                leading { +Tabler.Outline.Bell }
                trailing { Switch(checked = alerts, onCheckedChange = null) }
            }
        } else {
            Text("Trips are planned at the speed you walk, in the units you read distances in.")
            SegmentedControl(
                options = listOf("Kilometres", "Miles"),
                selectedIndex = units,
                onSelectedIndexChange = { units = it },
                modifier = Modifier.fillMaxWidth(),
            )
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

/** The same tick for a tap on a revealed action, before it runs and the row closes. */
private val swipeTapConfirmation = Knob.Flag("Tick on tap", initial = true)

internal val SwipeActionsDemo = ComponentDemo(
    slug = "swipe-actions",
    knobs = listOf(swipeActionCount, swipeConfirmation, swipeTapConfirmation),
) {
    val confirmation = this[swipeConfirmation]
    val tapConfirmation = this[swipeTapConfirmation]
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
        // One group, a list item's gap between its rows; each swipe container
        // takes its row's shape, so a middle row slides square-cornered.
        @Composable
        fun shapeOf(position: GroupPosition) =
            position.shape(ListItemDefaults.Shape, ListItemDefaults.InnerCorner, Orientation.Vertical)
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.Spacing)) {
            SwipeActions(
                end = trailing.take(this@ComponentDemo[swipeActionCount]),
                fullSwipeConfirmation = confirmation,
                tapConfirmation = tapConfirmation,
                shape = shapeOf(GroupPosition.First),
                start = listOf(
                    SwipeAction(
                        label = "Favourite",
                        icon = Tabler.Outline.Star,
                        onAction = { echo("Favourited") },
                        background = Theme.colours.success.solid,
                    ),
                ),
            ) {
                ListItem(onClick = { echo("Perth Busport") }, position = GroupPosition.First) {
                    +"Perth Busport"
                    supporting { +"Swipe either way" }
                }
            }
            // Actions on one side only: the other way the row gives under the finger
            // and comes back, rather than doing nothing until it is let go.
            SwipeActions(
                end = trailing.take(1),
                fullSwipeConfirmation = confirmation,
                tapConfirmation = tapConfirmation,
                shape = shapeOf(GroupPosition.Middle),
            ) {
                ListItem(onClick = { echo("Elizabeth Quay") }, position = GroupPosition.Middle) {
                    +"Elizabeth Quay"
                    supporting { +"Actions on one side" }
                }
            }
            // A tall row: its actions stop at an ordinary row's height and sit
            // centred on it, rather than growing into slabs as tall as the row.
            SwipeActions(
                end = trailing.take(this@ComponentDemo[swipeActionCount]),
                fullSwipeConfirmation = confirmation,
                tapConfirmation = tapConfirmation,
                shape = shapeOf(GroupPosition.Middle),
            ) {
                ListItem(onClick = { echo("Fremantle") }, position = GroupPosition.Middle) {
                    overline { +"Fremantle line · Terminus" }
                    +"Fremantle"
                    supporting {
                        +(
                            "A tall row. Heritage-listed, opened in 1907, at the end of the " +
                                "Fremantle line and a short walk from the ferry to Rottnest Island."
                            )
                    }
                }
            }
            SwipeToDismiss(
                onDismissRequest = { echo("Dismissed") },
                label = "Remove",
                icon = Tabler.Outline.Trash,
                fullSwipeConfirmation = confirmation,
                shape = shapeOf(GroupPosition.Last),
            ) {
                ListItem(position = GroupPosition.Last) {
                    +"Elizabeth Quay Station"
                    supporting { +"Fremantle line · Platform 2" }
                }
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
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.Spacing),
            flingBehavior = ScrollDefaults.flingBehavior(),
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
    val scroll = rememberScrollState()
    PullToRefresh(
        refreshing = refreshing,
        onRefresh = { refreshing = true },
        scrollState = scroll,
        modifier = Modifier.fillMaxWidth().height(200.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(scroll, flingBehavior = ScrollDefaults.flingBehavior()),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.Spacing),
        ) {
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

/** The scroller the bar is on: one of each kind foundation ships, and one that runs across. */
private val scrollbarOn = Knob.Choice("Scroller", listOf("Column", "Lazy list", "Grid", "Row"))

internal val ScrollbarDemo = ComponentDemo(slug = "scrollbar", knobs = listOf(scrollbarOn)) {
    // `alwaysVisible`, because under touch the bar is up only while the content
    // moves — and on a touch device, or in a still render, it would be gone.
    val box = Modifier.fillMaxWidth().height(160.dp)
    when (this[scrollbarOn]) {
        "Lazy list" -> {
            val list = rememberScrollbarListState()
            LazyColumn(box.scrollbar(list, alwaysVisible = true), state = list, flingBehavior = ScrollDefaults.flingBehavior()) {
                items(40) { Text("Departure ${it + 1}", style = Theme.typography.bodyMedium) }
            }
        }
        "Grid" -> {
            val grid = rememberLazyGridState()
            LazyVerticalGrid(GridCells.Fixed(3), box.scrollbar(grid, alwaysVisible = true), state = grid) {
                items(90) { Text("Stop ${it + 1}", style = Theme.typography.bodyMedium) }
            }
        }
        "Row" -> {
            val row = rememberScrollbarListState()
            LazyRow(
                Modifier.fillMaxWidth().height(64.dp).scrollbar(row, Orientation.Horizontal, alwaysVisible = true),
                state = row,
                horizontalArrangement = Arrangement.spacedBy(Theme.spacing.md),
            ) {
                items(30) { Text("Route ${it + 1}", style = Theme.typography.bodyMedium) }
            }
        }
        else -> {
            val scroll = rememberScrollState()
            Column(box.scrollbar(scroll, alwaysVisible = true).verticalScroll(scroll, flingBehavior = ScrollDefaults.flingBehavior())) {
                repeat(14) { Text("Departure ${it + 1}", style = Theme.typography.bodyMedium) }
            }
        }
    }
}

/**
 * Names in each script the rail knows, so the alphabet knob shows each one
 * filing its own words: kana by row, Hangul by first consonant.
 */
private val indexNames: Map<String, List<String>> = mapOf(
    "Latin" to listOf(
        "Armadale", "Ashfield", "Bassendean", "Bayswater", "Claremont", "Cottesloe", "Elizabeth Quay",
        "Esplanade", "Fremantle", "Glendalough", "Joondalup", "Kelmscott", "Leederville", "Maddington",
        "Mandurah", "Midland", "Mosman Park", "North Fremantle", "Perth", "Perth Underground", "Queens Park",
        "Rockingham", "Showgrounds", "Subiaco", "Thornlie", "Victoria Park", "Warwick", "Whitfords",
        "Yanchep", "1 Stop Shop",
    ),
    "Cyrillic" to listOf(
        "Арбатская", "Бауманская", "Волжская", "Выхино", "Динамо", "Ёлки", "Жулебино", "Измайловская",
        "Кропоткинская", "Лубянка", "Маяковская", "Новокузнецкая", "Охотный Ряд", "Парк культуры",
        "Римская", "Сокольники", "Таганская", "Улица 1905 года", "Фили", "Цветной бульвар", "Чеховская",
        "Шаболовская", "Электрозаводская", "Юго-Западная", "Ясенево", "Moscow City",
    ),
    "Greek" to listOf(
        "Αθήνα", "Βόλος", "Γλυφάδα", "Δάφνη", "Ελληνικό", "Ζωγράφου", "Ηράκλειο", "Θεσσαλονίκη", "Ιωάννινα",
        "Καλαμάτα", "Λάρισα", "Μαρούσι", "Νίκαια", "Ομόνοια", "Πειραιάς", "Ρόδος", "Σύνταγμα", "Τρίπολη",
        "Φάληρο", "Χανιά", "Ωρωπός",
    ),
    "Hebrew" to listOf(
        "אשדוד", "באר שבע", "גבעתיים", "דימונה", "הרצליה", "זכרון יעקב", "חיפה", "טבריה", "ירושלים", "כרמיאל",
        "לוד", "מודיעין", "נתניה", "עכו", "פתח תקווה", "צפת", "קריית גת", "רחובות", "שדרות", "תל אביב",
    ),
    "Arabic" to listOf(
        "أبوظبي", "إربد", "بيروت", "تونس", "جدة", "حلب", "دبي", "الرباط", "صنعاء", "طرابلس", "عمّان",
        "غزة", "القاهرة", "مسقط", "نواكشوط", "وهران",
    ),
    "Kana" to listOf(
        "あきはばら", "いけぶくろ", "うえの", "おおさか", "かんだ", "ぎんざ", "しぶや", "しながわ", "ちば",
        "とうきょう", "なごや", "にっぽり", "はかた", "ひろしま", "ふくおか", "まいはま", "めぐろ", "よこはま",
        "りょうごく", "わこう", "パリ", "ロンドン", "東京駅",
    ),
    "Hangul" to listOf(
        "강남", "광주", "대구", "대전", "목포", "부산", "서울", "속초", "인천", "울산", "전주", "제주", "춘천",
        "청주", "포항", "평택", "한강", "Seoul Station",
    ),
)

private val indexAlphabets: Map<String, IndexAlphabet> = mapOf(
    "Latin" to IndexAlphabet.Latin,
    "Cyrillic" to IndexAlphabet.Cyrillic + IndexAlphabet.Latin,
    "Greek" to IndexAlphabet.Greek + IndexAlphabet.Latin,
    "Hebrew" to IndexAlphabet.Hebrew + IndexAlphabet.Latin,
    "Arabic" to IndexAlphabet.Arabic + IndexAlphabet.Latin,
    "Kana" to IndexAlphabet.Kana + IndexAlphabet.Latin,
    "Hangul" to IndexAlphabet.Hangul + IndexAlphabet.Latin,
)

/** Which script's names, filed under that script's alphabet then Latin's. */
private val indexScript = Knob.Choice("Alphabet", indexAlphabets.keys.toList(), "Latin", name = { it })

private val indexEnabled = Knob.Flag("Enabled", initial = true)

/** Every other name dropped, so more of the rail's letters have nothing under them. */
private val indexSparse = Knob.Flag("Sparse")

internal val IndexScrollerDemo = ComponentDemo(
    slug = "index-scroller",
    knobs = listOf(indexScript, indexEnabled, indexSparse),
) {
    val script = this[indexScript]
    val alphabet = indexAlphabets.getValue(script)
    val all = indexNames.getValue(script)
    val names = if (this[indexSparse]) all.filterIndexed { i, _ -> i % 2 == 0 } else all
    val formats = Theme.formats
    val indexed = remember(names, alphabet, formats) { names.indexedBy(alphabet, formats) { it } }
    val list = rememberLazyListState()
    Box(Modifier.fillMaxWidth().height(360.dp)) {
        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(end = IndexScrollerDefaults.Width),
        ) {
            indexed.groups.forEach { group ->
                listSection(group.items, key = { it }, sectionKey = group.label, title = { +group.label }) { name, position ->
                    ListItem(position = position) { +name }
                }
            }
        }
        IndexScroller(
            state = list,
            sections = indexed.sections,
            modifier = Modifier.align(Alignment.CenterEnd),
            enabled = this@ComponentDemo[indexEnabled],
            alphabet = alphabet,
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
                .verticalScroll(scroll, flingBehavior = ScrollDefaults.flingBehavior()),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.Spacing),
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
    DisclosureListItemDemo,
    SplitListItemDemo,
    ListSectionDemo,
    SwipeActionsDemo,
    ReorderableItemDemo,
    PullToRefreshDemo,
    LoadMoreDemo,
    ScrollbarDemo,
    IndexScrollerDemo,
    FadingEdgesDemo,
    TableDemo,
)
