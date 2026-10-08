package io.kontour.ui.components.datetime

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kontour.ui.locale.DateStyle
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Theme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `DateField` and `DateTimeField`: the field a form shows for a `DatePicker` and
 * a `DateTimePicker`, as `TimeField` is for a `TimePicker`.
 *
 * Reported from an app: *"There's no `DateField`. `TimePicker` has `TimeField`
 * … `DatePicker` has no matching field. For the post date I built a read-only
 * `TextField` with a calendar `IconButton` that opens the `DatePicker` in a
 * `Dialog`."*
 */
@OptIn(ExperimentalTestApi::class)
class DateFieldTest {

    @Test
    fun aDateFieldShowsItsDateAndIsNamedByItsLabel() = runComposeUiTest {
        var opened = 0
        var written = ""
        setContent {
            KontourTheme(reduceMotion = true) {
                written = Theme.formats.date(LocalDate(2026, 3, 14), DateStyle.Medium)
                DateField(value = LocalDate(2026, 3, 14), onClick = { opened++ }, label = "Post date")
            }
        }
        waitForIdle()
        onNodeWithText(written).fetchSemanticsNode()
        val field = onNodeWithContentDescription("Post date")
        assertEquals(written, field.fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription))
        field.performClick()
        waitForIdle()
        assertEquals(1, opened, "the field opens its picker")
    }

    @Test
    fun anEmptyDateFieldShowsItsPlaceholder() = runComposeUiTest {
        setContent {
            KontourTheme(reduceMotion = true) {
                DateField(value = null, onClick = {}, label = "Post date", placeholder = "Not scheduled")
            }
        }
        waitForIdle()
        onNodeWithText("Not scheduled").fetchSemanticsNode()
        assertEquals(
            "Not scheduled",
            onNodeWithContentDescription("Post date").fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription),
        )
    }

    @Test
    fun aDateTimeFieldWritesBoth() = runComposeUiTest {
        var written = ""
        val value = LocalDateTime(LocalDate(2026, 3, 14), LocalTime(9, 5))
        setContent {
            KontourTheme(reduceMotion = true) {
                written = Theme.formats.dateTime(value.date, value.time)
                DateTimeField(value = value, onClick = {}, label = "Publish at")
            }
        }
        waitForIdle()
        onNodeWithText(written).fetchSemanticsNode()
    }

    /**
     * A line under the field and an error in its place, as a text field has.
     * Reported from an app: *"`DateField` still has no `supporting` or
     * `errorMessage`."*
     */
    @Test
    fun aDateFieldHasASupportingLineAndAnErrorInItsPlace() = runComposeUiTest {
        var error by mutableStateOf<String?>(null)
        setContent {
            KontourTheme(reduceMotion = true) {
                DateField(
                    value = null,
                    onClick = {},
                    label = "Post date",
                    supporting = "When it goes live",
                    errorMessage = error,
                )
            }
        }
        waitForIdle()
        onNodeWithText("When it goes live").fetchSemanticsNode()
        val field = onNodeWithContentDescription("Post date")
        assertEquals(null, field.fetchSemanticsNode().config.getOrNull(SemanticsProperties.Error))

        error = "Choose a date after today"
        waitForIdle()
        assertEquals("Choose a date after today", field.fetchSemanticsNode().config.getOrNull(SemanticsProperties.Error))
        onAllNodesWithText("When it goes live").assertCountEquals(0)
    }
}
