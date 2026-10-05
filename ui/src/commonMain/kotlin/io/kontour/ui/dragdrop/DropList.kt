package io.kontour.ui.dragdrop

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.ui.theme.Theme

/**
 * A lazy list that takes drops at a position, and whose items can be carried —
 * within it, to another list, or to any other target of the same type.
 *
 * ```kotlin
 * val todo = rememberDropListState(todoScroll, Card, cards, key = { it.id },
 *     label = "To do", itemLabel = { it.title }) { drop ->
 *     board.move(drop.payload, from = drop.fromList, to = todo, at = drop.index)
 * }
 * LazyColumn(state = todoScroll, modifier = Modifier.dropList(todo)) {
 *     dragItems(todo) { _, card -> CardRow(card) }
 * }
 * ```
 *
 * Made by [rememberDropListState], attached with [dropList], filled with
 * [dragItems].
 */
@Stable
class DropListState<T : Any> internal constructor(
    /** The list's own scroll state, which a carried thing near its ends scrolls. */
    val listState: LazyListState,
    internal val type: DragType<T>,
    label: String,
) {
    internal val id: Int = nextListId++
    internal var items: List<T> by mutableStateOf(emptyList())
    internal var key: (T) -> Any = { it }
    internal var itemLabel: (T) -> String = { it.toString() }
    internal var onDrop: (DropEvent<T>) -> Unit = {}
    internal var accepting: (T) -> Boolean = { true }
    internal var canDrag: (T) -> Boolean = { true }
    internal var shape: Shape? = null
    internal var layoutDirection: LayoutDirection = LayoutDirection.Ltr

    /** The host, for which of this list's own items is carried. */
    internal var host: DragHost? = null

    /** Its terms for drags from outside the app, and what reads them. */
    internal var external: ExternalDrops? = null
    internal var reader: DropReader? = null

    /** Where a drag from outside the app stands with this list. */
    internal var externalStatus: DropStatus by mutableStateOf(DropStatus.Idle)

    /**
     * Where a drag from outside the app would land, where the platform says
     * where it is: an insertion line rather than a gap, since there is no size
     * to make room for.
     */
    internal var externalIndex: Int? by mutableStateOf(null)

    /** The insertion line's colour, read from the theme as the state is remembered. */
    internal var lineColour: Color = Color.Unspecified

    /**
     * Which way the list runs, as its last layout said. State, set from layout
     * rather than read from [LazyListState.layoutInfo] as items compose: a
     * composition that reads the layout info is invalidated by every layout,
     * and a list whose caller cannot skip recomposing then lays out and
     * recomposes for ever. This changes once at most, for a row.
     */
    internal var orientation: Orientation by mutableStateOf(Orientation.Vertical)

    /** Reads [orientation] off the list once it is placed. */
    internal val readOrientation: (LayoutCoordinates) -> Unit = {
        val info = listState.layoutInfo
        if (info.totalItemsCount > 0 && info.orientation != orientation) orientation = info.orientation
    }

    /** Takes the layout direction the list is laid out in. */
    internal val readDirection: (LayoutDirection) -> Unit = { layoutDirection = it }

    /** Draws the line a drag from outside the app would land at. */
    internal val drawInsertion: ContentDrawScope.() -> Unit = {
        drawContent()
        val index = externalIndex?.takeIf { externalStatus == DropStatus.Over }
        val along = index?.let { insertionAt(it) }
        if (along != null) {
            val stroke = InsertionLine.toPx()
            if (orientation == Orientation.Vertical) {
                drawLine(lineColour, Offset(0f, along), Offset(size.width, along), stroke)
            } else {
                val x = if (layoutDirection == LayoutDirection.Rtl) size.width - along else along
                drawLine(lineColour, Offset(x, 0f), Offset(x, size.height), stroke)
            }
        }
    }

    internal val externalHandle = ExternalDropHandle(
        external = { external },
        reader = { reader },
        status = { externalStatus = it },
        over = { externalOver(it) },
        index = { externalIndex },
    )

    /** The key of one of this list's own items while it is carried; `null` while none is. */
    @Suppress("UNCHECKED_CAST")
    internal val carriedKey: Any?
        get() = host?.session?.takeIf { it.fromList === this }?.let { key(it.payload as T) }

    /** Whether the carried item is on its way home, when its own place opens again for it to land in. */
    internal val carriedHome: Boolean
        get() = host?.session?.takeIf { it.fromList === this }?.phase == DragPhase.Returning

    /** Where the gap is, counted without the carried item. */
    internal var gapIndex: Int? by mutableStateOf(null)

    /** How long the gap is along the list, in pixels: the carried thing's size. */
    internal var gapSize: Int by mutableIntStateOf(0)

    internal val gapKey: String = "kontour.dropGap.$id"

    internal val target: DropTarget = object : DropTarget(type, label) {
        @Suppress("UNCHECKED_CAST")
        override fun accepts(payload: Any): Boolean = accepting(payload as T)

        override fun over(session: DragSession, pointerInRoot: Offset) = this@DropListState.over(session)

        override fun leave() {
            gapIndex = null
        }

        override fun changes(session: DragSession): Boolean =
            !(session.fromList === this@DropListState && gapIndex == session.fromIndex)

        override fun landingBounds(session: DragSession): Rect? = this@DropListState.gapBounds(session)

        override fun positionText(session: DragSession): String {
            val index = gapIndex ?: return label
            val host = host ?: return label
            return host.strings.dragPosition(label, host.position(index, count(session) + 1))
        }

        override val vertical: Boolean
            get() = listState.layoutInfo.orientation == Orientation.Vertical

        override fun positions(session: DragSession): Int = count(session) + 1

        override fun place(session: DragSession, index: Int) {
            if (session.size != androidx.compose.ui.unit.IntSize.Zero) {
                gapSize = if (vertical) session.size.height else session.size.width
            }
            gapIndex = index
            session.index = index
        }

        @Suppress("UNCHECKED_CAST")
        override fun drop(session: DragSession) {
            val index = gapIndex ?: return
            onDrop(DropEvent(session.payload as T, index, session.fromIndex, session.fromList))
        }
    }

    /** What the list is called, for "Move to …" and for what is announced. */
    val label: String get() = target.label

    /**
     * Where this list stands with whatever is being carried: something of its
     * own type in the app, or a drag from outside it that [ExternalDrops] takes.
     */
    val status: DropStatus get() = externalStatus.takeIf { it != DropStatus.Idle } ?: target.statusState

    /**
     * Where a drop would land while something is over this list — counted as
     * [DropEvent.index] is, without the carried item — and `null` otherwise.
     */
    val dropIndex: Int? get() = gapIndex ?: externalIndex

    /**
     * Where a drag from outside the app at [pointerInRoot] would land: before the
     * first item whose middle is past it. `null`, where the platform does not say
     * where the drag is, leaves it to the caller.
     */
    internal fun externalOver(pointerInRoot: Offset?) {
        val coordinates = target.coordinates?.takeIf { it.isAttached }
        if (pointerInRoot == null || coordinates == null) {
            externalIndex = null
            return
        }
        val info = listState.layoutInfo
        val vertical = info.orientation == Orientation.Vertical
        val local = coordinates.windowToLocal(pointerInRoot)
        var probe = if (vertical) local.y else local.x
        if (!vertical && layoutDirection == LayoutDirection.Rtl) probe = coordinates.size.width - probe
        probe -= info.beforeContentPadding
        val positions = HashMap<Any, Int>(items.size)
        items.forEachIndexed { index, item -> positions[key(item)] = index }
        var landing: Int? = null
        var lastSeen: Int? = null
        for (item in info.visibleItemsInfo) {
            val index = positions[item.key] ?: continue
            lastSeen = index
            if (landing == null && probe < item.offset + item.size / 2f) landing = index
        }
        externalIndex = (landing ?: lastSeen?.plus(1) ?: 0).coerceIn(0, items.size)
    }

    /** Where the insertion line for [externalIndex] is drawn, along the list from its start; `null` if not on screen. */
    internal fun insertionAt(index: Int): Float? {
        val info = listState.layoutInfo
        val positions = HashMap<Any, Int>(items.size)
        items.forEachIndexed { i, item -> positions[key(item)] = i }
        val visible = info.visibleItemsInfo.filter { it.key in positions }
        val before = visible.firstOrNull { positions[it.key] == index }
        val after = visible.lastOrNull { positions[it.key] == index - 1 }
        val at = when {
            before != null && after != null -> (after.offset + after.size + before.offset) / 2f
            before != null -> before.offset.toFloat()
            after != null -> (after.offset + after.size).toFloat()
            else -> return null
        }
        return at + info.beforeContentPadding
    }

    /** How many items there are, not counting one of its own being carried. */
    private fun count(session: DragSession): Int =
        items.size - if (session.fromList === this) 1 else 0

    /**
     * Works out where the carried thing would land: before the first of this
     * list's items whose centre is past the carried thing's own centre.
     *
     * Measured against the list as it is drawn, gap and all, which is what
     * makes a place stick once it is taken. With the gap above an item, that
     * item sits a gap's length further down, so the carried thing has to pass
     * the middle of where it *is* to move below it; once below, the item has
     * moved up out of the way, and the thing has to come back past its middle
     * again to return. Measured as if the gap were not there — tried first —
     * a card held still in its own place sat past the middle of the item below,
     * which had moved up into the place it left, and took the next place on the
     * first wobble. The carried item's own place, folded to nothing, counts for
     * nothing.
     */
    private fun over(session: DragSession) {
        val coordinates = target.coordinates?.takeIf { it.isAttached } ?: return
        val info = listState.layoutInfo
        val vertical = info.orientation == Orientation.Vertical
        val reversed = !vertical && layoutDirection == LayoutDirection.Rtl
        val origin = coordinates.positionInRoot()
        val centre = session.topLeft + Offset(session.size.width / 2f, session.size.height / 2f)
        var probe = if (vertical) centre.y - origin.y else centre.x - origin.x
        if (reversed) probe = coordinates.size.width - probe
        probe -= info.beforeContentPadding

        gapSize = if (vertical) session.size.height else session.size.width
        val from = if (session.fromList === this) session.fromIndex else null
        val positions = HashMap<Any, Int>(items.size)
        items.forEachIndexed { index, item -> positions[key(item)] = index }

        var landing: Int? = null
        var lastSeen: Int? = null
        for (item in info.visibleItemsInfo) {
            val index = positions[item.key] ?: continue
            if (index == from) continue
            val place = if (from != null && index > from) index - 1 else index
            lastSeen = place
            if (landing == null && probe < item.offset + item.size / 2f) landing = place
        }
        val count = count(session)
        val index = (landing ?: lastSeen?.plus(1) ?: gapIndex ?: 0).coerceIn(0, count)
        gapIndex = index
        session.index = index
    }

    /**
     * Where a landing here flies to, in the root: the gap — or, for one of this
     * list's own items, its own place, which it holds where it would land.
     * Read from the list's layout rather than from the item, whose drawn
     * position may still be on its way there.
     */
    @Suppress("UNCHECKED_CAST")
    private fun gapBounds(session: DragSession): Rect? {
        val coordinates = target.coordinates?.takeIf { it.isAttached } ?: return null
        val info = listState.layoutInfo
        val slot = if (session.fromList === this) key(session.payload as T) else gapKey
        val gap = info.visibleItemsInfo.firstOrNull { it.key == slot } ?: return null
        val vertical = info.orientation == Orientation.Vertical
        val origin = coordinates.positionInRoot()
        val start = (gap.offset + info.beforeContentPadding).toFloat()
        val size = Size(session.size.width.toFloat(), session.size.height.toFloat())
        val topLeft = when {
            vertical -> Offset(origin.x, origin.y + start)
            layoutDirection == LayoutDirection.Rtl -> Offset(origin.x + coordinates.size.width - start - gap.size, origin.y)
            else -> Offset(origin.x + start, origin.y)
        }
        return Rect(topLeft, size)
    }

    private companion object {
        var nextListId = 0
    }
}

