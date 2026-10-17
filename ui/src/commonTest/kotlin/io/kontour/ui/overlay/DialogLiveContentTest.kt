package io.kontour.ui.overlay

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kontour.ui.adaptive.Scaffold
import io.kontour.ui.foundation.Text
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A dialog whose content changes while it is open shows the content it has
 * now, whenever the change comes.
 *
 * Reported from an admin: a rollback dialog opened on "Comparing…", with its
 * confirm disabled until the comparison came back, stayed on "Comparing…" with
 * the button disabled about one time in three. The comparison arrives on its
 * own clock, so it can land before the dialog's entry is published, between
 * that and the entry's first frame, or after; each is tried here, frame by
 * frame, from the places a screen puts a dialog: straight in the host, in a
 * `Scaffold`'s body, which is a subcomposition, and in a lazy list's item.
 *
 * The content closes over a plain value rather than reading a state, the way
 * a screen does with `val state by viewModel.state.collectAsState()` and then
 * `state.comparison`: what keeps it live is the lambda the caller passes
 * changing, not a state the dialog reads for itself.
 */
@OptIn(ExperimentalTestApi::class)
class DialogLiveContentTest {

    private enum class Place { Host, Scaffold, LazyItem }

    @Test
    fun aDialogInTheHostShowsContentThatChangesWhileItOpens() = everyFrame(Place.Host)

    @Test
    fun aDialogInAScaffoldShowsContentThatChangesWhileItOpens() = everyFrame(Place.Scaffold)

    @Test
    fun aDialogInALazyItemShowsContentThatChangesWhileItOpens() = everyFrame(Place.LazyItem)

    /**
     * The other way a screen writes it: the loading dialog and the loaded one
     * as two calls, one in each branch of an `if`, under one key. The first
     * goes as the second arrives, and the second must be what is left.
     */
    @Test
    fun aDialogSwappedForAnotherUnderTheSameKeyShowsTheNewOne() {
        for (frames in 0..4) {
            runComposeUiTest {
                var visible by mutableStateOf(false)
                var compared by mutableStateOf(false)
                setContent {
                    KontourTheme(reduceMotion = true) {
                        OverlayHost(Modifier.fillMaxSize()) {
                            if (compared) {
                                RollbackDialog(visible, compared = true, key = Rollback)
                            } else {
                                RollbackDialog(visible, compared = false, key = Rollback)
                            }
                        }
                    }
                }
                openThenCompare(frames, open = { visible = true }, compare = { compared = true })
                assertCompared("the second of two dialogs under one key, $frames frames after opening")
            }
        }
    }

    private fun everyFrame(place: Place) {
        // 0 is the comparison arriving with the open, before any frame; 1 is
        // before the entry's first frame; the rest after it.
        for (frames in 0..4) {
            runComposeUiTest {
                var visible by mutableStateOf(false)
                var compared by mutableStateOf(false)
                setContent {
                    KontourTheme(reduceMotion = true) {
                        OverlayHost(Modifier.fillMaxSize()) {
                            // Read here, in the caller, and handed down as a
                            // value.
                            val now = compared
                            Placed(place) { RollbackDialog(visible, compared = now) }
                        }
                    }
                }
                openThenCompare(frames, open = { visible = true }, compare = { compared = true })
                assertCompared("${place.name}, the comparison $frames frames after opening")
            }
        }
    }

    @Composable
    private fun Placed(place: Place, content: @Composable () -> Unit) {
        when (place) {
            Place.Host -> content()
            Place.Scaffold -> Scaffold { Box { content() } }
            Place.LazyItem -> LazyColumn(Modifier.fillMaxSize()) { item { content() } }
        }
    }

    @Composable
    private fun RollbackDialog(visible: Boolean, compared: Boolean, key: Any? = null) {
        val body: @Composable ColumnScope.() -> Unit = {
            Text(if (compared) Ready else Comparing)
        }
        val actions: @Composable DialogActionsScope.() -> Unit = {
            confirm(Confirm, enabled = compared) {}
        }
        if (key == null) {
            Dialog(visible = visible, onDismissRequest = {}, actions = actions, content = body)
        } else {
            Dialog(visible = visible, onDismissRequest = {}, key = key, actions = actions, content = body)
        }
    }

    private fun ComposeUiTest.openThenCompare(frames: Int, open: () -> Unit, compare: () -> Unit) {
        mainClock.autoAdvance = false
        waitForIdle()
        open()
        repeat(frames) { mainClock.advanceTimeByFrame() }
        compare()
        repeat(SettleFrames) { mainClock.advanceTimeByFrame() }
        waitForIdle()
    }

    private fun ComposeUiTest.assertCompared(case: String) {
        assertEquals(
            0,
            onAllNodesWithText(Comparing).fetchSemanticsNodes().size,
            "$case: the dialog still says \"$Comparing\" — it kept the content it opened with",
        )
        onNodeWithText(Ready).assertExists("$case: the dialog does not show what it was given since it opened")
        onNodeWithText(Confirm).assertIsEnabled()
    }

    private companion object {
        const val Comparing = "Comparing…"
        const val Ready = "Roll back to version 41"
        const val Confirm = "Roll back"
        const val Rollback = "rollback"
        const val SettleFrames = 40
    }
}
