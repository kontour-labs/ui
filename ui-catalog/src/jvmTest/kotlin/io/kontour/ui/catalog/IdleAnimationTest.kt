package io.kontour.ui.catalog

import io.kontour.ui.components.display.BranchProgress
import io.kontour.ui.components.display.BranchTimeline
import io.kontour.ui.components.display.ConnectorStyle
import io.kontour.ui.components.display.GaugeIndicator
import io.kontour.ui.components.display.Gauge
import io.kontour.ui.components.display.HorizontalTimeline
import io.kontour.ui.components.display.Timeline
import io.kontour.ui.components.display.TimelineItem
import io.kontour.ui.components.display.TimelineList
import io.kontour.ui.components.display.TimelineListScope
import io.kontour.ui.foundation.Text
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.display.LinearProgress
import io.kontour.ui.components.display.Skeleton
import io.kontour.ui.components.display.Spinner
import io.kontour.ui.components.display.StepProgress
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A screen that is not moving does not ask for frames.
 *
 * The gap this fills is a specific one, and the library had four of it. An
 * animation in Compose has two halves — registering it, and reading what it
 * produces — and only the second one is visible. `rememberInfiniteTransition`
 * subscribes to the frame clock the moment it is composed and goes on asking for
 * frames forever; whether anything reads the value only decides whether the
 * picture changes.
 *
 * So a component can gate the read, draw a perfectly still image, pass every
 * golden, and still drive the frame clock at sixty hertz. Round 22 added exactly
 * that gate to `LinearProgress` and `StepProgress` — `val phase = if (animating)
 * sweep else 0f` — and left both transitions registered.
 *
 * Nothing in this repository could see it. A golden photographs the picture,
 * which is right. A phase count counts recompositions, and there are none — the
 * value is read in a draw scope. A stopwatch measures a frame that is genuinely
 * cheap. The cost is not in any of those places: it is one thread being woken
 * sixty times a second, which on JVM is a warm core and on Wasm is the frame
 * budget for everything else on the page.
 *
 * `ComposeScene.hasInvalidations` is the one thing that can tell, so this is the
 * test that asserts on it. Confirmed from outside as well: a browser on the
 * published site, with `prefers-reduced-motion: reduce` emulated, counted 44
 * animation frames in three seconds on the skeleton page and 70 on the progress
 * page — identical to the same pages without it.
 */
class IdleAnimationTest {

    @Test
    fun aDeterminateProgressBarIsNotAnAnimation() = assertSettles("LinearProgress(progress = 0.6f)") {
        LinearProgress(progress = 0.6f, modifier = Modifier.width(200.dp))
    }

    /**
     * A gauge that has reached its reading asks for nothing. Its value is read in
     * draw from an `Animatable` that is only ever moved by a new reading.
     */
    @Test
    fun aGaugeAtItsReadingIsNotAnAnimation() = assertSettles("Gauge(value = 0.65f)") {
        Gauge(value = 0.65f, indicator = GaugeIndicator.Needle, majorTicks = 5)
    }

    @Test
    fun aSettledStepRowIsNotAnAnimation() = assertSettles("StepProgress at rest") {
        StepProgress(current = 2, total = 4, working = false, modifier = Modifier.width(200.dp))
    }

    @Test
    fun reducedMotionStopsTheIndeterminateSweep() =
        assertSettles("LinearProgress(progress = null) under reduced motion", reduceMotion = true) {
            LinearProgress(progress = null, modifier = Modifier.width(200.dp))
        }

    @Test
    fun reducedMotionStopsTheSkeletonWipe() =
        assertSettles("Skeleton under reduced motion", reduceMotion = true) {
            Skeleton(Modifier.width(200.dp).height(24.dp))
        }

    /**
     * The one that must **not** settle, and the reason this file asserts both ways.
     *
     * A test suite that only checks things stop is one `if (false)` away from
     * passing on a library that never animates at all. An indeterminate bar with
     * motion allowed is the case the gates above have to leave alone.
     */
    @Test
    fun anIndeterminateProgressBarStillAnimates() {
        Scene(width = 400, height = 200, density = 2f) {
            Box(Modifier.fillMaxSize()) { LinearProgress(progress = null, modifier = Modifier.width(200.dp)) }
        }.use { scene ->
            scene.advance(SettleFrames)
            assertTrue(
                scene.stillAnimating(),
                "an indeterminate LinearProgress stopped asking for frames, so the sweep " +
                    "is not running. The gate that stops a determinate bar animating has " +
                    "caught the one case that is supposed to.",
            )
        }
    }

