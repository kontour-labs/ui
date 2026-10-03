package io.kontour.ui.overlay

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kontour.ui.components.text.TextField
import io.kontour.ui.foundation.Text
import io.kontour.ui.sheet.ModalBottomSheet
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A modal opening takes the focus off whatever had it, so the keyboard goes.
 *
 * Asked for in those words: *"when a dialog or something pops up, any text
 * fields lose focus (and therefore, the ime will hide)"*.
 *
 * The page's half was already true, and the first two tests here passed before
 * anything changed: the host makes the page unfocusable under a modal, and
 * Compose takes the focus off a field that can no longer have it. What was not
 * true is the case those tests could not see — a field in a *sheet* with a
 * dialog opened over it. The sheet is an overlay, not the page, so nothing made
 * it unfocusable, and its field kept its focus, its caret and, on a phone, its
 * keyboard over the dialog. That is the third test, and it failed.
 *
 * Only a modal — an overlay that dims the page. A menu or a combobox's list
 * opens *from* a field, and typing into the field while it is open is the point
 * of it, so those leave the focus where it is. And a modal that puts the focus
 * in itself — a command palette's field — still gets it: the page's focus goes
 * first, and the modal's own request comes after.
 */
@OptIn(ExperimentalTestApi::class)
class ModalFocusClearTest {

    @Test
    fun aDialogTakesTheFocusOffThePage() = runComposeUiTest {
        val open = mutableStateOf(false)
        val page = FieldProbe()
        setContent { Stage(page, open) { Dialog(visible = it, onDismissRequest = {}) { Text(InDialog) } } }
        focus(page)

        open.value = true
        waitForIdle()
        onNodeWithText(InDialog).assertExists("the dialog did not open, so this measures nothing")
        assertFalse(page.focused, "the page's field kept its focus, and its keyboard, under an open dialog")
    }

    @Test
    fun aModalSheetTakesTheFocusOffThePage() = runComposeUiTest {
        val open = mutableStateOf(false)
        val page = FieldProbe()
        setContent {
            Stage(page, open) { ModalBottomSheet(visible = it, onDismissRequest = {}) { Text(InSheet) } }
        }
        focus(page)

        open.value = true
        waitForIdle()
        onNodeWithText(InSheet).assertExists("the sheet did not open, so this measures nothing")
        assertFalse(page.focused, "the page's field kept its focus under an open modal sheet")
    }

    /** A dialog over a sheet takes the focus off a field in the sheet. */
    @Test
    fun aDialogOverASheetTakesTheFocusOffTheSheet() = runComposeUiTest {
        val sheetOpen = mutableStateOf(true)
        val dialogOpen = mutableStateOf(false)
        val inSheet = FieldProbe()
        setContent {
            KontourTheme {
                OverlayHost(Modifier.fillMaxSize()) {
                    ModalBottomSheet(visible = sheetOpen.value, onDismissRequest = {}) { Field(inSheet) }
                    Dialog(visible = dialogOpen.value, onDismissRequest = {}) { Text(InDialog) }
                }
            }
        }
        waitForIdle()
        focus(inSheet)

        dialogOpen.value = true
        waitForIdle()
        onNodeWithText(InDialog).assertExists("the dialog did not open, so this measures nothing")
        assertFalse(inSheet.focused, "the sheet's field kept its focus under the dialog opened over it")
    }

    /** A modal that focuses its own field still gets to: the page's goes first. */
    @Test
    fun aModalThatFocusesItsOwnFieldKeepsIt() = runComposeUiTest {
        val open = mutableStateOf(false)
        val page = FieldProbe()
        val inside = FieldProbe()
        setContent {
            Stage(page, open) {
                Dialog(visible = it, onDismissRequest = {}) {
                    Field(inside)
                    LaunchedEffect(Unit) { inside.requester.requestFocus() }
                }
            }
        }
        focus(page)

        open.value = true
        waitForIdle()
        assertFalse(page.focused, "the page's field kept its focus")
        assertTrue(inside.focused, "the dialog's own field did not get the focus it asked for")
    }

    /**
     * A menu is not a modal: it opens from a field, and the field keeps typing.
     * `ScrimStyle.Transparent` is the menu's, and it traps focus for a keyboard
     * reason while leaving the page there to be read.
     */
    @Test
    fun aMenuLeavesTheFieldFocused() = runComposeUiTest {
        val open = mutableStateOf(false)
        val page = FieldProbe()
        setContent {
            Stage(page, open) { visible ->
                val host = LocalOverlayHost.current
                LaunchedEffect(visible) {
                    if (visible) {
                        host.show(
                            OverlayEntry(
                                key = InMenu,
                                layer = OverlayLayer.Menu,
                                scrim = ScrimStyle.Transparent,
                                trapFocus = false,
                                content = { Text(InMenu) },
                            )
                        )
                    }
                }
            }
        }
        focus(page)

        open.value = true
        waitForIdle()
        onNodeWithText(InMenu).assertExists("the menu did not open, so this measures nothing")
        assertTrue(page.focused, "a menu opened from a focused field took the focus off it")
    }

    private fun androidx.compose.ui.test.ComposeUiTest.focus(probe: FieldProbe) {
        runOnIdle { probe.requester.requestFocus() }
        waitForIdle()
        assertTrue(probe.focused, "the field never took focus, so this measures nothing")
    }

    @Composable
    private fun Stage(page: FieldProbe, open: MutableState<Boolean>, overlay: @Composable (Boolean) -> Unit) {
        KontourTheme {
            OverlayHost(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize()) { Column { Field(page) } }
                overlay(open.value)
            }
        }
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
        const val InDialog = "In the dialog"
        const val InSheet = "In the sheet"
        const val InMenu = "In the menu"
    }
}
