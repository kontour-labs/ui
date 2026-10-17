package io.kontour.ui.dragdrop

import androidx.compose.foundation.gestures.Orientation
import io.kontour.ui.locale.Quantity
import io.kontour.ui.locale.PluralCategory
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.ui.theme.Strings

/** A step a keyboard asks of a carried thing. */
internal enum class KeyStep { Back, Forward, PreviousTarget, NextTarget, First, Last }

/**
 * What [event] asks of a drag under way: a step, a drop or a cancel. `null` for
 * a key that is not the drag's, which then goes where it would have gone.
 *
 * Along the list the carried thing is in, the arrows that run along it; across,
 * to the previous or next target, the other two — left and right swapped right
 * to left.
 */
internal fun keyStepFor(event: KeyEvent, vertical: Boolean, layoutDirection: LayoutDirection): KeyStep? {
    if (event.type != KeyEventType.KeyDown) return null
    val rtl = layoutDirection == LayoutDirection.Rtl
    val left = if (rtl) KeyStep.Forward else KeyStep.Back
    val right = if (rtl) KeyStep.Back else KeyStep.Forward
    return when (event.key) {
        Key.DirectionUp -> if (vertical) KeyStep.Back else KeyStep.PreviousTarget
        Key.DirectionDown -> if (vertical) KeyStep.Forward else KeyStep.NextTarget
        Key.DirectionLeft -> if (vertical) (if (rtl) KeyStep.NextTarget else KeyStep.PreviousTarget) else left
        Key.DirectionRight -> if (vertical) (if (rtl) KeyStep.PreviousTarget else KeyStep.NextTarget) else right
        Key.MoveHome -> KeyStep.First
        Key.MoveEnd -> KeyStep.Last
        else -> null
    }
}

/** Whether [event] picks up the source it reaches: Space or Enter on a handle, Alt and an arrow anywhere. */
internal fun picksUp(event: KeyEvent, handle: Boolean): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    return when (event.key) {
        Key.Spacebar, Key.Enter, Key.NumPadEnter -> handle
        Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight -> event.isAltPressed
        else -> false
    }
}

/**
 * The keys of a keyboard carry, handled at the host whatever has the focus: the
 * source's own place may have folded away under it.
 *
 * Returns whether the key was the drag's.
 */
internal fun DragHost.handleKey(event: KeyEvent, layoutDirection: LayoutDirection): Boolean {
    val current = session ?: return false
    if (current.phase != DragPhase.Carrying) return false
    if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
        cancel()
        return true
    }
    if (!current.keyboard) return false
    if (event.type == KeyEventType.KeyDown && event.key in DropKeys) {
        putDown()
        return true
    }
    val vertical = current.target?.vertical ?: true
    val step = keyStepFor(event, vertical, layoutDirection) ?: return event.key in DragKeys
    keyStep(step, layoutDirection)
    return true
}

private val DropKeys = setOf(Key.Enter, Key.NumPadEnter, Key.Spacebar)
private val DragKeys = setOf(
    Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight, Key.MoveHome, Key.MoveEnd,
)

/** One step of a keyboard carry: along the target, or to the next one. */
internal fun DragHost.keyStep(step: KeyStep, layoutDirection: LayoutDirection) {
    val current = session ?: return
    val target = current.target
    val positions = target?.positions(current)
    when (step) {
        KeyStep.Back, KeyStep.Forward, KeyStep.First, KeyStep.Last -> {
            if (target == null || positions == null) {
                if (step == KeyStep.Back || step == KeyStep.First) moveToTarget(-1, layoutDirection) else moveToTarget(1, layoutDirection)
                return
            }
            val at = current.index ?: 0
            val next = when (step) {
                KeyStep.Back -> at - 1
                KeyStep.Forward -> at + 1
                KeyStep.First -> 0
                else -> positions - 1
            }.coerceIn(0, positions - 1)
            if (next != at || current.index == null) placeAt(target, next)
        }
        KeyStep.PreviousTarget -> moveToTarget(-1, layoutDirection)
        KeyStep.NextTarget -> moveToTarget(1, layoutDirection)
    }
}

/** To the target before or after the one it is over, in reading order. */
private fun DragHost.moveToTarget(by: Int, layoutDirection: LayoutDirection) {
    val current = session ?: return
    val order = reachable(current, layoutDirection)
    if (order.isEmpty()) return
    val here = order.indexOf(current.target)
    val next = order[if (here < 0) (if (by > 0) 0 else order.lastIndex) else (here + by).coerceIn(0, order.lastIndex)]
    if (next === current.target) return
    val keep = current.index
    current.target?.leave()
    current.target = next
    current.index = null
    val positions = next.positions(current)
    if (positions != null) placeAt(next, (keep ?: positions - 1).coerceIn(0, positions - 1)) else placed(next)
}

private fun DragHost.placeAt(target: DropTarget, index: Int) {
    val current = session ?: return
    target.place(current, index)
    placed(target)
}

private fun DragHost.placed(target: DropTarget) {
    val current = session ?: return
    for (each in targets) {
        each.statusState = when {
            each.type !== current.type -> DropStatus.Idle
            each === target -> if (each.accepts(current.payload)) DropStatus.Over else DropStatus.Refused
            each.accepts(current.payload) -> DropStatus.Available
            else -> DropStatus.Idle
        }
    }
    onSnap()
    announce(target.positionText(current))
}

