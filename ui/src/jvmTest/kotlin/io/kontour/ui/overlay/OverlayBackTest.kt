package io.kontour.ui.overlay

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import io.kontour.ui.foundation.Text
import io.kontour.ui.sheet.ModalBottomSheet
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Back reaches the innermost thing that can take it, and nothing else.
 *
 * Driven through a real dispatcher and a direct input — the same path the
 * Android back gesture, iOS's edge pan and Escape all arrive by — so what is
 * tested is which handler answers, not what a handler does when called.
 */
@OptIn(ExperimentalTestApi::class)
class OverlayBackTest {

    private object PageInfo : NavigationEventInfo()

    /** A dispatcher the test owns, and the input a gesture comes in through. */
    private class Back {
        val dispatcher = NavigationEventDispatcher()
        val input = DirectNavigationEventInput().also { dispatcher.addInput(it) }
        val owner = object : NavigationEventDispatcherOwner {
            override val navigationEventDispatcher = dispatcher
        }

        /** A key or a button: completes with no gesture before it. */
        fun press() = input.backCompleted()

        fun start(edge: Int = NavigationEvent.EDGE_LEFT) =
            input.backStarted(NavigationEvent(edge, 0f, 0f, 0f))

        fun drag(progress: Float, edge: Int = NavigationEvent.EDGE_LEFT) =
            input.backProgressed(NavigationEvent(edge, progress, 0f, 0f))

        fun release() = input.backCompleted()

        fun abandon() = input.backCancelled()
    }

    private class Fixture {
        var pageBacks = 0
        var dialogDismissals = 0
        var dialog by mutableStateOf(false)
        var dismissible by mutableStateOf(true)
        var innerStack by mutableStateOf(0)
        var innerBacks = 0
    }

    /** The page: a back handler of its own, as a navigation stack would have. */
    @Composable
    private fun Page(fixture: Fixture) {
        NavigationBackHandler(
            state = rememberNavigationEventState(PageInfo),
            isBackEnabled = true,
            onBackCompleted = { fixture.pageBacks++ },
        )
        Box(Modifier.fillMaxSize())
    }

