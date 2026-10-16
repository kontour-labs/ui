package io.kontour.ui.dragdrop

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.theme.Theme
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * What is carried, by name. A source and a target meet only on the same type,
 * so a card cannot be dropped into a list of stops however it is held.
 *
 * ```kotlin
 * val Card = DragType<Card>("card")
 * ```
 *
 * Compared by identity: two types with the same name are two types. Declare
 * each once, at the top level, beside what it carries.
 */
@Immutable
class DragType<T : Any>(val name: String) {
    override fun toString(): String = "DragType($name)"
}

/**
 * How a press becomes a pick-up.
 *
 * - [Auto]: straight away with a mouse, after a hold with a finger — a finger's
 *   drag is a scroll until it has held still long enough to mean otherwise.
 * - [Immediate]: on the pointer's first movement, for a handle or anything that
 *   has no scroll to steal.
 * - [LongPress]: after a hold, whatever the pointer is.
 */
enum class DragStart { Auto, Immediate, LongPress }

/**
 * Where a drop target stands with the drag under way.
 *
 * - [Idle]: nothing is being carried that it could take.
 * - [Available]: something it could take is being carried elsewhere — the time
 *   to show where it could go.
 * - [Over]: the thing is over it, and would be taken.
 * - [Refused]: the thing is over it, and would not be: the right type, turned
 *   away by `accepts`.
 */
enum class DropStatus { Idle, Available, Over, Refused }

/**
 * A drop into a list: [payload], put at [index].
 *
 * [index] is the position it ends at, counted **as if it had already left the
 * list it came from**. So a move within one list is
 * `items.removeAt(fromIndex); items.add(index, payload)`, and a move between two
 * is the remove from the one and the add to the other. [fromList] and
 * [fromIndex] say where it came from: both `null` for something that was not in
 * a list, [fromList] the same state as the target's for a move within a list.
 * [fromIndex] is where the payload is in [fromList] as it is dropped, found by
 * its key, so a list that changed while it was carried is still right to
 * `removeAt`.
 */
@Immutable
class DropEvent<T : Any> internal constructor(
    val payload: T,
    val index: Int,
    val fromIndex: Int?,
    val fromList: DropListState<*>?,
)

/** The look of a drag, for anything built on the same parts. */
object DragAndDropDefaults {
    /** The shape a carried thing's shadow is cast in, when nothing says otherwise. */
    val previewShape: Shape
        @Composable @ReadOnlyComposable get() = Theme.shapes.medium
}

/** How far a carried thing lifts off the page: the shadow under it, as a reordered row's. */
internal val DragLift: Dp = 8.dp

/** How much bigger a carried thing is drawn than it sits: lifted, not grown. */
internal const val DragPreviewScale: Float = 1.03f

/** How close to a scrolling region's edge a carried thing starts it scrolling. */
internal val AutoScrollEdge: Dp = 64.dp

/** How fast a region scrolls with the carried thing at its very edge, per second. */
internal val AutoScrollMaxSpeed: Dp = 1600.dp

/**
 * How long a region takes to reach the speed its edge asks for:
 * a carried thing passing through an edge on its way somewhere does not throw
 * the list.
 */
internal val AutoScrollRamp: Duration = 400.milliseconds

/**
 * The state of a plain drop target: a bin, a column header, a folder. A list
 * that takes drops at a position is a [DropListState].
 *
 * Made by [rememberDropTargetState] and attached with [dropTarget].
 */
@Stable
class DropTargetState<T : Any> internal constructor(
    type: DragType<T>,
    label: String,
) {
    internal var onDrop: (T) -> Unit = {}
    internal var accepting: (T) -> Boolean = { true }

    /** Its terms for drags from outside the app, and what reads them. */
    internal var external: ExternalDrops? = null
    internal var reader: DropReader? = null

    /** Where a drag from outside the app stands with this target. */
    internal var externalStatus: DropStatus by mutableStateOf(DropStatus.Idle)

    internal val externalHandle = ExternalDropHandle(
        external = { external },
        reader = { reader },
        status = { externalStatus = it },
        over = {},
        index = { null },
    )

    internal val target: DropTarget = object : DropTarget(type, label) {
        @Suppress("UNCHECKED_CAST")
        override fun accepts(payload: Any): Boolean = accepting(payload as T)

        @Suppress("UNCHECKED_CAST")
        override fun drop(session: DragSession) {
            onDrop(session.payload as T)
        }
    }

    /** What it is called, for "Move to …" and for what is announced. */
    val label: String get() = target.label

    /**
     * Where this target stands with whatever is being carried: something of its
     * own type in the app, or a drag from outside it that [ExternalDrops] takes.
     */
    val status: DropStatus get() = externalStatus.takeIf { it != DropStatus.Idle } ?: target.statusState

    /** Whether something it would take is over it. */
    val isOver: Boolean get() = status == DropStatus.Over
}

