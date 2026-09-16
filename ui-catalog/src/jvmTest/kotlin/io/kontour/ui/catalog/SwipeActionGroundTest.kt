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
import io.kontour.ui.components.list.SwipeValue
import io.kontour.ui.components.list.rememberSwipeActionsState
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Where a row's actions sit, and what colour the ground behind it is.
 *
 * With one action a side there is nothing to get wrong, which is why none of
 * this was noticed until a demo drew three.
 *
 * ### The order runs from the screen edge in toward the row
 *
 * So the **first** action of a list is the one furthest from the row, and the
 * last is the panel against its edge. That is the convention swipe rows use
 * everywhere, and the reason is the full swipe: carrying a row all the way runs
 * the *first* action of the side, and what a full swipe looks like is that
 * action growing from the edge until it has the whole row. With the order the
 * other way up, the action a full swipe commits to was the one hard against the
 * row and the one at the edge was the one it would never run.
 *
 * A `Row` packs against the row from opposite directions on the two sides, so
 * one of the two lists has to be reversed to say the same thing — which is
 * exactly the kind of asymmetry a test is for.
 *
 * ### And the ground is the nearest action
 *
 * The strip the row slides off belongs to whatever it is sliding *onto*, which
 * is the action closest to it. Same asymmetry, same reason, and it was wrong on
 * the trailing side for the same reason the order was.
 *
 * ### Why the pixels
 *
 * The strip is a `drawBehind` on a box that matches the row's size. It has no
 * node of its own, no semantics and no size to query: the colour it paints is
 * the entire thing under test, and it is only ever observable as ink. The panels
 * are reachable by semantics, but only as a *set* — the row exposes them as
 * custom actions, in caller order, which says nothing about where they are.
 */
class SwipeActionGroundTest {

    /**
     * Three panels, read off a fully open row, from the row's edge outward.
     *
     * Opened through `initialValue` rather than by dragging. A full reveal is
     * 264dp of travel and the commit sits only a little past it, so a gesture
     * long enough to show all three is a gesture one settle away from deleting
     * the row — and the arithmetic that separates them is not what is under test
     * here.
     *
     * Sampled a few pixels inside each panel's leading edge, which is clear of
     * the icon and label centred in it.
     */
    @Test
    fun theDeclaredOrderRunsFromTheScreenEdgeInTowardTheRow() {
        assertEquals(
            listOf(Pinned, Archived, Remove),
            panelColours(towardStart = false),
            "reading a fully swiped row's trailing panels from its edge outward. " +
                "The list is Remove, Archive, Pin, so Pin belongs against the row " +
                "and Remove against the screen edge: the first action is the one a " +
                "full swipe runs, and a full swipe is that action arriving from the " +
                "edge and taking the row.",
        )
        assertEquals(
            listOf(Pinned, Archived, Remove),
            panelColours(towardStart = true),
            "and the same reading on the leading side, which packs the other way " +
                "and therefore lays the list out the other way up. Both sides run " +
                "edge-inward; a side that does not is the asymmetry this exists for.",
        )
    }

    @Test
    fun aTrailingSwipeRevealsTheNearestActionsColour() {
        assertEquals(
            Pinned,
            groundColour(towardStart = false),
            "a row swiped onto its trailing actions showed the ground of the " +
                "action furthest from it. The strip is what the row is sliding " +
                "onto, so it belongs to the action it is about to reach — which " +
                "on this side is the *last* one in the list.",
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
     * Each panel's colour on a fully open row, ordered from the row outward.
     */
    private fun panelColours(towardStart: Boolean): List<Color> {
        val sampled = mutableListOf<Color>()

        Scene(width = Width, height = Height, reduceMotion = true) {
            Box(Modifier.fillMaxSize().background(Color.White).padding(Margin.dp)) {
                SwipeActions(
                    start = if (towardStart) actions() else emptyList(),
                    end = if (towardStart) emptyList() else actions(),
                    state = rememberSwipeActionsState(
                        initialValue = if (towardStart) SwipeValue.Start else SwipeValue.End,
                    ),
                    modifier = Modifier.fillMaxWidth().height(RowHeight.dp),
                ) {
                    ListItem { +"Perth Underground" }
                }
            }
        }.use { scene ->
            val frame = scene.frames(SettleFrames)
            val midY = ((Margin + RowHeight / 2) * Density).toInt()
            val panel = (ActionWidth * Density).toFloat()
            // The row's own edge on a fully open row: it has travelled the
            // whole set's width, toward the trailing side or the leading one.
            val rowEdge = if (towardStart) {
                Margin * Density + panel * Actions
            } else {
                (Width - Margin * Density) - panel * Actions
            }
            repeat(Actions) { index ->
                // A tenth into each panel from the side facing the row, so the
                // reading walks outward whichever way the set runs — and lands
                // nowhere near the icon and label centred in it.
                val step = (index + Inset) * panel
                val x = if (towardStart) rowEdge - step else rowEdge + step
                sampled += Color(frame.getRGB(x.toInt(), midY)).copy(alpha = 1f)
            }
        }
        return sampled
    }

    /**
     * The colour painted immediately behind the row's trailing edge, part-swiped.
     *
     * Sampled a few pixels into the strip and vertically centred, which is clear
     * of the row's rounded corner and of the action buttons' own ink.
     */
    private fun groundColour(towardStart: Boolean): Color {
        val actions = actions()
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

    private fun actions() = listOf(
        SwipeAction("Remove", Tabler.Outline.Trash, {}, Remove),
        SwipeAction("Archive", Tabler.Outline.Archive, {}, Archived),
        SwipeAction("Pin", Tabler.Outline.Pin, {}, Pinned),
    )

    private companion object {
        const val Width = 720
        const val Height = 240
        const val Density = 2
        const val Margin = 16
        const val RowHeight = 64

        /** Wide enough that the strip is unmistakable, short of the commit. */
        const val Reveal = 180f

        const val Actions = 3

        /** `SwipeActionsDefaults.ActionWidth`, in dp. */
        const val ActionWidth = 88

        /** A row opened by `initialValue` still animates onto its anchor. */
        const val SettleFrames = 40

        /** How far into a panel to read, as a fraction of its width. */
        const val Inset = 0.1f

        // Flat, far apart, and none of them near the page or the row.
        val Remove = Color(0xFFCC2222)
        val Archived = Color(0xFFCC8800)
        val Pinned = Color(0xFF2244CC)
    }
}
