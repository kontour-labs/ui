package io.kontour.ui.components.calendar

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Theme
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The header offers its views in a menu whenever their words would not fit side
 * by side, at whatever size the type is.
 *
 * It chose from a word's width at the default type, so at 200% a 900dp
 * calendar kept six segments in 480dp, and the control stacked them into a
 * column: what event-calendar.md says never happens. Found in the November
 * 2026 audit.
 */
@OptIn(ExperimentalTestApi::class)
class CalendarSwitcherLargeTextTest {

    private fun ComposeUiTest.menuButtons(fontScale: Float, width: Int): Int {
        var chooseView = ""
        setContent {
            KontourTheme(reduceMotion = true) {
                chooseView = Theme.strings.chooseView
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                    OverlayHost(Modifier.size(width.dp, 800.dp)) {
                        val state = rememberEventCalendarState(LocalDate(2026, 10, 14), CalendarView.Week, LocalTime(8, 0), DayOfWeek.MONDAY)
                        EventCalendar(state = state, events = emptyList(), now = null, modifier = Modifier.size(width.dp, 800.dp))
                    }
                }
            }
        }
        waitForIdle()
        val opensTheMenu = SemanticsMatcher("opens the view menu") {
            it.config.getOrNull(SemanticsActions.OnClick)?.label == chooseView
        }
        return onAllNodes(opensTheMenu).fetchSemanticsNodes().size
    }

    @Test
    fun atTwiceTheTypeTheViewsAreAMenu() = runComposeUiTest {
        assertEquals(1, menuButtons(fontScale = 2f, width = 900), "at 200% the six views were offered as segments")
    }

    @Test
    fun underTheWholeWidthAtTwiceTheTypeTheViewsAreAMenu() = runComposeUiTest {
        assertEquals(1, menuButtons(fontScale = 2f, width = 600), "at 200% the six views were offered as segments")
    }

    /** At the default size, as before: segments beside the title. */
    @Test
    fun atTheDefaultTypeTheViewsAreSegments() = runComposeUiTest {
        assertEquals(0, menuButtons(fontScale = 1f, width = 900))
    }
}