/**
 * A plain drop target's state.
 *
 * ```kotlin
 * val bin = rememberDropTargetState(Card, label = "Archive", onDrop = { archive(it) })
 * Box(Modifier.dropTarget(bin)) { … }
 * ```
 *
 * @param label What the target is called, for the move a screen reader offers —
 *   "Move to Archive" — and for what is announced as something comes over it.
 * @param onDrop Called once, with the payload, when one is let go over this.
 * @param accepts Whether this would take [T]: the right type and still turned
 *   away — a full column, a card already there. Refused, it shows
 *   [DropStatus.Refused] and the thing goes home.
 * @param external Drags from outside the app it takes — files from the desktop,
 *   text from another window — and what to do with them. `null`, the default,
 *   takes none.
 */
@Composable
fun <T : Any> rememberDropTargetState(
    type: DragType<T>,
    label: String,
    onDrop: (T) -> Unit,
    accepts: (T) -> Boolean = { true },
    external: ExternalDrops? = null,
): DropTargetState<T> {
    val state = remember(type) { DropTargetState(type, label) }
    state.target.label = label
    state.onDrop = onDrop
    state.accepting = accepts
    state.external = external
    state.reader = if (external != null) rememberDropReader() else null
    if (external != null) LocalExternalDropProbe.current?.invoke(state)
    return state
}

/**
 * Hears each target that takes drags from outside the app as it composes: for
 * tests, which cannot make a platform drag.
 */
internal val LocalExternalDropProbe = staticCompositionLocalOf<((DropTargetState<*>) -> Unit)?> { null }

/**
 * The state of a target for drags from outside the app only: an upload area
 * that takes files from the desktop, a field that takes a link from another
 * window. It takes nothing dragged within the app — there is no type to drag.
 *
 * ```kotlin
 * val upload = rememberDropTargetState(
 *     label = "Attachments",
 *     external = ExternalDrops.files { files, _ -> attach(files) },
 * )
 * DropZone(upload) { +"Drop files here" }
 * ```
 */
@Composable
fun rememberDropTargetState(label: String, external: ExternalDrops): DropTargetState<Nothing> =
    rememberDropTargetState(OutsideOnly, label, onDrop = {}, external = external)

/** The type of a target that takes only drags from outside the app: no source carries it. */
internal val OutsideOnly = DragType<Nothing>("outside")

/**
 * Takes drops for [state] over this element's bounds.
 *
 * Inside another target, the innermost takes the drop; in an overlay — a sheet,
 * a dialog — it outranks the page under it, and the page behind a dimmed modal
 * takes nothing.
 */
fun Modifier.dropTarget(state: DropTargetState<*>, enabled: Boolean = true): Modifier =
    this.registersDropTarget(state.target, enabled)
        .externalDrops(state.externalHandle, enabled)

/**
 * Makes this element carry [payload] when dragged.
 *
 * ```kotlin
 * Card(Modifier.dragSource(CardType, card, label = card.title)) { … }
 * ```
 *
 * Needs an [io.kontour.ui.overlay.OverlayHost] above it, which draws the thing
 * being carried: without one this does nothing, and says so once in the log.
 *
 * @param label What the carried thing is called, for what is announced as it
 *   is picked up and put down.
 * @param start How a press becomes a pick-up. See [DragStart].
 * @param interactionSource Told of the press, as a clickable's is.
 */
@Composable
fun <T : Any> Modifier.dragSource(
    type: DragType<T>,
    payload: T,
    label: String,
    enabled: Boolean = true,
    start: DragStart = DragStart.Auto,
    interactionSource: MutableInteractionSource? = null,
): Modifier {
    val current by rememberUpdatedState(SourceSpec(type, payload, label, null, null))
    val handle = remember { SourceHandle() }
    return this
        .carriedElement(handle, list = false, shape = DragAndDropDefaults.previewShape)
        .pickUpGesture(
            handle = handle,
            spec = { current },
            enabled = enabled,
            start = start,
            interactionSource = interactionSource,
        )
}

/** What a source carries, read when it is picked up rather than when it was composed. */
internal class SourceSpec(
    val type: DragType<*>,
    val payload: Any,
    val label: String,
    val fromList: DropListState<*>?,
    val fromIndex: Int?,
)
