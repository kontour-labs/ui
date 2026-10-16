package io.kontour.ui.overlay

import io.kontour.ui.theme.clipProxy
import io.kontour.ui.platform.webRole
import io.kontour.ui.platform.WebRole
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusTarget
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.offset
import io.kontour.ui.a11y.contrastEdge
import io.kontour.ui.a11y.highContrast
import io.kontour.ui.a11y.optionalBorder
import io.kontour.ui.components.list.scrollbar
import io.kontour.ui.foundation.Icon
import io.kontour.ui.motion.ScrollDefaults
import io.kontour.ui.theme.Motion
import io.kontour.ui.theme.kontourMotion
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.ui.unit.dp
import io.kontour.ui.a11y.minimumTouchTarget
import io.kontour.ui.components.list.ListItemScope
import io.kontour.ui.components.list.ScrollbarDefaults
import io.kontour.ui.components.list.listItemSlots
import io.kontour.ui.foundation.AnimatedCheckMark
import io.kontour.ui.foundation.ContentScope
import io.kontour.ui.foundation.ContentSlot
import io.kontour.ui.foundation.HorizontalDivider
import io.kontour.ui.foundation.LocalContentColour
import io.kontour.ui.foundation.ProvideContentColour
import io.kontour.ui.foundation.ProvideTextStyle
import io.kontour.ui.foundation.SystemIcons
import io.kontour.ui.foundation.Text
import io.kontour.ui.input.InputModality
import io.kontour.ui.input.LocalInputModality
import io.kontour.ui.input.LocalInputModalityState
import io.kontour.ui.input.insetFocusRing
import io.kontour.ui.input.pointerCursor
import io.kontour.ui.interaction.rememberLongPressFeedback
import io.kontour.ui.interaction.kontourIndication
import io.kontour.ui.theme.Theme
import io.kontour.ui.theme.inset
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** Sizing shared by every menu surface. Override per call site if you must. */
object MenuDefaults {
    /** Distance between a menu and the control it drops from. */
    val Gap: Dp
        @Composable @ReadOnlyComposable get() = Theme.componentDefaults.menuGap

    /** How close a menu may come to the edge of the window. */
    val ScreenMargin: Dp = 8.dp

