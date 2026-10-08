package io.kontour.ui.components.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.input.InputModality
import io.kontour.ui.input.LocalInputModality
import io.kontour.ui.locale.LocaleFormats
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Strings
import io.kontour.ui.theme.Theme
import io.kontour.ui.theme.Tone
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

/**
 * A grip across an event's edge is whole on every frame, the event faded or
 * not: reported as *"if you drag the bottom time adjustment bar, then when you
 * let go, the bars are briefly clipped before they become fully visible
 * again"*. The event's fade drew into a layer its own size, and cut off the
 * half of each grip past its edge until it had faded back up.
 *
 * Found by the grip's red, in the strip just under the event's bottom edge.
 */
@OptIn(ExperimentalTestApi::class)
class EventCalendarGripTest {

    private val review = CalendarEvent(1, "Design review", LocalDateTime(2026, 10, 14, 10, 0), LocalDateTime(2026, 10, 14, 11, 0), tone = Tone.Danger)

    @Test
    fun theGripsAreWholeAsTheEventFadesBackUp() = runComposeUiTest {
        lateinit var formats: LocaleFormats
        lateinit var strings: Strings
        var density = 1f
        setContent {
            KontourTheme(darkTheme = false, reduceMotion = false) {
                CompositionLocalProvider(LocalInputModality provides InputModality.Touch) {
                    OverlayHost(Modifier.size(700.dp, 700.dp).background(Color.White)) {
                        formats = Theme.formats
                        strings = Theme.strings
                        density = androidx.compose.ui.platform.LocalDensity.current.density
                        EventCalendar(
                            state = rememberEventCalendarState(LocalDate(2026, 10, 14), CalendarView.Week, LocalTime(8, 0), DayOfWeek.MONDAY),
                            events = listOf(review),
                            now = null,
                            onEventMove = { _, _, _ -> },
                            onEventResize = { _, _, _ -> },
                            modifier = Modifier.size(700.dp, 700.dp),
                        )
                    }
                }
            }
        }
        waitForIdle()
        val node = onNode(hasContentDescription(eventDescription(review, formats, strings)))
        val bounds = node.fetchSemanticsNode().boundsInRoot
        onRoot().performTouchInput {
            down(bounds.center)
            advanceEventTime(800)
            up()
        }
        waitForIdle()
        onNode(hasText(strings.adjustTime) and hasClickAction()).performClick()
        waitForIdle()
        // The end taken from its grip and carried down half an hour.
        onRoot().performTouchInput {
            down(Offset(bounds.center.x, bounds.bottom))
            repeat(8) { moveBy(Offset(0f, 32f * density / 8f)) }
        }
        waitForIdle()
        mainClock.autoAdvance = false
        onRoot().performTouchInput { up() }
        // Just under the edge, where the outer half of the bottom grip is.
        val y = (bounds.bottom + 1f * density).toInt()
        val xs = (bounds.center.x - 8f * density).toInt()..(bounds.center.x + 8f * density).toInt()
        val missing = (0 until 12).mapNotNull { frame ->
            mainClock.advanceTimeByFrame()
            val image = onRoot().captureToImage().toPixelMap()
            val red = xs.count { x -> image[x, y].let { it.red - it.green > 0.15f } }
            frame.takeIf { red < xs.count() / 2 }
        }
        assertTrue(missing.isEmpty(), "the grip past the event's bottom edge was cut off on frames $missing after letting go")
    }
    /**
     * Let go, the event held is whole at once, over the events it was widened
     * across: reported as *"when you tap and hold an event… then when you let
     * go, the other events briefly flash through before disappearing again"*.
     * The copy over it went at once and it faded back up from 40%, so what was
     * under it showed through.
     */
    @Test
    fun letGoTheEventHeldHidesWhatItCoversAtOnce() = runComposeUiTest {
        val standup = CalendarEvent(5, "Standup", LocalDateTime(2026, 10, 14, 10, 0), LocalDateTime(2026, 10, 14, 10, 45), tone = Tone.Success)
        lateinit var formats: LocaleFormats
        lateinit var strings: Strings
        setContent {
            KontourTheme(darkTheme = false, reduceMotion = false) {
                CompositionLocalProvider(LocalInputModality provides InputModality.Touch) {
                    OverlayHost(Modifier.size(700.dp, 700.dp).background(Color.White)) {
                        formats = Theme.formats
                        strings = Theme.strings
                        EventCalendar(
                            state = rememberEventCalendarState(LocalDate(2026, 10, 14), CalendarView.Week, LocalTime(8, 0), DayOfWeek.MONDAY),
                            events = listOf(review, standup),
                            now = null,
                            onEventMove = { _, _, _ -> },
                            onEventDelete = {},
                            modifier = Modifier.size(700.dp, 700.dp),
                        )
                    }
                }
            }
        }
        waitForIdle()
        val held = onNode(hasContentDescription(eventDescription(review, formats, strings))).fetchSemanticsNode().boundsInRoot
        val under = onNode(hasContentDescription(eventDescription(standup, formats, strings))).fetchSemanticsNode().boundsInRoot
        // Held and not let go: the clock run on past the long press.
        onRoot().performTouchInput { down(held.center) }
        mainClock.advanceTimeBy(800)
        waitForIdle()
        mainClock.autoAdvance = false
        onRoot().performTouchInput { up() }
        val at = (under.center.x.toInt()) to (under.top + 4f).toInt()
        fun colour() = onRoot().captureToImage().toPixelMap()[at.first, at.second]
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeByFrame()
        val first = colour()
        repeat(30) { mainClock.advanceTimeByFrame() }
        val settled = colour()
        val apart = maxOf(kotlin.math.abs(first.red - settled.red), kotlin.math.abs(first.green - settled.green), kotlin.math.abs(first.blue - settled.blue))
        assertTrue(apart < 0.03f, "just after letting go the event held showed what was under it: $first, then $settled")
    }
}