/**
 * A drop list's state.
 *
 * @param listState The lazy list's own state, which a carried thing near its
 *   ends scrolls.
 * @param items What the list shows, in order — the caller's list, which stays the
 *   one source of truth: nothing moves in it until [onDrop] moves it.
 * @param key A stable key for each item, as `items(key = …)` takes.
 * @param label What the list is called: "Move to Doing", and "Doing, 3 of 5" as
 *   a carried thing comes over it.
 * @param itemLabel What each item is called, for what is said as it is picked up.
 * @param onDrop One call per drop: what, where, and where it came from. A drop
 *   back where it started is not one, and calls nothing.
 * @param accepts Whether this list would take [T] — a column at its limit, a list
 *   that holds no duplicates.
 * @param canDrag Whether an item can be picked up at all.
 * @param previewShape The shape its items' shadows are cast in while carried.
 * @param external Drags from outside the app it takes — files from the desktop,
 *   text from another window — and what to do with them. `null`, the default,
 *   takes none. An insertion line shows where one would land, where the
 *   platform says where the drag is.
 */
@Composable
fun <T : Any> rememberDropListState(
    listState: LazyListState,
    type: DragType<T>,
    items: List<T>,
    key: (T) -> Any,
    label: String,
    itemLabel: (T) -> String,
    onDrop: (DropEvent<T>) -> Unit,
    accepts: (T) -> Boolean = { true },
    canDrag: (T) -> Boolean = { true },
    previewShape: Shape = DragAndDropDefaults.previewShape,
    external: ExternalDrops? = null,
): DropListState<T> {
    val state = remember(listState, type) { DropListState(listState, type, label) }
    state.host = LocalDragHost.current
    state.external = external
    state.reader = if (external != null) rememberDropReader() else null
    state.lineColour = Theme.colours.accent.solid
    state.items = items
    state.key = key
    state.target.label = label
    state.itemLabel = itemLabel
    state.onDrop = onDrop
    state.accepting = accepts
    state.canDrag = canDrag
    state.shape = previewShape
    return state
}

