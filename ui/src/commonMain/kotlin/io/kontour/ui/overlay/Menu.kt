package io.kontour.ui.overlay

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.a11y.minimumTouchTarget
import io.kontour.ui.components.list.ListItemScope
import io.kontour.ui.components.list.Scrollbar
import io.kontour.ui.components.list.listItemSlots
import io.kontour.ui.foundation.AnimatedCheckMark
import io.kontour.ui.foundation.ContentScope
import io.kontour.ui.foundation.ContentSlot
import io.kontour.ui.foundation.HorizontalDivider
import io.kontour.ui.foundation.ProvideContentColor
import io.kontour.ui.foundation.ProvideTextStyle
import io.kontour.ui.foundation.SystemIcons
import io.kontour.ui.foundation.Text
import io.kontour.ui.input.InputModality
import io.kontour.ui.input.LocalInputModality
import io.kontour.ui.interaction.FeedbackIntent
import io.kontour.ui.interaction.LocalFeedback
import io.kontour.ui.interaction.kontourIndication
import io.kontour.ui.theme.Theme
import io.kontour.ui.theme.inset
import kotlinx.coroutines.delay

/** Sizing shared by every menu surface. Override per call site if you must. */
object MenuDefaults {
    /** Distance between a menu and the control it drops from. */
    val Gap: Dp = 4.dp

    /** How close a menu may come to the edge of the window. */
    val ScreenMargin: Dp = 8.dp

    /**
     * How long a nested menu waits after the pointer leaves before closing.
     *
     * Long enough to cross the gap between the row and the submenu, short enough
     * that a menu you have moved away from does not linger.
     */
    val SubmenuCloseDelay: Long = 220L

    val MinWidth: Dp = 180.dp
    val MaxWidth: Dp = 320.dp

    /** Beyond this the menu scrolls rather than growing. */
    val MaxHeight: Dp = 400.dp
}

/**
 * A menu that drops from the control it is declared next to.
 *
 * ```
 * Box {
 *     IconButton(Tabler.Outline.Dots, "More", onClick = { visible = true })
 *     DropdownMenu(visible, onDismissRequest = { visible = false }) {
 *         item("Share", icon = Tabler.Outline.Share, onClick = ::share)
 *         divider()
 *         item("Delete", destructive = true, onClick = ::delete)
 *     }
 * }
 * ```
 *
 * Anchors to its immediate parent, so the layout that positions the trigger
 * positions the menu too. It renders into the [OverlayHost] rather than inline,
 * which is what lets it escape a clipping parent — a menu on the last row of a
 * scrolling list is the case that catches naive implementations.
 *
 * Opening scales it up from the corner nearest the anchor, so it reads as coming
 * *out of* the control rather than appearing over it.
 *
 * @param side Which way it prefers to open. Flipped automatically when there is
 *   no room — see [positionAnchored].
 * @param matchAnchorWidth For a select, whose menu should line up with the field
 *   it belongs to.
 */
@Composable
fun DropdownMenu(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    side: OverlaySide = OverlaySide.Bottom,
    alignment: OverlayAlignment = OverlayAlignment.Start,
    matchAnchorWidth: Boolean = false,
    scrim: ScrimStyle = ScrimStyle.Transparent,
    content: @Composable MenuScope.() -> Unit,
) {
    var anchor by remember { mutableStateOf<Rect?>(null) }
    Box(Modifier.parentBounds { anchor = it })

    AnchoredDropdownMenu(
        visible = visible,
        anchor = anchor,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        side = side,
        alignment = alignment,
        matchAnchorWidth = matchAnchorWidth,
        scrim = scrim,
        content = content,
    )
}

/**
 * A [DropdownMenu] anchored to a rectangle you supply rather than to its parent.
 *
 * ```kotlin
 * var field by remember { mutableStateOf<Rect?>(null) }
 *
 * Row(Modifier.anchorBounds { field = it }) { … }
 * AnchoredDropdownMenu(visible, field, onDismissRequest = { … }) { … }
 * ```
 *
 * [DropdownMenu] covers the common case by anchoring to the layout it is
 * declared inside. This one is for when that is not the right node: a select,
 * whose menu must line up with the field frame rather than with whichever slot
 * the menu happened to be declared in; a context menu at a pointer position; a
 * submenu attached to its own row.
 *
 * Pair it with [Modifier.anchorBounds] on the element the menu belongs to.
 */
