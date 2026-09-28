package io.kontour.ui.components.display

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * A tag can lead to what it labels — asked for from a phone — and stays a plain
 * label when it does not.
 */
@OptIn(ExperimentalTestApi::class)
class TagTest {

    @Test
    fun aClickableTagIsAButtonThatRuns() = runComposeUiTest {
        var opened = 0
        setContent { KontourTheme(reduceMotion = true) { Tag(onClick = { opened++ }) { +"960" } } }
        val node = onNodeWithText("960", useUnmergedTree = false).fetchSemanticsNode()
        assertEquals(Role.Button, node.config.getOrNull(SemanticsProperties.Role))
        onNodeWithText("960").performClick()
        waitForIdle()
        assertEquals(1, opened)
    }

    @Test
    fun aPlainTagHasNothingToPress() = runComposeUiTest {
        setContent { KontourTheme(reduceMotion = true) { Tag { +"Live" } } }
        val node = onNodeWithText("Live").fetchSemanticsNode()
        assertNull(node.config.getOrNull(SemanticsActions.OnClick), "a label with nothing to do offered a click")
        assertNull(node.config.getOrNull(SemanticsProperties.Role))
    }

    @Test
    fun aDisabledTagIgnoresThePress() = runComposeUiTest {
        var opened = 0
        setContent { KontourTheme(reduceMotion = true) { Tag(onClick = { opened++ }, enabled = false) { +"960" } } }
        val node = onNodeWithText("960").fetchSemanticsNode()
        assertNotNull(node.config.getOrNull(SemanticsProperties.Disabled))
        onNodeWithText("960").performClick()
        waitForIdle()
        assertEquals(0, opened)
        assertFalse(opened > 0)
    }
}
