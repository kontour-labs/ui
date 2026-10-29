package io.kontour.ui.motion

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import io.kontour.ui.PhaseCounts
import io.kontour.ui.countPhases
import io.kontour.ui.foundation.Text
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Theme
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A page sliding over another under [pageEffects] is not drawn again on every
 * frame of the slide.
 *
 * The dim under the page beneath and the shadow the page on top casts are drawn
 * from the transition, so their drawing runs on every frame of a push or a pop.
 * They were drawn round the page's own content, and so every frame drew the
 * whole page over too, where the slide alone moves a page that is already
 * drawn. Not visible in any picture; counted, by a line of text on each page.
 */
class PageEffectsCostTest {

    @Test
    fun aPushUnderSwipeBackDrawsEachPageOnce() {
        var page by mutableIntStateOf(0)
        val text = PhaseCounts()
        val scene = ImageComposeScene(width = 720, height = 1400, density = Density(2f)) {
            KontourTheme(darkTheme = false) {
                val motion = rememberPageMotion(BackStyle.Swipe)
                AnimatedContent(targetState = page, transitionSpec = { motion.push() }) { shown ->
                    Column(
                        Modifier
                            .fillMaxSize()
                            .background(Theme.colours.background)
                            .pageEffects(motion, this, isPop = { false }),
                    ) {
                        Text("Page $shown", modifier = Modifier.countPhases(text))
                    }
                }
            }
        }
        try {
            var nanos = 0L
            fun frames(count: Int) = repeat(count) {
                nanos += 16_000_000L
                scene.render(nanos).close()
            }
            frames(4)
            text.reset()
            page = 1
            frames(Frames)

            assertTrue(text.draws >= 2, "neither page was drawn: the push measured nothing")
            assertTrue(
                text.draws <= 4,
                "a push drew its two pages ${text.draws} times in $Frames frames: the dim and the shadow are drawing the pages over",
            )
        } finally {
            scene.close()
        }
    }

    private companion object {
        /** Longer than the slide's 350ms. */
        const val Frames = 24
    }
}
