package io.kontour.ui.sheet

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * On a wide window a modal bottom sheet stops at its own, wider cap, and a sheet
 * beside the page at the narrower one it always had.
 *
 * Asked for as *"can we make the modal bottom sheet a bit wider on large screens
 * by default? it looks a bit daft. just the modal one though, not the regular
 * bottom sheet"*.
 */
@OptIn(ExperimentalTestApi::class)
class ModalSheetWidthTest {

    private fun ComposeUiTest.widthOf(modal: Boolean): Float {
        setContent {
            KontourTheme(reduceMotion = true) {
                OverlayHost(Modifier.size(1200.dp, 800.dp)) {
                    val content: @Composable () -> Unit = {
                        Box(Modifier.fillMaxWidth().height(120.dp).testTag("content"))
                    }
                    if (modal) {
                        ModalBottomSheet(visible = true, onDismissRequest = {}) { content() }
                    } else {
                        BottomSheet(
                            state = rememberSheetState(
                                detents = listOf(SheetDetent.Hidden, SheetDetent.Expanded),
                                initialDetent = SheetDetent.Expanded,
                            ),
                        ) { content() }
                    }
                }
            }
        }
        waitForIdle()
        val bounds = onNodeWithTag("content").fetchSemanticsNode().boundsInRoot
        return with(density) { bounds.width.toDp().value }
    }

    @Test
    fun aModalSheetIsWiderThanASheetBesideThePage() = runComposeUiTest {
        val width = widthOf(modal = true)
        assertTrue(width > 480f && width <= 560f, "a modal sheet in a 1200dp window is ${width}dp wide, not its own 560dp cap")
    }

    @Test
    fun aSheetBesideThePageKeepsItsCap() = runComposeUiTest {
        val width = widthOf(modal = false)
        assertTrue(width <= 480f, "a sheet beside the page in a 1200dp window is ${width}dp wide, past its 480dp cap")
    }
}
