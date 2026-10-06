package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.kontour.ui.components.text.TextArea
import io.kontour.ui.components.text.TextField
import io.kontour.ui.components.text.TextFieldVariant
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A field set for code is set in the mono face: every character as wide as
 * every other.
 *
 * Reported: *"`TextArea` has no monospace or code style."* A config snippet, a
 * key or a command in Outfit has columns that do not line up and an O that
 * passes for a 0.
 *
 * Measured by where the ink ends. Six narrow letters and six wide ones, each
 * followed by the same `X`: in a monospaced face the `X` lands in the same
 * column either way, and in a proportional one six `M`s push it most of an
 * em further along per letter.
 */
class CodeFieldTest {

    @Test
    fun aCodeAreasTextIsMonospaced() {
        val narrow = inkEnd { TextArea(rememberTextFieldState(Narrow), code = true, variant = Bare, modifier = Wide) }
        val wide = inkEnd { TextArea(rememberTextFieldState(Broad), code = true, variant = Bare, modifier = Wide) }
        assertSameColumn(narrow, wide, "a code TextArea's text")
    }

    @Test
    fun aCodeFieldsPlaceholderIsMonospaced() {
        val narrow = inkEnd { TextField(rememberTextFieldState(), placeholder = Narrow, code = true, variant = Bare, modifier = Wide) }
        val wide = inkEnd { TextField(rememberTextFieldState(), placeholder = Broad, code = true, variant = Bare, modifier = Wide) }
        assertSameColumn(narrow, wide, "a code TextField's placeholder")
    }

    /** Without it, the field's own face — which is what makes the test above see anything. */
    @Test
    fun withoutItTheFieldIsProportional() {
        val narrow = inkEnd { TextArea(rememberTextFieldState(Narrow), variant = Bare, modifier = Wide) }
        val wide = inkEnd { TextArea(rememberTextFieldState(Broad), variant = Bare, modifier = Wide) }
        assertTrue(
            wide - narrow > 40,
            "six Ms should run well past six is in the body face; they end at $wide and $narrow",
        )
    }

    private fun assertSameColumn(narrow: Int, wide: Int, what: String) {
        assertTrue(narrow > 0 && wide > 0, "$what drew no ink to measure ($narrow, $wide)")
        assertTrue(
            abs(wide - narrow) <= 2,
            "$what ends at x=$narrow after six is and at x=$wide after six Ms — not monospaced",
        )
    }

    /** The rightmost column of dark ink in a white scene holding [field]. */
    private fun inkEnd(field: @androidx.compose.runtime.Composable () -> Unit): Int =
        Scene(width = 720, height = 240) {
            Box(Modifier.fillMaxSize().background(Color.White)) { field() }
        }.use { it.frames(20) }.inkEnd()

    private fun BufferedImage.inkEnd(): Int {
        var right = -1
        for (y in 0 until height) {
            for (x in width - 1 downTo right + 1) {
                // The placeholder's muted grey as well as the text's ink.
                if ((getRGB(x, y) shr 16 and 0xFF) < 200) {
                    right = x
                    break
                }
            }
        }
        return right
    }

    private companion object {
        const val Narrow = "iiiiiiX"
        const val Broad = "MMMMMMX"
        val Wide = Modifier.fillMaxWidth()

        /** No frame, so the only ink is the text. */
        val Bare = TextFieldVariant.Plain
    }
}
