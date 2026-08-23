package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Star
import io.kontour.ui.components.action.Button
import io.kontour.ui.components.action.ButtonSize
import io.kontour.ui.components.action.ButtonVariant
import io.kontour.ui.components.action.FabSize
import io.kontour.ui.components.action.FloatingActionButton
import io.kontour.ui.components.action.IconButton
import io.kontour.ui.components.action.IconToggleButton
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Every button answers a press by moving.
 *
 * `tokens.md` has said so for a long time — *"`KontourIndication` already
 * applies it, so every pressable component in the system gets the bounce without
 * asking for it"* — and the mechanism was never the problem. `KontourIndication`
 * does exactly what it claims: snappy on the way down, `springBouncy` on the way
 * back. The **policy** was the problem, and it was two things.
 *
 * A default `IconButton` is `Ghost`, and `ButtonDefaults.pressScale` mapped Ghost
 * to `1f`. Every `IconToggleButton` hard-codes Ghost. So the two most-tapped
 * controls in the library could not respond to a press at all, and a `Toolbar`'s
 * own documented example put a `ButtonGroup` that shrank next to a bare
 * `IconButton` that did not. The stated reason — a shrinking label reads as text
 * jumping — is right about text and was being applied to glyphs.
 *
 * And what did shrink shrank 3%, which is what one constant comes out as when it
 * has to serve a 28dp icon button and a full-width one.
 *
 * ### Measured as the control's own ink getting smaller
 *
 * The scale is a `graphicsLayer` inside the indication node, which no semantics
 * assertion can see and no golden can hold still for — the same reason
 * `RowPressReachesControlTest` gave for measuring the interaction source instead.
 * A held press is stable, though: press, let the spring settle, and photograph
 * it. The ink's bounding box then answers the question directly.
 */
class PressScaleTest {

    @Test
    fun everyButtonShrinksWhenItIsPressed() {
        val failures = Buttons.mapNotNull { button ->
            val (resting, pressed) = inkWidths(button.content)
            if (pressed < resting) null else "${button.name} $resting→$pressed"
        }

        assertTrue(
            failures.isEmpty(),
            "these did not move at all under a held press: " +
                failures.joinToString("; ") +
                " (resting→pressed ink width, px). A control that cannot " +
                "acknowledge a finger is a control the user cannot tell they hit.",
        )
    }

    /**
     * A small control moves more than a large one.
     *
     * This is the half of the change that is a judgement rather than a defect,
     * so it is pinned as an ordering rather than as numbers: whatever the
     * constants end up being, an XSmall icon button has to move further than an
     * XLarge button, because 3% is invisible on the first and a collapse on the
     * second.
     */
    @Test
    fun aSmallButtonMovesFurtherThanALargeOne() {
        val (smallRest, smallPress) = inkWidths {
            IconButton(Tabler.Outline.Star, "Save", {}, size = ButtonSize.XSmall)
        }
        val (largeRest, largePress) = inkWidths {
            Button(onClick = {}, size = ButtonSize.XLarge) { +"Save the whole itinerary" }
        }

        val small = (smallRest - smallPress).toFloat() / smallRest
        val large = (largeRest - largePress).toFloat() / largeRest

        assertTrue(
            small > large,
            "an XSmall icon button shrinks ${"%.1f".format(small * 100)}% and an " +
                "XLarge button ${"%.1f".format(large * 100)}%. One number for both " +
                "is invisible on the first and a collapse on the second, which is " +
                "how it came to be 3%.",
        )
    }

    /** One control, and the name a failure should call it by. */
    private class Case(val name: String, val content: @Composable () -> Unit)

    private val Buttons = listOf(
        Case("IconButton (default, Ghost)") {
            IconButton(Tabler.Outline.Star, "Save", {})
        },
        Case("IconButton (Primary)") {
            IconButton(Tabler.Outline.Star, "Save", {}, variant = ButtonVariant.Primary)
        },
        Case("IconToggleButton") {
            var on by mutableStateOf(false)
            IconToggleButton(
                icon = Tabler.Outline.Star,
                contentDescription = "Save",
                checked = on,
                onCheckedChange = { on = it },
            )
        },
        Case("Button (Primary)") { Button(onClick = {}) { +"Save" } },
        Case("Button (Tertiary, as in a ButtonGroup)") {
            Button(onClick = {}, variant = ButtonVariant.Tertiary) { +"Save" }
        },
        Case("FloatingActionButton") {
            FloatingActionButton(Tabler.Outline.Star, "Save", {}, size = FabSize.Medium)
        },
    )

    /**
     * The width of the control's ink at rest, and under a finger.
     *
     * Held rather than tapped: the press spring is `springSnappy` and settles in
     * a few frames, so a held press is a stable picture, where a tap would be a
     * race against the bouncy return.
     */
    private fun inkWidths(content: @Composable () -> Unit): Pair<Int, Int> {
        var bounds = Rect.Zero
        var resting = 0
        var pressed = 0

        Scene(width = 500, height = 220) {
            Box(Modifier.fillMaxSize().background(Color.White).padding(24.dp)) {
                Box(Modifier.reportBounds { bounds = it }) { content() }
            }
        }.use { scene ->
            resting = scene.frames(8).inkWidth()
            scene.press(bounds.alongX(0.5f))
            pressed = scene.frames(20).inkWidth()
            scene.release(bounds.alongX(0.5f))
        }

        return resting to pressed
    }
}

/**
 * How wide the control's own ink is, ignoring the press wash.
 *
 * The threshold is deliberately high. `overlayPressed` is 24% black, so on a
 * white page the wash is its own ~61-per-channel mark — and on a *ghost* control
 * that wash is the only container there is, so a lenient threshold measures the
 * glyph at rest and the whole wash circle when pressed and reports the button
 * getting **bigger** under a finger. Anything past [Solid] is a container or a
 * glyph; nothing that faint is either.
 */
private fun BufferedImage.inkWidth(): Int {
    val page = getRGB(2, 2)
    var left = -1
    var right = -1
    for (x in 0 until width) {
        var inked = false
        for (y in 0 until height) {
            val rgb = getRGB(x, y)
            if (kotlin.math.abs((rgb shr 16 and 0xFF) - (page shr 16 and 0xFF)) > Solid ||
                kotlin.math.abs((rgb shr 8 and 0xFF) - (page shr 8 and 0xFF)) > Solid ||
                kotlin.math.abs((rgb and 0xFF) - (page and 0xFF)) > Solid
            ) {
                inked = true
                break
            }
        }
        if (inked) {
            if (left < 0) left = x
            right = x
        }
    }
    return if (left < 0) 0 else right - left + 1
}

/** Well past the 24% wash, well inside any container or glyph. */
private const val Solid = 96
