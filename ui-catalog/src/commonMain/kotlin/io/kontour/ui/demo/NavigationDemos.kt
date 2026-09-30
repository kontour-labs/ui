package io.kontour.ui.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Bell
import com.composables.icons.tabler.outline.Calendar
import com.composables.icons.tabler.outline.DotsVertical
import com.composables.icons.tabler.outline.Home
import com.composables.icons.tabler.outline.Map
import com.composables.icons.tabler.outline.Pin
import com.composables.icons.tabler.outline.Plus
import com.composables.icons.tabler.outline.Search
import com.composables.icons.tabler.outline.Star
import com.composables.icons.tabler.outline.Ticket
import com.composables.icons.tabler.outline.Trash
import com.composables.icons.tabler.outline.User
import io.kontour.ui.adaptive.WindowSizeClassProvider
import io.kontour.ui.components.action.Button
import io.kontour.ui.components.action.ButtonSize
import io.kontour.ui.components.action.ButtonVariant
import io.kontour.ui.components.action.FabSize
import io.kontour.ui.components.action.FloatingActionButton
import io.kontour.ui.components.action.IconButton
import io.kontour.ui.foundation.Text
import io.kontour.ui.nav.Breadcrumbs
import io.kontour.ui.nav.Crumb
import io.kontour.ui.nav.ModalNavRail
import io.kontour.ui.nav.NavBar
import io.kontour.ui.nav.NavBarStyle
import io.kontour.ui.nav.NavScope
import io.kontour.ui.nav.NavRail
import io.kontour.ui.nav.NavExpandPlacement
import io.kontour.ui.nav.NavigationSuiteType
import io.kontour.ui.nav.NavSearch
import io.kontour.ui.nav.NavigationSuiteScaffold
import io.kontour.ui.nav.Pagination
import io.kontour.ui.nav.Tab
import io.kontour.ui.nav.TabBar
import io.kontour.ui.nav.TopBar
import io.kontour.ui.nav.TopBarStyle
import io.kontour.ui.nav.rememberNavSearchState
import io.kontour.ui.nav.rememberNavigationSuiteState
import io.kontour.ui.sheet.SheetSide
import io.kontour.ui.components.display.Badge
import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.tabler.outline.Route
import io.kontour.ui.overlay.DropdownMenu
import io.kontour.ui.overlay.OverlayAlignment
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.Theme

/**
 * A bordered box a navigation surface can fill.
 *
 * A nav bar sizes itself to the bottom of whatever contains it and a rail to the
 * leading edge, so both need a container with edges to find — dropped straight
 * into a documentation page they would try to be the page. The frame also
 * carries its own `OverlayHost`, because a rail's search field and a bar's
 * expanding slot render into the nearest one and the site's is the whole window.
 */
@Composable
private fun Frame(height: Dp, content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .border(
                width = Theme.sizing.borderWidth,
                color = Theme.colours.outline,
                shape = Theme.shapes.medium,
            )
            .clip(Theme.shapes.medium)
            .background(Theme.colours.surfaceSunken),
    ) {
        OverlayHost(Modifier.fillMaxSize()) { content() }
    }
}

/** One demo destination. */
private class Destination(val key: String, val label: String, val icon: ImageVector, val badge: Int? = null)

/**
 * The destinations the demos draw. Nine, because the rail's overflow is only
 * shown by more destinations than a 300dp rail is tall; the bar takes four.
 */
private val allDestinations = listOf(
    Destination("home", "Home", Tabler.Outline.Home),
    Destination("map", "Map", Tabler.Outline.Map),
    Destination("plan", "Plan", Tabler.Outline.Calendar, badge = 2),
    Destination("profile", "Profile", Tabler.Outline.User),
    Destination("nearby", "Nearby", Tabler.Outline.Pin),
    Destination("search", "Search", Tabler.Outline.Search),
    Destination("alerts", "Alerts", Tabler.Outline.Bell),
    Destination("saved", "Saved", Tabler.Outline.Star),
    Destination("tickets", "Tickets", Tabler.Outline.Ticket),
)

