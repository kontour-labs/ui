package io.kontour.ui.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Density
import io.kontour.ui.components.selection.Stepper
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A stepper is the same width whatever it is showing.
 *
 * It was not. `valueWidth` is a *minimum*, wide enough for two digits, which is
 * enough while `format` returns digits — and stops being enough the moment it
 * returns words. `"1 bag"` is narrower than `"2 bags"`, so pressing `+` widened
 * the middle and shoved the `+` button to the right. The control twitched every
 * time it was used, which is the sort of thing nobody files and everybody
 * notices.
 *
 * Measured as a whole-control width rather than by inspecting the value cell:
 * what matters is that the buttons do not move, and that is the same statement.
 */
class StepperWidthTest {

    private fun widthOf(value: Int, format: (Int) -> String, range: IntRange): Float {
        var width = Float.NaN
        val scene = ImageComposeScene(width = 600, height = 200, density = Density(2f)) {
            KontourTheme(darkTheme = false, reduceMotion = true) {
                Box(Modifier.fillMaxSize()) {
                    Stepper(
                        value = value,
                        onValueChange = {},
                        contentDescription = "Bags",
                        range = range,
                        format = format,
                        modifier = Modifier.onGloballyPositioned {
                            width = it.size.width.toFloat()
                        },
                    )
                }
            }
        }
        try {
            repeat(4) { scene.render(16_000_000L * it) }
        } finally {
            scene.close()
        }
        return width
    }

    @Test
    fun aPluralisingFormatDoesNotMoveTheButtons() {
        val format: (Int) -> String = { if (it == 1) "1 bag" else "$it bags" }
        val range = 1..9

        val atOne = widthOf(1, format, range)
        val atTwo = widthOf(2, format, range)

        assertEquals(
            atOne,
            atTwo,
            "the stepper is ${atOne}px showing \"1 bag\" and ${atTwo}px showing " +
                "\"2 bags\", so the buttons move as the value changes",
        )
    }

    @Test
    fun aRangeWiderThanTheFloorStillReservesEnough() {
        // Past `StepperDefaults.ValueWidth`, which is two digits' worth. Below
        // that the minimum hides the problem, which is why the pluralising case
        // above is the one that found it.
        val format: (Int) -> String = { "$it" }
        val range = 1..99999

        val atOne = widthOf(1, format, range)
        val atMax = widthOf(99999, format, range)

        assertEquals(
            atOne,
            atMax,
            "the stepper is ${atOne}px at 1 and ${atMax}px at 99999, so a " +
                "five-digit range does not reserve room for five digits",
        )
    }
}
