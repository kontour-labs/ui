package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import io.kontour.icons.tabler.Tabler
import io.kontour.icons.tabler.outline.InfoCircle
import io.kontour.ui.a11y.contrastRatio
import io.kontour.ui.components.action.TextButton
import io.kontour.ui.components.action.TextIconButton
import io.kontour.ui.foundation.ProvideTextStyle
import io.kontour.ui.foundation.Text
import io.kontour.ui.foundation.richText
import io.kontour.ui.theme.ContrastLevel
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Theme
import io.kontour.ui.theme.lightColourScheme
import io.kontour.ui.theme.squareShapes
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A text button, and a link in a line of text, answer a tap where it can be
 * seen, and move nothing to do it.
 *
 * Asked for for 0.5.2 as *"with text buttons, can we add some sort of visual
 * feedback that they've been tapped, similar to regular buttons? make sure it
 * works well with all the inline text button and stuff that we do"*. Both had a
 * tint for a press, and both followed the press state as it changed: a text
 * button through composition, a link through Compose's `pressedStyle`. A tap is a
 * press and a release inside one frame, and inside a scrolling column, where
 * `clickable` holds the press back to see whether it is a scroll, it always is.
 * So the tint answered a mouse and never a thumb. The press and release here are
 * sent with no frame between them, the shortest tap the harness can express and
 * the one a scroller turns every real tap into.
 *
 * Measured as pixels: what changed between a frame at rest and the frames after
 * the tap, inside the control and outside it. Inside has to change; outside must
 * not, which is the whole of "nothing moves" for a word in a line of text.
 */
class TextButtonPressTest {

    /**
     * Fails on the code before 0.5.2: the tap changed no pixel at all.
     */
    @Test
    fun aTapOnATextButtonInAScrollingColumnIsSeen() {
        var bounds = Rect.Zero
        var taps = 0
        Scene(width = 600, height = 300) {
            Column(Modifier.fillMaxSize().background(Color.White).verticalScroll(rememberScrollState()).padding(24.dp)) {
                Text("Signed out after a week away.")
                Box(Modifier.reportBounds { bounds = it }) {
                    TextButton(onClick = { taps++ }) { +"Sign in again" }
                }
            }
        }.use { scene ->
            val resting = scene.frames(8)
            val at = bounds.center
            scene.press(at)
            scene.release(at)
            val (inside, outside) = watch(scene, resting, bounds, frames = 16)
            val after = scene.frames(40)

            assertEquals(1, taps, "the tap did not reach the button, so this measured nothing")
            assertTrue(
                inside > MinimumSeen,
                "a tap on a text button in a scrolling column changed $inside pixels inside it " +
                    "at most. The press and release arrive together there, and a press shown " +
                    "only while it is down is shown for no frame at all",
            )
            assertEquals(0, outside, "a tap changed $outside pixels outside the text button's own box")
            assertEquals(0, changed(resting, after, everywhere(resting)), "the press did not go away again")
        }
    }

    /**
     * Passes on the old code as well, where the tint was a `background`: kept
     * so that the ground staying drawn, rather than laid out, is checked.
     */
    @Test
    fun aHeldPressKeepsTheButtonsSizeAndDoesNotShrinkTheLabel() {
        var bounds = Rect.Zero
        Scene(width = 600, height = 200) {
            Box(Modifier.fillMaxSize().background(Color.White).padding(24.dp)) {
                Box(Modifier.reportBounds { bounds = it }) {
                    TextButton(onClick = {}) { +"Forgot your password?" }
                }
            }
        }.use { scene ->
            val resting = scene.frames(8)
            val restingBounds = bounds
            scene.press(bounds.center)
            val pressed = scene.frames(20)
            val pressedBounds = bounds

            assertEquals(restingBounds, pressedBounds, "a press changed the text button's bounds")
            assertTrue(changed(resting, pressed, restingBounds) > MinimumSeen, "a held press drew nothing")
            assertEquals(0, changed(resting, pressed, outside(resting, restingBounds)), "a held press drew outside the button")
            // Within a pixel: the pressed ink is darker, so a column of
            // antialiasing at either end can cross the threshold that the
            // resting ink's did not. A 3% shrink of this label is nine.
            val rest = inkWidth(resting)
            val held = inkWidth(pressed)
            assertTrue(
                abs(rest - held) <= 1,
                "a text button's label went from ${rest}px to ${held}px under a press. A word that " +
                    "shrinks reads as the text jumping, which is why a ghost Button with a label does not shrink either",
            )
            scene.release(bounds.center)
        }
    }

