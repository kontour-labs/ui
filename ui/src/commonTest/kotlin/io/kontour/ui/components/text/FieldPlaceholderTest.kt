package io.kontour.ui.components.text

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.ui.theme.KontourTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * An empty field's placeholder sits where its text will start: at the top of
 * the input, at its start edge — the top left, or the top right right to left.
 *
 * Reported as *"in large text fields, the placeholder text is centred. it
 * should be top-left aligned (obviously please account for RTL layouts too),
 * like the text itself"*, and then *"please fix it in single-line fields too"*.
 * The placeholder shared a box with the input and was centred in it: in a text
 * area the input is several lines tall and its text starts at the top, so the
 * hint floated a line or more below where the first character goes. It was cut
 * to one line as well, however many the area had. In a single-line field the
 * hint was where the text goes, but the empty input, a pixel shorter than one
 * with a character in it, was centred a pixel below it, and its caret jumped
 * up a pixel at the first character.
 */
@OptIn(ExperimentalTestApi::class)
class FieldPlaceholderTest {

    private class Measured(val placeholder: Rect, val input: Rect)

    private fun ComposeUiTest.measure(hint: String): Measured {
        waitForIdle()
        val placeholder = onNodeWithText(hint, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val input = onNode(hasSetTextAction()).fetchSemanticsNode().boundsInRoot
        return Measured(placeholder, input)
    }

    private fun ComposeUiTest.area(hint: String, rtl: Boolean = false, width: Dp = 320.dp, minLines: Int = 3): Measured {
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                KontourTheme {
                    Box(Modifier.width(width)) {
                        TextArea(
                            state = rememberTextFieldState(),
                            label = "What went wrong?",
                            placeholder = hint,
                            lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = minLines, maxHeightInLines = 8),
                        )
                    }
                }
            }
        }
        return measure(hint)
    }

    private fun ComposeUiTest.field(hint: String, rtl: Boolean = false, density: Float = 1f): Measured {
        setContent {
            CompositionLocalProvider(
                LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                LocalDensity provides Density(density),
            ) {
                KontourTheme {
                    Box(Modifier.width(320.dp)) {
                        TextField(state = rememberTextFieldState(), label = "Where to?", placeholder = hint)
                    }
                }
            }
        }
        return measure(hint)
    }

    private fun near(expected: Float, actual: Float, what: String) =
        assertTrue(abs(expected - actual) < 1f, "$what: expected $expected, was $actual")

    @Test
    fun anEmptyAreasPlaceholderStartsAtTheTopStart() = runComposeUiTest {
        val m = area("Which service, where, and when")
        near(m.input.top, m.placeholder.top, "the placeholder's top against the input's, where the first line goes")
        near(m.input.left, m.placeholder.left, "the placeholder's start against the input's")
    }

    @Test
    fun rightToLeftTheAreasPlaceholderStartsAtTheTopRight() = runComposeUiTest {
        val m = area("Which service, where, and when", rtl = true)
        near(m.input.top, m.placeholder.top, "right to left, the placeholder's top against the input's")
        near(m.input.right, m.placeholder.right, "right to left, the placeholder's start — its right edge — against the input's")
    }

    @Test
    fun aLongPlaceholderWrapsOverTheAreasLines() = runComposeUiTest {
        val long = "Which service it was, the stop you were waiting at, and what time you got there"
        val m = area(long, width = 200.dp)
        val line = m.input.height / 3f
        assertTrue(m.placeholder.height > line * 1.5f, "a long placeholder was cut to one line: ${m.placeholder.height}px of a ${line}px line")
        assertTrue(m.placeholder.height <= m.input.height + 0.5f, "the placeholder ran past the area's three lines: ${m.placeholder.height}px of ${m.input.height}")
    }

    @Test
    fun aSingleLineFieldsPlaceholderIsWhereItsTextGoes() {
        for (density in Densities) runComposeUiTest {
            val m = field("Station, stop or address", density = density)
            near(m.input.top, m.placeholder.top, "at ${density}x, a single-line field's placeholder top against its input's")
            near(m.input.left, m.placeholder.left, "at ${density}x, a single-line field's placeholder start against its input's")
            // One line of the same type. Within a pixel rather than under one:
            // an empty input measures a pixel shorter than a line of text.
            assertTrue(
                abs(m.input.height - m.placeholder.height) <= 1f,
                "at ${density}x, a single-line field's placeholder is not one line of the input's type: " +
                    "${m.placeholder.height}px against ${m.input.height}",
            )
        }
    }

    @Test
    fun theFirstCharacterGoesWhereThePlaceholderWas() {
        for (density in Densities) runComposeUiTest {
            val m = field("Station, stop or address", density = density)
            onNode(hasSetTextAction()).performTextInput("P")
            waitForIdle()
            val typed = onNode(hasSetTextAction()).fetchSemanticsNode().boundsInRoot
            // Where the text goes: the hint's line, and the input — and its
            // caret — where it was while empty. An empty input measures a pixel
            // shorter than one with a character in it; centred, it sat a pixel
            // below the hint and jumped up at the first character. Sized by the
            // input alone instead, the box put the hint a pixel below the
            // typed text at some densities and not others.
            near(m.placeholder.top, typed.top, "at ${density}x, the typed text's top against where the placeholder was")
            near(m.input.top, typed.top, "at ${density}x, the input's top once a character is typed against its top empty")
        }
    }

    @Test
    fun rightToLeftASingleLineFieldsPlaceholderIsWhereItsTextGoes() = runComposeUiTest {
        val m = field("Station, stop or address", rtl = true)
        near(m.input.top, m.placeholder.top, "right to left, a single-line field's placeholder top against its input's")
        near(m.input.right, m.placeholder.right, "right to left, a single-line field's placeholder start against its input's")
    }

    private companion object {
        /** A desktop's, a 2x screen's, a common Android phone's and a 3x screen's. */
        val Densities = listOf(1f, 2f, 2.625f, 3f)
    }
}
