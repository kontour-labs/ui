package io.kontour.ui.nav3

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.unit.Density
import io.kontour.ui.foundation.Text
import io.kontour.ui.motion.BackStyle
import io.kontour.ui.motion.LocalBackStyle
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A pane following a back gesture is moved by its layer, and not drawn again on
 * every frame of the gesture.
 *
 * The shadow it casts onto the ground it uncovers is drawn round its content,
 * and it read the gesture's progress there, though the shadow depends only on
 * whether the pane has moved at all: it is drawn inside the layer that moves
 * the pane, and moves with it. So every frame of a drag drew the whole pane over
 * again. Counted by a line of text in the pane.
 */
class PaneBackMotionCostTest {

    @Test
    fun aPaneDraggedBackIsDrawnOnceForTheDrag() {
        for (style in listOf(BackStyle.Swipe, BackStyle.Predictive)) {
            val back = SceneBackState()
            var draws = 0
            val scene = ImageComposeScene(width = 720, height = 1400, density = Density(2f)) {
                KontourTheme(darkTheme = false) {
                    CompositionLocalProvider(LocalBackStyle provides style) {
                        Box(Modifier.fillMaxSize().paneBackMotion(back)) {
                            Text("Platform 2", modifier = Modifier.drawWithContent { draws++; drawContent() })
                        }
                    }
                }
            }
            try {
                var nanos = 0L
                fun frame() {
                    nanos += 16_000_000L
                    scene.render(nanos).close()
                }
                repeat(4) { frame() }
                draws = 0
                repeat(Frames) { step ->
                    back.progress = (step + 1) * 0.02f
                    frame()
                }
                assertTrue(
                    draws <= 2,
                    "under $style a pane dragged back over $Frames frames was drawn $draws times",
                )
            } finally {
                scene.close()
            }
        }
    }

    private companion object {
        const val Frames = 24
    }
}
