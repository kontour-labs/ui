package io.kontour.ui.motion

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.Text
import io.kontour.ui.theme.KontourTheme
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TestTimeSource

/**
 * A list thrown by a finger the platform reads as still is thrown anyway, at the
 * finger's own speed — and only then. See [FingerTrail].
 */
@OptIn(ExperimentalTestApi::class)
class RestedFlickTest {

    /** A thumb laid on the list, rested, then flicked: UIKit's tracker calls that no throw. */
    @Test
    fun aRestThenAFlickIsReadOffTheTrail() {
        val trail = FingerTrail(TestTimeSource())
        trail.down(0)
        trail.move(80, Offset(0f, -30f))
        trail.move(88, Offset(0f, -30f))
        trail.move(96, Offset(0f, -30f))
        trail.move(104, Offset(0f, -30f))
        trail.lift(110)
        val thrown = trail.throwAtLift()
        assertTrue(thrown != null && thrown.y < -2500f, "four moves of 30px 8ms apart after a rest read as $thrown")
    }

    @Test
    fun aFingerThatStoppedBeforeLiftingThrewNothing() {
        val trail = FingerTrail(TestTimeSource())
        trail.down(0)
        trail.move(16, Offset(0f, -60f))
        trail.move(32, Offset(0f, -60f))
        trail.lift(120)
        assertNull(trail.throwAtLift())
    }

    /** Read straight after the lift, or not at all: a later read is about some other gesture. */
    @Test
    fun aLiftLongGoneIsNotReadAgain() {
        val clock = TestTimeSource()
        val trail = FingerTrail(clock)
        trail.down(0)
        trail.move(16, Offset(0f, -60f))
        trail.move(32, Offset(0f, -60f))
        trail.lift(40)
        clock += 400.milliseconds
        assertNull(trail.throwAtLift())
    }

    /** A gesture the trail does not follow — a mouse's — leaves nothing to read. */
    @Test
    fun aClearedTrailHasNothingToRead() {
        val trail = FingerTrail(TestTimeSource())
        trail.down(0)
        trail.move(16, Offset(0f, -60f))
        trail.move(32, Offset(0f, -60f))
        trail.lift(40)
        trail.clear()
        assertNull(trail.throwAtLift())
    }

    /**
     * Only a release reported as exactly nothing is touched, and only along the
     * axis the gesture scrolled on.
     */
    @Test
    fun onlyANothingIsAnsweredAndOnlyAlongTheAxisItScrolled() = runTest {
        fun thrownDiagonally(): FingerTrail = FingerTrail(TestTimeSource()).apply {
            down(0)
            move(16, Offset(20f, -60f))
            move(32, Offset(20f, -60f))
            lift(36)
        }

        val reported = RestedFlicks(thrownDiagonally())
        reported.onPreScroll(Offset(0f, -60f), NestedScrollSource.UserInput)
        assertEquals(Velocity.Zero, reported.onPreFling(Velocity(0f, -900f)), "a release the platform read was changed")

        val vertical = RestedFlicks(thrownDiagonally())
        vertical.onPreScroll(Offset(0f, -60f), NestedScrollSource.UserInput)
        val handed = -vertical.onPreFling(Velocity.Zero)
        assertTrue(handed.x == 0f && handed.y < -1000f, "a vertical scroll was handed $handed")

        val unscrolled = RestedFlicks(thrownDiagonally())
        assertEquals(Velocity.Zero, unscrolled.onPreFling(Velocity.Zero), "a fling with no scroll before it was answered")
    }

    /**
     * End to end, on a real list, against the same list without it.
     *
     * The JVM's tracker is not UIKit's, but it has the other half of the same
     * rule — a gap of more than 40ms between the last two touches reads as a
     * finger that had stopped — so a late frame just before a lift in place is a
     * throw it reports as nothing. The control proves it does: its list stops
     * where the finger left it.
     */
    @Test
    fun aListThrownThroughALateFrameCarriesOn() {
        val caught = travel(catching = true)
        val lost = travel(catching = false)
        assertTrue(
            caught > lost + 40,
            "caught, the list came to rest at $caught; lost, at $lost — the throw was not carried",
        )
    }

    private fun travel(catching: Boolean): Int {
        var travelled = 0
        runComposeUiTest {
            lateinit var list: LazyListState
            setContent {
                KontourTheme(darkTheme = false, reduceMotion = false) {
                    list = rememberLazyListState()
                    Box(
                        Modifier
                            .fillMaxSize()
                            .then(if (catching) Modifier.catchRestedFlicks(FingerTrail(TestTimeSource())) else Modifier)
                    ) {
                        LazyColumn(state = list, modifier = Modifier.fillMaxSize().testTag("list")) {
                            items(400) { Text("Row $it", modifier = Modifier.height(40.dp)) }
                        }
                    }
                }
            }
            onNodeWithTag("list").performTouchInput {
                down(center)
                repeat(6) { moveBy(Offset(0f, -30f)) }
                // A late frame, and the finger still moving through it.
                advanceEventTime(60)
                moveBy(Offset(0f, -30f))
                up()
            }
            waitForIdle()
            val row = with(density) { 40.dp.roundToPx() }
            travelled = list.firstVisibleItemIndex * row + list.firstVisibleItemScrollOffset
        }
        return travelled
    }
}