    /**
     * A link that wraps is grounded on both of its lines, and the words around
     * it, on both lines, stay exactly as they were.
     *
     * Fails on the code before 0.5.2: a link's pressed look was Compose's
     * `pressedStyle`, and the tap changed no pixel.
     */
    @Test
    fun aTapOnALinkThatWrapsGroundsBothLinesAndMovesNothingElse() {
        val link = LinkedText()
        Scene(width = 600, height = 300) {
            Box(Modifier.fillMaxSize().background(Color.White).verticalScroll(rememberScrollState()).padding(24.dp)) {
                link.Content()
            }
        }.use { scene ->
            val resting = scene.frames(8)
            val (first, second) = link.lineBoxes()
            val at = first.center
            scene.press(at)
            scene.release(at)
            var firstSeen = 0
            var secondSeen = 0
            var elsewhere = 0
            val near = listOf(first.inflated(Margin), second.inflated(Margin))
            repeat(16) {
                val frame = scene.frame()
                firstSeen = maxOf(firstSeen, changed(resting, frame, first))
                secondSeen = maxOf(secondSeen, changed(resting, frame, second))
                elsewhere = maxOf(elsewhere, changed(resting, frame) { x, y -> near.none { it.contains(Offset(x + 0.5f, y + 0.5f)) } })
            }

            assertEquals(1, link.taps, "the tap did not follow the link, so this measured nothing")
            assertTrue(
                firstSeen > MinimumSeen && secondSeen > MinimumSeen,
                "a tap on a link that wraps changed $firstSeen pixels on its first line and " +
                    "$secondSeen on its second. Both halves are the link, and both have to answer",
            )
            assertEquals(0, elsewhere, "a tap on a link changed $elsewhere pixels outside the link's own lines")
        }
    }

    /**
     * A finger that lands on a link to scroll the page does not light it up.
     *
     * The other half of following `clickable`'s reading of a touch, and the
     * reason a link's ground does not simply appear on the way down.
     */
    @Test
    fun aScrollThatStartsOnALinkDoesNotGroundIt() {
        val link = LinkedText()
        Scene(width = 600, height = 300) {
            Column(Modifier.fillMaxSize().background(Color.White).verticalScroll(rememberScrollState()).padding(24.dp)) {
                link.Content()
                Box(Modifier.width(10.dp).padding(top = 600.dp))
            }
        }.use { scene ->
            scene.frames(8)
            val before = link.textBounds()
            val (first, _) = link.lineBoxes()
            val at = first.center
            // One motion, with no frame between the finger landing and it
            // moving away: a flick, which is what scrolls a page.
            scene.press(at)
            scene.move(at - Offset(0f, 40f))
            scene.move(at - Offset(0f, 120f))
            scene.release(at - Offset(0f, 120f))
            var grounds = 0
            repeat(30) { grounds = maxOf(grounds, groundPixels(scene.frame())) }

            assertTrue(link.textBounds().top < before.top, "the page did not scroll, so this measured nothing")
            assertEquals(0, link.taps, "the scroll followed the link")
            // Not nought: an antialiased edge of the accent's own ink can land
            // on the container's colour. A ground behind three words is
            // thousands.
            assertTrue(grounds < MinimumSeen, "a scroll that started on a link drew $grounds pixels of the link's ground")
        }
    }

