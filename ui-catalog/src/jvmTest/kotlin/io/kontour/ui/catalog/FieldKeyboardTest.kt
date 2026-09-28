package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.text.TextField
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A field that takes focus is brought into view whole: label, frame and the line
 * under it — not only the line of text being typed.
 *
 * Reported from a phone: *"when the ime opens after clicking it, it should move the
 * screen up so the bottom of the field is visible, not just the bottom of the
 * text"*. Foundation brings the *caret* into view, and a caret is one line in the
 * middle of a frame with a supporting line under it.
 *
 * There is no keyboard here, so the viewport is simply short: the field starts
 * inside it and runs off its bottom, which is the position a keyboard puts a field
 * in. The other half of the report — the blank keyboard-sized gap — is two hosts
 * each making room for the same keyboard, and needs a device to see.
 */
class FieldKeyboardTest {

    @Test
    fun focusingAFieldBringsItsSupportingLineIntoView() {
        var field = Rect.Zero
        Scene(width = Size, height = Size) {
            Column(
                Modifier
                    .fillMaxSize()
                    .background(Color.White)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                Spacer(Modifier.height(Above.dp))
                TextField(
                    state = rememberTextFieldState(),
                    label = "Destination",
                    supporting = "The stop you are travelling to",
                    modifier = Modifier.fillMaxWidth().reportBounds { field = it },
                )
                Spacer(Modifier.height(400.dp))
            }
        }.use { scene ->
            scene.frames(4)
            assertTrue(
                field.top < Size && field.bottom > Size,
                "the field should start on screen and run off its bottom before it is " +
                    "focused, or this is not the case under test ($field in a ${Size}px view)",
            )
            // The visible top of the frame, just under the label.
            scene.tap(Offset(field.center.x, field.top + 50f))
            scene.frames(40)
        }
        assertTrue(
            field.bottom <= Size + 0.5f,
            "focused, the field's bottom is at ${field.bottom}px in a ${Size}px view — its " +
                "supporting line is still out of sight. Bringing only the caret into " +
                "view is what was reported.",
        )
    }

    private companion object {
        const val Size = 600

        /** Puts the field's label and the top of its frame on screen, and no more. */
        const val Above = 250
    }
}