    /**
     * A spinner keeps turning under reduced motion, and that is the design.
     *
     * Its arc stops breathing — that is decoration — but the rotation is the
     * status: a spinner that does not move is not reporting anything. Pinned
     * here so that "reduced motion means nothing animates" cannot be applied to
     * it by someone reading the four tests above.
     */
    @Test
    fun aSpinnerKeepsTurningUnderReducedMotion() {
        Scene(width = 400, height = 200, density = 2f, reduceMotion = true) {
            Box(Modifier.fillMaxSize()) { Spinner() }
        }.use { scene ->
            scene.advance(SettleFrames)
            assertTrue(scene.stillAnimating(), "a spinner stopped turning under reduced motion")
        }
    }

    /**
     * A timeline nobody is travelling draws nothing that moves, in any of its
     * four forms: its rail's clock only runs while there is a pulse or a band.
     */
    @Test
    fun timelinesWithoutProgressAreNotAnimations() {
        assertSettles("Timeline without progress") { Timeline { stops() } }
        assertSettles("HorizontalTimeline without progress") { HorizontalTimeline { stops() } }
        assertSettles("TimelineList without progress") { TimelineList { listStops() } }
        assertSettles("BranchTimeline without progress") { history(progress = null) }
    }

    @Test
    fun reducedMotionStillsEveryTimelinesProgress() {
        assertSettles("Timeline at a stop, reduced motion", reduceMotion = true) { Timeline(progress = 1f) { stops() } }
        assertSettles("Timeline between stops, reduced motion", reduceMotion = true) { Timeline(progress = 0.5f) { stops() } }
        assertSettles("TimelineList at a stop, reduced motion", reduceMotion = true) { TimelineList(progress = 1f) { listStops() } }
        assertSettles("TimelineList between stops, reduced motion", reduceMotion = true) {
            TimelineList(progress = 0.5f) { listStops() }
        }
        assertSettles("BranchTimeline in progress, reduced motion", reduceMotion = true) {
            history(BranchProgress(reached = "a", towards = "b"))
        }
    }

    /** The other way round: a stop the journey is at pulses, for as long as it is there. */
    @Test
    fun aTimelineListAtAStopStillPulses() = assertAnimates("TimelineList at a stop") {
        TimelineList(progress = 1f) { listStops() }
    }

    /**
     * A plain Timeline finds out where its items are only once it has laid them
     * out, so this is the one that proves the band is started from there.
     */
    @Test
    fun aTimelineBetweenStopsStillCarriesItsBand() = assertAnimates("Timeline between stops") {
        Timeline(progress = 0.5f) { stops() }
    }

    @Test
    fun aBranchTimelineMovingOnStillAnimates() = assertAnimates("BranchTimeline moving on") {
        history(BranchProgress(reached = "a", towards = "b"))
    }

    @Composable
    private fun stops() {
        TimelineItem { Text("Perth") }
        TimelineItem { Text("Elizabeth Quay") }
        TimelineItem(connector = ConnectorStyle.None) { Text("Busport") }
    }

    private fun TimelineListScope.listStops() {
        item("Perth")
        item("Elizabeth Quay")
        item("Busport")
    }

    @Composable
    private fun history(progress: BranchProgress?) {
        BranchTimeline(
            items = listOf("b" to listOf("a"), "a" to emptyList()),
            id = { it.first },
            parents = { it.second },
            progress = progress,
        ) { commit -> item { +commit.first } }
    }

    private fun assertAnimates(what: String, content: @Composable () -> Unit) {
        Scene(width = 400, height = 300, density = 2f) {
            Box(Modifier.fillMaxSize()) { content() }
        }.use { scene ->
            scene.advance(SettleFrames)
            assertTrue(scene.stillAnimating(), "$what stopped asking for frames, so its progress is not moving")
        }
    }

    private fun assertSettles(
        what: String,
        reduceMotion: Boolean = false,
        content: @Composable () -> Unit,
    ) {
        Scene(width = 400, height = 200, density = 2f, reduceMotion = reduceMotion) {
            Box(Modifier.fillMaxSize()) { content() }
        }.use { scene ->
            scene.advance(SettleFrames)
            assertFalse(
                scene.stillAnimating(),
                "$what was still asking for frames after $SettleFrames of them, with " +
                    "nothing on screen able to change. Something registered an infinite " +
                    "transition it does not read — gating the read leaves the whole cost, " +
                    "so the gate has to go around `rememberInfiniteTransition` itself.",
            )
        }
    }

    private companion object {
        /** Past any entry animation, and past the value animator a progress bar runs. */
        const val SettleFrames = 90
    }
}
