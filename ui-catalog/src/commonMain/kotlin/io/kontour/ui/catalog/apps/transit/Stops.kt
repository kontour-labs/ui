package io.kontour.ui.catalog.apps.transit

import io.kontour.ui.catalog.pageScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import io.kontour.icons.tabler.Tabler
import io.kontour.icons.tabler.outline.Check
import io.kontour.icons.tabler.outline.ChevronRight
import io.kontour.icons.tabler.outline.MapPin
import io.kontour.icons.tabler.outline.Star
import io.kontour.icons.tabler.outline.Ticket
import io.kontour.ui.catalog.apps.SkylineArt
import io.kontour.ui.catalog.apps.rememberAppToast
import io.kontour.ui.components.action.Button
import io.kontour.ui.components.action.ButtonVariant
import io.kontour.ui.components.action.IconToggleButton
import io.kontour.ui.components.display.HorizontalTimeline
import io.kontour.ui.components.display.KeyValueList
import io.kontour.ui.components.display.StepProgress
import io.kontour.ui.components.display.TimelineItem
import io.kontour.ui.components.list.IndexAlphabet
import io.kontour.ui.components.list.IndexScroller
import io.kontour.ui.components.list.IndexScrollerDefaults
import io.kontour.ui.components.list.ListItem
import io.kontour.ui.components.list.indexedBy
import io.kontour.ui.components.list.listSection
import io.kontour.ui.components.selection.Chip
import io.kontour.ui.components.selection.ChipGroup
import io.kontour.ui.components.table.Table
import io.kontour.ui.components.table.TableAlign
import io.kontour.ui.components.text.SearchField
import io.kontour.ui.foundation.GroupPosition
import io.kontour.ui.foundation.Text
import io.kontour.ui.motion.pageBack
import io.kontour.ui.nav.Tab
import io.kontour.ui.nav.TabBar
import io.kontour.ui.nav.TopBar
import io.kontour.ui.nav.TopBarStyle
import io.kontour.ui.nav.navigationSuitePadding
import io.kontour.ui.nav.rememberTopBarCollapseState
import io.kontour.ui.nav.topBarCollapse
import io.kontour.ui.theme.Theme
import androidx.compose.ui.unit.dp

/**
 * Every stop on the network, A to Z, with a search above and an index beside.
 *
 * The index is the reader's alphabet then Latin's, as `IndexAlphabet.forLocale`
 * gives it, so on a Korean phone the Hangul consonants come first even though
 * every stop here is in Latin letters — which is what a contacts list does,
 * and why the letters with nothing under them are dimmed rather than gone.
 */
@Composable
internal fun StopsPage(onStop: (String) -> Unit) {
    val query = rememberTextFieldState()
    var filter by remember { mutableStateOf("") }
    val collapse = rememberTopBarCollapseState()
    val formats = Theme.formats
    val alphabet = IndexAlphabet.forLocale(Theme.locale)
    val matching = remember(filter) { stops.filter { it.name.contains(filter.trim(), ignoreCase = true) } }
    val indexed = remember(matching, alphabet, formats) { matching.indexedBy(alphabet, formats) { it.name } }
    val list = rememberLazyListState()
    val suite = navigationSuitePadding()
    val direction = LocalLayoutDirection.current

    Column(Modifier.fillMaxSize().topBarCollapse(collapse)) {
        TopBar(style = TopBarStyle.Large, navigation = rootNavigation(), collapseState = collapse) {
            +"Stops"
            supporting { +"${stops.size} on five lines" }
        }
        SearchField(
            state = query,
            placeholder = "Search stops",
            onQuery = { filter = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = suite.calculateStartPadding(direction) + Theme.spacing.md,
                    end = suite.calculateEndPadding(direction) + Theme.spacing.md,
                    bottom = Theme.spacing.sm,
                ),
        )
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                state = list,
                modifier = Modifier
                    .fillMaxSize()
                    // No scrollbar: the index down the edge is how this list is
                    // moved through.
                    .testTag("stops"),
                contentPadding = PaddingValues(
                    start = suite.calculateStartPadding(direction) + Theme.spacing.md,
                    end = suite.calculateEndPadding(direction) + IndexScrollerDefaults.Width + Theme.spacing.xs,
                    bottom = suite.calculateBottomPadding() + Theme.spacing.md,
                ),
            ) {
                if (matching.isEmpty()) {
                    item {
                        Text(
                            "No stop is called that.",
                            style = Theme.typography.bodyMedium,
                            colour = Theme.colours.contentMuted,
                            modifier = Modifier.padding(vertical = Theme.spacing.md),
                        )
                    }
                }
                // A section a letter: its heading, its rows a hairline apart
                // with their corners worked out, and where the rail's letter
                // lands, all from the section.
                indexed.groups.forEach { group ->
                    listSection(group.items, key = { it.name }, sectionKey = group.label, title = { +group.label }) { stop, position ->
                        ListItem(
                            onClick = { onStop(stop.name) },
                            position = position,
                            modifier = Modifier.testTag("stop-${stop.name}"),
                        ) {
                            leading { +Tabler.Outline.MapPin }
                            +stop.name
                            supporting { +stop.lines.joinToString(" · ") { it.name.removeSuffix(" line") } }
                            trailing { +Tabler.Outline.ChevronRight }
                        }
                    }
                }
            }
            IndexScroller(
                state = list,
                sections = indexed.sections,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = suite.calculateEndPadding(direction), bottom = suite.calculateBottomPadding()),
                alphabet = alphabet,
            )
        }
    }
}

