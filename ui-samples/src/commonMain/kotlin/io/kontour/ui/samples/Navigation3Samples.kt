package io.kontour.ui.samples

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import io.kontour.ui.motion.pageBack
import io.kontour.ui.nav.NavigationSuiteScaffold
import io.kontour.ui.nav.TopBar
import io.kontour.ui.nav.navigationSuitePadding
import io.kontour.ui.nav3.coversNavigation
import io.kontour.ui.nav3.rememberNavigationSuiteSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import io.kontour.ui.components.list.ListGroup
import io.kontour.ui.foundation.Text
import io.kontour.ui.nav3.detailPane
import io.kontour.ui.nav3.listPane
import io.kontour.ui.nav3.mainPane
import io.kontour.ui.nav3.rememberListDetailSceneStrategy
import io.kontour.ui.nav3.rememberPageTransitionStrategy
import io.kontour.ui.nav3.rememberSupportingPaneSceneStrategy
import io.kontour.ui.nav3.supportingPane

/** The caller's own route keys, which a real app would make serializable. */
internal object StopList
internal data class StopDetail(val name: String)
internal object RunSummary
internal object Conditions
internal object Departures
internal object Saved
internal object Ticket

@Composable
fun ListDetailSceneStrategyBasics() {
    val backStack = remember { mutableStateListOf<Any>(StopList) }

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        sceneStrategies = listOf(rememberListDetailSceneStrategy()),
        entryProvider = entryProvider {
            // The list, and what fills the detail pane before anything is
            // picked. On a phone the placeholder is never drawn.
            entry<StopList>(metadata = listPane { Text("Pick a stop") }) {
                ListGroup {
                    for (name in listOf("Perth Underground", "Elizabeth Quay")) {
                        item(label = name, onClick = { backStack += StopDetail(name) })
                    }
                }
            }
            // Beside the list when there is room; on top of it when not.
            entry<StopDetail>(metadata = detailPane()) { stop -> Text(stop.name) }
        },
    )
}

@Composable
fun SupportingPaneSceneStrategyBasics() {
    val backStack = remember { mutableStateListOf<Any>(RunSummary) }

    // Inside the app's `OverlayHost`: on a narrow window the supporting pane
    // is a sheet, and a sheet draws in the host.
    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        sceneStrategies = listOf(rememberSupportingPaneSceneStrategy()),
        entryProvider = entryProvider {
            entry<RunSummary>(metadata = mainPane()) {
                ListGroup {
                    item(label = "Conditions", onClick = { backStack += Conditions })
                }
            }
            entry<Conditions>(metadata = supportingPane()) { Text("24 °C, dry") }
        },
    )
}

@Composable
fun PageTransitionStrategyBasics() {
    val backStack = remember { mutableStateListOf<Any>(StopList) }

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        sceneStrategies = listOf(rememberListDetailSceneStrategy()),
        // Every page pushes, pops and follows a back gesture the way its
        // platform's do: predictive back on Android, swipe back on iOS.
        sceneDecoratorStrategies = listOf(rememberPageTransitionStrategy()),
        entryProvider = entryProvider {
            entry<StopList>(metadata = listPane()) {
                ListGroup {
                    item(label = "Perth Underground", onClick = { backStack += StopDetail("Perth Underground") })
                }
            }
            entry<StopDetail>(metadata = detailPane()) { stop -> Text(stop.name) }
        },
    )
}

@Composable
fun NavigationSuiteSceneStrategyBasics() {
    val backStack = remember { mutableStateListOf<Any>(Departures) }
    val here = backStack.firstOrNull()
    // The navigation around the pages inside it. Its own back stack is this
    // one: going to a tab here replaces the stack's root.
    val suite = rememberNavigationSuiteSceneStrategy<Any> { pages ->
        NavigationSuiteScaffold(
            destinations = {
                item(here == Departures, { backStack.clear(); backStack += Departures }, key = Departures, icon = null) { +"Departures" }
                item(here == Saved, { backStack.clear(); backStack += Saved }, key = Saved, icon = null) { +"Saved" }
            },
        ) { pages() }
    }

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        sceneStrategies = listOf(suite),
        sceneDecoratorStrategies = listOf(rememberPageTransitionStrategy()),
        entryProvider = entryProvider {
            entry<Departures> {
                // Pages inside the navigation keep clear of the bar themselves.
                ListGroup(Modifier.padding(navigationSuitePadding())) {
                    item(label = "Perth Underground", onClick = { backStack += StopDetail("Perth Underground") })
                    item(label = "Show my ticket", onClick = { backStack += Ticket })
                }
            }
            entry<Saved> { Text("Saved stops") }
            entry<StopDetail> { stop ->
                Column {
                    TopBar(onBack = pageBack()) { +stop.name }
                }
            }
            // Over the bar: it slides in over everything, and back uncovers it.
            entry<Ticket>(metadata = coversNavigation()) {
                Column {
                    TopBar(onBack = pageBack()) { +"Ticket" }
                }
            }
        },
    )
}
