package io.kontour.ui.nav3

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import io.kontour.ui.foundation.Text
import io.kontour.ui.motion.BackStyle
import io.kontour.ui.motion.LocalBackStyle
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.sheet.ModalBottomSheet
import io.kontour.ui.theme.KontourTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A stack inside a sheet is the sheet's colour, and going back in it draws no
 * edge against the sheet's own header and padding around it.
 *
 * Each page carries a red block, so the fade on what a page draws can be told
 * from the ground it draws it on.
 */
@OptIn(ExperimentalTestApi::class)
class SheetStackGroundTest {

    private object Departures
    private object Route

    private class Back {
        val dispatcher = NavigationEventDispatcher()
        val input = DirectNavigationEventInput().also { dispatcher.addInput(it) }
        val owner = object : NavigationEventDispatcherOwner {
            override val navigationEventDispatcher = dispatcher
        }
    }

    @Composable
    private fun Page(title: String) {
        Column(Modifier.fillMaxSize()) {
            Text(title)
            Box(Modifier.padding(start = 120.dp, top = 40.dp).size(24.dp).background(Color.Red).testTag("$title block"))
        }
    }

    @Composable
    private fun Stack(backStack: SnapshotStateList<Any>) {
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            sceneDecoratorStrategies = listOf(rememberPageTransitionStrategy(contentSwipe = false)),
            entryProvider = entryProvider {
                entry<Departures> { Page("Departures") }
                entry<Route> { Page("Route 950") }
            },
        )
    }

    @Composable
    private fun InASheet(back: Back, dark: Boolean, backStack: SnapshotStateList<Any>) {
        CompositionLocalProvider(
            LocalNavigationEventDispatcherOwner provides back.owner,
            LocalBackStyle provides BackStyle.Swipe,
        ) {
            KontourTheme(darkTheme = dark) {
                OverlayHost {
                    ModalBottomSheet(visible = true, onDismissRequest = {}) {
                        // Room below the stack that is the sheet's and not the
                        // stack's, as the safe area gives it on a phone.
                        Box(Modifier.fillMaxWidth().height(360.dp).padding(bottom = 40.dp).testTag("stack")) {
                            Stack(backStack)
                        }
                    }
                }
            }
        }
    }

    private fun ComposeUiTest.dragTo(back: Back, progress: Float) {
        runOnUiThread { back.input.backStarted(NavigationEvent(NavigationEvent.EDGE_LEFT, 0f, 0f, 0f)) }
        waitForIdle()
        runOnUiThread { back.input.backProgressed(NavigationEvent(NavigationEvent.EDGE_LEFT, progress, 0f, 0f)) }
        waitForIdle()
    }

    private fun ComposeUiTest.pixels(): PixelMap {
        waitForIdle()
        return onRoot().captureToImage().toPixelMap()
    }

    private fun ComposeUiTest.bounds(tag: String): Rect =
        onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun same(a: Color, b: Color) =
        abs(a.red - b.red) < 1.5f / 255 && abs(a.green - b.green) < 1.5f / 255 && abs(a.blue - b.blue) < 1.5f / 255

    @Test
    fun atRestThePageIsTheSheetsColour() = runDesktopComposeUiTest(width = 400, height = 800) {
        val back = Back()
        setContent { InASheet(back, dark = true, mutableStateListOf(Departures)) }
        val stack = bounds("stack")
        val map = pixels()
        val x = (stack.left + stack.width * 0.6f).toInt()
        val ground = map[x, (stack.bottom - 40f).toInt()]
        val gutter = map[x, (stack.bottom + 20f).toInt()]
        assertTrue(same(ground, gutter), "the page is $ground on a sheet of $gutter")
    }

    @Test
    fun goingBackInASheetDrawsNoEdgeAgainstIt() = runDesktopComposeUiTest(width = 400, height = 800) {
        val back = Back()
        val backStack = mutableStateListOf<Any>(Departures, Route)
        setContent { InASheet(back, dark = false, backStack) }
        waitForIdle()
        val restBlock = pixels().let { map ->
            val b = bounds("Route 950 block")
            map[b.center.x.toInt(), b.center.y.toInt()]
        }

        dragTo(back, 0.5f)
        val stack = bounds("stack")
        val map = pixels()
        // Left of the page on top, which has slid half the width away: the page
        // beneath, on its empty ground, and the sheet's padding right below.
        val x = (stack.left + stack.width * 0.2f).toInt()
        val ground = map[x, (stack.bottom - 40f).toInt()]
        val gutter = map[x, (stack.bottom + 20f).toInt()]
        assertTrue(same(ground, gutter), "the page beneath is $ground against the sheet's $gutter: an edge")

        // What the page beneath draws on that ground does go back: its block is
        // no longer the red it is at rest.
        val block = bounds("Departures block")
        val faded = map[block.center.x.toInt(), block.center.y.toInt()]
        assertTrue(same(restBlock, Color.Red), "the block at rest should be red, was $restBlock")
        assertTrue(faded.green > 0.1f && faded.blue > 0.1f, "the page beneath did not recede: its block is $faded")
    }

    /** On the window's own ground the page beneath still dims, as iOS's does. */
    @Test
    fun onTheWindowThePageBeneathDims() = runDesktopComposeUiTest(width = 400, height = 800) {
        val back = Back()
        val backStack = mutableStateListOf<Any>(Departures, Route)
        setContent {
            CompositionLocalProvider(
                LocalNavigationEventDispatcherOwner provides back.owner,
                LocalBackStyle provides BackStyle.Swipe,
            ) {
                KontourTheme(darkTheme = false) {
                    OverlayHost { Stack(backStack) }
                }
            }
        }
        waitForIdle()
        dragTo(back, 0.5f)
        val ground = pixels()[80, 700]
        assertTrue(ground.red < 0.97f, "the page beneath was not dimmed: $ground")
    }
}
