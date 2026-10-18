package io.kontour.ui.nav

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.ui.a11y.contrastEdge
import io.kontour.ui.a11y.minimumTouchTarget
import io.kontour.ui.components.action.ButtonSize
import io.kontour.ui.components.action.IconButton
import io.kontour.ui.components.list.Scrollbar
import io.kontour.ui.components.list.fadingEdges
import io.kontour.ui.dragdrop.DragHost
import io.kontour.ui.dragdrop.DragItemOptions
import io.kontour.ui.dragdrop.DragItemScope
import io.kontour.ui.dragdrop.DropListState
import io.kontour.ui.dragdrop.LocalDragHost
import io.kontour.ui.dragdrop.dragItems
import io.kontour.ui.dragdrop.dropList
import io.kontour.ui.dragdrop.movesFor
import io.kontour.ui.dragdrop.sourceSpec
import io.kontour.ui.foundation.BarSurface
import io.kontour.ui.foundation.FadingText
import io.kontour.ui.foundation.ProvideContentColour
import io.kontour.ui.input.InputModality
import io.kontour.ui.input.LocalInputModality
import io.kontour.ui.input.focusRing
import io.kontour.ui.input.middleClick
import io.kontour.ui.input.pointerCursor
import io.kontour.ui.interaction.kontourIndication
import io.kontour.ui.motion.ScrollDefaults
import io.kontour.ui.motion.scrollToItem
import io.kontour.ui.motion.sizeSpec
import io.kontour.ui.platform.WebRole
import io.kontour.ui.platform.webRole
import io.kontour.ui.theme.Theme
import io.kontour.ui.theme.clipProxy
import io.kontour.ui.theme.topCornersOnly
import kotlin.math.roundToInt

/** What [DocumentTabs] takes by default. */
object DocumentTabsDefaults {
    /** The strip's height: a tab bar's, so the two sit level in one window. */
    val Height: Dp
        @Composable @ReadOnlyComposable get() = Theme.componentDefaults.tabBarHeight

    /** As narrow as a tab gets before the strip scrolls instead: its icon and a few letters. */
    val MinTabWidth: Dp = TabMinWidth

    /** As wide as a tab gets, however few there are: a title's worth, and no more. */
    val MaxTabWidth: Dp = TabMaxWidth

    /**
     * The strip's colours, from the theme.
     *
     * The selected tab is the page's [selectedContainer] — `surface`, by default
     * — so the document under the strip, on the same surface, is the tab's
     * own page; the strip's [container] is the darker ground the others sit on.
     */
    @Composable
    @ReadOnlyComposable
    fun colours(
        container: Color = Theme.colours.surfaceSunken,
        selectedContainer: Color = Theme.colours.surface,
        content: Color = Theme.colours.contentMuted,
        selectedContent: Color = Theme.colours.content,
        separator: Color = Theme.colours.outlineSubtle,
    ): DocumentTabsColours = DocumentTabsColours(container, selectedContainer, content, selectedContent, separator)
}

/**
 * A [DocumentTabs]' colours. Made by [DocumentTabsDefaults.colours].
 *
 * @property container The strip's ground, behind the tabs that are not selected.
 * @property selectedContainer The selected tab, and the page it opens onto.
 * @property content A tab's title and icon.
 * @property selectedContent The selected tab's.
 * @property separator The hairline between two tabs neither selected nor hovered.
 */
@Immutable
data class DocumentTabsColours(
    val container: Color,
    val selectedContainer: Color,
    val content: Color,
    val selectedContent: Color,
    val separator: Color,
)