    /**
     * How long a nested menu waits after the pointer leaves before closing.
     *
     * Long enough to cross the gap between the row and the submenu, short enough
     * that a menu you have moved away from does not linger.
     */
    val SubmenuCloseDelay: Duration = 220.milliseconds

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
 *     IconButton(icon = Tabler.Outline.Dots, contentDescription = "More", onClick = { visible = true })
 *     DropdownMenu(visible, onDismissRequest = { visible = false }) {
 *         item("Share", leadingIcon = Tabler.Outline.Share, onClick = ::share)
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
    // Held, and handed on as a lambda read where it is needed — the layout,
    // the way through the scrim — rather than read here. Every scroll frame
    // moves the anchor, and read here it recomposed this menu on each of them,
    // closed or open, for every row of a list that has one.
    val anchor = remember { mutableStateOf<Rect?>(null) }
    Box(Modifier.parentBounds { anchor.value = it })

    AnchoredDropdownMenuImpl(
        visible = visible,
        anchor = { anchor.value },
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        key = remember { Any() },
        side = side,
        alignment = alignment,
        matchAnchorWidth = matchAnchorWidth,
        scrim = scrim,
        anchorTakesPresses = false,
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
    /**
     * The overlay's identity in the host, and the one thing two of these shown
     * from the same place need to differ in. The default is one per call site,
     * remembered, which is right unless a loop shows several.
     */
    key: Any = remember { Any() },
    side: OverlaySide = OverlaySide.Bottom,
    alignment: OverlayAlignment = OverlayAlignment.Start,
    matchAnchorWidth: Boolean = false,
    scrim: ScrimStyle = ScrimStyle.Transparent,
    content: @Composable MenuScope.() -> Unit,
) {
    val latest = rememberUpdatedState(anchor)
    AnchoredDropdownMenuImpl(
        visible = visible,
        anchor = { latest.value },
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        key = key,
        side = side,
        alignment = alignment,
        matchAnchorWidth = matchAnchorWidth,
        scrim = scrim,
        anchorTakesPresses = false,
        content = content,
    )
}

/**
 * [AnchoredDropdownMenu], and whether a press on its [anchor] goes to the anchor.
 *
 * [anchorTakesPresses] true is for a menu hanging from something the user goes
 * on working in while it is open: a [Combobox][io.kontour.ui.components.text.Combobox]'s
 * field, typed in to narrow the list. Its scrim covered the field like the rest
 * of the page, so the first press in the text — a click to move the caret, the
 * first half of a double-click on a word — landed on the scrim, closed the list,
 * and took the typing with it. Reported as *"in something like the combobox, you
 * can't double tap or click to select text"*.
 *
 * Internal, because the one menu that needs it is the library's own, and an
 * anchor that takes presses is an anchor whose own press handling has to know
 * the menu is open — which a caller's button, toggling the menu it opened, does
 * not.
 *
 * Such a menu does not take the keyboard's focus as it opens, either. A menu
 * opened from the keyboard moves the focus into its first row, which is right
 * for a menu and wrong for a combobox: its field had just been given the focus,
 * and a frame later the menu took it, so what was typed next filtered nothing.
 * The anchor hands it over itself, through [entrance], which a combobox uses on
 * a Down arrow; and what is typed in its list goes to [typeInto], its field.
 *
 * A menu opened with a pointer holds the focus itself, on its panel, where it
 * draws no ring and presses nothing, so that a key pressed next is the menu's:
 * see `MenuKeys`. [parksFocus] false is for a submenu, which a pointer opens by
 * resting on its row, and which must not take the keys from the menu the
 * pointer is still in.
 *
 * [rows] are a select's options, built while the menu is open and drawn after
 * [content]. Past [LazyMenuFrom] of them the menu composes only the ones it
 * shows, and draws them alone: what a select puts in [content] is its "no
 * matches" line, which a list that long never needs. See `LazyMenuRows`.
 */
@Composable
internal fun AnchoredDropdownMenuImpl(
    visible: Boolean,
    anchor: () -> Rect?,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    key: Any = remember { Any() },
    side: OverlaySide = OverlaySide.Bottom,
    alignment: OverlayAlignment = OverlayAlignment.Start,
    matchAnchorWidth: Boolean = false,
    scrim: ScrimStyle = ScrimStyle.Transparent,
    anchorTakesPresses: Boolean = false,
    entrance: MenuEntrance? = null,
    typeInto: ((String) -> Unit)? = null,
    parksFocus: Boolean = true,
    rows: (() -> MenuRows)? = null,
    content: @Composable MenuScope.() -> Unit,
) {
    val host = LocalOverlayHost.current
    val density = LocalDensity.current
    val modality = LocalInputModality.current
    val dismiss by rememberUpdatedState(onDismissRequest)
    val body by rememberUpdatedState(content)
    // Read here: the entry below is built in an effect, which has no theme.
    val closeLabel = Theme.strings.closeMenu

    // **What the open menu draws with is read as it is now**, not as it was
    // when the menu opened: the entry below is built once, in an effect, and
    // what it captured stayed — a field that grew under an open list kept a
    // list its old width, and a new modifier or shape never arrived.
    val latestModifier by rememberUpdatedState(modifier)
    val latestShape by rememberUpdatedState(Theme.shapes.container)
    val latestRows by rememberUpdatedState(rows)
    val latestTypeInto by rememberUpdatedState(typeInto)

    // Read live by the overlay's measure pass rather than captured when the
    // entry was built — see `AnchoredOverlayLayout` — and never here, in this
    // composition, beyond whether there is one: the anchor moves on every frame
    // its page scrolls.
    val latestAnchor by rememberUpdatedState(anchor)
    val anchored by remember { derivedStateOf { latestAnchor() != null } }

    DisposableEffect(Unit) { onDispose { host.hide(key) } }

    LaunchedEffect(visible, anchored, side, alignment, matchAnchorWidth, scrim, anchorTakesPresses) {
        if (!visible || !anchored) {
            host.hide(key)
            return@LaunchedEffect
        }

        host.show(
            OverlayEntry(
                key = key,
                layer = OverlayLayer.Menu,
                scrim = scrim,
                dismissLabel = closeLabel,
                onDismiss = { dismiss() },
                // A menu should not steal focus from a mouse user mid-gesture,
                // but it must contain focus for a keyboard user. Trapping does
                // both: the ring only shows under keyboard modality anyway.
                trapFocus = true,
                content = {
                    // The anchor's width, for a list that matches its field:
                    // read in the open menu's own composition, so it follows a
                    // field that changes size while the list is open.
                    val anchorWidth = if (matchAnchorWidth) {
                        latestAnchor()?.let { with(density) { it.width.toDp() } } ?: Dp.Unspecified
                    } else {
                        Dp.Unspecified
                    }
                    AnchoredOverlayLayout(
                        anchorInRoot = { latestAnchor() },
                        side = side,
                        alignment = alignment,
                        gap = MenuDefaults.Gap,
                        margin = MenuDefaults.ScreenMargin,
                        minWidth = anchorWidth,
                        minimumPanel = MenuMinimumRoom,
                    ) {
                        MenuPanel(
                            modifier = latestModifier,
                            shape = latestShape,
                            matchAnchorWidth = matchAnchorWidth,
                            anchorWidth = anchorWidth,
                            rows = latestRows?.invoke(),
                            autoFocus = modality == InputModality.Keyboard && !anchorTakesPresses,
                            // Under a pointer that may be followed by keys; a
                            // finger has none to follow it with.
                            parkFocus = modality == InputModality.Mouse && !anchorTakesPresses && parksFocus,
                            entrance = entrance,
                            typeInto = latestTypeInto,
                            onDismissRequest = { dismiss() },
                            content = body,
                        )
                    }
                },
            ).apply {
                // The anchor as it is now, not as it was when the list opened:
                // a page scrolled under an open list takes its field with it,
                // and the way through the scrim has to follow.
                if (anchorTakesPresses) passThrough = { latestAnchor() }
            }
        )
    }
}

@Composable
private fun MenuPanel(
    modifier: Modifier,
    shape: Shape,
    matchAnchorWidth: Boolean,
    anchorWidth: Dp,
    rows: MenuRows?,
    autoFocus: Boolean,
    parkFocus: Boolean,
    entrance: MenuEntrance?,
    typeInto: ((String) -> Unit)?,
    onDismissRequest: () -> Unit,
    content: @Composable MenuScope.() -> Unit,
) {
    // A finger opens submenus in place; a pointer and the keyboard cascade them
    // beside their rows. Read here, live, rather than when the menu was shown:
    // the modality starts as touch until the first event says otherwise, and a
    // menu shown before a mouse had moved would otherwise nest for a finger.
    // And held while a level is open, so a key pressed in it — which makes the
    // modality the keyboard's — goes back a level rather than dropping them all.
    val levels = remember { MenuLevels() }
    val inPlace = LocalInputModality.current.needsLargeTargets || levels.open.isNotEmpty()
    val panelFocus = remember { FocusRequester() }
    val navigator = remember { MenuNavigator() }
    val keys = remember { MenuKeys() }
    keys.scope = rememberCoroutineScope()
    val formats = Theme.formats
    keys.fold = formats::foldForSearch
    keys.typeInto = typeInto
    val modalityState = LocalInputModalityState.current
    keys.showFocus = { modalityState?.current = InputModality.Keyboard }
    // The panel holds the focus for a menu opened with a pointer, until a key
    // takes it to a row; after that it is a group, and Tab does not stop on it.
    var parked by remember { mutableStateOf(parkFocus) }
    LaunchedEffect(Unit) {
        when {
            // To the option a select holds, or the first row.
            autoFocus -> keys.enter(navigator)
            parked -> runCatching { panelFocus.requestFocus() }
        }
    }
    DisposableEffect(entrance) {
        entrance?.enter = { keys.enter(navigator) }
        onDispose {
            entrance?.enter = null
            entrance?.onARow = false
        }
    }
    val sized = modifier
        .widthIn(
            min = if (matchAnchorWidth) Dp.Unspecified else MenuDefaults.MinWidth,
            max = MenuDefaults.MaxWidth,
        )
        // Measured from the rows, then clamped by the `widthIn` above — so
        // the panel hugs its content between 180dp and 320dp rather than
        // always taking the maximum.
        .width(IntrinsicSize.Max)
        .heightIn(max = MenuDefaults.MaxHeight)

    // **On touch, a submenu opens in place**: over its own row, growing from the
    // row's height, with the row kept on top as the way back and the menu under
    // it dimmed — the way an iOS menu nests. A submenu opened to the side of a
    // phone-sized menu is off the screen or over the finger, and a hover that
    // holds it open is not something a finger does.
    //
    // The levels are drawn here, in the menu's own overlay, rather than as more
    // overlays: one surface over another, in one place, so a tap outside the top
    // level goes back rather than closing everything, and nothing has to agree
    // across two overlay entries about where a row was.
    //
    // One tree whichever it is. The modality changes under an open menu — a key
    // pressed in it makes it the keyboard's — and a menu that swapped its layout
    // for it rebuilt every row and lost the focus the key had just moved.
    levels.scope = rememberCoroutineScope()
    levels.motion = Theme.motion
    val back = if (LocalLayoutDirection.current == LayoutDirection.Rtl) Key.DirectionRight else Key.DirectionLeft
    CompositionLocalProvider(LocalMenuLevels provides if (inPlace) levels else null) {
        Layout(
            modifier = Modifier
                .onGloballyPositioned { levels.root = it }
                .focusRequester(panelFocus)
                .onFocusChanged { focus ->
                    if (focus.hasFocus && !focus.isFocused) parked = false
                    entrance?.onARow = focus.hasFocus && !focus.isFocused
                }
                .focusProperties { canFocus = parked }
                .focusTarget()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when {
                        // Back a level, then out.
                        event.key == Key.Escape -> {
                            if (levels.open.isNotEmpty()) levels.closeTop() else onDismissRequest()
                            true
                        }
                        event.key == back && levels.open.isNotEmpty() -> {
                            levels.closeTop()
                            true
                        }
                        // The rest move about the level on top.
                        else -> keys.handle(event, levels.open.lastOrNull { !it.closing }?.navigator ?: navigator)
                    }
                },
            content = {
                LevelStack(
                    below = { levels.open.firstOrNull() },
                    levels = levels,
                ) {
                    MenuSurface(
                        modifier = sized,
                        shape = shape,
                        navigator = navigator,
                        dimmedBy = { levels.open.firstOrNull()?.progress?.value ?: 0f },
                        rows = rows,
                        anchorWidth = anchorWidth,
                    ) {
                        MenuScopeImpl(this, onDismissRequest).content()
                    }
                }
                levels.open.forEachIndexed { index, level ->
                    key(level.key) {
                        LevelStack(below = { levels.open.getOrNull(index + 1) }, levels = levels) {
                            MenuLevelSurface(
                                level = level,
                                levels = levels,
                                shape = shape,
                                onDismissRequest = onDismissRequest,
                                dimmedBy = { levels.open.getOrNull(index + 1)?.progress?.value ?: 0f },
                            )
                        }
                    }
                }
            },
        ) { measurables, constraints ->
            val base = measurables.first().measure(constraints)
            val width = base.width
            val reduced = levels.motion.reduceMotion
            val stacked = measurables.drop(1).mapIndexed { index, measurable ->
                val level = levels.open.getOrNull(index)
                val full = measurable.maxIntrinsicHeight(width)
                val grown = level?.progress?.value ?: 1f
                val from = level?.rowHeight?.roundToInt() ?: full
                val height = if (reduced) full else lerp(from.toFloat(), full.toFloat(), grown).roundToInt()
                // Over its row if it fits there, shifted up inside the menu if
                // it does not, and from the top when it is taller than the menu.
                val rowTop = level?.rowTop ?: 0f
                val settled = rowTop.coerceAtMost((base.height - full).toFloat()).coerceAtLeast(0f)
                val top = if (reduced) settled else lerp(rowTop, settled, grown)
                measurable.measure(Constraints.fixed(width, height.coerceAtLeast(0))) to top.roundToInt()
            }
            val height = stacked.fold(base.height) { tallest, (placeable, top) -> maxOf(tallest, top + placeable.height) }
            layout(width, height) {
                base.place(0, 0)
                stacked.forEach { (placeable, top) -> placeable.place(0, top) }
            }
        }
    }
}

/**
 * One level of an in-place menu — the menu itself, or a submenu over it — and,
 * while a level above it is open, a cover that takes a tap on it as "back".
 */
@Composable
private fun LevelStack(below: () -> MenuLevel?, levels: MenuLevels, surface: @Composable () -> Unit) {
    // Passes the minimum width on: a select's menu is given its field's width as
    // a minimum, and a box that dropped it left the panel hugging its rows
    // inside that width, narrower than the field it opened from.
    Box(propagateMinConstraints = true) {
        surface()
        val above = below()
        if (above != null) {
            Box(
                Modifier
                    .matchParentSize()
                    .pointerInput(above) { detectTapGestures { levels.close(above) } },
            )
        }
    }
}

/**
 * A menu's panel: the surface, its scrolling rows and its scrollbar. [dimmedBy]
 * is how far a level over it has opened, which dims it, draws it back a little
 * and takes it out of what a screen reader can reach. [rows] are a select's
 * options, and [anchorWidth] the field's, for a list long enough to be lazy.
 */
@Composable
private fun MenuSurface(
    modifier: Modifier,
    shape: Shape,
    navigator: MenuNavigator,
    dimmedBy: () -> Float = { 0f },
    rows: MenuRows? = null,
    anchorWidth: Dp = Dp.Unspecified,
    content: @Composable ColumnScope.() -> Unit,
) {
    val reduced = Theme.motion.reduceMotion
    val covered = dimmedBy() > 0f
    val dim = Theme.colours.scrim
    OverlaySurface(
        modifier = modifier
            // Drawn back a little, and no more: a layer's alpha would composite
            // the panel offscreen, cut to its own box, and square off the
            // shadow round it — which is how it was reported. The dimming is
            // drawn over the rows instead, inside the panel's shape.
            .graphicsLayer {
                if (!reduced) {
                    scaleX = 1f - LevelRecede * dimmedBy()
                    scaleY = scaleX
                }
            }
            .then(if (covered) Modifier.clearAndSetSemantics { } else Modifier),
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
        // last row was cut in half. The bar draws nothing when the content
        // fits, and under touch only while the list is moving, so it says
        // something only when there is something to say.
        //
        // In the panel rather than at each call site, which is the point: every
        // menu in the library goes through here, so `Select`, `MultiSelect`,
        // `Combobox`, the context menu and every submenu get it without knowing
        // it exists.
        Box(
            Modifier.drawWithContent {
                drawContent()
                val level = dimmedBy()
                if (level > 0f) drawRect(dim, alpha = LevelDim * level)
            }
        ) {
            CompositionLocalProvider(LocalMenuNavigator provides navigator) {
                // A long list of options is composed as it is shown; a short one
                // all at once, after the rest of the menu, as every menu is.
                if (rows != null && rows.count > LazyMenuFrom) {
                    LazyMenuRows(rows, shape, anchorWidth)
                } else {
                    val scroll = rememberScrollState()
                    val overscroll = rememberOverscrollEffect()
                    val fling = ScrollDefaults.flingBehavior()
                    val inset = Theme.spacing.xxs
                    val opens = remember(scroll, navigator) { OpensAtChoice(scroll, navigator) }
                    opens.rows = rows?.count ?: 0
                    opens.chosen = rows?.chosen ?: -1
                    SideEffect {
                        navigator.lazy = null
                        navigator.scroll = scroll
                    }
                    Column(
                        modifier = Modifier
                            // On the scroller, before its scroll: see `Modifier.scrollbar`.
                            // Start below the panel's own corner. Derived from the shape
                            // rather than restated as a number, so a consumer who
                            // overrides `Theme.shapes.container` gets a scrollbar that
                            // still clears it. A proportional corner answers zero, which
                            // is the right degenerate: no inset, as if the panel were square.
                            .scrollbar(scroll, cornerInset = ScrollbarDefaults.cornerInset(shape))
                            // A drag that starts on the list is the list's alone.
                            .keepsItsScroll(scroll, overscroll, fling)
                            // How tall the list is, for it to open with its
                            // value in the middle: see `OpensAtChoice`.
                            .layout { measurable, constraints ->
                                if (constraints.hasBoundedHeight) opens.viewport = constraints.maxHeight
                                opens.top = inset.roundToPx()
                                val placeable = measurable.measure(constraints)
                                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                            }
                            .verticalScroll(scroll, overscrollEffect = overscroll, flingBehavior = fling)
                            .onPlaced { navigator.opened = true }
                            .padding(horizontal = inset, vertical = inset)
                            .fillMaxWidth(),
                        verticalArrangement = opens,
                    ) {
                        content()
                        if (rows != null) {
                            for (index in 0 until rows.count) key(rows.keys[index]) { rows.Item(index) }
                        }
                    }
                }
            }
        }
    }
}

/**
 * A submenu open in place: its own row at the top, pointing down, as the way back;
 * then its rows. Reduced motion fades it in where it will be rather than growing
 * it from the row.
 */
@Composable
private fun MenuLevelSurface(
    level: MenuLevel,
    levels: MenuLevels,
    shape: Shape,
    onDismissRequest: () -> Unit,
    dimmedBy: () -> Float,
) {
    val reduced = Theme.motion.reduceMotion
    // The chevron turns from pointing along the row to pointing down as the
    // level opens, and back as it closes, so at rest it is the row's own.
    val turn = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -90f else 90f
    MenuSurface(
        modifier = Modifier
            // Faded in over the first part of its growth, and out over the last
            // of its closing, so it leaves as the row it grew from rather than as
            // a panel of its own dropped on the frame it lands — the snap reported
            // on tapping out of one. Under reduced motion the fade is the whole
            // of it.
            //
            // A faded layer is drawn offscreen, cut to its own box, which would
            // cut the level's shadow square as it faded. So the layer is made
            // bigger than the level by room for the shadow, and the level laid
            // out as its own size inside it.
            .layout { measurable, constraints ->
                val room = LevelShadowRoom.roundToPx()
                val placeable = measurable.measure(constraints.offset(2 * room, 2 * room))
                layout(placeable.width - 2 * room, placeable.height - 2 * room) { placeable.place(-room, -room) }
            }
            .graphicsLayer {
                alpha = if (reduced) level.progress.value else (level.progress.value / LevelFadeShare).coerceIn(0f, 1f)
            }
            .padding(LevelShadowRoom),
        shape = shape,
        navigator = level.navigator,
        dimmedBy = dimmedBy,
    ) {
        MenuItem(
            onClick = { levels.close(level) },
            modifier = Modifier.semantics { stateDescription = level.expandedLabel },
        ) {
            level.header(this)
            trailing {
                val slot = this
                Box(Modifier.graphicsLayer { rotationZ = turn * level.progress.value.coerceIn(0f, 1f) }) {
                    with(slot) { +SystemIcons.ChevronForward }
                }
            }
        }
        MenuDivider()
        MenuScopeImpl(this, onDismissRequest).(level.content)()
    }
}

/**
 * How much of the scrim a level under an open one is dimmed by, and how far it
 * draws back.
 */
private const val LevelDim: Float = 0.7f
private const val LevelRecede: Float = 0.03f

/** How much of a level's growth it fades in over, and of its closing it fades out over. */
private const val LevelFadeShare: Float = 0.3f

/** Room round a fading level for its shadow, which the fade would otherwise cut off. */
private val LevelShadowRoom = 32.dp

/**
 * The submenus open in place over a menu, innermost last. Provided by the menu's
 * panel on touch, where [SubMenu] opens into it rather than beside its row.
 */
@Stable
internal class MenuLevels {
    val open = mutableStateListOf<MenuLevel>()
    var root: LayoutCoordinates? = null
    lateinit var scope: CoroutineScope
    var motion: Motion = kontourMotion(reduceMotion = false)

