package io.kontour.ui.input

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.datetime.CalendarMonth
import io.kontour.ui.components.datetime.TimeField
import io.kontour.ui.components.display.Card
import io.kontour.ui.components.selection.ColourPicker
import io.kontour.ui.foundation.Text
import io.kontour.ui.overlay.MenuItem
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Theme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * The controls that had no visible keyboard focus at any tier now show one.
 *
 * A menu row, a day in a calendar, a time field, a clickable card and the colour
 * picker's square and tracks each took focus and drew nothing to say so — the
 * last two could not take focus at all. Each is focused here with the keyboard
 * as the input, and the focus ring's colour has to appear where it did not
 * before; the colour picker's track also has to answer an arrow key.
 */
@OptIn(ExperimentalTestApi::class)
class KeyboardFocusRingTest {

    @Test
    fun aMenuRowShowsWhereTheKeyboardIs() = ringAppears({ onNodeWithText("Rename") }) {
        MenuItem(onClick = {}) { +"Rename" }
    }

    @Test
    fun aDayInACalendarShowsWhereTheKeyboardIs() = ringAppears({ onNodeWithText("18") }) {
        CalendarMonth(
            month = LocalDate(2026, 6, 1),
            isSelected = { false },
            onSelectedChange = {},
        )
    }

    @Test
    fun aTimeFieldShowsWhereTheKeyboardIs() = ringAppears({ onNodeWithContentDescription("Leave at") }) {
        TimeField(value = LocalTime(8, 15), onClick = {}, label = "Leave at")
    }

    @Test
    fun aClickableCardShowsWhereTheKeyboardIs() = ringAppears({ onNodeWithText("Perth Busport") }) {
        Card(onClick = {}) { Text("Perth Busport") }
    }

    @Test
    fun theColourPickersSquareAndTracksTakeFocusAndShowIt() {
        ringAppears({ onNodeWithContentDescription("Hue") }) { picker() }
        ringAppears({ onNodeWithContentDescription("Saturation and brightness") }) { picker() }
    }

    @Test
    fun theColourPickersHueTrackAnswersAnArrowKey() = runComposeUiTest {
        var picked = Color(0xFFFF0000)
        setContent {
            KontourTheme {
                ColourPicker(colour = Color(0xFFFF0000), onColourChange = { picked = it }, showValueField = false)
            }
        }
        val hue = onNodeWithContentDescription("Hue")
        hue.requestFocus()
        hue.performKeyInput { pressKey(Key.DirectionRight) }
        waitForIdle()
        assertNotEquals(Color(0xFFFF0000), picked, "the hue track took focus and ignored the arrow key")
    }

    @Composable
    private fun picker() {
        ColourPicker(colour = Color(0xFF2E8B57), onColourChange = {}, showValueField = false)
    }

    /**
     * Focuses the node [find] names with the keyboard as the input, and holds that
     * pixels of the focus ring's colour appear that were not there before.
     */
    private fun ringAppears(
        find: ComposeUiTest.() -> SemanticsNodeInteraction,
        content: @Composable () -> Unit,
    ) = runComposeUiTest {
        var ring = Color.Unspecified
        setContent {
            KontourTheme {
                ring = Theme.colours.focusRing
                CompositionLocalProvider(LocalInputModality provides InputModality.Keyboard) {
                    Box(Modifier.background(Theme.colours.background).padding(12.dp).width(360.dp)) { content() }
                }
            }
        }
        waitForIdle()
        val before = ringPixels(ring)
        find().requestFocus()
        waitForIdle()
        val after = ringPixels(ring)
        assertTrue(
            after - before >= MinimumRingPixels,
            "focused from the keyboard, it drew $after pixels of the focus ring's colour against $before " +
                "before it had focus — no visible sign of where the keyboard is",
        )
    }

    private fun ComposeUiTest.ringPixels(ring: Color): Int {
        val pixels = onRoot().captureToImage().toPixelMap()
        var count = 0
        for (y in 0 until pixels.height) {
            for (x in 0 until pixels.width) {
                val pixel = pixels[x, y]
                if (abs(pixel.red - ring.red) < Tolerance &&
                    abs(pixel.green - ring.green) < Tolerance &&
                    abs(pixel.blue - ring.blue) < Tolerance
                ) {
                    count++
                }
            }
        }
        return count
    }

    private companion object {
        /** A ring round even the smallest of these is well over this many pixels. */
        const val MinimumRingPixels = 40
        const val Tolerance = 0.04f
    }
}