/**
 * Open documents, as a browser's or an editor's tabs: one selected and joined
 * to the page below it, the others flat on the strip, each closable, and a "+"
 * for another.
 *
 * ```kotlin
 * val tabs = rememberDropListState(scroll, Document, documents, key = { it.id },
 *     label = "Open documents", itemLabel = { it.title }, onDrop = { documents.move(it) })
 * DocumentTabs(
 *     state = tabs,
 *     value = open,
 *     onValueChange = { open = it },
 *     onClose = { documents.remove(it) },
 *     onAdd = { documents.add(untitled()) },
 * )
 * ```
 *
 * The strip is a drop list, so its tabs move as anything in a drop list
 * does: held and dragged along the strip, they slide the others aside, a
 * detent felt at each place; pulled down off it they come free, and carried
 * into another strip, they ride that one and drop into it — a split view's
 * second pane. Without an `OverlayHost` round the app nothing is carried, and
 * a screen reader's moves, the keyboard's and every button still work.
 *
 * Tabs share the strip's width between [minTabWidth] and [maxTabWidth], and
 * past that many the strip scrolls, the selected tab kept in sight. A tab grows
 * in as it opens and shrinks away as it closes; closing by mouse keeps the
 * others their width until the pointer leaves the strip, so the next tab's ×
 * is under it.
 *
 * @param state The tabs: the caller's list, which stays the one source of
 *   truth, as a drop list's does. Apply nothing else of the drop list's to
 *   anything; the strip is its list.
 * @param value The selected tab, if any.
 * @param onValueChange A tab chosen: pressed, picked up, or selected with the keyboard.
 * @param onClose Closes a tab: by its ×, a middle click, Delete, or a screen
 *   reader's "Close". `null` for tabs that cannot be closed.
 * @param onAdd Opens another, by the "+" after the last tab. `null` for no "+".
 * @param closeLabel A tab's × as a screen reader names it, for the tab's label.
 * @param leading A tab's icon: a favicon, a file's glyph, a spinner while it loads.
 */
