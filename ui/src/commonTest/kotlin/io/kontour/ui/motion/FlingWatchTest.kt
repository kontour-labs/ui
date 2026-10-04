package io.kontour.ui.motion

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TestTimeSource
import kotlinx.coroutines.test.runTest

/** What [FlingWatch] makes of the scrolls a throw passes up, and for how long. */
class FlingWatchTest {

    @Test
    fun aThrowsFrameSaysWhichWayItWasGoing() {
        val watch = FlingWatch(TestTimeSource())
        watch.onPostScroll(Offset(0f, -12f), Offset.Zero, NestedScrollSource.SideEffect)
        assertEquals(Orientation.Vertical, watch.takeAtPress())
        assertNull(watch.takeAtPress(), "read once: a throw a press caught stops, and says nothing more")
    }

    @Test
    fun aFingersOwnScrollIsNotAThrow() {
        val watch = FlingWatch(TestTimeSource())
        watch.onPostScroll(Offset(0f, -12f), Offset.Zero, NestedScrollSource.UserInput)
        assertNull(watch.takeAtPress())
    }

    @Test
    fun sidewaysIsHorizontal() {
        val watch = FlingWatch(TestTimeSource())
        watch.onPostScroll(Offset(30f, 2f), Offset.Zero, NestedScrollSource.SideEffect)
        assertEquals(Orientation.Horizontal, watch.takeAtPress())
    }

    @Test
    fun aThrowThatCameToRestIsForgotten() = runTest {
        val watch = FlingWatch(TestTimeSource())
        watch.onPostScroll(Offset(0f, -1f), Offset.Zero, NestedScrollSource.SideEffect)
        watch.onPostFling(Velocity(0f, -800f), Velocity.Zero)
        assertNull(watch.takeAtPress())
    }

    @Test
    fun aThrowIntoTheEndIsKeptWhileItBounces() = runTest {
        val clock = TestTimeSource()
        val watch = FlingWatch(clock)
        watch.onPostScroll(Offset(0f, -1f), Offset.Zero, NestedScrollSource.SideEffect)
        watch.onPostFling(Velocity(0f, -800f), Velocity(0f, -1200f))
        clock += 400.milliseconds
        assertEquals(Orientation.Vertical, watch.takeAtPress(), "still bouncing back from the end")

        watch.onPostFling(Velocity(0f, -800f), Velocity(0f, -1200f))
        clock += BounceWindow + 1.milliseconds
        assertNull(watch.takeAtPress(), "a bounce that has had its time has settled")
    }
}
