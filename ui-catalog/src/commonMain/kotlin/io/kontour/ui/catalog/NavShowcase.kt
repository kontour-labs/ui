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
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Calendar
import com.composables.icons.tabler.outline.DotsVertical
import com.composables.icons.tabler.outline.Home
import com.composables.icons.tabler.outline.Map
import com.composables.icons.tabler.outline.Search
import io.kontour.ui.adaptive.WindowSizeClassProvider
import io.kontour.ui.components.action.ButtonSize
import io.kontour.ui.components.action.ButtonVariant
import io.kontour.ui.components.action.FabSize
import io.kontour.ui.components.action.FloatingActionButton
import io.kontour.ui.components.action.IconButton
import io.kontour.ui.components.text.SearchField
import io.kontour.ui.foundation.Surface
import io.kontour.ui.foundation.Text
import io.kontour.ui.nav.Breadcrumbs
import io.kontour.ui.nav.Crumb
import io.kontour.ui.nav.NavBar
import io.kontour.ui.nav.NavItem
import io.kontour.ui.nav.NavRail
import io.kontour.ui.nav.NavigationSuiteScaffold
import io.kontour.ui.nav.Pagination
import io.kontour.ui.nav.Tab
import io.kontour.ui.nav.TabBar
import io.kontour.ui.nav.TopBar
import io.kontour.ui.nav.TopBarStyle
import io.kontour.ui.theme.Theme

