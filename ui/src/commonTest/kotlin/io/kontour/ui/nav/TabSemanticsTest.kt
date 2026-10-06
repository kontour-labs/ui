package io.kontour.ui.nav

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A tab says it is a tab, and whether it is the chosen one.
 *
 * Reported: *"Tabs show up as plain buttons to screen readers, not as tabs."*
 * In the browser they do, under Compose 1.12: its web layer overrides the role
 * of anything clickable with `button` and writes no `aria-selected`. Compose
 * 1.13 keeps the role, and its main branch writes the state. The library's
 * part is to pass both, which this holds it to on every platform — Android's
 * and iOS's trees take them as written today.
 */
@OptIn(ExperimentalTestApi::class)
class TabSemanticsTest {

    @Test
    fun eachTabIsATabAndSaysWhetherItIsChosen() = runComposeUiTest {
        setContent {
            KontourTheme {
                TabBar {
                    Tab(selected = true, onClick = {}, key = "live") { +"Live" }
                    Tab(selected = false, onClick = {}, key = "planned") { +"Planned" }
                }
            }
        }
        for ((label, chosen) in listOf("Live" to true, "Planned" to false)) {
            val config = onNodeWithText(label, useUnmergedTree = false).fetchSemanticsNode().config
            assertEquals(Role.Tab, config.getOrNull(SemanticsProperties.Role), "$label is not a tab")
            assertEquals(chosen, config.getOrNull(SemanticsProperties.Selected), "$label's selected state")
        }
    }
}
