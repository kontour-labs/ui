package io.kontour.ui.input

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A disabled control sets the arrow, rather than setting nothing.
 *
 * Nothing is not neutral. Compose shows the icon of the deepest node under the
 * pointer that *has* one, so a disabled button that asks for no cursor inside a
 * clickable card shows the card's hand — telling the reader the button will
 * answer a click it will not. The `enabled` parameter's own KDoc said it existed
 * to prevent exactly that, and the early return underneath it made it do the
 * opposite whenever two clickables were nested.
 *
 * Checked as the modifier it produces rather than as a rendered pointer, because
 * nothing here draws a pointer — a frame has no cursor in it.
 */
class DisabledCursorTest {

    @Test
    fun aDisabledControlSetsTheArrowRatherThanNothing() {
        var disabled: Modifier? = null
        val scene = ImageComposeScene(width = 8, height = 8) {
            CompositionLocalProvider(LocalInputModality provides InputModality.Mouse) {
                disabled = Modifier.pointerCursor(enabled = false)
            }
        }
        try {
            scene.render().close()
        } finally {
            scene.close()
        }
        assertEquals(
            Modifier.pointerHoverIcon(PointerIcon.Default),
            disabled,
            "a disabled control set no cursor at all, so an enclosing clickable's hand " +
                "shows through it",
        )
    }
}
