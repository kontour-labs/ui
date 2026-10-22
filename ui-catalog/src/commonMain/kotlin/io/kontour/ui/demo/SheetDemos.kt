package io.kontour.ui.demo

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import io.kontour.icons.tabler.outline.Check
import io.kontour.icons.tabler.outline.Train
import io.kontour.ui.catalog.apps.transit.DepartureListItem
import io.kontour.ui.catalog.apps.transit.Mode
import io.kontour.ui.catalog.apps.transit.departures
import io.kontour.ui.catalog.apps.transit.stops
import io.kontour.ui.catalog.pageScroll
import io.kontour.ui.components.display.Banner
import io.kontour.ui.components.list.ListSection
import io.kontour.ui.components.selection.ChipGroup
import io.kontour.ui.components.selection.FilterChip
import io.kontour.ui.components.selection.SegmentedControl
import io.kontour.ui.components.selection.SelectionRow
import io.kontour.ui.components.selection.Switch
import io.kontour.ui.components.text.TextField
import io.kontour.ui.theme.Tone
import io.kontour.ui.sheet.SheetPartPin
import io.kontour.ui.components.list.ListItem
import io.kontour.ui.components.list.ListItemDefaults
import io.kontour.ui.components.text.SearchField
import androidx.compose.runtime.key
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.kontour.icons.tabler.Tabler
import io.kontour.icons.tabler.outline.CurrentLocation
import io.kontour.icons.tabler.outline.Star
import io.kontour.ui.components.action.Button
import io.kontour.ui.components.action.ButtonSize
import io.kontour.ui.components.action.ButtonVariant
import io.kontour.ui.components.action.IconButton
import io.kontour.ui.foundation.GroupPosition
import io.kontour.ui.foundation.Surface
import io.kontour.ui.foundation.Text
import io.kontour.ui.overlay.OverlayAlignment
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.sheet.BottomSheet
import io.kontour.ui.sheet.BottomSheetDefaults
import io.kontour.ui.sheet.DragHandle
import io.kontour.ui.sheet.ModalBottomSheet
import io.kontour.ui.sheet.ModalSideSheet
import io.kontour.ui.sheet.SheetDetent
import io.kontour.ui.sheet.SheetEdgeMorph
import io.kontour.ui.sheet.SheetHeader
import io.kontour.ui.sheet.SheetHeaderStyle
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
    // Tall enough that a sheet at `Half` still leaves the page behind it
    // readable, and a modal sheet's content is not cut off on a phone.
    DemoWindow(Modifier.fillMaxWidth().height(520.dp)) {
        MapGround()
        content()
    }
}

/**
 * A street map for the sheets to sit over, as a transit or maps app's sheet
 * does: blocks of streets, a park, the river, the line through the station and
 * the station itself. Drawn in the theme's colours, so it reads in light and
 * dark, and quiet enough that a sheet over it is the thing being shown.
 */