/**
 * Takes drops for [state] across this lazy list, at the position the carried
 * thing is over, and scrolls it when the thing is held near either end.
 * Put it on the `LazyColumn` or `LazyRow` whose state [state] holds.
 */
fun Modifier.dropList(state: DropListState<*>, enabled: Boolean = true): Modifier =
    this.registersDropTarget(state.target, enabled)
        .dragAutoScroll(state.listState, state.orientation, enabled)
        .readsLayoutDirection(state.readDirection)
        .onGloballyPositioned(state.readOrientation)
        .externalDrops(state.externalHandle, enabled)
        .drawWithContent(state.drawInsertion)

/** How thick the line is that shows where a drag from outside the app would land. */
private val InsertionLine = 2.dp

/**
 * [state]'s items, each one draggable, with room opened where a carried thing
 * would land.
 *
 * ```kotlin
 * LazyColumn(state = scroll, modifier = Modifier.dropList(stops)) {
 *     dragItems(stops) { index, stop ->
 *         ListItem {
 *             +stop.name
 *             trailing { Icon(Grip, null, Modifier.dragHandle()) }
 *         }
 *     }
 * }
 * ```
 *
 * A press held on an item picks it up, or a mouse's drag; a press on its
 * [DragItemScope.dragHandle] picks it up at once. The neighbours slide aside as
 * the gap moves.
 *
 * A carried item over its own list **is** the gap: it keeps its full size,
 * unseen, at the place it would land, so the list makes exactly the room the
 * item will take. Carried anywhere else, its place folds away — and the folded
 * item goes to the end of the list, where the space a list leaves after every
 * item, folded or not, falls after the last card rather than between two.
 */
