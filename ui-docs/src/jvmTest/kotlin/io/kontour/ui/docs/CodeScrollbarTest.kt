package io.kontour.ui.docs

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import io.kontour.ui.foundation.Surface
import io.kontour.ui.input.InputModality
import io.kontour.ui.input.LocalInputModality
import io.kontour.ui.theme.KontourTheme
import org.jetbrains.skia.EncodedImageFormat
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A code block too wide for its window says so.
 *
 * `CodeBlock`'s own KDoc has claimed since it was written that "a scrollbar is
 * the honest answer on a narrow window", and for four rounds that comment was
 * the only place one existed: the block scrolled sideways with nothing to
 * indicate that it did, so a line running past the right edge was
 * indistinguishable from a line that had been cut off.
 *
 * ### Why this needs its own modality
 *
 * `Scrollbar` draws only for a pointer that can hover, which is right — a
 * permanent scrollbar on a touchscreen is not draggable at any sensible width
 * and takes room from the content. But it means an `ImageComposeScene`, which
 * has no pointer at all, defaults to `InputModality.Touch` and never draws one.
 * That is the structural reason `focusRing` and `Scrollbar` have both gone
 * whole rounds with no pixel covering them.
 *
 * `LocalInputModality` is public and a static local, so the modality can simply
 * be stated. Asserting the *difference* between the two rather than an absolute
 * position is what keeps this about the scrollbar: the same page, the same
 * blocks, the same width, and one composition local apart.
 */
class CodeScrollbarTest {

    @Test
    fun aCodeBlockTooWideToFitDrawsAScrollbarForAPointer() {
        val pointer = render(InputModality.Mouse)
        val finger = render(InputModality.Touch)

        // The two frames are the same page one composition local apart, so
        // every pixel that differs between them belongs to the scrollbar.
        var differing = 0
        var lowest = Int.MAX_VALUE
        for (y in 0 until Height) {
            for (x in 0 until Width) {
                if (pointer.getRGB(x, y) != finger.getRGB(x, y)) {
                    differing++
                    lowest = minOf(lowest, y)
                }
            }
        }

        assertTrue(
            differing > Tolerance,
            "a code block far wider than its window drew the same $differing " +
                "pixels to a mouse as to a finger — the scrollbar is not there",
        )
        // Along the bottom edge, not somewhere else that happens to react to a
        // pointer. The block is around 50px tall at this density.
        assertTrue(
            lowest > Height / 4,
            "the pixels that differ start at row $lowest, near the top of a " +
                "${Height}px scene — that is not a scrollbar along the bottom",
        )
    }

    /**
     * Ink in the strip the scrollbar occupies, along the bottom of the block.
     *
     * The block is the only thing in the scene and its background is a flat
     * `surfaceSunken`, so anything in that strip which is not the background is
     * the thumb.
     */
    private fun render(modality: InputModality): java.awt.image.BufferedImage {
        val block = Block.Code(
            language = "kotlin",
            // One line, far wider than the window, so there is certainly
            // something to scroll and certainly a thumb short enough to see.
            code = "Button(onClick = {}) { +\"" + "a really quite long label ".repeat(6) + "\" }",
        )
        val scene = ImageComposeScene(
            width = Width,
            height = Height,
            density = Density(1f),
            content = {
                // **Inside** the theme, not around it. `KontourTheme` installs
                // its own modality tracker and provides the local itself unless
                // one is already installed — and the flag that says so is
                // internal — so a provider wrapped around it is simply
                // overwritten. Wrapped around it, this test reported the two
                // frames as identical, which was true and about the harness.
                KontourTheme(darkTheme = false, reduceMotion = true) {
                    CompositionLocalProvider(LocalInputModality provides modality) {
                        Surface(Modifier.fillMaxSize()) {
                            LazyColumn(Modifier.fillMaxSize()) { prose(listOf(block)) }
                        }
                    }
                }
            },
        )
        try {
            // Several frames, not one. The scrollbar's own geometry comes from
            // a `ScrollState` that has no maximum until the content beneath it
            // has been measured, and its thickness and alpha are animated — so
            // the first frame has nothing to draw and knows it. The catalog's
            // `ScrollbarCornerInsetTest` renders thirty for the same reason.
            var bytes = ByteArray(0)
            repeat(Frames) { frame ->
                bytes = scene.render(frame * FrameNanos).encodeToData(EncodedImageFormat.PNG)!!.bytes
            }
            return ImageIO.read(ByteArrayInputStream(bytes))
        } finally {
            scene.close()
        }
    }

    private companion object {
        /** Narrow enough that a single line of Kotlin cannot fit. */
        const val Width = 320
        const val Height = 90

        /** More than antialiasing on a rounded cap could account for. */
        const val Tolerance = 50

        const val Frames = 20
        const val FrameNanos = 16_000_000L
    }
}