    /**
     * A glyph-only text button takes the shrink a ghost icon button does, and
     * under reduced motion keeps the ground and drops the shrink.
     *
     * The first half fails on the code before 0.5.2, where a `TextIconButton`
     * did not move at all.
     */
    @Test
    fun aTextIconButtonShrinksUnlessMotionIsReduced() {
        fun widths(reduceMotion: Boolean): Triple<Int, Int, Int> {
            var bounds = Rect.Zero
            var result = Triple(0, 0, 0)
            Scene(width = 300, height = 200, reduceMotion = reduceMotion) {
                Box(Modifier.fillMaxSize().background(Color.White).padding(48.dp)) {
                    // Large, so that a 7% shrink is several pixels and cannot be
                    // hidden by the darker pressed ink catching a column of
                    // antialiasing more at each edge.
                    ProvideTextStyle(Theme.typography.displayLarge) {
                        Box(Modifier.reportBounds { bounds = it }) {
                            TextIconButton(onClick = {}, icon = Tabler.Outline.InfoCircle, contentDescription = "About this stop")
                        }
                    }
                }
            }.use { scene ->
                val resting = scene.frames(8)
                scene.press(bounds.center)
                val pressed = scene.frames(20)
                result = Triple(inkWidth(resting), inkWidth(pressed), changed(resting, pressed, bounds))
                scene.release(bounds.center)
            }
            return result
        }

        val (rest, held, drawn) = widths(reduceMotion = false)
        assertTrue(held < rest - 3, "a TextIconButton did not shrink under a held press: $rest→$held px")
        assertTrue(drawn > MinimumSeen, "a TextIconButton drew no ground under a held press")

        val (calmRest, calmHeld, calmDrawn) = widths(reduceMotion = true)
        assertTrue(abs(calmRest - calmHeld) <= 2, "a TextIconButton shrank under reduced motion: $calmRest→$calmHeld px")
        assertTrue(calmDrawn > MinimumSeen, "under reduced motion a TextIconButton drew no ground: the press has to stay")
    }

    /** A disabled text button answers nothing, held or tapped. */
    @Test
    fun aDisabledTextButtonShowsNoPress() {
        var bounds = Rect.Zero
        Scene(width = 600, height = 200) {
            Box(Modifier.fillMaxSize().background(Color.White).padding(24.dp)) {
                Box(Modifier.reportBounds { bounds = it }) {
                    TextButton(onClick = {}, enabled = false) { +"Sign in again" }
                }
            }
        }.use { scene ->
            val resting = scene.frames(8)
            scene.press(bounds.center)
            scene.release(bounds.center)
            val (tapped, _) = watch(scene, resting, bounds, frames = 12)
            scene.press(bounds.center)
            val held = changed(resting, scene.frames(20), everywhere(resting))
            scene.release(bounds.center)
            assertEquals(0, tapped, "a tap on a disabled text button drew something")
            assertEquals(0, held, "a held press on a disabled text button drew something")
        }
    }

    /**
     * At the enhanced tier a press is the accent's solid behind the label,
     * which stands well off the page; the container it is in the standard tiers
     * is about 1.25:1 there.
     *
     * Fails on the code before 0.5.2, whose pressed ground at that tier was the
     * container.
     */
    @Test
    fun atHighContrastAPressedGroundStandsOffThePage() {
        for (dark in listOf(false, true)) {
            var bounds = Rect.Zero
            Scene(width = 600, height = 200, darkTheme = dark) {
                KontourTheme(darkTheme = dark, contrast = ContrastLevel.High, reduceMotion = true) {
                    Box(Modifier.fillMaxSize().background(Theme.colours.background).padding(24.dp)) {
                        Box(Modifier.reportBounds { bounds = it }) {
                            TextButton(onClick = {}) { +"Sign in again" }
                        }
                    }
                }
            }.use { scene ->
                val resting = scene.frames(8)
                scene.press(bounds.center)
                val pressed = scene.frames(20)
                // The clearance beside the first letter: ground, and no glyph.
                val x = (bounds.left + 1f).toInt()
                val y = bounds.center.y.toInt()
                val ratio = contrastRatio(Color(pressed.getRGB(x, y)), Color(resting.getRGB(x, y)))
                assertTrue(
                    ratio >= 3f,
                    "at high contrast (${if (dark) "dark" else "light"}) a pressed text button's ground is " +
                        "${"%.2f".format(ratio)}:1 against the page; it has to be seen at the tier that asks for 3:1 of an edge",
                )
                scene.release(bounds.center)
            }
        }
    }

