package io.kontour.ui.catalog

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.unit.dp
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A card let go goes to its place and stays there: drawn whole on every frame,
 * arriving without a step, and leaving nothing behind where it came from.
 *
 * Reported: *"when you drop it and it moves into place, it starts flying towards
 * the target, but then in the last little bit, it just 'snaps' and sometimes
 * blinks in and out of existence. this also happens when it flies back to its
 * original position … and also affects the item below"*.
 *
 * The carried card is drawn by the overlay host until it lands, and then by its
 * list, and the two used to disagree at the handover:
 *
 * - A card dropped into another list was a new item there, and a lazy list
 *   fades a new item in — from nothing, under a carried card that had just
 *   vanished.
 * - The list it left faded the old item out, at full size, over whatever was
 *   under the place it had folded to.
 * - A card going home flew to where its place had been when it was let go, and
 *   its place, opening again, was somewhere else by then.
 *
 * Each case is followed frame by frame, by the card's own colour on the page.
 */
class DragHandoverTest {

    @Test
    fun aCardMovedWithinItsListArrivesWithoutAStep() {
        val board = SpacedBoard(todo = listOf("Timetable", "Fares", "Signage", "Lifts", "Bins"), done = listOf("Maps"))
        board.run { scene ->
            val grab = board.bounds.getValue("Fares").center
            val drop = Offset(grab.x, board.bounds.getValue("Bins").top + 4f)
            scene.pickUp(grab)
            scene.carry(grab + Offset(0f, 24f), drop)
            scene.release(drop, PointerType.Mouse)
            follow(scene, board, "Fares")
        }
    }

    @Test
    fun aCardMovedToAnotherListArrivesWithoutAStepAndLeavesNothingBehind() {
        val board = SpacedBoard(todo = listOf("Timetable", "Fares", "Signage", "Lifts"), done = listOf("Maps", "Wi-Fi"))
        board.run { scene ->
            val grab = board.bounds.getValue("Fares").center
            val drop = Offset(board.doneList.center.x, board.bounds.getValue("Wi-Fi").top + 4f)
            val below = board.bounds.getValue("Lifts").bottom
            scene.pickUp(grab)
            scene.carry(grab + Offset(0f, 24f), drop)
            scene.release(drop, PointerType.Mouse)
            val frames = follow(scene, board, "Fares")
            frames.forEachIndexed { i, frame ->
                // Lifts is To do's last card once Fares has gone: under it, page.
                val left = board.todoList.center.x
                val marked = (below.toInt() + 4..below.toInt() + 130).maxOf { 1f - luminance(frame.getRGB(left.toInt(), it)) }
                assertTrue(marked < 0.05f, "frame $i after the drop: something was drawn under To do's last card ($marked)")
            }
        }
    }

    @Test
    fun aCardLetGoOverNothingGoesHomeWithoutAStep() {
        val board = SpacedBoard(todo = listOf("Timetable", "Fares", "Signage", "Lifts"), done = listOf("Maps"), gutter = true)
        board.run { scene ->
            val grab = board.bounds.getValue("Fares").center
            val nowhere = board.gutterBounds.center
            scene.pickUp(grab)
            scene.carry(grab + Offset(0f, 24f), nowhere)
            scene.release(nowhere, PointerType.Mouse)
            follow(scene, board, "Fares")
        }
    }

