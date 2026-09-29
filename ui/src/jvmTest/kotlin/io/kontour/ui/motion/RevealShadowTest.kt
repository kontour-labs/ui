package io.kontour.ui.motion

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.display.Card
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Theme
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A card arriving through [revealOnScroll] keeps its shadow while it fades in.
 *
 * Reported from the scroll-effects demo as the shadows "doing weird things" while
 * the cards arrived. The reveal faded through a `graphicsLayer` alpha, which
 * composites into a buffer the size of the element, and a card's shadow lies
 * outside the card: so for the length of the fade the shadow was cut off at the
 * card's edge, and came back in one frame when the fade finished. The analysis
 * is `overlayAppearance`'s, which fixed the same thing for menus and dialogs.
 *
 * Read beside the card rather than below it, because the reveals move vertically
 * or scale about the centre, and a point a few dp off the card's side at its
 * middle stays outside it in every frame of both.
 */
class RevealShadowTest {

    @Test
    fun aRevealedCardKeepsItsShadowWhileItFadesIn() {
        for (variant in listOf(RevealVariant.FadeUp, RevealVariant.FadeScale)) {
            val scene = ImageComposeScene(
                width = SceneWidth * 2,
                height = SceneHeight * 2,
                density = Density(2f),
            ) {
                KontourTheme(darkTheme = false, reduceMotion = false) {
                    Box(Modifier.fillMaxSize().background(Ground).padding(CardInset.dp)) {
                        Card(
                            modifier = Modifier.size(CardWidth.dp, CardHeight.dp).revealOnScroll(variant),
                            containerColour = Color.White,
                            shadow = Theme.elevation.overlay,
                        ) {}
                    }
                }
            }
            try {
                val ground = channel(Ground.red)
                val body = mutableListOf<Int>()
                val beside = mutableListOf<Int>()
                var nanos = 0L
                repeat(90) {
                    val pixels = scene.render(nanos).toComposeImageBitmap().toPixelMap()
                    nanos += 16_000_000L
                    val middle = (CardInset + CardHeight / 2) * 2
                    body += channel(pixels[(CardInset + CardWidth / 2) * 2, middle].red)
                    beside += channel(pixels[(CardInset - Outside) * 2, middle].red)
                }
                // Frames where the card is part-way in: neither the ground nor white.
                val fading = body.indices.filter { body[it] in (ground + 6)..(255 - 6) }
                assertTrue(fading.size >= 3, "$variant: never caught the card mid-fade, body = $body")
                assertTrue(beside.last() < ground - 2, "$variant: no shadow beside the card at rest, beside = $beside")
                val cut = fading.filter { beside[it] >= ground }
                assertTrue(
                    cut.isEmpty(),
                    "$variant: the shadow beside the card vanished while it faded in, on frames $cut — " +
                        "body = $body, beside = $beside, ground = $ground",
                )
                // And it fades *with* the card: a shadow at full strength under a
                // card that is still half transparent is the other way to get this
                // wrong, and reads as a grey slab arriving before the card does.
                assertTrue(
                    beside[fading.first()] > beside.last(),
                    "$variant: the shadow was already at full strength while the card faded in — beside = $beside",
                )
            } finally {
                scene.close()
            }
        }
    }

    private fun channel(value: Float): Int = (value * 255f).roundToInt()

    private companion object {
        /** Grey, so the white card's fade can be read against it. */
        val Ground = Color(0xFFD0D0D0)
        const val CardInset = 60
        const val CardWidth = 180
        const val CardHeight = 80
        /** How far off the card's side the shadow is read, in dp. */
        const val Outside = 4
        const val SceneWidth = 300
        const val SceneHeight = 200
    }
}