/**
 * The targets a carried thing can be moved to, in reading order: by rows, then
 * along each, as a reader would take them — right to left in a right-to-left
 * layout.
 */
internal fun DragHost.reachable(current: DragSession, layoutDirection: LayoutDirection): List<DropTarget> =
    targets
        .filter { it.enabled && it.type === current.type && it.layer >= blockedBelow && it.boundsInRoot() != null }
        .sortedWith(
            compareBy<DropTarget> { (it.boundsInRoot() ?: Rect.Zero).top.toInt() / RowBand }
                .thenBy { (it.boundsInRoot() ?: Rect.Zero).left.let { left -> if (layoutDirection == LayoutDirection.Rtl) -left else left } },
        )

/**
 * The targets a screen reader is offered a move to: every one [reachable] would
 * give, and those not yet laid out besides, after them.
 *
 * Not [reachable] itself, which wants bounds to order by: the moves are worked
 * out as the item attaches, before the list beside it has been placed, and a
 * target with no place yet is still somewhere to move to.
 */
internal fun DragHost.offered(current: DragSession, layoutDirection: LayoutDirection): List<DropTarget> {
    val placed = reachable(current, layoutDirection)
    return placed + targets.filter {
        it !in placed && it.enabled && it.type === current.type && it.layer >= blockedBelow
    }
}

/** Targets whose tops are this close, in pixels, read as one row. */
private const val RowBand = 48

/**
 * The moves a screen reader offers for a source: straight to each other target
 * that would take it, and — for an item in a list — up and down its own list.
 *
 * Each happens at once, with no carry: [DragHost.dropAt].
 */
internal fun DragHost?.movesFor(
    spec: SourceSpec,
    strings: Strings,
    layoutDirection: LayoutDirection,
): List<CustomAccessibilityAction> = buildList {
    val list = spec.fromList
    val index = spec.fromIndex
    if (list != null && index != null) {
        // A row says which way across, as its reader reads it: back is left,
        // and right to left it is right. It said up and down, a column's
        // words, for a row of tabs. The orientation is read here, in the
        // semantics block, where a row that has just said it is one is seen.
        val across = list.orientation == Orientation.Horizontal
        val rtl = layoutDirection == LayoutDirection.Rtl
        val back = when {
            !across -> strings.moveUp
            rtl -> strings.moveRight
            else -> strings.moveLeft
        }
        val forward = when {
            !across -> strings.moveDown
            rtl -> strings.moveLeft
            else -> strings.moveRight
        }
        if (index > 0) add(CustomAccessibilityAction(back) { this@movesFor.dropAt(spec, list.target, index - 1) })
        if (index < list.items.size - 1) {
            add(CustomAccessibilityAction(forward) { this@movesFor.dropAt(spec, list.target, index + 1) })
        }
    }
    val host = this@movesFor ?: return@buildList
    // Read here, in the semantics block, which is what Compose observes: a
    // target that attaches later — a column a `LazyRow` lays out after this
    // card's — or one that leaves offers the moves again. Read in composition
    // it did nothing, since the block it fed was the same block every time.
    host.attached.size
    val probe = DragSession.probe(spec)
    for (target in host.offered(probe, layoutDirection)) {
        if (target === list?.target || !target.accepts(spec.payload)) continue
        add(CustomAccessibilityAction(strings.moveTo(target.label)) { host.dropAt(spec, target, null) })
    }
}

/**
 * Puts [spec]'s thing in [target] at once, at [index] or the end: a screen
 * reader's move, or anything else that needs no carry. Works with or without a
 * drag host, as nothing is drawn.
 */
internal fun DragHost?.dropAt(spec: SourceSpec, target: DropTarget, index: Int?): Boolean {
    val session = DragSession.probe(spec)
    if (!target.accepts(spec.payload)) return false
    val positions = target.positions(session)
    if (positions != null) target.place(session, (index ?: (positions - 1)).coerceIn(0, positions - 1))
    if (!target.changes(session)) {
        target.leave()
        return false
    }
    target.drop(session)
    target.leave()
    this?.announce(strings.dragDropped(spec.label, target.label))
    return true
}

/**
 * What a screen reader hears as a drag goes, from a node that is always there:
 * a polite live region, so a pick-up, each place and the drop are read out in
 * turn without taking the reader's focus anywhere.
 */
@Composable
internal fun DragAnnouncer(host: DragHost) {
    val said = host.said
    Box(
        Modifier
            .size(1.dp)
            .semantics {
                liveRegion = LiveRegionMode.Polite
                if (said.isNotEmpty()) contentDescription = said
            },
    )
}

/** "3 of 5", in the reader's numbers — or in plain digits, before the layer has read the theme's. */
internal fun DragHost.position(index: Int, count: Int): String {
    val numbers = formats
    fun quantity(n: Int) = numbers?.quantity(n) ?: Quantity(n.toLong(), n.toString(), PluralCategory.Other)
    return strings.itemOfCount(quantity(index + 1), quantity(count))
}
