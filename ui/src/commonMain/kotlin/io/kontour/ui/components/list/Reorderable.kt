package io.kontour.ui.components.list

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.kontour.ui.foundation.GroupPosition
import io.kontour.ui.foundation.shape
import io.kontour.ui.motion.enterOrFade
import io.kontour.ui.motion.exitOrFade
import io.kontour.ui.a11y.minimumTouchTarget
import io.kontour.ui.foundation.Icon
import io.kontour.ui.input.Cursor
import io.kontour.ui.input.LocalInputModality
import io.kontour.ui.input.heldCursor
import io.kontour.ui.input.pointerCursor
import io.kontour.ui.motion.AnimatedSlot
import io.kontour.ui.motion.SlotGap
import io.kontour.ui.interaction.FeedbackIntent
import io.kontour.ui.interaction.LandingFeedback
import io.kontour.ui.interaction.PickUpAxis
import io.kontour.ui.interaction.detectPickUp
import io.kontour.ui.interaction.rememberLandingFeedback
import io.kontour.ui.interaction.rememberLongPressFeedback
import io.kontour.ui.interaction.rememberDetentTicker
import androidx.compose.animation.core.Animatable
import io.kontour.ui.theme.Theme
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Drives drag-to-reorder over a `LazyColumn`.
 *
 * ```kotlin
 * val listState = rememberLazyListState()
 * val reorder = rememberReorderableState(listState) { from, to ->
 *     viewModel.move(from, to)
 * }
 *
 * LazyColumn(state = listState) {
 *     itemsIndexed(favourites, key = { _, it -> it.id }) { index, favourite ->
 *         ReorderableItem(reorder, index) {
 *             ListItem { +favourite.name }
 *         }
 *     }
 * }
 * ```
 *
 * [onMove] fires *during* the drag, every time the dragged row passes another —
 * so the list reorders live under the finger rather than snapping into place on
 * release. That means the caller's list is the source of truth throughout, and
 * there is no separate "pending order" to reconcile.
 */
@Stable
class ReorderableState internal constructor(
    internal val listState: LazyListState,
    private val onMove: (from: Int, to: Int) -> Unit,
    private val onReorder: () -> Unit = {},
) {
    /** The index being dragged, or null. */
    var draggingIndex: Int? by mutableStateOf(null)
        private set

    internal var dragOffset by mutableFloatStateOf(0f)

    private var draggingKey: Any? = null

    /**
     * The rows in the list that can be reordered, as each [ReorderableItem]
     * composed in it reports its index.
     *
     * A drag lands only on these. It used to land on any item the list had on
     * screen, so a list with a header or a footer — an item that is not a row
     * of the caller's collection — reported the last row carried over the
     * footer as a move to one past the end, and the `add(to, removeAt(from))`
     * every caller writes threw.
     *
     * One holder per item, whose index it keeps current, rather than a set of
     * indices: two rows trading places each add the other's index and remove
     * their own, and in a set whichever went second took the first one's away.
     * Plain, not snapshot state: only a drag reads it, never a composition.
     */
    private val rows = mutableSetOf<RowIndex>()

    /** One [ReorderableItem]'s index, as the list last composed it. */
    internal class RowIndex(var index: Int)

    internal fun register(row: RowIndex) {
        rows += row
    }

    internal fun unregister(row: RowIndex) {
        rows -= row
    }

    /**
     * Whether the item at [index] is a row that can be reordered. Every item is,
     * in a list with no [ReorderableItem] in it — one driven through [start] and
     * [drag] alone — as it always was.
     */
    private fun isRow(index: Int): Boolean = rows.isEmpty() || rows.any { it.index == index }

    /**
     * Begins a drag on the row at [index], as a long press would.
     *
     * Public because a state a caller cannot reach is a state nobody can
     * verify. Until this was, the only way into a drag was the gesture, so the
     * lifted row could not be tested, photographed for the documentation, or
     * driven from a keyboard affordance an app wanted to add. Pair with [stop].
     */
    fun start(index: Int) {
        draggingIndex = index
        draggingKey = itemAt(index)?.key
        dragOffset = 0f
    }

    /**
     * Moves an in-progress drag by [delta] pixels. Reorders as it crosses rows.
     *
     * A move can happen more than once in a single drag — that is the whole
     * point — and each one is reported through [onReorder] so the caller can
     * click. The row crossing another is the moment the user is waiting to
     * feel; the pickup and the drop are the ones they can already see.
     */
    fun drag(delta: Float) {
        val index = draggingIndex ?: return
        dragOffset += delta

        val dragged = itemAt(index) ?: return
        val draggedCentre = dragged.offset + dragOffset + dragged.size / 2f

        // The row the dragged one is now sitting over: a row, not a header or a
        // footer the list holds besides them.
        val target = listState.layoutInfo.visibleItemsInfo.firstOrNull { candidate ->
            candidate.index != index &&
                isRow(candidate.index) &&
                draggedCentre >= candidate.offset &&
                draggedCentre <= candidate.offset + candidate.size
        } ?: return

        onMove(index, target.index)
        // The list has reordered underneath us; the dragged row is now where
        // the target was, so the visual offset shrinks by the distance moved.
        dragOffset -= (target.offset - dragged.offset)
        draggingIndex = target.index
        onReorder()
    }

    /** Ends the drag and settles the row. */
    fun stop() {
        draggingIndex = null
        draggingKey = null
        dragOffset = 0f
    }

    internal fun offsetFor(index: Int): Float =
        if (index == draggingIndex) dragOffset else 0f

    private fun itemAt(index: Int): LazyListItemInfo? =
        listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }

    /** Moves the item at [index] one place toward the start. For assistive tech. */
    internal fun moveUp(index: Int): Boolean {
        if (index <= 0) return false
        onMove(index, index - 1)
        return true
    }

    /** Moves the item at [index] one place toward the end. */
    internal fun moveDown(index: Int, count: Int): Boolean {
        if (index >= count - 1) return false
        onMove(index, index + 1)
        return true
    }
}

