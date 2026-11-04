package io.kontour.ui.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * A detent a sheet is given after it was created is a place it can go: by
 * code and by hand. Reported from an app: *"Kontour UI's `SheetState` only ever
 * allows the detents it was created with: assigning `detents` later never adds
 * one"*, true from 0.4.3 to 0.5.2, so it made every sheet with every detent it
 * might need. The allowed list read the constructor's parameter, which shadows
 * the property in an initialiser.
 */
@OptIn(ExperimentalTestApi::class)
class SheetDetentsLaterTest {

    private fun ComposeUiTest.settle() {
        repeat(90) { mainClock.advanceTimeByFrame() }
    }

    /** A sheet created with only a peek and half, given full height once [grown]. */
    private fun ComposeUiTest.sheet(grown: () -> Boolean, onState: (SheetState, CoroutineScope) -> Unit) {
        setContent {
            KontourTheme(reduceMotion = true) {
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    OverlayHost(Modifier.fillMaxSize()) {
                        val peek = SheetDetent.peek(160.dp)
                        val detents = if (grown()) listOf(peek, SheetDetent.Half, SheetDetent.Expanded) else listOf(peek, SheetDetent.Half)
                        val state = rememberSheetState(detents = detents, initialDetent = peek)
                        onState(state, rememberCoroutineScope())
                        BottomSheet(state = state, presentation = SheetPresentation.Edge, dragHandle = { DragHandle(Modifier.testTag("handle")) }) {
                            Box(Modifier.fillMaxWidth().height(2000.dp))
                        }
                    }
                }
            }
        }
    }

    @Test
    fun aDetentAssignedLaterIsAllowed() {
        val peek = SheetDetent.peek(160.dp)
        val state = SheetState(listOf(peek, SheetDetent.Half), peek) { true }
        state.detents = listOf(peek, SheetDetent.Half, SheetDetent.Expanded)
        assertEquals(listOf(peek, SheetDetent.Half, SheetDetent.Expanded), state.allowedDetents)
    }

    @Test
    fun aDetentAddedLaterCanBeMovedToInCode() = runComposeUiTest {
        var grown by mutableStateOf(false)
        lateinit var state: SheetState
        lateinit var scope: CoroutineScope
        mainClock.autoAdvance = false
        sheet({ grown }) { s, c -> state = s; scope = c }
        settle()
        grown = true
        settle()
        scope.launch { state.animateTo(SheetDetent.Expanded) }
        settle()
        assertEquals(SheetDetent.Expanded, state.currentDetent, "a detent added later was refused")
    }

    @Test
    fun aDetentAddedLaterCanBeDraggedTo() = runComposeUiTest {
        var grown by mutableStateOf(false)
        lateinit var state: SheetState
        mainClock.autoAdvance = false
        sheet({ grown }) { s, _ -> state = s }
        settle()
        grown = true
        settle()
        // Up the whole window from the handle, and let go still.
        onNodeWithTag("handle").performTouchInput {
            down(center)
            repeat(40) { moveBy(Offset(0f, -40f)); advanceEventTime(16) }
            advanceEventTime(400)
            up()
        }
        settle()
        assertEquals(SheetDetent.Expanded, state.currentDetent, "a detent added later could not be dragged to")
    }
}
