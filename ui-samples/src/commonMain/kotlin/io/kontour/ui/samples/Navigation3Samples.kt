package io.kontour.ui.samples

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
import io.kontour.ui.nav3.rememberSupportingPaneSceneStrategy
import io.kontour.ui.nav3.supportingPane

/** The caller's own route keys, which a real app would make serializable. */
internal object StopList
internal data class StopDetail(val name: String)
internal object RunSummary
internal object Conditions

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
