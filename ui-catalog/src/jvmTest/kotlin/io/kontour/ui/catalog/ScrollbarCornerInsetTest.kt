package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.list.Scrollbar
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A scrollbar starts below the corner of the container it is in.
 *
 * Item 24. A scrollbar measures *itself*, not its container, so it has no way to
 * discover that the panel around it is rounded — and a track that runs the full
 * height of a menu has both its ends behind the curve. `cornerInset` is how a
 * host tells it, and `Menu` derives the number from its own shape rather than
 * restating it.
 *
 * ### Measured as a difference, not a position
 *
 * The absolute top of the thumb also carries `Theme.spacing.xxs`, the padding
 * the track has always had. Asserting a position would pin that padding by
 * accident and fail the day it changes for unrelated reasons. The *difference*
 * between two insets is exactly the feature and nothing else.
 *
 * The page is white and the thumb is `outlineStrong`, so the first row with any
 * ink in it is the top of the thumb; there is nothing else in the scene.
 */
class ScrollbarCornerInsetTest {

    @Test
    fun theTrackStartsBelowTheContainersCorner() {
        val inset = 24.dp
        val flush = thumbTop(0.dp)
        val inseted = thumbTop(inset)

        assertTrue(
            flush >= 0 && inseted >= 0,
            "the thumb was not drawn at all: flush=$flush inset=$inseted",
        )

        val moved = inseted - flush
        val expected = inset.value.toInt() * Density
        assertTrue(
            abs(moved - expected) <= Tolerance,
            "a ${inset.value.toInt()}dp corner inset moved the thumb's top by " +
                "${moved / Density}dp, not ${inset.value.toInt()}dp — the track " +
                "is not clearing the corner it was told about",
        )
    }

    /** How far down the scene the scrollbar's thumb starts, in pixels. */
    private fun thumbTop(cornerInset: Dp): Int {
        var top = -1
        Scene(width = 120, height = 400) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                val scroll = rememberScrollState()
                // Long enough that the bar has something to indicate: a
                // scrollbar on content that fits returns without drawing.
                Column(Modifier.verticalScroll(scroll)) {
                    repeat(20) { Box(Modifier.fillMaxWidth().height(40.dp)) }
                }
                Scrollbar(
                    state = scroll,
                    modifier = Modifier.align(Alignment.CenterEnd),
                    cornerInset = cornerInset,
                    // The bar is drawn only for a pointer that can hover, and
                    // the scene has no pointer at all.
                    alwaysVisible = true,
                )
            }
        }.use { scene -> top = scene.frames(30).firstInkRow() }
        return top
    }

    /** The first row holding anything that is not the white page. */
    private fun BufferedImage.firstInkRow(): Int {
        for (y in 0 until height) {
            for (x in 0 until width) {
                if ((getRGB(x, y) and 0xFFFFFF) != 0xFFFFFF) return y
            }
        }
        return -1
    }

    private companion object {
        const val Density = 2

        /** Antialiasing on the thumb's rounded cap, and nothing more. */
        const val Tolerance = 3
    }
}
