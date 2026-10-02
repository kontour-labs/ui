package io.kontour.ui.components.display

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test

/** A ring of steps is heard as a row of steps is: the step it is on, out of how many. */
@OptIn(ExperimentalTestApi::class)
class CircularStepProgressTest {

    @Test
    fun aRingOfStepsIsTheStepItIsOnOutOfHowMany() = runComposeUiTest {
        setContent {
            KontourTheme {
                CircularStepProgress(current = 2, total = 4, contentDescription = "Checkout")
            }
        }
        onNodeWithContentDescription("Checkout")
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ProgressBarRangeInfo,
                    ProgressBarRangeInfo(current = 2f, range = 0f..4f, steps = 3),
                )
            )
    }

    /** With nothing passed, it says the step itself, in the same words as `StepProgress`. */
    @Test
    fun itSaysItsOwnStepWhenNotTold() = runComposeUiTest {
        setContent {
            KontourTheme { CircularStepProgress(current = 3, total = 5) }
        }
        onNodeWithContentDescription("Step 3 of 5").assertExists()
    }

    /** An unknown step is not step zero: it says it is in progress, and claims no position. */
    @Test
    fun anUnknownStepIsIndeterminate() = runComposeUiTest {
        setContent {
            KontourTheme { CircularStepProgress(current = null, total = 4, contentDescription = "Restoring") }
        }
        onNodeWithContentDescription("Restoring")
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ProgressBarRangeInfo,
                    ProgressBarRangeInfo.Indeterminate,
                )
            )
    }
}
