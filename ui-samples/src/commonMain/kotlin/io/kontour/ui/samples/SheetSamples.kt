package io.kontour.ui.samples

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.CurrentLocation
import com.composables.icons.tabler.outline.Star
import io.kontour.ui.components.action.Button
import io.kontour.ui.components.action.ButtonVariant
import io.kontour.ui.components.action.IconButton
import io.kontour.ui.foundation.Text
import io.kontour.ui.sheet.BottomSheet
import io.kontour.ui.sheet.DragHandle
import io.kontour.ui.sheet.ModalBottomSheet
import io.kontour.ui.sheet.SheetDetent
import io.kontour.ui.sheet.SheetHeader
import io.kontour.ui.sheet.SheetSide
import io.kontour.ui.sheet.SideSheet
import io.kontour.ui.sheet.rememberSheetState
import io.kontour.ui.sheet.sheetPeekAnchor
import io.kontour.ui.theme.Theme

@Composable
fun BottomSheetBasics() {
    val sheet = rememberSheetState(
        detents = listOf(
            SheetDetent.Hidden,
            SheetDetent.peek(140.dp),
            SheetDetent.Half,
            SheetDetent.Expanded,
        ),
        initialDetent = SheetDetent.peek(140.dp),
    )

    BottomSheet(
        state = sheet,
        // Controls that ride up with the sheet rather than being covered by
        // it — the map's "recentre" button is the case this is for.
        floatingControls = {
            IconButton(
                icon = Tabler.Outline.CurrentLocation,
                contentDescription = "Recentre",
                onClick = { recentre() },
                variant = ButtonVariant.Secondary,
            )
        },
    ) {
        // `sheetPeekAnchor` is what makes `peek` mean "as tall as this", so the
        // peek height follows the header instead of being a number to maintain.
        SheetHeader(modifier = Modifier.sheetPeekAnchor()) {
            +"Perth Underground"
            supporting { +"Platform 2 · Joondalup line" }
        }
        Departures()
    }
}

@Composable
fun ModalBottomSheetBasics() {
    var open by remember { mutableStateOf(false) }

    Button(onClick = { open = true }) { +"Rename favourite" }

    ModalBottomSheet(visible = open, onDismissRequest = { open = false }) {
        SheetHeader {
            +"Rename favourite"
            supporting { +"Perth Underground" }
        }
        Column(
            modifier = Modifier.padding(
                start = Theme.spacing.md,
                end = Theme.spacing.md,
                bottom = Theme.spacing.lg,
            ),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm),
        ) {
            Button(onClick = { save(); open = false }, modifier = Modifier.fillMaxWidth()) {
                +"Save"
            }
        }
    }
}

@Composable
fun SideSheetBasics() {
    var open by remember { mutableStateOf(false) }

    SideSheet(
        visible = open,
        onDismissRequest = { open = false },
        side = SheetSide.End,
        // Given a back arrow, the sheet becomes a second level rather than a
        // dead end — the filters open, and closing them returns to the list.
        onBack = { open = false },
    ) {
        SheetHeader { +"Filters" }
        Column(
            modifier = Modifier.padding(horizontal = Theme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
        ) {
            Text("Only show routes that run in the next hour")
        }
    }
}

@Composable
fun SheetHeaderBasics() {
    SheetHeader(
        actions = {
            IconButton(
                icon = Tabler.Outline.Star,
                contentDescription = "Add to favourites",
                onClick = { save() },
            )
        },
    ) {
        +"Perth Underground"
        supporting { +"Platform 2 · Joondalup line" }
    }
}

@Composable
fun DragHandleBasics() {
    val sheet = rememberSheetState(
        detents = listOf(SheetDetent.peek(140.dp), SheetDetent.Expanded),
        initialDetent = SheetDetent.peek(140.dp),
    )

    // Every sheet draws one already. Pass `dragHandle` only to replace it, or
    // `null` to take it away — a sheet with `draggable = false` should not
    // advertise a gesture it does not have.
    BottomSheet(state = sheet, dragHandle = { DragHandle(state = sheet) }) {
        SheetHeader(modifier = Modifier.sheetPeekAnchor()) { +"Perth Underground" }
        Departures()
    }
}
