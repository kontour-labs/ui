package io.kontour.ui.catalog

import androidx.compose.foundation.gestures.Orientation
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
import androidx.compose.runtime.mutableFloatStateOf
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
import io.kontour.ui.components.action.IconButton
import io.kontour.ui.components.display.Carousel
import io.kontour.ui.components.display.rememberCarouselState
import io.kontour.ui.components.list.ListGroup
import io.kontour.ui.components.list.ListItem
import io.kontour.ui.components.list.ListItemDefaults
import io.kontour.ui.components.list.SwipeAction
import io.kontour.ui.components.list.SwipeActions
import io.kontour.ui.components.selection.Slider
import io.kontour.ui.foundation.GroupPosition
import io.kontour.ui.foundation.Surface
import io.kontour.ui.foundation.SystemIcons
import io.kontour.ui.foundation.Text
import io.kontour.ui.foundation.shape
import io.kontour.ui.motion.ScrollDefaults
import io.kontour.ui.nav.ModalNavRail
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
import io.kontour.ui.sheet.closeEnclosingSheet
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
        "Beside the main pane on a tablet, where back closes it. On a phone it is a sheet: Android's back " +
            "closes it; on iOS it is dragged down. Either way it has a close button rather than a back arrow: " +
            "it was presented, not gone to.",
    ),
    Sheet(
        "Bottom sheet",
        "On Android back slides the sheet down its own way out, and never reaches the page under it; one that " +
            "may not close does not move. On iOS back leaves every sheet open: drag it down.",
    ),
    SheetStack(
        "Sheet with a stack",
        "A stack inside the sheet goes back first, on both. With nothing left in it, Android's back closes the " +
            "sheet; iOS's leaves it open. Its first page closes the sheet with a button; the pages after it go back.",
    ),
    SideSheets(
        "Side sheet and drawer",
        "Back slides each out by its own slide, following the finger. On iOS that is a drag towards its edge: " +
            "the sheet anywhere on it, or from the left edge of the screen; the drawer by its own drag left.",
    ),
    Dialog(
        "Dialog",
        "On Android back closes the dialog by its own exit; one that must be answered does not move. On iOS back " +
            "never closes a dialog. Either way back does not reach the page behind.",
    ),
    Nested(
        "All of them",
        "A detail, a sheet with a stack over it, and a dialog over that. Back unwinds them in order — on iOS " +
            "stopping at the dialog and the sheet, which are answered and dragged away there.",
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
 *
 * The first page of something presented — a sheet, a supporting pane shown as
 * one — has nowhere to go back to *within* it, so it gets [onClose] instead: a
 * close button, which is what leaving it is.
 */
@Composable
private fun FramePage(
    title: String,
    onBack: (() -> Unit)?,
    rows: List<Pair<String, () -> Unit>>,
    note: String? = null,
    onClose: (() -> Unit)? = null,
    extra: @Composable () -> Unit = {},
) {
    Column(Modifier.fillMaxSize()) {
        TopBar(
            onBack = onBack,
            windowInsets = WindowInsets(0),
            actions = onClose?.let { close ->
                { IconButton(icon = SystemIcons.Close, contentDescription = Theme.strings.close, onClick = close) }
            },
        ) { +title }
        Column(
            Modifier
                .fillMaxSize()
                .pageScroll(flingBehavior = ScrollDefaults.flingBehavior())
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
                    extra = { SidewaysControls() },
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

/**
 * Things on a page that take a sideways drag of their own, under a page that
 * can go back — where the edge and the page's controls share one finger.
 *
 * From the very edge a swipe is back, whatever it starts on, as on iOS. From
 * anywhere else it is the control's: a row with actions on one side only still
 * gives under the finger the other way rather than letting the page go.
 */
@Composable
private fun SidewaysControls() {
    var level by remember { mutableFloatStateOf(0.4f) }
    val photos = rememberCarouselState { 3 }
    Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm)) {
        Text(
            "From the very edge, back. From anywhere else, the control.",
            style = Theme.typography.bodySmall,
            colour = Theme.colours.contentMuted,
        )
        // One group: a list item's gap apart, each swipe in its row's shape.
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.Spacing)) {
            SwipeActions(
                start = listOf(SwipeAction("Pin", SystemIcons.Check, onAction = {}, background = Theme.colours.success.solid)),
                end = listOf(SwipeAction("Remove", SystemIcons.Close, onAction = {}, background = Theme.colours.danger.solid)),
                shape = GroupPosition.First.shape(ListItemDefaults.Shape, ListItemDefaults.InnerCorner, Orientation.Vertical),
            ) {
                ListItem(position = GroupPosition.First) {
                    +"Next departure"
                    supporting { +"Actions on both sides" }
                }
            }
            SwipeActions(
                end = listOf(SwipeAction("Remove", SystemIcons.Close, onAction = {}, background = Theme.colours.danger.solid)),
                shape = GroupPosition.Last.shape(ListItemDefaults.Shape, ListItemDefaults.InnerCorner, Orientation.Vertical),
            ) {
                ListItem(position = GroupPosition.Last) {
                    +"Saved stop"
                    supporting { +"Actions on one side" }
                }
            }
        }
        Slider(value = level, onValueChange = { level = it }, modifier = Modifier.fillMaxWidth())
        Carousel(
            state = photos,
            contentDescription = "Stop photos",
            modifier = Modifier.fillMaxWidth().height(96.dp),
        ) { page ->
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = Theme.shapes.small,
                containerColour = Theme.colours.surfaceSunken,
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Photo ${page + 1}") }
            }
        }
    }
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
                // Presented beside the run, or over it as a sheet: closed, not
                // gone back from.
                FramePage("Conditions", onBack = null, onClose = pop, rows = emptyList(), note = "24 °C, dry, a light easterly.")
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
            SheetStack(inner, onReport = { report = true }, withDialog = withDialog, onDone = onClose)
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
) {
    val pop: () -> Unit = { backStack.removeLastOrNull() }
    NavDisplay(
        backStack = backStack,
        onBack = pop,
        sceneDecoratorStrategies = listOf(rememberPageTransitionStrategy()),
        entryProvider = entryProvider {
            entry<StopsRoute> {
                // The sheet's first page closes the sheet, with the sheet's own
                // animation where it has one; the pages after it go back.
                val close = closeEnclosingSheet() ?: onDone
                FramePage(
                    "Departures", onBack = null, onClose = close,
                    rows = listOf("950", "935").map { "12:04 · $it" to { backStack += ServiceRoute("Perth Busport", it) } },
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
    ModalNavRail(visible = drawer, onDismissRequest = { drawer = false }) {
        for (place in listOf("Stops", "Routes", "Alerts")) {
            item(selected = place == "Stops", onClick = {}, key = place, icon = null) { +place }
        }
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
