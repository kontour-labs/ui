package io.kontour.ui.catalog.apps.transit

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Bell
import com.composables.icons.tabler.outline.Check
import com.composables.icons.tabler.outline.Dots
import com.composables.icons.tabler.outline.Refresh
import com.composables.icons.tabler.outline.Route
import com.composables.icons.tabler.outline.Sparkles
import com.composables.icons.tabler.outline.Star
import com.composables.icons.tabler.outline.Ticket
import io.kontour.ui.catalog.apps.SkylineArt
import io.kontour.ui.catalog.apps.rememberAppToast
import io.kontour.ui.components.action.ExtendedFloatingActionButton
import io.kontour.ui.components.action.IconButton
import io.kontour.ui.components.display.Tag
import io.kontour.ui.foundation.GroupPosition
import io.kontour.ui.components.list.ListItem
import io.kontour.ui.components.list.ListItemDefaults
import io.kontour.ui.components.list.PullToRefresh
import io.kontour.ui.components.list.SwipeAction
import io.kontour.ui.components.list.SwipeActions
import io.kontour.ui.components.list.listSection
import io.kontour.ui.components.selection.ChipGroup
import io.kontour.ui.components.selection.FilterChip
import io.kontour.ui.foundation.Text
import io.kontour.ui.foundation.shape
import io.kontour.ui.nav.TopBar
import io.kontour.ui.nav.navigationSuitePadding
import io.kontour.ui.nav.TopBarStyle
import io.kontour.ui.nav.rememberTopBarCollapseState
import io.kontour.ui.overlay.DropdownMenu
import io.kontour.ui.overlay.coachmarkStep
import io.kontour.ui.overlay.rememberCoachmarkTour
import io.kontour.ui.theme.Theme
import kotlinx.coroutines.delay

/**
 * The next departures from the home stop, under a picture of the city.
 *
 * What it is the demo of, in one screen:
 *
 * - **A hero bar** that collapses into a small one as the list scrolls, over
 *   a picture that runs under the status bar.
 * - **Pull to refresh**, outside the bar's collapse: a pull at the top opens
 *   the bar fully first, and only then starts the refresh, so the gesture
 *   that shows more of the picture is not mistaken for one asking for news.
 * - **Filter chips with counts**, and **swipe actions** on each row —
 *   favourite from the start, a reminder from the end.
 * - **A tour on request** — "Show me around", in the overflow menu — rather
 *   than tips that introduce themselves on the first launch.
 */
