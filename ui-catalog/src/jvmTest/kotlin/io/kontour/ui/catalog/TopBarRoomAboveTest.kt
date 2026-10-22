package io.kontour.ui.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.icons.tabler.Tabler
import io.kontour.icons.tabler.outline.Star
import io.kontour.ui.catalog.apps.AppHost
import io.kontour.ui.catalog.apps.MiniApp
import io.kontour.ui.components.action.IconButton
import io.kontour.ui.nav.TopBar
import io.kontour.ui.nav.TopBarStyle
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Theme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A large top bar with nothing on its first line keeps room above its title
 * where there is no status bar to give it some.
 *
 * Reported on the desktop: *"on desktop in your mini-apps, the large top bar has
 * no spacing above it. can you make sure it's not a library issue?"* It was the
 * library's. A `Large` bar leaves its first line out when it has no back button,
 * navigation or actions, so the title takes the line's place — and the only
 * room left above the title was the status bar's. On a phone that is 24dp or
 * more; in a desktop window it is nothing, and the title's line box started on
 * the window's top edge, its capitals 9dp under it and higher than a `Small`
 * bar's title sits. The mini-apps' root pages on the desktop are exactly that
 * bar: the way out is at the sidebar's head there, not in the bar.
 *
 * The room is the larger of the status bar and `Theme.spacing.md`, the large
 * title's margin at its sides, not the two added, so a phone's bar is what it
 * was. And it is the open bar's: collapsed, the bar is a `Small` one to the
 * pixel, with no more room above than a `Small` bar has.
 *
 * Measured from the title's node at a density of 1, so its bounds are in dp.
 */
@OptIn(ExperimentalTestApi::class)
class TopBarRoomAboveTest {

    private val heading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)

    /** The bounds of the title's words, in the bar's heading. */
    private fun ComposeUiTest.title(text: String): Rect =
        onAllNodes((heading and hasText(text)) or (hasText(text) and hasAnyAncestor(heading)), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .first()
            .boundsInRoot

    private class Measured(val bar: Rect, val title: Rect, val margin: Dp)

    private fun measure(
        style: TopBarStyle = TopBarStyle.Large,
        inset: Dp = 0.dp,
        controls: Boolean = false,
        progress: Float = 0f,
        direction: LayoutDirection = LayoutDirection.Ltr,
        fontScale: Float = 1f,
    ): Measured {
        var result: Measured? = null
        runDesktopComposeUiTest(width = 400, height = 600) {
            var margin = 0.dp
            setContent {
                CompositionLocalProvider(
                    LocalLayoutDirection provides direction,
                    LocalDensity provides Density(1f, fontScale),
                ) {
                    KontourTheme(darkTheme = false) {
                        margin = Theme.spacing.md
                        Box(Modifier.fillMaxSize()) {
                            TopBar(
                                modifier = Modifier.testTag("bar"),
                                style = style,
                                onBack = if (controls) ({}) else null,
                                actions = if (controls) ({ IconButton(icon = Tabler.Outline.Star, contentDescription = "Save", onClick = {}) }) else null,
                                collapseProgress = progress,
                                windowInsets = WindowInsets(top = inset),
                            ) { +"Perth" }
                        }
                    }
                }
            }
            waitForIdle()
            result = Measured(onNodeWithTag("bar").fetchSemanticsNode().boundsInRoot, title("Perth"), margin)
        }
        return requireNotNull(result)
    }

    /** With no status bar, the title keeps the large title's margin above it. */
    @Test
    fun withNoStatusBarTheTitleKeepsItsMarginAboveIt() {
        val open = measure()
        assertEquals(
            open.margin.value,
            open.title.top - open.bar.top,
            "a Large bar with nothing on its first line put its title ${open.title.top - open.bar.top}dp under its top edge",
        )
    }

    /** The larger of the two, not both: a phone's status bar is room enough. */
    @Test
    fun aStatusBarIsTheRoomRatherThanAddedToIt() {
        val phone = measure(inset = 24.dp)
        assertEquals(24f, phone.title.top - phone.bar.top, "under a 24dp status bar the title moved")
        val iPhone = measure(inset = 47.dp)
        assertEquals(47f, iPhone.title.top - iPhone.bar.top, "under a 47dp status bar the title moved")
        val shallow = measure(inset = 8.dp)
        assertEquals(
            shallow.margin.value,
            shallow.title.top - shallow.bar.top,
            "an inset shallower than the margin either added to it or left the title short of it",
        )
    }

    /** Collapsed, the bar is a Small bar: no room is left above it. */
    @Test
    fun collapsedItIsASmallBarWithNoRoomLeftOver() {
        val collapsed = measure(progress = 1f)
        val small = measure(style = TopBarStyle.Small)
        assertEquals(small.bar.height, collapsed.bar.height, "the collapsed bar kept room a Small bar has not")
        assertEquals(small.title.top, collapsed.title.top, "the collapsed title is not where a Small bar's is")
        val open = measure()
        assertTrue(open.bar.height > collapsed.bar.height, "the open bar is no taller than the collapsed one")
    }

    /** A bar with controls has its first line above the title, and that is its room; nothing changes there. */
    @Test
    fun aBarWithControlsIsAsItWas() {
        val bar = measure(controls = true)
        // The first line, a Small bar's height, above the title's.
        assertEquals(56f, bar.title.top - bar.bar.top, "a Large bar's title moved under its first line")
    }

    /** Right to left, and at twice the type size, the room above is the same. */
    @Test
    fun rightToLeftAndAtLargeTypeTheRoomIsTheSame() {
        val rtl = measure(direction = LayoutDirection.Rtl)
        assertEquals(rtl.margin.value, rtl.title.top - rtl.bar.top, "right to left, the title lost its room")
        val large = measure(fontScale = 2f)
        assertEquals(large.margin.value, large.title.top - large.bar.top, "at 200% type, the title lost its room")
        val collapsed = measure(fontScale = 2f, progress = 1f)
        val small = measure(style = TopBarStyle.Small, fontScale = 2f)
        assertEquals(small.bar.height, collapsed.bar.height, "at 200% type the collapsed bar is not a Small one")
    }

    /**
     * What was reported: the mini-apps' root pages in a desktop window, whose
     * large bars have nothing on the first line.
     */
    @Test
    fun onADesktopTheAppsLargeTitlesHaveRoomAbove() {
        val pages = listOf(
            Triple(MiniApp.Transit, "tab-Stops", "Stops"),
            Triple(MiniApp.Transit, "tab-Trips", "Trips"),
            Triple(MiniApp.Energy, "energy-tab-Usage", "Usage"),
        )
        for ((app, tab, words) in pages) {
            runDesktopComposeUiTest(width = 1280, height = 800) {
                var margin = 0.dp
                setContent {
                    KontourTheme(darkTheme = false) {
                        margin = Theme.spacing.md
                        AppHost(app, onLeave = {})
                    }
                }
                waitForIdle()
                onNodeWithTag(tab, useUnmergedTree = true).performClick()
                waitForIdle()
                val top = title(words).top
                assertTrue(top >= margin.value, "$app's $words page put its large title ${top}dp from the window's top")
            }
        }
    }
}
