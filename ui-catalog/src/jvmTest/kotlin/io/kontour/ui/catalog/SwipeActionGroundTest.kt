package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
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
 * ### And the whole picture mirrors
 *
 * Every case here runs in both layout directions, because the offset the
 * component works in is a *logical* one and almost everything that reads it is
 * mirrored by the framework already. The anchors were flipped a second time by
 * hand, so in RTL a swipe toward the trailing edge settled on the anchor named
 * for the other side: the drawing looked for actions that were not there and a
 * swiped row vacated a strip of bare page.
 *
 * Read outward from the row, the colours are the same list whichever way the
 * screen runs. That is the claim — a direction bug shows up as one of the four
 * readings coming back reversed, empty, or short.
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

    /**
     * And the same two readings with the screen running the other way.
     *
     * The picture mirrors and the *reading* does not: outward from the row is
     * outward from the row, so a direction that is right gives back the same
     * list. What a wrong one gives back is nothing — the anchors and the drawing
     * disagreed about which side had been opened, so the component drew no
     * panels and no strip at all and this comes back empty.
     */
    @Test
    fun theSameOrderRunsEdgeInwardWithTheScreenReversed() {
        assertEquals(
            listOf(Pinned, Archived, Remove),
            panelColours(towardStart = false, rtl = true),
            "the trailing panels of a fully swiped row in RTL, read outward from " +
                "its edge. Everything mirrors, so this is the same list as in LTR.",
        )
        assertEquals(
            listOf(Pinned, Archived, Remove),
            panelColours(towardStart = true, rtl = true),
            "and the leading side in RTL, which is the case that was blank: the " +
                "offset is logical and was being flipped by hand on top of the two " +
                "flips the framework had already applied.",
        )
    }

    /**
     * A swipe toward the trailing edge opens `start`, whichever way that is.
     *
     * The other half of the same bug, through the gesture rather than through
     * `initialValue`. Both reach the anchors, but only this one also says the
     * *finger* maps the way the documentation claims — "start: actions revealed
     * by swiping toward the trailing edge, following the layout direction" —
     * and the trailing edge is the right of the screen in one direction and the
     * left in the other.
     *
     * `end` is loaded too, with a different colour, so a run that opened the
     * wrong side comes back naming it rather than coming back empty.
     */
    @Test
    fun aSwipeTowardTheTrailingEdgeOpensTheStartActions() {
        assertEquals(
            Pinned,
            swipedTowardTrailing(rtl = false),
            "swiping a row toward the trailing edge in LTR — rightward — has to " +
                "open its `start` actions",
        )
        assertEquals(
            Pinned,
            swipedTowardTrailing(rtl = true),
            "and leftward in RTL, which is the same gesture on a screen that runs " +
                "the other way. A `Remove` here is the two sides swapped.",
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
    private fun panelColours(towardStart: Boolean, rtl: Boolean = false): List<Color> {
        val sampled = mutableListOf<Color>()

        Scene(width = Width, height = Height, reduceMotion = true) {
            Direction(rtl) {
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
            }
        }.use { scene ->
            val frame = scene.frames(SettleFrames)
            val midY = ((Margin + RowHeight / 2) * Density).toInt()
            val panel = (ActionWidth * Density).toFloat()
            // Which way the panels run out from the row, in screen terms.
            //
            // A fully open row has travelled the whole set's width toward the
            // trailing edge for its `start` actions and toward the leading edge
            // for its `end` ones — and which of those is the left of the screen
            // is the layout direction's business. The two questions compose to
            // one exclusive-or.
            val outwardIsLeft = towardStart != rtl
            val rowEdge = if (outwardIsLeft) {
                Margin * Density + panel * Actions
            } else {
                (Width - Margin * Density) - panel * Actions
            }
            repeat(Actions) { index ->
                // A tenth into each panel from the side facing the row, so the
                // reading walks outward whichever way the set runs — and lands
                // nowhere near the icon and label centred in it.
                val step = (index + Inset) * panel
                val x = if (outwardIsLeft) rowEdge - step else rowEdge + step
                sampled += Color(frame.getRGB(x.toInt(), midY)).copy(alpha = 1f)
            }
        }
        return sampled
    }

    /**
     * The colour of the panel against the row after a swipe toward the trailing
     * edge — rightward in LTR, leftward in RTL.
     *
     * Read at the row's own edge rather than at the screen's, because with three
     * actions on one side and one on the other the two sides open to different
     * widths and only the edge against the row is in the same place either way.
     */
    private fun swipedTowardTrailing(rtl: Boolean): Color {
        var sampled = Color.Unspecified

        Scene(width = Width, height = Height, reduceMotion = true) {
            Direction(rtl) {
                Box(Modifier.fillMaxSize().background(Color.White).padding(Margin.dp)) {
                    SwipeActions(
                        start = actions(),
                        end = listOf(SwipeAction("Remove", Tabler.Outline.Trash, {}, Remove)),
                        modifier = Modifier.fillMaxWidth().height(RowHeight.dp),
                    ) {
                        ListItem { +"Perth Underground" }
                    }
                }
            }
        }.use { scene ->
            scene.frames(3)
            val midY = ((Margin + RowHeight / 2) * Density).toFloat()
            val from = Offset(Width / 2f, midY)
            // Toward the trailing edge: the right of the screen in LTR and the
            // left of it in RTL.
            val travel = if (rtl) -Reveal else Reveal
            scene.press(from)
            repeat(8) {
                scene.move(Offset(from.x + travel * (it + 1) / 8f, midY))
                scene.frame()
            }
            val frame = scene.frames(2)
            // Inside the strip the row has just uncovered, a little back from
            // where its edge now is.
            //
            // The row moves toward the trailing edge, so the strip is against
            // the *leading* one — the left of the screen in LTR and the right of
            // it in RTL — and is `Reveal` wide less whatever the touch slop ate.
            // [Inside] is the margin for that, and is still well within the one
            // panel that covers the whole strip at this width.
            val edge = if (rtl) {
                (Width - Margin * Density) - Reveal
            } else {
                Margin * Density + Reveal
            }
            val x = (if (rtl) edge + Inside else edge - Inside).toInt()
            sampled = Color(frame.getRGB(x, midY.toInt())).copy(alpha = 1f)
            scene.release(Offset(from.x + travel, midY))
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

    /** The scene's content, with the screen running whichever way is asked for. */
    @Composable
    private fun Direction(rtl: Boolean, content: @Composable () -> Unit) {
        CompositionLocalProvider(
            LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
            content = content,
        )
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

        /** Pixels back from the row's edge, well inside the panel behind it. */
        const val Inside = 40f

        // Flat, far apart, and none of them near the page or the row.
        val Remove = Color(0xFFCC2222)
        val Archived = Color(0xFFCC8800)
        val Pinned = Color(0xFF2244CC)
    }
}
