package io.kontour.ui.samples

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.list.fadingEdges
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import io.kontour.icons.tabler.outline.Route
import io.kontour.ui.components.action.ExtendedFloatingActionButton
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import io.kontour.icons.tabler.Tabler
import io.kontour.icons.tabler.outline.Bell
import io.kontour.icons.tabler.outline.Bus
import io.kontour.icons.tabler.outline.Star
import io.kontour.icons.tabler.outline.Trash
import io.kontour.ui.adaptive.Scaffold
import io.kontour.ui.components.action.Button
import io.kontour.ui.components.action.IconButton
import io.kontour.ui.components.list.ListGroup
import io.kontour.ui.components.list.ListItem
import io.kontour.ui.components.list.ListItemDefaults
import io.kontour.ui.components.list.ListItemSplit
import io.kontour.ui.components.list.ListSection
import io.kontour.ui.components.list.PullToRefresh
import io.kontour.ui.components.list.SwipeAction
import io.kontour.ui.components.list.SwipeActions
import io.kontour.ui.components.list.listGroup
import io.kontour.ui.components.list.rememberScrollbarListState
import io.kontour.ui.components.list.scrollbar
import io.kontour.ui.components.selection.SelectionRow
import io.kontour.ui.components.selection.Switch
import io.kontour.ui.components.text.TextField
import io.kontour.ui.foundation.GroupPosition
import io.kontour.ui.foundation.shape
import io.kontour.ui.nav.TopBar
import io.kontour.ui.overlay.Dialog
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Theme

// The examples in `ui-docs/content/patterns.md`: the right way to put the
// library's pieces together, where the wrong way is easy to write.

@Composable
fun PatternRoot() {
    // Once, at the root of the app: the theme every component reads, and the
    // host every dialog, sheet, menu and toast draws in. Not per screen.
    KontourTheme {
        OverlayHost {
            Screen()
        }
    }
}

@Composable
fun PatternScrollingScreen() {
    val list = rememberScrollbarListState()
    val direction = LocalLayoutDirection.current
    Scaffold(topBar = { TopBar { +"Stops" } }) { bars ->
        // The list fills the screen, edge to edge, so the bar runs down the
        // screen's own edge. The bars' height and the page's margin are the
        // list's *content* padding: the rows start clear of the top bar and the
        // margin, and scroll under the bar and into the margin as they go.
        LazyColumn(
            modifier = Modifier.fillMaxSize().scrollbar(list),
            state = list,
            contentPadding = PaddingValues(
                start = bars.calculateStartPadding(direction) + Theme.spacing.md,
                top = bars.calculateTopPadding(),
                end = bars.calculateEndPadding(direction) + Theme.spacing.md,
                bottom = bars.calculateBottomPadding(),
            ),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.Spacing),
        ) {
            listGroup(stops, key = { it.name }) { stop ->
                item(stop.name, supporting = "${stop.routes} routes", icon = Tabler.Outline.Bus) {
                    openStop(stop.name)
                }
            }
        }
    }
}

@Composable
fun PatternRows() {
    // A titled group: the section lines its title up with the rows' words and
    // counts their corners. Each row is pressable as a row — its own press, its
    // own role — rather than a row wrapped in `Modifier.clickable`.
    ListSection(title = { +"Notifications" }) {
        ListGroup {
            item("Service alerts", supporting = "Delays and diversions", icon = Tabler.Outline.Bell) {
                open("Service alerts")
            }
            item("Favourites", icon = Tabler.Outline.Star) { open("Favourites") }
            split(ListItemSplit.Halves) {
                start(onClick = { open("Trains") }) { +"Trains" }
                end(onClick = { open("Buses") }) { +"Buses" }
            }
        }
    }
}

@Composable
fun PatternRowControl() {
    var alerts by remember { mutableStateOf(true) }
    // The whole row is the switch: the row toggles it, and the switch only
    // shows its state, so it takes no callback of its own.
    SelectionRow(selected = alerts, onSelectedChange = { alerts = it }, role = Role.Switch) {
        +"Service alerts"
        supporting { +"Delays and diversions" }
        trailing { Switch(alerts, onCheckedChange = null) }
    }
}

