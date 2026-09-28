package io.kontour.ui.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import io.kontour.ui.adaptive.ListDetailPaneScaffold
import io.kontour.ui.adaptive.PaneFocus
import io.kontour.ui.adaptive.SupportingPaneScaffold
import io.kontour.ui.foundation.Text
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The pane scaffolds move their panes rather than rebuilding them.
 *
 * `SupportingPaneScaffold` had no motion at all on two panes: the supporting pane
 * was there on one frame and gone on the next, because open and closed were two
 * different branches — a `Row` of two, or a `Box` of one. The same branch jump
 * rebuilt the main pane each time, so whatever it held reset whenever the
 * supporting pane opened or closed. `ListDetailPaneScaffold` did the same to its
 * list whenever the window crossed the two-pane breakpoint.
 */
@OptIn(ExperimentalTestApi::class)
class PaneScaffoldMotionTest {

    @Composable
    private fun Harness(content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalDensity provides Density(1f)) {
            KontourTheme { OverlayHost { content() } }
        }
    }

    @Test
    fun closingTheSupportingPaneSlidesItOut() = runDesktopComposeUiTest(width = 1600, height = 800) {
        var open by mutableStateOf(true)
        var mainWidth = 0
        setContent {
            Harness {
                SupportingPaneScaffold(
                    main = { Box(Modifier.fillMaxSize().onSizeChanged { mainWidth = it.width }) },
                    supporting = { Text("the filters") },
                    supportingVisible = open,
                    twoPane = true,
                )
            }
        }
        waitForIdle()
        val beside = mainWidth
        onNodeWithText("the filters").assertExists()

        mainClock.autoAdvance = false
        open = false
        repeat(4) { mainClock.advanceTimeByFrame() }

        // Partway: still drawn, and the main pane on its way to the full width.
        onNodeWithText("the filters").assertExists()
        assertTrue(
            mainWidth in (beside + 1) until 1600,
            "four frames into closing, the main pane was ${mainWidth}px wide — it was $beside " +
                "beside the supporting pane and is 1600 alone, so the pane did not slide, it jumped",
        )

        mainClock.autoAdvance = true
        waitForIdle()
        onNodeWithText("the filters").assertDoesNotExist()
        assertEquals(1600, mainWidth)
    }

    @Test
    fun theMainPaneKeepsItsStateAsTheSupportingPaneOpensAndCloses() =
        runDesktopComposeUiTest(width = 1600, height = 800) {
            var open by mutableStateOf(true)
            var built = 0
            setContent {
                Harness {
                    SupportingPaneScaffold(
                        main = {
                            val id = remember { built++ }
                            Text("main $id")
                        },
                        supporting = { Text("the filters") },
                        supportingVisible = open,
                        twoPane = true,
                    )
                }
            }
            waitForIdle()
            onNodeWithText("main 0").assertExists()

            open = false
            waitForIdle()
            open = true
            waitForIdle()

            onNodeWithText("main 0").assertExists()
            assertEquals(1, built, "the main pane was built $built times across a close and an open")
        }

    @Test
    fun aListKeepsItsPlaceWhenTheWindowCrossesTheBreakpoint() =
        runDesktopComposeUiTest(width = 1600, height = 800) {
            var wide by mutableStateOf(true)
            var list: LazyListState? = null
            setContent {
                Harness {
                    ListDetailPaneScaffold(
                        focus = PaneFocus.List,
                        onBack = {},
                        list = {
                            val state = rememberLazyListState()
                            list = state
                            LazyColumn(Modifier.fillMaxSize(), state = state) {
                                items(200) { Text("row $it") }
                            }
                        },
                        detail = { Text("the detail") },
                        twoPane = wide,
                    )
                }
            }
            waitForIdle()
            runOnIdle { runBlocking { checkNotNull(list).scrollToItem(40) } }
            waitForIdle()
            assertEquals(40, checkNotNull(list).firstVisibleItemIndex)

            wide = false
            waitForIdle()

            assertEquals(
                40,
                checkNotNull(list).firstVisibleItemIndex,
                "the window narrowed to one pane and the list went back to the top",
            )
        }
}
