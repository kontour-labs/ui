package io.kontour.ui.overlay

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A tooltip's icon is beside its words. The bubble's slots are documented as
 * "an icon or a painter beside it", and from 0.5.0 to 0.5.3 they were laid out
 * in a slot with no layout of its own, so the two were drawn on top of each
 * other: *"in the tooltip, the icons overlay the text"*.
 */
@OptIn(ExperimentalTestApi::class)
class TooltipSlotsTest {

    private val glyph = ImageVector.Builder(
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).build()

    @Test
    fun anIconIsBesideTheWordsNotOverThem() = runComposeUiTest {
        setContent {
            KontourTheme(reduceMotion = true) {
                OverlayHost(Modifier.size(400.dp)) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(40.dp)) {
                            Tooltip(visible = true) {
                                icon(glyph, contentDescription = "Saved")
                                +"Saved trips show on Home"
                            }
                        }
                    }
                }
            }
        }
        waitForIdle()
        val icon = onNodeWithContentDescription("Saved", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val words = onNodeWithText("Saved trips show on Home", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue(
            words.left >= icon.right,
            "the words start at ${words.left}, inside the icon, which ends at ${icon.right}",
        )
    }
}
