package io.kontour.ui.components.display

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.Text
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test

/** A meter is heard as a reading over its range, and takes the room a bar takes. */
@OptIn(ExperimentalTestApi::class)
class MeterTest {

    @Test
    fun aMeterIsOneReadingOverItsRange() = runComposeUiTest {
        setContent {
            KontourTheme {
                Meter(
                    value = 62f,
                    valueRange = 0f..100f,
                    contentDescription = "Battery",
                    stateDescription = { "${it.toInt()} percent charged" },
                ) {
                    Text("Battery")
                }
            }
        }
        onNodeWithContentDescription("Battery")
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ProgressBarRangeInfo,
                    ProgressBarRangeInfo(62f, 0f..100f),
                )
            )
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "62 percent charged"))
    }

    /** A reading off the end of the scale is reported at the end it is off. */
    @Test
    fun aReadingPastTheEndIsReportedAtTheEnd() = runComposeUiTest {
        setContent {
            KontourTheme { Meter(value = 140f, valueRange = 0f..100f, contentDescription = "Load") }
        }
        onNodeWithContentDescription("Load").assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.ProgressBarRangeInfo,
                ProgressBarRangeInfo(100f, 0f..100f),
            )
        )
    }

    /** Across, it fills the width it is given; up, it is its default length. */
    @Test
    fun aHorizontalMeterFillsItsWidthAndAVerticalOneHasALength() = runComposeUiTest {
        setContent {
            KontourTheme {
                Box(Modifier.width(300.dp)) {
                    Meter(value = 0.5f, contentDescription = "Across")
                }
                Meter(value = 0.5f, orientation = MeterOrientation.Vertical, contentDescription = "Up")
            }
        }
        onNodeWithContentDescription("Across").assertWidthIsEqualTo(300.dp)
        onNodeWithContentDescription("Up").assertHeightIsEqualTo(MeterDefaults.Length)
    }
}
