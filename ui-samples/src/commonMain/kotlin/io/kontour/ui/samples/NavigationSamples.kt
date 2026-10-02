package io.kontour.ui.samples

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Basket
import com.composables.icons.tabler.outline.Bell
import com.composables.icons.tabler.outline.Calendar
import com.composables.icons.tabler.outline.Car
import com.composables.icons.tabler.outline.Filter
import com.composables.icons.tabler.outline.Home
import com.composables.icons.tabler.outline.Map
import com.composables.icons.tabler.outline.Plus
import com.composables.icons.tabler.outline.Route
import com.composables.icons.tabler.outline.Search
import com.composables.icons.tabler.outline.Star
import com.composables.icons.tabler.outline.User
import io.kontour.ui.components.action.FloatingActionButton
import io.kontour.ui.components.action.IconButton
import io.kontour.ui.components.display.Badge
import io.kontour.ui.foundation.Text
import io.kontour.ui.nav.Breadcrumbs
import io.kontour.ui.nav.Crumb
import io.kontour.ui.nav.HeroTopBar
import io.kontour.ui.nav.ModalNavRail
import io.kontour.ui.nav.NavBar
import io.kontour.ui.nav.NavBarDefaults
import io.kontour.ui.nav.NavBarItemWidth
import io.kontour.ui.nav.NavBarStyle
import io.kontour.ui.nav.NavExpandingSlot
import io.kontour.ui.nav.NavRail
import io.kontour.ui.nav.NavSearch
import io.kontour.ui.nav.NavigationSuiteScaffold
import io.kontour.ui.nav.NavigationSuiteType
import io.kontour.ui.nav.Pagination
import io.kontour.ui.nav.Tab
import io.kontour.ui.nav.TabBar
import io.kontour.ui.nav.TopBar
import io.kontour.ui.nav.TopBarStyle
import io.kontour.ui.nav.rememberTopBarCollapseState
import io.kontour.ui.nav.rememberNavSearchState
import io.kontour.ui.nav.rememberNavigationSuiteState

@Composable
fun TopBarCollapsing() {
    val collapse = rememberTopBarCollapseState()

    // The bar collapses as the list scrolls: its connection goes on something
    // that holds both, so the bar takes the scroll until it has collapsed and
    // the rows move with the finger the whole way.
    Column(Modifier.nestedScroll(collapse.nestedScrollConnection)) {
        TopBar(
            style = TopBarStyle.Large,
            onBack = { back() },
            collapseState = collapse,
        ) {
            +"Perth Underground"
            supporting { +"Platform 2" }
        }
        LazyColumn {
            stopRows()
        }
    }
}

@Composable
fun HeroTopBarCollapsing() {
    val collapse = rememberTopBarCollapseState()

    Column(Modifier.nestedScroll(collapse.nestedScrollConnection)) {
        // The picture runs under the status bar; the title stands on the
        // bar's colour, faded in under it, and collapses as a Large bar does.
        HeroTopBar(
            image = { StopPhoto(Modifier.fillMaxSize()) },
            onBack = { back() },
            collapseState = collapse,
        ) {
            +"Kings Park"
            supporting { +"Fraser Avenue" }
        }
        LazyColumn {
            stopRows()
        }
    }
}

@Composable
fun TabBarBasics() {
    var selected by remember { mutableStateOf("departures") }

    TabBar {
        Tab(
            selected = selected == "departures",
            onClick = { selected = "departures" },
            key = "departures",
        ) {
            +"Departures"
        }
        Tab(
            selected = selected == "arrivals",
            onClick = { selected = "arrivals" },
            key = "arrivals",
        ) {
            +"Arrivals"
        }
    }
}

@Composable
fun NavScopeBasics() {
    var here by remember { mutableStateOf("home") }

    // Written once, drawn by whichever surface it is in. Each destination says
    // whether it is selected, has a key of its own, and a label a screen reader
    // hears wherever only its icon is drawn.
    NavBar {
        item(here == "home", { here = "home" }, key = "home", icon = Tabler.Outline.Home) { +"Home" }
        item(here == "saved", { here = "saved" }, key = "saved", icon = Tabler.Outline.Star) { +"Saved" }
        item(
            selected = here == "alerts",
            onClick = { here = "alerts" },
            key = "alerts",
            icon = Tabler.Outline.Bell,
            badge = { Badge(count = 3) },
        ) {
            +"Alerts"
        }
    }
}