@Composable
fun AnchoredDropdownMenu(
    visible: Boolean,
    anchor: Rect?,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    side: OverlaySide = OverlaySide.Bottom,
    alignment: OverlayAlignment = OverlayAlignment.Start,
    matchAnchorWidth: Boolean = false,
    scrim: ScrimStyle = ScrimStyle.Transparent,
    key: Any = remember { Any() },
    content: @Composable MenuScope.() -> Unit,
) {
    val host = LocalOverlayHost.current
    val density = LocalDensity.current
    val modality = LocalInputModality.current
    val dismiss by rememberUpdatedState(onDismissRequest)
    val body by rememberUpdatedState(content)

    val shape = Theme.shapes.container
    val anchorWidth = anchor?.let { with(density) { it.width.toDp() } } ?: Dp.Unspecified

    // Read live by the overlay's measure pass rather than captured when the
    // entry was built — see `AnchoredOverlayLayout`.
    val latestAnchor by rememberUpdatedState(anchor)

    DisposableEffect(Unit) { onDispose { host.hide(key) } }

    LaunchedEffect(visible, anchor != null, side, alignment, matchAnchorWidth, scrim) {
        if (!visible || anchor == null) {
            host.hide(key)
            return@LaunchedEffect
        }

        host.show(
            OverlayEntry(
                key = key,
                layer = OverlayLayer.Menu,
                scrim = scrim,
                dismissLabel = "Close menu",
                onDismiss = { dismiss() },
                // A menu should not steal focus from a mouse user mid-gesture,
                // but it must contain focus for a keyboard user. Trapping does
                // both: the ring only shows under keyboard modality anyway.
                trapFocus = true,
                content = {
                    AnchoredOverlayLayout(
                        anchorInRoot = { latestAnchor },
                        side = side,
                        alignment = alignment,
                        gap = MenuDefaults.Gap,
                        margin = MenuDefaults.ScreenMargin,
                        minWidth = if (matchAnchorWidth) anchorWidth else Dp.Unspecified,
                    ) {
                        MenuPanel(
                            modifier = modifier,
                            shape = shape,
                            matchAnchorWidth = matchAnchorWidth,
                            autoFocus = modality == InputModality.Keyboard,
                            onDismissRequest = { dismiss() },
                            content = body,
                        )
                    }
                },
            )
        )
    }
}

