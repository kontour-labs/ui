package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Calendar
import com.composables.icons.tabler.outline.DotsVertical
import com.composables.icons.tabler.outline.Home
import com.composables.icons.tabler.outline.Map
import com.composables.icons.tabler.outline.Search
import com.composables.icons.tabler.outline.User
import io.kontour.ui.adaptive.WindowSizeClassProvider
import io.kontour.ui.components.action.ButtonSize
import io.kontour.ui.components.action.ButtonVariant
import io.kontour.ui.components.action.FabSize
import io.kontour.ui.components.action.FloatingActionButton
import io.kontour.ui.components.action.IconButton
import io.kontour.ui.foundation.Surface
import io.kontour.ui.foundation.Text
import io.kontour.ui.nav.Breadcrumbs
import io.kontour.ui.nav.Crumb
import io.kontour.ui.nav.NavBar
import io.kontour.ui.nav.NavItem
import io.kontour.ui.nav.NavRail
import io.kontour.ui.nav.NavSearch
import io.kontour.ui.nav.NavigationSuiteScaffold
import io.kontour.ui.nav.Pagination
import io.kontour.ui.nav.rememberNavSearchState
import io.kontour.ui.nav.Tab
import io.kontour.ui.nav.TabBar
import io.kontour.ui.nav.TopBar
import io.kontour.ui.nav.TopBarStyle
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.Theme