    /**
     * A caller that moves the card some frames after `onDrop` — round a view
     * model, say — has it wait where it landed, drawn, until its lists show the
     * move, rather than handing over to lists that have not caught up.
     */
    @Test
    fun aDropTheListsShowLaterWaitsWhereItLanded() {
        val board = SpacedBoard(
            todo = listOf("Timetable", "Fares", "Signage", "Lifts"),
            done = listOf("Maps", "Wi-Fi"),
            deferMoves = true,
        )
        board.run { scene ->
            val grab = board.bounds.getValue("Fares").center
            val drop = Offset(board.doneList.center.x, board.bounds.getValue("Wi-Fi").top + 4f)
            scene.pickUp(grab)
            scene.carry(grab + Offset(0f, 24f), drop)
            scene.release(drop, PointerType.Mouse)
            val area = board.bounds.getValue("Fares").let { it.width * it.height }
            var flown = 0
            while (board.pending == null) {
                check(++flown < 120) { "the card never landed" }
                whole(scene.frame(), "Fares", area, "frame $flown of the flight")
            }
            val landed = assertNotNull(cardBox(scene.frame(), "Fares")).first
            repeat(3) { i ->
                val (box, _) = whole(scene.frame(), "Fares", area, "frame $i of waiting for the lists")
                assertTrue(near(box, landed, 1f), "waiting for the lists, the card moved from ${landed.topLeft} to ${box.topLeft}")
            }
            onComposeUiThread { board.pending!!.let { (event, lane) -> board.move(event, lane) } }
            follow(scene, board, "Fares")
        }
    }

    @Test
    fun underReducedMotionADroppedCardIsDrawnEveryFrame() {
        val board = SpacedBoard(
            todo = listOf("Timetable", "Fares", "Signage", "Lifts"),
            done = listOf("Maps", "Wi-Fi"),
            reduceMotion = true,
        )
        board.run { scene ->
            val grab = board.bounds.getValue("Fares").center
            val drop = Offset(board.doneList.center.x, board.bounds.getValue("Wi-Fi").top + 4f)
            scene.pickUp(grab)
            scene.carry(grab + Offset(0f, 24f), drop)
            scene.release(drop, PointerType.Mouse)
            // No flight: a step into its place is the point, and nothing else.
            follow(scene, board, "Fares", flies = false)
        }
    }

    /**
     * Cards with room either side of them in their list, as a list with
     * `contentPadding` has, land where the list puts them across it as well as
     * along it.
     *
     * Reported as *"when it snaps into place, it sort of settles a little bit
     * over to the left, then snaps back to the regular position"*. Where a
     * landing flew to was read from the list's layout, which says where an item
     * is along the list and nothing about across it, so it took the list's own
     * edge: the carried card settled the list's padding to the left of its
     * place, and stepped across when the card in the list took over.
     */
    @Test
    fun aCardMovedWithinAnInsetListArrivesWithoutAStepSideways() {
        val board = SpacedBoard(todo = listOf("Timetable", "Fares", "Signage", "Lifts", "Bins"), done = listOf("Maps"), inset = 24.dp)
        board.run { scene ->
            val grab = board.bounds.getValue("Fares").center
            val drop = Offset(grab.x, board.bounds.getValue("Bins").top + 4f)
            scene.pickUp(grab)
            scene.carry(grab + Offset(0f, 24f), drop)
            scene.release(drop, PointerType.Mouse)
            follow(scene, board, "Fares")
        }
    }

    @Test
    fun aCardMovedIntoAnInsetListArrivesWithoutAStepSideways() {
        val board = SpacedBoard(todo = listOf("Timetable", "Fares", "Signage", "Lifts"), done = listOf("Maps", "Wi-Fi"), inset = 24.dp)
        board.run { scene ->
            val grab = board.bounds.getValue("Fares").center
            val drop = Offset(board.doneList.center.x, board.bounds.getValue("Wi-Fi").top + 4f)
            scene.pickUp(grab)
            scene.carry(grab + Offset(0f, 24f), drop)
            scene.release(drop, PointerType.Mouse)
            follow(scene, board, "Fares")
        }
    }