@Composable
private fun MenuPanel(
    modifier: Modifier,
    shape: Shape,
    matchAnchorWidth: Boolean,
    autoFocus: Boolean,
    onDismissRequest: () -> Unit,
    content: @Composable MenuScope.() -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (autoFocus) runCatching { focusRequester.requestFocus() }
    }

    OverlaySurface(
        modifier = modifier
            .widthIn(
                min = if (matchAnchorWidth) Dp.Unspecified else MenuDefaults.MinWidth,
                max = MenuDefaults.MaxWidth,
            )
            // Measured from the rows, then clamped by the `widthIn` above — so
            // the panel hugs its content between 180dp and 320dp rather than
            // always taking the maximum.
            .width(IntrinsicSize.Max)
            .heightIn(max = MenuDefaults.MaxHeight)
            .focusRequester(focusRequester)
            .focusGroup()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                    onDismissRequest()
                    true
                } else {
                    false
                }
            },
        shape = shape,
        // Hands the panel's settled width down to the column, so the rows are
        // the panel's width rather than their own.
        propagateMinConstraints = true,
    ) {
        // Fills the panel, and the panel is sized from these rows' intrinsic
        // width — see the `width(IntrinsicSize.Max)` on the surface above.
        //
        // Both halves are needed and neither is enough alone. Intrinsic width
        // *here* sized the column to the widest row while the surface held its
        // own 180dp minimum, so a menu of short labels left the difference as
        // bare surface down the trailing edge, with the dividers and the hover
        // wash stopping short of the panel with it. Filling here without moving
        // the intrinsic measurement to the surface just made every menu its
        // maximum width instead, which is worse — a two-item menu has no
        // business being 320dp wide.
        // A scrollbar, where a scrollbar is a thing.
        //
        // A menu is the archetypal short container holding a list that may not
        // fit — a `Combobox` over forty stops, a context menu on a small window
        // — and until now the only clue that there was more below was that the
        // last row was cut in half. `Scrollbar` returns nothing on touch and
        // nothing when the content fits, so this costs an empty composable on a
        // phone and draws only when it has something to say.
        //
        // In the panel rather than at each call site, which is the point: every
        // menu in the library goes through here, so `Select`, `MultiSelect`,
        // `Combobox`, the context menu and every submenu get it without knowing
        // it exists.
        val scroll = rememberScrollState()
        Box {
            Column(
                modifier = Modifier
                    .verticalScroll(scroll)
                    .padding(horizontal = Theme.spacing.xxs, vertical = Theme.spacing.xxs)
                    .fillMaxWidth(),
            ) {
                MenuScopeImpl(this, onDismissRequest).content()
            }

            Scrollbar(
                state = scroll,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}

/**
 * One row in a menu.
 *
 * A keyboard shortcut is not a parameter here — it belongs to the DSL, as
 * [MenuScope.item]'s `shortcut`, and renders as a [io.kontour.ui.components.display.Kbd].
 *
 * @param selected Marks a chosen item. Draws a check, and reports a role that
 *   depends on [multiple].
 * @param multiple Whether more than one item in this menu can be selected at
 *   once. It decides the announced role and nothing visual: `Role.Checkbox`
 *   where several can be on, `Role.RadioButton` where exactly one can.
 *
 *   The distinction is the whole difference between the two components built on
 *   this. [Select][io.kontour.ui.components.text.Select] picks one;
 *   [MultiSelect][io.kontour.ui.components.text.MultiSelect] picks any number,
 *   and announced as radio buttons it was telling every screen-reader user that
 *   choosing a second mode of transport would clear the first.
 * @param destructive Renders in the danger tone. For anything the user cannot
 *   undo.
 */
@Composable
fun MenuItem(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    multiple: Boolean = false,
    destructive: Boolean = false,
    interactionSource: MutableInteractionSource? = null,
    content: ListItemScope.() -> Unit,
) {
    val slots = listItemSlots(content)
    val colors = Theme.colors
    val feedback = LocalFeedback.current
    val interactions = interactionSource ?: remember { MutableInteractionSource() }

    val contentColor = when {
        !enabled -> colors.contentDisabled
        destructive -> colors.danger.solid
        else -> colors.content
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                role = when {
                    multiple -> Role.Checkbox
                    selected -> Role.RadioButton
                    else -> Role.Button
                }
                if (selected) this.selected = true
            }
            .minimumTouchTarget()
            .padding(horizontal = Theme.spacing.xxs)
            .clip(Theme.shapes.container.inset(Theme.spacing.xxs))
            .clickable(
                interactionSource = interactions,
                // A menu row is a big target; scaling it makes the whole menu
                // look like it is wobbling.
                indication = kontourIndication(
                    Theme.shapes.container.inset(Theme.spacing.xxs),
                    pressScale = 1f,
                ),
                enabled = enabled,
                onClick = {
                    feedback.perform(FeedbackIntent.Selection)
                    onClick()
                },
            )
            .padding(horizontal = Theme.spacing.sm, vertical = Theme.spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val muted = if (enabled) colors.contentMuted else colors.contentDisabled

        slots.leading?.let { leading ->
            ProvideContentColor(contentColor) {
                ContentSlot(iconSize = Theme.sizing.iconMedium, content = leading)
            }
        }

        Box(Modifier.weight(1f)) {
            slots.label?.let { label ->
                ProvideContentColor(contentColor) {
                    ProvideTextStyle(Theme.typography.bodyMedium) {
                        ContentSlot(maxLines = 1, content = label)
                    }
                }
            }
        }

        slots.trailing?.let { trailing ->
            ProvideContentColor(muted) {
                ProvideTextStyle(Theme.typography.labelSmall) {
                    ContentSlot(iconSize = Theme.sizing.iconSmall, maxLines = 1, content = trailing)
                }
            }
        }
        // Reserved for the whole life of a multi-select menu, which stays open
        // while its ticks come and go: a mark that takes space only when it is
        // there makes every label in the menu step sideways as the user works
        // down it. A single-select menu closes on the choice, so the slot is
        // only worth reserving on the row that has one.
        if (multiple || selected) {
            AnimatedCheckMark(
                checked = selected,
                color = if (enabled) colors.accent.solid else colors.contentDisabled,
                size = Theme.sizing.iconSmall,
            )
        }
    }
}

/** Separates groups of related items. */
@Composable
fun MenuDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.padding(
            horizontal = Theme.spacing.xs,
            vertical = Theme.spacing.xxs,
        ),
        color = Theme.colors.outlineSubtle,
    )
}

