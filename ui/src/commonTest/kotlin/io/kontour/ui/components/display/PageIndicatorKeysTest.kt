package io.kontour.ui.components.display

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A page indicator that can be pressed is one Tab stop, and the arrows move
 * along it, the way the text runs. carousel.md counts the keyboard among the
 * four ways in; the dots took a finger and a screen reader and not a keyboard
 * until the November 2026 audit.
 */
@OptIn(ExperimentalTestApi::class)
class PageIndicatorKeysTest {

    private fun keys(direction: LayoutDirection, pressed: List<Key>): List<Int> {
        val asked = mutableListOf<Int>()
        runComposeUiTest {
            setContent {
                KontourTheme(reduceMotion = true) {
                    CompositionLocalProvider(LocalLayoutDirection provides direction) {
                        val state = rememberCarouselState { 5 }
                        PageIndicator(state = state, onPageClick = { asked += it }, modifier = Modifier.testTag("dots"))
                    }
                }
            }
            onNodeWithTag("dots").requestFocus()
            for (key in pressed) onNodeWithTag("dots").performKeyInput { pressKey(key) }
            waitForIdle()
        }
        return asked
    }

    @Test
    fun theArrowsAndHomeAndEndMoveAlongIt() {
        assertEquals(listOf(1, 4, 0), keys(LayoutDirection.Ltr, listOf(Key.DirectionRight, Key.MoveEnd, Key.MoveHome)))
    }

    @Test
    fun rightToLeftTheLeftArrowGoesOn() {
        assertEquals(listOf(1), keys(LayoutDirection.Rtl, listOf(Key.DirectionLeft)))
    }
}