/**
 * Remembers a [ReorderableState] for the list in [listState]. [onMove] is
 * called each time the lifted row passes another, with where it was and where
 * it now is.
 */
@Composable
fun rememberReorderableState(
    listState: LazyListState,
    onMove: (from: Int, to: Int) -> Unit,
): ReorderableState {
    val move by rememberUpdatedState(onMove)
    // Every row crossed snaps, the way a sheet's detents do. A reorder is a
    // discrete event happening under a finger that is not looking for it — the
    // user is watching the row they are holding, not the gap it just left.
    //
    // `Snap`, a resting place: the row has somewhere new to land. Through a
    // ticker whose index only ever goes up — one per gap crossed, across every
    // drag — so it is one shared mechanism rather than a call of its own, and
    // is never rate-limited: a crossing the hand did not feel is a row the eye
    // has to go looking for.
    val crossings = rememberDetentTicker(FeedbackIntent.Snap)
    return remember(listState, crossings) {
        var crossed = 0
        ReorderableState(
            listState = listState,
            onMove = { from, to -> move(from, to) },
            onReorder = {
                if (crossed == 0) crossings.at(0)
                crossed++
                crossings.at(crossed)
            },
        )
    }
}

/** Which side of a row its drag handle sits on. */
enum class ReorderHandleSide { Start, End }

