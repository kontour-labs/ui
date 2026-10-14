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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.icons.tabler.Tabler
import io.kontour.icons.tabler.outline.Bell
import io.kontour.icons.tabler.outline.Calendar
import io.kontour.icons.tabler.outline.DotsVertical
import io.kontour.icons.tabler.outline.Home
import io.kontour.icons.tabler.outline.Map
import io.kontour.icons.tabler.outline.Pin
import io.kontour.icons.tabler.outline.Plus
import io.kontour.icons.tabler.outline.Search
import io.kontour.icons.tabler.outline.Star
import io.kontour.icons.tabler.outline.Ticket
import io.kontour.icons.tabler.outline.Trash
import io.kontour.icons.tabler.outline.User
import io.kontour.ui.adaptive.Scaffold
import io.kontour.ui.adaptive.WindowSizeClassProvider
import io.kontour.ui.components.action.Button
import io.kontour.ui.components.action.ButtonSize
import io.kontour.ui.components.action.ButtonVariant
import io.kontour.ui.components.action.FabSize
import io.kontour.ui.components.action.FloatingActionButton
import io.kontour.ui.components.action.IconButton
import io.kontour.ui.foundation.LocalContainerColour
import io.kontour.ui.foundation.Text
import io.kontour.ui.components.display.rememberCarouselState
import io.kontour.ui.nav.HeroImages
import io.kontour.ui.nav.TopBarDefaults
import io.kontour.ui.nav.TopBarCollapseState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import io.kontour.ui.nav.Breadcrumbs
import io.kontour.ui.nav.Crumb
import io.kontour.ui.nav.ModalNavRail
import io.kontour.ui.nav.NavBar
import io.kontour.ui.nav.NavBarMarkerDrag
import io.kontour.ui.nav.NavBarDefaults
import io.kontour.ui.nav.NavBarItemWidth
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.WindowInsets
import io.kontour.ui.nav.TopBarStyle
import io.kontour.ui.nav.more
import io.kontour.ui.nav.rememberNavSearchState
import io.kontour.ui.nav.rememberNavigationSuiteState
import io.kontour.ui.sheet.SheetSide
import io.kontour.ui.components.display.Badge
import androidx.compose.ui.graphics.vector.ImageVector
import io.kontour.icons.tabler.outline.Route
import io.kontour.ui.overlay.DropdownMenu
import io.kontour.ui.overlay.OverlayAlignment
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.Theme
import androidx.compose.foundation.lazy.LazyColumn
import io.kontour.ui.nav.rememberTopBarCollapseState
import io.kontour.ui.nav.topBarCollapse
import io.kontour.ui.components.list.ListItem
import io.kontour.ui.components.list.listSection

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
        // The ground a floating bar fades its page into, as a `Surface` would
        // say: painted rather than a surface, the frame has to say it itself.
        CompositionLocalProvider(LocalContainerColour provides Theme.colours.surfaceSunken) {
            OverlayHost(Modifier.fillMaxSize()) { content() }
        }
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

/** Cells that share the bar, a tab bar's, or each destination as wide as it needs. */
private val barItemWidth = Knob.Choice("Item width", NavBarItemWidth.entries.toList(), NavBarItemWidth.Equal)

/** A highlight around the whole destination, the default, or the circle behind the glyph. */
private val barMarker = Knob.Choice("Marker", listOf(true, false), name = { if (it) "Destination" else "Glyph" })

/** The capsule, or a rounded rectangle. */
private val barRounded = Knob.Choice("Shape", listOf(false, true), name = { if (it) "Rounded" else "Default" })

/**
 * Drag the marker along the bar: off, choosing the destination it is let go
 * on, or choosing each one as it gets there.
 */
private val barDrag = Knob.Choice("Drag the marker", NavBarMarkerDrag.entries.toList(), NavBarMarkerDrag.SelectOnRelease, name = ::markerDragName)

private fun markerDragName(drag: NavBarMarkerDrag): String = when (drag) {
    NavBarMarkerDrag.Off -> "Off"
    NavBarMarkerDrag.SelectOnRelease -> "Choose on lift"
    NavBarMarkerDrag.SelectWhileDragging -> "Choose while dragging"
}

/** Four more destinations than the bar has room for, behind `more` at its end. */
private val barMore = Knob.Flag("More")

