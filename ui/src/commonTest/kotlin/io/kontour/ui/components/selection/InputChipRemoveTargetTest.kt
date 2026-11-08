package io.kontour.ui.components.selection

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.input.InputModality
import io.kontour.ui.input.LocalInputModality
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.LocalSizing
import io.kontour.ui.theme.Sizing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * An input chip's remove button has the platform's minimum to be pressed in,
 * across its width, without the chip growing taller than the chips beside it.
 * It was the 24dp it is drawn, under a finger as under a mouse. Found in the
 * November 2026 audit.
 */
@OptIn(ExperimentalTestApi::class)
class InputChipRemoveTargetTest {

    private var removed = 0

    private fun ComposeUiTest.chip(modality: InputModality) {
        setContent {
            KontourTheme(reduceMotion = true) {
                CompositionLocalProvider(
                    LocalInputModality provides modality,
                    LocalSizing provides Sizing(minTouchTarget = 48.dp),
                ) {
                    InputChip(onRemove = { removed++ }, removeLabel = "Remove Perth", modifier = Modifier.testTag("chip")) {
                        +"Perth"
                    }
                }
            }
        }
        waitForIdle()
    }

    private fun ComposeUiTest.remove() = onNode(hasContentDescription("Remove Perth"))

    @Test
    fun underAFingerTheTargetIsTheMinimumWideAndNoTallerThanTheChip() = runComposeUiTest {
        chip(InputModality.Touch)
        val target = remove().getBoundsInRoot()
        assertEquals(48.dp, target.right - target.left, "the remove button's target is not 48dp wide")
        assertTrue(target.bottom - target.top <= 34.dp, "the remove button's target is ${target.bottom - target.top} tall, taller than a chip")

        // 8dp in from the target's start: outside the 24dp button, inside its target.
        remove().performTouchInput { click(Offset(8.dp.toPx(), centerY)) }
        waitForIdle()
        assertEquals(1, removed, "a press beside the drawn button did not remove the chip")
    }

    /** Under a mouse the chip is laid out as it was: the 24dp button and 4dp after it. */
    @Test
    fun underAMouseTheChipIsAsItWas() = runComposeUiTest {
        chip(InputModality.Mouse)
        val target = remove().getBoundsInRoot()
        assertEquals(28.dp, target.right - target.left)
        assertEquals(24.dp, target.bottom - target.top)
    }
}
