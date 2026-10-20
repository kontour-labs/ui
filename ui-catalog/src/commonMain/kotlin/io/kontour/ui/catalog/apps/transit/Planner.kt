package io.kontour.ui.catalog.apps.transit

import io.kontour.ui.components.list.ListGroup
import io.kontour.icons.tabler.outline.CurrentLocation
import io.kontour.icons.tabler.outline.MapPin
import io.kontour.ui.sheet.SheetPartPin
import androidx.compose.foundation.layout.PaddingValues
import io.kontour.ui.catalog.pageScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.kontour.icons.tabler.Tabler
import io.kontour.icons.tabler.outline.ArrowsUpDown
import io.kontour.ui.catalog.apps.rememberAppToast
import io.kontour.ui.components.action.Button
import io.kontour.ui.components.action.IconButton
import io.kontour.ui.components.datetime.DatePicker
import io.kontour.ui.components.datetime.TimePicker
import io.kontour.ui.components.display.StepProgress
import io.kontour.ui.components.list.ListItem
import io.kontour.ui.components.list.ListItemDefaults
import io.kontour.ui.components.selection.SegmentedControl
import io.kontour.ui.foundation.GroupPosition
import io.kontour.ui.foundation.Text
import io.kontour.ui.sheet.ModalBottomSheet
import io.kontour.ui.sheet.SheetDetent
import io.kontour.ui.sheet.SheetHeader
import io.kontour.ui.sheet.rememberSheetState
import io.kontour.ui.sheet.sheetPeekAnchor
import io.kontour.ui.theme.Theme
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * Planning a trip, in a sheet of parts.
 *
 * **What shows at each size is what that size is for.** Peeking, the sheet is
 * its header and the two places: enough to see where the trip goes. At half,
 * when to leave and the routes found; expanded, the calendar and the clock, for
 * a trip that is not now. The parts are laid out in place and the sheet's edge
 * uncovers them, so dragging it up reveals the next part at the speed of the
 * finger; `from` keeps each out of a screen reader's way until the sheet is
 * that size. **Find routes is pinned** to the bottom of what the sheet shows,
 * at every size and through every frame of a drag.
 */
@Composable
internal fun PlannerSheet(visible: Boolean, onDismiss: () -> Unit) {
    val toast = rememberAppToast()
    val peek = remember { SheetDetent.peek(PeekFallback) }
    val sheet = rememberSheetState(
        detents = listOf(SheetDetent.Hidden, peek, SheetDetent.Half, SheetDetent.Expanded),
        initialDetent = SheetDetent.Hidden,
    )
    val from = rememberTextFieldState("Glendalough")
    val to = rememberTextFieldState()
    var arriving by remember { mutableIntStateOf(0) }
    var date by remember { mutableStateOf<LocalDate?>(TransitToday) }
    var time by remember { mutableStateOf(LocalTime(8, 15)) }
    var finding by remember { mutableStateOf(false) }
    var found by remember { mutableStateOf(false) }

    LaunchedEffect(finding) {
        if (!finding) return@LaunchedEffect
        delay(FindMillis)
        finding = false
        found = true
        toast("Three routes found")
    }

    ModalBottomSheet(
        visible = visible,
        onDismissRequest = onDismiss,
        state = sheet,
    ) { padding ->
        // At the foot of the sheet whatever size it is, as a navigation bar is.
        part(pin = SheetPartPin.Bottom) {
            Button(
                onClick = { finding = true },
                loading = finding,
                modifier = Modifier.fillMaxWidth().testTag("find-routes"),
            ) { +"Find routes" }
        }
        // Inside a scroller, so a short phone reaches the clock under the
        // calendar; the parts are still the sheet's, named through its scope.
        val sheetParts = this
        Column(Modifier.pageScroll(barPadding = PaddingValues()).padding(padding)) {
            sheetParts.part {
                SheetHeader(modifier = Modifier.sheetPeekAnchor()) {
                    +"Plan a trip"
                    supporting { +"From anywhere on the network" }
                }
                Row(
                    Modifier.padding(horizontal = Theme.spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
                ) {
                    // The two places as rows of one group, typed into in place.
                    ListGroup(Modifier.weight(1f)) {
                        field(from, label = "From", icon = Tabler.Outline.CurrentLocation)
                        field(to, label = "To", placeholder = "Station, stop or address", icon = Tabler.Outline.MapPin)
                    }
                    IconButton(
                        icon = Tabler.Outline.ArrowsUpDown,
                        contentDescription = "Swap from and to",
                        onClick = {
                            val there = to.text.toString()
                            to.setTextAndPlaceCursorAtEnd(from.text.toString())
                            from.setTextAndPlaceCursorAtEnd(there)
                        },
                    )
                }
            }
            sheetParts.part(from = SheetDetent.Half) {
                Column(
                    Modifier.padding(Theme.spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm),
                ) {
                    SegmentedControl(
                        options = listOf("Depart at", "Arrive by"),
                        selectedIndex = arriving,
                        onSelectedIndexChange = { arriving = it },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "${date?.let { Theme.formats.date(it) } ?: "Today"} at ${Theme.formats.time(time)}",
                        style = Theme.typography.bodyMedium,
                    )
                    if (found) Routes(towards = to.text.ifEmpty { "Elizabeth Quay" }.toString())
                }
            }
            sheetParts.part(from = SheetDetent.Expanded) {
                Column(
                    Modifier.padding(horizontal = Theme.spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Theme.spacing.md),
                ) {
                    DatePicker(
                        value = date,
                        onValueChange = { date = it },
                        today = TransitToday,
                        // In the middle of a sheet wider than the picker.
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        // The timetable does not go back, so neither does the planner.
                        isDateSelectable = { it >= TransitToday },
                    )
                    TimePicker(value = time, onValueChange = { time = it }, minuteStep = 5)
                }
            }
        }
    }
}

/** Three ways there, each with its legs drawn as steps. */
@Composable
private fun Routes(towards: String) {
    val options = listOf(
        Triple("Train, then the 950", "24 min · leave 08:15", 2),
        Triple("Train all the way", "31 min · leave 08:20", 1),
        Triple("Two buses", "38 min · leave 08:12", 3),
    )
    Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.xxs)) {
        Text("To $towards", style = Theme.typography.titleSmall)
        // The rows a list item's gap apart, as a group's are; the title keeps
        // its own space above them.
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.Spacing)) {
            options.forEachIndexed { index, (title, detail, legs) ->
                ListItem(position = GroupPosition.of(index, options.size)) {
                    +title
                    supporting { +detail }
                    trailing {
                        StepProgress(
                            current = legs,
                            total = legs,
                            modifier = Modifier.padding(start = Theme.spacing.sm).width(LegsWidth),
                            contentDescription = if (legs == 1) "One leg" else "$legs legs",
                        )
                    }
                }
            }
        }
    }
}

/** Where the sheet peeks before its header has been measured. */
private val PeekFallback = 200.dp
private val LegsWidth = 64.dp
private const val FindMillis = 1_400L