/**
 * Labels a group of items.
 *
 * Not focusable and not clickable, so keyboard navigation skips straight over it
 * — a heading that can be tabbed to but does nothing is a dead stop.
 */
@Composable
fun MenuSectionHeader(
    modifier: Modifier = Modifier,
    content: @Composable ContentScope.() -> Unit,
) {
    Box(
        modifier.padding(
            start = Theme.spacing.sm,
            end = Theme.spacing.sm,
            top = Theme.spacing.xs,
            bottom = Theme.spacing.xxs,
        )
    ) {
        ProvideTextStyle(Theme.typography.labelSmall) {
            ProvideContentColor(Theme.colors.contentMuted) {
                ContentSlot(iconSize = Theme.sizing.iconSmall, content = content)
            }
        }
    }
}

/**
 * A menu item that opens a menu of its own.
 *
 * ```
 * SubMenu(label = { +"Sort by" }) {
 *     MenuItem(onClick = ::sortByDeparture, selected = sort == Departure) { +"Departure" }
 *     MenuItem(onClick = ::sortByDuration, selected = sort == Duration) { +"Duration" }
 * }
 * ```
 *
 * Opens on hover for a mouse and on tap for a finger, which is the difference
 * that makes nested menus usable on touch at all — hover-only submenus are
 * unreachable without a pointer, and tap-only submenus feel sticky with a mouse.
 * It opens to the [OverlaySide.End] side and flips to [OverlaySide.Start] near
 * the window edge.
 *
 * Nested menus are worth being sparing with. Two levels is a category; three is
 * a filing system, and by then the flat list with section headers is easier to
 * scan.
 */
@Composable
fun SubMenu(
    label: ListItemScope.() -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    content: @Composable MenuScope.() -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    var openedByHover by remember { mutableStateOf(false) }
    var bounds by remember { mutableStateOf<Rect?>(null) }
    val interactions = remember { MutableInteractionSource() }
    val panelInteractions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    val panelHovered by panelInteractions.collectIsHoveredAsState()
    val modality = LocalInputModality.current

    // Hover opens it for a pointer; a tap opens it for a finger.
    //
    // Closing watches the *submenu* as well as the row, and waits a moment
    // before acting. Both halves are needed. Closing the instant the pointer
    // leaves the row would close it while the pointer is crossing the gap to
    // reach it — the classic nested-menu frustration, and the reason an earlier
    // version did not close on un-hover at all. But not closing meant the
    // submenu sat there until something else was clicked, which is worse: a menu
    // you have visibly moved away from should go.
    //
    // The delay is load-bearing rather than cosmetic, and it is not obvious why:
    // the open panel overlaps the row it came from, so opening takes the hover
    // *off* the row. Written as `open = hovered || panelHovered` — which reads
    // like the same thing — the two states chase each other every frame and the
    // composition never settles. `SubMenuHoverTest` catches the taste problem;
    // the hang is what you get for removing the delay on its own.
    // Only what hover opened does hover close. A pointer user who *clicks* the
    // row rather than resting on it was opening the submenu and watching it shut
    // again a moment later without having moved — the close ran on any device
    // that could hover, whatever had opened it.
    LaunchedEffect(hovered, panelHovered, modality) {
        if (!modality.supportsHover) return@LaunchedEffect
        if (hovered || panelHovered) {
            open = true
            openedByHover = true
        } else if (open && openedByHover) {
            delay(MenuDefaults.SubmenuCloseDelay)
            open = false
            openedByHover = false
        }
    }

    Box(Modifier.anchorBounds { bounds = it }) {
        MenuItem(
            onClick = {
                open = !open
                openedByHover = false
            },
            modifier = modifier,
            enabled = enabled,
            interactionSource = interactions,
        ) {
            label()
            if (leadingIcon != null) leading { +leadingIcon }
            trailing { +SystemIcons.ChevronForward }
        }
    }

    AnchoredDropdownMenu(
        visible = open && enabled,
        anchor = bounds,
        onDismissRequest = { open = false },
        modifier = Modifier.hoverable(panelInteractions),
        side = OverlaySide.End,
        alignment = OverlayAlignment.Start,
        // No scrim of its own, unlike every other menu — and this is the whole
        // reason hover-to-open works at all.
        //
        // `ScrimStyle.Transparent` blocks pointer input across the entire host,
        // which is right for a menu opened from the page: a click outside should
        // close it rather than land on whatever was underneath. A submenu is
        // already inside a menu that is doing exactly that, so its own scrim adds
        // no protection — it only covers the parent menu, including the row the
        // submenu opened from. The pointer resting on that row would then be
        // hovering the scrim instead, the row would go cold, the submenu would
        // close, and its scrim would go with it — putting the pointer back on the
        // row. A submenu that blinks on and off for as long as you look at it.
        scrim = ScrimStyle.None,
        content = content,
    )
}

