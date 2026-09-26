package io.kontour.ui.nav3

import io.kontour.ui.adaptive.PaneScaffoldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import io.kontour.ui.foundation.Text
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The supporting pane as a sheet, driven through a real `NavDisplay`.
 *
 * Two ways out, and each must pop exactly one entry. A drag or a scrim tap asks
 * the back stack to pop, and the sheet has already gone by the time it is asked
 * to leave. A system back pops first, and the sheet then has to leave on its own
 * — without reporting that as a *second* dismissal, which would pop the page the
 * sheet was over as well.
 */
@OptIn(ExperimentalTestApi::class)
class SupportingSheetTest {

    private object Run
    private object Conditions

    @Composable
    private fun Display(backStack: SnapshotStateList<Any>, onBack: () -> Unit) {
        KontourTheme {
            OverlayHost {
                NavDisplay(
                    backStack = backStack,
                    onBack = onBack,
                    sceneStrategies = listOf(SupportingPaneSceneStrategy(twoPane = false, PaneScaffoldDefaults.SupportingWeight, resizable = false, showDivider = true)),
                    entryProvider = entryProvider {
                        entry<Run>(metadata = mainPane()) { Text("the run") }
                        entry<Conditions>(metadata = supportingPane()) { Text("the conditions") }
                    },
                )
            }
        }
    }

    private fun ComposeUiTest.frames(count: Int) = repeat(count) { mainClock.advanceTimeByFrame() }

    @Test
    fun dismissingTheSheetPopsExactlyOneEntry() = runDesktopComposeUiTest(width = 400, height = 800) {
        val backStack = mutableStateListOf<Any>(Run, Conditions)
        var pops = 0
        setContent {
            Display(backStack, onBack = { pops++; backStack.removeLastOrNull() })
        }
        waitForIdle()
        onNodeWithText("the conditions").assertExists()
        // Under the sheet, and so hidden from a screen reader while it is open.
        onNodeWithText("the run", useUnmergedTree = true).assertExists()

        // The scrim, as a screen reader presses it — the same request a tap
        // outside the sheet makes.
        onNode(hasContentDescription("Close") and SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick))
            .performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()

        assertEquals(1, pops, "dismissing the sheet should pop once")
        assertEquals(listOf<Any>(Run), backStack.toList())
        onNodeWithText("the conditions").assertDoesNotExist()
        onNodeWithText("the run").assertExists()
    }

    @Test
    fun aSystemBackClosesTheSheetWithoutASecondPop() = runDesktopComposeUiTest(width = 400, height = 800) {
        val backStack = mutableStateListOf<Any>(Run, Conditions)
        var pops = 0
        setContent {
            Display(backStack, onBack = { pops++; backStack.removeLastOrNull() })
        }
        waitForIdle()
        mainClock.autoAdvance = false

        // What a system back does: the stack pops, and nobody asked the sheet.
        backStack.removeLastOrNull()
        frames(2)
        // Still composed, on its way down — the overlay waits for the sheet.
        onNodeWithText("the conditions").assertExists()

        frames(90)
        mainClock.autoAdvance = true
        waitForIdle()
        onNodeWithText("the conditions").assertDoesNotExist()
        assertEquals(0, pops, "the sheet reported its own exit as a dismissal and popped again")
        assertEquals(listOf<Any>(Run), backStack.toList())
    }

    // --- on two panes ------------------------------------------------------

    private var built = 0

    @Composable
    private fun WideDisplay(backStack: SnapshotStateList<Any>) {
        KontourTheme {
            OverlayHost {
                NavDisplay(
                    backStack = backStack,
                    onBack = { backStack.removeLastOrNull() },
                    sceneStrategies = listOf(SupportingPaneSceneStrategy(twoPane = true, PaneScaffoldDefaults.SupportingWeight, resizable = false, showDivider = true)),
                    entryProvider = entryProvider {
                        entry<Run>(metadata = mainPane()) { Text("the run") }
                        entry<Conditions>(metadata = supportingPane()) {
                            // Saved state, which Navigation 3 drops only once the
                            // entry's content has left composition.
                            val id = rememberSaveable { built++ }
                            Text("the conditions $id")
                        }
                    },
                )
            }
        }
    }

    @Test
    fun poppingTheSupportingPaneKeepsItDrawnUntilItHasSlidAway() =
        runDesktopComposeUiTest(width = 1600, height = 800) {
            val backStack = mutableStateListOf<Any>(Run, Conditions)
            setContent { WideDisplay(backStack) }
            waitForIdle()
            onNodeWithText("the conditions 0").assertExists()

            mainClock.autoAdvance = false
            backStack.removeLastOrNull()
            frames(4)
            // The entry has left the back stack, and the pane is still sliding
            // with its content in it rather than empty.
            onNodeWithText("the conditions 0").assertExists()

            mainClock.autoAdvance = true
            waitForIdle()
            onNodeWithText("the conditions 0").assertDoesNotExist()
            onNodeWithText("the run").assertExists()
        }

    @Test
    fun itsSavedStateIsDroppedOnceItHasGone() = runDesktopComposeUiTest(width = 1600, height = 800) {
        val backStack = mutableStateListOf<Any>(Run, Conditions)
        setContent { WideDisplay(backStack) }
        waitForIdle()
        onNodeWithText("the conditions 0").assertExists()

        backStack.removeLastOrNull()
        waitForIdle()
        backStack.add(Conditions)
        waitForIdle()

        // A fresh entry, not the old one brought back: the content drawn during
        // the slide was let go of, so Navigation 3 cleaned it up.
        onNodeWithText("the conditions 1").assertExists()
    }
}