    fun push(level: MenuLevel) {
        if (open.any { it.key == level.key }) return
        open += level
        scope.launch {
            level.progress.animateTo(
                1f,
                if (motion.reduceMotion) motion.tweenFast() else motion.springOrTween(motion.springDefault),
            )
        }
    }

    /** Closes [level] and every level over it. */
    fun close(level: MenuLevel) {
        val index = open.indexOf(level)
        if (index < 0) return
        val closing = open.subList(index, open.size).filter { !it.closing }
        if (closing.isEmpty()) return
        closing.forEach { it.closing = true }
        scope.launch {
            closing.map { launch { it.progress.animateTo(0f, motion.tweenFast()) } }.joinAll()
            open.removeAll(closing)
        }
    }

    fun closeTop() {
        open.lastOrNull { !it.closing }?.let(::close)
    }
}

/**
 * One submenu open in place: where its row was in the menu, and what to draw.
 * [rowTop] and [rowHeight] include the row's inset, so the level's own first row
 * lands exactly on it.
 */
internal class MenuLevel(
    val key: Any,
    val rowTop: Float,
    val rowHeight: Float,
    val expandedLabel: String,
    val header: ListItemScope.() -> Unit,
    val content: @Composable MenuScope.() -> Unit,
) {
    val progress = Animatable(0f)
    var closing by mutableStateOf(false)

    /** Its rows, which the keys move among while it is on top. */
    val navigator = MenuNavigator()
}

/** The in-place levels of the menu a row is in, or null where submenus cascade. */
internal val LocalMenuLevels = staticCompositionLocalOf<MenuLevels?> { null }

/**
 * Whether the rows of this menu are one choice among them, as a
 * [Select][io.kontour.ui.components.text.Select]'s options are, rather than
 * actions.
 *
 * A row cannot tell from its own `selected = false` whether it is an option not
 * chosen or an action with nothing to choose, and announced as the second it
 * was a button: a select's chosen option was the one radio button among
 * buttons. The selects say which they are; a menu of actions says nothing, and
 * its rows stay buttons.
 */
internal val LocalMenuPicksOne = staticCompositionLocalOf { false }

/** Whether the rows here are composed only for the menu to measure its width from. */
internal val LocalMenuRowSizing = staticCompositionLocalOf { false }

/** A row's place in a long list of options, or -1 for a row of an ordinary menu. */
internal val LocalMenuRowPlace = compositionLocalOf { -1 }

private fun lerp(from: Float, to: Float, fraction: Float): Float = from + (to - from) * fraction

/**
 * One row in a menu.
 *
 * A keyboard shortcut is not a parameter here — it belongs to the DSL, as
 * [MenuScope.item]'s `shortcut`, and renders as a [io.kontour.ui.components.display.Kbd].
 *
 * @param selected Marks a chosen item. Draws a check, and reports a role that
 *   depends on [multiple].
 * @param multiple Whether more than one item in this menu can be selected at
 *   once. It decides the announced role and nothing visual: `Role.Checkbox`,
 *   ticked or not, where several can be on, `Role.RadioButton` where exactly one
 *   can.
 *
 *   The distinction is the whole difference between the two components built on
 *   this. [Select][io.kontour.ui.components.text.Select] picks one;
 *   [MultiSelect][io.kontour.ui.components.text.MultiSelect] picks any number,
 *   and announced as radio buttons it was telling every screen-reader user that
 *   choosing a second mode of transport would clear the first. The selects mark
 *   their menus as choices themselves, so each of their options is announced as
 *   chosen or not; in a menu of actions only a row that is [selected] is a
 *   radio button, and the rest are buttons.
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
    val colours = Theme.colours
    val interactions = interactionSource ?: remember { MutableInteractionSource() }
    val picksOne = LocalMenuPicksOne.current
    // Composed only to be measured — see `LazyMenuRows` — and so nothing anyone
    // can press, reach or hear.
    val sizing = LocalMenuRowSizing.current
    val place = LocalMenuRowPlace.current

    // Where the menu's keys find it: see `MenuKeys`. Not a row composed only
    // to be measured, which nobody can reach.
    val stop = if (sizing) null else rememberMenuStop()
    if (stop != null) {
        stop.place = place
        stop.name = LocalMenuRowName.current ?: slots.said
        stop.enabled = enabled
        stop.chosen = LocalMenuRowChosen.current
    }

    // One shape for the clip and the indication, remembered.
    //
    // It was the same expression written twice, and `inset` returns a fresh
    // `SquircleShape` through `copy` — so a menu row built two of them per
    // recomposition and handed each to a different node. See `SquirclePaths`.
    val panelShape = Theme.shapes.container
    val rowInset = Theme.spacing.xxs
    val rowShape = remember(panelShape, rowInset) { panelShape.inset(rowInset) }

    val contentColour = when {
        !enabled -> colours.contentDisabled
        destructive -> colours.danger.solid
        else -> colours.content
    }

    DisableSelection {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .then(if (sizing) Modifier.clearAndSetSemantics { } else rowSemantics(selected, multiple, picksOne, place))
                // `fill`, so the row that lights up is the row the finger gets.
                //
                // No horizontal inset of its own either. The panel already pads by
                // `xxs` on all four sides, and this used to add a second `xxs`
                // across — so a row's highlight sat 8dp inside the panel at the
                // sides and 4dp at the top and bottom. Removing that inset was only
                // half the fix and made the other half visible: a reserved target
                // *centres* its content, so on a phone the highlight was a 36dp row
                // in a 48dp slot and the margins read 4dp at the sides against 10dp
                // top and bottom — the same complaint, the other way round. Filling
                // the target puts all four at 4dp and makes the row 48dp tall,
                // which is what it was always claiming to be.
                //
                // It also fixes the corner: `inset(xxs)` is the concentric radius
                // for a 4dp inset, and now every side really is at 4.
                .minimumTouchTarget(fill = true)
                .clip(rowShape.clipProxy())
                .then(
                    if (sizing) {
                        Modifier
                    } else {
                        Modifier
                            .then(if (stop != null) Modifier.menuStop(stop) else Modifier)
                            // Inside the row: the panel clips anything drawn around it.
                            .insetFocusRing(interactions, rowShape, enabled)
                            .pointerCursor(enabled = enabled)
                            .clickable(
                                interactionSource = interactions,
                                // A menu row is a big target; scaling it makes the whole menu
                                // look like it is wobbling.
                                indication = kontourIndication(rowShape, pressScale = 1f),
                                enabled = enabled,
                                onClick = onClick,
                            )
                    }
                )
                .padding(horizontal = Theme.spacing.sm, vertical = Theme.spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Theme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val muted = if (enabled) colours.contentMuted else colours.contentDisabled

            slots.leading?.let { leading ->
                ProvideContentColour(contentColour) {
                    ContentSlot(iconSize = Theme.sizing.iconMedium, content = leading)
                }
            }

            Box(Modifier.weight(1f)) {
                slots.label?.let { label ->
                    ProvideContentColour(contentColour) {
                        ProvideTextStyle(Theme.typography.bodyMedium) {
                            ContentSlot(maxLines = 1, content = label)
                        }
                    }
                }
            }

            slots.trailing?.let { trailing ->
                ProvideContentColour(muted) {
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
                    colour = if (enabled) colours.accent.solid else colours.contentDisabled,
                    size = Theme.sizing.iconSmall,
                )
            }
        }
    }
}

/**
 * What a menu row is to a screen reader: a checkbox where any number can be on,
 * a radio button among a select's options or for a ticked action, and a button
 * otherwise.
 */
private fun rowSemantics(selected: Boolean, multiple: Boolean, picksOne: Boolean, place: Int): Modifier =
    Modifier
        .then(
            when {
                multiple -> Modifier.webRole(WebRole.Checkbox, selected)
                picksOne || selected -> Modifier.webRole(WebRole.Radio, selected)
                else -> Modifier
            }
        )
        .semantics(mergeDescendants = true) {
            // Its place in a long list of options, which is composed only where
            // it is shown, so the rows round it cannot say how many there are.
            if (place >= 0) {
                collectionItemInfo = CollectionItemInfo(rowIndex = place, rowSpan = 1, columnIndex = 0, columnSpan = 1)
            }
            when {
                // Ticked or not, said either way: a checkbox with no
                // state is announced as neither.
                multiple -> {
                    role = Role.Checkbox
                    toggleableState = ToggleableState(selected)
                }
                // One of a select's options, chosen or not.
                picksOne -> {
                    role = Role.RadioButton
                    this.selected = selected
                }
                // A ticked row in a menu of actions — a sort order.
                selected -> {
                    role = Role.RadioButton
                    this.selected = true
                }
                else -> role = Role.Button
            }
        }

/**
 * Separates groups of related items.
 *
 * Drawn in the menu's own content colour, faintly, rather than in a line token,
 * because a menu is a raised panel and every line token is chosen for a lower
 * ground. `outlineSubtle`, which this was, is the faintest rule for a dense list
 * on `surface`: on the panel it measured 1.15:1 in light and, in dark, 1.06:1
 * and *darker* than the panel, which is a rule nobody can find. A wash of the
 * content colour stands off whatever it is drawn on — a submenu's level, a nav
 * menu, a theme's own panel colour — by as much as a list's divider stands off
 * a list. At high contrast it is the tier's `outline`, near ink, as before.
 */
@Composable
fun MenuDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.padding(
            horizontal = Theme.spacing.xs,
            vertical = Theme.spacing.xxs,
        ),
        colour = if (highContrast()) Theme.colours.outlineSubtle else LocalContentColour.current.copy(alpha = DividerAlpha),
    )
}