/**
 * Wraps one row so it can be dragged to a new position.
 *
 * ```kotlin
 * LazyColumn(state = listState) {
 *     itemsIndexed(favourites, key = { _, it -> it.id }) { index, favourite ->
 *         ReorderableItem(reorder, index, itemCount = favourites.size) {
 *             ListItem { +favourite.name }
 *         }
 *     }
 * }
 * ```
 *
 * **A `LazyItemScope` extension**, and it always was in everything but its
 * signature: it reads the row's position out of a `LazyListState`, which knows
 * nothing about a row that is not in the list. Being one is what lets it call
 * `animateItem()` on the rows that are *not* being dragged, which is how the
 * gap left behind gets filled by neighbours sliding into it rather than
 * teleporting.
 *
 * ### How a drag starts
 *
 * With a [handleIcon], from the handle, immediately. Without one, from a **long
 * press** on the row — on touch. A mouse gets neither: it presses and drags,
 * because that is what a mouse does, and holding a button still for half a
 * second to pick up a row is a gesture nobody has ever tried. Dragging a lazy
 * list with a mouse is not a thing desktops do, so there is nothing for an
 * immediate drag to fight with there. On the row it goes a few dp first, and
 * mostly up or down, so a click on the row is still a click and a drag sideways
 * still reaches [SwipeActions] inside it.
 *
 * The dragged row lifts — scaled slightly, shadowed, and above its neighbours —
 * because a row that moves without lifting reads as the list glitching rather
 * than as the user holding something.
 *
 * **Move up and move down are also custom accessibility actions.** A drag is not
 * a gesture a screen reader can perform, and reordering with no alternative
 * makes a whole feature unreachable. Pass [itemCount] so "move down" can be
 * withheld on the last row.
 *
 * @param index The row's index **in the `LazyColumn`**, which is also what
 *   `onMove` is given. In a list of rows and nothing else that is the row's
 *   index in the collection; with a header item above them it is one more, and
 *   `onMove` takes the header off both before it moves anything. An item that is
 *   not a `ReorderableItem` — a header, a footer, a "load more" — is never where
 *   a drag lands, so a row carried over one stays where it was.
 * @param shape The row's own shape, for the shadow it casts while lifted.
 *   Null — the default — derives it from [index] and [itemCount], which is what
 *   decides a row's corners everywhere else here: a first row is rounded on top,
 *   a middle row is square, and the shape morphs between them when a drag
 *   changes the row's position.
 *
 *   Worth knowing why this is not simply a shape you pass. A `graphicsLayer`
 *   shadow is drawn to the *layer's* shape, a rectangle unless told otherwise,
 *   so a rounded row casts a square shadow with corners sticking out past it.
 *   That was the default, and no caller anywhere passed anything else — a
 *   parameter a component needs in order to look right is a parameter that will
 *   not be supplied. Pass one only to override.
 * @param handleIcon A grip to drag from. Null leaves the whole row draggable,
 *   which is the better default on touch and the worse one anywhere a row also
 *   has a tap action of its own.
 * @param handleSide Which end the handle sits at. [ReorderHandleSide.End] by
 *   default: a handle on the leading edge competes with whatever the row leads
 *   with, which is usually an icon or an avatar.
 *
 *   The handle takes no label. It used to take one, and the label reached
 *   nothing — see [ReorderGrip].
 */