@Composable
private fun destinations(selected: Int, onSelectedChange: (Int) -> Unit) = listOf(
    NavItem("Home", Tabler.Outline.Home, { onSelectedChange(0) }),
    NavItem("Map", Tabler.Outline.Map, { onSelectedChange(1) }),
    NavItem("Plan", Tabler.Outline.Calendar, { onSelectedChange(2) }, badge = 2),
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

            Section("Bar — circles over the content, and nothing behind them") {
                Row(horizontalArrangement = Arrangement.spacedBy(Theme.spacing.md)) {
                    BarPanel("As it comes")
                    BarPanel("With a search field", search = true)
                    BarPanel("Named, for icons that are not obvious", showLabels = true)
                    BarPanel("Backdrop, for content this busy", backdrop = true, busy = true)
                }
            }

            Section("Rail — collapsed and expanded") {
                Row(horizontalArrangement = Arrangement.spacedBy(Theme.spacing.lg)) {
                    RailPanel(expanded = false)
                    RailPanel(expanded = true)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(Theme.spacing.lg)) {
                Column(
                    modifier = Modifier.width(520.dp),
                    verticalArrangement = Arrangement.spacedBy(Theme.spacing.md),
                ) {
                    Section("Top bars — a title, not destinations") {
                        Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs)) {
                            TopBar() {
                                +"Favourites"
                            }
                            TopBar(
                                onBack = tap("Back"),
                                showDivider = true,
                            ) {
                                +"Perth Underground"
                                supporting { +"Platform 2 · Joondalup line" }
                            }
                            TopBar(
                                style = TopBarStyle.Large,
                                onBack = tap("Back"),
                                collapseProgress = 0f,
                            ) {
                                +"Route 950"
                            }
                            TopBar(
                                style = TopBarStyle.Large,
                                onBack = tap("Back"),
                                // Half collapsed: the small title is fading in
                                // exactly as the large one leaves.
                                collapseProgress = 0.5f,
                            ) {
                                +"Route 950"
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier.width(520.dp),
                    verticalArrangement = Arrangement.spacedBy(Theme.spacing.md),
                ) {
                    Section("Tabs — within one screen") {
                        var tab by remember { mutableStateOf(1) }
                        TabBar(
                            actions = {
                                IconButton(
                                    icon = Tabler.Outline.DotsVertical,
                                    contentDescription = "More",
                                    onClick = tap("More"),
                                    variant = ButtonVariant.Ghost,
                                    size = ButtonSize.Small,
                                )
                            },
                        ) {
                            Tab(selected = tab == 0, onClick = { tab = 0 }, key = 0) {
                                +"Departures"
                            }
                            Tab(selected = tab == 1, onClick = { tab = 1 }, key = 1) {
                                +"Route map"
                            }
                            Tab(selected = tab == 2, onClick = { tab = 2 }, key = 2, badge = 2) {
                                +"Alerts"
                            }
                        }
                    }

                    Section("Breadcrumbs") {
                        Breadcrumbs(
                            listOf(
                                Crumb("Routes", onClick = tap("Routes")),
                                Crumb("Route 950", onClick = tap("Route 950")),
                                Crumb("Stops"),
                            )
                        )
                    }

                    Section("Pagination") {
                        Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs)) {
                            // Seeded at the three positions the collapsing
                            // logic branches on — first, middle, and a count
                            // small enough not to collapse at all — and each one
                            // pages for real from there.
                            val first = seed(0)
                            val middle = seed(19)
                            val short = seed(2)
                            Pagination(
                                value = first.value,
                                pageCount = 40,
                                onValueChange = { first.value = it },
                            )
                            Pagination(
                                value = middle.value,
                                pageCount = 40,
                                onValueChange = { middle.value = it },
                            )
                            Pagination(
                                value = short.value,
                                pageCount = 5,
                                onValueChange = { short.value = it },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A window of a given size, with the suite deciding where navigation goes. */

/**
 * A rail at both widths, side by side.
 *
 * The pair is the point: the marker is a bar on the leading edge either way, so
 * expanding changes how much you can read, not how you tell where you are.
 */

/**
 * The bar at phone width, over a stand-in for content.
 *
 * 360dp because that is where it is actually under pressure — it looks fine with
 * 700dp to spread into, and the question is whether it survives a small phone
 * with a search field and an action both wanting room.
 *
 * @param busy A stand-in for a photo or a promotional banner: the case
 *   [NavBar]'s backdrop exists for, and the only one where elevation alone is
 *   not enough to separate the circles from what is under them.
 */
@Composable
private fun BarPanel(
    label: String,
    showLabels: Boolean = false,
    search: Boolean = false,
    backdrop: Boolean = false,
    busy: Boolean = false,
) {
    var selected by remember { mutableStateOf(1) }

    Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs)) {
        Text(
            text = label,
            style = Theme.typography.labelSmall,
            color = Theme.colors.contentMuted,
        )
        Box(
            Modifier
                .width(360.dp)
                .height(170.dp)
                .clip(Theme.shapes.medium)
                .then(
                    if (busy) {
                        Modifier.background(
                            Brush.linearGradient(
                                listOf(
                                    Theme.colors.accent.solid,
                                    Theme.colors.warning.solid,
                                    Theme.colors.accent.container,
                                ),
                            )
                        )
                    } else {
                        Modifier.background(Theme.colors.surfaceSunken)
                    }
                ),
            contentAlignment = Alignment.BottomCenter,
        ) {
            NavBar(
                items = destinations(selected) { selected = it },
                selectedIndex = selected,
                showLabels = showLabels,
                backdrop = backdrop,
                search = if (search) {
                    {
                        SearchField(
                            state = rememberTextFieldState(),
                            placeholder = "Search",
                            // A pill inside a pill. At `shapes.small` the field's
                            // square-ish corners fight the bar's curve and clip
                            // against its rounded end.
                            shape = Theme.shapes.pill,
                        )
                    }
                } else {
                    null
                },
                // Deliberately dropped when there is a search field: the
                // reference has no separate action button, because the search
                // *is* the action. Keeping both is what made the first render of
                // this panel run out of width at 360dp.
                action = if (search) {
                    null
                } else {
                    {
                        FloatingActionButton(
                            icon = Tabler.Outline.Search,
                            contentDescription = "Search",
                            onClick = tap("Search"),
                            size = FabSize.Small,
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun RailPanel(expanded: Boolean) {
    var selected by remember { mutableStateOf(1) }
    // The pair of panels shows both widths at once, and each one can be expanded
    // and collapsed from its own chevron — which is the control the rail exists
    // to have.
    var wide by remember { mutableStateOf(expanded) }

    Box(
        Modifier
            .height(340.dp)
            .border(
                width = Theme.sizing.borderWidth,
                color = Theme.colors.outline,
                shape = Theme.shapes.medium,
            )
            .clip(Theme.shapes.medium)
    ) {
        NavRail(
            items = destinations(selected) { selected = it },
            selectedIndex = selected,
            expanded = wide,
            onExpandedChange = { wide = it },
            action = {
                FloatingActionButton(
                    icon = Tabler.Outline.Search,
                    contentDescription = "Search",
                    onClick = tap("Search"),
                )
            },
        )
    }
}

@Composable
private fun DevicePanel(title: String, width: Dp, height: Dp) {
    Column(
        modifier = Modifier.width(width),
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
    ) {
        Text(
            text = title.uppercase(),
            style = Theme.typography.monoLabel,
            color = Theme.colors.accent.solid,
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
                            onClick = tap("Search"),
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
                                containerColor = Theme.colors.surfaceSunken,
                            ) {
                                +"Map"
                            }
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
