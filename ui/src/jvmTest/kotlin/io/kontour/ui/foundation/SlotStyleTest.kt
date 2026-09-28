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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.display.BubbleSide
import io.kontour.ui.components.display.Callout
import io.kontour.ui.components.display.ChatBubble
import io.kontour.ui.components.display.Gauge
import io.kontour.ui.components.display.GaugeIndicator
import io.kontour.ui.components.display.Meter
import io.kontour.ui.components.display.Timeline
import io.kontour.ui.components.display.TimelineItem
import io.kontour.ui.components.selection.Knob
import io.kontour.ui.overlay.Dialog
import io.kontour.ui.overlay.Popover
import io.kontour.ui.components.display.Stat
import io.kontour.ui.components.list.ListItem
import io.kontour.ui.components.selection.SegmentedControl
import io.kontour.ui.overlay.DropdownMenu
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.ProvideTokens
import io.kontour.ui.theme.Tone
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

    // --- the containers that took `+` ----------------------------------------------

    /** Asserts [seen] was given [expected]'s size and weight. */
    private fun assertStyle(expected: TextStyle, seen: Seen, what: String) {
        assertEquals(expected.fontSize, seen.style?.fontSize, "$what is not in the expected size")
        assertEquals(expected.fontWeight, seen.style?.fontWeight, "$what is not in the expected weight")
    }

    @Test
    fun aChatBubblesMessageIsBodyAndItsMetaIsSmall() = runComposeUiTest {
        val message = Seen()
        val meta = Seen()
        var body: TextStyle? = null
        var small: TextStyle? = null
        themed {
            body = Theme.typography.bodyMedium
            small = Theme.typography.labelSmall
            ChatBubble(side = BubbleSide.Incoming, meta = { probe(meta) }) { probe(message) }
        }
        assertStyle(body!!, message, "the message")
        assertStyle(small!!, meta, "the meta")
    }

    @Test
    fun aCalloutIsSmallBodyInItsTone() = runComposeUiTest {
        val seen = Seen()
        var small: TextStyle? = null
        themed {
            small = Theme.typography.bodySmall
            Callout(tone = Tone.Warning) { probe(seen) }
        }
        assertStyle(small!!, seen, "the callout")
        assertNotEquals(Color.Unspecified, seen.colour)
    }

    @Test
    fun aTimelineItemsNameIsATitleAndItsDetailIsMuted() = runComposeUiTest {
        val name = Seen()
        val detail = Seen()
        var title: TextStyle? = null
        var small: TextStyle? = null
        var muted = Color.Unspecified
        themed {
            title = Theme.typography.titleSmall
            small = Theme.typography.bodySmall
            muted = Theme.colours.contentMuted
            Timeline {
                TimelineItem {
                    label { probe(name) }
                    supporting { probe(detail) }
                }
            }
        }
        assertStyle(title!!, name, "the stop's name")
        assertStyle(small!!, detail, "the stop's detail")
        assertEquals(muted, detail.colour, "the stop's detail is not muted")
    }

    @Test
    fun aDialogsTitleIsLargeAndAHeading() = runComposeUiTest {
        val seen = Seen()
        var large: TextStyle? = null
        themed {
            large = Theme.typography.titleLarge
            OverlayHost(Modifier.fillMaxSize()) {
                Dialog(visible = true, onDismissRequest = {}, title = { probe(seen); +"Filter departures" }) {}
            }
        }
        assertStyle(large!!, seen, "the dialog's title")
        onNode(isHeading() and hasText("Filter departures")).fetchSemanticsNode()
    }

    @Test
    fun aPopoversTitleIsSmallAndAHeading() = runComposeUiTest {
        val seen = Seen()
        var small: TextStyle? = null
        themed {
            small = Theme.typography.titleSmall
            OverlayHost(Modifier.fillMaxSize()) {
                Box {
                    Popover(visible = true, onDismissRequest = {}, title = { probe(seen); +"Route 950" }) {}
                }
            }
        }
        assertStyle(small!!, seen, "the popover's title")
        onNode(isHeading() and hasText("Route 950")).fetchSemanticsNode()
    }

    @Test
    fun aReadoutIsSizedByItsDial() = runComposeUiTest {
        val open = Seen()
        val hub = Seen()
        val caption = Seen()
        val knob = Seen()
        val bar = Seen()
        var typography: io.kontour.ui.theme.Typography? = null
        themed {
            typography = Theme.typography
            Column {
                Gauge(value = 0.4f, size = 160.dp) {
                    value { probe(open) }
                    label { probe(caption) }
                }
                Gauge(value = 0.4f, size = 160.dp, indicator = GaugeIndicator.Needle) { value { probe(hub) } }
                Knob(value = 0.4f, onValueChange = {}, contentDescription = "Volume") { value { probe(knob) } }
                Meter(value = 0.4f, modifier = Modifier.fillMaxWidth()) {
                    +"40%"
                    label { probe(bar) }
                }
            }
        }
        val type = typography!!
        assertStyle(type.displaySmall, open, "a large dial's figure")
        assertStyle(type.titleLarge, hub, "a figure under a needle's hub")
        assertStyle(type.labelSmall, caption, "a dial's caption")
        assertStyle(type.titleMedium, knob, "a knob's figure")
        assertStyle(type.labelMedium, bar, "a meter's caption")
    }
}
