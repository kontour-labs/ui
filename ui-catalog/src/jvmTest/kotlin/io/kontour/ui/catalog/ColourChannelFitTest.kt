package io.kontour.ui.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.selection.ColourFormat
import io.kontour.ui.components.selection.ColourPicker
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every channel of the narrowest picker holds its widest value whole.
 *
 * The channel fields used to be padded 4dp a side, tighter than the hex field's
 * 12, on a measurement that "255" lost its last digit at the standard padding.
 * They were asked to match the hex field, so this is the measurement, kept: four
 * fields in a 320dp picker — red, green, blue and alpha — each showing a three
 * digit value, and each value's laid-out width inside the field's text area.
 */
@OptIn(ExperimentalTestApi::class)
class ColourChannelFitTest {

    @Test
    fun threeDigitsFitEveryChannelOfANarrowPicker() = runComposeUiTest {
        setContent {
            KontourTheme(darkTheme = false, reduceMotion = true) {
                Box {
                    ColourPicker(
                        // 255, 128, 0 and 100: the widest red and alpha there are.
                        colour = Color(1f, 0.5f, 0f, 1f),
                        onColourChange = {},
                        modifier = Modifier.padding(16.dp).width(320.dp),
                        showAlphaSlider = true,
                        format = ColourFormat.Rgb,
                        swatches = emptyList(),
                    )
                }
            }
        }
        waitForIdle()
        val fields = onAllNodes(hasSetTextAction()).fetchSemanticsNodes()
        assertEquals(4, fields.size, "red, green, blue and alpha")
        for (field in fields) {
            val layouts = mutableListOf<TextLayoutResult>()
            field.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
            val layout = layouts.single()
            val text = layout.layoutInput.text.text
            val room = field.boundsInRoot.width
            // The line's own extent: a layout is as wide as the field allows it,
            // whatever it holds, so its size says nothing about the digits.
            val width = layout.getLineRight(0) - layout.getLineLeft(0)
            assertTrue(width > 0f, "'$text' laid out as nothing")
            assertTrue(
                width <= room,
                "'$text' is ${width}px wide in a ${room}px text area — its last digit is cut off",
            )
        }
    }
}
