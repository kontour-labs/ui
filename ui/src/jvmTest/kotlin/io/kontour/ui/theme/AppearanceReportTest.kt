package io.kontour.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import io.kontour.ui.platform.reportedAppearances
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `KontourTheme` tells the host which appearance it is drawing, and tells it again.
 *
 * Every platform has chrome the canvas cannot reach and that picks its colours
 * from a flag the host owns — Android's status and navigation bar icons, iOS's
 * status bar, a browser's scrollbars. Nothing in this library had ever set it,
 * and the symptom reported from a phone was a dark app under a light system
 * keeping dark icons on a dark bar.
 *
 * ### Why the JVM actual is not `Unit`
 *
 * It has nothing to tell a desktop window — a title bar follows the *operating
 * system's* appearance, not the application's. But an `expect` whose only
 * testable `actual` does nothing is plumbing that can be cut without any check
 * noticing, and Android and iOS have no test source set in this repository. So
 * the JVM one records, and this is the only place that can see whether the
 * report happens at all.
 *
 * ### The second report is the whole point
 *
 * One report at startup is what a host could have worked out for itself from
 * the system setting. What it cannot work out is a change the *app* made — a
 * reader moving the dark switch while the phone stays light — and that is the
 * case the report exists for.
 */
class AppearanceReportTest {

    @Test
    fun theThemeReportsItsAppearanceAndReportsItAgainWhenItChanges() {
        reportedAppearances.clear()
        var dark by mutableStateOf(false)
        val scene = ImageComposeScene(width = 200, height = 200, density = Density(1f)) {
            KontourTheme(darkTheme = dark) { Box(Modifier.fillMaxSize()) }
        }
        try {
            repeat(4) { scene.render(16_000_000L * it).close() }
            assertEquals(
                listOf(false),
                reportedAppearances.toList(),
                "the theme did not tell the host what it was drawing at all",
            )

            dark = true
            repeat(20) { scene.render(16_000_000L * (it + 5)).close() }
            assertEquals(
                listOf(false, true),
                reportedAppearances.toList(),
                "the theme went dark and the host was not told. A host that is " +
                    "told once, at startup, has learnt nothing it could not read " +
                    "from the system itself — the report exists for the change " +
                    "the *app* makes, which is a reader moving the dark switch " +
                    "while the phone stays light.",
            )
        } finally {
            scene.close()
        }
    }
}
