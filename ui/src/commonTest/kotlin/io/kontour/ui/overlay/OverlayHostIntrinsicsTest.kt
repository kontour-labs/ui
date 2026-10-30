package io.kontour.ui.overlay

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import io.kontour.ui.foundation.Text
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A host measured intrinsically, with an overlay open over it, answers for its
 * page and does not throw.
 *
 * An overlay's content is composed in a `SubcomposeLayout` now, where it is first
 * measured — see `ComposedOnMeasure` — and a `SubcomposeLayout` throws if asked
 * for an intrinsic size. A host inside `Modifier.width(IntrinsicSize.Max)`, say,
 * would have crashed the first time a dialog opened in it. The content's own
 * intrinsics are answered with nothing in front of it instead, so the host is
 * as big as its page, whatever is open over it.
 */
@OptIn(ExperimentalTestApi::class)
class OverlayHostIntrinsicsTest {

    @Test
    fun aHostMeasuredForItsWidthWithADialogOpenIsAsWideAsItsPage() = runComposeUiTest {
        setContent {
            KontourTheme {
                Column(Modifier.width(IntrinsicSize.Max)) {
                    OverlayHost(Modifier.testTag("host")) {
                        Box(Modifier.size(120.dp, 300.dp))
                        Dialog(visible = true, onDismissRequest = {}) { Text("Over the page") }
                    }
                }
            }
        }
        waitForIdle()
        onNodeWithText("Over the page").assertExists("the dialog never opened in a host measured for its width")
        assertEquals(120.dp, onNodeWithTag("host").getUnclippedBoundsInRoot().width, "the host's width is its page's")
    }

    @Test
    fun aHostMeasuredForItsHeightWithADialogOpenIsAsTallAsItsPage() = runComposeUiTest {
        setContent {
            KontourTheme {
                Row(Modifier.height(IntrinsicSize.Min)) {
                    OverlayHost(Modifier.testTag("host")) {
                        Box(Modifier.size(300.dp, 140.dp))
                        Dialog(visible = true, onDismissRequest = {}) { Text("Over the page") }
                    }
                }
            }
        }
        waitForIdle()
        onNodeWithText("Over the page").assertExists("the dialog never opened in a host measured for its height")
        assertEquals(140.dp, onNodeWithTag("host").getUnclippedBoundsInRoot().height, "the host's height is its page's")
    }
}