fun <T : Any> LazyListScope.dragItems(
    state: DropListState<T>,
    contentType: (T) -> Any? = { null },
    itemContent: @Composable DragItemScope.(index: Int, item: T) -> Unit,
) {
    val items = state.items
    val gap = state.gapIndex
    val carried = state.carriedKey
    fun emit(index: Int, item: T, folded: Boolean) {
        item(key = state.key(item), contentType = contentType(item)) {
            DragItem(state, index, item, folded, itemContent)
        }
    }
    val carriedAt = if (carried == null) -1 else items.indexOfFirst { state.key(it) == carried }
    if (carriedAt < 0 || state.carriedHome) {
        // Nothing of its own carried, or on its way back to its own place:
        // every item where it is, and a gap for something from elsewhere.
        var gapped = false
        items.forEachIndexed { index, item ->
            if (gap != null && !gapped && index == gap) {
                gapItem(state)
                gapped = true
            }
            emit(index, item, folded = false)
        }
        if (gap != null && !gapped) gapItem(state)
        return
    }
    // One of its own carried: the others in order, with the carried item at the
    // gap if it would land here and folded away at the end if not.
    var place = 0
    var placed = false
    items.forEachIndexed { index, item ->
        if (index == carriedAt) return@forEachIndexed
        if (gap != null && !placed && place == gap) {
            emit(carriedAt, items[carriedAt], folded = false)
            placed = true
        }
        emit(index, item, folded = false)
        place++
    }
    if (!placed) emit(carriedAt, items[carriedAt], folded = gap == null)
}