@Composable
fun <T : Any> DocumentTabs(
    state: DropListState<T>,
    value: T?,
    onValueChange: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClose: ((T) -> Unit)? = null,
    onAdd: (() -> Unit)? = null,
    closeLabel: (tab: String) -> String = Theme.strings.closeTab,
    addLabel: String = Theme.strings.newTab,
    closeIcon: ImageVector = Theme.icons.close,
    addIcon: ImageVector = Theme.icons.plus,
    minTabWidth: Dp = DocumentTabsDefaults.MinTabWidth,
    maxTabWidth: Dp = DocumentTabsDefaults.MaxTabWidth,
    colours: DocumentTabsColours = DocumentTabsDefaults.colours(),
    leading: (@Composable (item: T) -> Unit)? = null,
) {
    val density = LocalDensity.current
    val motion = Theme.motion
    val strings = Theme.strings
    val height = DocumentTabsDefaults.Height
    val foot = Theme.spacing.xs
    val body = Theme.shapes.extraSmall.topCornersOnly()
    val flared = remember(body, foot) { FlaredTabShape(body, foot) }
    val edge = contrastEdge()
    val mouse = LocalInputModality.current == InputModality.Mouse
    // A tab rides the strip until it is pulled clear, as a browser's does.
    state.tearOff = with(density) { TearOff.toPx() }

    val items = state.items
    val keys = items.map(state.key)
    val keySet = keys.toHashSet()
    val selectedKey = value?.let(state.key)
    val strip = remember { StripState() }

    // What has gone since the last composition, and is still drawn where it
    // was, shrinking: worked out here, so it is drawn in the very frame it
    // went, and kept from the next. A tab that went because it was dropped
    // somewhere else has gone already: the carried thing landed there.
    val held = state.heldKey
    val known = strip.known
    val goingNow = if (!strip.seeded || motion.reduceMotion) {
        emptyList()
    } else {
        known.mapIndexedNotNull { i, (key, item) ->
            if (key in keySet || key == held || strip.leaving.any { it.key == key }) return@mapIndexedNotNull null
            val after = known.drop(i + 1).firstOrNull { it.first in keySet }?.first
            // As it was drawn last: chosen or not, with its × or not, and as
            // wide. The strip's share grows as it goes, and a tab that grew with
            // it, or lost its ×, moved its title as it shrank.
            val wasSelected = key == strip.selectedWas
            Leaving(
                key = key,
                item = item,
                after = after,
                whole = strip.widthWas,
                selected = wasSelected,
                showedClose = wasSelected || key == strip.hovered || strip.widthWas >= CloseShownFrom,
                fraction = Animatable(1f),
            )
        }
    }
    val knownKeys = known.mapTo(HashSet()) { it.first }
    val seeded = strip.seeded
    SideEffect {
        strip.leaving.addAll(goingNow)
        strip.leaving.removeAll { it.key in keySet }
        strip.known = keys.zip(items)
        strip.selectedWas = selectedKey
        strip.seeded = true
    }
    val ghosts = strip.leaving.filter { it.key !in keySet } + goingNow

    // The selected tab in sight: scrolled to, if it is not all on the strip.
    LaunchedEffect(selectedKey, items.size) {
        val index = keys.indexOf(selectedKey)
        if (index < 0) return@LaunchedEffect
        val info = state.listState.layoutInfo
        val seen = info.visibleItemsInfo.firstOrNull { it.key == selectedKey }
        val whole = seen != null && seen.offset >= info.viewportStartOffset &&
            seen.offset + seen.size <= info.viewportEndOffset
        if (!whole) state.listState.scrollToItem(index, motion)
    }
    // A tab moved to by the keyboard, scrolled to first so it is composed to
    // take the focus.
    LaunchedEffect(strip.pendingFocus) {
        val index = keys.indexOf(strip.pendingFocus ?: return@LaunchedEffect)
        if (index >= 0 && state.listState.layoutInfo.visibleItemsInfo.none { it.key == strip.pendingFocus }) {
            state.listState.scrollToItem(index)
        }
    }

    val ltr = LocalLayoutDirection.current == LayoutDirection.Ltr
    fun keyed(event: KeyEvent, index: Int, item: T): Boolean {
        if (event.type != KeyEventType.KeyDown || keys.isEmpty()) return false
        val next = when (event.key) {
            Key.DirectionRight -> if (ltr) index + 1 else index - 1
            Key.DirectionLeft -> if (ltr) index - 1 else index + 1
            Key.MoveHome -> 0
            Key.MoveEnd -> keys.lastIndex
            Key.Delete, Key.Backspace -> {
                val close = onClose ?: return false
                close(item)
                return true
            }
            else -> return false
        }.coerceIn(0, keys.lastIndex)
        strip.roving = keys[next]
        strip.pendingFocus = keys[next]
        return true
    }

    BarSurface(modifier = modifier, containerColour = colours.container) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                // The strip's edge at the high contrast tier, all round it, as any
                // ground on the page has one there. Its foot is the line every tab
                // but the selected one stands on: the selected one's ground covers
                // it, so the tab's own edge runs on round the tab unbroken, open
                // to its page.
                .drawBehind {
                    if (edge != null) {
                        val w = edge.width.toPx()
                        drawRect(edge.brush, topLeft = Offset(w / 2, w / 2), size = Size(size.width - w, size.height - w), style = Stroke(w))
                    }
                }
                // Closing by mouse holds every tab at its width until the
                // pointer leaves the strip, so the next tab's × comes under it.
                .pointerInput(strip) {
                    awaitPointerEventScope {
                        while (true) {
                            if (awaitPointerEvent(PointerEventPass.Initial).type == PointerEventType.Exit) strip.frozen = null
                        }
                    }
                },
        ) {
            val addRoom = if (onAdd != null) height else 0.dp
            val share = ((maxWidth - addRoom - foot * 2) / keys.size.coerceAtLeast(1)).coerceIn(minTabWidth, maxTabWidth)
            val width = animateDpAsState(strip.frozen ?: share, motion.sizeSpec(), label = "documentTabWidth")
            val showsCloseAt = remember(width) { derivedStateOf { width.value >= CloseShownFrom } }
            SideEffect { strip.widthWas = width.value }

            val options = DragItemOptions(
                movesOnItem = false,
                // The carried tab's shadow is its ground's, below the gap at the
                // strip's top: the whole node's let the shadow show through the gap.
                shape = BelowGap(body, Theme.spacing.xs),
                slides = false,
                before = { next ->
                    ghosts.filter { it.after == next || (next == null && it.after !in keySet) }.forEach { ghost ->
                        item(key = ghost.key, contentType = "kontour.closingTab") {
                            LaunchedEffect(ghost) {
                                ghost.fraction.animateTo(0f, motion.sizeSpec())
                                strip.leaving.remove(ghost)
                            }
                            @Suppress("UNCHECKED_CAST")
                            val gone = ghost.item as T
                            val whole = remember(ghost) { mutableStateOf(ghost.whole) }
                            val inset = Theme.spacing.xs
                            Box(
                                Modifier
                                    // Cut where it ends as it narrows. Clipped inside
                                    // the narrowing, the clip was the tab's whole
                                    // width, and its title spilled out over the next.
                                    .clipToBounds()
                                    .tabWidth(whole, ghost.fraction)
                                    .heightIn(min = height)
                                    // Fading as it goes, ground and all, so nothing
                                    // of it is gone in a frame.
                                    .graphicsLayer { alpha = ghost.fraction.value }
                                    .drawBehind {
                                        val top = inset.toPx()
                                        if (ghost.selected && size.height > top) {
                                            val ground = body.createOutline(Size(size.width, size.height - top), layoutDirection, this)
                                            translate(top = top) { drawOutline(ground, colours.selectedContainer) }
                                        }
                                    }
                                    .clearAndSetSemantics {}
                                    // Below the gap at the strip's top, as the tab it was.
                                    .padding(top = inset),
                            ) {
                                TabFace(
                                    label = state.itemLabel(gone),
                                    colour = when {
                                        !enabled -> Theme.colours.contentDisabled
                                        ghost.selected -> colours.selectedContent
                                        else -> colours.content
                                    },
                                    foot = foot,
                                    height = height - inset,
                                    leading = leading?.let { slot -> @Composable { slot(gone) } },
                                    // Its × stays, so its title keeps the room it had;
                                    // pressed, it closes nothing, which has gone.
                                    close = if (ghost.showedClose) {
                                        {
                                            IconButton(
                                                onClick = {},
                                                icon = closeIcon,
                                                contentDescription = closeLabel(state.itemLabel(gone)),
                                                enabled = enabled,
                                                size = ButtonSize.XSmall,
                                                modifier = Modifier.focusProperties { canFocus = false },
                                            )
                                        }
                                    } else {
                                        null
                                    },
                                )
                            }
                        }
                    }
                },
            )

            // With no width to share — in a row that scrolls, or measured for its
            // natural size — as wide as its tabs at their widest: a lazy row
            // given no bound throws, and a weight given none is nothing.
            val bounded = constraints.hasBoundedWidth
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(if (bounded) Modifier.weight(1f, fill = false) else Modifier.width(share * keys.size + foot * 2)) {
                    LazyRow(
                        state = state.listState,
                        modifier = Modifier
                            .heightIn(min = height)
                            .dropList(state, enabled)
                            .fadingEdges(state.listState, Orientation.Horizontal)
                            .selectableGroup()
                            .semantics { collectionInfo = CollectionInfo(rowCount = 1, columnCount = keys.size) }
                            // Focus leaving the strip gives the stop back to the selected
                            // tab; not focus on its way from one tab to the next, which
                            // goes as the old tab stops being the stop.
                            .onFocusChanged { if (!it.hasFocus && strip.pendingFocus == null) strip.roving = null },
                        contentPadding = PaddingValues(horizontal = foot),
                        verticalAlignment = Alignment.Bottom,
                        flingBehavior = ScrollDefaults.flingBehavior(),
                    ) {
                        dragItems(state, options) { index, item ->
                            val key = state.key(item)
                            val previous = keys.getOrNull(index - 1)
                            val fresh = seeded && key !in knownKeys && key != held && !motion.reduceMotion
                            val grow = remember { Animatable(if (fresh) 0f else 1f) }
                            LaunchedEffect(Unit) { if (grow.value < 1f) grow.animateTo(1f, motion.sizeSpec()) }
                            val selected = key == selectedKey
                            val label = state.itemLabel(item)
                            val tabStop = key == (strip.roving ?: selectedKey ?: keys.firstOrNull())
                            val close = onClose?.let { closes -> { closes(item) } }
                            DocumentTab(
                                label = label,
                                index = index,
                                selected = selected,
                                enabled = enabled,
                                width = width,
                                grow = grow,
                                separates = index > 0 && !selected && previous != selectedKey &&
                                    key != strip.hovered && previous != strip.hovered,
                                body = body,
                                flared = flared,
                                foot = foot,
                                height = height,
                                colours = colours,
                                edge = edge,
                                focus = strip.requester(key),
                                tabStop = tabStop,
                                focusNow = strip.pendingFocus == key,
                                onFocused = { strip.pendingFocus = null },
                                onHovered = { hovered -> if (hovered) strip.hovered = key else if (strip.hovered == key) strip.hovered = null },
                                onSelect = { onValueChange(item) },
                                onClose = close?.let { shut ->
                                    { byPointer: Boolean ->
                                        if (byPointer && mouse) strip.frozen = width.value
                                        shut()
                                    }
                                },
                                closeLabel = closeLabel(label),
                                closeIcon = closeIcon,
                                showsCloseAt = showsCloseAt,
                                moves = { host, direction -> host.movesFor(sourceSpec(), strings, direction) },
                                onKey = { event -> keyed(event, index, item) },
                                leading = leading?.let { slot -> @Composable { slot(item) } },
                            )
                        }
                    }
                    // A strip that scrolls says so, along its top, where it
                    // never crosses the join with the page below.
                    Scrollbar(
                        state = state.listState,
                        modifier = Modifier.align(Alignment.TopStart).fillMaxWidth(),
                        orientation = Orientation.Horizontal,
                        // Disabled, the strip still scrolls, but says so in the
                        // disabled grey, as everything else on it does.
                        colour = if (enabled) Theme.colours.outlineStrong else Theme.colours.contentDisabled,
                        alwaysVisible = true,
                    )
                }
                if (onAdd != null) {
                    // In the square the tabs' share leaves it, in the middle of
                    // it: flush with the strip's end, its edge at high contrast
                    // ran into the strip's. And held as a tab's face holds its ×,
                    // below the gap at the strip's top in a box the face's height,
                    // so the two are level by construction, whatever a platform's
                    // touch target makes of either button.
                    Box(
                        Modifier.width(addRoom).height(height).padding(top = Theme.spacing.xs),
                        contentAlignment = Alignment.Center,
                    ) {
                        IconButton(
                            onClick = onAdd,
                            icon = addIcon,
                            contentDescription = addLabel,
                            enabled = enabled,
                            size = ButtonSize.Small,
                        )
                    }
                }
            }
        }
    }
}

