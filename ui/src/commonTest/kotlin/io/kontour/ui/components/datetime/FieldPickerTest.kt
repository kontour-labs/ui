package io.kontour.ui.components.datetime

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.adaptive.LocalWindowSizeClass
import io.kontour.ui.adaptive.WindowSizeClass
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Strings
import io.kontour.ui.theme.Theme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.LocalTime

/**
 * A field's picker opens one way for every field: a sheet on a phone, a dialog
 * wider. Asked about as *why date-field is in a dialog, but date-time and time
 * fields are just in a modal sheet*.
 */
@OptIn(ExperimentalTestApi::class)
class FieldPickerTest {

    private fun ComposeUiTest.picker(width: Int, onDismiss: () -> Unit = {}): Strings {
        lateinit var strings: Strings
        setContent {
            KontourTheme(reduceMotion = true) {
                CompositionLocalProvider(LocalWindowSizeClass provides WindowSizeClass.of(width.dp, 800.dp)) {
                    OverlayHost(Modifier.size(width.dp, 800.dp)) {
                        strings = Theme.strings
                        FieldPicker(visible = true, onDismissRequest = onDismiss, title = "Leave at") {
                            TimePicker(value = LocalTime(8, 15), onValueChange = {})
                        }
                    }
                }
            }
        }
        waitForIdle()
        return strings
    }

    @Test
    fun onAPhoneItIsASheetWithACloseButton() = runComposeUiTest {
        val strings = picker(width = 400)
        onNode(hasText("Leave at")).assertExists("the title was not shown")
        // Its header's close, and the scrim's own way out, which a sheet names the same.
        val closes = onAllNodes(hasContentDescription(strings.close) and hasClickAction()).fetchSemanticsNodes().size
        assertTrue(closes >= 1, "a phone's picker was not a sheet with its close")
        onNode(hasText(strings.done) and hasClickAction()).assertDoesNotExist()
    }

    @Test
    fun widerItIsADialogWithDone() = runComposeUiTest {
        var dismissed = 0
        val strings = picker(width = 900, onDismiss = { dismissed++ })
        onNode(hasText("Leave at")).assertExists("the title was not shown")
        onNode(hasContentDescription(strings.close) and hasClickAction()).assertDoesNotExist()
        onNode(hasText(strings.done) and hasClickAction()).performClick()
        waitForIdle()
        assertEquals(1, dismissed, "Done did not put the picker away")
    }
}