/** What an item of [dragItems] can do besides draw itself. */
@Stable
interface DragItemScope {
    /** Whether this item is the one being carried. */
    val isDragging: Boolean

    /**
     * Makes this element a handle: a press on it picks the item up at once,
     * without the hold a finger otherwise needs — a grip has no scroll to steal.
     */
    @Composable
    fun Modifier.dragHandle(): Modifier
}

private class DragItemScopeImpl<T : Any>(
    private val state: DropListState<T>,
    val handle: SourceHandle,
) : DragItemScope {
    var index by mutableIntStateOf(0)
    var item: T? by mutableStateOf(null)
    override var isDragging: Boolean by mutableStateOf(false)

    /** How many handles the item's content has, so the item knows whether to offer the moves itself. */
    var handles by mutableIntStateOf(0)

    fun spec(): SourceSpec {
        val what = item!!
        return SourceSpec(state.type, what, state.itemLabel(what), state, index)
    }

    @Composable
    override fun Modifier.dragHandle(): Modifier {
        DisposableEffect(Unit) {
            handles++
            onDispose { handles-- }
        }
        return this
            .pickUpGesture(
                handle = handle,
                spec = ::spec,
                enabled = item?.let(state.canDrag) ?: false,
                start = DragStart.Immediate,
                interactionSource = null,
                grip = true,
            )
            .grabCursor()
    }
}

@Composable
private fun <T : Any> LazyItemScope.DragItem(
    state: DropListState<T>,
    index: Int,
    item: T,
    folded: Boolean,
    content: @Composable DragItemScope.(index: Int, item: T) -> Unit,
) {
    val handle = remember { SourceHandle() }
    val scope = remember(state, handle) { DragItemScopeImpl(state, handle) }
    scope.index = index
    scope.item = item
    val host = LocalDragHost.current
    scope.isDragging = host?.session?.source === handle
    // A thing a keyboard put down here takes the focus back, wherever it went.
    val focus = remember { FocusRequester() }
    LaunchedEffect(host?.refocus) {
        if (host != null && host.refocus == item) {
            host.refocus = null
            runCatching { focus.requestFocus() }
        }
    }
    val vertical = state.orientation == Orientation.Vertical
    // Carried, it is not drawn, and goes straight to each place it holds: what
    // a landing flies to is where it is, not where it is on its way to.
    val still = Theme.motion.reduceMotion || scope.isDragging
    androidx.compose.foundation.layout.Box(
        Modifier
            .then(if (still) Modifier.animateItem(placementSpec = null) else Modifier.animateItem())
            .foldedAway(folded, vertical)
            .focusRequester(focus)
            .focusGroup()
            .carriedElement(handle, list = true, shape = state.shape ?: DragAndDropDefaults.previewShape)
            .pickUpGesture(
                handle = handle,
                spec = scope::spec,
                enabled = state.canDrag(item),
                start = DragStart.Auto,
                interactionSource = null,
                // A handle offers them inside the row a reader lands on.
                offersMoves = scope.handles == 0,
            ),
    ) {
        scope.content(index, item)
    }
}

/** The room opened where a carried thing would land. */
private fun <T : Any> LazyListScope.gapItem(state: DropListState<T>) {
    item(key = state.gapKey, contentType = "kontour.dropGap") {
        val density = LocalDensity.current
        val length = with(density) { state.gapSize.toDp() }
        val vertical = state.orientation == Orientation.Vertical
        Spacer(
            Modifier
                .then(if (Theme.motion.reduceMotion) Modifier.animateItem(placementSpec = null) else Modifier.animateItem())
                .then(if (vertical) Modifier.size(width = Theme.spacing.xxs, height = length) else Modifier.size(width = length, height = Theme.spacing.xxs)),
        )
    }
}

/**
 * Measured as it is, and laid out as nothing along the list while it is carried:
 * its place folds away, so the gap is the only room the list makes for it, and
 * it still draws — into the layer the carried thing is drawn from — at its full
 * size.
 */
private fun Modifier.foldedAway(folded: Boolean, vertical: Boolean): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val width = if (folded && !vertical) 0 else placeable.width
    val height = if (folded && vertical) 0 else placeable.height
    layout(width, height) { placeable.place(0, 0) }
}