@Composable
fun LazyItemScope.ReorderableItem(
    state: ReorderableState,
    index: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    itemCount: Int = Int.MAX_VALUE,
    shape: Shape? = null,
    handleIcon: ImageVector? = null,
    handleSide: ReorderHandleSide = ReorderHandleSide.End,
    moveUpLabel: String = Theme.strings.moveUp,
    moveDownLabel: String = Theme.strings.moveDown,
    content: @Composable () -> Unit,
) {
    val landing = rememberLandingFeedback()
    val longPressed = rememberLongPressFeedback()
    val motion = Theme.motion

    // The shadow's shape, derived from where the row sits rather than passed in.
    //
    // It was `RectangleShape` by default and **no call site passed anything**, so
    // every reorderable list drew a square shadow under rows whose top and bottom
    // corners are rounded. A parameter that has to be supplied for the component
    // to look right is a parameter that will not be supplied; `index` and
    // `itemCount` are already here, and they are what decides a row's corners
    // everywhere else in the library.
    //
    // Animated across a position change, which is the other half of the report:
    // dragging the second row to the top has to *become* a first row, and the row
    // it displaced has to lose its rounding as it becomes a middle one. Morphing
    // from the last settled shape rather than snapping is what makes the two read
    // as one movement.
    val base = ListItemDefaults.Shape
    val inner = ListItemDefaults.InnerCorner
    val position = GroupPosition.of(index, itemCount)
    var settled by remember { mutableStateOf(position) }
    val morph = remember { Animatable(1f) }
    LaunchedEffect(position) {
        if (position != settled) {
            morph.snapTo(0f)
            morph.animateTo(1f, motion.springOrTween(motion.springDefault))
            settled = position
        }
    }
    // Quantised, so a row morphing between two positions is one of thirteen
    // shapes rather than a new one per frame — and this one feeds a
    // `graphicsLayer`'s `shape` below, so a rebuild takes the shadow's blur with
    // it. See `rememberMorphedShape`.
    val morphed = rememberMorphedShape(
        from = settled.shape(base, inner, Orientation.Vertical),
        to = position.shape(base, inner, Orientation.Vertical),
        fraction = morph.value,
    )
    val shadowShape = shape ?: morphed
    val modality = LocalInputModality.current
    // Derived, so a crossing recomposes the two rows it changes — the one that
    // stopped being dragged and the one that started — rather than every row on
    // screen, all of which read the index.
    val dragging by remember(state, index) { derivedStateOf { state.draggingIndex == index } }

    // Read in composition because a theme colour has to be; painted in the draw
    // phase above. `surface` rather than the list's own ground: a row that has
    // been picked up is off the list and on top of it.
    val liftedColour = Theme.colours.surface

    /**
     * The row's index, read at gesture time rather than captured.
     *
     * This is the whole of the "it drops after one position" report. The
     * gesture used to be keyed on `index` — and reordering *changes* a row's
     * index, which is the gesture succeeding. So the first move restarted the
     * `pointerInput` node, which cancelled the drag that had just caused it. One
     * position, then dropped, every time, and never on the second attempt
     * because by then the finger was already down.
     */
    val currentIndex by rememberUpdatedState(index)

    // Says to the state that this item is a row a drag may land on. See
    // `ReorderableState.rows`.
    val row = remember(state) { ReorderableState.RowIndex(index) }
    SideEffect { row.index = index }
    DisposableEffect(state, row) {
        state.register(row)
        onDispose { state.unregister(row) }
    }

    val lift by animateFloatAsState(
        targetValue = if (dragging) 1f else 0f,
        animationSpec = motion.springOrTween(motion.springSnappy),
        label = "reorderLift",
    )

    // Read at gesture time, exactly like `currentIndex` above and for exactly the
    // same reason — see `reorderDrag`.
    //
    // It used to read `handleIcon != null || !modality.needsLargeTargets`, one
    // value for the whole row, because a handle meant the row itself was not
    // draggable at all. Now that both are, the question splits: **the row** waits
    // for a long press on a touchscreen, because a lazy list has a scroll to
    // steal; **the handle** never does, because a grip has nothing to steal.
    val pointerImmediate by rememberUpdatedState(!modality.needsLargeTargets)

    val rowDrags = Modifier.reorderDrag(
        state = state,
        enabled = enabled,
        immediate = { pointerImmediate },
        currentIndex = { currentIndex },
        landing = landing,
        longPressed = longPressed,
        decidesForMouse = true,
    )

    val handleDrags = Modifier.reorderDrag(
        state = state,
        enabled = enabled,
        immediate = { true },
        currentIndex = { currentIndex },
        landing = landing,
        longPressed = longPressed,
        decidesForMouse = false,
    )

    Box(
        modifier = modifier
            // Not on the row being dragged: it is following a finger, and a
            // placement animation would have it chasing itself. Every other row
            // is being *moved* by the reorder, which is exactly what this is
            // for — the gap the dragged row left gets filled by its neighbours
            // sliding into it rather than appearing there.
            //
            // Under reduced motion they take their places at once: a row
            // sliding into a gap is movement the finger did not make.
            .then(
                when {
                    dragging -> Modifier
                    motion.reduceMotion -> Modifier.animateItem(placementSpec = null)
                    else -> Modifier.animateItem()
                }
            )
            .zIndex(if (dragging) 1f else 0f)
            .graphicsLayer {
                translationY = state.offsetFor(index)
                // A small lift, not a large one. The row is still in the list.
                //
                // On the row rather than on the card below, so the grip travels
                // and grows with what it is holding — a handle that stayed put
                // while the card came up would read as two objects.
                scaleX = 1f + 0.02f * lift
                scaleY = 1f + 0.02f * lift
            }
            // **Held, the whole row is the grip.** The row follows the pointer
            // vertically, but the pointer is free to wander sideways off a 24dp
            // grip, or past the end of the list where the row stops — and a
            // grabbing cursor that lived on the grip alone went back to the arrow
            // the moment it did. A cursor belongs to the drag rather than to
            // whatever the pointer is over, which is how every desktop's own
            // drag-and-drop behaves; overriding the row's children is the nearest
            // Compose has to that.
            .heldCursor(dragging)
            // Withheld while disabled, as the drag is: a row that cannot be
            // carried offered a screen reader move up and move down regardless,
            // and moved when asked.
            .semantics {
                if (enabled) {
                    customActions = buildList {
                        if (index > 0) {
                            add(CustomAccessibilityAction(moveUpLabel) { state.moveUp(index) })
                        }
                        if (index < itemCount - 1) {
                            add(
                                CustomAccessibilityAction(moveDownLabel) {
                                    state.moveDown(index, itemCount)
                                }
                            )
                        }
                    }
                }
            }
            // Always, handle or not.
            //
            // This used to be `if (handleIcon == null)`, and the reasoning was
            // sound as far as it went: a row that is also a link cannot afford
            // to swallow a long press. What it missed is that asking for a
            // handle does not stop the row being a row — every platform's own
            // editable list lets you drag by either — so turning the grip on
            // took the whole row's gesture away with it.
            //
            // The long press is what keeps the link safe, and the row still
            // waits for one. The handle is the part that does not.
            .then(rowDrags)
    ) {
        // **One `Row` whether or not there is a grip**, where this used to branch
        // on `handleIcon == null` and put the content straight in the `Box`. A
        // lone `weight(1f)` child of a `Row` is the width the `Box` gave it, so
        // the handleless case draws exactly what it drew — and a grip that
        // animates in has to have something to animate *within*.
        Row(verticalAlignment = Alignment.CenterVertically) {
            ReorderGripSlot(
                icon = handleIcon,
                side = ReorderHandleSide.Start,
                handleSide = handleSide,
                enabled = enabled,
                drags = handleDrags,
            )
            Box(
                Modifier
                    .weight(1f)
                    // **The lift is cast by the card, not by the row.**
                    //
                    // Both of these used to sit on the outer `Box`, which is full
                    // width with a grip or without one — the content is a
                    // `weight(1f)` sibling of the handle. So a lifted row with a
                    // handle drew its shadow around a rectangle wider than the
                    // card anybody can see, and the strip beside the grip was
                    // shadow with nothing on top of it. Reported twice: once as
                    // the gap, and again after the gap was filled with
                    // `colours.surface`, which on a light page is the same white
                    // as the page — so the fix changed nothing visible and the
                    // shadow still looked like it was drawn around thin air.
                    //
                    // Measured from the content's own box, the shadow hugs what
                    // the reader calls the card, the grip sits beside it on the
                    // list, and the fill lands exactly under whatever the content
                    // paints. `ReorderShadowTest` holds both halves.
                    .graphicsLayer {
                        // In *pixels*, which `graphicsLayer` does not say and this
                        // used to assume otherwise: a bare `8f` is 8dp at 1x, 4 at
                        // 2x and 2.7 at 3x, so the lift got shallower the better
                        // the screen. Nothing caught it because every golden is
                        // rendered at one density.
                        shadowElevation = ReorderLift.toPx() * lift
                        // Without this the shadow is a rectangle whatever the row
                        // is.
                        this.shape = shadowShape
                        clip = false
                    }
                    // Under the content, so a `ListItem` with its own surface
                    // still wins, and ramped on the lift so a row at rest is
                    // exactly as it was — this paints nothing at all until
                    // something picks the row up. What it is for is a row whose
                    // content paints no ground of its own: a bare `Text` picked up
                    // out of a list should be a card, not a floating word.
                    //
                    // `drawBehind` rather than `background(…)`: `lift` is read
                    // inside the lambda, so a row being picked up invalidates its
                    // draw and not its composition. The outline comes back from
                    // `SquirclePaths` after the first frame.
                    .drawBehind {
                        val raised = lift
                        if (raised <= 0f) return@drawBehind
                        drawOutline(
                            outline = shadowShape.createOutline(size, layoutDirection, this),
                            color = liftedColour,
                            alpha = raised,
                        )
                    }
            ) {
                // A row held to lift it: swipe actions inside wait out a held
                // fingertip's wander before they take it for a swipe.
                CompositionLocalProvider(LocalHeldToPickUp provides { enabled && !pointerImmediate }) { content() }
            }
            ReorderGripSlot(
                icon = handleIcon,
                side = ReorderHandleSide.End,
                handleSide = handleSide,
                enabled = enabled,
                drags = handleDrags,
            )
        }
    }
}

