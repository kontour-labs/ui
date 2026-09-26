package io.kontour.ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import io.kontour.ui.components.action.Button
import io.kontour.ui.components.action.ButtonVariant
import io.kontour.ui.components.list.ListGroup
import io.kontour.ui.foundation.Text
import io.kontour.ui.nav.ModalNavDrawer
import io.kontour.ui.nav.TopBar
import io.kontour.ui.nav3.detailPane
import io.kontour.ui.nav3.listPane
import io.kontour.ui.nav3.mainPane
import io.kontour.ui.nav3.rememberListDetailSceneStrategy
import io.kontour.ui.nav3.rememberPageTransitionStrategy
import io.kontour.ui.nav3.rememberSupportingPaneSceneStrategy
import io.kontour.ui.nav3.supportingPane
import io.kontour.ui.overlay.AlertDialog
import io.kontour.ui.sheet.ModalBottomSheet
import io.kontour.ui.sheet.ModalSideSheet
import io.kontour.ui.sheet.SheetHeader
import io.kontour.ui.theme.Theme

/**
 * The places back has to work, each a small app of its own inside the frame.
 *
 * Every scenario is a real stack or a real overlay — a `NavDisplay`, a sheet
 * from the library — not a drawing of one, so what back does here is what it
 * does in an app built from the same parts.
 */
internal enum class BackScenario(val title: String, val note: String) {
    Pages(
        "Pages",
        "A stack of four. Back pops one page at a time; at the first, it is not the frame's.",
    ),
    ListDetail(
        "List and detail",
        "Two panes on a tablet: back goes through the details in their pane, and the list stays. " +
            "On a phone they are pages.",
    ),
    Supporting(
        "Supporting pane",
        "Beside the main pane on a tablet, and a sheet over it on a phone. Back closes it either way.",
    ),
    Sheet(
        "Bottom sheet",
        "Back closes the sheet, not the page under it. One that may not close takes back and gives only a little.",
    ),
    SheetStack(
        "Sheet with a stack",
        "A stack inside the sheet goes back first; with nothing left in it, back closes the sheet.",
    ),
    SideSheets(
        "Side sheet and drawer",
        "Each follows back towards its own edge.",
    ),
    Dialog(
        "Dialog",
        "Back closes the dialog. A dialog that must be answered refuses it — and back does not reach the page behind.",
    ),
    Nested(
        "All of them",
        "A detail, a sheet with a stack over it, and a dialog over that. Back unwinds them in order.",
    ),
}

/** A scenario, drawn into the frame. */
@Composable
internal fun BackScenarioContent(scenario: BackScenario, dismissible: Boolean) {
    when (scenario) {
        BackScenario.Pages -> PagesScenario()
        BackScenario.ListDetail -> ListDetailScenario(dismissible, withSheet = false)
        BackScenario.Supporting -> SupportingScenario()
        BackScenario.Sheet -> SheetScenario(dismissible)
        BackScenario.SheetStack -> SheetStackScenario(dismissible)
        BackScenario.SideSheets -> SideSheetsScenario(dismissible)
        BackScenario.Dialog -> DialogScenario(dismissible)
        BackScenario.Nested -> ListDetailScenario(dismissible, withSheet = true)
    }
}

// --- routes ----------------------------------------------------------------

private object StopsRoute
private data class StopRoute(val name: String)
private data class ServiceRoute(val stop: String, val route: String)
private data class VehicleRoute(val route: String)
private object RunRoute
private object ConditionsRoute

private val Stops = listOf("Perth Busport", "Elizabeth Quay", "Claremont", "Fremantle")

// --- the pages the scenarios are made of ------------------------------------

/**
 * One page: a title bar, with a back arrow when there is somewhere to go back
 * to, and a list of rows that go somewhere.
 */
@Composable
private fun FramePage(
    title: String,
    onBack: (() -> Unit)?,
    rows: List<Pair<String, () -> Unit>>,
    note: String? = null,
    extra: @Composable () -> Unit = {},
) {
    Column(Modifier.fillMaxSize()) {
        TopBar(onBack = onBack, windowInsets = WindowInsets(0)) { +title }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Theme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.md),
        ) {
            if (note != null) Text(note, style = Theme.typography.bodySmall, colour = Theme.colours.contentMuted)
            if (rows.isNotEmpty()) {
                ListGroup {
                    for ((label, onClick) in rows) item(label = label, onClick = onClick)
                }
            }
            extra()
        }
    }
}

@Composable
private fun Placeholder(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, colour = Theme.colours.contentMuted)
    }
}

@Composable
private fun Actions(content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
    ) { content() }
}

@Composable
private fun Action(label: String, onClick: () -> Unit) {
    Button(onClick = onClick, variant = ButtonVariant.Secondary) { Text(label) }
}

// --- full pages ---------------------------------------------------------------

