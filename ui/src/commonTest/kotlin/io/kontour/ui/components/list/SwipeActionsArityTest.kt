package io.kontour.ui.components.list

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.SystemIcons
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Three actions a side, and the fourth says so.
 *
 * The limit is not arbitrary and the message has to carry the reason, because
 * the caller cannot see the arithmetic: one action's target is 88dp, so three is
 * 264dp of travel and already most of a phone's width. A fourth is not reachable
 * on the device the gesture exists for, and a swipe row that silently hides its
 * last action is worse than one that refuses it.
 */
@OptIn(ExperimentalTestApi::class)
class SwipeActionsArityTest {

    private fun actions(count: Int) = List(count) { index ->
        SwipeAction(
            label = "Action $index",
            icon = SystemIcons.Close,
            onAction = {},
            background = Color.Red,
        )
    }

    private fun render(start: Int = 0, end: Int = 0) {
        runComposeUiTest {
            setContent {
                KontourTheme {
                    SwipeActions(
                        modifier = Modifier.fillMaxWidth().height(72.dp),
                        start = actions(start),
                        end = actions(end),
                    ) {
                        Box(Modifier.fillMaxWidth().height(72.dp))
                    }
                }
            }
        }
    }

    @Test
    fun threeASideIsAllowed() {
        render(start = 3, end = 3)
    }

    @Test
    fun aFourthActionIsRefusedAndSaysWhichSide() {
        for (side in listOf("start", "end")) {
            val thrown = try {
                if (side == "start") render(start = 4) else render(end = 4)
                null
            } catch (e: IllegalArgumentException) {
                e
            } catch (e: Throwable) {
                // The test harness wraps what a composition throws; the
                // precondition is still the cause somewhere down the chain.
                generateSequence(e) { it.cause }
                    .filterIsInstance<IllegalArgumentException>()
                    .firstOrNull()
            }

            val message = thrown?.message ?: fail(
                "a fourth $side action was accepted. Three 88dp targets is " +
                    "already most of a phone's width, so the fourth is a target " +
                    "nobody can reach — which is a thing to refuse, not to draw.",
            )
            assertTrue(
                message.contains(side),
                "the message for a fourth $side action does not name the side " +
                    "it means: \"$message\". A caller with actions on both sides " +
                    "cannot act on it otherwise.",
            )
            assertTrue(
                message.contains("SwipeActions"),
                "the message does not name the component: \"$message\"",
            )
        }
    }
}
