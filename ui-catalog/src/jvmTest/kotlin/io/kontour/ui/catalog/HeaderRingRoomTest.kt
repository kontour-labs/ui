package io.kontour.ui.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.kontour.ui.components.action.IconButton
import io.kontour.ui.nav.TopBar
import io.kontour.ui.nav.TopBarStyle
import io.kontour.ui.sheet.SheetHeader
import io.kontour.ui.sheet.SheetHeaderStyle
import io.kontour.ui.theme.ContrastLevel
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Theme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * At the high-contrast tier a bar's title keeps clear of its buttons' rings.
 *
 * Reported for 0.5.6 from Anyways' stop page: *"the back circle's ring touches
 * 'Warwick Farm Station', and the close button on the trailing end has the
 * same tight look"*. A sheet header put its title straight after the back
 * button, whose 48dp box is a 24dp glyph and its own padding; at the
 * high-contrast tier a ghost button draws its edge round the whole box, so the
 * padding is inside a ring and the title ran into it. The title now keeps
 * `sm` from a button at that tier, in a sheet header and a top bar alike, and
 * nothing changes at the standard tier, where nothing is drawn round the glyph.
 *
 * At a density of 1, so dp are pixels.
 */
@OptIn(ExperimentalTestApi::class)
class HeaderRingRoomTest {

    private class Gaps(val back: Rect, val title: Rect, val close: Rect, val bar: Float)

    private fun measure(contrast: ContrastLevel, bar: @Composable () -> Unit): Gaps {
        var gaps: Gaps? = null
        runDesktopComposeUiTest(width = 400, height = 200) {
            setContent {
                KontourTheme(darkTheme = false, contrast = contrast, reduceMotion = true) {
                    Box(Modifier.fillMaxSize()) { bar() }
                }
            }
            waitForIdle()
            gaps = Gaps(
                back = onNodeWithContentDescription("Back").fetchSemanticsNode().boundsInRoot,
                title = onNodeWithText("Warwick Farm Station").fetchSemanticsNode().boundsInRoot,
                close = onNodeWithContentDescription("Close").fetchSemanticsNode().boundsInRoot,
                bar = 400f,
            )
        }
        return gaps!!
    }

    private fun header(style: SheetHeaderStyle): @Composable () -> Unit = {
        SheetHeader(onClose = {}, onBack = {}, style = style) { +"Warwick Farm Station" }
    }

    private fun topBar(style: TopBarStyle): @Composable () -> Unit = {
        TopBar(
            onBack = {},
            style = style,
            windowInsets = WindowInsets(0),
            actions = { IconButton(icon = Theme.icons.close, contentDescription = "Close", onClick = {}) },
        ) { +"Warwick Farm Station" }
    }

    private fun assertRoom(what: String, gaps: Gaps, atLeast: Float, atMost: Float = Float.MAX_VALUE) {
        val lead = gaps.title.left - gaps.back.right
        assertTrue(lead in (atLeast - 1f)..(atMost + 1f), "$what: the title is ${lead}dp after the back button, wanted $atLeast..$atMost")
        val trail = gaps.close.left - gaps.title.right
        assertTrue(trail >= atLeast - 1f, "$what: the title is ${trail}dp before the close button, wanted at least $atLeast")
    }

    @Test
    fun aSheetHeadersTitleKeepsClearOfTheRings() {
        // The standard tier, as it was: the button's own padding is the room.
        assertRoom("SheetHeader, standard", measure(ContrastLevel.Standard, header(SheetHeaderStyle.Small)), atLeast = 0f, atMost = 0f)
        assertRoom("SheetHeader, high", measure(ContrastLevel.High, header(SheetHeaderStyle.Small)), atLeast = 12f, atMost = 12f)
    }

    @Test
    fun aTopBarsTitleKeepsTheSameRoom() {
        // Its title's own `xs` at the standard tier, and a sheet header's `sm` at the high one.
        assertRoom("TopBar, standard", measure(ContrastLevel.Standard, topBar(TopBarStyle.Small)), atLeast = 8f, atMost = 8f)
        assertRoom("TopBar, high", measure(ContrastLevel.High, topBar(TopBarStyle.Small)), atLeast = 12f, atMost = 12f)
    }

    /** The room is the controls' side's, so a centred title is still on the bar's middle. */
    @Test
    fun aCentredTitleStaysCentred() {
        for (contrast in ContrastLevel.entries) {
            val sheet = measure(contrast, header(SheetHeaderStyle.Centred))
            assertTrue(abs(sheet.title.center.x - sheet.bar / 2) <= 1f, "SheetHeader, $contrast: the centred title is at ${sheet.title.center.x}")
            val top = measure(contrast, topBar(TopBarStyle.Centred))
            assertTrue(abs(top.title.center.x - top.bar / 2) <= 1f, "TopBar, $contrast: the centred title is at ${top.title.center.x}")
            assertRoom("SheetHeader centred, $contrast", sheet, atLeast = if (contrast == ContrastLevel.High) 12f else 0f)
        }
    }
}
