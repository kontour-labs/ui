package io.kontour.ui.foundation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.kontour.ui.theme.KontourTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * An icon put in a sentence with `richText` is drawn by `Text` itself — no map
 * of inline content at the call site — at the text's size, in its colour or its
 * own, and read out as part of the sentence or not at all.
 */
@OptIn(ExperimentalTestApi::class)
class RichTextIconTest {

    /** A glyph that fills its box, so any pixel of it shows. */
    private val Square = ImageVector.Builder("square", 24.dp, 24.dp, 24f, 24f)
        .path(fill = SolidColor(Color.Black)) {
            moveTo(0f, 0f); lineTo(24f, 0f); lineTo(24f, 24f); lineTo(0f, 24f); close()
        }
        .build()

    private fun render(content: @androidx.compose.runtime.Composable () -> Unit): PixelMap {
        val scene = ImageComposeScene(width = 400, height = 120, density = Density(2f)) {
            KontourTheme(darkTheme = false) { content() }
        }
        try {
            scene.render(0L)
            return scene.render(16_000_000L).toComposeImageBitmap().toPixelMap()
        } finally {
            scene.close()
        }
    }

    private fun PixelMap.count(colour: Color): Int {
        var n = 0
        for (x in 0 until width) for (y in 0 until height) {
            val p = this[x, y]
            if (abs(p.red - colour.red) < 0.02f && abs(p.green - colour.green) < 0.02f &&
                abs(p.blue - colour.blue) < 0.02f && p.alpha > 0.98f
            ) n++
        }
        return n
    }

    @Test
    fun anIconIsDrawnInItsOwnTintAtTheTextsSize() {
        val pixels = render {
            Text(richText { +"Platform 3 "; icon(Square, "step-free access", tint = Color.Red); +" 4 min" }, fontSize = 20.sp)
        }
        // 1em of 20sp at density 2 is a 40px square: most of 1,600 pixels,
        // allowing for the edges' anti-aliasing.
        val red = pixels.count(Color.Red)
        assertTrue(red in 1_300..1_700, "expected a 40px square of the tint, found $red pixels of it")
    }

    @Test
    fun anUntintedIconIsDrawnInTheTextsColour() {
        val pixels = render {
            Text(richText { +Square; +" Every 15 minutes" }, colour = Color.Blue, fontSize = 20.sp)
        }
        assertTrue(pixels.count(Color.Blue) > 1_300, "the icon was not drawn in the text's colour")
    }

    /** `tone(Tone.Danger) { +icon; +"Cancelled" }` is one red phrase, icon and all. */
    @Test
    fun anUntintedIconTakesTheColourOfTheSpanItIsIn() {
        var danger = Color.Unspecified
        val pixels = render {
            danger = io.kontour.ui.theme.Theme.colours.danger.onContainer
            Text(
                richText { +"Route 950 "; tone(io.kontour.ui.theme.Tone.Danger) { +Square } },
                colour = Color.Blue,
                fontSize = 20.sp,
            )
        }
        // Only the icon is in the tone, so a square's worth of it is the icon.
        assertTrue(pixels.count(danger) > 1_300, "the icon was not drawn in its tone's colour")
    }

    @Test
    fun sizeIsInTheTextsOwnUnits() {
        val pixels = render {
            Text(richText { icon(Square, null, tint = Color.Red, size = 2.em) }, fontSize = 10.sp)
        }
        // 2em of 10sp is 20sp: 40px at density 2.
        val red = pixels.count(Color.Red)
        assertTrue(red in 1_300..1_700, "2em of 10sp should be a 40px square, found $red pixels")
    }

    @Test
    fun aDescriptionIsReadAsPartOfTheSentence() = runComposeUiTest {
        setContent {
            KontourTheme(darkTheme = false) {
                Text(
                    richText { +"Platform 3 "; icon(Square, "step-free access"); +" 4 min" },
                    Modifier.testTag("text"),
                )
            }
        }
        val node = onNodeWithTag("text").fetchSemanticsNode()
        val read = node.config[SemanticsProperties.Text].joinToString { it.text }
        assertEquals("Platform 3 step-free access 4 min", read)
    }

    @Test
    fun aDecorativeIconAddsNothingToRead() = runComposeUiTest {
        setContent {
            KontourTheme(darkTheme = false) {
                Text(richText { +Square; +"Every 15 minutes" }, Modifier.testTag("text"))
            }
        }
        val node = onNodeWithTag("text").fetchSemanticsNode()
        val read = node.config[SemanticsProperties.Text].joinToString { it.text }
        assertEquals("Every 15 minutes", read.replace("\u200B", ""))
        assertTrue(
            node.children.none { SemanticsProperties.ContentDescription in it.config },
            "the icon is a node of its own a screen reader stops on",
        )
    }

    @Test
    fun theCallersOwnInlineContentStillWorksBesideAnIcon() = runComposeUiTest {
        setContent {
            KontourTheme(darkTheme = false) {
                Text(
                    richText {
                        +Square; +" Press "
                        +androidx.compose.ui.text.buildAnnotatedString { appendInlineContent("key", "K") }
                    },
                    inlineContent = mapOf(
                        "key" to InlineTextContent(Placeholder(1.em, 1.em, PlaceholderVerticalAlign.TextCenter)) {
                            Box(Modifier.fillMaxSize().testTag("key"))
                        },
                    ),
                )
            }
        }
        onNodeWithTag("key").assertExists()
    }

    @Test
    fun theSameIconBuildsTheSameString() = runComposeUiTest {
        var first: androidx.compose.ui.text.AnnotatedString? = null
        var second: androidx.compose.ui.text.AnnotatedString? = null
        setContent {
            KontourTheme(darkTheme = false) {
                first = richText { +Square; icon(Square, "again", tint = Color.Red) }
                second = richText { +Square; icon(Square, "again", tint = Color.Red) }
            }
        }
        waitForIdle()
        assertEquals(first, second, "the same icons built different strings, so the text would be laid out again")
    }
}
