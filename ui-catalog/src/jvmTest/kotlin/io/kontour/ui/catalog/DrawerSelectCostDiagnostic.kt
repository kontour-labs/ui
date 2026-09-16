package io.kontour.ui.catalog

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.kontour.ui.adaptive.Scaffold
import io.kontour.ui.motion.Transitions
import io.kontour.ui.nav.ModalNavDrawer
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Where the half second between tapping a drawer item and the drawer moving goes.
 *
 * Reported from the gallery on a phone: tapping a destination in the modal nav
 * drawer "just does nothing for half a second" and only then animates. There is
 * no page transition in the catalog at all — `pages[selected].content` swaps
 * outright — so "animates to the next screen" can only be the drawer sliding
 * away, and the dead time is therefore *before* its first frame.
 *
 * The drawer's own `onClick` does two things at once:
 *
 * ```kotlin
 * onSelectedChange(index)   // the page behind the drawer becomes a different page
 * drawerOpen = false        // and the drawer starts to leave
 * ```
 *
 * Both land in the same snapshot, so the frame that starts the exit animation is
 * also the frame that composes and measures a whole destination for the first
 * time. If that is the cost, the exit cannot start until it is paid, and what a
 * reader sees is a tap that did nothing followed by an animation at full speed —
 * which is the report, exactly.
 *
 * ### The arms
 *
 * | arm | what changes on the measured frame | a slow arm means |
 * |---|---|---|
 * | `both` | the page **and** the drawer, together | nothing yet; the first reported case |
 * | `drawer` | the drawer only, page left alone | the exit animation is itself expensive |
 * | `page` | the page only, drawer already shut | composing a destination is the cost |
 * | `deferred` | the drawer, then the page one frame later — today | the slack is not enough |
 * | `crossfade` | `deferred`, with the two destinations faded between | a fade is not affordable |
 *
 * `drawer` is the control. If `both` is slow and `drawer` is fast, the dead time
 * is the page, and the fix is about *when* the swap happens rather than about
 * making a destination cheaper.
 *
 * ### What it found
 *
 * ```
 *   arm          f1     f2     f3     f4     f5     f6     f7     f8
 *   both       78.6   44.9   24.4   26.3   28.8   28.8   29.6   28.4
 *   drawer     17.7   17.0   14.9   17.0   17.3   19.1   18.1   18.2
 *   page       54.8    9.1    5.7    5.4    5.5    5.5    5.6    6.2
 *   deferred   16.8   60.9   40.8   24.7   25.4   32.2   29.0   24.8
 *   crossfade  18.9   75.3   38.6   43.3   39.8   39.8   42.8   39.3
 * ```
 *
 * The page, and it is a **first-frame** cost: composing a destination is 54.8ms
 * once and 5.5ms for ever after. So the fix is about which frame pays it.
 *
 * `both` pays it on the frame the finger lifts, which is a tap that does
 * nothing. Holding the choice until the drawer's content left composition cured
 * that and cost 150ms before anything changed, which was reported in turn as the
 * screen only changing after the drawer had gone. `deferred` gives the exit its
 * first frame alone and lets the destination compose on the second: the tap is
 * answered in 16.8ms and the expensive frame lands inside a moving animation,
 * where a drop is far less legible than a stall before one.
 *
 * **`crossfade` is the arm that answers a question rather than proposing a fix.**
 * Fading between the two destinations looked affordable on the arithmetic —
 * `page` settles at 5.5ms — and is not: two `LazyColumn`s, two sets of per-card
 * overlay hosts and two compositing layers carrying the scale and the alpha come
 * to 37-46ms a frame for the whole length of the fade, against 22-29 for the
 * cut. The settled cost of a destination is not the cost of drawing one through
 * a layer. Kept as an arm so the number is on record and nobody has to try it
 * twice.
 *
 * A discarded warm-up runs first, for the reason `FirstOpenCostDiagnostic`
 * records: the first measurement in a JVM measures the JIT.
 *
 * ### A diagnostic, not a gate
 *
 * The assertion is a catastrophe bound. What this is for is the table it prints.
 */
@OptIn(ExperimentalTestApi::class)
class DrawerSelectCostDiagnostic {