@Composable
fun NavBarBasics() {
    var here by remember { mutableStateOf("map") }

    // Docked to the bottom edge rather than floating over the page, with a word
    // under each icon for an app whose icons are not obvious.
    NavBar(style = NavBarStyle.Docked, showLabels = true) {
        item(here == "map", { here = "map" }, key = "map", icon = Tabler.Outline.Map) { +"Map" }
        item(here == "plan", { here = "plan" }, key = "plan", icon = Tabler.Outline.Calendar) { +"Plan" }
        item(here == "me", { here = "me" }, key = "me", icon = Tabler.Outline.User) { +"Me" }
    }
}

@Composable
fun NavBarCentreSearch() {
    var here by remember { mutableStateOf("home") }
    val search = rememberNavSearchState()

    // The search in the middle, two destinations either side: the thing people
    // came to do between the places they might go.
    NavBar {
        item(here == "home", { here = "home" }, key = "home", icon = Tabler.Outline.Home) { +"Home" }
        item(here == "map", { here = "map" }, key = "map", icon = Tabler.Outline.Map) { +"Map" }
        slot(weight = 1f) { NavSearch(state = search, searchIcon = Tabler.Outline.Search) }
        item(here == "plan", { here = "plan" }, key = "plan", icon = Tabler.Outline.Calendar) { +"Plan" }
        item(here == "me", { here = "me" }, key = "me", icon = Tabler.Outline.User) { +"Me" }
    }
}

@Composable
fun NavBarAction() {
    var here by remember { mutableStateOf("map") }

    // Beside the capsule, not in it: a floating action button has a surface of
    // its own, and on a phone the capsule narrows to make room for it.
    NavBar(
        action = {
            FloatingActionButton(onClick = { newTrip() }, icon = Tabler.Outline.Plus, contentDescription = "New trip")
        },
    ) {
        item(here == "map", { here = "map" }, key = "map", icon = Tabler.Outline.Map) { +"Map" }
        item(here == "plan", { here = "plan" }, key = "plan", icon = Tabler.Outline.Calendar) { +"Plan" }
        item(here == "me", { here = "me" }, key = "me", icon = Tabler.Outline.User) { +"Me" }
    }
}

@Composable
fun NavBarCircles() {
    var here by remember { mutableStateOf("drive") }

    // Circles rather than cells: each destination as wide as it needs, spread
    // across a phone and gathered in the middle of anything wider, and the
    // marker a circle behind the glyph.
    NavBar(
        itemWidth = NavBarItemWidth.Natural,
        marker = NavBarDefaults.glyphMarker(),
    ) {
        item(here == "drive", { here = "drive" }, key = "drive", icon = Tabler.Outline.Car) { +"Drive" }
        item(here == "shop", { here = "shop" }, key = "shop", icon = Tabler.Outline.Basket) { +"Shop" }
        item(here == "you", { here = "you" }, key = "you", icon = Tabler.Outline.User) { +"You" }
    }
}

@Composable
fun NavRailExpanding() {
    var here by remember { mutableStateOf("plan") }
    var railOpen by remember { mutableStateOf(false) }

    // A column of icons that grows into a sidebar. The icons stay where they
    // are; the labels are revealed beside them.
    NavRail(expanded = railOpen, onExpandedChange = { railOpen = it }) {
        item(here == "plan", { here = "plan" }, key = "plan", icon = Tabler.Outline.Map) { +"Plan" }
        item(here == "saved", { here = "saved" }, key = "saved", icon = Tabler.Outline.Star) { +"Saved" }
    }
}

@Composable
fun NavRailTree() {
    var here by remember { mutableStateOf("home") }
    var routesOpen by remember { mutableStateOf(true) }

    // A sidebar — a rail fixed at its widest — with a section and a group. The
    // group opens in place here; in a collapsed rail it opens a menu instead.
    NavRail(expanded = true) {
        item(here == "home", { here = "home" }, key = "home", icon = Tabler.Outline.Home) { +"Home" }
        section("Saved") {
            item(here == "stops", { here = "stops" }, key = "stops", icon = Tabler.Outline.Star) { +"Stops" }
            group(
                expanded = routesOpen,
                onExpandedChange = { routesOpen = it },
                key = "routes",
                icon = Tabler.Outline.Route,
                header = { +"Routes" },
            ) {
                item(here == "950", { here = "950" }, key = "950", icon = null) { +"950" }
                item(here == "998", { here = "998" }, key = "998", icon = null) { +"998" }
            }
        }
        divider()
        item(here == "alerts", { here = "alerts" }, key = "alerts", icon = Tabler.Outline.Bell) { +"Alerts" }
    }
}