@Composable
private fun PagesScenario() {
    val backStack = remember { mutableStateListOf<Any>(StopsRoute) }
    val pop: () -> Unit = { backStack.removeLastOrNull() }
    NavDisplay(
        backStack = backStack,
        onBack = pop,
        sceneDecoratorStrategies = listOf(rememberPageTransitionStrategy()),
        entryProvider = entryProvider {
            entry<StopsRoute> {
                FramePage("Stops", onBack = null, rows = Stops.map { it to { backStack += StopRoute(it) } })
            }
            entry<StopRoute> { stop ->
                FramePage(
                    stop.name, onBack = pop,
                    rows = listOf("950", "935", "37").map { "Route $it" to { backStack += ServiceRoute(stop.name, it) } },
                )
            }
            entry<ServiceRoute> { service ->
                FramePage(
                    "Route ${service.route}", onBack = pop,
                    rows = listOf("Bus 2417" to { backStack += VehicleRoute(service.route) }),
                    note = "From ${service.stop}, every 8 minutes.",
                )
            }
            entry<VehicleRoute> { vehicle ->
                FramePage("Bus 2417", onBack = pop, rows = emptyList(), note = "On route ${vehicle.route}, 3 minutes away.")
            }
        },
    )
}

// --- panes -------------------------------------------------------------------

/**
 * A list beside its details, or the same stack as pages on a phone — and, as
 * "All of them", a detail with a sheet to open that holds a stack and a dialog.
 */
@Composable
private fun ListDetailScenario(dismissible: Boolean, withSheet: Boolean) {
    val backStack = remember { mutableStateListOf<Any>(StopsRoute) }
    val pop: () -> Unit = { backStack.removeLastOrNull() }
    var departures by remember { mutableStateOf(false) }
    NavDisplay(
        backStack = backStack,
        onBack = pop,
        sceneStrategies = listOf(rememberListDetailSceneStrategy()),
        sceneDecoratorStrategies = listOf(rememberPageTransitionStrategy()),
        entryProvider = entryProvider {
            entry<StopsRoute>(metadata = listPane { Placeholder("Pick a stop") }) {
                FramePage(
                    "Stops", onBack = null,
                    rows = Stops.map { name ->
                        name to {
                            // A new stop replaces the details rather than
                            // piling onto them, as picking from a list does.
                            backStack.retainAll { it == StopsRoute }
                            backStack += StopRoute(name)
                        }
                    },
                )
            }
            entry<StopRoute>(metadata = detailPane()) { stop ->
                FramePage(
                    stop.name, onBack = pop,
                    rows = listOf("950", "935").map { "Route $it" to { backStack += ServiceRoute(stop.name, it) } },
                    extra = {
                        if (withSheet) Actions { Action("Departures") { departures = true } }
                    },
                )
            }
            entry<ServiceRoute>(metadata = detailPane()) { service ->
                FramePage(
                    "Route ${service.route}", onBack = pop, rows = emptyList(),
                    note = "From ${service.stop}. On a tablet this is the second detail in the pane: " +
                        "back goes to the first, and the list does not move.",
                )
            }
        },
    )
    if (withSheet) DeparturesSheet(departures, onClose = { departures = false }, dismissible, withDialog = true)
}

@Composable
private fun SupportingScenario() {
    val backStack = remember { mutableStateListOf<Any>(RunRoute) }
    val pop: () -> Unit = { backStack.removeLastOrNull() }
    NavDisplay(
        backStack = backStack,
        onBack = pop,
        sceneStrategies = listOf(rememberSupportingPaneSceneStrategy()),
        sceneDecoratorStrategies = listOf(rememberPageTransitionStrategy()),
        entryProvider = entryProvider {
            entry<RunRoute>(metadata = mainPane()) {
                FramePage(
                    "Sunday's run", onBack = null,
                    rows = listOf("Conditions" to { if (ConditionsRoute !in backStack) backStack += ConditionsRoute }),
                    note = "12.4 km, 58 minutes.",
                )
            }
            entry<ConditionsRoute>(metadata = supportingPane()) {
                FramePage("Conditions", onBack = pop, rows = emptyList(), note = "24 °C, dry, a light easterly.")
            }
        },
    )
}

// --- overlays ------------------------------------------------------------------

@Composable
private fun SheetScenario(dismissible: Boolean) {
    var open by remember { mutableStateOf(false) }
    FramePage(
        "Perth Busport", onBack = null, rows = emptyList(),
        extra = { Actions { Action("Departures") { open = true } } },
    )
    ModalBottomSheet(visible = open, onDismissRequest = { open = false }, dismissible = dismissible) { safeArea ->
        SheetHeader { +"Departures" }
        Column(Modifier.padding(safeArea).padding(horizontal = Theme.spacing.md)) {
            ListGroup {
                for (time in listOf("12:04 · 950", "12:09 · 935", "12:12 · 37")) item(label = time)
            }
            if (!dismissible) {
                Actions { Action("Done") { open = false } }
            }
        }
    }
}