    @Test
    fun whatTappingADestinationCosts() {
        // Thrown away: the JIT pass.
        frameCosts(changePage = true, closeDrawer = true)

        val both = frameCosts(changePage = true, closeDrawer = true)
        val drawer = frameCosts(changePage = false, closeDrawer = true)
        val page = frameCosts(changePage = true, closeDrawer = false, openDrawer = false)
        val deferred = frameCosts(changePage = true, closeDrawer = true, deferPage = true)
        val crossfade = frameCosts(
            changePage = true,
            closeDrawer = true,
            deferPage = true,
            crossFade = true,
        )

        println("tapping a destination in the modal drawer, ms per frame")
        println("  arm      " + (1..Frames).joinToString(" ") { "f$it".padStart(6) })
        val arms = listOf(
            "both" to both,
            "drawer" to drawer,
            "page" to page,
            "deferred" to deferred,
            "crossfade" to crossfade,
        )
        for ((name, costs) in arms) {
            println("  ${name.padEnd(9)}" + costs.joinToString(" ") { it.ms().padStart(6) })
        }
        println()
        println("  first frame — both ${both.first().ms()}, drawer ${drawer.first().ms()}, page ${page.first().ms()}")
        println("  total       — both ${both.sum().ms()}, drawer ${drawer.sum().ms()}, page ${page.sum().ms()}")

        assertTrue(
            both.sum() < CatastropheNanos,
            "tapping a destination took ${both.sum().ms()}ms of wall clock across " +
                "$Frames frames, which is past anything this is trying to tell " +
                "apart — read the table above rather than this line",
        )
    }

    /**
     * Wall clock per frame for the frames after the tap, in a scene of its own.
     *
     * A fresh scene per arm so the destination being switched to has never been
     * composed in it, which is the case the report is about. Glyph and vector
     * caches are process-wide and stay warm across scenes, so what differs
     * between the arms is composition and layout rather than the caches.
     */
    private fun frameCosts(
        changePage: Boolean,
        closeDrawer: Boolean,
        openDrawer: Boolean = true,
        deferPage: Boolean = false,
        crossFade: Boolean = false,
    ): List<Long> {
        var selected by mutableIntStateOf(Origin)
        var drawerOpen by mutableStateOf(false)
        val costs = mutableListOf<Long>()

        runDesktopComposeUiTest(width = Width, height = Height) {
            mainClock.autoAdvance = false
            setContent {
                KontourTheme {
                    OverlayHost(Modifier.fillMaxSize()) {
                        Scaffold {
                            Box(Modifier.fillMaxSize().padding(it)) {
                                if (crossFade) {
                                    val spec = Transitions.fadeThrough(fast = true)
                                    AnimatedContent(
                                        targetState = selected,
                                        transitionSpec = { spec },
                                        modifier = Modifier.fillMaxSize(),
                                        label = "destination",
                                    ) { page ->
                                        pages[page].content(Modifier.fillMaxWidth())
                                    }
                                } else {
                                    pages[selected].content(Modifier.fillMaxWidth())
                                }
                            }
                        }
                        ModalNavDrawer(
                            visible = drawerOpen,
                            onDismissRequest = { drawerOpen = false },
                        ) {
                            pages.forEachIndexed { index, page ->
                                item(page.title, page.icon, selected = index == selected) {}
                            }
                        }
                    }
                }
            }
            settle()

            if (openDrawer) {
                drawerOpen = true
                settle()
            }

            // The tap. Originally one snapshot carrying both, which is what put
            // the destination's first frame on the frame that starts the exit.
            if (changePage && !deferPage) selected = Target
            if (closeDrawer) drawerOpen = false

            repeat(Frames) { index ->
                val started = System.nanoTime()
                mainClock.advanceTimeByFrame()
                waitForIdle()
                costs += System.nanoTime() - started
                // One frame of slack, which is what `withFrameNanos` buys the
                // app: the exit gets its first frame to itself and the
                // destination composes behind it on the second.
                if (changePage && deferPage && index == 0) selected = Target
            }
        }
        return costs
    }

    private fun androidx.compose.ui.test.ComposeUiTest.settle() {
        repeat(SettleFrames) { mainClock.advanceTimeByFrame() }
        waitForIdle()
    }

    private fun Long.ms(): String = "${this / 1_000_000}.${(this / 100_000) % 10}"

    private companion object {
        /** A phone, which is the only width the catalog draws a modal drawer at. */
        const val Width = 420
        const val Height = 900

        /** "About" — a plain column, and where the gallery starts. */
        const val Origin = 0

        /** A generated family page: a `LazyColumn` of demo cards. */
        const val Target = 2

        const val Frames = 12
        const val SettleFrames = 40
        const val CatastropheNanos = 30_000_000_000L
    }
}
