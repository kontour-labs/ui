package io.kontour.ui.overlay

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.relocation.BringIntoViewModifierNode
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.text.TextField
import io.kontour.ui.components.text.keyboardCovers
import io.kontour.ui.sheet.ModalBottomSheet
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A field in a sheet or a dialog never asks anything outside it to move.
 *
 * Reported from Android: *"sometimes when a sheet (or dialog) opens at exactly
 * the same time as the keyboard, content gets pushed up. It should just become
 * scrollable behind the keyboard."* The field asked to be brought into view as
 * it took focus, while the sheet carrying it was still on its way up. Nothing
 * in the overlay answered, so the request reached the root, and Android's root
 * hands it to `View.requestRectangleOnScreen`, which pans the window. See
 * `endsBringIntoView`.
 *
 * The root here is a node round the overlay host that answers bring-into-view
 * requests as Android's does, and counts them instead of panning. A field
 * focused as its overlay opens must send it none.
 */
@OptIn(ExperimentalTestApi::class)
class OverlayKeepsWindowStillTest {

    @Test
    fun aFieldFocusedAsASheetArrivesAsksNothingOfTheWindow() = runComposeUiTest {
        val open = mutableStateOf(false)
        val probe = FieldProbe()
        val window = Window()
        stage(window) {
            ModalBottomSheet(visible = open.value, onDismissRequest = {}) {
                Column {
                    Spacer(Modifier.fillMaxWidth().height(SheetAboveField))
                    Field(probe)
                    LaunchedEffect(Unit) { probe.requester.requestFocus() }
                }
            }
        }
        open.value = true
        waitForIdle()

        assertTrue(probe.focused, "the sheet's field never took focus, so this measures nothing")
        assertEquals(emptyList(), window.asked, "a field focused as its sheet arrived asked the window to show it")
    }

    @Test
    fun aFieldFocusedAsADialogOpensAsksNothingOfTheWindow() = runComposeUiTest {
        val open = mutableStateOf(false)
        val probe = FieldProbe()
        val window = Window()
        stage(window) {
            Dialog(visible = open.value, onDismissRequest = {}) {
                Field(probe)
                LaunchedEffect(Unit) { probe.requester.requestFocus() }
            }
        }
        open.value = true
        waitForIdle()

        assertTrue(probe.focused, "the dialog's field never took focus, so this measures nothing")
        assertEquals(emptyList(), window.asked, "a field focused as its dialog opened asked the window to show it")
    }

    /** A field on the page still asks: the window is the page's to move. */
    @Test
    fun aFieldOnThePageStillAsks() = runComposeUiTest {
        val probe = FieldProbe()
        val window = Window()
        stage(window) {
            Column {
                Spacer(Modifier.fillMaxWidth().height(SheetAboveField))
                Field(probe)
            }
        }
        runOnIdle { probe.requester.requestFocus() }
        waitForIdle()

        assertTrue(probe.focused, "the page's field never took focus, so this measures nothing")
        assertTrue(window.asked.isNotEmpty(), "a field on the page no longer asked to be seen: the window would not move for it")
    }

    /** What a keyboard covers of a field: only the part of it below the keyboard's top. */
    @Test
    fun theKeyboardCoversOnlyWhatIsBelowItsTop() {
        assertEquals(0f, keyboardCovers(bottom = 500f, windowHeight = 1000f, keyboard = 400f), "a field above the keyboard")
        assertEquals(100f, keyboardCovers(bottom = 700f, windowHeight = 1000f, keyboard = 400f), "a field 100px under its top")
        assertEquals(0f, keyboardCovers(bottom = 900f, windowHeight = 1000f, keyboard = 0f), "with no keyboard")
    }

    private fun androidx.compose.ui.test.ComposeUiTest.stage(window: Window, content: @Composable () -> Unit) {
        setContent {
            KontourTheme {
                Box(Modifier.fillMaxSize().then(WindowElement(window))) {
                    OverlayHost(Modifier.fillMaxSize()) { content() }
                }
            }
        }
        waitForIdle()
    }

    @Composable
    private fun Field(probe: FieldProbe) {
        TextField(
            state = rememberTextFieldState(),
            modifier = remember(probe) {
                Modifier.focusRequester(probe.requester).onFocusChanged { probe.focused = it.hasFocus }
            },
        )
    }

    private class FieldProbe {
        val requester = FocusRequester()
        var focused = false
    }

    /** The requests that reached the root: where Android would have panned the window. */
    private class Window {
        val asked = mutableListOf<Rect?>()
    }

    private class WindowElement(val window: Window) : ModifierNodeElement<WindowNode>() {
        override fun create() = WindowNode(window)
        override fun update(node: WindowNode) {
            node.window = window
        }
        override fun hashCode() = window.hashCode()
        override fun equals(other: Any?) = other is WindowElement && other.window === window
    }

    private class WindowNode(var window: Window) : Modifier.Node(), BringIntoViewModifierNode {
        override suspend fun bringIntoView(childCoordinates: LayoutCoordinates, boundsProvider: () -> Rect?) {
            window.asked += boundsProvider()
        }
    }

    private companion object {
        val SheetAboveField = 300.dp
    }
}
