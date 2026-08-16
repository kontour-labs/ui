package io.kontour.ui.samples

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.ChevronDown
import com.composables.icons.tabler.outline.CurrentLocation
import com.composables.icons.tabler.outline.Minus
import com.composables.icons.tabler.outline.Navigation
import com.composables.icons.tabler.outline.Plus
import com.composables.icons.tabler.outline.Stack
import com.composables.icons.tabler.outline.Star
import com.composables.icons.tabler.outline.Trash
import com.composables.icons.tabler.outline.X
import io.kontour.ui.components.action.Button
import io.kontour.ui.components.action.ButtonGroup
import io.kontour.ui.components.action.ButtonSize
import io.kontour.ui.components.action.ButtonVariant
import io.kontour.ui.components.action.ExtendedFloatingActionButton
import io.kontour.ui.components.action.FloatingActionButton
import io.kontour.ui.components.action.IconButton
import io.kontour.ui.components.action.IconToggleButton
import io.kontour.ui.components.display.Spinner
import io.kontour.ui.components.action.Toolbar
import io.kontour.ui.components.action.ToolbarDivider

@Composable
fun ButtonBasics() {
    Button(onClick = { plan() }) {
        +"Plan a trip"
    }

    Button(
        onClick = { delete() },
        variant = ButtonVariant.Destructive,
        size = ButtonSize.Small,
    ) {
        +Tabler.Outline.Trash
        +"Delete"
    }
}

@Composable
fun ButtonLoading() {
    var saving by remember { mutableStateOf(false) }

    Button(onClick = { saving = true }, loading = saving) {
        +"Save this trip"
    }
}

@Composable
fun IconButtonBasics() {
    IconButton(Tabler.Outline.X, contentDescription = "Close", onClick = { dismiss() })
}

@Composable
fun IconButtonRotation() {
    var expanded by remember { mutableStateOf(false) }

    IconButton(
        icon = Tabler.Outline.ChevronDown,
        contentDescription = if (expanded) "Collapse" else "Expand",
        onClick = { expanded = !expanded },
        rotation = if (expanded) 180f else 0f,
    )
}

@Composable
fun IconToggleButtonBasics() {
    var favourite by remember { mutableStateOf(false) }

    IconToggleButton(
        icon = Tabler.Outline.Star,
        contentDescription = "Favourite",
        checked = favourite,
        onCheckedChange = { favourite = it },
    )
}

@Composable
fun FloatingActionButtonBasics() {
    FloatingActionButton(Tabler.Outline.Plus, "Add favourite", onClick = { add() })
}

@Composable
fun ExtendedFloatingActionButtonCollapsing(listState: LazyListState) {
    ExtendedFloatingActionButton(
        icon = Tabler.Outline.Navigation,
        contentDescription = "Start trip to Perth Station",
        expanded = listState.firstVisibleItemIndex == 0,
        onClick = { start() },
    ) {
        +"Start"
    }
}

@Composable
fun ButtonGroupBasics() {
    ButtonGroup {
        item(onClick = { zoomOut() }, contentDescription = "Zoom out", icon = Tabler.Outline.Minus)
        item(
            onClick = { recentre() },
            contentDescription = "Recentre",
            icon = Tabler.Outline.CurrentLocation,
        )
        item(onClick = { zoomIn() }, contentDescription = "Zoom in", icon = Tabler.Outline.Plus)
    }
}

@Composable
fun ToolbarBasics() {
    Toolbar {
        ButtonGroup {
            item(
                onClick = { zoomOut() },
                contentDescription = "Zoom out",
                icon = Tabler.Outline.Minus,
            )
            item(onClick = { zoomIn() }, contentDescription = "Zoom in", icon = Tabler.Outline.Plus)
        }
        ToolbarDivider()
        IconButton(Tabler.Outline.Stack, "Map layers", onClick = { openLayers() })
    }
}

@Composable
fun SpinnerBasics() {
    Spinner(contentDescription = "Loading departures")
}
