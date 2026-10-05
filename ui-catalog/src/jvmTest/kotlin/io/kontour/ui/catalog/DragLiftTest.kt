package io.kontour.ui.catalog

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerType
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A carried card lifts off the page and sets back down on it: its shadow rises
 * as it is picked up and is gone by the time it reaches its place.
 *
 * Reported: *"picking up an item is fine (except the shadow 'jumps' a bit) …
 * the shadow does weird things … also when you drop it"*. The shadow was there
 * in full on the first frame of the pick-up, and stayed in full all the way to
 * the place it landed, where it vanished with the carried card — while the card
 * underneath, which has no shadow, took over. A shadow that arrives and leaves
 * in a frame is a jump both times.
 *
 * Measured on the page: how dark the white just under the carried card is.
 */
class DragLiftTest {

    @Test
    fun aCardsShadowRisesAsItIsPickedUp() {
        val board = SpacedBoard(todo = listOf("Timetable", "Fares"), done = listOf("Maps"))
        board.run { scene ->
            val grab = board.bounds.getValue("Fares").center
            scene.move(grab - Offset(4f, 4f), PointerType.Mouse)
            scene.frames(2)
            scene.press(grab, PointerType.Mouse)
            scene.frame()
            // Past the slop in one move: picked up on it, drawn from the next frame.
            scene.move(grab + Offset(0f, 30f), PointerType.Mouse)
            val first = scene.frame()
            val settled = scene.frames(30)

            val atFirst = assertNotNull(shadowUnder(first, "Fares", grab.x), "the carried card should be drawn on the first frame")
            val atRest = assertNotNull(shadowUnder(settled, "Fares", grab.x))
            assertTrue(atRest > 0.04f, "a carried card should cast a shadow once lifted: $atRest")
            assertTrue(
                atFirst < atRest * 0.4f,
                "the shadow should rise, not appear: $atFirst on the first frame, $atRest once lifted",
            )
        }
    }

    @Test
    fun aCardHasSetDownByTheTimeItReachesItsPlace() {
        val board = SpacedBoard(todo = listOf("Timetable", "Fares"), done = listOf("Maps"))
        board.run { scene ->
            val grab = board.bounds.getValue("Fares").center
            val x = board.doneList.center.x
            // Below Maps, the last card in Done: into white page.
            val drop = Offset(x, board.bounds.getValue("Maps").bottom + 150f)
            scene.pickUp(grab)
            scene.carry(grab + Offset(0f, 24f), drop)
            val carried = assertNotNull(shadowUnder(scene.frames(20), "Fares", x))
            scene.release(drop, PointerType.Mouse)

            val frames = List(60) { scene.frame() }
            val bottoms = frames.map { cardRun(it, "Fares", x)?.last }
            val rest = assertNotNull(bottoms.last(), "the card should be in Done once it has landed")
            val arrived = bottoms.indexOfFirst { it != null && abs(it - rest) <= 1 }
            val there = assertNotNull(shadowUnder(frames[arrived], "Fares", x))
            assertTrue(
                there < carried * 0.25f,
                "on the frame it reached its place the card still cast ${there / carried * 100}% of its carried shadow",
            )
        }
    }
}

/** The longest run of [card]'s colour down column [x], or null if it is not drawn there. */
internal fun cardRun(image: BufferedImage, card: String, x: Float): IntRange? {
    val colour = CardColours.getValue(card)
    var best: IntRange? = null
    var start = -1
    val column = x.toInt()
    for (y in 0..image.height) {
        val inside = y < image.height && sameColour(image.getRGB(column, y), colour)
        if (inside && start < 0) start = y
        if (!inside && start >= 0) {
            val run = start until y
            if (best == null || run.count() > best.count()) best = run
            start = -1
        }
    }
    return best?.takeIf { it.count() > 20 }
}

/**
 * How dark the page is just under [card] in column [x]: 0 for white, more the
 * deeper the shadow. The darkest of the few pixels below its bottom edge.
 */
internal fun shadowUnder(image: BufferedImage, card: String, x: Float): Float? {
    val run = cardRun(image, card, x) ?: return null
    return (run.last + 3..run.last + 12)
        .filter { it < image.height }
        .maxOfOrNull { 1f - luminance(image.getRGB(x.toInt(), it)) }
}
