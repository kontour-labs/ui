package io.kontour.ui.components.selection

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kontour.ui.theme.KontourTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * An input chip with no remove button keeps the same room at its end as at
 * its start.
 *
 * Reported on a file field holding one file, whose chip has no remove of its
 * own: *"the file's chip doesn't have any right padding"*. The chip's end was
 * 4dp, which is right beside a remove button that brings its own 4dp, and was
 * kept without one.
 */
@OptIn(ExperimentalTestApi::class)
class InputChipEndTest {

    @Test
    fun withoutARemoveButtonTheEndMatchesTheStart() = runComposeUiTest {
        setContent {
            KontourTheme {
                InputChip(onRemove = {}, removeLabel = "Remove", removeIcon = null, modifier = Modifier.testTag("chip")) { +"timetable.pdf" }
            }
        }
        val chip = onNodeWithTag("chip").fetchSemanticsNode().boundsInRoot
        val text = onNodeWithText("timetable.pdf", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val start = text.left - chip.left
        val end = chip.right - text.right
        assertTrue(abs(end - start) <= 1f, "the chip should have as much room after its text as before it: ${start}px before, ${end}px after")
    }
}
