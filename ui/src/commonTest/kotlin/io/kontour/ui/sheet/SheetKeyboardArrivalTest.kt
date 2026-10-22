package io.kontour.ui.sheet

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A sheet opened with a field that takes focus rises with the keyboard, not
 * after it, and the page behind recedes once, without swinging back.
 *
 * The keyboard's inset is the sheet's bottom padding, so while it slides up
 * the sheet grows on every frame, and its anchors move with it. Foundation's
 * `animateTo` restarts on every change of anchors, and a restarted animation
 * spends its first frame where it is: the sheet stood still for the whole of
 * the keyboard's slide and only then came up. The page's recede, which is how
 * far the sheet has come against how far it has to go, swung back as the
 * destination climbed away from a sheet that was not moving.
 *
 * And closed, it lets go of the keyboard as it sets off.
 *
 * Reported from Android, from the energy app's *Schedule a run*: *"the backdrop
 * scale sort of shrinks and then expands a bit, and then shrinks again when the
 * sheet opens. however the sheet doesn't open over the ime for like half a
 * second after the ime opens"*.
 */
@OptIn(ExperimentalTestApi::class)
class SheetKeyboardArrivalTest {

    private class Frame(val shown: Float, val arrived: Float, val keyboard: Dp)

    private fun open(keyboardFrom: Int): List<Frame> {
        val frames = mutableListOf<Frame>()
        runComposeUiTest {
            var visible by mutableStateOf(false)
            var keyboard by mutableStateOf(0.dp)
            var state: SheetState? = null
            mainClock.autoAdvance = false
            setContent {
                KontourTheme {
                    OverlayHost(Modifier.size(400.dp, 800.dp)) {
                        val sheet = rememberSheetState()
                        state = sheet
                        ModalBottomSheet(
                            visible = visible,
                            onDismissRequest = { visible = false },
                            state = sheet,
                            windowInsets = WindowInsets(bottom = keyboard),
                        ) { padding ->
                            // Padded as a sheet's content is: the keyboard's
                            // inset is part of what it is handed.
                            Box(Modifier.padding(padding).fillMaxWidth().height(240.dp))
                        }
                    }
                }
            }
            mainClock.advanceTimeByFrame()
            visible = true
            repeat(Frames) { frame ->
                // As Android's own: a dozen frames from nothing to its height.
                val rising = frame - keyboardFrom
                if (rising >= 0) keyboard = Keyboard * ((rising + 1f) / KeyboardFrames).coerceAtMost(1f)
                mainClock.advanceTimeByFrame()
                val sheet = state!!
                frames += Frame(sheet.visibleHeight, sheet.arrivedFraction, keyboard)
            }
        }
        return frames
    }

    @Test
    fun theSheetRisesWhileTheKeyboardDoes() {
        val frames = open(keyboardFrom = KeyboardFrom)
        val rising = frames.withIndex().filter { (i, f) -> i > 0 && f.keyboard > 0.dp && f.keyboard < Keyboard }
        val still = rising.filter { (i, f) -> f.shown <= frames[i - 1].shown + 0.5f }
        assertTrue(
            still.isEmpty(),
            "the sheet stood still on ${still.size} of the ${rising.size} frames the keyboard was rising: " +
                frames.joinToString { "${it.shown.toInt()}px" },
        )
    }

    @Test
    fun thePageRecedesOnceWithoutSwingingBack() {
        val frames = open(keyboardFrom = KeyboardFrom)
        val back = frames.zipWithNext().filter { (a, b) -> b.arrived < a.arrived - 0.005f }
        assertTrue(
            back.isEmpty(),
            "the page came forward again on ${back.size} frames of the opening: " +
                frames.joinToString { "${(it.arrived * 100).toInt()}%" },
        )
        assertTrue(frames.last().arrived > 0.99f, "the sheet never arrived: ${frames.last().arrived}")
    }

    /**
     * Closed with a field in it focused, the sheet lets go of the keyboard as
     * it sets off, not when it has landed and its field has gone with it.
     *
     * Reported as *"closing sheets with a keyboard open makes the keyboard take
     * just like half a second too long to close"*.
     */
    @Test
    fun theKeyboardGoesAsTheSheetDoes() = runComposeUiTest {
        var visible by mutableStateOf(true)
        var hides = 0
        val keyboard = object : SoftwareKeyboardController {
            override fun show() = Unit
            override fun hide() {
                hides++
            }
        }
        val field = FocusRequester()
        var state: SheetState? = null
        setContent {
            CompositionLocalProvider(LocalSoftwareKeyboardController provides keyboard) {
                KontourTheme {
                    OverlayHost(Modifier.size(400.dp, 800.dp)) {
                        val sheet = rememberSheetState(detents = listOf(SheetDetent.Hidden, SheetDetent.Expanded))
                        state = sheet
                        ModalBottomSheet(visible = visible, onDismissRequest = { visible = false }, state = sheet) { padding ->
                            BasicTextField(
                                rememberTextFieldState("Dishwasher"),
                                Modifier.padding(padding).focusRequester(field).fillMaxWidth().height(240.dp),
                            )
                        }
                    }
                }
            }
        }
        waitForIdle()
        runOnIdle { field.requestFocus() }
        waitForIdle()
        val before = hides
        val open = state!!.visibleHeight

        mainClock.autoAdvance = false
        visible = false
        repeat(2) { mainClock.advanceTimeByFrame() }
        assertTrue(hides > before, "the keyboard was not told to go as the sheet set off")
        assertTrue(
            state!!.visibleHeight > open / 2,
            "the sheet was already ${state!!.visibleHeight.toInt()} of ${open.toInt()}px down: this is not its first frames",
        )
    }

    private companion object {
        const val Frames = 60

        /**
         * A few frames after the sheet sets off, as on a phone, where the
         * keyboard's window takes a moment to come: the sheet had begun to
         * rise, and the page to recede, when it stopped.
         */
        const val KeyboardFrom = 6
        const val KeyboardFrames = 12f
        val Keyboard = 300.dp
    }
}
