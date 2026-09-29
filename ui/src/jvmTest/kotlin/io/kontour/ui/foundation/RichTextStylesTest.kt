package io.kontour.ui.foundation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Theme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The styles `richText` gained with underline, highlight, the type scale, a
 * colour and a gradient: each draws what it says, and the gradient is laid
 * across its own words rather than the whole paragraph.
 */
@OptIn(ExperimentalTestApi::class)
class RichTextStylesTest {

    private fun render(width: Int = 800, content: @Composable () -> Unit): PixelMap {
        val scene = ImageComposeScene(width = width, height = 200, density = Density(2f)) {
            KontourTheme(darkTheme = false) {
                Box(Modifier.background(Color.White)) { content() }
            }
        }
        try {
            scene.render(0L)
            return scene.render(16_000_000L).toComposeImageBitmap().toPixelMap()
        } finally {
            scene.close()
        }
    }

    /** Pixels close to [colour], opaque. */
    private fun PixelMap.near(x: Int, y: Int, colour: Color, slack: Float = 0.12f): Boolean {
        val p = this[x, y]
        return abs(p.red - colour.red) < slack && abs(p.green - colour.green) < slack &&
            abs(p.blue - colour.blue) < slack
    }

    /** The ink in columns [from] until [to]: pixels that are clearly not the white ground. */
    private fun PixelMap.ink(from: Int, to: Int): List<Color> = buildList {
        for (x in from until to) for (y in 0 until height) {
            val p = this@ink[x, y]
            // Opaque as well as dark: past the text's own box the scene is
            // transparent, which reads as black.
            if (p.alpha > 0.5f && p.red + p.green + p.blue < 2.2f) add(p)
        }
    }

    @Test
    fun aGradientRunsAcrossItsOwnWordsInTheMiddleOfASentence() {
        var layout: TextLayoutResult? = null
        val word = "gradient"
        val before = "A long run of words before the "
        val pixels = render {
            Text(
                richText { +before; gradient(listOf(Color.Red, Color.Blue), word); +" and after it." },
                fontSize = 20.sp,
                colour = Color.Black,
                onTextLayout = { layout = it },
            )
        }
        val result = requireNotNull(layout)
        val start = result.getBoundingBox(before.length)
        val end = result.getBoundingBox(before.length + word.length - 1)
        val first = pixels.ink(start.left.toInt(), start.right.toInt())
        val last = pixels.ink(end.left.toInt(), end.right.toInt())
        assertTrue(first.isNotEmpty() && last.isNotEmpty(), "the gradient's word drew no ink")
        // The first letter is mostly red and the last mostly blue. Sized to the
        // paragraph, as Compose sizes a span's brush, the whole word sat in the
        // red third of it.
        val firstRed = first.map { it.red - it.blue }.average()
        val lastBlue = last.map { it.blue - it.red }.average()
        assertTrue(firstRed > 0.3, "the word's first letter is not red: red minus blue is $firstRed")
        assertTrue(lastBlue > 0.3, "the word's last letter is not blue: blue minus red is $lastBlue")
        // And the words either side are still the text's own colour.
        val plain = pixels.ink(0, result.getBoundingBox(before.length - 3).right.toInt())
        assertTrue(plain.all { abs(it.red - it.blue) < 0.1f }, "the words before the gradient took some of it")
    }

    @Test
    fun aHighlightIsTheMarkersTintBehindTheWords() {
        var marker = Color.Unspecified
        var layout: TextLayoutResult? = null
        val pixels = render {
            marker = Theme.colours.warning.container
            Text(
                richText { +"Due "; highlight("tomorrow") },
                fontSize = 20.sp,
                onTextLayout = { layout = it },
            )
        }
        val box = requireNotNull(layout).getBoundingBox(4)
        // A corner of the first highlighted letter's box, clear of its ink.
        assertTrue(
            pixels.near(box.left.toInt() + 1, box.top.toInt() + 2, marker, slack = 0.02f),
            "no marker behind the highlighted word: ${pixels[box.left.toInt() + 1, box.top.toInt() + 2]} " +
                "where $marker was expected",
        )
    }

    @Test
    fun aColourIsTheColourGiven() {
        var layout: TextLayoutResult? = null
        val pixels = render {
            Text(richText { +"Line "; colour(Color(0xFF00A000), "green") }, fontSize = 20.sp, onTextLayout = { layout = it })
        }
        val result = requireNotNull(layout)
        val ink = pixels.ink(result.getBoundingBox(5).left.toInt(), result.getBoundingBox(9).right.toInt())
        assertTrue(ink.any { it.green > 0.5f && it.red < 0.2f && it.blue < 0.2f }, "the coloured word is not green")
    }

    @Test
    fun aStyleFromTheTypeScaleSetsItsSize() {
        var layout: TextLayoutResult? = null
        render {
            Text(
                richText { +"x"; style(typography.displaySmall, "x") },
                style = Theme.typography.bodyMedium,
                onTextLayout = { layout = it },
            )
        }
        val result = requireNotNull(layout)
        val small = result.getBoundingBox(0).width
        val large = result.getBoundingBox(1).width
        assertTrue(large > small * 1.8f, "a displaySmall x is ${large}px wide beside a bodyMedium one of ${small}px")
    }

    @Test
    fun anUnderlineIsAnUnderline() = runComposeUiTest {
        var built: androidx.compose.ui.text.AnnotatedString? = null
        setContent { KontourTheme { built = richText { +"a "; underline("b") } } }
        waitForIdle()
        val span = requireNotNull(built).spanStyles.single { it.start == 2 }
        assertEquals(TextDecoration.Underline, span.item.textDecoration)
    }

    @Test
    fun aGradientChangesNothingAScreenReaderHears() = runComposeUiTest {
        setContent {
            KontourTheme {
                Text(
                    richText { +"Meet "; gradient(listOf(Color.Red, Color.Blue), "Kontour"); +"." },
                    modifier = Modifier.testTag("t").width(300.dp),
                )
            }
        }
        val said = onNodeWithTag("t").fetchSemanticsNode().config[SemanticsProperties.Text].joinToString()
        assertEquals("Meet Kontour.", said)
    }
}
