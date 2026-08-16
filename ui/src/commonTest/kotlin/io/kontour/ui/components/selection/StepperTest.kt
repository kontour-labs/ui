package io.kontour.ui.components.selection

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A stepper's whole job is its two ends.
 *
 * The middle — press `+`, get one more — is the part that cannot really break.
 * What breaks is the boundary: a `+` at the maximum that still looks pressable,
 * or one that refuses silently while announcing itself as available.
 */
@OptIn(ExperimentalTestApi::class)
class StepperTest {

    /**
     * At the top of the range the increment button is *disabled*, not merely
     * inert.
     *
     * Clamping the value without disabling the button gives a control that looks
     * live and does nothing — the exact defect `EverythingRespondsTest` exists to
     * catch, and one a screen-reader user cannot see greyed out. Reverting
     * `canIncrement` to `enabled` fails this on the `assertIsNotEnabled`.
     */
    @Test
    fun theIncrementButtonDisablesAtTheTop() = runComposeUiTest {
        var value = 9
        setContent {
            KontourTheme {
                Stepper(
                    value = value,
                    onValueChange = { value = it },
                    contentDescription = "Adults",
                    range = 1..9,
                )
            }
        }

        onNodeWithContentDescription("Increase").assertIsNotEnabled()
        onNodeWithContentDescription("Decrease").assertIsEnabled()

        onNodeWithContentDescription("Increase").performClick()
        assertEquals(9, value, "a disabled increment must not change the value")
    }

    @Test
    fun theDecrementButtonDisablesAtTheBottom() = runComposeUiTest {
        var value = 1
        setContent {
            KontourTheme {
                Stepper(
                    value = value,
                    onValueChange = { value = it },
                    contentDescription = "Adults",
                    range = 1..9,
                )
            }
        }

        onNodeWithContentDescription("Decrease").assertIsNotEnabled()
        onNodeWithContentDescription("Increase").assertIsEnabled()
    }

    /**
     * A `step` larger than one still stops at the boundary rather than
     * overshooting it.
     *
     * With `range = 0..10` and `step = 4`, pressing `+` twice reaches 8. The
     * third press would be 12, so the button is disabled — a naive
     * `value < range.last` check leaves it enabled and produces a clamp to 10,
     * which is a value the step sequence never contains.
     */
    @Test
    fun aStepThatWouldOvershootDisablesRatherThanClamping() = runComposeUiTest {
        var value = 8
        setContent {
            KontourTheme {
                Stepper(
                    value = value,
                    onValueChange = { value = it },
                    contentDescription = "Bags",
                    range = 0..10,
                    step = 4,
                )
            }
        }

        onNodeWithContentDescription("Increase").assertIsNotEnabled()
        assertEquals(8, value)
    }

    /**
     * The number is the control's state, not a node of its own.
     *
     * Left merged, a screen reader walks "Adults", then "Decrease", then an
     * unlabelled "2", then "Increase" — and the 2 belongs to nothing. Reverting
     * the `clearAndSetSemantics` puts it back in the tree.
     */
    @Test
    fun theValueIsAnnouncedAsStateRatherThanAsItsOwnNode() = runComposeUiTest {
        setContent {
            KontourTheme {
                Stepper(
                    value = 2,
                    onValueChange = {},
                    contentDescription = "Adults",
                    modifier = Modifier.testTag("stepper"),
                )
            }
        }

        onNodeWithTag("stepper").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "2"),
        )
        onNodeWithTag("stepper").assert(
            hasContentDescription("Adults"),
        )
    }
}