@Composable
fun PatternIconOnly() {
    // An icon with no words beside it says what it does to a screen reader.
    IconButton(onClick = { delete() }, icon = Tabler.Outline.Trash, contentDescription = "Remove favourite")
}

@Composable
fun PatternDialog() {
    var open by remember { mutableStateOf(false) }
    Button(onClick = { open = true }) { +"Rename" }

    // Always composed, shown by `visible`: it animates in and out, and takes
    // the back gesture while it is open. It draws in the root's `OverlayHost`,
    // wherever in the tree it is written.
    val name = rememberTextFieldState("Perth Underground")
    Dialog(
        visible = open,
        onDismissRequest = { open = false },
        title = { +"Rename favourite" },
        actions = {
            cancel()
            confirm("Save") {
                rename(name.text.toString())
                open = false
            }
        },
    ) {
        // The label is the field's own: it is what a screen reader reads for it,
        // and what stays when the field has text in it.
        TextField(name, label = "Name")
    }
}

@Composable
fun PatternSwipeInGroup() {
    // In a group, the swipe clips the row as the group draws it: square where
    // it meets a neighbour, round at the group's ends.
    stops.forEachIndexed { index, stop ->
        val position = GroupPosition.of(index, stops.size)
        SwipeActions(
            end = listOf(SwipeAction("Remove", Tabler.Outline.Trash, { remove(stop.name) }, Theme.colours.danger.solid)),
            shape = position.shape(ListItemDefaults.Shape, ListItemDefaults.InnerCorner, Orientation.Vertical),
        ) {
            ListItem(onClick = { openStop(stop.name) }, position = position) { +stop.name }
        }
    }
}

@Composable
fun PatternPullToRefresh(refreshing: Boolean) {
    // The list's own state: the pull starts only when the list is at its top.
    val rows = rememberLazyListState()
    PullToRefresh(refreshing = refreshing, onRefresh = { refresh() }, scrollState = rows) {
        LazyColumn(state = rows) {
            items(stops, key = { it.name }) { stop -> ListItem { +stop.name } }
        }
    }
}

@Composable
fun PatternScrollAnswer() {
    val list = rememberScrollbarListState()
    // A question about the scroll, answered in a derived state: the screen
    // recomposes when the answer changes, not on every pixel the list moves.
    val atTop by remember { derivedStateOf { list.firstVisibleItemIndex == 0 } }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().scrollbar(list), state = list) {
            items(stops, key = { it.name }) { stop ->
                ListItem(onClick = { openStop(stop.name) }) { +stop.name }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { plan() },
            icon = Tabler.Outline.Route,
            contentDescription = "Plan a trip",
            expanded = atTop,
            modifier = Modifier.align(Alignment.BottomEnd).padding(Theme.spacing.md),
        ) {
            +"Plan a trip"
        }
    }
}

@Composable
fun PatternFadeOnScroll() {
    val scroll = rememberScrollState()
    Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
        // The value is read inside the layer's block, which Compose re-runs on
        // its own, without composing or measuring anything: the header fades
        // as the page scrolls, and nothing else is asked to change.
        HeroHeader(
            Modifier.graphicsLayer {
                alpha = 1f - (scroll.value / FadeDistancePx).coerceIn(0f, 1f)
            },
        )
        Departures()
    }
}

@Composable
fun PatternStateColour(live: Boolean) {
    // The state is what animates, not the colour. The two ends are the theme's
    // on this frame, so a theme switch moves them at once and only a change of
    // `live` fades; mixed in the draw, so the fade composes nothing.
    val lit by animateFloatAsState(if (live) 1f else 0f, Theme.motion.tweenFast())
    val off = Theme.colours.surfaceRaised
    val on = Theme.colours.accent.container
    Box(Modifier.size(12.dp).drawBehind { drawCircle(lerp(off, on, lit)) })
}

@Composable
fun PatternFadedList() {
    val list = rememberLazyListState()
    // The list's own fade: a layer on each 24dp edge, and the rows between
    // them drawn straight on, as on a list with no fade at all.
    LazyColumn(Modifier.fillMaxSize().fadingEdges(list), state = list) {
        items(stops, key = { it.name }) { stop ->
            ListItem(onClick = { openStop(stop.name) }) { +stop.name }
        }
    }
}