/** The first [count] demo destinations, in whichever surface this is. */
@Composable
private fun NavScope.destinations(here: String, onChange: (String) -> Unit, count: Int = 4) {
    allDestinations.take(count).forEach { d ->
        item(
            selected = here == d.key,
            onClick = { onChange(d.key) },
            key = d.key,
            icon = d.icon,
            badge = d.badge?.let { n -> { Badge(count = n) } },
        ) { +d.label }
    }
}

/** A section and a group, as a sidebar would hold them. */
@Composable
private fun NavScope.tree(here: String, onChange: (String) -> Unit, groupOpen: Boolean, onGroupOpen: (Boolean) -> Unit) {
    item(here == "home", { onChange("home") }, key = "home", icon = Tabler.Outline.Home) { +"Home" }
    section("Travel") {
        item(here == "map", { onChange("map") }, key = "map", icon = Tabler.Outline.Map) { +"Map" }
        group(
            expanded = groupOpen,
            onExpandedChange = onGroupOpen,
            key = "routes",
            icon = Tabler.Outline.Route,
            header = { +"Routes" },
        ) {
            item(here == "950", { onChange("950") }, key = "950", icon = null) { +"950" }
            item(here == "998", { onChange("998") }, key = "998", icon = null) { +"998" }
        }
    }
    divider()
    item(here == "profile", { onChange("profile") }, key = "profile", icon = Tabler.Outline.User) { +"Profile" }
}

/** Something behind a bar, so a floating one is seen to float. */
@Composable
private fun PageBehind() {
    Column(
        Modifier.fillMaxSize().padding(Theme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
    ) {
        repeat(6) { row ->
            Box(
                Modifier
                    .fillMaxWidth(if (row % 2 == 0) 0.9f else 0.6f)
                    .height(14.dp)
                    .clip(Theme.shapes.capsule)
                    .background(if (row % 3 == 0) Theme.colours.accent.solid else Theme.colours.outline)
            )
        }
    }
}

private val barStyle = Knob.Choice("Style", NavBarStyle.entries.toList(), NavBarStyle.Floating)
private val barLabels = Knob.Flag("Labels")

/**
 * A search in the middle of the bar, where a map app keeps it: a pill that opens
 * a field over the screen, sized to the bar rather than to its slot.
 */
private val barSearch = Knob.Flag("Search in the bar")

/**
 * Where an expanded search panel goes, which is a choice about thumbs. Does
 * nothing visible until the pill is tapped, which is why it is a knob rather
 * than a sentence.
 */
private val barSearchPlacement =
    Knob.Choice("Panel", NavExpandPlacement.entries.toList(), NavExpandPlacement.AboveKeyboard)

/** A floating action button, beside a floating bar and inside a docked one. */
private val barAction = Knob.Flag("Action")

internal val NavBarDemo = ComponentDemo(
    slug = "nav-bar",
    knobs = listOf(barStyle, barLabels, barSearch, barSearchPlacement, barAction),
) {
    var here by remember { mutableStateOf("map") }
    val search = rememberNavSearchState()
    val withSearch = this[barSearch]
    val placement = this[barSearchPlacement]
    Frame(height = 160.dp) {
        PageBehind()
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            NavBar(
                style = this@ComponentDemo[barStyle],
                showLabels = this@ComponentDemo[barLabels],
                action = if (this@ComponentDemo[barAction]) {
                    {
                        FloatingActionButton(
                            onClick = { echo("New trip") },
                            icon = Tabler.Outline.Plus,
                            contentDescription = "New trip",
                        )
                    }
                } else {
                    null
                },
            ) {
                val first = allDestinations.take(if (withSearch) 2 else 4)
                first.forEach { d ->
                    item(here == d.key, { here = d.key }, key = d.key, icon = d.icon) { +d.label }
                }
                if (withSearch) {
                    slot(weight = 1f) {
                        NavSearch(
                            state = search,
                            placement = placement,
                            placeholder = "Search stops",
                            searchIcon = Tabler.Outline.Search,
                            onSearch = { echo("Search: $it") },
                        )
                    }
                    allDestinations.subList(2, 4).forEach { d ->
                        item(here == d.key, { here = d.key }, key = d.key, icon = d.icon) { +d.label }
                    }
                }
            }
        }
    }
}