@Composable
private fun SheetStackScenario(dismissible: Boolean) {
    var open by remember { mutableStateOf(false) }
    FramePage(
        "Perth Busport", onBack = null, rows = emptyList(),
        extra = { Actions { Action("Departures") { open = true } } },
    )
    DeparturesSheet(open, onClose = { open = false }, dismissible, withDialog = false)
}

/**
 * A sheet holding a stack of its own: departures, then a service, then a bus —
 * and, in "All of them", a dialog from the last.
 */
@Composable
private fun DeparturesSheet(open: Boolean, onClose: () -> Unit, dismissible: Boolean, withDialog: Boolean) {
    ModalBottomSheet(visible = open, onDismissRequest = onClose, dismissible = dismissible) { safeArea ->
        val inner = remember { mutableStateListOf<Any>(StopsRoute) }
        var report by remember { mutableStateOf(false) }
        Box(Modifier.fillMaxWidth().height(420.dp).padding(safeArea)) {
            SheetStack(inner, onReport = { report = true }, withDialog = withDialog, onDone = onClose, dismissible = dismissible)
        }
        if (withDialog) ReportDialog(report, onClose = { report = false }, dismissible = dismissible)
    }
}

@Composable
private fun SheetStack(
    backStack: SnapshotStateList<Any>,
    onReport: () -> Unit,
    withDialog: Boolean,
    onDone: () -> Unit,
    dismissible: Boolean,
) {
    val pop: () -> Unit = { backStack.removeLastOrNull() }
    NavDisplay(
        backStack = backStack,
        onBack = pop,
        sceneDecoratorStrategies = listOf(rememberPageTransitionStrategy()),
        entryProvider = entryProvider {
            entry<StopsRoute> {
                FramePage(
                    "Departures", onBack = null,
                    rows = listOf("950", "935").map { "12:04 · $it" to { backStack += ServiceRoute("Perth Busport", it) } },
                    extra = { if (!dismissible) Actions { Action("Done", onDone) } },
                )
            }
            entry<ServiceRoute> { service ->
                FramePage(
                    "Route ${service.route}", onBack = pop,
                    rows = listOf("Bus 2417" to { backStack += VehicleRoute(service.route) }),
                )
            }
            entry<VehicleRoute> {
                FramePage(
                    "Bus 2417", onBack = pop, rows = emptyList(),
                    note = "Back goes to the route, inside the sheet. The sheet stays open.",
                    extra = { if (withDialog) Actions { Action("Report a problem", onReport) } },
                )
            }
        },
    )
}

@Composable
private fun SideSheetsScenario(dismissible: Boolean) {
    var filters by remember { mutableStateOf(false) }
    var drawer by remember { mutableStateOf(false) }
    FramePage(
        "Stops", onBack = null, rows = emptyList(),
        extra = {
            Actions {
                Action("Filters") { filters = true }
                Action("Menu") { drawer = true }
            }
        },
    )
    ModalSideSheet(visible = filters, onDismissRequest = { filters = false }, dismissible = dismissible) {
        Column(Modifier.padding(Theme.spacing.md), verticalArrangement = Arrangement.spacedBy(Theme.spacing.md)) {
            Text("Filters", style = Theme.typography.titleMedium)
            ListGroup {
                for (mode in listOf("Bus", "Train", "Ferry")) item(label = mode)
            }
            if (!dismissible) Actions { Action("Done") { filters = false } }
        }
    }
    ModalNavDrawer(visible = drawer, onDismissRequest = { drawer = false }) {
        for (place in listOf("Stops", "Routes", "Alerts")) item(label = place, selected = place == "Stops") { drawer = false }
    }
}

@Composable
private fun DialogScenario(dismissible: Boolean) {
    var open by remember { mutableStateOf(false) }
    FramePage(
        "Trip to Fremantle", onBack = null, rows = emptyList(),
        extra = { Actions { Action("Delete trip") { open = true } } },
    )
    AlertDialog(
        visible = open,
        onDismissRequest = { open = false },
        confirmLabel = "Delete",
        onConfirm = { open = false },
        destructive = true,
        hapticWarning = false,
        dismissible = dismissible,
    ) {
        +"Delete this trip?"
        message { +"It will be taken off every device you use." }
    }
}

@Composable
private fun ReportDialog(open: Boolean, onClose: () -> Unit, dismissible: Boolean) {
    AlertDialog(
        visible = open,
        onDismissRequest = onClose,
        confirmLabel = "Send",
        onConfirm = onClose,
        dismissible = dismissible,
    ) {
        +"Report a problem with bus 2417?"
        message { +"Back closes this, then the sheet's stack, then the sheet, then the details." }
    }
}
