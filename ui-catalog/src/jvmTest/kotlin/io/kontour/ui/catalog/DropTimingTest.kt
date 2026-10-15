package io.kontour.ui.catalog

import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerType
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A card let go over a list is dropped as it settles into its place, not after.
 *
 * Reported from the drag and drop demo: *"it feels like the final 'drop' happens
 * just a fraction of a second too late. it should happen as soon as the item has
 * settled into place"*. The drop waited for the flight's spring to finish, and a
 * spring with no threshold of its own runs on to a hundredth of a pixel: on a
 * short flight that is a tenth of a second after the eye has seen it arrive. And
 * it waited for the shadow too, which comes down on its own spring.
 */
class DropTimingTest {

    @Test
    fun aCardIsDroppedAsItSettlesIntoItsPlace() {
        val board = SpacedBoard(todo = listOf("Timetable", "Fares", "Signage"), done = listOf("Maps", "Wi-Fi"))
        board.run { scene ->
            val picked = board.bounds.getValue("Fares")
            val grab = picked.center
            val over = Offset(board.doneList.center.x, board.bounds.getValue("Wi-Fi").bottom + 40f)
            scene.pickUp(grab)
            scene.carry(grab + Offset(0f, 24f), over)
            // Where the carried card is as it is let go: under the pointer, held
            // where it was taken.
            val letGoAt = picked.topLeft + (over - grab)
            scene.release(over, PointerType.Mouse)

            var frames = 0
            while ("Fares" !in board.done.items && frames < 120) {
                scene.frame()
                frames++
            }
            assertTrue("Fares" in board.done.items, "the card was never dropped")
            scene.frames(30)
            val place = board.bounds.getValue("Fares").topLeft

            // When the flight is within half a pixel of its place — where the
            // eye has it settled — on the spring the host flies it with.
            val flight = (place - letGoAt).getDistance()
            val settles = TargetBasedAnimation(
                spring(dampingRatio = 0.9f, stiffness = 1400f, visibilityThreshold = 0.5f),
                Float.VectorConverter,
                initialValue = flight,
                targetValue = 0f,
            ).durationNanos / 1_000_000
            val droppedAfter = frames * 16L
            // A frame to land on, and one for the list to show the move.
            assertTrue(
                droppedAfter <= settles + 2 * 16,
                "a card flying ${flight.toInt()}px to its place settled within half a pixel after ${settles}ms " +
                    "and was dropped after ${droppedAfter}ms",
            )
        }
    }
}
