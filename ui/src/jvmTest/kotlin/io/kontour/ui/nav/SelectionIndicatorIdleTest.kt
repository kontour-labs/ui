package io.kontour.ui.nav

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import io.kontour.ui.foundation.Text
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Theme
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A group with a selection marker stops asking for frames as soon as it has
 * nothing left to move.
 *
 * The marker faded itself up to opaque after arriving anywhere, and at rest it
 * leant toward nothing, each by an animation run whatever the value already
 * was. A tween from where it is to where it is changes nothing and still asks
 * for a frame on each of its steps: a tab bar, a nav bar or a rail on a screen
 * just opened went on asking for thirteen frames, ten of them for nothing, and
 * again at the end of every travel. What only `hasInvalidations` can see; see
 * the catalog's `IdleAnimationTest`.
 */
class SelectionIndicatorIdleTest {

    @Test
    fun aTabBarIsStillOnceItHasDrawn() = assertStill("TabBar") {
        TabBar {
            Tab(selected = true, onClick = {}, key = 0) { Text("Departures") }
            Tab(selected = false, onClick = {}, key = 1) { Text("Arrivals") }
        }
    }

    @Test
    fun aNavBarIsStillOnceItHasDrawn() = assertStill("NavBar") {
        NavBar(windowInsets = WindowInsets(0)) {
            item(selected = true, onClick = {}, key = "plan", icon = Theme.icons.chevronBack) { +"Plan" }
            item(selected = false, onClick = {}, key = "map", icon = Theme.icons.chevronForward) { +"Map" }
        }
    }

    private fun assertStill(what: String, content: @Composable () -> Unit) {
        for (reduceMotion in listOf(false, true)) {
            val scene = ImageComposeScene(width = 720, height = 400, density = Density(2f)) {
                KontourTheme(darkTheme = false, reduceMotion = reduceMotion) { content() }
            }
            try {
                var nanos = 0L
                var frames = 0
                do {
                    nanos += 16_000_000L
                    scene.render(nanos).close()
                    frames++
                } while (scene.hasInvalidations() && frames < 60)
                assertTrue(
                    frames <= StillWithin,
                    "$what${if (reduceMotion) " under reduced motion" else ""} asked for $frames frames after it first composed, " +
                        "with nothing on it moving",
                )
            } finally {
                scene.close()
            }
        }
    }

    private companion object {
        /** Composed, laid out and the marker placed by its effect: three, measured. */
        const val StillWithin = 4
    }
}
