package io.kontour.ui.overlay

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A command palette stands clear of the window's sides.
 *
 * Asked for as *"in the command palette, can we give it some minimum side
 * padding (that goes for all "dialog"-type components if it doesn't exist
 * already)"*. A `Dialog` and a bar's expanding panel already kept a margin; the
 * palette took its width cap and nothing else, so on a phone it ran from one
 * edge of the glass to the other.
 *
 * At a density of 1.
 */
@OptIn(ExperimentalTestApi::class)
class CommandPaletteSidesTest {

    private fun ComposeUiTest.palette(window: Int) {
        setContent {
            KontourTheme {
                Box(Modifier.requiredSize(window.dp, 640.dp)) {
                    OverlayHost(Modifier.fillMaxSize()) {
                        CommandPalette(
                            visible = true,
                            onDismissRequest = {},
                            modifier = Modifier.testTag("palette"),
                            commands = listOf(
                                Command("plan", "Plan a trip", onAction = {}),
                                Command("saved", "Saved trips", onAction = {}),
                            ),
                        )
                    }
                }
            }
        }
        waitForIdle()
    }

    @Test
    fun onAPhoneItKeepsClearOfBothSides() = runComposeUiTest {
        palette(window = 360)
        val panel = onNodeWithTag("palette").fetchSemanticsNode().boundsInRoot
        assertTrue(panel.left >= Margin, "the palette's left edge is ${panel.left}px from the window's")
        assertTrue(panel.right <= 360 - Margin, "the palette's right edge is ${360 - panel.right}px from the window's")
        assertEquals(panel.left, 360 - panel.right, 1f, "the palette is not centred: $panel")
    }

    @Test
    fun wideItStillStopsAtItsWidth() = runComposeUiTest {
        palette(window = 1000)
        val panel = onNodeWithTag("palette").fetchSemanticsNode().boundsInRoot
        assertTrue(panel.width < 1000 - 2 * Margin - 100, "on a wide window the palette spread to ${panel.width}px")
        assertEquals(panel.left, 1000 - panel.right, 1f, "the palette is not centred: $panel")
    }

    private companion object {
        const val Margin = 16f
    }
}