@Composable
private fun MapGround() {
    val colours = Theme.colours
    // In a layer of its own and drawn from a cache, so nothing the sheet over it
    // does — moving, morphing, its shadow — makes the map draw again.
    Spacer(
        Modifier
            .fillMaxSize()
            .clearAndSetSemantics {}
            .graphicsLayer()
            .drawWithCache {
                val w = size.width
                val h = size.height
                val river = Path().apply {
                    moveTo(0f, h * 0.72f)
                    cubicTo(w * 0.3f, h * 0.64f, w * 0.55f, h * 0.86f, w, h * 0.74f)
                    lineTo(w, h * 0.86f)
                    cubicTo(w * 0.55f, h * 0.98f, w * 0.3f, h * 0.76f, 0f, h * 0.84f)
                    close()
                }
                val line = Path().apply {
                    moveTo(w * 0.05f, h * 0.05f)
                    lineTo(w * 0.30f, h * 0.26f)
                    quadraticTo(w * 0.42f, h * 0.36f, w * 0.56f, h * 0.38f)
                    lineTo(w * 0.98f, h * 0.44f)
                }
                val minor = 3.dp.toPx()
                val major = 7.dp.toPx()
                val station = Offset(w * 0.44f, h * 0.365f)
                onDrawBehind {
                    drawRect(colours.surfaceSunken)
                    // A park, and the river along the south of the city.
                    drawRoundRect(
                        colours.success.container,
                        topLeft = Offset(w * 0.58f, h * 0.10f),
                        size = Size(w * 0.30f, h * 0.18f),
                        cornerRadius = CornerRadius(12.dp.toPx()),
                    )
                    drawPath(river, colours.info.container)
                    // Streets: a grid, a little off square, and two main roads across it.
                    for (x in StreetsDown) drawLine(colours.surface, Offset(w * x, 0f), Offset(w * (x + 0.04f), h), minor)
                    for (y in StreetsAcross) drawLine(colours.surface, Offset(0f, h * y), Offset(w, h * (y - 0.03f)), minor)
                    drawLine(colours.surface, Offset(0f, h * 0.43f), Offset(w, h * 0.30f), major, StrokeCap.Round)
                    drawLine(colours.surface, Offset(w * 0.40f, 0f), Offset(w * 0.47f, h), major, StrokeCap.Round)
                    // The line, and the station on it.
                    drawPath(line, colours.accent.solid, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
                    drawCircle(colours.surface, 9.dp.toPx(), station)
                    drawCircle(colours.accent.solid, 9.dp.toPx(), station, style = Stroke(3.dp.toPx()))
                }
            },
    )
}

private val StreetsDown = floatArrayOf(0.12f, 0.31f, 0.50f, 0.69f, 0.88f)
private val StreetsAcross = floatArrayOf(0.08f, 0.22f, 0.36f, 0.50f, 0.64f)

/**
 * The stop's next departures, as the Transit app's board shows them: each with
 * its mode, platform or stand, minutes, and whether it is running to time.
 */
@Composable
private fun Departures(onPick: (String) -> Unit) {
    Column(
        modifier = Modifier.padding(
            start = BottomSheetDefaults.ContentPadding,
            end = BottomSheetDefaults.ContentPadding,
            bottom = BottomSheetDefaults.ContentPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.Spacing),
    ) {
        // All twelve, so the board is taller than the 520dp frame at every size
        // and scrolls inside the sheet as a real one does — and the sheet's full
        // height is a step above `Half`, which the expand-to-edge morph runs across.
        departures.forEachIndexed { index, departure ->
            DepartureListItem(
                departure = departure,
                position = GroupPosition.of(index, departures.size),
                onClick = { onPick("${departure.route} to ${departure.towards}") },
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
    Knob.Choice("Presentation", SheetPresentation.entries.toList(), SheetPresentation.Floating)

/**
 * Where the sheet sits once the card is wider than the sheet's 480dp cap.
 *
 * **Does nothing on a phone, and that is the feature.** Below the cap the sheet
 * is the card, and there is nowhere for it to go; the knob moves something only
 * in a window wide enough for the cap to bind, which on this page means the
 * desktop catalog. A reader on a phone pressing it and seeing no change is being
 * shown the rule that a phone is untouched.
 */
private val sheetAlignment =
    Knob.Choice("Align", OverlayAlignment.entries.toList(), OverlayAlignment.Centre)

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

/**
 * A search field pinned to the sheet, over a list of stops — and collapsed,
 * the sheet is only the field. `Bottom` keeps it at the foot as a navigation
 * bar is, `Top` under the handle as a maps app has it, and `Collapsed` at the
 * head of the list, carried to the top as the sheet collapses. `None` is the
 * stop's header and departure board instead.
 *
 * The sheet's detents change with it, to `SheetDetent.parts()` in place of the
 * peek, so the knob starts the sheet over rather than bending the one already
 * open.
 */
private val sheetSearchBar = Knob.Choice("Search bar", SheetPartPin.entries.toList(), SheetPartPin.None)

/**
 * No fade under the field, so the list shows round it. Does nothing without
 * the field, or with it in the list.
 */
private val sheetNoFade = Knob.Flag("No fade under it")

internal val BottomSheetDemo = ComponentDemo(
    slug = "bottom-sheet",
    knobs = listOf(sheetPresentation, sheetEdgeMorph, sheetAlignment, sheetControlsAlignment, sheetSearchBar, sheetNoFade),
) {
    val presentation = this[sheetPresentation]
    val searchBar = this[sheetSearchBar]
    val search = searchBar != SheetPartPin.None
    // Where it rests collapsed: the header, or the search field.
    val collapsed = if (search) SheetDetent.parts() else SheetDetent.peek(140.dp)
    val sheet = key(searchBar) {
        rememberSheetState(
            detents = listOf(
                SheetDetent.Hidden,
                collapsed,
                SheetDetent.Half,
                SheetDetent.Expanded,
            ),
            initialDetent = SheetDetent.Hidden,
        )
    }
    val query = rememberTextFieldState()
    // Opened to the peek, so the demo starts where a real screen would rather
    // than at Hidden, which draws nothing at all.
    LaunchedEffect(sheet) { sheet.animateTo(collapsed) }

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
            fadeUnder = !this@ComponentDemo[sheetNoFade],
        ) { padding ->
            if (search) {
                val field: @Composable ColumnScope.() -> Unit = {
                    SearchField(
                        query,
                        placeholder = "Search stops",
                        // In from the sheet's sides, as the stops are; the foot's own inset is the sheet's.
                        modifier = if (searchBar == SheetPartPin.Bottom) {
                            Modifier
                        } else {
                            Modifier.padding(horizontal = BottomSheetDefaults.ContentPadding, vertical = Theme.spacing.xs)
                        },
                    )
                }
                // At the foot or the top, the field is the sheet's and the list
                // scrolls under it, its padding grown by the field's height.
                if (searchBar != SheetPartPin.Collapsed) part(pin = searchBar, content = field)
                val sheetParts = this
                LazyColumn(
                    modifier = Modifier.padding(horizontal = BottomSheetDefaults.ContentPadding),
                    contentPadding = padding,
                    verticalArrangement = Arrangement.spacedBy(ListItemDefaults.Spacing),
                ) {
                    // At the head of the list, where it scrolls away with the
                    // stops, and comes back to the top as the sheet collapses.
                    if (searchBar == SheetPartPin.Collapsed) {
                        item(key = "search") { sheetParts.part(pin = SheetPartPin.Collapsed, content = field) }
                    }
                    // The network's stations, narrowed as the reader types.
                    val typed = query.text.trim()
                    val found = if (typed.isEmpty()) stops else stops.filter { it.name.contains(typed, ignoreCase = true) }
                    itemsIndexed(found, key = { _, stop -> stop.name }) { index, stop ->
                        ListItem(onClick = { echo(stop.name) }, position = GroupPosition.of(index, found.size)) {
                            leading { +Tabler.Outline.Train }
                            +stop.name
                            supporting { +"Zone ${stop.zone} · ${stop.lines.joinToString { it.name }}" }
                        }
                    }
                }
                return@BottomSheet
            }
            // In a scroller, as the Transit app's planner is: the board is taller
            // than the sheet at every size, and the parts are still the sheet's,
            // named through its scope.
            val sheetParts = this
            Column(Modifier.pageScroll(barPadding = PaddingValues()).padding(padding)) {
                // Always there: it is what the peek detent is peeking *at*.
                sheetParts.part {
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
                        supporting { +"Zone 1 · Trains and buses" }
                    }
                }
                // And the board, below it. The peek is anchored to the header, so
                // the board starts exactly at the sheet's edge — it is drawn, off
                // the bottom of the window, and a drag uncovers it at the speed of
                // the finger rather than announcing itself at a detent. `from` says
                // which size it belongs to, which is what keeps a screen reader out
                // of it until the sheet is that size.
                sheetParts.part(from = SheetDetent.Half) {
                    Column {
                        Banner(
                            tone = Tone.Warning,
                            modifier = Modifier.padding(
                                start = BottomSheetDefaults.ContentPadding,
                                end = BottomSheetDefaults.ContentPadding,
                                bottom = Theme.spacing.sm,
                            ),
                        ) {
                            +"Mandurah line trains are running up to 3 minutes late."
                        }
                        Departures(onPick = ::echo)
                    }
                }
            }
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

/**
 * Whether the screen shrinks back behind a modal sheet, `recedes`. Off is for a
 * screen with a map or a video behind, which the platform moves on every frame
 * of the shrink. Read as the sheet opens, so turning it starts the sheet over.
 */
private val sheetRecedes = Knob.Flag("Recede the page", initial = true)

internal val ModalBottomSheetDemo = ComponentDemo(
    slug = "modal-bottom-sheet",
    knobs = listOf(sheetOpen, sheetDismissible, sheetRecedes, sheetAlignment),
) {
    // Keyed on the knob, so toggling it *resets* the sheet rather than fighting
    // it. The sheet has two inputs — the knob and its own dismissal — and one
    // of them has to win; making the knob a reset means a sheet swiped away
    // comes back on the next toggle, which is the behaviour a reader expects
    // from a control labelled "Open". The button below reopens it directly.
    var open by remember(this[sheetOpen], this[sheetRecedes]) { mutableStateOf(this[sheetOpen]) }
    val dismissible = this[sheetDismissible]
    val recedes = this[sheetRecedes]
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
            recedes = recedes,
            dismissible = dismissible,
        ) {
            SheetHeader {
                +"Rename favourite"
                supporting { +"Perth Underground" }
            }
            RenameFavourite(
                onCancel = { open = false },
                onSave = { name -> open = false; echo("Saved as $name") },
            )
        }
    }
}

/**
 * What a rename sheet holds: the name to change, prefilled with the one it has,
 * and a way to save it or not. Save waits for a name that is not blank and not
 * the one it already has.
 */
@Composable
private fun RenameFavourite(onCancel: () -> Unit, onSave: (String) -> Unit) {
    val name = rememberTextFieldState("Perth Underground")
    val typed = name.text.trim().toString()
    Column(
        modifier = Modifier.padding(
            start = BottomSheetDefaults.ContentPadding,
            end = BottomSheetDefaults.ContentPadding,
            bottom = BottomSheetDefaults.ContentPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.md),
    ) {
        TextField(
            state = name,
            label = "Name",
            supporting = "Shown on your home screen and in alerts",
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Theme.spacing.sm)) {
            Button(onClick = onCancel, variant = ButtonVariant.Secondary, modifier = Modifier.weight(1f)) { +"Cancel" }
            Button(
                onClick = { onSave(typed) },
                enabled = typed.isNotEmpty() && typed != "Perth Underground",
                modifier = Modifier.weight(1f),
            ) { +"Save" }
        }
    }
}

private val sheetSide = Knob.Choice("Side", SheetSide.entries.toList(), SheetSide.Start)

/**
 * The non-modal side sheet: a rail beside a page that stays usable.
 *
 * The button on the page is still pressable with the rail open — which is the
 * whole difference from the modal sheet below, and the thing to try.
 */
internal val SideSheetDemo = ComponentDemo(
    slug = "side-sheet",
    knobs = listOf(sheetSide),
) {
    var open by remember { mutableStateOf(true) }
    val side = this[sheetSide]
    Screen {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // On the side the rail is not, so it stays pressable with the rail open —
            // which is the difference from the modal sheet, and the thing to try.
            Button(
                onClick = { open = !open; echo(if (open) "Filters shown" else "Filters hidden") },
                variant = ButtonVariant.Secondary,
                size = ButtonSize.Small,
                modifier = Modifier
                    .align(if (side == SheetSide.Start) Alignment.BottomEnd else Alignment.BottomStart)
                    .padding(Theme.spacing.md),
            ) { +(if (open) "Hide" else "Filters") }

            SideSheet(
                visible = open,
                side = side,
                // As wide as the filters want, short of the button beside it: on a
                // phone the page is narrow, and a rail over its only control would
                // be the modal sheet by another name.
                width = (maxWidth - RoomBesideTheRail).coerceIn(200.dp, 280.dp),
                paneTitle = "Filters",
            ) {
                // No scrim and no back gesture: the app owns `visible`, so the header's
                // close button is how the rail goes away.
                SheetHeader(onClose = { open = false }) { +"Filters" }
                FilterBody(onShow = { count -> echo("Showing $count departures") })
            }
        }
    }
}

/** The page the non-modal rail leaves beside it: room for its button and the button's margin. */
private val RoomBesideTheRail = 104.dp

internal val ModalSideSheetDemo = ComponentDemo(
    slug = "modal-side-sheet",
    knobs = listOf(sheetSide, sheetDismissible),
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

        ModalSideSheet(
            visible = open,
            onDismissRequest = { open = false },
            side = side,
            width = 300.dp,
            dismissible = dismissible,
            onBack = { open = false },
        ) {
            // The header's own close button is the way out when the scrim is
            // not one — `onClose` defaults to `closeEnclosingSheet()`.
            SheetHeader { +"Filters" }
            FilterBody(onShow = { count -> open = false; echo("Showing $count departures") })
        }
    }
}