    /**
     * A square theme grounds a press in a square, and the default one does not —
     * for a text button and for a link alike.
     *
     * The link's half fails on the code before 0.5.2, whose pressed link was a
     * square box the paragraph drew whatever the theme.
     */
    @Test
    fun aPressedGroundTakesTheThemesShape() {
        fun corners(square: Boolean): Pair<Boolean, Boolean> {
            var bounds = Rect.Zero
            val link = LinkedText()
            var result = false to false
            Scene(width = 600, height = 300, reduceMotion = true) {
                KontourTheme(darkTheme = false, reduceMotion = true, shapes = if (square) squareShapes() else io.kontour.ui.theme.Shapes()) {
                    Column(Modifier.fillMaxSize().background(Color.White).padding(24.dp)) {
                        Box(Modifier.reportBounds { bounds = it }) { TextButton(onClick = {}) { +"Sign in again" } }
                        Box(Modifier.padding(top = 24.dp)) { link.Content() }
                    }
                }
            }.use { scene ->
                val resting = scene.frames(8)
                scene.press(bounds.center, PointerType.Mouse)
                val button = scene.frames(20)
                scene.release(bounds.center, PointerType.Mouse)
                scene.frames(30)
                val (first, _) = link.lineBoxes()
                scene.press(first.center, PointerType.Mouse)
                val linked = scene.frames(20)
                scene.release(first.center, PointerType.Mouse)
                fun grounded(image: BufferedImage, x: Float, y: Float) =
                    differs(image.getRGB(x.toInt(), y.toInt()), resting.getRGB(x.toInt(), y.toInt()))
                // One pixel in from the very corner of each ground. The
                // button's is found from what the press changed, because the
                // box it reports includes the target it reserves round itself.
                val drawn = changedBox(resting, button, bounds)
                result = grounded(button, drawn.left + 1f, drawn.top + 1f) to
                    grounded(linked, first.left - Clearance + 1f, first.top + 1f)
            }
            return result
        }

        val (squareButton, squareLink) = corners(square = true)
        val (roundButton, roundLink) = corners(square = false)
        assertTrue(squareButton && squareLink, "a square theme's pressed ground is not square: button $squareButton, link $squareLink")
        assertTrue(!roundButton && !roundLink, "the default theme's pressed ground has a square corner: button $roundButton, link $roundLink")
    }

    /** A mouse over a link grounds it, now that the library draws that rather than Compose. */
    @Test
    fun aMouseOverALinkGroundsIt() {
        val link = LinkedText()
        Scene(width = 600, height = 300, reduceMotion = true) {
            Box(Modifier.fillMaxSize().background(Color.White).padding(24.dp)) { link.Content() }
        }.use { scene ->
            val resting = scene.frames(8)
            val (first, second) = link.lineBoxes()
            scene.move(Offset(2f, 2f), PointerType.Mouse)
            scene.move(first.center, PointerType.Mouse)
            val hovered = scene.frames(12)
            scene.move(Offset(2f, 2f), PointerType.Mouse)
            val left = scene.frames(12)
            assertTrue(changed(resting, hovered, first) > MinimumSeen, "a mouse over a link did not ground its first line")
            assertTrue(changed(resting, hovered, second) > MinimumSeen, "a mouse over a link did not ground its second line")
            assertEquals(0, changed(resting, left, everywhere(resting)), "the hover stayed after the mouse left")
        }
    }

    /**
     * A paragraph with a link in it that wraps, and where that link is.
     *
     * The width is chosen so that "replacement" ends the first line and "buses"
     * starts the second; [lineBoxes] fails the test if it ever does not.
     */
    private class LinkedText {
        var taps = 0
        private var layout: TextLayoutResult? = null
        private var origin = Rect.Zero

        @Composable
        fun Content() {
            Text(
                richText {
                    +Before
                    link(Words) { taps++ }
                    +" until the line reopens."
                },
                modifier = Modifier.width(260.dp).reportBounds { origin = it },
                onTextLayout = { layout = it },
            )
        }

        fun textBounds(): Rect = origin

        /** The link's box on each of its two lines, in the scene's pixels. */
        fun lineBoxes(): Pair<Rect, Rect> {
            val layout = requireNotNull(layout) { "the text was never laid out" }
            val start = Before.length
            val end = start + Words.length
            val first = layout.getLineForOffset(start)
            val last = layout.getLineForOffset(end - 1)
            check(last == first + 1) { "the link is on lines $first to $last, not two: the test's width needs changing" }
            val split = layout.getLineEnd(first, visibleEnd = true)
            val one = layout.getPathForRange(start, split).getBounds()
            val two = layout.getPathForRange(layout.getLineStart(last), end).getBounds()
            return one.translate(origin.topLeft) to two.translate(origin.topLeft)
        }

