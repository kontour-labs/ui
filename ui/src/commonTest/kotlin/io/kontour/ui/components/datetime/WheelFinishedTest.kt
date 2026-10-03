package io.kontour.ui.components.datetime

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A wheel says when it has come to rest, once, as well as every row it passes.
 *
 * Asked for from the gallery's transit settings: *"you've got it toasting
 * every time the "home stop" changes, which becomes annoying if i scroll
 * through them fast. can we make it only update when the picker comes to
 * rest? that might involve adding some more callbacks for these sorts of
 * components."* The rows a flick passes are what the wheel shows; the row it
 * stops on is what should happen.
 */
@OptIn(ExperimentalTestApi::class)
class WheelFinishedTest {

    private class Counts {
        var index by mutableIntStateOf(0)
        val changes = mutableListOf<Int>()
        var finished = 0
    }

    private fun ComposeUiTest.wheel(infinite: Boolean = false): Counts {
        val counts = Counts()
        setContent {
            KontourTheme(reduceMotion = true) {
                WheelPicker(
                    items = (0 until 40).toList(),
                    selectedIndex = counts.index,
                    onSelectedIndexChange = {
                        counts.index = it
                        counts.changes += it
                    },
                    itemLabel = { "Row $it" },
                    modifier = Modifier.testTag("wheel"),
                    infinite = infinite,
                    onSelectedIndexChangeFinished = { counts.finished++ },
                )
            }
        }
        waitForIdle()
        return counts
    }

    private fun ComposeUiTest.flick() {
        onNodeWithTag("wheel").performTouchInput { swipeUp(durationMillis = 120) }
        waitForIdle()
    }

    @Test
    fun aFlickPassesRowsAndFinishesOnce() = runComposeUiTest {
        val counts = wheel()
        flick()
        assertTrue(counts.changes.distinct().size > 2, "the flick passed ${counts.changes.distinct()}, too few rows to show anything")
        assertEquals(1, counts.finished, "a flick that passed ${counts.changes.distinct().size} rows finished ${counts.finished} times")
    }

    @Test
    fun anEndlessWheelFinishesOnceToo() = runComposeUiTest {
        val counts = wheel(infinite = true)
        flick()
        assertTrue(counts.changes.distinct().size > 2, "the flick passed ${counts.changes.distinct()}")
        assertEquals(1, counts.finished, "an endless wheel finished ${counts.finished} times for one flick")
    }

    @Test
    fun theCallerSettingTheValueIsNotTheReaderFinishing() = runComposeUiTest {
        val finite = wheel()
        runOnUiThread { finite.index = 12 }
        waitForIdle()
        assertEquals(0, finite.finished, "the caller moving the wheel was reported as the reader finishing")
    }

    @Test
    fun theCallerTurningAnEndlessWheelIsNotTheReaderFinishing() = runComposeUiTest {
        val endless = wheel(infinite = true)
        runOnUiThread { endless.index = 12 }
        waitForIdle()
        assertEquals(0, endless.finished, "the caller turning an endless wheel was reported as the reader finishing")
    }
}
