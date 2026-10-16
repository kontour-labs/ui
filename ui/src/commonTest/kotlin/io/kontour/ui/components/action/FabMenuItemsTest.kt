package io.kontour.ui.components.action

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kontour.ui.foundation.SystemIcons
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * An open FAB menu whose items change under it.
 */
@OptIn(ExperimentalTestApi::class)
class FabMenuItemsTest {

    /**
     * An item added to an open menu is shown, and nothing throws.
     *
     * The open menu read its items fresh and each item's spring from when it
     * was shown, so a fourth item looked for a fourth spring in a list of three.
     */
    @Test
    fun anItemAddedToAnOpenMenuIsShown() = runComposeUiTest {
        var count by mutableStateOf(3)
        setContent {
            KontourTheme(reduceMotion = false) {
                OverlayHost(Modifier.fillMaxSize()) {
                    FabMenu(
                        expanded = true,
                        onExpandedChange = {},
                        icon = SystemIcons.Default.close,
                        contentDescription = "Add",
                    ) {
                        repeat(count) { index -> item(SystemIcons.Default.close, "Item $index", onClick = {}) }
                    }
                }
            }
        }
        waitForIdle()
        fun shown(label: String) =
            onAllNodes(hasText(label) or hasContentDescription(label), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        assertTrue(shown("Item 2"), "the menu did not open, so this checks nothing")

        count = 4
        waitForIdle()

        assertTrue(shown("Item 3"), "the item added to the open menu is not there")
    }
}
