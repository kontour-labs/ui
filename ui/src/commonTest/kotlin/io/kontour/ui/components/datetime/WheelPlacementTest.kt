package io.kontour.ui.components.datetime

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.theme.KontourTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The selected row is the row in the middle.
 *
 * Reported twice, the second time as *"the highlighted row is one too high"* on
 * the infinite wheel. It stayed open through round 25 because it could not be
 * measured: `InfiniteWheelTest`'s KDoc records the attempt — three bands of ink
 * 80px apart with the strongest one row above centre — and then says why that
 * reading proves nothing. `wheelFade` bottoms out at **0.2 alpha rather than
 * zero**, so a threshold on rendered pixels cannot tell a row that is absent
 * from one that is present and faint, and the same picture is consistent with an
 * off-by-one, with a row collapse, and with the detector simply missing the
 * faint rows.
 *
 * ### Why this can answer it
 *
 * Every row is a real `Text` node with the item's own label on it, whatever its
 * alpha. So this asks the semantics tree where a row *is* rather than asking the
 * pixels whether it is bright: `onNodeWithText(label).getBoundsInRoot()` returns
 * the same rectangle at 0.2 alpha as at 1.0. Alpha cannot lie to it, and neither
 * can the shrink.
 *
 * ### The control matters more than usual here
 *
 * The finite path is not under suspicion and shares none of the infinite path's
 * layout code — no `requiredHeight`, no manual `IntOffset`, a `LazyColumn` with
 * content padding and a parent that centres. Running the identical assertion
 * against both is what separates "the infinite wheel is off by one" from "this
 * test's idea of the centre is wrong", and only one of those is worth a fix.
 */
@OptIn(ExperimentalTestApi::class)
class WheelPlacementTest {

    private val ItemHeight: Dp = 40.dp
    private val VisibleItems = 5

    /** Two digits, so no label is a substring of another and every row is unique. */
    private val hours = (0..23).map { it.toString().padStart(2, '0') }

    /**
     * How far the selected row's centre sits below the wheel's centre, in dp.
     *
     * Positive is too low, negative is too high. One whole row is [ItemHeight],
     * so the reported defect would read as about `-40`.
     */
    private fun offsetOfSelectedRow(infinite: Boolean, selected: Int): Float {
        var drift = Float.NaN
        runComposeUiTest {
            setContent {
                KontourTheme {
                    Box(Modifier.width(120.dp).testTag("wheel")) {
                        WheelPicker(
                            items = hours,
                            selected = selected,
                            onSelectedChange = {},
                            label = { it },
                            visibleItems = VisibleItems,
                            itemHeight = ItemHeight,
                            infinite = infinite,
                        )
                    }
                }
            }
            waitForIdle()

            val wheel = onNodeWithTag("wheel").getBoundsInRoot()
            val row = onNodeWithText(hours[selected]).getBoundsInRoot()
            val wheelCentre = (wheel.top + wheel.bottom).value / 2f
            val rowCentre = (row.top + row.bottom).value / 2f
            drift = rowCentre - wheelCentre
        }
        return drift
    }

    @Test
    fun theFiniteWheelPutsTheSelectedRowInTheMiddle() {
        // The control. If this fails, the test is wrong about where the middle
        // is and the infinite result below means nothing.
        val drift = offsetOfSelectedRow(infinite = false, selected = 5)
        assertTrue(
            abs(drift) < 2f,
            "the finite wheel put its selected row ${drift}dp from the centre of " +
                "the picker. This is the path nobody has complained about, so a " +
                "failure here means the measurement is wrong rather than the wheel.",
        )
    }

    @Test
    fun theInfiniteWheelPutsTheSelectedRowInTheMiddle() {
        val drift = offsetOfSelectedRow(infinite = true, selected = 5)
        assertTrue(
            abs(drift) < 2f,
            "the infinite wheel put its selected row ${drift}dp from the centre " +
                "of the picker, and a row is ${ItemHeight.value}dp tall — so " +
                "${drift / ItemHeight.value} rows out. Measured through the " +
                "semantics tree rather than the pixels, so the 0.2 alpha floor " +
                "that blocked the last attempt cannot account for it.",
        )
    }

    @Test
    fun theTwoPathsAgreeWithEachOther() {
        // Stated separately from the two above because it is the comparison the
        // report is really about: whichever way the absolute answer goes, the
        // two wheels must not disagree with each other by a row.
        val finite = offsetOfSelectedRow(infinite = false, selected = 5)
        val wrapping = offsetOfSelectedRow(infinite = true, selected = 5)
        assertTrue(
            abs(finite - wrapping) < 2f,
            "the finite wheel puts its selected row ${finite}dp from centre and " +
                "the infinite one ${wrapping}dp — a difference of " +
                "${wrapping - finite}dp on a ${ItemHeight.value}dp row.",
        )
    }
}
