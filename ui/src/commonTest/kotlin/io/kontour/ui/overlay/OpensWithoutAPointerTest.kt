package io.kontour.ui.overlay

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.down
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.withKeysDown
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.up
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.action.IconButton
import io.kontour.ui.input.InputModality
import io.kontour.ui.input.LocalInputModality
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Theme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Overlays that open from something other than a plain press do so whatever
 * the control under them is.
 *
 * Found in the November 2026 audit:
 * - `Modifier.tooltip` read focus from its own interaction source, which the
 *   control it was on never wrote to, so on a button with no
 *   `interactionSource` passed it never opened as the keyboard reached it.
 * - `ContextMenuArea` looked for its long press in the main pass, after a
 *   `clickable` inside had taken the press, so over a row or a card that can
 *   be pressed a long press never opened it.
 * - `ContextMenuArea` had no keyboard route at all, where `DocumentTabs` had
 *   Shift+F10 and the menu key.
 */
@OptIn(ExperimentalTestApi::class)
class OpensWithoutAPointerTest {

    @Test
    fun aTooltipOnAButtonOpensAsTheKeyboardReachesIt() = runComposeUiTest {
        mainClock.autoAdvance = false
        setContent {
            Screen(InputModality.Keyboard) {
                IconButton(
                    onClick = {},
                    icon = Theme.icons.search,
                    contentDescription = "Find a stop",
                    modifier = Modifier.testTag("control").tooltip("Filter routes"),
                )
            }
        }
        frames(10)
        onNodeWithTag("control").requestFocus()
        frames(30)
        onNodeWithText("Filter routes").assertExists("a tooltip did not open as the keyboard focused its button")
    }

    @Test
    fun aLongPressOverSomethingPressableOpensTheContextMenuAndPressesNothing() = runComposeUiTest {
        mainClock.autoAdvance = false
        var pressed = 0
        setContent {
            Screen(InputModality.Touch) {
                ContextMenuArea(menu = { item("Rename") {} }) {
                    Box(Modifier.size(200.dp, 60.dp).testTag("row").clickable { pressed++ })
                }
            }
        }
        frames(10)
        onNodeWithTag("row").performTouchInput { down(center) }
        frames(60)
        onNodeWithTag("row").performTouchInput { up() }
        frames(30)
        onNodeWithText("Rename").assertExists("a long press over a pressable row did not open its context menu")
        assertEquals(0, pressed, "the long press that opened the menu pressed the row as well")
    }

    @Test
    fun shiftF10OrTheMenuKeyOpensTheContextMenuFromTheFocusInside() {
        for (keys in listOf(listOf(Key.ShiftLeft, Key.F10), listOf(Key.Menu))) {
            runComposeUiTest {
                mainClock.autoAdvance = false
                setContent {
                    Screen(InputModality.Keyboard) {
                        ContextMenuArea(menu = { item("Rename") {} }) {
                            Box(Modifier.size(200.dp, 60.dp).testTag("row").clickable {})
                        }
                    }
                }
                frames(10)
                onNodeWithTag("row").requestFocus()
                onNodeWithTag("row").performKeyInput { withKeysDown(keys.dropLast(1)) { pressKey(keys.last()) } }
                frames(30)
                onNodeWithText("Rename").assertExists("${keys.joinToString("+")} did not open the context menu")
            }
        }
    }

    @Composable
    private fun Screen(modality: InputModality, content: @Composable () -> Unit) {
        KontourTheme(reduceMotion = true) {
            CompositionLocalProvider(LocalInputModality provides modality) {
                OverlayHost(Modifier.fillMaxSize()) {
                    Box(Modifier.padding(start = 40.dp, top = 160.dp)) { content() }
                }
            }
        }
    }

    private fun ComposeUiTest.frames(count: Int) = repeat(count) { mainClock.advanceTimeByFrame() }
}
