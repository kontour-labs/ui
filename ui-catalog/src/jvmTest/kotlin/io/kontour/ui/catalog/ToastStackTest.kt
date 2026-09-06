package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import kotlin.test.assertNull
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

    /**
     * A toast that never reached the screen still expires.
     *
     * The clock lived in `ToastCard`, and a card is only composed for the
     * `maxVisible` toasts at the front of the stack. So a toast queued behind
     * them had no `LaunchedEffect` running at all: its timer did not start until
     * the ones in front had gone, at which point it ran its *full* duration from
     * the beginning. Four confirmations in a burst took two rounds of the clock
     * to clear rather than one, which is the jank in the report — and
     * `ToastHost`'s own KDoc claimed the opposite ("their timers run either
     * way").
     *
     * ### Measured by widening the window, not by timing the stack
     *
     * The obvious test — show five, time how long the stack takes to empty — is
     * a wall-clock race, and this harness renders a frame of 16ms in about 45ms
     * of real time, so the animations cost more than their nominal duration and
     * the margin between one round of the clock and two is not safe. The
     * question here is asked as a single settled frame instead: give the hidden
     * toast plenty of time to expire, then raise `maxVisible` and ask whether it
     * appears. If its clock ran, there is nothing left to appear.
     */
    @Test
    fun aToastBehindTheVisibleWindowStillRunsItsClock() {
        var maxVisible by mutableStateOf(1)
        var narrow = 0
        var grew: BufferedImage? = null

        Scene(width = 600, height = SceneHeight) {
            val toasts = remember { ToastHostState() }
            OverlayHost(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().background(Color.White))
                ToastHost(toasts, maxVisible = maxVisible)
                LaunchedEffect(Unit) {
                    toasts.show("Saved for offline", durationMillis = 2_500)
                    // Pinned and newest, so it is the one card the window has
                    // room for, and the stack never empties out from under the
                    // measurement.
                    toasts.show("Couldn't reach the timetable", durationMillis = 0)
                }
            }
        }.use { scene ->
            // Twice the hidden toast's duration, so a clock that started at all
            // has finished. `until = { false }` is "render until the deadline".
            scene.renderUntil(timeoutMillis = 5_000) { false }
            narrow = scene.frame().stackHeight()
            maxVisible = 3
            grew = scene.renderUntil(timeoutMillis = 2_000) { it.stackHeight() > narrow + 10 }
        }

        assertTrue(narrow > 0, "no toast was drawn at all")
        assertNull(
            grew,
            "the stack was ${narrow}px with room for one toast and grew to " +
                "${grew?.stackHeight()}px the moment there was room for two — the " +
                "toast queued behind was still there five seconds after its " +
                "2,500ms timer should have taken it, because its clock only " +
                "starts when it is drawn",
        )
    }

    /**
     * The toasts waiting behind step in at the sides, visibly.
     *
     * They always shrank a little — `1 - 0.07 * depth`, so 134px then 124px at
     * this scene's density — and five pixels a side across a 32px overlap is not
     * a step you can see. The stack rendered as one lumpy silhouette with a
     * wobble in it, which is the report: they should get *gradually smaller*,
     * and the size they were is the largest they should be.
     *
     * Measured as the narrowest pill against the widest, both taken from the
     * band above the front card. Both numbers are in the same frame, so this
     * cannot be satisfied by a stack that is uniformly narrow.
     */
    @Test
    fun theToastsBehindTaper() {
        val stack = stackOfFive()
        val profile = stack.widthProfile()
        val cardTop = stack.cardTop(profile)
        // The deepest pill is the only one whose straight midsection is not
        // covered by something in front, so it is the only one with a plateau —
        // and the first one from the top is therefore its own width.
        val deepest = profile.firstPlateau()
        assertNotNull(deepest, "no pill was drawn above the front card at all")
        val nearest = profile[cardTop - 2]

        assertTrue(
            nearest - deepest >= TaperStep,
            "the pill at the back of the stack is ${deepest}px wide and the one " +
                "at the front of it is ${nearest}px — ${nearest - deepest}px " +
                "between them across three toasts. They are all the same size; " +
                "nothing tapers.",
        )
    }

    /**
     * Three toasts wait behind the front one, and you can tell them apart.
     *
     * Two things at once, because they are the same measurement. The stack shows
     * four now rather than three — one card and three pills — and every pill
     * carries a hairline in its own content colour so the boundary between two
     * of them is visible.
     *
     * That rim is the "shadows for separation" in the report, arrived at the
     * long way round: the shadow is black and so is the toast, so
     * `Theme.elevation.high` puts 10% black over an almost-black pill and draws
     * nothing. See the note in `ToastSurface`.
     *
     * Counted down the middle of the stack, where every pill's boundary crosses
     * and nothing else does.
     */
    @Test
    fun threeToastsWaitBehindTheFrontOneAndEachIsSeparate() {
        val stack = stackOfFive()
        val profile = stack.widthProfile()
        val top = profile.indexOfFirst { it > 0 }
        val cardTop = stack.cardTop(profile)

        val body = stack.luminance(stack.width / 2, cardTop - 6)
        var runs = 0
        var inRun = false
        for (y in top until cardTop) {
            val lit = stack.luminance(stack.width / 2, y) >= body + RimContrast
            if (lit && !inRun) runs++
            inRun = lit
        }

        assertTrue(
            runs == 3,
            "counted $runs lit edges down the middle of the stack between the " +
                "top of it and the front card, where three pills should each " +
                "contribute one. Zero means they are one undivided silhouette " +
                "of the same colour; fewer than three means fewer than three " +
                "are being drawn.",
        )
    }

    /** A settled stack with five toasts queued and none of them expiring. */
    private fun stackOfFive(): BufferedImage {
        var image: BufferedImage? = null
        Scene(width = 600, height = SceneHeight) {
            val toasts = remember { ToastHostState() }
            OverlayHost(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().background(Color.White))
                ToastHost(toasts)
                LaunchedEffect(Unit) {
                    repeat(4) { toasts.show("Saved for offline", durationMillis = 0) }
                    toasts.show("Couldn't reach the timetable service", durationMillis = 0)
                }
            }
        }.use { scene -> image = scene.frames(60) }
        return requireNotNull(image)
    }

    /** How wide the run of toast surface is on every row, top to bottom. */
    private fun BufferedImage.widthProfile(): List<Int> =
        (0 until height).map { widthAtRow(it) }

    /**
     * The first row belonging to the front card.
     *
     * The card is several times wider than any pill, so "most of the widest
     * thing on screen" finds it without needing to know what any of the pills
     * measure.
     */
    private fun BufferedImage.cardTop(profile: List<Int>): Int {
        val widest = profile.max()
        return profile.indexOfFirst { it > widest * 7 / 10 }
    }

    /** The width of the first run of rows that hold still, or null if none do. */
    private fun List<Int>.firstPlateau(): Int? {
        var run = 1
        for (i in 1 until size) {
            if (this[i] == this[i - 1] && this[i] > 0) {
                run++
                if (run >= PlateauRows) return this[i]
            } else {
                run = 1
            }
        }
        return null
    }

    private fun BufferedImage.luminance(x: Int, y: Int): Int {
        val rgb = getRGB(x, y)
        return (((rgb shr 16) and 0xFF) + ((rgb shr 8) and 0xFF) + (rgb and 0xFF)) / 3
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

        /**
         * How much narrower the back of the stack has to be than the front of
         * it, in this scene's pixels.
         *
         * The taper is 12% of a 72dp pill per step, over two steps, at density
         * two: 35px if it is working. The old 7% managed ten. Twenty-four sits
         * between them with room on both sides for antialiasing and for the
         * nearest pill being sampled part-way up its cap.
         */
        const val TaperStep = 24

        /**
         * How much lighter than the pill's own body a rim has to be to count.
         *
         * The rim measures about 70 against a body of 18 here. Twenty-five is
         * comfortably past the antialiasing on the pill's own outline and
         * nowhere near the rim itself.
         */
        const val RimContrast = 25

        /** How many rows of identical width count as a pill's straight side. */
        const val PlateauRows = 4

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