/**
 * A grip that arrives and leaves from the edge it lives on.
 *
 * Turning handles on used to be a layout jump: `handleIcon == null` chose between
 * the content filling the row and the content being a `weight(1f)` sibling of a
 * grip, so a row lost 48dp of width between two frames and the reader saw the
 * text reflow rather than a handle appear.
 *
 * [AnimatedSlot] is the house answer to an appearing row item and it is used here
 * for its *other* property as much as for the animation: the width being
 * interpolated is the grip's own, so the content's `weight(1f)` remainder is
 * interpolated with it and the card's width animates without a second animation
 * to keep in step. There is no gap to carry — a grip is
 * `minimumTouchTarget()` wide around a 20dp glyph, so it brings its own
 * breathing room — which is why [AnimatedSlot]'s `gap` is zero here rather than
 * a spacing token.
 *
 * **It expands from its own side.** A trailing grip grows leftward out of the
 * right-hand edge and a leading one rightward out of the left, which reads as the
 * handle sliding in from outside the row rather than being pushed out of the
 * middle of it.
 *
 * `springOrTween` on the width and a plain tween on the alpha, matching
 * `FloatingActionButton`'s extended label exactly: the same shape of change, and
 * a second curve for it would be a second thing to tune.
 *
 * @param icon Null when there is no handle. The last non-null one is held, so the
 *   exit animation has a glyph to draw on its way out — without that, turning
 *   handles off collapses an empty box and the icon vanishes on frame one.
 * @param side Which end of the row this slot is. There is one at each end and at
 *   most one of them is ever visible, so moving [handleSide] across animates the
 *   old grip out and the new one in for free.
 */
