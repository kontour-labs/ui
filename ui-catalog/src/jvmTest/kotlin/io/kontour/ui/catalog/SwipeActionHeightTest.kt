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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.icons.tabler.Tabler
import io.kontour.icons.tabler.outline.Trash
import io.kontour.ui.components.list.ListItem
import io.kontour.ui.components.list.SwipeAction
import io.kontour.ui.components.list.SwipeActions
import io.kontour.ui.components.list.SwipeActionsDefaults
import io.kontour.ui.components.list.SwipeValue
import io.kontour.ui.components.list.rememberSwipeActionsState
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * On a tall row, an action is as tall as an ordinary row's, not as the tall one.
 *
 * Reported as "if a list row is very tall and has a swipe action, then that swipe
 * action is also ridiculously tall. can we please cap the swipe action at a
 * reasonable height (maybe the height of a regular list item)". A button was
 * never taller than it was wide, and otherwise the row's height — so on a 160dp
 * row its 80dp of width made it an 80dp-tall slab, and a full swipe's takeover,
 * which is as wide as the row has moved, made it the row's whole 160dp.
 *
 * Read off the pixels: the button is the only thing in its colour, so its
 * height is the first to the last pixel of that colour down the middle of it.
 * The icon and label in between are skipped over, not counted against it.
 */
class SwipeActionHeightTest {

    @Test
    fun anOpenActionOnATallRowIsARegularRowsHeight() {
        val (_, height) = openButton()
        assertNear(
            RegularRow * Density, height,
            "an action revealed on a ${TallRow}dp row is ${height / Density}dp tall. It is meant " +
                "to stop at a two-line row's ${RegularRow}dp and sit centred on the tall row",
        )
    }

    /**
     * A full swipe's takeover grows across the strip as the row is carried, and
     * not down it as well: held past the point of no return, the outermost
     * action is wider than any one action is, and still a regular row's height.
     */
    @Test
    fun theTakeoverGrowsAcrossNotDown() {
        var width = 0
        var height = 0
        Scene(width = Width, height = Height, reduceMotion = true) {
            Row(maxActionHeight = SwipeActionsDefaults.MaxActionHeight, initial = SwipeValue.Resting)
        }.use { scene ->
            scene.frames(3)
            val midY = rowMiddle()
            val from = Offset(Width - 80f, midY)
            scene.drag(from, Offset(from.x - Carried, midY), steps = 16, release = false)
            val frame = scene.frames(SettleFrames)
            val across = frame.runOf(Remove, row = midY.toInt())
            width = across.last - across.first + 1
            height = frame.extentOf(Remove, column = (across.first + across.last) / 2)
            scene.release(Offset(from.x - Carried, midY))
        }
        assertTrue(
            width > ActionWidth * Density,
            "held past the point of no return, the action should have taken the strip, and it " +
                "is ${width / Density}dp wide",
        )
        assertNear(
            RegularRow * Density, height,
            "the takeover is ${width / Density}dp wide and ${height / Density}dp tall on a " +
                "${TallRow}dp row. It grows across the strip, and stays a regular row's height",
        )
    }

    /** The cap leaves room for the label: what the button holds is the same as on a 64dp row. */
    @Test
    fun theLabelStillUnfoldsUnderTheIcon() {
        val (frame, _) = openButton()
        val column = buttonMiddle()
        val ink = frame.inkExtentWithin(column, Remove)
        // The icon alone is 24dp; with the label under it, the two run to well
        // past that.
        assertTrue(
            ink > IconLarge * Density + LabelAtLeast * Density,
            "what is drawn in the button runs ${ink / Density}dp top to bottom: the icon and " +
                "no label. A 64dp button has room for both",
        )
    }

    /** `Dp.Infinity` is the old behaviour, for a design that wants it. */
    @Test
    fun anInfiniteCapTakesTheRowsWholeHeight() {
        val (_, height) = openButton(maxActionHeight = Dp.Infinity, actionWidth = WideAction.dp)
        assertNear(
            TallRow * Density, height,
            "with no cap a wide action is as tall as the row, and this one is ${height / Density}dp",
        )
    }

