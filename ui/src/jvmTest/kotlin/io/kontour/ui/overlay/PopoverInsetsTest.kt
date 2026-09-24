package io.kontour.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
 * An anchored overlay keeps clear of the window's insets only where its host
 * actually meets them.
 *
 * Reported from a phone: *"the popover now doesn't appear in the right location"*.
 * Rendered at phone size on the desktop it was exactly right, which is what made it
 * a phone bug: the difference is the status bar and home indicator. The overlay
 * took both off the edges of whatever host it was in, and the catalog's popover
 * is in a 260dp stage half-way down the page — so 47dp off its top and 34dp off its
 * bottom left less than the 64dp floor on either side of the button in its middle,
 * and the panel was pushed up over the button it belonged to.
 *
 * The insets come in through `LocalOverlaySafeArea`, because a desktop scene has
 * none of its own.
 */
class PopoverInsetsTest {

    @Test
    fun aHostHalfWayDownThePageIgnoresTheWindowsInsets() {
        val (anchor, panel) = open(fillsTheWindow = false)
        assertTrue(
            panel.top >= anchor.bottom,
            "in a 260dp host half-way down a phone, a popover asked for below starts at " +
                "${panel.top}px against an anchor ending at ${anchor.bottom}px — it is " +
                "covering the button it belongs to. The status bar and home indicator " +
                "are nowhere near this host and were taken off its edges anyway.",
        )
    }

    /**
     * And a host that does fill the window still keeps clear of them: an anchor
     * 60dp off the bottom has room below it only if the home indicator is ignored.
     */
    @Test
    fun aHostThatFillsTheWindowStillKeepsClearOfThem() {
        val (anchor, panel) = open(fillsTheWindow = true)
        assertTrue(
            panel.bottom <= anchor.top,
            "an anchor 60dp above a 34dp home indicator had its popover drawn below it " +
                "(panel ${panel.top}..${panel.bottom}px, anchor ${anchor.top}..${anchor.bottom}px) " +
                "— into the home indicator, where there was no room for it",
        )
    }

    /** The anchor's bounds and the popover's first line's, both in the scene. */
    private fun open(fillsTheWindow: Boolean): Pair<Rect, Rect> {
        var anchor = Rect.Zero
        var panel = Rect.Zero
        val scene = ImageComposeScene(width = 780, height = 1600, density = Density(2f)) {
            KontourTheme(darkTheme = false, reduceMotion = true) {
                CompositionLocalProvider(LocalOverlaySafeArea provides PhoneInsets) {
                    OverlayHost(Modifier.fillMaxSize()) {
                        if (fillsTheWindow) {
                            Box(Modifier.fillMaxSize().background(Color.White)) {
                                Trigger(
                                    Modifier.align(Alignment.BottomCenter).padding(bottom = 60.dp),
                                    { anchor = it },
                                    { panel = it },
                                )
                            }
                        } else {
                            Column(Modifier.fillMaxSize().background(Color.White).padding(16.dp)) {
                                Spacer(Modifier.height(250.dp))
                                Box(Modifier.fillMaxWidth().height(260.dp)) {
                                    OverlayHost(Modifier.fillMaxSize()) {
                                        Box(Modifier.fillMaxSize()) {
                                            Trigger(Modifier.align(Alignment.Center), { anchor = it }, { panel = it })
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        try {
            for (i in 0..30) scene.render(i * 16_000_000L)
        } finally {
            scene.close()
        }
        assertTrue(anchor.height > 0f && panel.height > 0f, "nothing was drawn ($anchor, $panel)")
        return anchor to panel
    }

    @Composable
    private fun Trigger(modifier: Modifier, onAnchor: (Rect) -> Unit, onPanel: (Rect) -> Unit) {
        Box(modifier.onGloballyPositioned { onAnchor(Rect(it.positionInRoot(), it.size.toSize())) }) {
            IconButton(icon = Tabler.Outline.InfoCircle, contentDescription = "About", onClick = {})
            Popover(visible = true, onDismissRequest = {}) {
                Text(
                    "Route 950",
                    style = Theme.typography.titleSmall,
                    modifier = Modifier.onGloballyPositioned { onPanel(Rect(it.positionInRoot(), it.size.toSize())) },
                )
                Text(
                    "Runs every 15 minutes until 11pm, then every 30 minutes overnight.",
                    style = Theme.typography.bodySmall,
                )
            }
        }
    }

    private companion object {
        /** An iPhone's status bar and home indicator. */
        val PhoneInsets = WindowInsets(top = 47.dp, bottom = 34.dp)
    }
}