private val railExpanded = Knob.Flag("Expanded")
private val railToggle = Knob.Flag("Toggle", initial = true)
private val railTree = Knob.Flag("Sections and groups")
private val railAlignment = Knob.Choice(
    "Items",
    listOf(Alignment.Top, Alignment.CenterVertically),
    name = { if (it == Alignment.Top) "Top" else "Centred" },
)

internal val NavRailDemo = ComponentDemo(
    slug = "nav-rail",
    knobs = listOf(railExpanded, railToggle, railTree, railAlignment),
) {
    var here by remember { mutableStateOf("map") }
    var expanded by remember { mutableStateOf(false) }
    var groupOpen by remember { mutableStateOf(true) }
    val fixed = this[railExpanded]
    val toggle = this[railToggle]
    val tree = this[railTree]
    Frame(height = 320.dp) {
        NavRail(
            expanded = if (toggle) expanded else fixed,
            onExpandedChange = if (toggle) ({ expanded = it }) else null,
            itemAlignment = this@ComponentDemo[railAlignment],
        ) {
            if (tree) {
                tree(here, { here = it }, groupOpen, { groupOpen = it })
            } else {
                // More than a 320dp rail is tall: it scrolls rather than squashing.
                destinations(here, { here = it }, count = 9)
            }
        }
    }
}

private val modalSide = Knob.Choice("Side", SheetSide.entries.toList(), SheetSide.Start)

internal val ModalNavRailDemo = ComponentDemo(slug = "modal-nav-rail", knobs = listOf(modalSide)) {
    var open by remember { mutableStateOf(false) }
    var here by remember { mutableStateOf("map") }
    var groupOpen by remember { mutableStateOf(true) }
    Frame(height = 400.dp) {
        PageBehind()
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Button(onClick = { open = true }, variant = ButtonVariant.Secondary) { +"Open the rail" }
        }
        ModalNavRail(
            visible = open,
            onDismissRequest = { open = false },
            side = this@ComponentDemo[modalSide],
        ) {
            tree(here, { here = it }, groupOpen, { groupOpen = it })
        }
    }
}

internal val NavScopeDemo = ComponentDemo(slug = "nav-scope") {
    var here by remember { mutableStateOf("map") }
    var groupOpen by remember { mutableStateOf(false) }
    // One set of destinations, drawn by a bar and by a rail. The group is a menu
    // in the bar and a disclosure in the wide rail.
    Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.md)) {
        Frame(height = 120.dp) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                NavBar { tree(here, { here = it }, groupOpen, { groupOpen = it }) }
            }
        }
        Frame(height = 320.dp) {
            NavRail(expanded = true) { tree(here, { here = it }, groupOpen, { groupOpen = it }) }
        }
    }
}

private val searchPlacement =
    Knob.Choice("Panel", NavExpandPlacement.entries.toList(), NavExpandPlacement.AboveKeyboard)

internal val NavSearchDemo = ComponentDemo(slug = "nav-search", knobs = listOf(searchPlacement)) {
    var here by remember { mutableStateOf("map") }
    var railOpen by remember { mutableStateOf(false) }
    val search = rememberNavSearchState()
    val placement = this[searchPlacement]
    // The same search in a bar, where it is a pill, and in a rail, where it is a
    // pill until the rail is wide enough to type in and then the field itself.
    Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.md)) {
        Frame(height = 140.dp) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                NavBar {
                    destinations(here, { here = it }, count = 2)
                    slot(weight = 1f) {
                        NavSearch(state = search, placement = placement, searchIcon = Tabler.Outline.Search)
                    }
                }
            }
        }
        Frame(height = 300.dp) {
            NavRail(
                expanded = railOpen,
                onExpandedChange = { railOpen = it },
                header = { NavSearch(state = search, placement = placement, searchIcon = Tabler.Outline.Search) },
            ) {
                destinations(here, { here = it })
            }
        }
    }
}