    @Composable
    private fun Harness(back: Back, fixture: Fixture) {
        CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides back.owner) {
            KontourTheme(darkTheme = false) {
                OverlayHost(Modifier.fillMaxSize()) {
                    Page(fixture)
                    Dialog(
                        visible = fixture.dialog,
                        onDismissRequest = {
                            fixture.dialogDismissals++
                            fixture.dialog = false
                        },
                        dismissible = fixture.dismissible,
                    ) {
                        // A stack inside the dialog, which answers first while
                        // it has somewhere to go back to.
                        NavigationBackHandler(
                            state = rememberNavigationEventState(PageInfo),
                            isBackEnabled = fixture.innerStack > 0,
                            onBackCompleted = {
                                fixture.innerBacks++
                                fixture.innerStack--
                            },
                        )
                        Text("Delete this trip?", Modifier.size(200.dp, 80.dp))
                    }
                }
            }
        }
    }

    @Test
    fun backWithNothingOpenGoesToThePage() = runComposeUiTest {
        val back = Back()
        val fixture = Fixture()
        setContent { Harness(back, fixture) }
        waitForIdle()
        runOnUiThread { back.press() }
        waitForIdle()
        assertEquals(1, fixture.pageBacks)
    }

    @Test
    fun backClosesTheDialogAndNotThePageBehindIt() = runComposeUiTest {
        val back = Back()
        val fixture = Fixture()
        setContent { Harness(back, fixture) }
        runOnUiThread { fixture.dialog = true }
        waitForIdle()

        runOnUiThread { back.press() }
        waitForIdle()
        assertEquals(1, fixture.dialogDismissals, "the dialog did not close on back")
        assertEquals(0, fixture.pageBacks, "the page went back from behind an open dialog")

        runOnUiThread { back.press() }
        waitForIdle()
        assertEquals(1, fixture.dialogDismissals, "a dialog already closing was asked again")
        assertEquals(1, fixture.pageBacks, "with the dialog gone, back belongs to the page again")
    }

    /**
     * A dialog that may not be dismissed takes back and refuses it.
     *
     * Before the host handled back at all, `dismissible = false` only turned off
     * the tap outside — `dismissOnBack` stayed true — so the first thing that
     * wired back up would have closed dialogs that say they cannot be closed.
     * And letting it through instead would pop the screen behind a dialog that
     * is still asking its question.
     */
    @Test
    fun aDialogThatMayNotCloseTakesBackAndRefusesIt() = runComposeUiTest {
        val back = Back()
        val fixture = Fixture()
        fixture.dismissible = false
        setContent { Harness(back, fixture) }
        runOnUiThread { fixture.dialog = true }
        waitForIdle()

        runOnUiThread { back.press() }
        waitForIdle()
        assertEquals(0, fixture.dialogDismissals, "a dialog that may not close was closed by back")
        assertEquals(0, fixture.pageBacks, "back went past a dialog that dims the page")

        // A gesture let go is refused the same way.
        runOnUiThread {
            back.start()
            back.drag(0.8f)
            back.release()
        }
        waitForIdle()
        assertEquals(0, fixture.dialogDismissals)
        assertEquals(0, fixture.pageBacks)
    }

    @Test
    fun aStackInsideTheDialogGoesBackFirst() = runComposeUiTest {
        val back = Back()
        val fixture = Fixture()
        fixture.innerStack = 2
        setContent { Harness(back, fixture) }
        runOnUiThread { fixture.dialog = true }
        waitForIdle()

        repeat(2) {
            runOnUiThread { back.press() }
            waitForIdle()
        }
        assertEquals(2, fixture.innerBacks, "the stack inside the dialog did not answer first")
        assertEquals(0, fixture.dialogDismissals)

        runOnUiThread { back.press() }
        waitForIdle()
        assertEquals(1, fixture.dialogDismissals, "with its stack empty, back closes the dialog")
        assertEquals(0, fixture.pageBacks)
    }

    @Test
    fun anAbandonedGestureClosesNothing() = runComposeUiTest {
        val back = Back()
        val fixture = Fixture()
        setContent { Harness(back, fixture) }
        runOnUiThread { fixture.dialog = true }
        waitForIdle()

        runOnUiThread {
            back.start()
            back.drag(0.3f)
            back.drag(0.6f)
            back.abandon()
        }
        waitForIdle()
        assertEquals(0, fixture.dialogDismissals)
        assertEquals(0, fixture.pageBacks)
        assertTrue(fixture.dialog, "an abandoned gesture closed the dialog")

        runOnUiThread {
            back.start()
            back.drag(0.6f)
            back.release()
        }
        waitForIdle()
        assertEquals(1, fixture.dialogDismissals, "a gesture let go closes the dialog")
    }

    /**
     * An entry that neither dims nor dismisses on back — a toast, a plain
     * tooltip — is not in back's way.
     */
    @Test
    fun anEntryThatNeitherDimsNorDismissesLetsBackThrough() = runComposeUiTest {
        val back = Back()
        val fixture = Fixture()
        lateinit var host: OverlayHostState
        setContent {
            CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides back.owner) {
                KontourTheme(darkTheme = false) {
                    OverlayHost(Modifier.fillMaxSize()) {
                        host = LocalOverlayHost.current
                        Page(fixture)
                    }
                }
            }
        }
        waitForIdle()
        runOnUiThread {
            host.show(
                OverlayEntry(
                    key = "toast",
                    layer = OverlayLayer.Toast,
                    scrim = ScrimStyle.None,
                    dismissOnBack = false,
                    trapFocus = false,
                    content = { Text("Saved") },
                )
            )
        }
        waitForIdle()
        runOnUiThread { back.press() }
        waitForIdle()
        assertEquals(1, fixture.pageBacks, "a toast stopped back reaching the page")
        assertTrue(host.isShowing("toast"), "back closed a toast that does not dismiss on back")
    }

    @Test
    fun theTopEntryGoesFirst() = runComposeUiTest {
        val back = Back()
        val fixture = Fixture()
        lateinit var host: OverlayHostState
        val closed = mutableListOf<String>()
        setContent {
            CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides back.owner) {
                KontourTheme(darkTheme = false) {
                    OverlayHost(Modifier.fillMaxSize()) {
                        host = LocalOverlayHost.current
                        Page(fixture)
                    }
                }
            }
        }
        waitForIdle()
        runOnUiThread {
            host.show(OverlayEntry(key = "sheet", layer = OverlayLayer.Sheet, onDismiss = { closed += "sheet" }) { Text("Sheet") })
            host.show(OverlayEntry(key = "menu", layer = OverlayLayer.Menu, scrim = ScrimStyle.Transparent, onDismiss = { closed += "menu" }) { Text("Menu") })
        }
        waitForIdle()
        runOnUiThread { back.press() }
        waitForIdle()
        assertEquals(listOf("menu"), closed, "back closed something other than the top entry")
        runOnUiThread { back.press() }
        waitForIdle()
        assertEquals(listOf("menu", "sheet"), closed)
        assertEquals(0, fixture.pageBacks)
    }
    /**
     * A stack inside a sheet pops first, then the sheet closes — and the
     * sheet's `onDismissRequest` comes once, as its page promises for every
     * way a user can close it.
     */
    @Test
    fun aSheetWithAStackInsideItPopsTheStackThenClosesOnce() = runComposeUiTest {
        val back = Back()
        val fixture = Fixture()
        fixture.innerStack = 1
        var sheetOpen by mutableStateOf(false)
        var sheetDismissals = 0
        setContent {
            CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides back.owner) {
                KontourTheme(darkTheme = false) {
                    OverlayHost(Modifier.fillMaxSize()) {
                        Page(fixture)
                        ModalBottomSheet(
                            visible = sheetOpen,
                            onDismissRequest = {
                                sheetDismissals++
                                sheetOpen = false
                            },
                        ) {
                            NavigationBackHandler(
                                state = rememberNavigationEventState(PageInfo),
                                isBackEnabled = fixture.innerStack > 0,
                                onBackCompleted = {
                                    fixture.innerBacks++
                                    fixture.innerStack--
                                },
                            )
                            Text("Departures", Modifier.size(200.dp, 200.dp))
                        }
                    }
                }
            }
        }
        runOnUiThread { sheetOpen = true }
        waitForIdle()

        runOnUiThread { back.press() }
        waitForIdle()
        assertEquals(1, fixture.innerBacks, "the stack inside the sheet did not answer first")
        assertEquals(0, sheetDismissals)

        runOnUiThread {
            back.start()
            back.drag(0.7f)
            back.release()
        }
        waitForIdle()
        assertEquals(1, sheetDismissals, "back with the stack empty should close the sheet, once")
        assertEquals(0, fixture.pageBacks, "back reached the page past an open sheet")

        runOnUiThread { back.press() }
        waitForIdle()
        assertEquals(1, sheetDismissals, "a closed sheet was asked to close again")
        assertEquals(1, fixture.pageBacks, "with the sheet gone, back belongs to the page")
    }
}
