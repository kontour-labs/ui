package io.kontour.ui.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.platform.DeviceCorners
import io.kontour.ui.platform.deviceCornersOverride
import io.kontour.ui.theme.KontourTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A sheet's header keeps clear of a display's corners when they are rounder than
 * the sheet's own.
 *
 * Asked for as *"the sheet header still feels like it's a little bit close to the
 * rounded corners of the sheet, especially on devices with extra-rounded corners
 * like iphones and newer pixels."* An edge sheet takes the display's corners, up
 * to [EdgeSheetCornerCap] — 55pt on an iPhone is 40dp on its sheet — against the
 * theme's 34dp. The header moves in by what the rounder corner cuts into the
 * diagonal, 1 − 1/√2 of the difference: 1.76dp at the cap, where it was 6.15dp
 * for the whole of an iPhone's corner.
 *
 * In `jvmTest`, because the display's corners come through the JVM's override.
 * The harness is [SheetHeaderInsetTest]'s, with the corners set.
 */
@OptIn(ExperimentalTestApi::class)
class SheetCornerInsetTest {

    private class Measured(val title: Rect, val body: Rect, val sheetTop: Dp, val window: Rect)

    private fun measure(
        corners: DeviceCorners?,
        rtl: Boolean = false,
        handle: Boolean = true,
    ): Measured {
        var result: Measured? = null
        deviceCornersOverride = corners
        try {
            runComposeUiTest {
                lateinit var sheet: SheetState
                mainClock.autoAdvance = false
                setContent { Harness(rtl, handle) { sheet = it } }
                settle()
                val window = onRoot().getBoundsInRoot()
                val title = onNodeWithText(Title, useUnmergedTree = true).getBoundsInRoot()
                val body = onNodeWithTag(Body).getBoundsInRoot()
                val sheetTop = window.bottom - with(density) { sheet.visibleHeight.toDp() }
                result = Measured(
                    title = Rect(title.left.value, title.top.value, title.right.value, title.bottom.value),
                    body = Rect(body.left.value, body.top.value, body.right.value, body.bottom.value),
                    sheetTop = sheetTop,
                    window = Rect(window.left.value, window.top.value, window.right.value, window.bottom.value),
                )
            }
        } finally {
            deviceCornersOverride = null
        }
        return result!!
    }

    private fun ComposeUiTest.settle() {
        repeat(90) { mainClock.advanceTimeByFrame() }
    }

    private fun near(expected: Float, actual: Float, what: String) {
        assertTrue(abs(expected - actual) < 1f, "$what: expected $expected, was $actual")
    }

    /** The title's line, centred in the 24dp row the close button's icon makes. */
    private fun centring(m: Measured) = (Icon - (m.title.bottom - m.title.top)) / 2

    @Test
    fun onAnIphonesCornersTheTitleMovesInAlongTheCorner() {
        val m = measure(DeviceCorners.uniform(55.dp))
        near(Inset + RoomIphone, m.title.left - m.body.left, "beside the title, from the sheet's side")
        near(Band + RoomIphone + centring(m), m.title.top - m.sheetTop.value, "above the title, from the sheet's top edge")
    }

    @Test
    fun theThemesOwnCornerLeavesTheHeaderWhereItWas() {
        val m = measure(DeviceCorners.uniform(34.dp))
        near(Inset, m.title.left - m.body.left, "beside the title")
        near(Band + centring(m), m.title.top - m.sheetTop.value, "above the title")
    }

    @Test
    fun eachSideFromItsOwnCornerAndMirroredRightToLeft() {
        // Rounder at the top left than at the top right.
        val corners = DeviceCorners(topLeft = 60.dp, topRight = 20.dp, bottomRight = 20.dp, bottomLeft = 60.dp)
        // 60dp at the left, capped like any corner rounder than the cap.
        val left = cornerRoom(EdgeSheetCornerCap, 34.dp).value
        val ltr = measure(corners)
        near(Inset + left, ltr.title.left - ltr.body.left, "left to right, the title starts beside the rounder corner")
        val rtl = measure(corners, rtl = true)
        // Right to left the title starts at the right, under the squarer corner.
        near(Inset, rtl.body.right - rtl.title.right, "right to left, the title starts beside the squarer corner")
    }

    @Test
    fun withoutAHandleTheHeaderClearsTheCornerAbove() {
        val m = measure(DeviceCorners.uniform(55.dp), handle = false)
        near(Inset + RoomIphone + centring(m), m.title.top - m.sheetTop.value, "above the title, with no band over it")
    }

    /**
     * In a part pinned to the sheet's top, a [SheetHeader] moves in from the corners as it
     * does in the content: the slot hands it the corners' room and the band, as
     * the content's column does.
     */
    @Test
    fun aHeaderInTheHeaderSlotMovesInToo() {
        deviceCornersOverride = DeviceCorners.uniform(55.dp)
        try {
            runComposeUiTest {
                mainClock.autoAdvance = false
                setContent {
                    KontourTheme(reduceMotion = true) {
                        Box(Modifier.fillMaxSize().background(Color.White)) {
                            OverlayHost(Modifier.fillMaxSize()) {
                                val state = rememberSheetState(
                                    detents = listOf(SheetDetent.parts(), SheetDetent.Half, SheetDetent.Expanded),
                                    initialDetent = SheetDetent.Half,
                                )
                                BottomSheet(
                                    state = state,
                                    presentation = SheetPresentation.Edge,
                                ) {
                                    part(pin = SheetPartPin.Top) { SheetHeader { +Title } }
                                    Box(Modifier.fillMaxWidth().height(400.dp).testTag(Body))
                                }
                            }
                        }
                    }
                }
                settle()
                // The body fills the sheet's width: its sides are the sheet's.
                val body = onNodeWithTag(Body).getBoundsInRoot()
                val title = onNodeWithText(Title, useUnmergedTree = true).getBoundsInRoot()
                near(Inset + RoomIphone, (title.left - body.left).value, "beside a header in the slot, from the sheet's side")
            }
        } finally {
            deviceCornersOverride = null
        }
    }

    @Composable
    private fun Harness(rtl: Boolean, handle: Boolean, onState: (SheetState) -> Unit) {
        KontourTheme(reduceMotion = true) {
            CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    OverlayHost(Modifier.fillMaxSize()) {
                        val peek = SheetDetent.peek(160.dp)
                        val state = rememberSheetState(
                            detents = listOf(SheetDetent.Hidden, peek, SheetDetent.Expanded),
                            initialDetent = peek,
                        )
                        onState(state)
                        BottomSheet(
                            state = state,
                            presentation = SheetPresentation.Edge,
                            dragHandle = if (handle) ({ DragHandle() }) else null,
                        ) {
                            SheetHeader(modifier = Modifier.sheetPeekAnchor()) { +Title }
                            Box(Modifier.fillMaxWidth().height(400.dp).testTag(Body))
                        }
                    }
                }
            }
        }
    }

    private companion object {
        const val Title = "Perth Underground"
        const val Body = "body"
        const val Inset = 16f
        const val Band = 24f
        const val Icon = 24f

        /** An iPhone's 55pt corner, capped, less the theme's 34, times 1 − 1/√2. */
        val RoomIphone = cornerRoom(EdgeSheetCornerCap, 34.dp).value
    }
}
