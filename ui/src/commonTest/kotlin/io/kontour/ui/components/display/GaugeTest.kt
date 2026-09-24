package io.kontour.ui.components.display

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kontour.ui.foundation.Text
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test

/** A gauge is heard as a reading over its range, in the words it is given. */
@OptIn(ExperimentalTestApi::class)
class GaugeTest {

    @Test
    fun aGaugeIsOneReadingOverItsRange() = runComposeUiTest {
        setContent {
            KontourTheme {
                Gauge(
                    value = 8_500f,
                    valueRange = 0f..10_000f,
                    contentDescription = "Engine speed",
                    stateDescription = { "${it.toInt()} revolutions a minute" },
                ) {
                    Text("8.5k")
                }
            }
        }
        onNodeWithContentDescription("Engine speed")
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ProgressBarRangeInfo,
                    ProgressBarRangeInfo(8_500f, 0f..10_000f),
                )
            )
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "8500 revolutions a minute"))
    }
}
