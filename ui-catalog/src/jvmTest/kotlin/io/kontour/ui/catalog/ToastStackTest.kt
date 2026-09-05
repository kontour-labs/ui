package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.overlay.ToastHost
import io.kontour.ui.overlay.ToastHostState
import io.kontour.ui.overlay.ToastPosition
import io.kontour.ui.overlay.ToastTone
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertNotNull
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
        // which is 16dp — thirty-two pixels here. Two of them, so the stack is
        // comfortably taller than a single toast even allowing for the ones
        // behind being scaled down. The threshold is well under the real figure
        // on purpose: what is being asserted is that they stack at all.
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
        // A short one shown after a pinned one expires first, which under a
        // queue is impossible: it would not have started counting.
        //
        // ### Why this waits rather than counting frames
        //
        // A toast expires through `delay`, which is the wall clock, and `Scene`
        // advances 16ms of *frame* time per rendered frame. Nothing ties the
        // two together: a frame costs about 45ms of real time on a throttled
        // container and rather less on a CI runner.
        //
        // This test used to render 95 frames and call it two seconds. It was
        // 3.6 seconds on the machine it was written on — so it passed — and
        // under 1.5 on GitHub's runners, where the short toast had not expired
        // yet and the assertion below failed. It began failing the day
        // `:ui-catalog` grew enough other tests to change what it shared a
        // runner with, having been wrong since it was written.
        //
        // Three seconds rather than 1,500ms for the short one, so that settling
        // the entry animation cannot eat the whole duration on a slow machine —
        // and `pinnedAlone` below is what catches it if it ever does.
        val pinnedAlone = heightAfter { toasts ->
            toasts.show("Couldn't reach the timetable", durationMillis = 0)
        }

        var withBoth = 0
        var afterTheShortOneWent: BufferedImage? = null

        Scene(width = 600, height = 400) {
            val toasts = remember { ToastHostState() }
            OverlayHost(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().background(Color.White))
                ToastHost(toasts)
                LaunchedEffect(Unit) {
                    toasts.show("Couldn't reach the timetable", durationMillis = 0)
                    toasts.show("Saved", durationMillis = 3_000)
                }
            }
        }.use { scene ->
            // The entry is animated on the frame clock, so a frame count is the
            // right unit here — the same 24 every other test in this file uses.
            withBoth = scene.frames(24).stackHeight()
            afterTheShortOneWent = scene.renderUntil { it.stackHeight() < withBoth - 10 }
        }

        assertTrue(withBoth > 0, "nothing was drawn at all")
        assertTrue(
            withBoth > pinnedAlone + 10,
            "the stack was only ${withBoth}px with both toasts against " +
                "${pinnedAlone}px for the pinned one alone — the short one was " +
                "already gone before this measured, so the test proves nothing",
        )
        val settled = afterTheShortOneWent
        assertNotNull(
            settled,
            "the stack was ${withBoth}px tall and never shrank — the short one " +
                "never expired, so it was waiting on the pinned one's clock",
        )
        assertTrue(
            settled.stackHeight() > 0,
            "the stack emptied entirely — the pinned toast went too, and a " +
                "`durationMillis = 0` toast is supposed to stay until dismissed",
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

    @Test
    fun aTopStackSitsAtTheTop() {
        // The first test of the non-default position, and it has to ask *where*
        // the ink is rather than how much of it there is. Every other test here
        // measures the height of the run of toast surfaces, which is the same
        // number whichever edge the stack is anchored to — so all of them would
        // pass against a top stack that was drawing in the wrong half of the
        // screen, or underneath the status bar.
        val bottom = topmostSurface(ToastPosition.Bottom)
        val top = topmostSurface(ToastPosition.Top)

        assertTrue(bottom > 0 && top > 0, "one of the stacks drew nothing at all")
        assertTrue(
            top < bottom / 2,
            "a top-anchored stack started ${top}px down a ${SceneHeight}px window " +
                "against ${bottom}px for a bottom-anchored one — it is not at the top",
        )
    }

    @Test
    fun aTopStackHonoursATopInset() {
        // What this can and cannot prove is worth being exact about.
        //
        // The real defect was the *default*: `windowInsets` was fixed at
        // `sheetEdges`, which is bottom, horizontal and the IME and has no top
        // side at all, so a top-anchored stack drew underneath the status bar
        // and the cutout. That default is not checkable here — every
        // `WindowInsets` in an `ImageComposeScene` is zero, because there is no
        // platform to report a status bar, so `sheetEdges` and `topEdges` are
        // indistinguishable from the harness's point of view. A first draft of
        // this test passed with the fix reverted for exactly that reason.
        //
        // What *is* checkable is the plumbing underneath it: that the stack
        // applies the top side of whatever insets it is handed. A stack that
        // ignored them, or padded only the bottom, would fail here — and the
        // default's correctness is then one expression that can be read.
        val withoutInset = topmostSurface(ToastPosition.Top, statusBar = 0)
        val withInset = topmostSurface(ToastPosition.Top, statusBar = StatusBar)

        assertTrue(
            withInset >= withoutInset + StatusBar - Slack,
            "a top toast started ${withInset}px down with a ${StatusBar}px status " +
                "bar and ${withoutInset}px without one — the stack is not applying " +
                "the top side of its insets",
        )
    }

    /** How far down the window the topmost toast surface begins. */
    private fun topmostSurface(position: ToastPosition, statusBar: Int = 0): Int {
        var row = -1
        Scene(width = 600, height = SceneHeight) {
            val toasts = remember { ToastHostState() }
            OverlayHost(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().background(Color.White))
                ToastHost(
                    toasts,
                    position = position,
                    windowInsets = WindowInsets(top = statusBar, bottom = statusBar),
                )
                LaunchedEffect(Unit) {
                    toasts.show("Saved for offline", durationMillis = 0)
                }
            }
        }.use { scene ->
            val image = scene.frames(24)
            for (y in 0 until image.height) {
                if ((0 until image.width).any { isSurface(image.getRGB(it, y)) }) {
                    row = y
                    break
                }
            }
        }
        return row
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

    /**
     * The toast behind is a pill, not a card the same width as the one in front.
     *
     * Item 25. Every card used to be held at the stack's widest — a ratchet in
     * `ToastStack`, `stackWidthPx = maxOf(stackWidthPx, size.width)`, applied as
     * `widthIn(min = …)` — so a short toast grew the moment a longer one arrived
     * behind it. The file argued for that: one silhouette is what makes a stack
     * read as a stack. The answer is that a stack of one card with plain pills
     * behind it reads better, and does not need every card to be the same shape.
     *
     * ### Measured as two widths in one frame, not one width across two frames
     *
     * Comparing "before the long one arrived" against "after" would pass on a
     * stack that was always wide, and it would have to hold a frame from an
     * animation still in flight. Both cards are on screen at once here, so the
     * question is answerable from a single settled frame: how wide is the band
     * peeking out at the top, against how wide is the card at the bottom?
     *
     * Under the old model those are the same number by construction. The pill is
     * a fixed width and the card sizes to a long message, so any real gap between
     * them is the fix.
     */
    @Test
    fun theToastBehindIsAPillRatherThanACardOfTheSameWidth() {
        var peek = 0
        var card = 0

        Scene(width = 600, height = SceneHeight) {
            val toasts = remember { ToastHostState() }
            OverlayHost(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().background(Color.White))
                ToastHost(toasts)
                LaunchedEffect(Unit) {
                    // Pinned, so the frame is settled rather than mid-timer, and
                    // short-then-long because that is the reported order: the
                    // short one is the one that used to stretch.
                    toasts.show("Saved", durationMillis = 0)
                    toasts.show(
                        "Couldn't reach the timetable service just now",
                        durationMillis = 0,
                    )
                }
            }
        }.use { scene ->
            val image = scene.frames(40)
            val rows = image.surfaceRows()
            assertNotNull(rows, "no toast surface was drawn at all")
            peek = image.widthAtRow(rows.first + Probe)
            card = image.widthAtRow(rows.last - Probe)
        }

        assertTrue(card > 0 && peek > 0, "peek=$peek card=$card — one band drew nothing")
        assertTrue(
            peek < card * PillShare,
            "the toast behind is ${peek}px wide against the front card's ${card}px. " +
                "They are within ${((peek.toFloat() / card) * 100).toInt()}% of each " +
                "other, which is a stack of cards holding one width between them " +
                "rather than a card with a pill behind it.",
        )
    }

    /** The first and last rows holding any toast surface. */
    private fun BufferedImage.surfaceRows(): IntRange? {
        var first = -1
        var last = -1
        for (y in 0 until height) {
            val any = (0 until width).any { isSurface(getRGB(it, y)) }
            if (!any) continue
            if (first < 0) first = y
            last = y
        }
        return if (first < 0) null else first..last
    }

    /** How wide the run of toast surface is on one row. */
    private fun BufferedImage.widthAtRow(y: Int): Int {
        if (y !in 0 until height) return 0
        var left = width
        var right = -1
        for (x in 0 until width) {
            if (!isSurface(getRGB(x, y))) continue
            if (x < left) left = x
            if (x > right) right = x
        }
        return if (right < 0) 0 else right - left + 1
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
    private companion object {
        const val SceneHeight = 400

        /** A plausible status bar in this scene's pixels. */
        const val StatusBar = 72

        /** Antialiasing and a pixel of shadow. */
        const val Slack = 4

        /** Far enough into a band to be past its rounded corner. */
        const val Probe = 12

        /**
         * How much of the front card's width a pill behind it may take.
         *
         * Generous: the pill is a fixed width and the card is holding a long
         * sentence, so the real gap is much larger than this. The number is a
         * line between "these are two shapes" and "these are one silhouette",
         * not a measurement of the design.
         */
        const val PillShare = 0.75f
    }

    private fun isSurface(rgb: Int): Boolean {
        val luminance =
            ((rgb shr 16 and 0xFF) * 30 + (rgb shr 8 and 0xFF) * 59 + (rgb and 0xFF) * 11) / 100
        return luminance < 128
    }
}

