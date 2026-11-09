package io.kontour.ui.components.selection

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The arrows move a rating's score, a half at a time where it allows halves.
 *
 * Enter and Space on a mark give its whole score, so a rating with `allowHalf`
 * had no way to a half from the keyboard. Found in the November 2026 audit.
 */
@OptIn(ExperimentalTestApi::class)
class RatingKeysTest {

    private fun ComposeUiTest.rating(
        start: Float,
        allowHalf: Boolean,
        direction: LayoutDirection = LayoutDirection.Ltr,
        allowZero: Boolean = true,
    ): () -> Float {
        var value by mutableFloatStateOf(start)
        setContent {
            KontourTheme(reduceMotion = true) {
                CompositionLocalProvider(LocalLayoutDirection provides direction) {
                    Rating(
                        value = value,
                        onValueChange = { value = it },
                        contentDescription = "Your rating",
                        allowHalf = allowHalf,
                        allowZero = allowZero,
                    )
                }
            }
        }
        waitForIdle()
        onAllNodes(isSelectable())[0].requestFocus()
        return { value }
    }

    private fun ComposeUiTest.press(key: Key) {
        onNode(isFocused()).performKeyInput { pressKey(key) }
        waitForIdle()
    }

    @Test
    fun withHalvesTheArrowsMoveAHalfAndHomeAndEndGoToTheEnds() = runComposeUiTest {
        val value = rating(start = 0f, allowHalf = true)
        val seen = mutableListOf<Float>()
        for (key in listOf(Key.DirectionRight, Key.DirectionRight, Key.DirectionUp, Key.DirectionLeft, Key.MoveEnd, Key.DirectionDown, Key.MoveHome)) {
            press(key)
            seen += value()
        }
        assertEquals(listOf(0.5f, 1f, 1.5f, 1f, 5f, 4.5f, 0f), seen)
    }

    @Test
    fun withoutHalvesTheArrowsMoveAMark() = runComposeUiTest {
        val value = rating(start = 2f, allowHalf = false)
        press(Key.DirectionRight)
        assertEquals(3f, value())
        press(Key.DirectionLeft)
        press(Key.DirectionLeft)
        assertEquals(1f, value())
    }

    @Test
    fun rightToLeftTheLeftArrowGivesMore() = runComposeUiTest {
        val value = rating(start = 1f, allowHalf = true, direction = LayoutDirection.Rtl)
        press(Key.DirectionLeft)
        assertEquals(1.5f, value())
    }

    /** Without zero, the least a half rating can be is a half. */
    @Test
    fun homeIsTheLeastItMayBe() = runComposeUiTest {
        val value = rating(start = 3f, allowHalf = true, allowZero = false)
        press(Key.MoveHome)
        assertEquals(0.5f, value())
    }
}