/**
 * Which surface the scaffold shows. The scaffold's whole job is picking one from
 * the window's width, and this overrides that — `ModalRail` is never picked
 * automatically, and the others are only seen here without a tablet.
 */
private val suiteType =
    Knob.Choice("Surface", NavigationSuiteType.entries.toList(), NavigationSuiteType.Bar)
private val suiteBarStyle = Knob.Choice("Bar style", NavBarStyle.entries.toList(), NavBarStyle.Floating)
private val suiteExpandable = Knob.Flag("Rail expands", initial = true)

internal val NavigationSuiteScaffoldDemo = ComponentDemo(
    slug = "navigation-suite-scaffold",
    knobs = listOf(suiteType, suiteBarStyle, suiteExpandable),
) {
    val type = this[suiteType]
    var here by remember { mutableStateOf("map") }
    val suite = rememberNavigationSuiteState()
    // Its own `WindowSizeClassProvider`, so the scaffold reads the size of this
    // box rather than of the reader's browser.
    Frame(height = 320.dp) {
        WindowSizeClassProvider(Modifier.fillMaxSize()) {
            NavigationSuiteScaffold(
                destinations = { destinations(here, { here = it }) },
                state = suite,
                type = type,
                barStyle = this@ComponentDemo[suiteBarStyle],
                railExpandable = this@ComponentDemo[suiteExpandable],
            ) { contentPadding ->
                Box(
                    Modifier.fillMaxSize().padding(contentPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    if (type == NavigationSuiteType.ModalRail) {
                        Button(onClick = suite::showModal, variant = ButtonVariant.Secondary) { +"Destinations" }
                    } else {
                        Text("content", style = Theme.typography.eyebrow, colour = Theme.colours.contentSubtle)
                    }
                }
            }
        }
    }
}

private val topBarStyle = Knob.Choice("Style", TopBarStyle.entries.toList(), TopBarStyle.Small)
private val topBarBack = Knob.Flag("Back", initial = true)

internal val TopBarDemo = ComponentDemo(
    slug = "top-bar",
    knobs = listOf(topBarStyle, topBarBack),
) {
    val style = this[topBarStyle]
    val back = this[topBarBack]
    TopBar(
        style = style,
        onBack = if (back) ({ echo("Back") }) else null,
        showDivider = true,
        actions = { OverflowMenu(::echo) },
    ) {
        +"Perth Underground"
        supporting { +"Platform 2 · Joondalup line" }
    }
}

/**
 * The overflow button both bars carry, and what it opens.
 *
 * A button labelled "More" that echoes the word "More" demonstrates nothing —
 * the point of an overflow slot is the menu behind it, so this is the library's
 * own [DropdownMenu] with rows a transit app would really put there.
 */
@Composable
private fun OverflowMenu(echo: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(
            icon = Tabler.Outline.DotsVertical,
            contentDescription = "More",
            onClick = { open = !open },
            variant = ButtonVariant.Ghost,
            size = ButtonSize.Small,
        )
        DropdownMenu(
            visible = open,
            onDismissRequest = { open = false },
            // Opened from the trailing edge, so it hangs back into the bar
            // rather than off the side of it.
            alignment = OverlayAlignment.End,
        ) {
            // The rows dismiss the menu themselves — see `MenuScopeImpl.item`.
            item("Pin this stop", leadingIcon = Tabler.Outline.Pin) { echo("Pin this stop") }
            item("Alert settings", leadingIcon = Tabler.Outline.Bell) { echo("Alert settings") }
            divider()
            item("Remove stop", leadingIcon = Tabler.Outline.Trash, destructive = true) {
                echo("Remove stop")
            }
        }
    }
}

/**
 * The rule under the bar, which now defaults to off.
 *
 * A pill *and* a rule is the sliding-underline bar wearing a different hat, so
 * the default flipped this round — and the demo never passed the parameter, so
 * the arrangement it flipped away from became unreachable from the site.
 */
private val tabDivider = Knob.Flag("Divider")