@Composable
internal fun DeparturesPage(
    onStop: (String) -> Unit,
    onTicket: () -> Unit,
    onPlan: () -> Unit,
    showPlanButton: Boolean,
) {
    val toast = rememberAppToast()
    var shown by remember { mutableStateOf(departures) }
    var refreshing by remember { mutableStateOf(false) }
    var showing by remember { mutableStateOf(setOf(Mode.Train, Mode.Bus)) }
    var favourites by remember { mutableStateOf(setOf<String>()) }
    var menu by remember { mutableStateOf(false) }
    val collapse = rememberTopBarCollapseState()
    val list = rememberLazyListState()
    val tour = rememberCoachmarkTour("ticket", "filters", "plan")

    LaunchedEffect(refreshing) {
        if (!refreshing) return@LaunchedEffect
        delay(RefreshMillis)
        shown = refreshed(shown)
        refreshing = false
        toast("Departures updated")
    }

    val visible = shown.filter { it.mode in showing }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TopBar(
                style = TopBarStyle.Hero,
                image = { SkylineArt(Modifier.fillMaxSize()) },
                navigation = rootNavigation(),
                actions = {
                    IconButton(
                        icon = Tabler.Outline.Ticket,
                        contentDescription = "My ticket",
                        onClick = onTicket,
                        modifier = Modifier.coachmarkStep(
                            tour,
                            id = "ticket",
                            title = "Your ticket",
                            message = "Your balance, and how close today is to the fare cap.",
                        ),
                    )
                    Box {
                        IconButton(icon = Tabler.Outline.Dots, contentDescription = "More", onClick = { menu = true })
                        DropdownMenu(visible = menu, onDismissRequest = { menu = false }) {
                            item("Refresh", leadingIcon = Tabler.Outline.Refresh, onClick = {
                                menu = false
                                refreshing = true
                            })
                            item("Show me around", leadingIcon = Tabler.Outline.Sparkles, onClick = {
                                menu = false
                                tour.start()
                            })
                        }
                    }
                },
                collapseState = collapse,
            ) {
                +"Perth Underground"
                supporting { +"Next departures" }
            }
            // The refresh under the bar, round the list alone: it is the
            // list a pull brings down, with the spinner in the gap it
            // leaves below the bar. The bar's collapse goes on the list,
            // inside the refresh, so a pull down opens the bar before it
            // starts a refresh, and pushing the spinner away comes before
            // folding the bar.
            PullToRefresh(
                refreshing = refreshing,
                onRefresh = { refreshing = true },
                modifier = Modifier.weight(1f),
            ) {
                LazyColumn(
                    state = list,
                    modifier = Modifier.fillMaxSize().nestedScroll(collapse.nestedScrollConnection).testTag("departures"),
                    contentPadding = transitPagePadding(),
                    // No arrangement: the departures are a `listSection`,
                    // which puts a list item's gap between its rows and its own
                    // space above them, and an arrangement would add to both.
                ) {
                    item(key = "filters") {
                        ChipGroup(
                            Modifier
                                .coachmarkStep(
                                    tour,
                                    id = "filters",
                                    title = "Trains, buses or both",
                                    message = "The counts are what is leaving in the next half hour.",
                                ),
                        ) {
                            for (mode in Mode.entries) {
                                FilterChip(
                                    selected = mode in showing,
                                    onSelectedChange = { on -> showing = if (on) showing + mode else showing - mode },
                                    selectedIcon = Tabler.Outline.Check,
                                ) {
                                    +mode.label
                                    tag { +"${shown.count { it.mode == mode }}" }
                                }
                            }
                        }
                    }
                    if (visible.isEmpty()) {
                        item(key = "none") {
                            Text(
                                "Nothing is leaving. Choose trains or buses above.",
                                style = Theme.typography.bodyMedium,
                                colour = Theme.colours.contentMuted,
                                modifier = Modifier.padding(top = Theme.spacing.md),
                            )
                        }
                    }
                    listSection(visible, key = { it.id }) { departure, position ->
                        DepartureRow(
                            departure = departure,
                            position = position,
                            favourite = departure.route in favourites,
                            onClick = { onStop(stopFor(departure)) },
                            onFavourite = {
                                favourites = favourites + departure.route
                                toast("${departure.route} added to favourites")
                            },
                            onRemind = { toast("We'll remind you 2 minutes before the ${departure.route} leaves") },
                        )
                    }
                }
            }
        }

        if (showPlanButton) {
            ExtendedFloatingActionButton(
                icon = Tabler.Outline.Route,
                contentDescription = "Plan a trip",
                expanded = list.firstVisibleItemIndex == 0,
                onClick = onPlan,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(navigationSuitePadding())
                    .padding(Theme.spacing.md)
                    .coachmarkStep(tour, id = "plan", title = "Plan a trip", message = "From here to anywhere on the network."),
            ) {
                +"Plan"
            }
        }
    }
}

@Composable
private fun DepartureRow(
    departure: Departure,
    position: GroupPosition,
    favourite: Boolean,
    onClick: () -> Unit,
    onFavourite: () -> Unit,
    onRemind: () -> Unit,
) {
    SwipeActions(
        start = listOf(
            SwipeAction(
                label = "Favourite",
                icon = Tabler.Outline.Star,
                onAction = onFavourite,
                background = Theme.colours.success.solid,
            ),
        ),
        end = listOf(
            SwipeAction(
                label = "Remind me",
                icon = Tabler.Outline.Bell,
                onAction = onRemind,
                background = Theme.colours.accent.solid,
            ),
        ),
        // The row's own shape, so a middle row slides square-cornered like
        // the row it is rather than as a rounded card.
        shape = position.shape(ListItemDefaults.Shape, ListItemDefaults.InnerCorner, Orientation.Vertical),
    ) {
        ListItem(onClick = onClick, position = position, modifier = Modifier.fillMaxWidth()) {
            leading { +iconFor(departure.mode) }
            +"${departure.route} to ${departure.towards}"
            supporting {
                val platform = if (departure.mode == Mode.Train) "Platform ${departure.platform}" else departure.platform
                +"$platform · ${if (departure.minutes == 0) "now" else "${departure.minutes} min"}${if (favourite) " · ★" else ""}"
            }
            trailing {
                Tag(tone = departure.running.tone) { +departure.running.label }
            }
        }
    }
}

/** Where a row goes when tapped: the stop it is heading for, if the app knows it. */
private fun stopFor(departure: Departure): String =
    stops.firstOrNull { it.name == departure.towards }?.name ?: "Perth Underground"

private const val RefreshMillis = 1_200L
