package io.kontour.ui.foundation

import androidx.compose.foundation.layout.Column
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Share
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import io.kontour.ui.components.display.Stat
import io.kontour.ui.components.list.ListItem
import io.kontour.ui.components.selection.SegmentedControl
import io.kontour.ui.overlay.DropdownMenu
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.ProvideTokens
import io.kontour.ui.theme.Theme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * What a slot gives `+"…"`: the style and colour of the region it is in, so a
 * call site writes the words and nothing else.
 */
@OptIn(ExperimentalTestApi::class)
class SlotStyleTest {

    /** The text style and content colour a slot provides, as `+"…"` reads them. */
    private class Seen {
        var style: TextStyle? = null
        var colour: Color = Color.Unspecified
    }

    @Composable
    private fun probe(seen: Seen) {
        seen.style = LocalTextStyle.current
        seen.colour = LocalContentColour.current
    }

    private fun ComposeUiTest.themed(content: @Composable () -> Unit) {
        setContent { KontourTheme(darkTheme = false) { content() } }
        waitForIdle()
    }

    @Test
    fun aListRowsTrailingValueIsMutedBodyAndDimsWithTheRow() = runComposeUiTest {
        val on = Seen()
        val off = Seen()
        var body: TextStyle? = null
        var muted = Color.Unspecified
        themed {
            body = Theme.typography.bodyMedium
            muted = Theme.colours.contentMuted
            Column {
                ListItem(onClick = {}) {
                    +"Walk to the stop"
                    trailing { probe(on) }
                }
                ListItem(onClick = {}, enabled = false) {
                    +"Walk to the stop"
                    trailing { probe(off) }
                }
            }
        }
        assertEquals(body!!.fontSize, on.style!!.fontSize, "the trailing value is not in the body size")
        assertEquals(body!!.fontWeight, on.style!!.fontWeight)
        assertEquals(muted, on.colour, "the trailing value is not muted")
        assertNotEquals(on.colour, off.colour, "the trailing value does not dim with a disabled row")
    }

    /** An item given a trailing icon and a shortcut draws both, the icon first. */
    @Test
    fun aMenuItemKeepsItsIconBesideItsShortcut() = runComposeUiTest {
        var icon by mutableStateOf(false)
        themed {
            OverlayHost(Modifier.fillMaxSize()) {
                DropdownMenu(visible = true, onDismissRequest = {}) {
                    item("Share", trailingIcon = if (icon) Tabler.Outline.Share else null, shortcut = "⌘S") {}
                }
            }
        }
        // Ink in the strip just before the key cap, where the icon goes: the
        // menu is at least as wide as its floor either way, so its width says
        // nothing.
        fun ink(): Int {
            waitForIdle()
            val key = onNodeWithText("⌘S", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val map = onRoot().captureToImage().toPixelMap()
            val ground = map[(key.left - 48f).toInt(), key.center.y.toInt()]
            var count = 0
            for (x in (key.left - 28f).toInt() until (key.left - 4f).toInt()) {
                for (y in key.top.toInt() until key.bottom.toInt()) {
                    if (map[x, y] != ground) count++
                }
            }
            return count
        }
        val alone = ink()
        icon = true
        val both = ink()
        assertEquals(0, alone, "something is drawn before a lone key cap")
        assertTrue(both > 10, "the trailing icon was dropped: $both pixels of it")
    }

    @Test
    fun aStatLabelCarriesSpansAndIsStillAnnounced() = runComposeUiTest {
        themed {
            Stat {
                value("12")
                +buildAnnotatedString { append("Services today") }
            }
        }
        val spoken = onNodeWithText("Services today", substring = true, useUnmergedTree = false)
            .fetchSemanticsNode().config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }
        assertTrue(spoken.any { "Services today" in it && "12" in it }, "the stat announced $spoken")
    }

    @Test
    fun aSegmentedControlSetsItsOptionsInCapitalsWhenTheThemeDoes() = runComposeUiTest {
        themed {
            ProvideTokens(componentDefaults = Theme.componentDefaults.copy(uppercaseLabels = true)) {
                SegmentedControl(options = listOf("Day", "Week"), selectedIndex = 0, onSelectedIndexChange = {})
            }
        }
        onNodeWithText("DAY", useUnmergedTree = true).fetchSemanticsNode()
        onNodeWithText("WEEK", useUnmergedTree = true).fetchSemanticsNode()
    }
}