/**
 * One tab: its ground, drawn only when selected and then out past its sides
 * into the feet that join it to the page; its separator from the tab before;
 * and, inset from the top of the strip, the tab a reader presses.
 */
@Composable
private fun DragItemScope.DocumentTab(
    label: String,
    index: Int,
    selected: Boolean,
    enabled: Boolean,
    width: State<Dp>,
    grow: Animatable<Float, AnimationVector1D>,
    separates: Boolean,
    body: Shape,
    flared: Shape,
    foot: Dp,
    height: Dp,
    colours: DocumentTabsColours,
    edge: BorderStroke?,
    focus: FocusRequester,
    tabStop: Boolean,
    focusNow: Boolean,
    onFocused: () -> Unit,
    onHovered: (Boolean) -> Unit,
    onSelect: () -> Unit,
    onClose: ((byPointer: Boolean) -> Unit)?,
    closeLabel: String,
    closeIcon: ImageVector,
    showsCloseAt: State<Boolean>,
    moves: (host: DragHost?, direction: LayoutDirection) -> List<CustomAccessibilityAction>,
    onKey: (KeyEvent) -> Boolean,
    leading: (@Composable () -> Unit)?,
) {
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    LaunchedEffect(hovered) { onHovered(hovered) }
    val host = LocalDragHost.current
    val direction = LocalLayoutDirection.current
    // Picked up by a pointer, a tab is chosen, as a browser's is.
    LaunchedEffect(isDragging) {
        if (isDragging && host?.session?.keyboard == false && !selected) onSelect()
    }
    LaunchedEffect(focusNow) {
        if (focusNow) {
            runCatching { focus.requestFocus() }
            onFocused()
        }
    }
    val inset = Theme.spacing.xs
    val hairline = Theme.sizing.borderWidth
    val colour = when {
        !enabled -> Theme.colours.contentDisabled
        selected -> colours.selectedContent
        else -> colours.content
    }
    val showsClose = selected || hovered || showsCloseAt.value

    Box(
        Modifier
            // A tab growing or shrinking shows as much of itself as it has room
            // for: clipped outside the narrowing, where its bounds are that room.
            .graphicsLayer { clip = grow.value < 1f; shape = RectangleShape }
            .tabWidth(width, grow)
            .heightIn(min = height)
            .drawBehind {
                val top = inset.toPx()
                val line = hairline.toPx()
                // Carried along its strip, the tab is still on it, and drawn as
                // it is there, feet and all. Pulled clear, it is a tab on its
                // own: a plain ground, with neither the feet that join it to the
                // page nor an edge, which the host draws round anything carried.
                // And grounded whenever it is carried, chosen or not, so it is
                // never a title floating over the others.
                val free = isDragging && host?.session?.riding == null
                if (isDragging && (free || !selected) && size.height > top) {
                    val ground = body.createOutline(Size(size.width, size.height - top), layoutDirection, this)
                    translate(top = top) { drawOutline(ground, colours.selectedContainer) }
                    return@drawBehind
                }
                // Nor does it bring the hairline that parted it from the tab before.
                if (separates && !isDragging) {
                    val x = if (layoutDirection == LayoutDirection.Ltr) 0f else size.width
                    val reach = (size.height - top) * SeparatorShare
                    val middle = top + (size.height - top) / 2
                    drawLine(colours.separator, Offset(x, middle - reach / 2), Offset(x, middle + reach / 2), line)
                }
                if (selected && size.height > top) {
                    val reach = foot.toPx()
                    val ground = Size(size.width + reach * 2, size.height - top)
                    val outline = flared.createOutline(ground, layoutDirection, this)
                    translate(left = -reach, top = top) {
                        drawOutline(outline, colours.selectedContainer)
                        if (edge != null) {
                            val w = edge.width.toPx()
                            // Not along the bottom, which is the page.
                            clipRect(top = -w, bottom = ground.height - w, left = -w, right = ground.width + w) {
                                drawOutline(outline, edge.brush, style = Stroke(w))
                            }
                        }
                    }
                }
            },
    ) {
        // As tall as the strip's tabs are, and no taller: a lazy row offers its
        // items all the height it was given, and a tab that filled it took
        // every pixel below the strip in a column with it.
        //
        // Pressed across the strip's whole height, the gap at its top
        // included, which is already a finger's: the touch target reserves
        // nothing more, and the tab is drawn at the same height on a phone as
        // on a desktop. Under the gap, the tab it draws — its focus ring, its
        // clip and its press — are the body's alone. Reserved below the gap,
        // the target made a phone's tabs 8dp taller than a desktop's.
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = height)
                .minimumTouchTarget()
                .focusRequester(focus)
                .focusProperties { canFocus = tabStop }
                .onKeyEvent(onKey)
                .pointerCursor(enabled = enabled)
                .webRole(WebRole.Tab, selected)
                .middleClick(enabled = enabled && onClose != null) { onClose?.invoke(true) }
                .selectable(
                    selected = selected,
                    interactionSource = interactions,
                    indication = null,
                    enabled = enabled,
                    role = Role.Tab,
                    onClick = onSelect,
                )
                .semantics {
                    collectionItemInfo = CollectionItemInfo(rowIndex = 0, rowSpan = 1, columnIndex = index, columnSpan = 1)
                    if (enabled) {
                        customActions = moves(host, direction) + listOfNotNull(
                            onClose?.let { close -> CustomAccessibilityAction(closeLabel) { close(false); true } },
                        )
                    }
                }
                .padding(top = inset)
                .focusRing(interactions, body)
                .clip(body.clipProxy())
                // No shrink: the tab rises into the page as it is chosen, and a
                // tab that also flinched would be two answers to one press.
                .indication(interactions, kontourIndication(body, pressScale = 1f)),
        ) {
            TabFace(
                label = label,
                colour = colour,
                foot = foot,
                height = height - inset,
                leading = leading,
                close = if (showsClose && onClose != null) {
                    {
                        IconButton(
                            onClick = { onClose(true) },
                            icon = closeIcon,
                            contentDescription = closeLabel,
                            enabled = enabled,
                            size = ButtonSize.XSmall,
                            // A tab's own ×, reached by pointer: the keyboard
                            // closes the focused tab with Delete, and a reader
                            // by the tab's "Close", so it is not a stop of its own.
                            modifier = Modifier.focusProperties { canFocus = false },
                        )
                    }
                } else {
                    null
                },
            )
        }
    }
}