internal val NavBarDemo = ComponentDemo(
    slug = "nav-bar",
    knobs = listOf(barStyle, barLabels, barSearch, barSearchPlacement, barAction, barItemWidth, barMarker, barRounded, barDrag, barMore),
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
                shape = if (this@ComponentDemo[barRounded]) RoundedCornerShape(20.dp) else null,
                itemWidth = this@ComponentDemo[barItemWidth],
                marker = if (this@ComponentDemo[barMarker]) {
                    NavBarDefaults.destinationMarker()
                } else {
                    NavBarDefaults.glyphMarker()
                },
                markerDrag = this@ComponentDemo[barDrag],
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
                if (this@ComponentDemo[barMore]) {
                    more {
                        allDestinations.drop(4).take(4).forEach { d ->
                            item(here == d.key, { here = d.key }, key = d.key, icon = d.icon) { +d.label }
                        }
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
private val topBarFade = Knob.Flag("Fade under", initial = false)

/** A hero's picture as several, swiped between, with their dots above the title. */
private val topBarPictures = Knob.Flag("Several pictures", initial = false)

/** How far a Large or Hero bar opens: the style's own, or a height asked for. */
private val topBarOpen = Knob.Choice(
    "Open height",
    listOf<Dp?>(null, 280.dp, 440.dp),
    null,
    name = { it?.let { height -> "${height.value.roundToInt()}dp" } ?: "Default" },
)

internal val TopBarDemo = ComponentDemo(
    slug = "top-bar",
    knobs = listOf(topBarStyle, topBarBack, topBarOpen, topBarFade, topBarPictures),
) {
    val style = this[topBarStyle]
    val back = this[topBarBack]
    val open = this[topBarOpen]
    val fade = this[topBarFade]
    val several = this[topBarPictures]
    val skies = rememberCarouselState { Skies.size }
    val tall = style == TopBarStyle.Hero || (style == TopBarStyle.Large && open != null)
    // Over a list that scrolls, which is the only way to see a Large bar
    // collapse: the bar takes the scroll until its title has gone up into
    // the first line, then the rows move. And the list under the bar, padded
    // by its height, so there is something to fade under it.
    val collapse = rememberTopBarCollapseState()
    // A hero is drawn over its list instead, and follows the list's scroll:
    // the list is the only thing that scrolls, so the two never share a drag.
    val list = rememberLazyListState()
    val follow = rememberTopBarCollapseState(list, initialOpenHeight = open ?: TopBarDefaults.HeroHeight)
    val hero = style == TopBarStyle.Hero
    val bar = @Composable { state: TopBarCollapseState ->
        TopBar(
            style = style,
            onBack = if (back) ({ echo("Back") }) else null,
            // A line where the fade starts would undo it.
            showDivider = style != TopBarStyle.Hero && !fade,
            fadeUnder = fade,
            actions = { OverflowMenu(::echo) },
            image = {
                if (several) {
                    HeroImages(skies) { page -> DuskOverTheRiver(Skies[page]) }
                } else {
                    DuskOverTheRiver()
                }
            },
            expandedHeight = open ?: Dp.Unspecified,
            collapseState = state,
            // Not at the top of the window: a card on a page. Left to the
            // default, the bar padded itself by the status bar's height in
            // the middle of the page, which over a picture put its buttons
            // well down it.
            windowInsets = WindowInsets(0),
        ) {
            +"Perth Underground"
            supporting { +"Platform 2 · Joondalup line" }
        }
    }
    val departures: LazyListScope.() -> Unit = {
        // One group, a list item's gap between its rows.
        listSection(List(12) { it }) { index, position ->
            ListItem(position = position) {
                +"Departure ${index + 1}"
                supporting { +"${4 + index * 6} min · Joondalup" }
            }
        }
    }
    Frame(height = if (tall) 560.dp else 320.dp) {
        if (hero) {
            Box(Modifier.fillMaxSize()) {
                LazyColumn(
                    Modifier.fillMaxSize().topBarCollapse(follow),
                    state = list,
                    contentPadding = PaddingValues(top = follow.openHeight),
                    content = departures,
                )
                bar(follow)
            }
        } else {
            Scaffold(
                modifier = Modifier.topBarCollapse(collapse),
                topBar = { bar(collapse) },
                windowInsets = WindowInsets(0),
            ) { padding ->
                LazyColumn(Modifier.fillMaxSize(), contentPadding = padding, content = departures)
            }
        }
    }
}

/** A sky to draw the river's city under: its gradient, top to foot, and its sun or moon. */
private class Sky(val gradient: List<Color>, val light: Color, val lightAt: Offset)

private val Dusk = Sky(
    listOf(Color(0xFFF6D365), Color(0xFFFDA085), Color(0xFF6B3F69), Color(0xFF2B1F4A)),
    Color(0xFFFFF1C1),
    Offset(0.72f, 0.58f),
)

/** The hero's pictures with "Several pictures" on: dusk, a clear morning, and night. */
private val Skies = listOf(
    Dusk,
    Sky(listOf(Color(0xFF8EC5FC), Color(0xFFB8D8F8), Color(0xFFE0C3FC), Color(0xFF7A6B9A)), Color(0xFFFFFDF0), Offset(0.28f, 0.34f)),
    Sky(listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364), Color(0xFF16222A)), Color(0xFFE8ECF2), Offset(0.62f, 0.22f)),
)

/**
 * A dusk over the river, drawn: a sky pale at the top and deep at the foot, a
 * low sun, and a city's edge along the bottom — so the fade under a hero's
 * title has something busy to soften, the way a photograph would. Or another
 * [sky] over the same city.
 */
@Composable
private fun DuskOverTheRiver(sky: Sky = Dusk) {
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(sky.gradient))
        drawCircle(
            sky.light,
            radius = size.minDimension * 0.09f,
            center = Offset(size.width * sky.lightAt.x, size.height * sky.lightAt.y),
        )
        // Towers of different heights and widths, bright windows on some.
        val ground = size.height
        var x = 0f
        var i = 0
        while (x < size.width) {
            val w = size.width * (0.05f + 0.04f * ((i * 7) % 5) / 4f)
            val h = size.height * (0.12f + 0.22f * ((i * 3) % 7) / 6f)
            drawRect(Color(0xFF1B1430), topLeft = Offset(x, ground - h), size = Size(w, h))
            if (i % 2 == 0) {
                drawRect(Color(0xFFFFD27A), topLeft = Offset(x + w * 0.3f, ground - h * 0.8f), size = Size(w * 0.18f, h * 0.08f))
            }
            x += w + size.width * 0.008f
            i++
        }
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