/**
 * Wraps [content] so that a right-click — or a long press on touch — opens
 * [menu] at the pointer.
 *
 * ```
 * ContextMenuArea(
 *     menu = {
 *         MenuItem(onClick = ::copy) { +"Copy stop ID" }
 *         MenuItem(onClick = ::favourite) { +"Add to favourites" }
 *     },
 * ) {
 *     StopRow(stop)
 * }
 * ```
 *
 * The two triggers are not interchangeable dialects of the same thing: a long
 * press must not fire for a mouse user who simply holds the button still, and a
 * secondary click cannot happen on a touchscreen. Both are wired, and the long
 * press is gated on the active [io.kontour.ui.input.InputModality].
 *
 * A context menu should never be the *only* way to reach an action. Anything in
 * here needs a visible route too.
 */
@Composable
fun ContextMenuArea(
    menu: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    // Two pieces of state rather than one nullable `Rect`, because the anchor has
    // to outlive the dismissal. Clearing it *was* the dismissal, so the menu
    // spent its whole exit animation with no anchor to place against and slid to
    // the top-left corner of the window on the way out. `AnchoredOverlayLayout`
    // now keeps the last real anchor as well, so either half of this is enough —
    // and the pair is worth having anyway: "where it opened" and "whether it is
    // open" are two facts, and one of them survives the other.
    var anchor by remember { mutableStateOf<Rect?>(null) }
    var open by remember { mutableStateOf(false) }
    var coordinates by remember {
        mutableStateOf<LayoutCoordinates?>(null)
    }
    val feedback = LocalFeedback.current
    val modality = LocalInputModality.current

    fun openAt(local: Offset) {
        val coords = coordinates ?: return
        val root = coords.localToRoot(local)
        // A zero-size anchor: the menu hangs off the point itself, which is what
        // makes it feel like it belongs to the click rather than the row.
        anchor = Rect(root, root)
        open = true
    }

    Box(
        modifier = modifier
            .onGloballyPositioned { coordinates = it }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val secondary = event.buttons.isSecondaryPressed
                        if (event.type == PointerEventType.Press && secondary) {
                            val change = event.changes.firstOrNull() ?: continue
                            change.consume()
                            openAt(change.position)
                        }
                    }
                }
            }
            .pointerInput(enabled, modality) {
                if (!enabled || !modality.needsLargeTargets) return@pointerInput
                detectTapGestures(
                    onLongPress = { position ->
                        feedback.perform(FeedbackIntent.LongPress)
                        openAt(position)
                    },
                )
            }
    ) {
        content()
    }

    AnchoredDropdownMenu(
        visible = open,
        anchor = anchor,
        onDismissRequest = { open = false },
        side = OverlaySide.Bottom,
        alignment = OverlayAlignment.Start,
        content = menu,
    )
}