/**
 * Tabs that run past the edge and scroll, rather than sharing the width.
 *
 * The two are different components wearing one name: a fixed bar divides what
 * it has between however many tabs there are, and a scrolling one lets each tab
 * be as wide as its label and takes the row off the edge. Six tabs is where the
 * difference stops being theoretical.
 */
private val tabScrollable = Knob.Flag("Scrollable")

internal val TabBarDemo = ComponentDemo(
    slug = "tab-bar",
    knobs = listOf(tabDivider, tabScrollable),
) {
    var tab by remember { mutableStateOf(1) }
    var narrow by remember { mutableStateOf(0) }
    val divider = this[tabDivider]
    val scrollable = this[tabScrollable]

    Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.lg)) {
        TabBar(
            modifier = Modifier.fillMaxWidth(),
            showDivider = divider,
            scrollable = scrollable,
            actions = { OverflowMenu(::echo) },
        ) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, key = 0) { +"Departures" }
            Tab(selected = tab == 1, onClick = { tab = 1 }, key = 1) { +"Route map" }
            Tab(selected = tab == 2, onClick = { tab = 2 }, key = 2, badge = 2) { +"Alerts" }
            if (scrollable) {
                Tab(selected = tab == 3, onClick = { tab = 3 }, key = 3) { +"Timetable" }
                Tab(selected = tab == 4, onClick = { tab = 4 }, key = 4) { +"Accessibility" }
                Tab(selected = tab == 5, onClick = { tab = 5 }, key = 5) { +"Fares and passes" }
            }
        }

        // Deliberately too narrow for its labels.
        //
        // A tab's horizontal padding yields rather than pushing the label out of
        // the bar, so a squeezed tab keeps its word and its marker. This is the
        // only place that is visible: at any comfortable width the padding is
        // simply there and nothing is being demonstrated.
        Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs)) {
            Text(
                "Squeezed to 220dp",
                style = Theme.typography.labelSmall,
                colour = Theme.colours.contentMuted,
            )
            TabBar(modifier = Modifier.width(220.dp), showDivider = divider) {
                Tab(selected = narrow == 0, onClick = { narrow = 0 }, key = 0) { +"Departures" }
                Tab(selected = narrow == 1, onClick = { narrow = 1 }, key = 1) { +"Route map" }
                Tab(selected = narrow == 2, onClick = { narrow = 2 }, key = 2) { +"Alerts" }
            }
        }
    }
}

internal val BreadcrumbsDemo = ComponentDemo(slug = "breadcrumbs") {
    // The last crumb has no `onClick`, which is what makes it the current page
    // rather than a link back to itself.
    Breadcrumbs(
        listOf(
            Crumb("Routes", onClick = { echo("Routes") }),
            Crumb("Route 950", onClick = { echo("Route 950") }),
            Crumb("Stops"),
        ),
    )
}

/**
 * Whether the elision is a button.
 *
 * Off, the run of numbers just has a gap in it. On, the gap is a control that
 * opens a field to type a page number into — which is the answer to "page 3 of
 * 40, and I want page 27". It is off by default and the demo never passed it,
 * so the popover existed and could not be reached.
 */
private val paginationJump = Knob.Flag("Jump")

internal val PaginationDemo = ComponentDemo(slug = "pagination", knobs = listOf(paginationJump)) {
    var page by remember { mutableStateOf(19) }
    val allowJump = this[paginationJump]
    Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.md)) {
        Pagination(
            value = page,
            pageCount = 40,
            onValueChange = { page = it },
            allowJump = allowJump,
        )
        Text(
            "Page ${page + 1} of 40 — the run of numbers collapses differently " +
                "at each end.",
            style = Theme.typography.bodySmall,
            colour = Theme.colours.contentMuted,
        )
    }
}

internal val navigationDemos = listOf(
    NavScopeDemo,
    NavBarDemo,
    NavRailDemo,
    ModalNavRailDemo,
    NavSearchDemo,
    NavigationSuiteScaffoldDemo,
    TopBarDemo,
    TabBarDemo,
    BreadcrumbsDemo,
    PaginationDemo,
)