/**
 * How much of the content colour a divider is: 1.28:1 off the panel in light and
 * 1.39:1 in dark, which is `outline` on its own ground in each.
 */
private const val DividerAlpha: Float = 0.12f

/**
 * Labels a group of items.
 *
 * Not focusable and not clickable, so keyboard navigation skips straight over it
 * — a heading that can be tabbed to but does nothing is a dead stop.
 */
@Composable
fun MenuSectionHeader(
    modifier: Modifier = Modifier,
    title: @Composable ContentScope.() -> Unit,
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
            ProvideContentColour(Theme.colours.contentMuted) {
                ContentSlot(iconSize = Theme.sizing.iconSmall, content = title)
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
 * An icon goes in the label, `leading { +icon }`, as on a [MenuItem].
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
    interactionSource: MutableInteractionSource? = null,
    content: @Composable MenuScope.() -> Unit,
) {
    val levels = LocalMenuLevels.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    // Right opens a submenu and left comes back out of it, mirrored right to left.
    val forward = if (rtl) Key.DirectionLeft else Key.DirectionRight
    val back = if (rtl) Key.DirectionRight else Key.DirectionLeft
    if (levels != null) {
        InPlaceSubMenuRow(label, modifier, enabled, interactionSource, content, levels, forward)
        return
    }
    var open by remember { mutableStateOf(false) }
    var openedByHover by remember { mutableStateOf(false) }
    val rowFocus = remember { FocusRequester() }
    var bounds by remember { mutableStateOf<Rect?>(null) }
    val interactions = interactionSource ?: remember { MutableInteractionSource() }
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
            modifier = modifier
                .focusRequester(rowFocus)
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == forward && enabled) {
                        open = true
                        openedByHover = false
                        true
                    } else {
                        false
                    }
                },
            enabled = enabled,
            interactionSource = interactions,
        ) {
            label()
            trailing { +SystemIcons.ChevronForward }
        }
    }

    run {
        AnchoredDropdownMenuImpl(
            visible = open && enabled,
            anchor = { bounds },
            // Escape, from inside it: back to the row it came from, as Left
            // goes, so the keyboard is still in the menu rather than nowhere.
            onDismissRequest = {
                open = false
                runCatching { rowFocus.requestFocus() }
            },
            modifier = Modifier
                .hoverable(panelInteractions)
                // Left, from anywhere in it, is back to the row it came from.
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == back) {
                        open = false
                        runCatching { rowFocus.requestFocus() }
                        true
                    } else {
                        false
                    }
                },
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
            // A pointer opens it by resting on its row, and the keys stay with
            // the menu that row is in.
            parksFocus = false,
            content = content,
        )
    }
}