/** A tab's face: its icon, its title fading where it is cut, and its ×. */
@Composable
private fun TabFace(
    label: String,
    colour: Color,
    foot: Dp,
    height: Dp,
    leading: (@Composable () -> Unit)?,
    close: (@Composable () -> Unit)?,
) {
    val spacing = Theme.spacing
    Row(
        Modifier.fillMaxWidth().heightIn(min = height).padding(start = foot + spacing.xxs, end = spacing.xxs),
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            ProvideContentColour(colour) {
                Box(Modifier.size(Theme.sizing.iconSmall), contentAlignment = Alignment.Center) { leading() }
            }
        }
        FadingText(label, Theme.typography.labelLarge, Modifier.weight(1f), colour)
        // No taller than the tab: a phone's touch target is 48dp, and the ×
        // reserving it grew the tab that showed it 8dp taller than the rest,
        // and the strip with it. It is pressed across the tab's height.
        if (close != null) Box(Modifier.heightIn(max = height), contentAlignment = Alignment.Center) { close() }
    }
}

/**
 * Laid out [grow] of the strip's tab [width] wide, its content measured at the
 * whole of it: a tab opening or closing slides out from under the next rather
 * than reflowing its title letter by letter.
 */
private fun Modifier.tabWidth(width: State<Dp>, grow: Animatable<Float, AnimationVector1D>): Modifier =
    layout { measurable, constraints ->
        val whole = width.value.roundToPx()
        val shown = (whole * grow.value.coerceIn(0f, 1f)).roundToInt()
        val placeable = measurable.measure(constraints.copy(minWidth = whole, maxWidth = whole))
        layout(shown, placeable.height) { placeable.placeRelative(0, 0) }
    }

