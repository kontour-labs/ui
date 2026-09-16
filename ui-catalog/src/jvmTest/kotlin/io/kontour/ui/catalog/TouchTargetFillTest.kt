package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.a11y.minimumTouchTarget
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Sizing
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A reserved touch target either centres its content or is filled by it.
 *
 * Reported as a dropdown whose rows looked short and whose margins were wrong —
 * "the top and bottom padding is now larger than the left and right". Both
 * halves were one cause, and it is not the padding.
 *
 * `minimumTouchTarget` reserves the platform's minimum and **centres the visual
 * child in it** without stretching. For a control with a drawn shape that is
 * exactly right: a 20dp checkbox stays a 20dp checkbox and only its hit area
 * grows. For a **row** it is wrong, because a row has no drawn shape of its own
 * — its shape *is* the box, and the highlight, the clip and the press ripple all
 * belong to it. A menu row is one line of content inside 8dp of padding, about
 * 36dp, floating in a 48dp slot: 6dp of dead space above and below the thing
 * that lights up, against the panel's 4dp at the sides.
 *
 * ### Why this was invisible until somebody held a phone
 *
 * `platformMinTouchTarget` is 48dp on Android, 44 on iOS and web and **24dp on
 * the JVM**, and a 36dp row already clears 24. So the modifier expanded nothing
 * on a desktop, there was no slack to be off-centre in, and every golden in the
 * repository rendered the correct picture of the wrong thing. Overriding the
 * sizing is what makes a JVM test answer the question a phone asks — the same
 * device `FieldHeightTest` uses, and for the same reason.
 *
 * ### Why the pixels
 *
 * All of this happens inside one layout node. The node is 48dp either way — that
 * is what reserving means — so its semantics, its bounds and its measured size
 * are identical before and after the fix, and no assertion about them can tell
 * the two apart. What changes is the size the modifiers *inside* it are drawn
 * at, which is only observable as ink.
 */
class TouchTargetFillTest {

    @Test
    fun aRowThatFillsItsTargetDrawsTheWholeTarget() {
        val drawn = inkHeight(fill = true, minTouchTarget = PhoneTarget)

        assertEquals(
            PhoneTarget.value.toInt(),
            drawn,
            "a filled row drew ${drawn}dp of ink inside a ${PhoneTarget.value.toInt()}dp " +
                "target — the row that lights up is not the row the finger gets, " +
                "which is the gap a reader reads as the wrong margin",
        )
    }

    @Test
    fun aControlThatDoesNotFillKeepsItsOwnSize() {
        val drawn = inkHeight(fill = false, minTouchTarget = PhoneTarget)

        // The default, and the one worth protecting: a 20dp control inside 8dp
        // of padding is 36dp of ink whatever target is reserved around it.
        assertEquals(
            ContentHeight,
            drawn,
            "the default grew its content to ${drawn}dp. `minimumTouchTarget` must go " +
                "on reserving space without changing how large anything looks, or " +
                "every checkbox in the library becomes 48dp of ink",
        )
    }

    /**
     * On a desktop the two are the same, and that is the point.
     *
     * 24dp is smaller than the content, so nothing is reserved, nothing is
     * centred and `fill` has nothing to fill. A version of this defect that only
     * reproduces where the constant is large is a version nobody looking at this
     * repository's goldens would ever have seen.
     */
    @Test
    fun atADesktopTargetTheSettingMakesNoDifference() {
        val filled = inkHeight(fill = true, minTouchTarget = DesktopTarget)
        val centred = inkHeight(fill = false, minTouchTarget = DesktopTarget)

        assertTrue(
            filled == centred && filled == ContentHeight,
            "at a ${DesktopTarget.value.toInt()}dp target the two modes drew ${filled}dp " +
                "and ${centred}dp — below the content's own height there is nothing " +
                "to reserve and the two cannot differ",
        )
    }

    /**
     * The height of the black block, in dp.
     *
     * Black on white with nothing else on the page, so the block's bounding box
     * is exact and needs no tolerance.
     */
    private fun inkHeight(fill: Boolean, minTouchTarget: Dp): Int {
        var image: BufferedImage? = null
        Scene(width = 320 * Density, height = 160) {
            Themed(minTouchTarget) {
                Box(Modifier.fillMaxSize().background(Color.White).padding(8.dp)) {
                    // The chain a row uses, in the order a row uses it: the
                    // target outside, then the thing that draws, then the
                    // padding that sets the content's own height.
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .minimumTouchTarget(fill = fill)
                            .background(Color.Black)
                            .padding(vertical = 8.dp)
                    ) {
                        Box(Modifier.size(20.dp))
                    }
                }
            }
        }.use { scene -> image = scene.frames(2) }

        val frame = requireNotNull(image) { "nothing rendered" }
        var top = Int.MAX_VALUE
        var bottom = -1
        for (y in 0 until frame.height) {
            for (x in 0 until frame.width) {
                if ((frame.getRGB(x, y) and 0xFFFFFF) == 0x000000) {
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                    break
                }
            }
        }
        require(bottom >= 0) { "no ink was drawn at all" }
        return (bottom - top + 1) / Density
    }

    @Composable
    private fun Themed(minTouchTarget: Dp, content: @Composable () -> Unit) {
        KontourTheme(sizing = Sizing(minTouchTarget = minTouchTarget), content = content)
    }

    private companion object {
        /** [Scene]'s default. */
        const val Density = 2

        /** Android's minimum. iOS's 44dp reproduces the same fault one dp smaller. */
        val PhoneTarget = 48.dp

        /** The JVM's, which is what made this invisible. */
        val DesktopTarget = 24.dp

        /** A 20dp control inside 8dp of padding, top and bottom. */
        const val ContentHeight = 36
    }
}
