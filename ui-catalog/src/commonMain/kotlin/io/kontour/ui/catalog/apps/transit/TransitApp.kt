package io.kontour.ui.catalog.apps.transit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Bus
import com.composables.icons.tabler.outline.History
import com.composables.icons.tabler.outline.MapPin
import com.composables.icons.tabler.outline.Route
import com.composables.icons.tabler.outline.Settings
import io.kontour.ui.adaptive.LocalWindowSizeClass
import io.kontour.ui.adaptive.WindowWidthClass
import io.kontour.ui.catalog.apps.LeaveAppButton
import io.kontour.ui.catalog.apps.MiniApp
import io.kontour.ui.components.action.Button
import io.kontour.ui.foundation.Icon
import io.kontour.ui.foundation.Text
import io.kontour.ui.nav.NavigationSuiteScaffold
import io.kontour.ui.nav.navigationSuiteTypeFor
import io.kontour.ui.nav3.coversNavigation
import io.kontour.ui.nav3.rememberNavigationSuiteSceneStrategy
import io.kontour.ui.nav3.rememberPageTransitionStrategy
import io.kontour.ui.theme.Theme

/*
 * Transit's places. Plain objects and data classes rather than serializable
 * keys: the gallery keeps its stack for as long as the app runs and does not
 * need it to outlive the process.
 */
internal data object DeparturesRoute
internal data object StopsRoute
internal data object TripsRoute
internal data object SettingsRoute
internal data class StopRoute(val name: String)
internal data class LineRoute(val name: String)
internal data object TicketRoute
internal data class TripRoute(val id: String)

/** The four places at the root, in the order the navigation shows them. */
private val tabs = listOf(DeparturesRoute, StopsRoute, TripsRoute, SettingsRoute)

/**
 * Transit: the next departures from a home stop, an A–Z of stops, a year of
 * trips and the app's settings, with a ticket over the navigation and a trip
 * planner in a sheet.
 *
 * **One back stack, the navigation round it.** The tabs are the stack's root:
 * choosing one replaces the stack, and every page goes on top of it. On a
 * phone a stop, a line, a trip and the ticket cover the navigation
 * (`coversNavigation`): each is about one thing, and the bar's tabs would be in
 * its way. Beside a rail or a sidebar they stay inside the navigation, which is
 * at the side and in nobody's way — except the ticket, which is held up on its
 * own everywhere, as a card is to a reader at the gate. Back goes down the
 * stack, and at the root it leaves the app.
 *
 * On a phone the navigation is a floating bar, whose marker can be dragged
 * from tab to tab; above that, a rail or a sidebar, with the planner as its
 * action and the way out at its head.
 */
@Composable
internal fun TransitApp() {
    val backStack = remember { mutableStateListOf<Any>(DeparturesRoute) }
    var planning by remember { mutableStateOf(false) }
    val width = LocalWindowSizeClass.current.width
    val compact = width == WindowWidthClass.Compact
    val root = backStack.firstOrNull()

    fun go(tab: Any) {
        backStack.clear()
        backStack += tab
    }

    val suite = rememberNavigationSuiteSceneStrategy<Any> { pages ->
        NavigationSuiteScaffold(
            destinations = {
                for (tab in tabs) {
                    item(
                        selected = root == tab,
                        onClick = { go(tab) },
                        key = tab,
                        icon = iconFor(tab),
                        modifier = Modifier.testTag("tab-${labelFor(tab)}"),
                    ) { +labelFor(tab) }
                }
            },
            type = navigationSuiteTypeFor(width),
            header = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = Theme.spacing.sm),
                ) {
                    LeaveAppButton()
                    Icon(MiniApp.Transit.icon, contentDescription = null)
                    Text(MiniApp.Transit.title, style = Theme.typography.titleSmall)
                }
            },
            // On a phone the planner is the departures' own button; beside a
            // rail or a sidebar there is room for it on every page.
            action = if (compact) null else {
                {
                    Button(onClick = { planning = true }) {
                        +Tabler.Outline.Route
                        +"Plan a trip"
                    }
                }
            },
        ) { pages() }
    }

    // A stop, a line or a trip is a page of its own on a phone, over the
    // navigation as well as the page under it: each is about one thing, with
    // its own back button and things to do at its foot, and the bar's tabs
    // would only be in their way. Beside a rail or a sidebar the navigation is
    // at the side, out of the way, and the page stays inside it.
    val detail: Map<String, Any> = if (compact) coversNavigation() else emptyMap()

    CompositionLocalProvider(LocalLeaveInTopBar provides compact) {
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            sceneStrategies = listOf(suite),
            sceneDecoratorStrategies = listOf(rememberPageTransitionStrategy()),
            entryProvider = entryProvider {
                entry<DeparturesRoute> {
                    DeparturesPage(
                        onStop = { backStack += StopRoute(it) },
                        onTicket = { backStack += TicketRoute },
                        onPlan = { planning = true },
                        showPlanButton = compact,
                    )
                }
                entry<StopsRoute> { StopsPage(onStop = { backStack += StopRoute(it) }) }
                entry<TripsRoute> { TripsPage(onTrip = { backStack += TripRoute(it) }) }
                entry<SettingsRoute> { TransitSettingsPage() }
                entry<StopRoute>(metadata = detail) { route ->
                    StopPage(
                        stop = stopNamed(route.name),
                        onLine = { backStack += LineRoute(it) },
                        onTicket = { backStack += TicketRoute },
                    )
                }
                entry<LineRoute>(metadata = detail) { route -> LinePage(lineNamed(route.name), onStop = { backStack += StopRoute(it) }) }
                entry<TripRoute>(metadata = detail) { route -> TripPage(tripWithId(route.id)) }
                // Over the navigation, as a ticket is held up on its own.
                entry<TicketRoute>(metadata = coversNavigation()) { TicketPage() }
            },
        )
    }

    PlannerSheet(visible = planning, onDismiss = { planning = false })
}

private fun labelFor(tab: Any): String = when (tab) {
    DeparturesRoute -> "Departures"
    StopsRoute -> "Stops"
    TripsRoute -> "Trips"
    else -> "Settings"
}

private fun iconFor(tab: Any) = when (tab) {
    DeparturesRoute -> Tabler.Outline.Bus
    StopsRoute -> Tabler.Outline.MapPin
    TripsRoute -> Tabler.Outline.History
    else -> Tabler.Outline.Settings
}