/** Two either side of a search, which is where Anyways is going. */
@Composable
private fun fourDestinations(selected: Int, onSelectedChange: (Int) -> Unit) = listOf(
    NavItem("Home", Tabler.Outline.Home, { onSelectedChange(0) }),
    NavItem("Map", Tabler.Outline.Map, { onSelectedChange(1) }),
    NavItem("Plan", Tabler.Outline.Calendar, { onSelectedChange(2) }, badge = 2),
    NavItem("Profile", Tabler.Outline.User, { onSelectedChange(3) }),
)

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
            DeviceStrip {
                DevicePanel("Compact — bar at the bottom", width = 360.dp, height = 620.dp)
                // The same phone with a gesture bar under it. Every other golden
                // in this project renders at zero insets — the JVM has no system
                // bars to report — so the bar's `windowInsets` default has been
                // correct and invisible since the day it was written, and "does
                // it respect the safe zone" was not a question any picture here
                // could answer. Passing the inset explicitly is the only way to
                // ask it, and 48dp is the tallest thing Android puts there.
                DevicePanel(
                    "Compact — with a 48dp gesture bar",
                    width = 360.dp,
                    height = 620.dp,
                    safeArea = 48.dp,
                )
                DevicePanel("Medium — rail on the leading edge", width = 700.dp, height = 620.dp)
                DevicePanel("Expanded — drawer", width = 900.dp, height = 620.dp)
            }

            Section("Bar — circles over the content, and nothing behind them") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Theme.spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Theme.spacing.md),
                ) {
                    BarPanel("As it comes")
                    BarPanel("With a search field", search = true)
                    BarPanel("Four destinations around a search", centreSearch = true)
                    BarPanel("Named, for icons that are not obvious", showLabels = true)
                    BarPanel("Backdrop, for content this busy", backdrop = true, busy = true)
                }
            }

            Section("Rail — collapsed and expanded") {
                DeviceStrip {
                    RailPanel(expanded = false)
                    RailPanel(expanded = true)
                }
            }

            Section("Rail — a search that grows with it") {
                DeviceStrip {
                    RailPanel(expanded = false, search = true)
                    RailPanel(expanded = true, search = true)
                }
            }

            // Not a `DeviceStrip`: these two hold ordinary specimens, and their
            // 520dp is a preference rather than the exhibit. Inside a horizontal
            // scroller a child is measured at infinite width, so `Panel`'s
            // `widthIn(max =)` stopped shrinking on a phone — a 1,080dp strip
            // with the section headings themselves cut off mid-word, and
            // pagination four screens to the right of where it looks like it is.
            // `FlowRow` gives back the ceiling: side by side at 520 on a desktop,
            // stacked at 360 on a phone.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Theme.spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Theme.spacing.lg),
            ) {
                Panel(width = 520.dp, spacing = Theme.spacing.md) {
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

                Panel(width = 520.dp, spacing = Theme.spacing.md) {
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
    /**
     * A [NavSearch] between two pairs of destinations — the arrangement Anyways
     * is heading for, and the one `searchIndex` exists to make possible. Tapping
     * the pill expands it over the keyboard; the panel hosts its own overlay so
     * the expansion happens inside this box rather than over the gallery.
     */
    centreSearch: Boolean = false,
    backdrop: Boolean = false,
    busy: Boolean = false,
) {
    var selected by remember { mutableStateOf(1) }
    val navSearch = rememberNavSearchState()

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
            // The panel is its own overlay host, so an expanding search fills
            // *this* box rather than the gallery page it is sitting on. Which is
            // also what makes the specimen honest: on a phone the box is the
            // window, and this is the shape of what the user would see.
            //
            // The inner `Box` is not decoration. `OverlayHost` lays its content
            // out at the top of itself, so wrapping the bar in one silently
            // undid the enclosing box's `BottomCenter` and left every bar
            // specimen floating at the top of its panel — which is not where a
            // navigation bar goes, and is the first thing anyone looking at
            // these pictures noticed.
            OverlayHost(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            NavBar(
                items = if (centreSearch) {
                    fourDestinations(selected) { selected = it }
                } else {
                    destinations(selected) { selected = it }
                },
                selectedIndex = selected,
                showLabels = showLabels,
                backdrop = backdrop,
                searchIndex = if (centreSearch) 2 else null,
                search = if (centreSearch) {
                    {
                        NavSearch(
                            state = navSearch,
                            searchIcon = Tabler.Outline.Search,
                            results = {
                                Text(
                                    "Results go here",
                                    style = Theme.typography.bodyMedium,
                                    color = Theme.colors.contentMuted,
                                )
                            },
                        )
                    }
                } else if (search) {
                    {
                        // The same component, trailing the destinations rather
                        // than between them. It used to be a bare `SearchField`
                        // here, which is `surfaceSunken` on a `surfaceSunken`
                        // panel — a specimen of a control you cannot see.
                        NavSearch(
                            state = navSearch,
                            searchIcon = Tabler.Outline.Search,
                        )
                    }
                } else {
                    null
                },
                // Deliberately dropped when there is a search field: the
                // reference has no separate action button, because the search
                // *is* the action. Keeping both is what made the first render of
                // this panel run out of width at 360dp.
                action = if (search || centreSearch) {
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
    }
}

@Composable
private fun RailPanel(expanded: Boolean, search: Boolean = false) {
    var selected by remember { mutableStateOf(1) }
    val navSearch = rememberNavSearchState()
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
        // Its own host, so a collapsed rail's search pill has somewhere to open
        // into — a rail at 88dp cannot hold a field any more than a bar can.
        OverlayHost(Modifier.fillMaxSize()) {
        NavRail(
            items = destinations(selected) { selected = it },
            selectedIndex = selected,
            expanded = wide,
            onExpandedChange = { wide = it },
            // A pill at 88dp and a field at 280dp, decided by the rail rather
            // than by this call site: `NavSearch` reads `LocalNavExpansion`, so
            // the same line of code is both. Nothing here says which.
            header = if (search) {
                {
                    NavSearch(
                        state = navSearch,
                        searchIcon = Tabler.Outline.Search,
                    )
                }
            } else {
                null
            },
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
}

@Composable
private fun DevicePanel(title: String, width: Dp, height: Dp, safeArea: Dp = 0.dp) {
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
                    windowInsets = if (safeArea > 0.dp) {
                        WindowInsets(bottom = safeArea)
                    } else {
                        WindowInsets(0)
                    },
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

            // Over the scaffold, not under it: `NavigationSuiteScaffold` opens
            // with an opaque full-size `Surface`, which painted straight over
            // the first attempt at this. Drawn where the system would put its
            // gesture bar, so the safe area is something you can see the bar
            // clearing rather than a number it claims to be clearing.
            if (safeArea > 0.dp) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(safeArea)
                        .background(Theme.colors.accent.container.copy(alpha = 0.7f)),
                )
            }
        }
    }
}
