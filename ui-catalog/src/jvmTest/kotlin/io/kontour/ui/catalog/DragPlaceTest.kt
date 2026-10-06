package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.dragdrop.DragType
import io.kontour.ui.dragdrop.DropEvent
import io.kontour.ui.dragdrop.DropListState
import io.kontour.ui.dragdrop.dragItems
import io.kontour.ui.dragdrop.dropList
import io.kontour.ui.dragdrop.rememberDropListState
import io.kontour.ui.overlay.OverlayHost
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A carried card's place in a list with space between its cards — every list
 * the Board app has — is the card's own size and the space after it, no more.
 *
 * Reported as a jump: *"picking up an item is fine (except the shadow 'jumps' a
 * bit) … in the last little bit, it just 'snaps'"*. A carried card used to fold
 * to nothing where it was and a gap open where it would land. A folded card is
 * still an item, and a list puts its spacing after every item, so the list grew
 * by one spacing for as long as the card was carried: the cards under it moved
 * down by that much as it was picked up, and the gap it flew to was that much
 * below where it ended up, so it landed and then jumped.
 */
class DragPlaceTest {

    @Test
    fun pickingACardUpMovesNothingUnderIt() {
        val board = SpacedBoard(todo = listOf("Timetable", "Fares", "Signage", "Lifts"), done = listOf("Maps"))
        board.run { scene ->
            val before = board.bounds.getValue("Signage").top
            scene.pickUp(board.bounds.getValue("Fares").center)
            scene.frames(30)
            val after = board.bounds.getValue("Signage").top
            assertTrue(
                abs(after - before) < 0.5f,
                "the card under the one picked up moved from $before to $after: a carried card's place grew by the list's spacing",
            )
        }
    }

    @Test
    fun carriedDownItsOwnListTheCardsItPassesTakeItsPlaceExactly() {
        val board = SpacedBoard(todo = listOf("Timetable", "Fares", "Signage", "Lifts", "Bins"), done = listOf("Maps"))
        board.run { scene ->
            val fares = board.bounds.getValue("Fares").top
            val signage = board.bounds.getValue("Signage").top
            val grab = board.bounds.getValue("Fares").center
            // Past Lifts' middle and before Bins': the fourth place.
            val over = Offset(grab.x, board.bounds.getValue("Bins").top + 4f)
            scene.pickUp(grab)
            scene.carry(grab + Offset(0f, 24f), over)
            scene.frames(30)
            val signageNow = board.bounds.getValue("Signage").top
            val liftsNow = board.bounds.getValue("Lifts").top
            assertTrue(abs(signageNow - fares) < 0.5f, "Signage should be where Fares was, at $fares; it is at $signageNow")
            assertTrue(abs(liftsNow - signage) < 0.5f, "Lifts should be where Signage was, at $signage; it is at $liftsNow")
        }
    }

    @Test
    fun carriedOutOfItsListTheCardsUnderItCloseUpExactly() {
        val board = SpacedBoard(todo = listOf("Timetable", "Fares", "Signage", "Lifts"), done = listOf("Maps", "Wi-Fi"))
        board.run { scene ->
            val fares = board.bounds.getValue("Fares").top
            val grab = board.bounds.getValue("Fares").center
            val over = Offset(board.doneList.center.x, board.bounds.getValue("Wi-Fi").bottom + 40f)
            scene.pickUp(grab)
            scene.carry(grab + Offset(0f, 24f), over)
            scene.frames(30)
            val signageNow = board.bounds.getValue("Signage").top
            assertTrue(abs(signageNow - fares) < 0.5f, "Signage should have closed up to $fares, where Fares was; it is at $signageNow")
        }
    }
}

/** Cards a solid colour each, so a frame can say where each one is drawn. */
internal val CardColours: Map<String, Color> = mapOf(
    "Timetable" to Color(0xFFB71C1C),
    "Fares" to Color(0xFF1B5E20),
    "Signage" to Color(0xFF0D47A1),
    "Lifts" to Color(0xFFE65100),
    "Bins" to Color(0xFF4A148C),
    "Maps" to Color(0xFF006064),
    "Wi-Fi" to Color(0xFF3E2723),
)

