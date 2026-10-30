package io.kontour.ui.overlay

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kontour.ui.adaptive.Scaffold
import io.kontour.ui.foundation.Text
import io.kontour.ui.sheet.ModalBottomSheet
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * An overlay's content is composed once as it opens, not twice.
 *
 * Every overlay's content used to be composed a second time, with nothing
 * skipped, on the third frame of its opening, in case its caller had changed it
 * in the frame it appeared: that change could be lost, and composing it all
 * again was how it was caught. For a sheet of 40 rows that was 296 recomposed
 * scopes on one frame of its slide, against 314 for its first composition, and
 * the frame took 5.4–10.6ms on a desktop JVM against 3.1ms without it: several
 * times that on a cheap phone, in the first frames of the motion. The content is
 * composed where it is first measured now, which the change cannot race, so
 * there is nothing to catch: see `ComposedOnMeasure` in `OverlayHost`, and
 * `DialogLiveContentTest` for the change that must still arrive.
 *
 * Counted by the rows themselves, each a composable that reports every time it
 * is composed, from a caller in a `Scaffold`'s body as a screen's is.
 */
@OptIn(ExperimentalTestApi::class)
class OverlayContentOnceTest {

    @Test
    fun aDialogsRowsAreComposedOnceAsItOpens() = runComposeUiTest {
        val counts = IntArray(Rows)
        open(counts) { visible, title, rows -> Dialog(visible = visible, onDismissRequest = {}) { Text(title); rows() } }
        assertOnce(counts, "a dialog")
    }

    @Test
    fun aSheetsRowsAreComposedOnceAsItOpens() = runComposeUiTest {
        val counts = IntArray(Rows)
        open(counts) { visible, title, rows -> ModalBottomSheet(visible = visible, onDismissRequest = {}) { Text(title); rows() } }
        assertOnce(counts, "a sheet")
    }

    /**
     * The race itself, with nothing composed twice for it: the title changes in
     * the frame the sheet first composes, and the sheet shows the new one while
     * its rows, which did not change, are still composed once.
     */
    @Test
    fun aSheetWhoseTitleChangesAsItOpensShowsItAndComposesItsRowsOnce() = runComposeUiTest {
        val counts = IntArray(Rows)
        open(counts, changeOnFrame = 1) { visible, title, rows ->
            ModalBottomSheet(visible = visible, onDismissRequest = {}) { Text(title); rows() }
        }
        onNodeWithText(Changed).assertExists("the sheet kept the title it opened with")
        assertOnce(counts, "a sheet whose title changed as it opened")
    }

    /**
     * Shows [overlay] from a `Scaffold`'s body, over [Frames] frames, with
     * [counts] rows in it; on frame [changeOnFrame] of the open, if there is
     * one, its title changes.
     */
    private fun ComposeUiTest.open(
        counts: IntArray,
        changeOnFrame: Int? = null,
        overlay: @Composable (visible: Boolean, title: String, rows: @Composable () -> Unit) -> Unit,
    ) {
        var visible by mutableStateOf(false)
        var title by mutableStateOf(Opened)
        mainClock.autoAdvance = false
        setContent {
            KontourTheme(reduceMotion = true) {
                OverlayHost(Modifier.fillMaxSize()) {
                    Scaffold {
                        Box {
                            val now = title
                            overlay(visible, now) { Column { repeat(counts.size) { Row(counts, it) } } }
                        }
                    }
                }
            }
        }
        repeat(3) { mainClock.advanceTimeByFrame() }
        visible = true
        repeat(Frames) { frame ->
            if (frame == changeOnFrame) title = Changed
            mainClock.advanceTimeByFrame()
        }
        waitForIdle()
    }

    private fun assertOnce(counts: IntArray, what: String) {
        assertEquals(
            List(counts.size) { 1 },
            counts.toList(),
            "how many times each row of $what was composed as it opened",
        )
    }

    /** One row, reporting each time it is composed. */
    @Composable
    private fun Row(counts: IntArray, index: Int) {
        SideEffect { counts[index]++ }
        Text("Departure ${index + 1}")
    }

    private companion object {
        const val Rows = 40
        const val Frames = 30
        const val Opened = "Departures"
        const val Changed = "Departures from Perth"
    }
}