@Composable
fun ModalNavRailBasics() {
    var open by remember { mutableStateOf(false) }
    var here by remember { mutableStateOf("home") }

    IconButton(icon = Tabler.Outline.Home, contentDescription = "Destinations", onClick = { open = true })

    // Over the screen rather than beside it. Choosing a destination closes it, a
    // frame after the page has changed behind it.
    ModalNavRail(visible = open, onDismissRequest = { open = false }) {
        item(here == "home", { here = "home" }, key = "home", icon = Tabler.Outline.Home) { +"Home" }
        item(here == "saved", { here = "saved" }, key = "saved", icon = Tabler.Outline.Star) { +"Saved" }
        item(here == "alerts", { here = "alerts" }, key = "alerts", icon = Tabler.Outline.Bell) { +"Alerts" }
    }
}

@Composable
fun NavigationSuiteScaffoldBasics() {
    var here by remember { mutableStateOf("home") }
    val suite = rememberNavigationSuiteState()

    // A bar on a phone, a rail on a tablet, a sidebar on a desktop — chosen from
    // the window size class, so the screen below says nothing about which.
    NavigationSuiteScaffold(
        destinations = {
            item(here == "home", { here = "home" }, key = "home", icon = Tabler.Outline.Home) { +"Home" }
            item(here == "saved", { here = "saved" }, key = "saved", icon = Tabler.Outline.Star) { +"Saved" }
            item(
                selected = here == "alerts",
                onClick = { here = "alerts" },
                key = "alerts",
                icon = Tabler.Outline.Bell,
                badge = { Badge(count = 3) },
            ) {
                +"Alerts"
            }
        },
        state = suite,
        railExpandable = true,
    ) { contentPadding ->
        Screen()
    }
}

@Composable
fun NavigationSuiteModalRail() {
    var here by remember { mutableStateOf("home") }
    val suite = rememberNavigationSuiteState()

    // No navigation on screen until the app asks for it: a top bar's menu
    // button calls `suite.showModal()`.
    NavigationSuiteScaffold(
        destinations = {
            item(here == "home", { here = "home" }, key = "home", icon = Tabler.Outline.Home) { +"Home" }
            item(here == "saved", { here = "saved" }, key = "saved", icon = Tabler.Outline.Star) { +"Saved" }
        },
        state = suite,
        type = NavigationSuiteType.ModalRail,
    ) {
        IconButton(icon = Tabler.Outline.Home, contentDescription = "Destinations", onClick = suite::showModal)
    }
}

@Composable
fun NavSearchBasics() {
    val search = rememberNavSearchState()

    // A pill in a bar or a narrow rail that opens a field over the screen, and a
    // field in place once there is room for one.
    NavSearch(
        state = search,
        searchIcon = Tabler.Outline.Search,
        onSearch = { nearby() },
    ) {
        Text("Recent: Perth Underground")
    }
}

@Composable
fun NavExpandingSlotBasics() {
    var open by remember { mutableStateOf(false) }

    // The same pill-and-panel with something other than a search in it.
    NavExpandingSlot(
        expanded = open,
        onExpandedChange = { open = it },
        expandedContent = { Text("Filters go here") },
    ) {
        +Tabler.Outline.Filter
        +"Filters"
    }
}

@Composable
fun BreadcrumbsBasics() {
    // The last crumb has no `onClick`, and that is what makes it the current
    // page rather than a link back to itself.
    Breadcrumbs(
        listOf(
            Crumb("Routes", onClick = { nearby() }),
            Crumb("Route 950", onClick = { nearby() }),
            Crumb("Stops"),
        ),
    )
}

@Composable
fun PaginationBasics() {
    var page by remember { mutableStateOf(0) }

    // `window` is how many numbers sit either side of the current one; the run
    // collapses differently at each end so the control never changes width.
    Pagination(value = page, pageCount = 40, onValueChange = { page = it })
}