/**
 * Two lists of plain cards with the Board app's space between them, in an
 * overlay host, on a white page: the least a drag between lists needs, and
 * nothing that draws on its own between the cards.
 */
internal class SpacedBoard(
    todo: List<String>,
    done: List<String>,
    val gutter: Boolean = false,
    val reduceMotion: Boolean = false,
    /** Holds each drop in [pending] rather than moving the card, as a caller that moves it later would. */
    val deferMoves: Boolean = false,
    /** Space either side of the cards, inside each list. */
    val inset: Dp = 0.dp,
    /** Cards this wide, centred in their lists, rather than as wide as the list. */
    val cardWidth: Dp? = null,
) {
    class Lane(initial: List<String>, val label: String) {
        val items: SnapshotStateList<String> = mutableStateListOf<String>().also { it.addAll(initial) }
        var state: DropListState<String>? = null
    }

    val todo = Lane(todo, "To do")
    val done = Lane(done, "Done")
    val bounds = mutableMapOf<String, Rect>()
    var todoList = Rect.Zero
    var doneList = Rect.Zero
    var gutterBounds = Rect.Zero
    /** A drop held back by [deferMoves], and the lane it was made into. */
    var pending: Pair<DropEvent<String>, Lane>? = null

    /** Moves [event]'s card into [into], as `onDrop` would have. */
    fun move(event: DropEvent<String>, into: Lane) {
        val from = listOf(todo, done).first { it.state === event.fromList }
        from.items.removeAt(event.fromIndex!!)
        into.items.add(event.index, event.payload)
    }

    fun run(drive: (Scene) -> Unit) {
        Scene(width = 640, height = 900, reduceMotion = reduceMotion) {
            OverlayHost {
                Row(Modifier.fillMaxSize().background(Color.White)) {
                    Lane(todo, Modifier.weight(1f).reportBounds { todoList = it })
                    if (gutter) Box(Modifier.weight(0.5f).fillMaxHeight().reportBounds { gutterBounds = it })
                    Lane(done, Modifier.weight(1f).reportBounds { doneList = it })
                }
            }
        }.use { scene ->
            scene.frames(6)
            drive(scene)
        }
    }

    @androidx.compose.runtime.Composable
    private fun Lane(lane: Lane, modifier: Modifier) {
        val scroll = rememberLazyListState()
        val state = rememberDropListState(
            listState = scroll,
            type = Card,
            items = lane.items,
            key = { it },
            label = lane.label,
            itemLabel = { it },
            onDrop = { event -> if (deferMoves) pending = event to lane else move(event, lane) },
        )
        lane.state = state
        LazyColumn(
            state = scroll,
            modifier = modifier.fillMaxHeight().dropList(state),
            contentPadding = PaddingValues(horizontal = inset),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            dragItems(state) { _, item ->
                Box(
                    Modifier
                        .then(if (cardWidth != null) Modifier.width(cardWidth) else Modifier.fillMaxWidth())
                        .height(60.dp)
                        .background(CardColours.getValue(item))
                        .reportBounds { bounds[item] = it },
                )
            }
        }
    }

    companion object {
        val Card = DragType<String>("card")
    }
}

/**
 * A mouse's pick-up at [at]: moved there first, as a mouse is, then pressed and
 * nudged past the slop — a mouse picks up on its first real movement, not on the
 * press — and held there, a little below where it went down.
 */
internal fun Scene.pickUp(at: Offset) {
    move(at - Offset(4f, 4f), PointerType.Mouse)
    frames(2)
    press(at, PointerType.Mouse)
    frame()
    for (step in 1..4) {
        move(at + Offset(0f, step * 6f), PointerType.Mouse)
        frame()
    }
}

/** A mouse carried from [from] to [to] in steps, a frame each. */
internal fun Scene.carry(from: Offset, to: Offset, steps: Int = 16) {
    repeat(steps) { i ->
        move(from + (to - from) * ((i + 1f) / steps), PointerType.Mouse)
        frame()
    }
    frames(4)
}