/**
 * A [SubMenu]'s row where submenus open in place: a tap, Enter or Right opens its
 * level over the row.
 */
@Composable
private fun InPlaceSubMenuRow(
    label: ListItemScope.() -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    interactionSource: MutableInteractionSource?,
    content: @Composable MenuScope.() -> Unit,
    levels: MenuLevels,
    forward: Key,
) {
    val key = remember { Any() }
    var row by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val inset = with(LocalDensity.current) { Theme.spacing.xxs.toPx() }
    val expanded = Theme.strings.expanded
    val currentLabel by rememberUpdatedState(label)
    val currentContent by rememberUpdatedState(content)
    fun openLevel() {
        val here = row?.takeIf { it.isAttached } ?: return
        val root = levels.root?.takeIf { it.isAttached } ?: return
        val top = root.localPositionOf(here, Offset.Zero).y
        levels.push(
            MenuLevel(
                key = key,
                rowTop = top - inset,
                rowHeight = here.size.height + inset * 2,
                expandedLabel = expanded,
                header = currentLabel,
                content = { currentContent() },
            )
        )
    }
    MenuItem(
        onClick = ::openLevel,
        modifier = modifier
            .onGloballyPositioned { row = it }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == forward && enabled) {
                    openLevel()
                    true
                } else {
                    false
                }
            },
        enabled = enabled,
        interactionSource = interactionSource,
    ) {
        label()
        trailing { +SystemIcons.ChevronForward }
    }
}

