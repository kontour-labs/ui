package io.kontour.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.InfoCircle
import io.kontour.ui.components.action.IconButton
import io.kontour.ui.foundation.Text
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Theme
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * An anchored overlay leaves from the side it was on.
 *
 * Reported on a combobox: it opens above the field when the keyboard leaves no
 * room below — right — and tapping elsewhere closes the keyboard and the menu
 * together, so the room below came back during the menu's exit and it *"briefly
 * jumps back under the combobox as it's closing"*. The keyboard is a bottom inset
 * here, supplied through `LocalOverlaySafeArea`, and it goes away just after the
 * panel is told to close — the order a real keyboard and a real tap arrive in.
 */
class AnchoredExitTest {

    @Test
    fun aPanelFlippedAboveForTheKeyboardLeavesFromAbove() {
        var anchor = Rect.Zero
        var panel = Rect.Zero
        var visible by mutableStateOf(true)
        var keyboard by mutableStateOf<WindowInsets?>(WindowInsets(bottom = 300.dp))
        val scene = ImageComposeScene(width = 600, height = 1000, density = Density(2f)) {
            KontourTheme(darkTheme = false, reduceMotion = true) {
                CompositionLocalProvider(LocalOverlaySafeArea provides (keyboard ?: WindowInsets(0))) {
                    OverlayHost(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize().background(Color.White)) {
                            Box(
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 180.dp)
                                    .onGloballyPositioned { anchor = Rect(it.positionInRoot(), it.size.toSize()) }
                            ) {
                                IconButton(icon = Tabler.Outline.InfoCircle, contentDescription = "About", onClick = {})
                                Popover(visible = visible, onDismissRequest = {}) {
                                    Text(
                                        "Route 950",
                                        style = Theme.typography.titleSmall,
                                        modifier = Modifier.onGloballyPositioned {
                                            panel = Rect(it.positionInRoot(), it.size.toSize())
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        var time = 0L
        fun frame() {
            scene.render(time)
            time += 16_000_000L
        }
        try {
            repeat(20) { frame() }
            assertTrue(
                panel.bottom <= anchor.top,
                "with a keyboard over the room below, the popover should open above its " +
                    "anchor; it is at ${panel.top}..${panel.bottom} against ${anchor.top}",
            )
            visible = false
            frame()
            frame()
            // And now the keyboard goes, while the popover is still on its way out.
            keyboard = null
            val below = mutableListOf<Float>()
            repeat(20) {
                frame()
                if (panel.bottom > anchor.top + 1f) below += panel.top
            }
            assertTrue(
                below.isEmpty(),
                "the closing popover jumped under its anchor (top at $below against the " +
                    "anchor's ${anchor.bottom}) when the keyboard went — it should leave " +
                    "from above, where it was",
            )
        } finally {
            scene.close()
        }
    }
}
