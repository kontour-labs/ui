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
 * Where a row's action buttons sit, and what shows between them.
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
 * ### Separate buttons, with the page between them
 *
 * *"(up to) three squircle shapes expand out of the side when you start swiping"*.
 * The strip the row vacates used to be painted in the nearest action's colour with
 * the buttons laid over it; now each action is its own squircle in the row's shape,
 * and the page shows in the gaps, round the corners and above and below a button
 * that has not yet grown to the row's height.
 *
 * ### Why the pixels
 *
 * The buttons are internal and laid out every frame from the live offset. They are
 * reachable by semantics only as a *set* — the row exposes them as custom actions,
 * in caller order, which says nothing about where they are or what shape they have.
 */
class SwipeActionButtonsTest {

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

    /**
     * On a fully open row the page shows between the buttons, between the last one
     * and the row, and round each button's corners.
     *
     * Read at the slot boundaries — where one action's share of the strip meets the
     * next — which is the middle of each gap, and a few pixels in from a button's
     * top corner, which a squircle leaves bare.
     */
    @Test
    fun thePageShowsBetweenTheButtonsAndRoundTheirCorners() {
        val readings = openRow { frame, midY, slotEdge, rowTop ->
            buildList {
                // Between the outer and middle, and the middle and inner, buttons.
                add("between the outer two" to frame.colourAt(slotEdge(1), midY))
                add("between the inner two" to frame.colourAt(slotEdge(2), midY))
                // Between the inner button and the row it came out from behind.
                add("between the inner button and the row" to frame.colourAt(slotEdge(3) + 4, midY))
                // Just inside the outer button's top corner, on the row's top line.
                add("in the outer button's corner" to frame.colourAt(slotEdge(0) - HalfGap - 3, rowTop + 2))
            }
        }
        readings.forEach { (where, colour) ->
            assertEquals(
                Color.White, colour,
                "$where the page should show, and there was action ink — the " +
                    "buttons are painted as one strip rather than as separate " +
                    "squircles",
            )
        }
    }

    /**
     * Early in a swipe a button is a small squircle in the middle of the row's
     * height, not a full-height stripe.
     *
     * Each button is no taller than it is wide, so 60px into a swipe on a 128px row
     * the one button is 44px round and the page shows above and below it.
     */
    @Test
    fun earlyInASwipeAButtonIsASquircleNotAStripe() {
        var above = Color.Unspecified
        var middle = Color.Unspecified

        Scene(width = Width, height = Height, reduceMotion = true) {
            Box(Modifier.fillMaxSize().background(Color.White).padding(Margin.dp)) {
                SwipeActions(
                    end = listOf(SwipeAction("Remove", Tabler.Outline.Trash, {}, Remove)),
                    modifier = Modifier.fillMaxWidth().height(RowHeight.dp),
                ) {
                    ListItem { +"Perth Underground" }
                }
            }
        }.use { scene ->
            scene.frames(3)
            val midY = ((Margin + RowHeight / 2) * Density).toFloat()
            val from = Offset(Width / 2f, midY)
            scene.drag(from, Offset(from.x - EarlyTravel, midY), steps = 8, release = false)
            val frame = scene.frames(2)
            // The middle of the strip the row has uncovered.
            val x = Width - Margin * Density - EarlyTravel / 2f
            middle = frame.colourAt(x, midY)
            above = frame.colourAt(x, (Margin * Density + 6).toFloat())
            scene.release(Offset(from.x - EarlyTravel, midY))
        }
        assertEquals(Remove, middle, "no action at all in the middle of the uncovered strip")
        assertEquals(
            Color.White, above,
            "60px into a swipe the action is already the row's full height. It is " +
                "supposed to grow out of the side as a squircle and lengthen into a " +
                "button as the row uncovers it.",
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
     * A fully open row of three trailing actions, handed to [read] with the row's
     * middle line, the row's top edge, and the x of the boundary between slot `i`
     * and slot `i + 1` counted from the screen edge (0 is the edge itself, 3 the
     * row's).
     */
    private fun <T> openRow(
        read: (frame: java.awt.image.BufferedImage, midY: Float, slotEdge: (Int) -> Float, rowTop: Float) -> T,
    ): T {
        var result: T? = null
        Scene(width = Width, height = Height, reduceMotion = true) {
            Box(Modifier.fillMaxSize().background(Color.White).padding(Margin.dp)) {
                SwipeActions(
                    end = actions(),
                    state = rememberSwipeActionsState(initialValue = SwipeValue.End),
                    modifier = Modifier.fillMaxWidth().height(RowHeight.dp),
                ) {
                    ListItem { +"Perth Underground" }
                }
            }
        }.use { scene ->
            val frame = scene.frames(SettleFrames)
            val right = Width - Margin * Density
            val panel = (ActionWidth * Density).toFloat()
            result = read(
                frame,
                ((Margin + RowHeight / 2) * Density).toFloat(),
                { i -> right - panel * i },
                (Margin * Density).toFloat(),
            )
        }
        return requireNotNull(result)
    }

    private fun java.awt.image.BufferedImage.colourAt(x: Float, y: Float): Color =
        Color(getRGB(x.toInt(), y.toInt())).copy(alpha = 1f)

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

        /** Half of `SwipeActionsDefaults.Gap`, 8dp, at this scene's density. */
        const val HalfGap = 8f

        /** Early in a swipe: the one button is `60 - 16` = 44px round. */
        const val EarlyTravel = 60f

        // Flat, far apart, and none of them near the page or the row.
        val Remove = Color(0xFFCC2222)
        val Archived = Color(0xFFCC8800)
        val Pinned = Color(0xFF2244CC)
    }
}