/**
 * What both side sheet demos hold: the filters a departure board has. Which
 * modes, how far ahead, and step-free stops only, with the count they leave
 * shown on the button as it changes.
 */
@Composable
private fun FilterBody(onShow: (Int) -> Unit) {
    var trains by remember { mutableStateOf(true) }
    var buses by remember { mutableStateOf(true) }
    var within by remember { mutableStateOf(1) }
    var stepFree by remember { mutableStateOf(false) }
    val minutes = listOf(15, 30, 60)[within]
    val shown = departures.count { departure ->
        (if (departure.mode == Mode.Train) trains else buses) &&
            departure.minutes <= minutes &&
            (!stepFree || stops.firstOrNull { it.name == departure.towards }?.accessible != false)
    }
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(
                start = BottomSheetDefaults.ContentPadding,
                end = BottomSheetDefaults.ContentPadding,
                bottom = BottomSheetDefaults.ContentPadding,
            ),
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.md),
    ) {
        ListSection(title = { +"Show" }) {
            ChipGroup {
                FilterChip(selected = trains, onSelectedChange = { trains = it }, selectedIcon = Tabler.Outline.Check) {
                    +"Trains · ${departures.count { it.mode == Mode.Train }}"
                }
                FilterChip(selected = buses, onSelectedChange = { buses = it }, selectedIcon = Tabler.Outline.Check) {
                    +"Buses · ${departures.count { it.mode == Mode.Bus }}"
                }
            }
        }
        ListSection(title = { +"Leaving within" }) {
            SegmentedControl(
                options = listOf("15 min", "30 min", "1 hr"),
                selectedIndex = within,
                onSelectedIndexChange = { within = it },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        SelectionRow(
            selected = stepFree,
            onSelectedChange = { stepFree = it },
            role = Role.Switch,
            position = GroupPosition.Only,
        ) {
            +"Step-free only"
            trailing { Switch(checked = stepFree, onCheckedChange = null) }
        }
        // One above the other, as a narrow rail has room for them.
        Column(verticalArrangement = Arrangement.spacedBy(Theme.spacing.sm)) {
            Button(onClick = { onShow(shown) }, enabled = shown > 0, modifier = Modifier.fillMaxWidth()) {
                +"Show $shown departures"
            }
            Button(
                onClick = { trains = true; buses = true; within = 1; stepFree = false },
                // Nothing to reset until something has changed.
                enabled = !trains || !buses || within != 1 || stepFree,
                variant = ButtonVariant.Secondary,
                modifier = Modifier.fillMaxWidth(),
            ) { +"Reset" }
        }
    }
}

private val headerStyle =
    Knob.Choice("Style", SheetHeaderStyle.entries.toList(), SheetHeaderStyle.Small)
private val headerBack = Knob.Flag("Back button")

internal val SheetHeaderDemo = ComponentDemo(
    slug = "sheet-header",
    knobs = listOf(headerStyle, headerBack),
) {
    var open by remember { mutableStateOf(true) }
    val style = this[headerStyle]
    val back = this[headerBack]
    Screen {
        if (!open) {
            Button(
                onClick = { open = true },
                variant = ButtonVariant.Secondary,
                size = ButtonSize.Small,
                modifier = Modifier.align(Alignment.Center),
            ) { +"Open it again" }
        }
        ModalBottomSheet(visible = open, onDismissRequest = { open = false }) { padding ->
            // Pinned under the handle, as a stop's sheet in a maps app has it,
            // and the board scrolls under it.
            part(pin = SheetPartPin.Top) {
                SheetHeader(
                    style = style,
                    onBack = if (back) ({ echo("Back") }) else null,
                    actions = {
                        IconButton(
                            icon = Tabler.Outline.Star,
                            contentDescription = "Add to favourites",
                            onClick = { echo("Favourited") },
                        )
                    },
                ) {
                    +"Perth Underground"
                    supporting { +"Zone 1 · Trains and buses" }
                }
            }
            Column(Modifier.pageScroll(barPadding = PaddingValues()).padding(padding)) { Departures(onPick = ::echo) }
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
    ModalSideSheetDemo,
    SheetHeaderDemo,
    DragHandleDemo,
)
