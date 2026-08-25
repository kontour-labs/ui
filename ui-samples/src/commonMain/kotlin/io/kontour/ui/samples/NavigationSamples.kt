package io.kontour.ui.samples

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Calendar
import com.composables.icons.tabler.outline.Home
import com.composables.icons.tabler.outline.Map
import com.composables.icons.tabler.outline.User
import com.composables.icons.tabler.outline.Star
import io.kontour.ui.nav.NavItem
import io.kontour.ui.nav.NavBar
import io.kontour.ui.nav.NavRail
import io.kontour.ui.nav.NavSearch
import io.kontour.ui.nav.rememberNavSearchState
import io.kontour.ui.nav.Tab
import io.kontour.ui.nav.TabBar
import io.kontour.ui.nav.TopBar
import io.kontour.ui.nav.TopBarStyle
import io.kontour.ui.nav.collapseProgress
import com.composables.icons.tabler.outline.Bell
import io.kontour.ui.nav.Breadcrumbs
import io.kontour.ui.nav.Crumb
import io.kontour.ui.nav.ModalNavDrawer
import io.kontour.ui.nav.NavBar
import io.kontour.ui.nav.NavigationSuiteScaffold
import io.kontour.ui.nav.Pagination

@Composable
fun NavRailExpanding() {
    var current by remember { mutableStateOf(0) }
    var railOpen by remember { mutableStateOf(false) }

    val destinations = listOf(
        NavItem(label = "Plan", icon = Tabler.Outline.Map, onClick = { current = 0 }),
        NavItem(label = "Saved", icon = Tabler.Outline.Star, onClick = { current = 1 }),
    )

    NavRail(
        items = destinations,
        selectedIndex = current,
        expanded = railOpen,
        onExpandedChange = { railOpen = it },
    )
}

@Composable
fun TopBarCollapsing() {
    val listState = rememberLazyListState()

    // The bar does not own the scroll state, so the caller hands it the
    // progress. `collapseProgress` computes it from a `LazyListState`.
    TopBar(
        style = TopBarStyle.Large,
        collapseProgress = collapseProgress(listState),
    ) {
        +"Perth Underground"
        supporting { +"Platform 2" }
    }

    LazyColumn(state = listState) {
        stopRows()
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
fun NavBarCentreSearch() {
    var current by remember { mutableStateOf(0) }
    val search = rememberNavSearchState()

    val destinations = listOf(
        NavItem(label = "Home", icon = Tabler.Outline.Home, onClick = { current = 0 }),
        NavItem(label = "Map", icon = Tabler.Outline.Map, onClick = { current = 1 }),
        NavItem(label = "Plan", icon = Tabler.Outline.Calendar, onClick = { current = 2 }),
        NavItem(label = "Profile", icon = Tabler.Outline.User, onClick = { current = 3 }),
    )

    NavBar(
        items = destinations,
        selectedIndex = current,
        search = { NavSearch(state = search) },
        searchIndex = destinations.size / 2,
    )
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

@Composable
fun NavItemBasics() {
    var selected by remember { mutableStateOf(0) }

    // One list, described once. `NavBar`, `NavRail`, `NavDrawer` and
    // `NavigationSuiteScaffold` all take it, so changing surface at a
    // breakpoint is not a second copy of the destinations.
    val items = remember {
        listOf(
            NavItem("Home", Tabler.Outline.Home, onClick = { selected = 0 }),
            NavItem("Saved", Tabler.Outline.Star, onClick = { selected = 1 }),
            NavItem("Alerts", Tabler.Outline.Bell, badge = 3, onClick = { selected = 2 }),
        )
    }

    NavBar(items = items, selectedIndex = selected)
}

@Composable
fun NavigationSuiteScaffoldBasics() {
    var selected by remember { mutableStateOf(0) }
    val items = remember {
        listOf(
            NavItem("Home", Tabler.Outline.Home, onClick = { selected = 0 }),
            NavItem("Saved", Tabler.Outline.Star, onClick = { selected = 1 }),
            NavItem("Alerts", Tabler.Outline.Bell, badge = 3, onClick = { selected = 2 }),
        )
    }

    // A bar on a phone, a rail on a tablet, a drawer on a desktop — chosen from
    // the window size class, so the screen below says nothing about which.
    NavigationSuiteScaffold(items = items, selectedIndex = selected) { contentPadding ->
        Screen()
    }
}

@Composable
fun NavDrawerBasics() {
    var open by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf("Home") }
    var routesOpen by remember { mutableStateOf(true) }

    // The same `NavDrawerScope` content the permanent `NavDrawer` takes, over
    // the screen instead of beside it — a tree rather than a flat list, because
    // a drawer is where destinations stop being three of them.
    ModalNavDrawer(visible = open, onDismissRequest = { open = false }) {
        item("Home", icon = Tabler.Outline.Home, selected = selected == "Home") {
            selected = "Home"
            open = false
        }
        section("Saved") {
            item("Stops", selected = selected == "Stops") { selected = "Stops"; open = false }
            group("Routes", expanded = routesOpen, onExpandedChange = { routesOpen = it }) {
                item("950", selected = selected == "950") { selected = "950"; open = false }
                item("998", selected = selected == "998") { selected = "998"; open = false }
            }
        }
        divider()
        item("Alerts", icon = Tabler.Outline.Bell, badge = 3, selected = selected == "Alerts") {
            selected = "Alerts"
            open = false
        }
    }
}