    /** A fully open row of one wide-enough action, and its button's height in pixels. */
    private fun openButton(
        maxActionHeight: Dp? = null,
        actionWidth: Dp = ActionWidth.dp,
    ): Pair<BufferedImage, Int> {
        var frame: BufferedImage? = null
        var height = 0
        Scene(width = Width, height = Height, reduceMotion = true) {
            Row(
                maxActionHeight = maxActionHeight ?: SwipeActionsDefaults.MaxActionHeight,
                initial = SwipeValue.End,
                actionWidth = actionWidth,
            )
        }.use { scene ->
            val image = scene.frames(SettleFrames)
            frame = image
            val across = image.runOf(Remove, row = rowMiddle().toInt())
            height = image.extentOf(Remove, column = (across.first + across.last) / 2)
        }
        return requireNotNull(frame) to height
    }

    @androidx.compose.runtime.Composable
    private fun Row(maxActionHeight: Dp, initial: SwipeValue, actionWidth: Dp = ActionWidth.dp) {
        Box(Modifier.fillMaxSize().background(Color.White).padding(Margin.dp)) {
            SwipeActions(
                end = listOf(SwipeAction("Remove", Tabler.Outline.Trash, {}, Remove, fullSwipe = true)),
                state = rememberSwipeActionsState(initialValue = initial),
                actionWidth = actionWidth,
                maxActionHeight = maxActionHeight,
                modifier = Modifier.fillMaxWidth().height(TallRow.dp),
            ) {
                ListItem { +"Perth Underground" }
            }
        }
    }

    private fun rowMiddle() = (Margin + TallRow / 2f) * Density

    /** The middle of a fully open action's button: half a target in from the row's right edge. */
    private fun buttonMiddle() = (Width - Margin * Density - ActionWidth * Density / 2f).toInt()

    /** The widest run of [colour] along [row]. */
    private fun BufferedImage.runOf(colour: Color, row: Int): IntRange {
        val xs = (0 until width).filter { Color(getRGB(it, row)).copy(alpha = 1f) == colour }
        check(xs.isNotEmpty()) { "no $colour along row $row" }
        return xs.first()..xs.last()
    }

    /** From the first to the last pixel of [colour] down [column]. */
    private fun BufferedImage.extentOf(colour: Color, column: Int): Int {
        val ys = (0 until height).filter { Color(getRGB(column, it)).copy(alpha = 1f) == colour }
        check(ys.isNotEmpty()) { "no $colour down column $column" }
        return ys.last() - ys.first() + 1
    }

    /**
     * How far, top to bottom, something other than [ground] is drawn inside the
     * stretch of [ground] down [column]: the icon and label the button holds.
     */
    private fun BufferedImage.inkExtentWithin(column: Int, ground: Color): Int {
        val ys = (0 until height).filter { Color(getRGB(column, it)).copy(alpha = 1f) == ground }
        val inside = (ys.first()..ys.last()).filter { y ->
            (column - 24..column + 24).any { x -> Color(getRGB(x, y)).copy(alpha = 1f) != ground }
        }
        return if (inside.isEmpty()) 0 else inside.last() - inside.first() + 1
    }

    private fun assertNear(expected: Float, actual: Int, message: String) =
        assertTrue(abs(expected - actual) <= 2f, "$message (expected ${expected}px, measured ${actual}px)")

    private companion object {
        const val Width = 720
        const val Height = 480
        const val Density = 2f
        const val Margin = 16

        /** A row with a picture or a paragraph in it. */
        const val TallRow = 160

        /** `ComponentDefaults.listItemTwoLineMinHeight`. */
        const val RegularRow = 64

        /** `SwipeActionsDefaults.ActionWidth`, in dp. */
        const val ActionWidth = 88

        /** Wide enough that an uncapped button is as tall as the row: 176 less the 8dp gap. */
        const val WideAction = 176

        const val IconLarge = 24

        /** What the label adds under the icon, at the least: its gap and most of its line. */
        const val LabelAtLeast = 10

        /** Past half the row, where a held swipe has passed the point of no return. */
        const val Carried = 480f

        const val SettleFrames = 40

        val Remove = Color(0xFFCC2222)
    }
}
