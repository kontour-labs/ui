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
import com.composables.icons.tabler.outline.Search
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
import io.kontour.ui.nav.ModalNavDrawer
import io.kontour.ui.nav.NavBar
import io.kontour.ui.nav.NavItem
import io.kontour.ui.nav.NavRail
import io.kontour.ui.nav.NavigationSuiteScaffold
import io.kontour.ui.nav.Pagination
import io.kontour.ui.nav.Tab
import io.kontour.ui.nav.TabBar
import io.kontour.ui.nav.TopBar
import io.kontour.ui.nav.TopBarStyle
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

@Composable
private fun destinations(selected: Int, onSelectedChange: (Int) -> Unit) = listOf(
    NavItem("Home", Tabler.Outline.Home, { onSelectedChange(0) }),
    NavItem("Map", Tabler.Outline.Map, { onSelectedChange(1) }),
    NavItem("Plan", Tabler.Outline.Calendar, { onSelectedChange(2) }, badge = 2),
    NavItem("Profile", Tabler.Outline.User, { onSelectedChange(3) }),
)

private val barLabels = Knob.Flag("Labels", initial = true)

internal val NavSurfacesDemo = ComponentDemo(
    slug = "nav-surfaces",
    knobs = listOf(barLabels),
) {
    var selected by remember { mutableStateOf(1) }
    var expanded by remember { mutableStateOf(false) }
    val labels = this[barLabels]

    Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.md)) {
        Text("NavBar", style = Theme.typography.labelMedium)
        Frame(height = 150.dp) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                NavBar(
                    items = destinations(selected) { selected = it },
                    selectedIndex = selected,
                    showLabels = labels,
                    action = {
                        FloatingActionButton(
                            icon = Tabler.Outline.Search,
                            contentDescription = "Search",
                            onClick = { echo("Search") },
                            size = FabSize.Small,
                        )
                    },
                )
            }
        }
        Text("NavRail", style = Theme.typography.labelMedium)
        Frame(height = 300.dp) {
            NavRail(
                items = destinations(selected) { selected = it },
                selectedIndex = selected,
                expanded = expanded,
                onExpandedChange = { expanded = it },
            )
        }
    }
}

internal val NavItemDemo = ComponentDemo(slug = "nav-item") {
    var selected by remember { mutableStateOf(0) }
    // `NavItem` is a model rather than a component — a label, an icon, a
    // callback and an optional badge — so the demo is the surfaces reading it.
    Frame(height = 150.dp) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            NavBar(
                items = destinations(selected) { selected = it },
                selectedIndex = selected,
            )
        }
    }
}

internal val NavigationSuiteScaffoldDemo = ComponentDemo(slug = "navigation-suite-scaffold") {
    var selected by remember { mutableStateOf(1) }
    // Its own `WindowSizeClassProvider`, so the scaffold picks bar, rail or
    // drawer from the size of *this box* rather than of the reader's browser.
    // That is the whole component: one call, three surfaces.
    Frame(height = 320.dp) {
        WindowSizeClassProvider(Modifier.fillMaxSize()) {
            NavigationSuiteScaffold(
                items = destinations(selected) { selected = it },
                selectedIndex = selected,
            ) { contentPadding ->
                Box(
                    Modifier.fillMaxSize().padding(bottom = contentPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "content",
                        style = Theme.typography.monoLabel,
                        colour = Theme.colours.contentSubtle,
                    )
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
            item("Pin this stop", icon = Tabler.Outline.Pin) { echo("Pin this stop") }
            item("Alert settings", icon = Tabler.Outline.Bell) { echo("Alert settings") }
            divider()
            item("Remove stop", icon = Tabler.Outline.Trash, destructive = true) {
                echo("Remove stop")
            }
        }
    }
}

internal val TabBarDemo = ComponentDemo(slug = "tab-bar") {
    var tab by remember { mutableStateOf(1) }
    TabBar(
        modifier = Modifier.fillMaxWidth(),
        actions = { OverflowMenu(::echo) },
    ) {
        Tab(selected = tab == 0, onClick = { tab = 0 }, key = 0) { +"Departures" }
        Tab(selected = tab == 1, onClick = { tab = 1 }, key = 1) { +"Route map" }
        Tab(selected = tab == 2, onClick = { tab = 2 }, key = 2, badge = 2) { +"Alerts" }
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

internal val PaginationDemo = ComponentDemo(slug = "pagination") {
    var page by remember { mutableStateOf(19) }
    Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.md)) {
        Pagination(value = page, pageCount = 40, onValueChange = { page = it })
        Text(
            "Page ${page + 1} of 40 — the run of numbers collapses differently " +
                "at each end.",
            style = Theme.typography.bodySmall,
            colour = Theme.colours.contentMuted,
        )
    }
}

internal val ModalNavDrawerDemo = ComponentDemo(slug = "nav-drawer") {
    var open by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(1) }
    val pages = listOf("Home", "Map", "Plan", "Profile")
    Frame(height = 300.dp) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Button(onClick = { open = true }, variant = ButtonVariant.Secondary) {
                +"Open the drawer"
            }
        }
        ModalNavDrawer(visible = open, onDismissRequest = { open = false }) {
            section("Travel") {
                pages.take(3).forEachIndexed { index, page ->
                    item(page, selected = index == selected) {
                        selected = index
                        open = false
                    }
                }
            }
            divider()
            item("Profile", selected = selected == 3) { selected = 3; open = false }
        }
    }
}

internal val navigationDemos = listOf(
    NavSurfacesDemo,
    NavItemDemo,
    NavigationSuiteScaffoldDemo,
    TopBarDemo,
    TabBarDemo,
    BreadcrumbsDemo,
    PaginationDemo,
    ModalNavDrawerDemo,
)
