package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import io.kontour.ui.components.datetime.CalendarNavigationState
import io.kontour.ui.components.datetime.DateRangePicker
import io.kontour.ui.components.datetime.DatePicker
import io.kontour.ui.components.datetime.rememberCalendarNavigationState
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A sideways scroll pages the month — a trackpad's swipe, a tilt wheel, shift and
 * the wheel — once per swipe, and a vertical scroll is left to the page.
 *
 * Reported as wanted on the desktop, in every calendar that pages.
 */
class CalendarScrollPagingTest {

    @Test
    fun aNotchSidewaysPagesAMonth() {
        picker { scene, at, navigation ->
            scene.scroll(at, Offset(1f, 0f))
            scene.frames(20)
            assertEquals(LocalDate(2026, 9, 1), navigation().visibleMonth, "scrolling right did not page forward")
            scene.scroll(at, Offset(-1f, 0f))
            scene.frames(20)
            assertEquals(LocalDate(2026, 8, 1), navigation().visibleMonth, "scrolling left did not page back")
        }
    }

    /** A trackpad swipe is many small deltas, and its momentum more: one page for all of it. */
    @Test
    fun aSwipeIsOnePage() {
        picker { scene, at, navigation ->
            repeat(12) {
                scene.scroll(at, Offset(0.4f, 0.05f))
                scene.frame()
            }
            scene.frames(30)
            assertEquals(LocalDate(2026, 9, 1), navigation().visibleMonth, "one swipe paged more or less than once")
        }
    }

    @Test
    fun aVerticalScrollIsThePages() {
        picker { scene, at, navigation ->
            repeat(4) {
                scene.scroll(at, Offset(0.2f, 1f))
                scene.frame()
            }
            scene.frames(20)
            assertEquals(LocalDate(2026, 8, 1), navigation().visibleMonth, "a vertical scroll paged the month")
        }
    }

    /** Right to left, the months run the other way, and so does the scroll. */
    @Test
    fun rightToLeftTheScrollRunsTheOtherWay() {
        picker(direction = LayoutDirection.Rtl) { scene, at, navigation ->
            scene.scroll(at, Offset(1f, 0f))
            scene.frames(20)
            assertEquals(LocalDate(2026, 7, 1), navigation().visibleMonth, "right to left, scrolling right should go back")
        }
    }

    /** The range picker pages the same way. */
    @Test
    fun aDateRangePickerPagesToo() {
        lateinit var navigation: CalendarNavigationState
        var bounds = Rect.Zero
        Scene(width = 700, height = 800) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                navigation = rememberCalendarNavigationState(LocalDate(2026, 8, 1))
                DateRangePicker(
                    start = null,
                    end = null,
                    onRangeChange = { _, _ -> },
                    navigation = navigation,
                    modifier = Modifier.reportBounds { bounds = it },
                )
            }
        }.use { scene ->
            scene.frames(6)
            scene.scroll(bounds.center, Offset(1f, 0f))
            scene.frames(20)
        }
        assertEquals(LocalDate(2026, 9, 1), navigation.visibleMonth)
    }

    private fun picker(
        direction: LayoutDirection = LayoutDirection.Ltr,
        body: (Scene, Offset, () -> CalendarNavigationState) -> Unit,
    ) {
        lateinit var navigation: CalendarNavigationState
        var bounds = Rect.Zero
        Scene(width = 700, height = 800) {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    navigation = rememberCalendarNavigationState(LocalDate(2026, 8, 1))
                    DatePicker(
                        value = null,
                        onValueChange = {},
                        navigation = navigation,
                        modifier = Modifier.reportBounds { bounds = it },
                    )
                }
            }
        }.use { scene ->
            scene.frames(6)
            body(scene, bounds.center, { navigation })
        }
    }
}