@Composable
private fun ReorderGripSlot(
    icon: ImageVector?,
    side: ReorderHandleSide,
    handleSide: ReorderHandleSide,
    enabled: Boolean,
    drags: Modifier,
) {
    val motion = Theme.motion
    val held = remember { mutableStateOf(icon) }
    if (icon != null) held.value = icon
    val shown = held.value
    val from = if (side == ReorderHandleSide.Start) Alignment.Start else Alignment.End

    AnimatedSlot(
        visible = icon != null && handleSide == side,
        gap = 0.dp,
        side = if (side == ReorderHandleSide.Start) SlotGap.Trailing else SlotGap.Leading,
        // Under reduced motion the grip fades where it will sit and the row
        // takes its new width at once: the card's text reflowing sideways as the
        // grip grows is the movement.
        enter = motion.enterOrFade(
            expandHorizontally(motion.springOrTween(motion.springDefault), from) + fadeIn(motion.tweenFast()),
        ),
        exit = motion.exitOrFade(
            shrinkHorizontally(motion.springOrTween(motion.springDefault), from) + fadeOut(motion.tweenFast()),
        ),
    ) {
        if (shown != null) ReorderGrip(shown, enabled, drags)
    }
}

/**
 * The grip itself: a target, not a picture.
 *
 * `minimumTouchTarget` around a small glyph, because a handle is the one part of
 * a reorderable row that has to be hit deliberately — and it is `clearAndSet`
 * rather than merged, because the row already carries move-up and move-down as
 * custom actions and a screen reader has no use for a third route that needs a
 * drag.
 *
 * **So it takes no label, and the icon's description is null.** There used to be
 * a `handleLabel` parameter, defaulting to "Move up", passed down here as the
 * icon's `contentDescription` — and then erased one node up by the
 * `clearAndSetSemantics` above it. A public parameter that could not reach
 * anything, documented nowhere, called from nowhere, announcing the wrong word
 * if it ever had. The clearing is the part that is right: the row's two custom
 * actions are the accessible route, and this is decoration on top of them.
 */
@Composable
private fun ReorderGrip(
    icon: ImageVector,
    enabled: Boolean,
    drags: Modifier,
) {
    Box(
        modifier = Modifier
            .minimumTouchTarget()
            .clearAndSetSemantics { }
            // The grip only, not the row: a row's content may be a link, whose
            // hand a grab cursor across the whole row would hide. The grip is the
            // part that is only ever dragged, so it says so. Once the row is
            // lifted, the row's own [heldCursor] takes over.
            .pointerCursor(Cursor.Grab, enabled = enabled)
            .then(drags),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) Theme.colours.contentMuted else Theme.colours.contentDisabled,
            size = Theme.sizing.iconMedium,
        )
    }
}

/**
 * The drag gesture, on the row or on its handle.
 *
 * [immediate] is the difference between a mouse and a fingertip, and between a
 * handle and a bare row. A long press exists to keep a drag from stealing a
 * scroll; neither a mouse over a lazy list nor a press on a dedicated grip has a
 * scroll to steal, so neither should have to wait half a second for one.
 *
 * [decidesForMouse] is the row's, not the handle's: a mouse on a whole row
 * goes as far as a swipe decides on before it is a pick-up, and only mostly up
 * or down, so a click with a pixel of jitter in it is still a click and a drag
 * sideways is still a swipe. A grip has nothing inside it to lose that way.
 */