/**
 * One stop: its picture, its lines, what it has, and a way to the ticket.
 *
 * A hero bar again, with a back button this time — the stop is a page on the
 * stack — and a favourite toggle among its actions, drawn on the same disc as
 * the back button so both read over any picture.
 */
@Composable
internal fun StopPage(stop: StopInfo, onLine: (String) -> Unit, onTicket: () -> Unit) {
    val toast = rememberAppToast()
    var favourite by remember { mutableStateOf(false) }
    // Drawn over the page and following its scroll: the page is the only
    // thing that scrolls, so the bar and it never share out a drag.
    val scroll = rememberScrollState()
    val collapse = rememberTopBarCollapseState(scroll)
    val seed = remember(stop) { stop.name.sumOf { it.code } }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .pageScroll(scroll)
                .padding(top = collapse.openHeight)
                .padding(transitPagePadding()),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.md),
        ) {
            StopDetails(stop, onLine, onTicket)
        }
        TopBar(
            style = TopBarStyle.Hero,
            onBack = pageBack(),
            image = { SkylineArt(Modifier.fillMaxSize(), seed = seed) },
            actions = {
                IconToggleButton(
                    icon = Tabler.Outline.Star,
                    contentDescription = "Favourite",
                    checked = favourite,
                    onCheckedChange = {
                        favourite = it
                        toast(if (it) "${stop.name} added to favourites" else "${stop.name} removed from favourites")
                    },
                )
            },
            collapseState = collapse,
        ) {
            +stop.name
            supporting { +"Zone ${stop.zone}" }
        }
    }
}

/** What there is to know about a stop, under its bar. */
@Composable
private fun StopDetails(stop: StopInfo, onLine: (String) -> Unit, onTicket: () -> Unit) {
    TransitHeading("Lines")
    ChipGroup {
        for (line in stop.lines) {
            Chip(onClick = { onLine(line.name) }) { +line.name }
        }
    }
    TransitHeading("At this stop")
    KeyValueList {
        item("Zone", "${stop.zone}")
        item("Lines", "${stop.lines.size}")
        item("Every", "${stop.lines.minOf { it.every }} min at peak")
        if (stop.accessible) {
            item("Step-free", announcement = "yes") { +Tabler.Outline.Check }
        } else {
            item("Step-free", "No — ramps at the Wellington Street end")
        }
    }
    Button(onClick = onTicket, variant = ButtonVariant.Secondary, modifier = Modifier.fillMaxWidth()) {
        +Tabler.Outline.Ticket
        +"Show my ticket"
    }
}

/**
 * A line: its stations across the page, where the next train is along them,
 * and its timetable for each kind of day.
 *
 * The stations are a horizontal timeline that scrolls on its own, with the
 * part already run drawn as progress; a step bar under it counts the same
 * stops, for a reader who wants the number rather than the picture. The
 * timetable is a table under a tab bar, sticky on its first column so the
 * service stays beside its times when the table scrolls sideways.
 */
@Composable
internal fun LinePage(line: Line, onStop: (String) -> Unit) {
    var day by remember { mutableStateOf(0) }
    val at = line.stations.size / 3
    Column(Modifier.fillMaxSize()) {
        TopBar(onBack = pageBack()) {
            +line.name
            supporting { +"Every ${line.every} min" }
        }
        Column(
            Modifier.fillMaxSize().pageScroll().padding(transitPagePadding()),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.md),
        ) {
            TransitHeading("Next train")
            HorizontalTimeline(progress = at.toFloat() / (line.stations.size - 1)) {
                line.stations.forEachIndexed { i, station ->
                    TimelineItem(
                        filled = i <= at,
                        modifier = Modifier.testTag("station-$station"),
                    ) {
                        +station
                        supporting { +if (i <= at) "Passed" else "${(i - at) * 3} min" }
                    }
                }
            }
            StepProgress(
                current = at + 1,
                total = line.stations.size,
                gap = 0.dp,
                contentDescription = "Stop ${at + 1} of ${line.stations.size}",
            )
            Text(
                "Between ${line.stations[at]} and ${line.stations[at + 1]}, ${line.stations.size - at - 1} stops from ${line.stations.last()}.",
                style = Theme.typography.bodySmall,
                colour = Theme.colours.contentMuted,
            )
            ChipGroup {
                Chip(onClick = { onStop(line.stations.first()) }) { +line.stations.first() }
                Chip(onClick = { onStop(line.stations.last()) }) { +line.stations.last() }
            }
            TransitHeading("Timetable")
            TabBar {
                listOf("Weekdays", "Saturday", "Sunday").forEachIndexed { i, label ->
                    Tab(selected = day == i, onClick = { day = i }, key = label) { +label }
                }
            }
            Table(
                items = timetable(line, day),
                modifier = Modifier.fillMaxWidth().padding(top = Theme.spacing.xs),
                stickyColumns = 1,
                striped = true,
            ) {
                column("Service", width = ServiceColumn) { +it.service }
                column("Departs") { +it.departs }
                column("Arrives") { +it.arrives }
                column("Platform", align = TableAlign.End) { +it.platform }
            }
        }
    }
}

private val ServiceColumn = 72.dp
