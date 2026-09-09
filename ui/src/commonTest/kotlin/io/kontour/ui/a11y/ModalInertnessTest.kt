package io.kontour.ui.a11y

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kontour.ui.components.action.Button
import io.kontour.ui.overlay.Dialog
import io.kontour.ui.overlay.LocalOverlayHost
import io.kontour.ui.overlay.OverlayEntry
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.overlay.OverlayLayer
import io.kontour.ui.overlay.ScrimStyle
import io.kontour.ui.sheet.ModalBottomSheet
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What is behind a *dimmed* overlay is gone, not merely covered.
 *
 * A modal overlay dims the page, takes the focus and eats the taps aimed past
 * it. Every one of those is a fact about *sighted, pointer or keyboard* use, and
 * none of them was a fact about the semantics tree — which was left untouched. A
 * button under an open sheet's scrim kept its click action and its enabled flag,
 * so a screen reader could still find it, announce it and activate it: reaching
 * a control the app had just decided nobody should reach.
 *
 * ### How it was found, which is the interesting part
 *
 * Not by an audit. `EverythingRespondsTest` in `:ui-catalog` — a test whose
 * entire subject is "a specimen wired to a callback that goes nowhere looks
 * alive and is not" — began failing the moment a sheet demo was given a knob
 * that opens it at rest. It named the button behind the scrim as a dead control,
 * which is exactly what that button is. The instrument could have reported this
 * at any point; nothing in the repository had ever left a modal open long enough
 * to ask it.
 *
 * ### `canFocus` was already half the sentence
 *
 * `OverlayHost` has carried `focusProperties { canFocus = !trapping }` on the
 * content sibling since focus trapping was written, under the comment "focus
 * cannot enter content that is behind a modal overlay". This is that intention,
 * applied to the one traversal order that is not the assistive one.
 *
 * ### Scoped by the dimming, which is what the last two tests are for
 *
 * `ScrimStyle` already draws this line, in its own KDoc: `None` is "no dimming,
 * and pointer events pass through — tooltips, toasts"; `Transparent` is "no
 * dimming, but pointer events are blocked — menus"; `Dimmed` is "dialogs, modal
 * sheets". Only the third is a statement that the page is not available, and
 * making the assistive tree agree with what a sighted user is shown is the whole
 * of this change.
 *
 * The first cut keyed off `trapFocus` and was wrong, which `OverlayScrollTest`
 * said immediately: a menu traps focus so that a keyboard user cannot arrow out
 * of it, while the page behind it keeps scrolling and `BackdropStyle`'s KDoc
 * describes it as "content the user is still reading". Taking that page away
 * from a screen reader — and only from a screen reader — would have been a
 * downgrade dressed as a fix.
 */
@OptIn(ExperimentalTestApi::class)
class ModalInertnessTest {

    @Test
    fun anOpenSheetHidesWhatIsUnderIt() = runComposeUiTest {
        val open = mutableStateOf(false)
        setContent {
            Stage(open) { ModalBottomSheet(visible = it, onDismissRequest = {}) { Inside(InSheet) } }
        }
        assertBehind(1, "with no sheet open")

        open.value = true
        waitForIdle()
        onNodeWithText(InSheet).assertExists("the sheet did not open, so this measures nothing")
        assertBehind(0, "with a modal bottom sheet open over it")
    }

    @Test
    fun anOpenDialogHidesWhatIsUnderIt() = runComposeUiTest {
        val open = mutableStateOf(false)
        setContent {
            Stage(open) { Dialog(visible = it, onDismissRequest = {}) { Inside(InDialog) } }
        }
        assertBehind(1, "with no dialog open")

        open.value = true
        waitForIdle()
        onNodeWithText(InDialog).assertExists("the dialog did not open, so this measures nothing")
        assertBehind(0, "with a dialog open over it")
    }

    /** `ScrimStyle.None`: an addition to the page, not a replacement of it. */
    @Test
    fun anUndimmedOverlayLeavesThePageAlone() = runComposeUiTest {
        val open = mutableStateOf(false)
        setContent { Stage(open) { Banner(it, ScrimStyle.None, InToast) } }
        assertBehind(1, "with no overlay open")

        open.value = true
        waitForIdle()
        onNodeWithText(InToast).assertExists("the overlay did not show, so this measures nothing")
        assertBehind(1, "with an undimmed overlay showing")
    }

    /**
     * `ScrimStyle.Transparent`, which is a menu: blocking, but not modal.
     *
     * This is the arm that failed when the rule was written against `trapFocus`,
     * and it is the one worth keeping: a dropdown traps focus for a keyboard
     * reason and is in every other respect a thing you read the page around.
     */
    @Test
    fun aBlockingButUndimmedOverlayLeavesThePageReadable() = runComposeUiTest {
        val open = mutableStateOf(false)
        setContent { Stage(open) { Banner(it, ScrimStyle.Transparent, InMenu) } }
        assertBehind(1, "with no menu open")

        open.value = true
        waitForIdle()
        onNodeWithText(InMenu).assertExists("the menu did not open, so this measures nothing")
        assertBehind(1, "with a menu open over it")
    }

    /**
     * One overlay entry, shown for as long as the test wants it.
     *
     * A raw entry rather than a real `Toast` or `Menu`: both schedule or anchor
     * themselves in ways a test would have to race or lay out around, and the
     * property under examination is [ScrimStyle] alone.
     */
    @Composable
    private fun Banner(visible: Boolean, scrim: ScrimStyle, label: String) {
        val host = LocalOverlayHost.current
        LaunchedEffect(visible) {
            if (visible) {
                host.show(
                    OverlayEntry(
                        key = label,
                        layer = OverlayLayer.Menu,
                        scrim = scrim,
                        content = { Inside(label) },
                    )
                )
            }
        }
    }

    /** The page: one button, plus whatever overlay the test puts over it. */
    @Composable
    private fun Stage(open: MutableState<Boolean>, overlay: @Composable (Boolean) -> Unit) {
        KontourTheme {
            OverlayHost(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize()) { Button(onClick = {}) { +Underneath } }
                overlay(open.value)
            }
        }
    }

    @Composable
    private fun Inside(label: String) {
        Button(onClick = {}) { +label }
    }

    /**
     * How many nodes reading "Underneath" the semantics tree offers.
     *
     * A count rather than exists/does-not-exist, so the failure says which
     * direction it went — a page that has become unreachable when nothing covers
     * it and a page still reachable under a scrim are opposite defects, and
     * `assertDoesNotExist` takes no message to tell them apart.
     */
    private fun ComposeUiTest.assertBehind(expected: Int, situation: String) {
        assertEquals(
            expected,
            onAllNodesWithText(Underneath).fetchSemanticsNodes().size,
            "the \"$Underneath\" button $situation: assistive technology reads " +
                "the semantics tree and nothing else, so a control it can still " +
                "find under a modal is a control it can still press",
        )
    }

    private companion object {
        const val Underneath = "Underneath"
        const val InSheet = "In the sheet"
        const val InDialog = "In the dialog"
        const val InToast = "In the toast"
        const val InMenu = "In the menu"
    }
}
