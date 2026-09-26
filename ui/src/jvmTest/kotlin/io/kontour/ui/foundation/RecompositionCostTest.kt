package io.kontour.ui.foundation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.overlay.tooltip
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Work that a frame should not be doing, counted.
 *
 * Neither of these shows in a picture — the frame comes out the same either way —
 * which is why they are counted rather than rendered. Each was a value read one
 * phase too early: in composition, where only the layout or the drawing used it.
 */
class RecompositionCostTest {

    /**
     * A `Text` whose colour animates is laid out once, not once a frame.
     *
     * Its layout callback was a lambda written inline around a wrapper built on
     * every composition, so it was a new callback every time — and `BasicText`
     * answers a new callback by throwing away the paragraph it had shaped. Every
     * label in a button, a tab or a chip changing state re-shaped its string on
     * each frame of the fade. A colour is a drawing change and nothing else.
     */
    @Test
    fun aTextUnderAnAnimatedColourIsLaidOutOnce() {
        var layouts = 0
        val counted: (TextLayoutResult) -> Unit = { layouts++ }
        var warm by mutableStateOf(false)
        val scene = ImageComposeScene(width = 400, height = 200, density = Density(2f)) {
            KontourTheme(darkTheme = false) {
                val colour by animateColorAsState(
                    targetValue = if (warm) Color.Red else Color.Blue,
                    animationSpec = tween(durationMillis = 400, easing = LinearEasing),
                    label = "test",
                )
                Text("Route 950", colour = colour, onTextLayout = counted)
            }
        }
        try {
            var time = 0L
            fun frames(count: Int) = repeat(count) {
                scene.render(time)
                time += 16_000_000L
            }
            frames(3)
            val settled = layouts
            assertTrue(settled >= 1, "the text never reported a layout")

            warm = true
            frames(30)

            assertEquals(
                settled,
                layouts,
                "a colour fade laid the text out again ${layouts - settled} times",
            )
        } finally {
            scene.close()
        }
    }

    /**
     * A control with a tooltip on it does not recompose while it scrolls past.
     *
     * The tooltip's anchor is the control's position, which changes on every
     * frame of a scroll, and it used to be read by whatever composable the
     * modifier was written in — so a screen of controls with tooltips on them
     * recomposed, every frame, for bubbles none of which were showing.
     */
    @Test
    fun aClosedTooltipDoesNotRecomposeItsCallerAsItScrolls() {
        var compositions = 0
        val scroll = ScrollState(initial = 0)
        val scene = ImageComposeScene(width = 400, height = 400, density = Density(2f)) {
            KontourTheme(darkTheme = false) {
                OverlayHost(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                        Spacer(Modifier.height(100.dp))
                        TooltipCaller { compositions++ }
                        Spacer(Modifier.height(2000.dp))
                    }
                }
            }
        }
        try {
            var time = 0L
            fun frame() {
                scene.render(time)
                time += 16_000_000L
            }
            repeat(3) { frame() }
            val settled = compositions
            assertTrue(settled >= 1, "the caller never composed")

            repeat(20) {
                scroll.dispatchRawDelta(10f)
                frame()
            }

            assertTrue(scroll.value > 0, "the column never scrolled")
            assertEquals(
                settled,
                compositions,
                "twenty frames of scrolling recomposed the tooltip's caller ${compositions - settled} times",
            )
        } finally {
            scene.close()
        }
    }
}

/** Counts its own compositions, and carries a tooltip that is never shown. */
@Composable
private fun TooltipCaller(onCompose: () -> Unit) {
    SideEffect(onCompose)
    Box(Modifier.size(40.dp).tooltip("Route 950"))
}
