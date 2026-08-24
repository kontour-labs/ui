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
