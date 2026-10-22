package io.kontour.ui.components.list

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.SystemIcons
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test

/**
 * What a swipe row offers a screen reader in place of the gesture.
 */
@OptIn(ExperimentalTestApi::class)
class SwipeActionsSemanticsTest {

    /**
     * Disabled, the row offers none of its actions.
     *
     * The swipe was withheld and the custom actions were not, so a row that
     * could not be swiped still deleted itself for a screen reader that asked.
     */
    @Test
    fun aDisabledRowOffersNoActions() = runComposeUiTest {
        var enabled by mutableStateOf(true)
        val delete = SwipeAction(label = "Delete", icon = SystemIcons.Default.close, onAction = {}, background = Color.Red)
        setContent {
            KontourTheme(reduceMotion = true) {
                SwipeActions(modifier = Modifier.fillMaxWidth().height(72.dp), enabled = enabled, end = listOf(delete)) {
                    Box(Modifier.fillMaxWidth().height(72.dp))
                }
            }
        }
        val offersDelete = SemanticsMatcher("offers Delete") { node ->
            node.config.getOrElse(SemanticsActions.CustomActions) { emptyList() }.any { it.label == "Delete" }
        }
        waitForIdle()
        onAllNodes(offersDelete).assertCountEquals(1)

        enabled = false
        waitForIdle()

        onAllNodes(offersDelete).assertCountEquals(0)
    }
}
