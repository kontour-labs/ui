package io.kontour.ui.components.text

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A select's menu is exactly as wide as the field it opened from, even when
 * its options are much shorter than the field.
 *
 * Reported from a phone: the select, multi-select and combobox menus "are no
 * longer the same width as the field that they opened from". The menu still
 * asked for the field's width. But when submenus began opening in place, the
 * panel was wrapped in a box, and a box does not pass a minimum width on. So
 * the panel hugged its short rows and sat inside the width it was given, against
 * the field's leading edge.
 */
@OptIn(ExperimentalTestApi::class)
class SelectMenuWidthTest {

    @Test
    fun aSelectsMenuIsTheFieldsWidth() = runComposeUiTest {
        show {
            Select(
                value = null,
                options = Options,
                onValueChange = {},
                label = "Route",
                state = rememberSelectState(initialExpanded = true),
                modifier = Modifier.width(FieldWidth.dp),
            )
        }
        assertMenuMatchesField("a select")
    }

    @Test
    fun aMultiSelectsMenuIsTheFieldsWidth() = runComposeUiTest {
        show {
            MultiSelect(
                value = emptySet(),
                options = Options,
                onValueChange = {},
                label = "Route",
                state = rememberSelectState(initialExpanded = true),
                modifier = Modifier.width(FieldWidth.dp),
            )
        }
        assertMenuMatchesField("a multi-select")
    }

    @Test
    fun aComboboxsMenuIsTheFieldsWidth() = runComposeUiTest {
        show {
            Combobox(
                value = null,
                options = Options,
                onValueChange = {},
                label = "Route",
                state = rememberSelectState(initialExpanded = true),
                modifier = Modifier.width(FieldWidth.dp),
            )
        }
        assertMenuMatchesField("a combobox")
    }

    private fun ComposeUiTest.show(field: @Composable () -> Unit) {
        setContent {
            KontourTheme(darkTheme = false, reduceMotion = true) {
                OverlayHost(Modifier.fillMaxSize().background(Page)) {
                    Box(Modifier.padding(start = 40.dp, top = 40.dp)) { field() }
                }
            }
        }
        waitForIdle()
    }

    /**
     * Reads the panel's edges from the picture, across the middle of its first
     * row. The page behind it is a colour no menu is drawn in, so the panel runs
     * from the first pixel in its own colour to the last.
     */
    private fun ComposeUiTest.assertMenuMatchesField(what: String) {
        val field = onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.DropdownList))
            .fetchSemanticsNodes().single().boundsInRoot
        val row = onAllNodesWithText(Options.first()).fetchSemanticsNodes().last().boundsInRoot
        assertTrue(row.top > field.bottom, "$what's menu did not open below its field: field $field, row $row")

        val pixels = onRoot().captureToImage().toPixelMap()
        val y = row.center.y.toInt()
        // Just inside the row's own end, past its short label: the panel's colour.
        val panel = pixels[(row.right - 3f).toInt(), y]
        fun inPanel(x: Int): Boolean {
            val c = pixels[x, y]
            return abs(c.red - panel.red) < 0.02f && abs(c.green - panel.green) < 0.02f &&
                abs(c.blue - panel.blue) < 0.02f
        }
        val scan = (field.left - 30f).toInt().coerceAtLeast(0)..(field.right + 30f).toInt().coerceAtMost(pixels.width - 1)
        val left = scan.first(::inPanel)
        val right = scan.last(::inPanel)
        val drawn = Rect(left.toFloat(), row.top, right + 1f, row.bottom)
        assertTrue(
            abs(drawn.left - field.left) <= EdgeSlack && abs(drawn.right - field.right) <= EdgeSlack,
            "$what's menu was drawn from ${drawn.left} to ${drawn.right} (${drawn.width}px), and its field " +
                "from ${field.left} to ${field.right} (${field.width}px) — the menu should be the field's width",
        )
    }

    private companion object {
        /** Much wider than any option, so a menu that hugs its rows shows it. */
        const val FieldWidth = 320

        val Options = listOf("A", "B", "C")

        val Page = Color(0xFFB4D2B4)

        /** The panel's hairline and anti-aliased corners, at most. */
        const val EdgeSlack = 3f
    }
}
