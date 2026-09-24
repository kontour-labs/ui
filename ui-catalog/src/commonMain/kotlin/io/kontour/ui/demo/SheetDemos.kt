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
import io.kontour.ui.overlay.OverlayAlignment
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.sheet.BottomSheet
import io.kontour.ui.sheet.DragHandle
import io.kontour.ui.sheet.ModalBottomSheet
import io.kontour.ui.sheet.SheetDetent
import io.kontour.ui.sheet.SheetHeader
import io.kontour.ui.sheet.SheetHeaderStyle
import io.kontour.ui.sheet.SheetEdgeMorph
import io.kontour.ui.sheet.SheetPresentation
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
                color = Theme.colours.outline,
                shape = Theme.shapes.medium,
            )
            .clip(Theme.shapes.medium),
        colour = Theme.colours.surface,
    ) {
        OverlayHost(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().background(Theme.colours.surfaceSunken)) {
                Text(
                    text = "map",
                    modifier = Modifier.align(Alignment.TopCenter).padding(Theme.spacing.lg),
                    style = Theme.typography.eyebrow,
                    colour = Theme.colours.contentSubtle,
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

/**
 * Flush to the window's edges, or floating clear of them.
 *
 * Worth a knob rather than a second demo because the two are the same sheet: the
 * detents, the drag, the peek anchor and the floating controls are all unchanged,
 * and what moves is three edges and four corners.
 */
private val sheetPresentation =
    Knob.Choice("Presentation", SheetPresentation.entries.toList(), SheetPresentation.Edge)

/**
 * Where the sheet sits once the card is wider than the sheet's 640dp cap.
 *
 * **Does nothing on a phone, and that is the feature.** Below the cap the sheet
 * is the card, and there is nowhere for it to go; the knob moves something only
 * in a window wide enough for the cap to bind, which on this page means the
 * desktop catalog. A reader on a phone pressing it and seeing no change is being
 * shown the rule that a phone is untouched.
 */
private val sheetAlignment =
    Knob.Choice("Align", OverlayAlignment.entries.toList(), OverlayAlignment.Center)

/**
 * Whether a floating sheet becomes an edge sheet as it is pulled up to its top
 * detent — the default — or stays a floating panel at every size.
 *
 * Does nothing at `Edge`, which is what the morph arrives at. Drag the sheet from
 * Half to its full height with this on and the margins close up as it goes.
 */
private val sheetEdgeMorph = Knob.Flag("Expands to edge", initial = true)

/** Which end of the sheet's own top edge the floating controls gather at. */
private val sheetControlsAlignment =
    Knob.Choice("Controls", OverlayAlignment.entries.toList(), OverlayAlignment.End)

internal val BottomSheetDemo = ComponentDemo(
    slug = "bottom-sheet",
    knobs = listOf(sheetPresentation, sheetEdgeMorph, sheetAlignment, sheetControlsAlignment),
) {
    val presentation = this[sheetPresentation]
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
            presentation = presentation,
            edgeMorph = if (this@ComponentDemo[sheetEdgeMorph]) SheetEdgeMorph() else null,
            alignment = this@ComponentDemo[sheetAlignment],
            floatingControlsAlignment = this@ComponentDemo[sheetControlsAlignment],
            floatingControls = {
                IconButton(
                    icon = Tabler.Outline.CurrentLocation,
                    contentDescription = "Recentre",
                    onClick = { echo("Recentre") },
                    variant = ButtonVariant.Secondary,
                )
            },
        ) {
            // Always there: it is what the peek detent is peeking *at*.
            part {
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
            }
            // And the board, below it. The peek is anchored to the header, so the
            // board starts exactly at the sheet's edge — it is drawn, off the
            // bottom of the window, and a drag uncovers it at the speed of the
            // finger rather than announcing itself at a detent. `from` says which
            // size it belongs to, which is what keeps a screen reader out of it
            // until the sheet is that size.
            part(from = SheetDetent.Half) { Departures() }
        }
    }
}

/**
 * Whether a press outside closes it.
 *
 * Off is the sheet you cannot leave by tapping the scrim — a rename that has to
 * be finished or cancelled. Every demo carrying this knob keeps a button inside
 * the sheet as well, because a reader who turns it off and finds no way back out
 * has hit a trap rather than a demonstration.
 */
private val sheetDismissible = Knob.Flag("Dismissible", initial = true)

/**
 * Whether the sheet is already up, defaulting to **yes**.
 *
 * A modal sheet at rest is a button, and a picture of a button is not a picture
 * of a sheet. The panel this demo replaced opened its modal deliberately for
 * that reason — its own note said "catch them open and the scrim, the close
 * button and the swipe" — and deleting the panel took the only image in the
 * repository of a scrim, of a sheet's shadow against a dimmed ground, and of
 * the halo ring `Backdrop`'s KDoc describes. The knob puts them back, and
 * unlike the panel it is also a control a reader can work.
 */
private val sheetOpen = Knob.Flag("Open", initial = true)

internal val ModalBottomSheetDemo = ComponentDemo(
    slug = "modal-bottom-sheet",
    knobs = listOf(sheetOpen, sheetDismissible, sheetAlignment),
) {
    // Keyed on the knob, so toggling it *resets* the sheet rather than fighting
    // it. The sheet has two inputs — the knob and its own dismissal — and one
    // of them has to win; making the knob a reset means a sheet swiped away
    // comes back on the next toggle, which is the behaviour a reader expects
    // from a control labelled "Open". The button below reopens it directly.
    var open by remember(this[sheetOpen]) { mutableStateOf(this[sheetOpen]) }
    val dismissible = this[sheetDismissible]
    Screen {
        Button(
            onClick = { open = true },
            variant = ButtonVariant.Secondary,
            modifier = Modifier.align(Alignment.Center),
        ) { +"Rename favourite" }

        ModalBottomSheet(
            visible = open,
            onDismissRequest = { open = false },
            alignment = this@ComponentDemo[sheetAlignment],
            dismissible = dismissible,
        ) {
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
                    colour = Theme.colours.contentMuted,
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

/** Flush to its edge, or a panel floating clear of three of them. */
private val sideSheetPresentation =
    Knob.Choice("Presentation", SheetPresentation.entries.toList(), SheetPresentation.Edge)

/**
 * A grip on the sheet's inner edge that drags it out to the whole window. On a
 * phone the card is barely wider than the sheet, so there is little to drag across
 * — which is the rule for a window with nothing to expand into.
 */
private val sideSheetExpandable = Knob.Flag("Expandable", initial = true)

/** Whether a floating sheet becomes an edge sheet as it widens. */
private val sideSheetEdgeMorph = Knob.Flag("Expands to edge", initial = true)

internal val SideSheetDemo = ComponentDemo(
    slug = "side-sheet",
    knobs = listOf(sheetSide, sideSheetPresentation, sideSheetExpandable, sideSheetEdgeMorph, sheetDismissible),
) {
    var open by remember { mutableStateOf(false) }
    val side = this[sheetSide]
    val dismissible = this[sheetDismissible]
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
            presentation = this@ComponentDemo[sideSheetPresentation],
            expandable = this@ComponentDemo[sideSheetExpandable],
            edgeMorph = this@ComponentDemo[sideSheetEdgeMorph],
            dismissible = dismissible,
            onBack = { open = false },
        ) {
            // The header's own close button is the way out when the scrim is
            // not one — `onClose` defaults to `closeEnclosingSheet()`.
            SheetHeader { +"Filters" }
            Column(
                modifier = Modifier.padding(horizontal = Theme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
            ) {
                Text("Only show routes that", style = Theme.typography.labelMedium)
                Text(
                    "run in the next hour",
                    style = Theme.typography.bodySmall,
                    colour = Theme.colours.contentMuted,
                )
            }
        }
    }
}

private val headerStyle =
    Knob.Choice("Style", SheetHeaderStyle.entries.toList(), SheetHeaderStyle.Small)

internal val SheetHeaderDemo = ComponentDemo(
    slug = "sheet-header",
    knobs = listOf(headerStyle),
) {
    var open by remember { mutableStateOf(true) }
    val style = this[headerStyle]
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
                style = style,
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

internal val DragHandleDemo = ComponentDemo(slug = "drag-handle") {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm),
    ) {
        // No `LocalSheetState`, so this is the resting pill rather than the
        // draggable one — a handle is drawn by the sheet, not dragged on its own.
        DragHandle(state = null)
        Text(
            "Drawn by a sheet, not draggable on its own",
            style = Theme.typography.labelSmall,
            colour = Theme.colours.contentMuted,
        )
    }
}

internal val sheetDemos = listOf(
    BottomSheetDemo,
    ModalBottomSheetDemo,
    SideSheetDemo,
    SheetHeaderDemo,
    DragHandleDemo,
)