/**
 * Two to four actions side by side, an icon over a word each — the row across
 * the top of an iOS menu, for the actions used most.
 *
 * ```kotlin
 * MenuActionRow {
 *     action("Copy", Tabler.Outline.Copy) { copy(stop) }
 *     action("Share", Tabler.Outline.Share) { share(stop) }
 * }
 * ```
 *
 * For a menu built from the composables. In a [DropdownMenu]'s builder,
 * [MenuScope.actionRow] is the same row and closes the menu as each action runs;
 * this one leaves that to [MenuActionRowScope.action]'s `onClick`. The arrow keys
 * walk along it.
 */
@Composable
fun MenuActionRow(modifier: Modifier = Modifier, filled: Boolean = false, content: MenuActionRowScope.() -> Unit) {
    MenuActionCells(MenuActionRowScope().apply(content).actions, modifier, filled) { it.onClick() }
}

/**
 * The cells of an action row, each running [run] with its action — on a sunken
 * ground of its own when [filled], and on the menu's otherwise, which is the
 * default: asked for from a phone as the buttons having "no background".
 */
@Composable
internal fun MenuActionCells(actions: List<MenuAction>, modifier: Modifier, filled: Boolean, run: (MenuAction) -> Unit) {
    require(actions.size in 1..MaxActions) {
        "A menu's action row holds two to four actions, not ${actions.size}: past four each word has too " +
            "little of the menu's width to be read, and one is an item."
    }
    val cells = remember(actions.size) { List(actions.size) { FocusRequester() } }
    var focused by remember { mutableStateOf(-1) }
    // One stop for the menu's Up and Down, entered at its first cell; Left and
    // Right walk along it.
    val stop = rememberMenuStop()
    stop?.enabled = actions.any { it.enabled }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val colours = Theme.colours
    val panelShape = Theme.shapes.container
    val inset = Theme.spacing.xxs
    val cellShape = remember(panelShape, inset) { panelShape.inset(inset) }
    val edge = contrastEdge()
    DisableSelection {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(bottom = Theme.spacing.xxs)
                .then(if (stop != null) Modifier.menuStop(stop) else Modifier)
                .focusGroup()
                // Along the row, the way the arrow points: the next cell is to the
                // left right to left.
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown || focused < 0) return@onPreviewKeyEvent false
                    val step = when (event.key) {
                        Key.DirectionRight -> if (rtl) -1 else 1
                        Key.DirectionLeft -> if (rtl) 1 else -1
                        else -> return@onPreviewKeyEvent false
                    }
                    val next = focused + step
                    if (next in cells.indices) runCatching { cells[next].requestFocus() }
                    true
                },
            horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xxs),
        ) {
            actions.forEachIndexed { index, action ->
                val interactions = remember { MutableInteractionSource() }
                val tint = when {
                    !action.enabled -> colours.contentDisabled
                    action.destructive -> colours.danger.solid
                    else -> colours.content
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .minimumTouchTarget(fill = true)
                        .clip(cellShape.clipProxy())
                        .then(if (filled) Modifier.background(colours.surfaceSunken) else Modifier)
                        // Edged at the enhanced tier either way, as a ghost button is.
                        .optionalBorder(edge, cellShape)
                        .focusRequester(cells[index])
                        .onFocusChanged { if (it.isFocused) focused = index else if (focused == index) focused = -1 }
                        .insetFocusRing(interactions, cellShape, action.enabled)
                        .pointerCursor(enabled = action.enabled)
                        .clickable(
                            interactionSource = interactions,
                            indication = kontourIndication(cellShape, pressScale = 1f),
                            enabled = action.enabled,
                            role = Role.Button,
                            onClick = { run(action) },
                        )
                        .padding(vertical = Theme.spacing.xs, horizontal = Theme.spacing.xxs),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Theme.spacing.xxs),
                ) {
                    Icon(imageVector = action.icon, contentDescription = null, tint = tint, size = Theme.sizing.iconMedium)
                    Text(
                        text = action.label,
                        style = Theme.typography.labelSmall.copy(textAlign = TextAlign.Center),
                        colour = tint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** The most actions an action row holds. */
private const val MaxActions: Int = 4

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
    /**
     * The menu, with the same scope a [DropdownMenu]'s content has — so `item`,
     * the shorthand that closes the menu as it runs, works here too. It was a
     * plain `ColumnScope`, and a context menu was the one menu without it.
     */
    menu: @Composable MenuScope.() -> Unit,
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
    val longPressed = rememberLongPressFeedback()
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
                        longPressed()
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

/**
 * The least room a menu opens into, on the side it was asked for, before it tries
 * the other side: about four and a half rows.
 *
 * Reported of the combobox: close to the keyboard, its menu opened below the field
 * *two rows high* before it would flip above. The floor it was held to was the
 * popover's — a line of text — and a menu is a list to choose from; two rows of
 * one is not a list anybody can choose from. The half row is so a reader can see
 * there is more. Capped at the menu's own height, so a short menu that fits below
 * still opens below.
 */
private val MenuMinimumRoom: Dp = 208.dp
