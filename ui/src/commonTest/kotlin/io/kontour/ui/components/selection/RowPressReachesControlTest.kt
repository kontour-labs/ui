package io.kontour.ui.components.selection

import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.down
import androidx.compose.ui.test.up
import io.kontour.ui.interaction.LocalRowInteractionSource
import io.kontour.ui.theme.KontourTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A control inside a row can see the row being pressed.
 *
 * `SelectionRow` puts the tap target on the whole row and hands its control
 * `onCheckedChange = null` — the control shows state, the row decides. But
 * showing state includes showing the *press*: a `Switch` stretches its thumb
 * while held, and it reads that from its own `interactionSource`. Given a null
 * callback it built a source nobody ever pushed to, so tapping the row moved the
 * value and the switch never flinched.
 *
 * Asserted on the local rather than on the stretch itself. The stretch is a
 * `graphicsLayer` scale inside a `Canvas`, which a semantics test cannot see and
 * a golden cannot hold still for; what is actually worth pinning is that the row
 * publishes its presses and that a passenger control receives them.
 */
@OptIn(ExperimentalTestApi::class)
class RowPressReachesControlTest {

    @Test
    fun holdingTheRowPressesTheControlInsideIt() {
        var pressedInside = false

        runComposeUiTest {
            setContent {
                KontourTheme(reduceMotion = true) {
                    SelectionRow(
                        selected = false,
                        onSelectedChange = {},
                        role = Role.Switch,
                        modifier = Modifier.testTag(Tag),
                    ) {
                        +"Show live vehicles"
                        trailing { Spy { pressedInside = pressedInside || it } }
                    }
                }
            }

            // Held, not clicked — a press that is released in the same gesture
            // may never be observed between two frames.
            onNodeWithTag(Tag).performTouchInput { down(Offset(centerX, centerY)) }
            waitForIdle()

            assertTrue(
                pressedInside,
                "the row was being held and nothing inside it knew — a switch in " +
                    "this position cannot stretch its thumb, because the source it " +
                    "reads is one nobody pushes to",
            )

            onNodeWithTag(Tag).performTouchInput { up() }
        }
    }

    /** Reports whatever the row published, if anything. */
    @androidx.compose.runtime.Composable
    private fun Spy(onPressed: (Boolean) -> Unit) {
        val source: InteractionSource? = LocalRowInteractionSource.current
        if (source == null) {
            onPressed(false)
            return
        }
        val pressed by source.collectIsPressedAsState()
        onPressed(pressed)
    }

    private companion object {
        const val Tag = "row"
    }
}
