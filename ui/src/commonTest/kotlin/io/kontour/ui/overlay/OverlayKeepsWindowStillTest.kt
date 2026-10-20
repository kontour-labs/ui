package io.kontour.ui.overlay

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
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
 * A field in a sheet or a dialog never moves anything outside it to be seen.
 *
 * Reported from Android: *"sometimes when a sheet (or dialog) opens at exactly
 * the same time as the keyboard, content gets pushed up. It should just become
 * scrollable behind the keyboard."* The field asked to be brought into view as
 * it took focus, while the sheet carrying it was still on its way up and the
 * field was low on the screen. Nothing in the overlay answered, so the request
 * reached the root, and Android's root pans the window for it. See
 * `endsBringIntoView`.
 *
 * The window here is a scroller round the overlay host, taller than the
 * scroller's view, so it has somewhere to pan to: the same thing the root
 * does, where a test can see it. A field focused as its overlay opens, below
 * that view, must leave it where it was.
 */
@OptIn(ExperimentalTestApi::class)
class OverlayKeepsWindowStillTest {

    @Test
    fun aFieldFocusedAsASheetArrivesLeavesTheWindowWhereItWas() = runComposeUiTest {
        val open = mutableStateOf(false)
        val probe = FieldProbe()
        val window = stage {
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
        assertEquals(0, window.value, "a field focused as its sheet arrived moved the window ${window.value}px to be seen")
    }

    @Test
    fun aFieldFocusedAsADialogOpensLeavesTheWindowWhereItWas() = runComposeUiTest {
        val open = mutableStateOf(false)
        val probe = FieldProbe()
        val window = stage {
            Dialog(visible = open.value, onDismissRequest = {}) {
                Field(probe)
                LaunchedEffect(Unit) { probe.requester.requestFocus() }
            }
        }
        open.value = true
        waitForIdle()

        assertTrue(probe.focused, "the dialog's field never took focus, so this measures nothing")
        assertEquals(0, window.value, "a field focused as its dialog opened moved the window ${window.value}px to be seen")
    }

    /** What a keyboard covers of a field: only the part of it below the keyboard's top. */
    @Test
    fun theKeyboardCoversOnlyWhatIsBelowItsTop() {
        assertEquals(0f, keyboardCovers(bottom = 500f, windowHeight = 1000f, keyboard = 400f), "a field above the keyboard")
        assertEquals(100f, keyboardCovers(bottom = 700f, windowHeight = 1000f, keyboard = 400f), "a field 100px under its top")
        assertEquals(0f, keyboardCovers(bottom = 900f, windowHeight = 1000f, keyboard = 0f), "with no keyboard")
    }

    /**
     * An overlay host twice as tall as the view onto it, in a scroller: the
     * window, and how far it has been panned.
     */
    private fun androidx.compose.ui.test.ComposeUiTest.stage(overlay: @Composable () -> Unit): ScrollState {
        lateinit var scroll: ScrollState
        setContent {
            KontourTheme {
                scroll = rememberScrollState()
                Box(Modifier.size(WindowWidth, WindowHeight).verticalScroll(scroll)) {
                    Box(Modifier.size(WindowWidth, WindowHeight * 2)) {
                        OverlayHost(Modifier.fillMaxSize()) { overlay() }
                    }
                }
            }
        }
        waitForIdle()
        return scroll
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

    private companion object {
        val WindowWidth = 400.dp
        val WindowHeight = 400.dp
        val SheetAboveField = 300.dp
    }
}
