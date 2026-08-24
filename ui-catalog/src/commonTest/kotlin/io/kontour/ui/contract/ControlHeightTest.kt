package io.kontour.ui.contract

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Plus
import io.kontour.ui.components.action.Button
import io.kontour.ui.components.action.ButtonSize
import io.kontour.ui.components.action.IconButton
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A row of mixed controls lines up.
 *
 * `Sizing` promises exactly that — "control heights, shared by buttons, inputs
 * and selects so a row of mixed controls lines up without per-call-site
 * padding" — and one control was not keeping it. `IconButton` sized itself as
 * `iconSize + iconOnlyPadding * 2`, which is a second way of saying how tall a
 * control is, and it disagreed with the first at three of the five sizes: 24
 * against 28, 28 against 36, 40 against 44. Large and XLarge happened to agree,
 * which is most of why it lasted.
 *
 * The visible cost was every joined control that mixes the two. A `ButtonGroup`
 * with an icon action beside a labelled one was ragged, and so was the trailing
 * half of a `SplitButton` — which is where it was finally noticed, by someone
 * looking at a picture.
 *
 * ### Measured, not asserted against a number
 *
 * There is no list of expected heights here. A test that says "Medium is 44dp"
 * pins the token; this one says the two agree, which is the property that
 * matters and the one that survives a change to the scale.
 */
@OptIn(ExperimentalTestApi::class)
class ControlHeightTest {

    @Test
    fun anIconButtonIsAsTallAsAButtonOfTheSameSize() = runComposeUiTest {
        ButtonSize.entries.forEach { size ->
            setContent {
                KontourTheme(reduceMotion = true) {
                    Row {
                        Box(Modifier.testTag(Labelled)) {
                            Button(onClick = {}, size = size) { +"Save" }
                        }
                        Box(Modifier.testTag(IconOnly)) {
                            IconButton(
                                icon = Tabler.Outline.Plus,
                                contentDescription = "Add",
                                onClick = {},
                                size = size,
                            )
                        }
                    }
                }
            }

            val labelled = onNodeWithTag(Labelled).fetchSemanticsNode().size.height
            val iconOnly = onNodeWithTag(IconOnly).fetchSemanticsNode().size.height

            assertEquals(
                labelled,
                iconOnly,
                "at $size a Button draws ${labelled}px tall and an IconButton " +
                    "${iconOnly}px — put them in one `ButtonGroup` or either half " +
                    "of a `SplitButton` and the join stops reading as a join",
            )
        }
    }

    private companion object {
        const val Labelled = "labelled"
        const val IconOnly = "icon-only"
    }
}
