package io.kontour.ui.overlay

import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Theme
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A submenu opened in place closes the way it opened, over as long, rather than
 * snapping shut.
 *
 * It opened on the theme's spring and closed on its fastest tween, so a long
 * submenu collapsed its whole height in a fraction of the time it took to
 * grow. Reported as *"its closing animation feels a lot quicker and less fluent
 * than its opening animation, especially in longer lists"*.
 */
@OptIn(ExperimentalTestApi::class)
class SubmenuCloseTest {

    @Test
    fun aSubmenuTakesAsLongToCloseAsToOpen() = runComposeUiTest {
        val levels = MenuLevels()
        setContent {
            KontourTheme {
                levels.scope = rememberCoroutineScope()
                levels.motion = Theme.motion
            }
        }
        waitForIdle()
        mainClock.autoAdvance = false
        val level = MenuLevel(key = "Sort by", rowTop = 0f, rowHeight = 40f, expandedLabel = "", header = {}, content = {})

        runOnUiThread { levels.push(level) }
        var opening = 0
        while (level.progress.value < 0.99f && opening < MaxFrames) {
            mainClock.advanceTimeByFrame()
            opening++
        }

        runOnUiThread { levels.close(level) }
        var closing = 0
        while (level.progress.value > 0.01f && closing < MaxFrames) {
            mainClock.advanceTimeByFrame()
            closing++
        }

        assertTrue(
            closing >= opening * 0.8,
            "it opened over $opening frames and closed over $closing: it should leave about as gently as it came",
        )
    }

    private companion object {
        const val MaxFrames = 200
    }
}
