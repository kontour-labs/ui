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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.overlay.ToastHost
import io.kontour.ui.overlay.ToastHostState
import io.kontour.ui.overlay.ToastPosition
import io.kontour.ui.overlay.ToastTone
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.test.Test
import kotlin.time.TimeSource
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
     * A toast promoted to the front with little left on its clock is topped up.
     *
     * Reported as a toast that flashed rather than arrived. A toast behind the
     * front one counts the whole time it is waiting — deliberate, and
     * `aToastBehindTheVisibleWindowStillRunsItsClock` is the test for it — but it
     * is only being *read* at the front. So one that reaches the front with a few
     * hundred milliseconds left is promoted and gone in the same breath.
     *
     * ### Measured as two emptying times, not one
     *
     * `delay` is the wall clock and this harness renders a 16ms frame in about
     * 45ms of real time, so an absolute deadline would be a race on a throttled
     * container. Both figures below come out of the same harness on the same
     * machine, and it is their *difference* that is the claim.
     *
     * ### Why the durations are 5,000 and 3,400 and not 4,000 and 3,800
     *
     * The first version of this test used a 200ms gap, and it failed reporting
     * 4,006ms against 3,994ms — no top-up at all. The gap was the fault, not the
     * fix. **Promotion happens when the toast in front is *removed*, not when it
     * expires**, and removal is the far end of an exit animation: about 200ms of
     * frame time, which is a dozen frames, which is over half a second of real
     * time here. The toast behind expired before it ever reached the front.
     *
     * That is the whole reason the numbers are what they are, and it is also why
     * the floor makes the result *insensitive* to that lag: whatever remains at
     * promotion, the answer is the floor. It would take an exit lasting more than
     * 1,600ms to put this back in a race.
     *
     * ### What the two bounds separate
     *
     * The 3,400ms toast is removed at about 4,000ms, so the 5,000ms one is
     * promoted with roughly 1,000ms left and three fifths of 5,000 is 3,000. A
     * **restart** — the other behaviour on the table, and the one this host was
     * rewritten to stop being — would hand it a fresh 5,000 and empty at about
     * `alone + 4,000`, with a stack of four taking four full durations to clear.
     *
     * Measured, not predicted. Against the fix the two runs are 6,710ms and
     * 4,838ms, a difference of **1,872ms**; with the top-up disabled they are
     * 4,992ms and 4,821ms, a difference of **171ms**. The bounds are set either
     * side of that, and clear of the 4,000 a restart would produce.
     */
    @Test
    fun aToastPromotedWithLittleTimeLeftIsToppedUpToAFloor() {
        val alone = emptiesAfter("one 5,000ms toast, never promoted") { toasts ->
            toasts.show("Saved for offline", durationMillis = 5_000)
        }
        val promoted = emptiesAfter("a 5,000ms toast behind a 3,400ms one") { toasts ->
            toasts.show("Saved for offline", durationMillis = 5_000)
            toasts.show("Route updated", durationMillis = 3_400)
        }

        assertTrue(
            promoted > alone + 1_000,
            "the stack emptied ${promoted}ms after it appeared with the 5,000ms " +
                "toast waiting behind a 3,400ms one, against ${alone}ms for that " +
                "same toast on its own — a difference of ${promoted - alone}ms, so " +
                "the promoted one was not topped up at all. It reached the front " +
                "with about a second left and went almost immediately",
        )
        assertTrue(
            promoted < alone + 3_000,
            "the stack emptied ${promoted}ms after it appeared against ${alone}ms " +
                "for the toast alone — a difference of ${promoted - alone}ms, near " +
                "the 4,000 a full restart would give rather than the 2,000 a floor " +
                "of three fifths does. It is being restarted on promotion, which " +
                "makes a stack of four take four durations to clear",
        )
    }

    /**
     * How long the stack took to empty, from the frame it first had ink in.
     *
     * Real milliseconds, because the clock a toast expires on is the real one —
     * see `eachToastKeepsItsOwnClock`. The wait for ink first is load-bearing:
     * `show` runs in a `LaunchedEffect`, so the opening frames have an empty
     * stack and "empty" would be satisfied before anything had been shown.
     */
    private fun emptiesAfter(what: String, shown: (ToastHostState) -> Unit): Long {
        var elapsed = 0L
        var emptied: BufferedImage? = null
        Scene(width = 600, height = 400) {
            val toasts = remember { ToastHostState() }
            OverlayHost(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().background(Color.White))
                ToastHost(toasts)
                LaunchedEffect(Unit) { shown(toasts) }
            }
        }.use { scene ->
            val appeared = scene.renderUntil(timeoutMillis = 3_000) { it.stackHeight() > 0 }
            assertNotNull(appeared, "no toast was ever drawn for $what")
            val mark = TimeSource.Monotonic.markNow()
            emptied = scene.renderUntil(timeoutMillis = 20_000) { it.stackHeight() == 0 }
            elapsed = mark.elapsedNow().inWholeMilliseconds
        }
        assertNotNull(emptied, "the stack never emptied within 20s for $what")
        return elapsed
    }

    /**
     * A pill behind the card shrinks and fades away. It does not just stop being
     * there.
     *
     * The report, near enough word for word. Every toast shared one exit —
     * `slideOutVertically` toward the anchored edge — and for the front card that
     * is the right one, because it is the only thing there and the edge it leaves
     * for is the edge it arrived from. For a pill it is not a direction at all:
     * sliding toward the edge takes it *under* the card in front, so the last
     * frame it is drawn on is a full-size one and the next frame it is gone.
     *
     * ### The measurement is the width of the band above the card, frame by frame
     *
     * Only [io.kontour.ui.overlay.ToastDefaults.Peek] of a pill ever shows — the
     * offset is measured from the front card's edge precisely so that stays true
     * whatever height the card is — so that band is the whole of what a user sees
     * a pill do. Rendered on the frame clock, so the sequence is reproducible
     * rather than a wall-clock race.
     *
     * Sliding, it reads `130, 130, 130, 0, …`: full width, full width, gone. Not
     * one intermediate value, which is exactly what "vanishes" means when you
     * write it down. Shrinking, it reads `130, 130, 130, 100, 82, 0` — 77% then
     * 63% of the width it was, on its way out.
     *
     * The assertion is therefore the thing the two do differently: is there any
     * frame at all on which the pill is drawn *and* narrower than it was?
     */
    @Test
    fun aPillLeavesByShrinking() {
        val (resting, widths) = pillExitWidths()
        val shrunk = widths.filter { it > 0 && it <= resting * 3 / 4 }

        assertTrue(
            shrunk.isNotEmpty(),
            "the band above the front card measured $widths across the pill's " +
                "exit, against ${resting}px at rest — it went from full width " +
                "straight to nothing without ever being drawn smaller. That is " +
                "the pill sliding out under the card rather than shrinking away.",
        )
    }

    /**
     * The front card still leaves the way it did. The control for the test above.
     *
     * A pill's exit is the one that changed. Giving every toast the shrink would
     * have been the easier edit and the wrong one: the front card has an edge to
     * leave by, and a card that shrinks in place reads as being taken back rather
     * than dismissed.
     */
    @Test
    fun theFrontCardStillSlidesOut() {
        val (resting, widths) = cardExitWidths()
        val shrunk = widths.filter { it > 0 && it <= resting * 3 / 4 }

        assertTrue(
            shrunk.isEmpty(),
            "the front card measured $widths across its exit against ${resting}px " +
                "at rest, so it was drawn at ${shrunk} on the way out — it is " +
                "shrinking in place. Only the pills behind it should do that.",
        )
    }

    /**
     * The widest row of the band above the front card, once per frame, while the
     * pill behind it leaves. Paired with the width that band rests at.
     */
    private fun pillExitWidths(): Pair<Int, List<Int>> {
        var go by mutableStateOf(false)
        var pillId = 0L
        var resting = 0
        val widths = mutableListOf<Int>()

        Scene(width = 600, height = SceneHeight) {
            val toasts = remember { ToastHostState() }
            OverlayHost(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().background(Color.White))
                ToastHost(toasts)
                LaunchedEffect(Unit) {
                    // Pinned, both of them, so nothing expires under the
                    // measurement and the only thing that moves is the one this
                    // dismisses.
                    pillId = toasts.show("Saved", durationMillis = 0)
                    toasts.show("Couldn't reach the timetable service", durationMillis = 0)
                }
                LaunchedEffect(go) { if (go) toasts.dismiss(pillId) }
            }
        }.use { scene ->
            val settled = scene.frames(60)
            // Fixed from the settled frame and then held. The card does not move
            // while the pill leaves, so this row stays the boundary — and taking
            // it fresh each frame would follow the pill down instead of holding
            // still while it goes.
            val cardTop = settled.cardTop(settled.widthProfile())
            val rows = 0 until cardTop
            resting = rows.maxOf { settled.leavingWidthAtRow(it) }
            go = true
            repeat(ExitFrames) {
                val image = scene.frame()
                widths += rows.maxOf { image.leavingWidthAtRow(it) }
            }
        }
        return resting to widths
    }

    /**
     * The same, for a lone toast's own exit — which is a front card, at depth
     * zero, with nothing behind it.
     *
     * One toast rather than two on purpose. With a pill behind it the frame stops
     * being a measurement of the card: the pill is 134px against the card's 477
     * and, worse, it is *promoted* the moment the card goes and grows toward full
     * size in the frames after. The first version of this control read
     * `477, 477, 477, 134, 134, …` and called the pill a shrinking card.
     */
    private fun cardExitWidths(): Pair<Int, List<Int>> {
        var go by mutableStateOf(false)
        var cardId = 0L
        var resting = 0
        val widths = mutableListOf<Int>()

        Scene(width = 600, height = SceneHeight) {
            val toasts = remember { ToastHostState() }
            OverlayHost(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().background(Color.White))
                ToastHost(toasts)
                LaunchedEffect(Unit) {
                    cardId = toasts.show("Couldn't reach the timetable service", durationMillis = 0)
                }
                LaunchedEffect(go) { if (go) toasts.dismiss(cardId) }
            }
        }.use { scene ->
            val settled = scene.frames(60)
            val rows = 0 until settled.height
            resting = rows.maxOf { settled.leavingWidthAtRow(it) }
            go = true
            repeat(ExitFrames) {
                val image = scene.frame()
                widths += rows.maxOf { image.leavingWidthAtRow(it) }
            }
        }
        return resting to widths
    }

    /**
     * How wide the run of *leaving* toast is on one row.
     *
     * `isSurface` is deliberately strict — dark pixels only — because everywhere
     * else in this file it is counting solid stacked cards against their own
     * shadows. A toast on its way out is fading, and under that threshold it
     * reports as gone two frames before it stops being drawn: the pill's real
     * `130, 130, 130, 100, 82, 0` came back as `128, 128, 128, 0, 0, 0`, which is
     * the very shape the test exists to distinguish from. So the exit is measured
     * against the ground instead — clearly darker than the white behind it,
     * whatever its alpha is down to.
     */
    private fun BufferedImage.leavingWidthAtRow(y: Int): Int {
        var left = width
        var right = -1
        for (x in 0 until width) {
            val rgb = getRGB(x, y)
            val mean = (((rgb shr 16) and 0xFF) + ((rgb shr 8) and 0xFF) + (rgb and 0xFF)) / 3
            if (mean >= LeavingGround) continue
            if (x < left) left = x
            if (x > right) right = x
        }
        return if (right < 0) 0 else right - left + 1
    }

    /**
     * A pill's exit lasts long enough to be seen.
     *
     * Reported twice. The first time the pill slid out under the cards in front
     * and was simply absent on the next frame; it shrinks and fades in place now,
     * and `aPillLeavesByShrinking` pins that. The second report was that there
     * was *still* no animation — and the shrink was running perfectly, over two
     * frames of a nine-frame tween.
     *
     * The cause was the easing, not the duration. `tweenFast` carries
     * `Motion.standard`, `cubic-bezier(0.16, 1, 0.3, 1)`: a hard ease-out that is
     * 75% done one frame in and 93% done after two. `Motion` already says so —
     * `tweenExit` exists because "run in reverse it makes an overlay drop most of
     * its opacity at once and then linger" — and the toast was not using it.
     *
     * ### Counted in frames the stack actually moves on
     *
     * A pill shows only `Peek` of itself, so the thing to count is not how far it
     * travels but on how many frames it travels *at all*. Against the arrival
     * easing the stack's top edge reads `562, 575, 581` and stops: two moves.
     * Against `tweenExit` it reads `562, 564, 565, 566, 568, 570, 573, 577, 581`
     * — nine, and accelerating, which is what a departure should do.
     */
    @Test
    fun aPillTakesLongEnoughLeavingToBeSeen() {
        val tops = pillExitTopEdges()
        val moved = tops.zipWithNext().count { (a, b) -> a != b }

        assertTrue(
            moved >= 5,
            "the top of the stack moved on only $moved frames while a pill left, " +
                "out of ${tops.size} sampled: $tops. The shrink is running and " +
                "finishing before anyone can see it — an arrival easing on a " +
                "departure, which puts nine frames of travel into the first two.",
        )
    }

    /**
     * Dragging a toast the way it does not go is a rubber band, not a slow drag.
     *
     * It used to scale every delta by a flat third, which bounds nothing: pull
     * far enough and the card leaves the screen in the direction it refuses to be
     * dismissed in. Each pixel of pull now moves it less than the last, easing up
     * to `RubberBand` of its own height and stopping.
     *
     * ### And the pills come too
     *
     * Reported alongside it: the stack came apart under a finger, because `swipe`
     * belonged to the card being dragged and every pill behind it stayed where it
     * was. It is owned by the stack now and every card reads it, so the top and
     * bottom of the stack move by the same amount — which is what the second
     * assertion here is.
     */
    @Test
    fun theWrongWayRubberBandsAndTakesTheStackWithIt() {
        val (topTravel, bottomTravel, series) = wrongWayTravel()

        assertTrue(
            bottomTravel in 5..40,
            "pulling the front card 240px the way it does not dismiss moved it " +
                "${bottomTravel}px, over $series. Under 5 it is not responding at " +
                "all, which reads as a dead control; over 40 it is not bounded.",
        )
        assertTrue(
            series.last() > series[3] + 2,
            "the card was ${series[3]}px along by the fourth step of the pull and " +
                "${series.last()}px by the twelfth: $series. That is a wall, not a " +
                "band. Damping each delta as it arrives always does this — the " +
                "damping depends on where the card already is, so it converges " +
                "within two or three events and is flat from there. A flat third " +
                "of every delta gives `10, 10, 10 …`; a linear ramp gives " +
                "`2, 19, 19, 19 …`. A band has to be a function of the whole pull.",
        )
        assertTrue(
            abs(topTravel - bottomTravel) <= 4,
            "the bottom of the stack travelled ${bottomTravel}px under the finger " +
                "and the top only ${topTravel}px. The pills are being left " +
                "behind: a stack is one object, and half of it following a drag " +
                "while the other half stays put says it is not.",
        )
    }

    /**
     * Clearing a toast by hand buys the ones behind it time.
     *
     * Reported from the far end of a deep stack: *"I tried to access one that was
     * a heap of a way down, and it disappeared as I was clearing the ones on
     * top."* Every toast counts down wherever it sits — deliberately, and
     * `aToastBehindTheVisibleWindowStillRunsItsClock` is the test for it — so
     * working through the ones in front spends the time of the one behind them.
     * The act of reaching for it is what takes it away.
     *
     * A clear now tops the others back up by `ClearedGrace`, capped at a full
     * lifetime. Expiring does **not**: routing both through the same path would
     * have every expiry extend every other toast, and a stack that never empties.
     * That is why `ToastHostState` has an internal `expire` beside its public
     * `dismiss`.
     *
     * Measured the way `aToastPromotedWithLittleTimeLeftIsToppedUpToAFloor` is,
     * and for the same reason: two emptying times off the same harness, because
     * an absolute deadline here would be a wall-clock race.
     */
    @Test
    fun clearingOneByHandBuysTheOthersTime() {
        val alone = emptiesAfter("one 4,000ms toast, nothing cleared") { toasts ->
            toasts.show("Saved for offline", durationMillis = 4_000)
        }
        val afterClearing = clearedAfter()

        assertTrue(
            afterClearing > alone + 800,
            "the 4,000ms toast emptied ${afterClearing}ms after it appeared with a " +
                "pinned one cleared off the top of it at 1,500ms, against " +
                "${alone}ms on its own — a difference of ${afterClearing - alone}ms. " +
                "Clearing the ones in front is still costing the one behind them " +
                "the time it takes to do the clearing.",
        )
        assertTrue(
            afterClearing < alone + 2_600,
            "the toast emptied ${afterClearing}ms against ${alone}ms alone, a " +
                "difference of ${afterClearing - alone}ms. The grace is capped at " +
                "a full lifetime — 1,500ms of waiting plus 4,000ms is 5,500 — so " +
                "anything past that is a clear resetting the clock rather than " +
                "topping it up, and a stack of ten would never empty.",
        )
    }

    /**
     * The stack's top edge, once per frame, while the deepest pill leaves.
     *
     * Four pinned toasts so nothing expires under the measurement, and the
     * deepest one dismissed by hand: the only thing that moves is the pill going.
     */
    private fun pillExitTopEdges(): List<Int> {
        var go by mutableStateOf(false)
        var deepest = 0L
        val tops = mutableListOf<Int>()

        // Three, in a tall scene. Four is the maximum the host draws and the
        // deepest of four is tapered down to almost nothing — its whole
        // contribution to the stack's top edge is one `Peek`, so it goes in a
        // single step whatever easing it leaves on, and the measurement says
        // nothing about the easing. Three is the shape a burst actually makes.
        Scene(width = 600, height = TallScene) {
            val toasts = remember { ToastHostState() }
            OverlayHost(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().background(Color.White))
                ToastHost(toasts)
                LaunchedEffect(Unit) {
                    repeat(3) {
                        val id = toasts.show("Toast number $it", durationMillis = 0)
                        if (it == 0) deepest = id
                    }
                }
                LaunchedEffect(go) { if (go) toasts.dismiss(deepest) }
            }
        }.use { scene ->
            scene.frames(60)
            go = true
            repeat(ExitFrames) { tops += scene.frame().stackTop() }
        }
        return tops
    }

    /**
     * How far the bottom and the top of the stack travel under a 240px drag the
     * way the front card does *not* dismiss.
     */
    private fun wrongWayTravel(): Triple<Int, Int, List<Int>> {
        var topTravel = 0
        var bottomTravel = 0
        val series = mutableListOf<Int>()

        Scene(width = 600, height = SceneHeight) {
            val toasts = remember { ToastHostState() }
            OverlayHost(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().background(Color.White))
                ToastHost(toasts)
                LaunchedEffect(Unit) {
                    repeat(4) { toasts.show("Toast number $it", durationMillis = 0) }
                }
            }
        }.use { scene ->
            val settled = scene.frames(60)
            val fromTop = settled.stackTop()
            val fromBottom = settled.stackBottom()
            // A bottom-anchored stack dismisses downward, so up is the way it
            // will not go. The press point is taken from the settled frame
            // rather than written down: the first version of this used a
            // coordinate copied from a taller scene and pressed 240px below the
            // window, which moves nothing and reads exactly like a control that
            // refuses to be dragged.
            val x = settled.width / 2f
            val y = fromBottom - 20f
            scene.press(Offset(x, y))
            for (step in 1..12) {
                scene.move(Offset(x, y - step * 20f))
                series += fromBottom - scene.frame().stackBottom()
            }
            val pulled = scene.frame()
            topTravel = fromTop - pulled.stackTop()
            bottomTravel = fromBottom - pulled.stackBottom()
            scene.release(Offset(x, y - 240f))
        }
        return Triple(topTravel, bottomTravel, series)
    }

    /**
     * How long the stack takes to empty when a pinned toast is cleared off the
     * top of a 4,000ms one, 1,500ms in.
     */
    private fun clearedAfter(): Long {
        var clear by mutableStateOf(false)
        var pinned = 0L
        var elapsed = 0L
        var emptied: BufferedImage? = null

        Scene(width = 600, height = 400) {
            val toasts = remember { ToastHostState() }
            OverlayHost(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().background(Color.White))
                ToastHost(toasts)
                LaunchedEffect(Unit) {
                    toasts.show("Saved for offline", durationMillis = 4_000)
                    // Pinned and in front, so it is only ever gone by hand.
                    pinned = toasts.show("Couldn't reach the timetable", durationMillis = 0)
                }
                LaunchedEffect(clear) { if (clear) toasts.dismiss(pinned) }
            }
        }.use { scene ->
            val appeared = scene.renderUntil(timeoutMillis = 3_000) { it.stackHeight() > 0 }
            assertNotNull(appeared, "no toast was drawn at all")
            val mark = TimeSource.Monotonic.markNow()
            scene.renderUntil(timeoutMillis = 1_500) { false }
            clear = true
            emptied = scene.renderUntil(timeoutMillis = 20_000) { it.stackHeight() == 0 }
            elapsed = mark.elapsedNow().inWholeMilliseconds
        }
        assertNotNull(emptied, "the stack never emptied")
        return elapsed
    }

    /**
     * The first row holding a toast, or the image height if none do.
     *
     * Against the ground rather than through `isSurface`, and the difference is
     * not cosmetic: `isSurface` is deliberately strict — dark pixels only,
     * because everywhere else in this file it is counting solid stacked cards
     * against their own shadows. A pill on its way out is *fading*, and under
     * that threshold it stops being seen two frames before it stops being drawn.
     * The first version of `aPillTakesLongEnoughLeavingToBeSeen` used it and
     * reported a single jump, `243 -> 262`, for the nine-frame travel this file
     * measures elsewhere. Same mistake `BackdropBlurTest` records; the same fix.
     */
    private fun BufferedImage.stackTop(): Int =
        (0 until height).firstOrNull { y -> rowHasToast(y) } ?: height

    /** The last such row, or zero. */
    private fun BufferedImage.stackBottom(): Int =
        (height - 1 downTo 0).firstOrNull { y -> rowHasToast(y) } ?: 0

    private fun BufferedImage.rowHasToast(y: Int): Boolean =
        (0 until width).any { x ->
            val rgb = getRGB(x, y)
            val mean = (((rgb shr 16) and 0xFF) + ((rgb shr 8) and 0xFF) + (rgb and 0xFF)) / 3
            mean < LeavingGround
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
         * How many frames of a toast's exit to sample.
         *
         * `Scene.frame` advances 16ms of frame time, and the exit is
         * `motion.tweenFast`, so a couple of dozen is several times over. The
         * tail is zeros either way and reads as such in a failure message.
         */
        const val ExitFrames = 24

        /** Tall enough for a three-deep stack to show every pill. */
        const val TallScene = 700

        /**
         * How dark a pixel has to be to count as a toast that is leaving.
         *
         * The ground under these scenes is white, and a toast is near-black, so
         * anything this side of mid-grey is the toast at some alpha and not the
         * soft shadow around it. See `leavingWidthAtRow`.
         */
        const val LeavingGround = 200

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