    /** And cards narrower than their list, centred in it, land centred. */
    @Test
    fun aNarrowCardMovedIntoACentredListArrivesWithoutAStepSideways() {
        val board = SpacedBoard(todo = listOf("Timetable", "Fares", "Signage", "Lifts"), done = listOf("Maps", "Wi-Fi"), cardWidth = 100.dp)
        board.run { scene ->
            val grab = board.bounds.getValue("Fares").center
            val drop = Offset(board.doneList.center.x, board.bounds.getValue("Wi-Fi").top + 4f)
            scene.pickUp(grab)
            scene.carry(grab + Offset(0f, 24f), drop)
            scene.release(drop, PointerType.Mouse)
            follow(scene, board, "Fares")
        }
    }

    private fun whole(frame: BufferedImage, card: String, area: Float, what: String): Pair<Rect, Float> {
        val found = assertNotNull(cardBox(frame, card), "$what: $card was not drawn at all")
        assertTrue(found.second >= area * 0.85f, "$what: $card was not drawn whole — ${found.second / area * 100}% of it")
        return found
    }

    /**
     * Renders the frames after a release and checks [card] in each: drawn, and
     * whole; at its place at the end; and once there, there for good, having
     * got there — if it [flies] — without a step.
     */
    private fun follow(scene: Scene, board: SpacedBoard, card: String, flies: Boolean = true): List<BufferedImage> {
        val frames = List(60) { scene.frame() }
        val size = board.bounds.getValue(card).size
        val area = size.width * size.height
        val boxes = frames.mapIndexed { i, frame -> whole(frame, card, area, "frame $i after the release").first }
        val rest = boxes.last()
        val placed = assertNotNull(board.bounds[card])
        assertTrue(
            abs(rest.top - placed.top) <= 1.5f && abs(rest.left - placed.left) <= 1.5f,
            "$card should end where its list put it, at ${placed.topLeft}; it was drawn at ${rest.topLeft}",
        )
        val arrived = boxes.indexOfFirst { near(it, rest, 1f) }
        for (i in arrived until boxes.size) {
            assertTrue(near(boxes[i], rest, 1f), "frame $i: $card reached its place on frame $arrived and then left it, to ${boxes[i].topLeft}")
        }
        if (flies && arrived > 0) {
            val step = max(abs(boxes[arrived - 1].top - rest.top), abs(boxes[arrived - 1].left - rest.left))
            assertTrue(step <= 8f, "$card stepped ${step}px into its place on frame $arrived")
        }
        return frames
    }

    private fun near(a: Rect, b: Rect, by: Float) = abs(a.top - b.top) <= by && abs(a.left - b.left) <= by
}

/**
 * Where [card] is drawn — the box round every pixel of its colour, sampled
 * every other pixel — and roughly how many pixels that is.
 */
internal fun cardBox(image: BufferedImage, card: String): Pair<Rect, Float>? {
    val colour = CardColours.getValue(card)
    var left = Int.MAX_VALUE
    var top = Int.MAX_VALUE
    var right = -1
    var bottom = -1
    var count = 0
    for (y in 0 until image.height step 2) {
        for (x in 0 until image.width step 2) {
            if (!sameColour(image.getRGB(x, y), colour)) continue
            count++
            if (x < left) left = x
            if (x > right) right = x
            if (y < top) top = y
            if (y > bottom) bottom = y
        }
    }
    if (count < 50) return null
    return Rect(left.toFloat(), top.toFloat(), right + 1f, bottom + 1f) to count * 4f
}

internal fun sameColour(rgb: Int, colour: Color): Boolean {
    val r = (rgb shr 16) and 0xFF
    val g = (rgb shr 8) and 0xFF
    val b = rgb and 0xFF
    return abs(r - (colour.red * 255).toInt()) <= 6 &&
        abs(g - (colour.green * 255).toInt()) <= 6 &&
        abs(b - (colour.blue * 255).toInt()) <= 6
}

internal fun luminance(rgb: Int): Float {
    val r = ((rgb shr 16) and 0xFF) / 255f
    val g = ((rgb shr 8) and 0xFF) / 255f
    val b = (rgb and 0xFF) / 255f
    return 0.2126f * r + 0.7152f * g + 0.0722f * b
}
