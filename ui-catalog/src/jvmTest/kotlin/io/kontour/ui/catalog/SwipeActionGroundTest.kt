package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Archive
import com.composables.icons.tabler.outline.Pin
import com.composables.icons.tabler.outline.Trash
import io.kontour.ui.components.list.ListItem
import io.kontour.ui.components.list.SwipeAction
import io.kontour.ui.components.list.SwipeActions
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The ground behind a swiped row is the action it is sliding onto.
 *
 * With one action a side there is nothing to get wrong, which is why this was
 * not noticed until a demo drew three. The strip under the row took
 * `revealed.last()` for both sides — and the actions are laid out in caller
 * order, packed to the start on a rightward swipe and to the end on a leftward
 * one, so the row sits *after* them in the first case and *before* them in the
 * second. `last()` is the nearest action on one side and the furthest on the
 * other.
 *
 * Trailing actions are the common arrangement, so what a reader saw was a row
 * sliding off Remove onto a band of Pin — the colour of an action two along from
 * the one it is about to reach.
 *
 * ### Why the pixels
 *
 * The strip is a `drawBehind` on a box that matches the row's size. It has no
 * node of its own, no semantics and no size to query: the colour it paints is
 * the entire thing under test, and it is only ever observable as ink.
 */
class SwipeActionGroundTest {

    @Test
    fun aTrailingSwipeRevealsTheNearestActionsColour() {
        assertEquals(
            Remove,
            groundColour(towardStart = false),
            "a row swiped onto its trailing actions showed the ground of the " +
                "action furthest from it. The strip is what the row is sliding " +
                "onto, so it belongs to the action it is about to reach.",
        )
    }

    /**
     * And the other side still works, which is the half that was already right.
     *
     * Worth keeping: the fix is a branch on the direction, and a branch that got
     * the second case wrong would look exactly like the first one being fixed.
     */
    @Test
    fun aLeadingSwipeAlsoRevealsTheNearestActionsColour() {
        assertEquals(
            Pinned,
            groundColour(towardStart = true),
            "a row swiped onto its leading actions showed the wrong ground — on " +
                "this side the nearest action is the last one in the list",
        )
    }

    /**
     * The colour painted immediately behind the row's trailing edge, part-swiped.
     *
     * Sampled a few pixels into the strip and vertically centred, which is clear
     * of the row's rounded corner and of the action buttons' own ink.
     */
    private fun groundColour(towardStart: Boolean): Color {
        val actions = listOf(
            SwipeAction("Remove", Tabler.Outline.Trash, {}, Remove),
            SwipeAction("Archive", Tabler.Outline.Archive, {}, Archived),
            SwipeAction("Pin", Tabler.Outline.Pin, {}, Pinned),
        )
        var sampled = Color.Unspecified

        Scene(width = Width, height = Height, reduceMotion = true) {
            Box(Modifier.fillMaxSize().background(Color.White).padding(Margin.dp)) {
                SwipeActions(
                    start = if (towardStart) actions else emptyList(),
                    end = if (towardStart) emptyList() else actions,
                    modifier = Modifier.fillMaxWidth().height(RowHeight.dp),
                ) {
                    ListItem { +"Perth Underground" }
                }
            }
        }.use { scene ->
            scene.frames(3)
            val midY = ((Margin + RowHeight / 2) * Density).toFloat()
            val from = Offset(Width / 2f, midY)
            // Far enough to open a wide strip and not so far that it commits.
            val travel = if (towardStart) Reveal else -Reveal
            scene.press(from)
            scene.frame()
            repeat(8) {
                scene.move(Offset(from.x + travel * (it + 1) / 8f, midY))
                scene.frame()
            }
            val frame = scene.frame()

            // Just inside the strip, from whichever edge it grew from.
            val x = if (towardStart) Margin * Density + 4 else Width - Margin * Density - 4
            sampled = Color(frame.getRGB(x, midY.toInt()))
        }
        return sampled.copy(alpha = 1f)
    }

    private companion object {
        const val Width = 720
        const val Height = 240
        const val Density = 2
        const val Margin = 16
        const val RowHeight = 64

        /** Wide enough that the strip is unmistakable, short of the commit. */
        const val Reveal = 180f

        // Flat, far apart, and none of them near the page or the row.
        val Remove = Color(0xFFCC2222)
        val Archived = Color(0xFFCC8800)
        val Pinned = Color(0xFF2244CC)
    }
}
