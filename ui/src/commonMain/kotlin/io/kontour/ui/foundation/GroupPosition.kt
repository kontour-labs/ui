package io.kontour.ui.foundation

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp

/**
 * Where something sits in a run of its kind, which decides which of its corners
 * round.
 *
 * One answer for every group the library draws — the rows of a list, the
 * buttons of a group, the bubbles of one sender's messages. They were three
 * enums with the same four entries, so a position worked out for one could not
 * be handed to another, and each carried its own copy of [of].
 *
 * A run reads as one object with parts in it rather than a stack of separate
 * ones: only its outside corners round, and the ones facing a neighbour go
 * square.
 */
@Immutable
enum class GroupPosition {
    /** The only one. All four corners round. */
    Only,

    /** The first of several. */
    First,

    /** Between two others. Square all round. */
    Middle,

    /** The last of several — which, in a run of messages, carries the tail. */
    Last,
    ;

    companion object {
        /**
         * Where item [index] of [count] sits.
         *
         * ```kotlin
         * itemsIndexed(stops) { index, stop ->
         *     ListItem(
         *         onClick = { open(stop) },
         *         position = GroupPosition.of(index, stops.size),
         *     ) { +stop.name }
         * }
         * ```
         */
        fun of(index: Int, count: Int): GroupPosition = when {
            count <= 1 -> Only
            index == 0 -> First
            index == count - 1 -> Last
            else -> Middle
        }

        /**
         * Where item [index] of [items] sits, where a run is consecutive items
         * that [runOf] says belong together — the same sender, for a chat.
         */
        fun <T> of(items: List<T>, index: Int, runOf: (T) -> Any?): GroupPosition {
            val who = runOf(items[index])
            val before = index > 0 && runOf(items[index - 1]) == who
            val after = index < items.lastIndex && runOf(items[index + 1]) == who
            return when {
                before && after -> Middle
                before -> Last
                after -> First
                else -> Only
            }
        }
    }
}

/**
 * [shape] with the corners that face a neighbour squared to [square].
 *
 * `start`/`end` rather than left/right, so the first of a row rounds the corners
 * the reader starts from in either direction.
 *
 * Pure, and tested, because an off-by-one here is the kind of thing that looks
 * fine on a three-item list in the catalog and wrong on every one-item list in
 * the app.
 *
 * @param orientation Which way the run goes. A list is a column; a button group
 *   is a row unless it is a `VerticalButtonGroup`.
 */
fun GroupPosition.shape(
    shape: CornerBasedShape,
    square: Dp,
    orientation: Orientation,
): CornerBasedShape {
    val flat = CornerSize(square)
    return when (this) {
        GroupPosition.Only -> shape
        GroupPosition.Middle ->
            shape.copy(topStart = flat, topEnd = flat, bottomStart = flat, bottomEnd = flat)

        GroupPosition.First -> when (orientation) {
            Orientation.Horizontal -> shape.copy(topEnd = flat, bottomEnd = flat)
            Orientation.Vertical -> shape.copy(bottomStart = flat, bottomEnd = flat)
        }

        GroupPosition.Last -> when (orientation) {
            Orientation.Horizontal -> shape.copy(topStart = flat, bottomStart = flat)
            Orientation.Vertical -> shape.copy(topStart = flat, topEnd = flat)
        }
    }
}
