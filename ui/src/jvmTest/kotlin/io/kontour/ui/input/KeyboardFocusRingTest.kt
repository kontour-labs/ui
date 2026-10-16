package io.kontour.ui.input

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
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
import kotlin.test.assertEquals
import io.kontour.ui.components.selection.RangeSlider
import io.kontour.ui.components.selection.Rating
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
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

    @Test
    fun eachRangeSliderThumbTakesFocusAndShowsIt() {
        ringAppears({ onNodeWithContentDescription("Range start") }) { range() }
        ringAppears({ onNodeWithContentDescription("Range end") }) { range() }
    }

    @Test
    fun aRangeSliderThumbAnswersItsKeysTheWayTheyPoint() {
        for (rtl in listOf(false, true)) runComposeUiTest {
            var value by mutableStateOf(0.2f..0.8f)
            setContent {
                KontourTheme {
                    CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                        RangeSlider(value = value, onValueChange = { value = it }, steps = 9)
                    }
                }
            }
            val start = onNodeWithContentDescription("Range start")
            start.requestFocus()
            start.performKeyInput { pressKey(Key.DirectionRight) }
            waitForIdle()
            // A step is a tenth: Right raises it left to right, and lowers it
            // right to left, where the track runs from the right.
            assertEquals(if (rtl) 0.1f else 0.3f, value.start, 0.001f, "rtl = $rtl")
            assertEquals(0.8f, value.endInclusive, 0.001f, "the other thumb moved")
            // End goes as far as a finger could take it: up to the other thumb.
            start.performKeyInput { pressKey(Key.MoveEnd) }
            waitForIdle()
            assertTrue(value.start <= value.endInclusive, "the start key-pressed past the end: $value")
        }
    }

    /**
     * A rating given an interaction source rings the star that has focus, not
     * all five.
     *
     * Every mark used to take the caller's source, and a ring reads focus from
     * its source, so focusing one star drew the ring round every star in the
     * row. Measured against the same rating without a source, where each mark
     * always had its own: the two have to draw the same one ring.
     */
    @Test
    fun aRatingWithAnInteractionSourceRingsOnlyTheFocusedStar() {
        val alone = ringDrawn({ onNodeWithContentDescription("3 out of 5") }) {
            Rating(value = 2f, onValueChange = {}, contentDescription = "Your rating")
        }
        val shared = ringDrawn({ onNodeWithContentDescription("3 out of 5") }) {
            Rating(
                value = 2f,
                onValueChange = {},
                contentDescription = "Your rating",
                interactionSource = remember { MutableInteractionSource() },
            )
        }
        assertTrue(alone >= MinimumRingPixels, "the focused star drew no ring of its own ($alone pixels)")
        assertTrue(
            shared < alone * 2,
            "given an interaction source, the rating drew $shared pixels of ring against $alone for " +
                "one star — the ring is round every mark that shares the source",
        )
    }

    /**
     * A control without focus does not recompose when the input modality
     * changes, and one with focus still shows and hides its ring as it does.
     *
     * `focusRing` read the modality whatever the focus, so every control that
     * draws a ring — most of them on a screen — recomposed each time the hand
     * went from the mouse to the keyboard and back, to arrive at "no ring"
     * again. Read only once the control has focus, it is a read nothing
     * unfocused depends on.
     */
    @Test
    fun anUnfocusedRingIgnoresTheModalityAndAFocusedOneFollowsIt() = runComposeUiTest {
        var modality by mutableStateOf(InputModality.Mouse)
        val source = MutableInteractionSource()
        var ring = Color.Unspecified
        RingedCompositions.count = 0
        setContent {
            KontourTheme {
                ring = Theme.colours.focusRing
                CompositionLocalProvider(LocalInputModality provides modality) {
                    Box(Modifier.background(Theme.colours.background).padding(12.dp)) { Ringed(source) }
                }
            }
        }
        waitForIdle()
        val settled = RingedCompositions.count

        modality = InputModality.Keyboard
        waitForIdle()
        modality = InputModality.Mouse
        waitForIdle()
        assertEquals(
            settled, RingedCompositions.count,
            "a control without focus recomposed when the input modality changed",
        )

        // Focused with the mouse as the input: no ring, until the keyboard is.
        onNodeWithTag("ringed").requestFocus()
        waitForIdle()
        val mouse = ringPixels(ring)
        modality = InputModality.Keyboard
        waitForIdle()
        val keyboard = ringPixels(ring)
        assertTrue(
            keyboard - mouse >= MinimumRingPixels,
            "a focused control drew $keyboard pixels of ring under the keyboard against $mouse under the mouse",
        )
        modality = InputModality.Mouse
        waitForIdle()
        assertTrue(ringPixels(ring) < keyboard, "the ring stayed up after the mouse took over again")
    }

    @Composable
    private fun range() {
        RangeSlider(value = 0.2f..0.8f, onValueChange = {}, contentDescription = "Fare zone")
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

    /** How many pixels of the ring's colour focusing [find] from the keyboard adds. */
    private fun ringDrawn(
        find: ComposeUiTest.() -> SemanticsNodeInteraction,
        content: @Composable () -> Unit,
    ): Int {
        var drawn = 0
        runComposeUiTest {
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
            drawn = ringPixels(ring) - before
        }
        return drawn
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

/** How many times [Ringed] has composed. An object, so nothing passed to it is a new lambda. */
private object RingedCompositions {
    var count = 0
}

/** A focusable box with a ring, which composes again only when something it reads changes. */
@Composable
private fun Ringed(source: MutableInteractionSource) {
    SideEffect { RingedCompositions.count++ }
    Box(Modifier.size(40.dp).focusRing(source).focusable(interactionSource = source).testTag("ringed"))
}
