package io.kontour.ui.components.action

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kontour.ui.foundation.SystemIcons
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A button can be given the name a screen reader reads, apart from its label.
 *
 * Reported: *"`Button` has no way to set its accessible name; I used a
 * semantics modifier."* `Checkbox`, `Switch` and the rest took a
 * `contentDescription` in 0.4.2; a button with a label did not, and a terse
 * label — "Save" — could only be expanded for a screen reader by reaching
 * past the component.
 *
 * And the extended FAB, whose description is documented as surviving the
 * label — "Start" on screen, "Start trip to Perth Station" read aloud — set
 * it only while collapsed: open, the label was the whole name.
 */
@OptIn(ExperimentalTestApi::class)
class ButtonNameTest {

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.description(): List<String>? =
        fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)

    @Test
    fun aButtonTakesAName() = runComposeUiTest {
        setContent {
            KontourTheme {
                Button(onClick = {}, contentDescription = "Save this trip to Perth") { +"Save" }
            }
        }
        assertEquals(listOf("Save this trip to Perth"), onNode(hasClickAction()).description())
    }

    @Test
    fun aTextButtonTakesAName() = runComposeUiTest {
        setContent {
            KontourTheme {
                TextButton(onClick = {}, contentDescription = "Forgot your password?") { +"Forgot?" }
            }
        }
        assertEquals(listOf("Forgot your password?"), onNode(hasClickAction()).description())
    }

    @Test
    fun aSplitButtonsMainHalfTakesAName() = runComposeUiTest {
        setContent {
            KontourTheme {
                OverlayHost {
                    SplitButton(
                        onClick = {},
                        expanded = false,
                        onExpandedChange = {},
                        menuContentDescription = "More ways to save",
                        contentDescription = "Save this trip",
                        menu = {},
                    ) { +"Save" }
                }
            }
        }
        val main = onAllNodes(hasClickAction() and hasText("Save")).fetchSemanticsNodes().single()
        assertEquals(listOf("Save this trip"), main.config.getOrNull(SemanticsProperties.ContentDescription))
    }

    @Test
    fun anOpenExtendedFabIsNamedByItsDescription() = runComposeUiTest {
        setContent {
            KontourTheme {
                ExtendedFloatingActionButton(
                    onClick = {},
                    icon = SystemIcons.ChevronForward,
                    contentDescription = "Start trip to Perth Station",
                    expanded = true,
                ) { +"Start" }
            }
        }
        assertEquals(listOf("Start trip to Perth Station"), onNode(hasClickAction()).description())
    }

    /** Without one, the label is the name, as before. */
    @Test
    fun withoutOneTheLabelIsTheName() = runComposeUiTest {
        setContent { KontourTheme { Button(onClick = {}) { +"Save" } } }
        val button = onNode(hasClickAction()).fetchSemanticsNode().config
        assertEquals(null, button.getOrNull(SemanticsProperties.ContentDescription))
        assertEquals(listOf("Save"), button.getOrNull(SemanticsProperties.Text)?.map { it.text })
    }
}
