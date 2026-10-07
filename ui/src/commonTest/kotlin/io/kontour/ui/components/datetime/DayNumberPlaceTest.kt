package io.kontour.ui.components.datetime

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.theme.KontourTheme
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A day's number is where it is whatever is drawn under it — asked for as
 * *"can you make it so the dots don't move the number? same goes for all other
 * calendar components"*. A dot or an annotation used to share a centred column
 * with the number, so a day with one had its number nudged up out of line with
 * the rest of its week.
 */
@OptIn(ExperimentalTestApi::class)
class DayNumberPlaceTest {

    // 8, 9 and 10 June 2026 are a Monday, Tuesday and Wednesday: one week row.
    private val dotted = LocalDate(2026, 6, 8)
    private val plain = LocalDate(2026, 6, 9)
    private val noted = LocalDate(2026, 6, 10)

    private fun tops(annotated: Boolean): Triple<Float, Float, Float> {
        var result = Triple(0f, 0f, 0f)
        runComposeUiTest {
            setContent {
                KontourTheme(darkTheme = false, reduceMotion = true) {
                    CalendarMonth(
                        month = dotted,
                        isSelected = { false },
                        onSelectedChange = {},
                        markerFor = { if (it == dotted || it == noted) Color.Red else null },
                        annotationFor = if (annotated) ({ if (it == noted) DayAnnotation("$9") else null }) else null,
                        firstDayOfWeek = DayOfWeek.MONDAY,
                        modifier = Modifier.width(350.dp),
                    )
                }
            }
            waitForIdle()
            fun top(day: LocalDate) = onNodeWithText("${day.day}", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top
            result = Triple(top(dotted), top(plain), top(noted))
        }
        return result
    }

    @Test
    fun aDotDoesNotMoveItsDaysNumber() {
        val (dot, none, _) = tops(annotated = false)
        assertTrue(abs(dot - none) < 0.5f, "the 8th's number is at $dot with a dot, the 9th's at $none without")
    }

    @Test
    fun inAnAnnotatedMonthEveryNumberIsInOnePlace() {
        val (dot, none, note) = tops(annotated = true)
        assertTrue(abs(dot - none) < 0.5f && abs(note - none) < 0.5f, "numbers at $dot (dot), $none (nothing), $note (annotation and dot)")
    }
}
