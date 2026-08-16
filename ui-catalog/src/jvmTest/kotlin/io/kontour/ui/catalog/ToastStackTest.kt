package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.overlay.ToastHost
import io.kontour.ui.overlay.ToastHostState
import io.kontour.ui.overlay.ToastTone
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Toasts stack, and each one runs its own clock.
 *
 * The old host held a *queue* and showed its head. A toast pinned for an answer
 * stopped every later one from being seen at all, and four rapid confirmations
 * took sixteen seconds to get through — each waiting for the one in front to
 * expire before its own timer even started.
 *
 * Counted by ink rather than by state, because what changed is what reaches the
 * screen: the state has always held everything that was shown, and the question
 * is how much of it a user can see.
 */
class ToastStackTest {

    @Test
    fun threeToastsAreOnScreenAtOnce() {
        val three = heightAfter { toasts ->
            toasts.show("Added to favourites")
            toasts.show("Saved for offline")
            toasts.show("Route updated")
        }
        val one = heightAfter { toasts -> toasts.show("Added to favourites") }

        // Each toast behind the front one peeks out by `ToastDefaults.Peek`,
        // which is 10dp — twenty pixels here. Two of them, so the stack is at
        // least thirty pixels taller than a single toast even allowing for the
        // ones behind being scaled down.
        assertTrue(
            three > one + 30,
            "three toasts made a stack ${three}px tall against ${one}px for one " +
                "— they are not stacking, only the newest is showing",
        )
    }

    @Test
    fun aPinnedToastDoesNotBlockTheOnesBehindIt() {
        // The defect this rewrite exists for. A pinned toast used to sit at the
        // head of the queue for ever, and nothing queued behind it was ever
        // drawn — its timer had not even started.
        val both = heightAfter { toasts ->
            toasts.show("Couldn't reach the timetable", tone = ToastTone.Danger, durationMillis = 0)
            toasts.show("Saved for offline")
        }
        val pinnedOnly = heightAfter { toasts ->
            toasts.show("Couldn't reach the timetable", tone = ToastTone.Danger, durationMillis = 0)
        }

        assertTrue(
            both > pinnedOnly + 15,
            "a toast shown behind a pinned one made a stack ${both}px tall " +
                "against ${pinnedOnly}px for the pinned one alone — it is still " +
                "being blocked",
        )
    }

    @Test
    fun eachToastKeepsItsOwnClock() {
        // A short one shown after a long one expires first, which under a queue
        // is impossible: it would not have started counting.
        var withBoth = 0
        var afterTheShortOneWent = 0

        Scene(width = 600, height = 400) {
            val toasts = remember { ToastHostState() }
            OverlayHost(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().background(Color.White))
                ToastHost(toasts)
                LaunchedEffect(Unit) {
                    toasts.show("Couldn't reach the timetable", durationMillis = 0)
                    toasts.show("Saved", durationMillis = 1_500)
                }
            }
        }.use { scene ->
            // Half a second in: both settled, and nothing near the short one's
            // second and a half.
            withBoth = scene.frames(30).stackHeight()
            // Two seconds in: the short one has gone and the pinned one has not,
            // which under a queue is impossible — it would not have started
            // counting until the pinned one expired, and it never does.
            afterTheShortOneWent = scene.frames(95).stackHeight()
        }

        assertTrue(withBoth > 0, "nothing was drawn at all")
        assertTrue(
            afterTheShortOneWent < withBoth - 10,
            "the stack was ${withBoth}px tall with both toasts and " +
                "${afterTheShortOneWent}px a second later — the short one never " +
                "expired, so it was waiting on the pinned one's clock",
        )
    }

    @Test
    fun theyStackUnderReducedMotionToo() {
        val three = heightAfter(reduceMotion = true) { toasts ->
            toasts.show("Added to favourites")
            toasts.show("Saved for offline")
            toasts.show("Route updated")
        }
        val one = heightAfter(reduceMotion = true) { toasts ->
            toasts.show("Added to favourites")
        }
        assertTrue(
            three > one + 30,
            "under reduced motion three toasts made a stack ${three}px tall " +
                "against ${one}px for one",
        )
    }

    @Test
    fun theCloseControlIsOffUnlessAskedFor() {
        val plain = widthAfter { toasts -> toasts.show("Added to favourites") }
        val closable = widthAfter(showClose = true) { toasts -> toasts.show("Added to favourites") }

        assertTrue(
            closable > plain + 20,
            "a toast was ${plain}px wide without `showClose` and ${closable}px " +
                "with it — the close control is not appearing",
        )
    }

    /** Renders a stack and measures how tall it ended up. */
    private fun heightAfter(
        showClose: Boolean = false,
        reduceMotion: Boolean = false,
        shown: (ToastHostState) -> Unit,
    ): Int {
        var ink = 0
        Scene(width = 600, height = 400, reduceMotion = reduceMotion) {
            val toasts = remember { ToastHostState() }
            OverlayHost(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().background(Color.White))
                ToastHost(toasts, showClose = showClose)
                LaunchedEffect(Unit) { shown(toasts) }
            }
        }.use { scene ->
            ink = scene.frames(24).stackHeight()
        }
        return ink
    }

    /** Renders a stack and measures how wide the front toast ended up. */
    private fun widthAfter(showClose: Boolean = false, shown: (ToastHostState) -> Unit): Int {
        var width = 0
        Scene(width = 600, height = 400) {
            val toasts = remember { ToastHostState() }
            OverlayHost(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().background(Color.White))
                ToastHost(toasts, showClose = showClose)
                LaunchedEffect(Unit) { shown(toasts) }
            }
        }.use { scene ->
            val image = scene.frames(24)
            var left = image.width
            var right = -1
            for (y in 0 until image.height) {
                for (x in 0 until image.width) {
                    if (!isSurface(image.getRGB(x, y))) continue
                    if (x < left) left = x
                    if (x > right) right = x
                }
            }
            width = if (right < 0) 0 else right - left + 1
        }
        return width
    }

    /**
     * How tall the run of toast surfaces is, top to bottom.
     *
     * A direct count of how many are showing: they overlap, so each one behind
     * the front adds only its `Peek` of height — but it adds it reliably, where
     * a pixel count is dominated by the front card and says almost nothing.
     */
    private fun BufferedImage.stackHeight(): Int {
        var top = -1
        var bottom = -1
        for (y in 0 until height) {
            var dark = false
            for (x in 0 until width) {
                if (isSurface(getRGB(x, y))) {
                    dark = true
                    break
                }
            }
            if (dark) {
                if (top < 0) top = y
                bottom = y
            }
        }
        return if (top < 0) 0 else bottom - top + 1
    }

    /**
     * Whether this pixel belongs to a toast *surface*.
     *
     * Dark ones only. A toast carries a wide soft shadow, and counting anything
     * that is not white counted mostly shadow — which barely grows when a second
     * toast is stacked ten dp behind the first, so the measurement said nothing
     * about how many were showing.
     */
    private fun isSurface(rgb: Int): Boolean {
        val luminance =
            ((rgb shr 16 and 0xFF) * 30 + (rgb shr 8 and 0xFF) * 59 + (rgb and 0xFF) * 11) / 100
        return luminance < 128
    }
}

