package io.kontour.ui.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.CurrentLocation
import com.composables.icons.tabler.outline.Star
import io.kontour.ui.components.action.Button
import io.kontour.ui.components.action.ButtonSize
import io.kontour.ui.components.action.ButtonVariant
import io.kontour.ui.components.action.IconButton
import io.kontour.ui.foundation.Surface
import io.kontour.ui.foundation.Text
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.sheet.BottomSheet
import io.kontour.ui.sheet.ModalBottomSheet
import io.kontour.ui.sheet.SheetDetent
import io.kontour.ui.sheet.SheetHeader
import io.kontour.ui.sheet.SheetSide
import io.kontour.ui.sheet.SideSheet
import io.kontour.ui.sheet.rememberSheetState
import io.kontour.ui.sheet.sheetPeekAnchor
import io.kontour.ui.theme.Theme

/**
 * A framed screen for a sheet to come into, with its own overlay host.
 *
 * The ground behind it is deliberately not blank: a non-modal sheet exists so
 * that what is underneath stays usable, and a sheet over nothing demonstrates
 * the opposite of the thing it is for.
 */
@Composable
private fun Screen(content: @Composable BoxScope.() -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(420.dp)
            .border(
                width = Theme.sizing.borderWidth,
                color = Theme.colors.outline,
                shape = Theme.shapes.medium,
            )
            .clip(Theme.shapes.medium),
        color = Theme.colors.surface,
    ) {
        OverlayHost(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().background(Theme.colors.surfaceSunken)) {
                Text(
                    text = "map",
                    modifier = Modifier.align(Alignment.TopCenter).padding(Theme.spacing.lg),
                    style = Theme.typography.monoLabel,
                    color = Theme.colors.contentSubtle,
                )
                content()
            }
        }
    }
}

@Composable
private fun Departures() {
    Column(
        modifier = Modifier.padding(
            start = Theme.spacing.md,
            end = Theme.spacing.md,
            bottom = Theme.spacing.lg,
        ),
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm),
    ) {
        repeat(5) { index ->
            Text(
                "${950 + index} to Elizabeth Quay — ${4 + index * 7} min",
                style = Theme.typography.bodyMedium,
            )
        }
    }
}

internal val BottomSheetDemo = ComponentDemo(slug = "bottom-sheet") {
    val sheet = rememberSheetState(
        detents = listOf(
            SheetDetent.Hidden,
            SheetDetent.peek(140.dp),
            SheetDetent.Half,
            SheetDetent.Expanded,
        ),
        initialDetent = SheetDetent.Hidden,
    )
    // Opened to the peek, so the demo starts where a real screen would rather
    // than at Hidden, which draws nothing at all.
    LaunchedEffect(Unit) { sheet.animateTo(SheetDetent.peek(140.dp)) }

    Screen {
        BottomSheet(
            state = sheet,
            actions = {
                IconButton(
                    icon = Tabler.Outline.CurrentLocation,
                    contentDescription = "Recentre",
                    onClick = { echo("Recentre") },
                    variant = ButtonVariant.Secondary,
                )
            },
        ) {
            SheetHeader(
                modifier = Modifier.sheetPeekAnchor(),
                actions = {
                    IconButton(
                        icon = Tabler.Outline.Star,
                        contentDescription = "Add to favourites",
                        onClick = { echo("Favourited") },
                    )
                },
            ) {
                +"Perth Underground"
                supporting { +"Platform 2 · Joondalup line" }
            }
            Departures()
        }
    }
}

internal val ModalBottomSheetDemo = ComponentDemo(slug = "modal-bottom-sheet") {
    var open by remember { mutableStateOf(false) }
    Screen {
        Button(
            onClick = { open = true },
            variant = ButtonVariant.Secondary,
            modifier = Modifier.align(Alignment.Center),
        ) { +"Rename favourite" }

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
                Text(
                    "Give it a name you will recognise on the home screen.",
                    style = Theme.typography.bodySmall,
                    color = Theme.colors.contentMuted,
                )
                Button(
                    onClick = { open = false; echo("Saved") },
                    modifier = Modifier.fillMaxWidth(),
                ) { +"Save" }
            }
        }
    }
}

private val sheetSide = Knob.Choice("Side", SheetSide.entries.toList(), SheetSide.Start)

internal val SideSheetDemo = ComponentDemo(slug = "side-sheet", knobs = listOf(sheetSide)) {
    var open by remember { mutableStateOf(false) }
    val side = this[sheetSide]
    Screen {
        Button(
            onClick = { open = true },
            variant = ButtonVariant.Secondary,
            modifier = Modifier.align(Alignment.Center),
        ) { +"Filters" }

        SideSheet(
            visible = open,
            onDismissRequest = { open = false },
            side = side,
            width = 240.dp,
            onBack = { open = false },
        ) {
            SheetHeader { +"Filters" }
            Column(
                modifier = Modifier.padding(horizontal = Theme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
            ) {
                Text("Only show routes that", style = Theme.typography.labelMedium)
                Text(
                    "run in the next hour",
                    style = Theme.typography.bodySmall,
                    color = Theme.colors.contentMuted,
                )
            }
        }
    }
}

internal val SheetHeaderDemo = ComponentDemo(slug = "sheet-header") {
    var open by remember { mutableStateOf(true) }
    Screen {
        if (!open) {
            Button(
                onClick = { open = true },
                variant = ButtonVariant.Secondary,
                size = ButtonSize.Small,
                modifier = Modifier.align(Alignment.Center),
            ) { +"Open it again" }
        }
        ModalBottomSheet(visible = open, onDismissRequest = { open = false }) {
            SheetHeader(
                actions = {
                    IconButton(
                        icon = Tabler.Outline.Star,
                        contentDescription = "Add to favourites",
                        onClick = { echo("Favourited") },
                    )
                },
            ) {
                +"Perth Underground"
                supporting { +"Platform 2 · Joondalup line" }
            }
            Departures()
        }
    }
}

internal val sheetDemos = listOf(
    BottomSheetDemo,
    ModalBottomSheetDemo,
    SideSheetDemo,
    SheetHeaderDemo,
)
