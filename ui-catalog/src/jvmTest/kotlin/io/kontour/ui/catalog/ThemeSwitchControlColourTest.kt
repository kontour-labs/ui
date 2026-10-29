package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.action.Button
import io.kontour.ui.components.selection.Switch
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Theme
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A theme switch moves a control's colours with the theme, and a control's own
 * change still fades.
 *
 * Asked for as: *"with a theme switch, there should be no animation at all."*
 * A `Button` or a `Switch` used to run its own 150ms colour tween towards the
 * theme's new colour, so with a theme that cuts — `animateThemeChanges` off, the
 * default — every button and switch on screen faded for nine frames after the
 * page around it had changed, recomposing on each. See `animateComponentColour`.
 *
 * And the half that must not move: *"i do want it to remain as-is without
 * reduced motion on though, as i need it to be consistent with everything else
 * that fades"*. A button disabled, or a switch turned off, fades as it did.
 */
class ThemeSwitchControlColourTest {

    private var dark by mutableStateOf(false)
    private var enabled by mutableStateOf(true)
    private val button = arrayOfNulls<Rect>(1)

    private fun scene(): Scene = Scene(width = 400, height = 300) {
        KontourTheme(darkTheme = dark) {
            Column(
                Modifier.fillMaxSize().background(Theme.colours.background).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                // No label: the probe reads the container, not a glyph.
                Button(onClick = {}, enabled = enabled, modifier = Modifier.size(120.dp, 48.dp).reportBounds { button[0] = it }) {}
                Switch(checked = true, onCheckedChange = {}, enabled = enabled)
            }
        }
    }

    /** On the frame the theme cuts, the button is in the new theme's colour, and nothing is left moving. */
    @Test
    fun aThemeThatCutsTakesItsControlsWithIt() {
        dark = false
        enabled = true
        scene().use { scene ->
            scene.frames(10)
            val light = scene.frame().buttonColour()
            dark = true
            val first = scene.frame().buttonColour()
            val settled = scene.frames(20).buttonColour()
            assertTrue(light != settled, "the button's colour did not change with the theme: ${hex(light)}")
            assertTrue(
                first == settled,
                "on the frame the theme cut, the button was ${hex(first)}, on its way to ${hex(settled)}: it " +
                    "faded on its own after a theme that does not fade",
            )
            dark = false
            scene.frame()
            scene.frame()
            assertTrue(!scene.stillAnimating(), "two frames after a theme that cuts, something is still animating")
        }
    }

    /** A control's own change, the theme unchanged, fades as it always did. */
    @Test
    fun aControlsOwnChangeStillFades() {
        dark = false
        enabled = true
        scene().use { scene ->
            scene.frames(10)
            val on = scene.frame().buttonColour()
            enabled = false
            val first = scene.frame().buttonColour()
            val settled = scene.frames(30).buttonColour()
            assertTrue(on != settled, "a disabled button kept its enabled colour")
            assertTrue(
                first != settled && distance(first, on) < distance(settled, on),
                "a button disabled jumped straight to ${hex(settled)} (first frame ${hex(first)}, from ${hex(on)}) " +
                    "rather than fading there as before",
            )
        }
    }

    private fun BufferedImage.buttonColour(): Int {
        val at = button[0] ?: error("the button never reported its bounds")
        return getRGB(at.center.x.toInt(), at.center.y.toInt()) and 0xFFFFFF
    }

    private fun distance(a: Int, b: Int): Int =
        listOf(16, 8, 0).sumOf { abs((a shr it and 0xFF) - (b shr it and 0xFF)) }

    private fun hex(colour: Int) = "#%06X".format(colour)
}