        private companion object {
            const val Before = "Services are suspended between Perth and Midland. "
            const val Words = "See replacement buses"
        }
    }

    /** The most pixels that changed inside and outside [box] over [frames] frames. */
    private fun watch(scene: Scene, resting: BufferedImage, box: Rect, frames: Int): Pair<Int, Int> {
        var inside = 0
        var outside = 0
        repeat(frames) {
            val frame = scene.frame()
            inside = maxOf(inside, changed(resting, frame, box))
            outside = maxOf(outside, changed(resting, frame, outside(resting, box)))
        }
        return inside to outside
    }

    /** Pixels in the light scheme's accent container: a link's or a text button's ground. */
    private fun groundPixels(image: BufferedImage): Int {
        val ground = lightColourScheme().accent.container.toArgb()
        var count = 0
        for (y in 0 until image.height) for (x in 0 until image.width) {
            if (!differs(image.getRGB(x, y), ground)) count++
        }
        return count
    }

    private fun everywhere(image: BufferedImage): (Int, Int) -> Boolean = { _, _ -> true }

    /** Outside [box] by more than antialiasing reaches. */
    private fun outside(image: BufferedImage, box: Rect): (Int, Int) -> Boolean {
        val wide = box.inflate(1f)
        return { x, y -> !wide.contains(Offset(x + 0.5f, y + 0.5f)) }
    }

    private fun changed(a: BufferedImage, b: BufferedImage, box: Rect): Int =
        changed(a, b) { x, y -> box.contains(Offset(x + 0.5f, y + 0.5f)) }

    private fun changed(a: BufferedImage, b: BufferedImage, where: (Int, Int) -> Boolean): Int {
        var count = 0
        for (y in 0 until a.height) for (x in 0 until a.width) {
            if (where(x, y) && a.getRGB(x, y) != b.getRGB(x, y)) count++
        }
        return count
    }

    /** The smallest box round every pixel inside [within] that differs between [a] and [b]. */
    private fun changedBox(a: BufferedImage, b: BufferedImage, within: Rect): Rect {
        var left = Int.MAX_VALUE
        var top = Int.MAX_VALUE
        var right = -1
        var bottom = -1
        for (y in 0 until a.height) for (x in 0 until a.width) {
            if (within.contains(Offset(x + 0.5f, y + 0.5f)) && differs(a.getRGB(x, y), b.getRGB(x, y))) {
                left = minOf(left, x)
                top = minOf(top, y)
                right = maxOf(right, x)
                bottom = maxOf(bottom, y)
            }
        }
        check(right >= 0) { "the press changed nothing inside $within" }
        return Rect(left.toFloat(), top.toFloat(), right + 1f, bottom + 1f)
    }

    private fun Rect.inflated(by: Float) = Rect(left - by, top - by, right + by, bottom + by)

    private fun differs(a: Int, b: Int): Boolean =
        abs((a shr 16 and 0xFF) - (b shr 16 and 0xFF)) > 8 ||
            abs((a shr 8 and 0xFF) - (b shr 8 and 0xFF)) > 8 ||
            abs((a and 0xFF) - (b and 0xFF)) > 8

    /** How wide the dark ink is: the glyphs, and not a pale ground behind them. */
    private fun inkWidth(image: BufferedImage): Int {
        val page = image.getRGB(2, 2)
        var left = -1
        var right = -1
        for (x in 0 until image.width) {
            val inked = (0 until image.height).any { y ->
                val rgb = image.getRGB(x, y)
                abs((rgb shr 16 and 0xFF) - (page shr 16 and 0xFF)) > Solid ||
                    abs((rgb shr 8 and 0xFF) - (page shr 8 and 0xFF)) > Solid ||
                    abs((rgb and 0xFF) - (page and 0xFF)) > Solid
            }
            if (inked) {
                if (left < 0) left = x
                right = x
            }
        }
        return if (left < 0) 0 else right - left + 1
    }

    private companion object {
        /** More than a stray antialiased edge; far fewer than any ground behind a word. */
        const val MinimumSeen = 40

        /** Past the light accent container, well inside the accent's own ink. */
        const val Solid = 96

        /** `GroundClearance`, 2dp at the scene's density of 2. */
        const val Clearance = 4f

        /** The clearance, and a pixel of antialiasing past it. */
        const val Margin = Clearance + 2f
    }
}