private fun Modifier.reorderDrag(
    state: ReorderableState,
    enabled: Boolean,
    immediate: () -> Boolean,
    currentIndex: () -> Int,
    landing: LandingFeedback,
    longPressed: () -> Unit,
    decidesForMouse: Boolean,
): Modifier = if (!enabled) {
    this
} else {
    // Keyed on the state alone — not on the index, and **not on which gesture to
    // use** either.
    //
    // `immediate` was a key here, and it is derived from `LocalInputModality`,
    // which `trackInputModality` rewrites on every pointer event on the Initial
    // pass — including the one that starts this gesture. The local defaults to
    // `Touch`, so on a desktop the first mouse press of a session *is* a change:
    // the key moved, the node restarted, and `awaitEachGesture` went back to
    // wanting a fresh `awaitFirstDown` that could not come until the finger
    // lifted. Reported as "picking it up doesn't always work", on a first attempt
    // that fails and a second that does not.
    //
    // It is the same mistake `currentIndex` exists to avoid, one parameter over,
    // and the note there says so in as many words. A gesture node must not be
    // keyed on anything that changes *during* a gesture; what changes gets read
    // when the gesture asks for it.
    this.pointerInput(state) {
        // Whether *this* node is the one holding the row.
        //
        // The row and its handle are two of these over overlapping areas, and
        // both see every down — `awaitFirstDown(requireUnconsumed = false)`.
        // Movement sorts them out by itself, because the winner consumes and the
        // long-press loop gives up the moment its finger travels. A press held
        // *still* on the grip does not: the row's half-second would elapse, fire
        // a `LongPress`, and start a drag the grip is about to start again.
        //
        // So starting is conditional on nobody having the row, and everything
        // after it is conditional on this node having been the one that took it.
        // Without the second half a node that never started would still call
        // `stop()` on the drag that did.
        var owned = false
        // Whether the row passed another. A pick-up let go of where it was
        // landed nowhere new, and says so by saying nothing.
        var crossed = false
        // Whether a hold is what is picking it up now, from `onHeld`.
        var held = false

        val onStart: (Offset) -> Unit = {
            if (state.draggingIndex == null) {
                owned = true
                // Only where a long press is what started it. `LongPress`
                // announces that a threshold was reached and the row is now
                // yours to move — on the [immediate] path there is no threshold
                // to announce, and firing it there was a haptic for a
                // mouse-down on a grip. Told by the detector rather than read
                // off [immediate], which a mouse after a touch still answers
                // as a finger.
                if (held) longPressed()
                crossed = false
                state.start(currentIndex())
            }
            held = false
        }
        val onDrag: (PointerInputChange, Offset) -> Unit = { change, amount ->
            if (owned) {
                change.consume()
                val before = state.draggingIndex
                state.drag(amount.y)
                if (state.draggingIndex != before) crossed = true
            }
        }
        val onEnd: () -> Unit = {
            if (owned) {
                owned = false
                // Only after a move: a row picked up and put straight back has
                // not landed anywhere. See `LandingFeedback` for what it plays.
                if (crossed) landing.landed()
                state.stop()
            }
        }
        detectPickUp(
            axis = PickUpAxis.Vertical,
            immediate = immediate,
            onPickUp = onStart,
            onCarry = onDrag,
            onPutDown = onEnd,
            onCancel = { if (owned) { owned = false; state.stop() } },
            // Once lifted, the row's alone: its own swipe actions, inside it,
            // cannot take the carry. See `SwipeReorderTest`.
            exclusive = true,
            decidesForMouse = decidesForMouse,
            onHeld = { held = true },
        )
    }
}

/**
 * Whether the row something is in can be held to pick it up — a reorderable row
 * on a touchscreen — read when a press lands. For swipe actions inside one,
 * which wait out a held fingertip's wander before claiming a swipe.
 */
internal val LocalHeldToPickUp = staticCompositionLocalOf<() -> Boolean> { { false } }

/**
 * How far the dragged row lifts off the list.
 *
 * A `Dp`, because `graphicsLayer.shadowElevation` is in pixels and the number
 * that was there was neither — a raw `8f` that meant 8dp on a 1x desktop and
 * 2.7dp on a 3x phone, which is most of the lift gone on the device the gesture
 * is actually made on.
 */
private val ReorderLift: Dp = 8.dp
