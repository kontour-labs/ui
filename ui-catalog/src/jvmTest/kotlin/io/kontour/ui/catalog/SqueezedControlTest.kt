package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.selection.Checkbox
import io.kontour.ui.components.selection.RadioButton
import io.kontour.ui.components.selection.Switch
import kotlin.test.Test
import kotlin.test.fail

/**
 * Selection controls given less room than they asked for.
 *
 * `Modifier.size` states a *preference*. A `Row` that has run out of width hands
 * out less than that, or nothing at all, and a control has no say in it — so
 * "narrower than I wanted" is a state every one of these has to survive, not an
 * edge case a caller can be told to avoid.
 *
 * `Switch` did not survive it. Its thumb was placed with
 * `coerceIn(0f, size.width - stretchedWidth)`, and `coerceIn` **throws** on an
 * inverted range: measured at zero width it raised
 *
 *     IllegalArgumentException: Cannot coerce value to an empty range:
 *     maximum -66.0 is less than minimum 0.0.
 *
 * from inside the draw phase, where there is nothing to catch it. That is the
 * crash reported from a phone, and this is the direct test for it.
 *
 * ### Direct, because the page-level test can no longer see it
 *
 * `PhoneWidthTest` is what found this, but the showcase row that squeezed the
 * switch has since been made to wrap — so that test would now pass with the
 * library fix reverted, and would defend nothing. A component's promise belongs
 * in a test about the component.
 */
class SqueezedControlTest {

    @Test
    fun aSwitchSurvivesBeingGivenNothing() = drawsAtEveryWidth("Switch") { modifier ->
        Switch(checked = true, onCheckedChange = null, modifier = modifier)
    }

    @Test
    fun aCheckboxSurvivesBeingGivenNothing() = drawsAtEveryWidth("Checkbox") { modifier ->
        Checkbox(checked = true, onCheckedChange = null, modifier = modifier)
    }

    @Test
    fun aRadioButtonSurvivesBeingGivenNothing() = drawsAtEveryWidth("RadioButton") { modifier ->
        RadioButton(selected = true, onClick = null, modifier = modifier)
    }

    @Test
    fun aRowThatHasRunOutOfWidthDoesNotTakeTheFrameWithIt() {
        // The shape that actually happened: eleven controls in a row built for
        // four. The last ones are measured at nothing, and drawing nothing is
        // the only correct answer available to them.
        try {
            Scene(width = 240, height = 200) {
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        repeat(4) { Checkbox(checked = true, onCheckedChange = null) }
                        repeat(3) { RadioButton(selected = true, onClick = null) }
                        repeat(3) { Switch(checked = it % 2 == 0, onCheckedChange = null) }
                    }
                }
            }.use { scene -> scene.frames(6) }
        } catch (error: Throwable) {
            fail("a row of eleven controls in 120dp threw ${error::class.simpleName}: ${error.message}")
        }
    }

    /**
     * Draws one control at every width from nothing up to more than it wants.
     *
     * Zero is the case that threw, but it is not the only suspicious one: the
     * thumb stretches to 1.25× while pressed, so there is a band between "fits
     * at rest" and "fits while held" that a single width would step straight
     * over.
     */
    private fun drawsAtEveryWidth(name: String, control: @Composable (Modifier) -> Unit) {
        for (width in Widths) {
            try {
                Scene(width = 200, height = 200) {
                    Box(Modifier.fillMaxSize().background(Color.White)) {
                        // A `Box` of a fixed width passes it down as a hard
                        // maximum, which is exactly what an exhausted `Row` does.
                        Box(Modifier.width(width.dp)) { control(Modifier) }
                    }
                }.use { scene -> scene.frames(4) }
            } catch (error: Throwable) {
                fail(
                    "$name threw at ${width}dp wide — ${error::class.simpleName}: " +
                        "${error.message}. A control given less room than it asked " +
                        "for has to look cramped, not take the frame down.",
                )
            }
        }
    }

    private companion object {
        /** Nothing, slivers, under the thumb, under the stretched thumb, and comfortable. */
        val Widths = listOf(0, 1, 4, 12, 20, 22, 24, 28, 48, 64)
    }
}