/** [body] below the gap at the top of a strip: where a tab's ground is, and so where a carried tab's shadow is. */
private class BelowGap(private val body: Shape, private val gap: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val top = with(density) { gap.toPx() }.coerceIn(0f, size.height.coerceAtLeast(0f))
        val path = Path()
        path.addOutline(body.createOutline(Size(size.width, size.height - top), layoutDirection, density))
        path.translate(Offset(0f, top))
        return Outline.Generic(path)
    }

    override fun equals(other: Any?): Boolean = other is BelowGap && other.body == body && other.gap == gap

    override fun hashCode(): Int = 31 * body.hashCode() + gap.hashCode()
}

/** A strip's own memory: what it showed, what is leaving, and where its keyboard and pointer are. */
@Stable
private class StripState {
    var hovered: Any? by mutableStateOf(null)
    var frozen: Dp? by mutableStateOf(null)
    var roving: Any? by mutableStateOf(null)
    var pendingFocus: Any? by mutableStateOf(null)

    /** Each tab's key and item as the last composition applied them. */
    var known: List<Pair<Any, Any>> = emptyList()

    /** The selected tab's key, and the tabs' width, as the last composition applied them: what a closing tab is drawn as. */
    var selectedWas: Any? = null
    var widthWas: Dp = 0.dp
    var seeded = false
    val leaving = mutableStateListOf<Leaving>()
    private val focus = HashMap<Any, FocusRequester>()

    fun requester(key: Any): FocusRequester = focus.getOrPut(key) { FocusRequester() }
}

/**
 * A closed tab, still drawn where it was, shrinking: [after] is the key of the
 * tab it is before, or `null` at the end. [whole], [selected] and [showedClose]
 * are how it was drawn as it went, which it keeps until it has gone.
 */
private class Leaving(
    val key: Any,
    val item: Any,
    val after: Any?,
    val whole: Dp,
    val selected: Boolean,
    val showedClose: Boolean,
    val fraction: Animatable<Float, AnimationVector1D>,
)

private val TabMinWidth = 72.dp
private val TabMaxWidth = 240.dp

/** How far a pointer may pull a tab from its strip before it comes away. */
private val TearOff = 32.dp

/** From this wide, every tab shows its ×; narrower, only the selected and the hovered. */
private val CloseShownFrom = 112.dp

/** How much of a tab's height its separator from the one before runs. */
private const val SeparatorShare = 0.5f
