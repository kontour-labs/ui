package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.DotsVertical
import io.kontour.ui.components.action.IconButton
import io.kontour.ui.nav.TopBar
import io.kontour.ui.nav.TopBarStyle
import io.kontour.ui.theme.KontourTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A centred top bar's title is centred on the **bar**.
 *
 * Reported as: hide the back button and the title is no longer centred, it moves
 * off to the left a bit. It does, and a `Row` of leading, weighted title,
 * trailing cannot do otherwise — a weighted child is centred in the space its
 * siblings left over, which is the middle of the bar only when the two sides
 * happen to be the same width.
 *
 * The title's own centre against the bar's, with a back button and without one.
 * Both have to land in the middle; the second is the reported case and the first
 * is what stops a fix that simply moves the error to the other configuration.
 */
@OptIn(ExperimentalTestApi::class)
class TopBarCentringTest {

    @Test
    fun aCentredTitleSitsInTheMiddleWithOrWithoutABackButton() {
        val withBack = titleCentre(back = true)
        val withoutBack = titleCentre(back = false)

        assertTrue(
            abs(withBack - BarCentre) <= Tolerance,
            "with a back button the title's centre was ${withBack}dp against the " +
                "bar's ${BarCentre}dp",
        )
        assertTrue(
            abs(withoutBack - BarCentre) <= Tolerance,
            "with the back button hidden the title's centre was ${withoutBack}dp " +
                "against the bar's ${BarCentre}dp — it is centred on what the " +
                "controls left over rather than on the bar",
        )
    }

    /** The x of the centre of the title, in dp, in a 400dp bar. */
    private fun titleCentre(back: Boolean): Float {
        var centre = -1f
        runComposeUiTest {
            setContent {
                KontourTheme {
                    Box(Modifier.fillMaxSize().background(Color.White)) {
                        TopBar(
                            modifier = Modifier.width(BarWidth.dp),
                            style = TopBarStyle.Centred,
                            onBack = if (back) ({}) else null,
                            actions = {
                                IconButton(
                                    icon = Tabler.Outline.DotsVertical,
                                    contentDescription = "More",
                                    onClick = {},
                                )
                            },
                        ) {
                            +Title
                        }
                    }
                }
            }
            val bounds = onNodeWithText(Title).fetchSemanticsNode().boundsInRoot
            centre = (bounds.left + bounds.right) / 2f
        }
        return centre
    }

    private companion object {
        const val Title = "Perth Underground"
        const val BarWidth = 400

        /** The middle of the bar. The harness runs at density 1, so dp is px. */
        const val BarCentre = BarWidth / 2f

        /**
         * Half a character. Tight enough that centring on the leftover space
         * fails it — a back button is 48dp, so that error is 24dp — and loose
         * enough to survive text measurement rounding.
         */
        const val Tolerance = 6f
    }
}
