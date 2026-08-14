package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.composables.icons.tabler.outline.Calendar
import com.composables.icons.tabler.outline.Home
import com.composables.icons.tabler.outline.Map
import com.composables.icons.tabler.outline.Search
import io.kontour.ui.adaptive.WindowSizeClassProvider
import io.kontour.ui.components.action.FloatingActionButton
import io.kontour.ui.foundation.Surface
import io.kontour.ui.foundation.Text
import io.kontour.ui.nav.Breadcrumbs
import io.kontour.ui.nav.Crumb
import io.kontour.ui.nav.NavItem
import io.kontour.ui.nav.NavigationSuiteScaffold
import io.kontour.ui.nav.Pagination
import io.kontour.ui.nav.Tab
import io.kontour.ui.nav.TabBar
import io.kontour.ui.nav.TopBar
import io.kontour.ui.nav.TopBarStyle
import io.kontour.ui.theme.Theme

@Composable
private fun destinations(selected: Int, onSelect: (Int) -> Unit) = listOf(
    NavItem("Home", Tabler.Outline.Home, { onSelect(0) }),
    NavItem("Map", Tabler.Outline.Map, { onSelect(1) }),
    NavItem("Plan", Tabler.Outline.Calendar, { onSelect(2) }, badge = 2),
)

/**
 * The navigation suite at three window sizes, plus the bars that are not
 * navigation.
 *
 * The point of the top row is the placement rule: destinations go at the
 * *bottom* on a phone and on the *leading edge* once there is room beside the
 * content. Each panel provides its own window size class, so the scaffold picks
 * as it would on a real device of that width.
 */
@Composable
fun NavShowcase(modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = Theme.colors.background) {
        Column(
            modifier = Modifier.padding(Theme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.lg),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Theme.spacing.lg)) {
                DevicePanel("Compact — bar at the bottom", width = 360.dp, height = 620.dp)
                DevicePanel("Medium — rail on the leading edge", width = 700.dp, height = 620.dp)
                DevicePanel("Expanded — drawer", width = 900.dp, height = 620.dp)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(Theme.spacing.lg)) {
                Column(
                    modifier = Modifier.width(520.dp),
                    verticalArrangement = Arrangement.spacedBy(Theme.spacing.md),
                ) {
                    Section("Top bars — a title, not destinations") {
                        Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs)) {
                            TopBar(title = "Favourites")
                            TopBar(
                                title = "Perth Underground",
                                subtitle = "Platform 2 · Joondalup line",
                                onBack = {},
                                showDivider = true,
                            )
                            TopBar(
                                title = "Route 950",
                                style = TopBarStyle.Large,
                                onBack = {},
                                collapseProgress = 0f,
                            )
                            TopBar(
                                title = "Route 950",
                                style = TopBarStyle.Large,
                                onBack = {},
                                // Half collapsed: the small title is fading in
                                // exactly as the large one leaves.
                                collapseProgress = 0.5f,
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier.width(520.dp),
                    verticalArrangement = Arrangement.spacedBy(Theme.spacing.md),
                ) {
                    Section("Tabs — within one screen") {
                        var tab by remember { mutableStateOf(1) }
                        TabBar(selectedIndex = tab) {
                            Tab("Departures", selected = tab == 0, onClick = { tab = 0 })
                            Tab("Route map", selected = tab == 1, onClick = { tab = 1 })
                            Tab("Alerts", selected = tab == 2, onClick = { tab = 2 }, badge = 2)
                        }
                    }

                    Section("Breadcrumbs") {
                        Breadcrumbs(
                            listOf(
                                Crumb("Routes", onClick = {}),
                                Crumb("Route 950", onClick = {}),
                                Crumb("Stops"),
                            )
                        )
                    }

                    Section("Pagination") {
                        Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs)) {
                            Pagination(page = 0, pageCount = 40, onPageChange = {})
                            Pagination(page = 19, pageCount = 40, onPageChange = {})
                            Pagination(page = 2, pageCount = 5, onPageChange = {})
                        }
                    }
                }
            }
        }
    }
}

/** A window of a given size, with the suite deciding where navigation goes. */
@Composable
private fun DevicePanel(title: String, width: Dp, height: Dp) {
    Column(
        modifier = Modifier.width(width),
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
    ) {
        Text(
            text = title.uppercase(),
            style = Theme.typography.monoLabel,
            color = Theme.colors.accent,
        )
        Box(
            Modifier
                .width(width)
                .height(height)
                .border(
                    width = Theme.sizing.borderWidth,
                    color = Theme.colors.outline,
                    shape = Theme.shapes.medium,
                )
                .clip(Theme.shapes.medium)
        ) {
            // Its own size class, so the scaffold picks as it would on a device
            // of this width rather than inheriting the catalog's window.
            WindowSizeClassProvider(Modifier.fillMaxSize()) {
                var selected by remember { mutableStateOf(1) }
                NavigationSuiteScaffold(
                    items = destinations(selected) { selected = it },
                    selectedIndex = selected,
                    action = {
                        FloatingActionButton(
                            icon = Tabler.Outline.Search,
                            contentDescription = "Search",
                            onClick = {},
                        )
                    },
                ) { contentPadding ->
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Theme.colors.surfaceSunken)
                            .padding(bottom = contentPadding),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Theme.spacing.md),
                            verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
                        ) {
                            TopBar(
                                title = "Map",
                                actions = {},
                                containerColor = Theme.colors.surfaceSunken,
                            )
                            Text(
                                "content",
                                style = Theme.typography.monoLabel,
                                color = Theme.colors.contentSubtle,
                            )
                        }
                    }
                }
            }
        }
    }
}
