package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.sheet.SheetPresentation
import io.kontour.ui.sheet.SheetSide
import io.kontour.ui.sheet.SideSheet
import io.kontour.ui.sheet.SideSheetState
import io.kontour.ui.sheet.rememberSideSheetState
import io.kontour.ui.theme.KontourTheme
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A side sheet can float, can be widened to the whole window, and becomes an edge
 * sheet on the way — the bottom sheet's morph, on the other axis.
 *
 * Asked for as *"make the side sheet morph the same way"*. The side sheet had one
 * width and no drag, so the morph needed somewhere to go: an expandable sheet has
 * a grip on its inner edge that drags it out to the far side of the window, and a
 * floating one closes its margins and squares its corners as it goes, arriving as
 * a page flush to every edge.
 *
 * Read off the window's own edge pixels, in a colour only the sheet paints: the
 * scrim dims the page and the backdrop shrinks it, so "not the sheet" is the only
 * honest reading of the space around it.
 *
 * 1400 by 900 at a density of 2, with a 300dp sheet: 600px wide at rest, a 24px
 * floating margin.
 */
class SideSheetMorphTest {

    @Test
    fun aFloatingSideSheetKeepsItsDistance() {
        val image = scene(presentation = SheetPresentation.Floating)

        assertEquals(Margin, image.gapRight(), "a floating side sheet is not ${Margin}px off its side")
        assertEquals(Margin, image.gapTop(), "a floating side sheet is not ${Margin}px off the top")
        assertEquals(Margin, image.gapBottom(), "a floating side sheet is not ${Margin}px off the bottom")
        assertFalse(image.isSheet(Width - 2 - Margin, Margin + 1), "a floating side sheet's outer top corner is square")
    }

    @Test
    fun anEdgeSideSheetIsUnchanged() {
        val image = scene(presentation = SheetPresentation.Edge)

        assertEquals(0, image.gapRight(), "an edge side sheet is off its side")
        assertTrue(image.isSheet(Width - 2, 1) && image.isSheet(Width - 2, Height - 2), "an edge side sheet does not reach its corners")
        assertEquals(Width - Resting, image.innerEdge(), "an edge side sheet is not its own width")
    }

    @Test
    fun anExpandedFloatingSideSheetIsTheWholeWindow() {
        val image = scene(presentation = SheetPresentation.Floating, expanded = true)

        assertTrue(
            image.isSheet(1, 1) && image.isSheet(Width - 2, 1) &&
                image.isSheet(1, Height - 2) && image.isSheet(Width - 2, Height - 2),
            "an expanded floating side sheet does not reach all four corners of the window",
        )
        assertEquals(0, image.innerEdge(), "an expanded side sheet does not reach the far side")
    }

    /**
     * Dragged by its grip halfway to the far side and held: the sheet is halfway
     * wider, and its margins are halfway gone — a switch at some width would pass
     * the two arms either side of this and fail here.
     */
    @Test
    fun theMorphFollowsTheGrip() {
        var inner = -1
        var right = -1
        var top = -1
        Scene(width = Width, height = Height) {
            Harness(presentation = SheetPresentation.Floating)
        }.use { scene ->
            val resting = scene.frames(60)
            val grip = Offset(resting.innerEdge().toFloat(), Height / 2f)
            val to = grip - Offset(grip.x / 2f + Slop, 0f)
            scene.drag(from = grip, to = to, steps = 24, release = false)
            val held = scene.frames(10)
            inner = held.innerEdge()
            right = held.gapRight()
            top = held.gapTop()
            scene.release(to)
        }

        val restingInner = Width - Margin - Resting
        assertTrue(inner in 1 until restingInner, "held halfway, the sheet's inner edge was at $inner, where it rests at $restingInner")
        assertTrue(
            right in 1 until Margin && top in 1 until Margin,
            "held halfway, the sheet was ${right}px off its side and ${top}px off the top, where it " +
                "floats at $Margin and an edge sheet is at 0. The morph follows the grip, not a switch",
        )
    }

    @Test
    fun withoutTheMorphItExpandsFloating() {
        val image = scene(presentation = SheetPresentation.Floating, expanded = true, edgeMorph = false)

        assertEquals(Margin, image.gapRight(), "an expanded sheet with `edgeMorph = false` lost its side margin")
        assertEquals(Margin, image.gapTop(), "an expanded sheet with `edgeMorph = false` lost its top margin")
        assertEquals(Margin, image.innerEdge(), "an expanded sheet with `edgeMorph = false` lost its far margin")
    }

    @Test
    fun aStartSideSheetExpandsTheOtherWay() {
        val resting = scene(presentation = SheetPresentation.Floating, side = SheetSide.Start)
        assertTrue(!resting.isSheet(Margin - 2, Height / 2) && resting.isSheet(Margin + 2, Height / 2), "a floating start sheet is not ${Margin}px off the left")

        val expanded = scene(presentation = SheetPresentation.Floating, side = SheetSide.Start, expanded = true)
        assertTrue(
            expanded.isSheet(1, 1) && expanded.isSheet(Width - 2, Height - 2),
            "an expanded start sheet does not cover the window",
        )
    }

    /**
     * What the margin stops clearing, the content is padded clear of — and the far
     * side's inset, which a resting sheet is nowhere near, once the sheet reaches it.
     */
    @Test
    fun theContentIsHandedTheInsetsTheSheetNowCovers() {
        val insets = WindowInsets(top = 24.dp, left = 30.dp, right = 40.dp)
        val resting = contentBounds(insets, expanded = false)
        val expanded = contentBounds(insets, expanded = true)

        // At rest the margin is the inset: 80px on the right, 48 at the top, so the
        // surface starts there and the content fills it unpadded.
        assertEquals(Width - 80f, resting.right, "a resting floating sheet padded its content on the outer side")
        assertEquals(48f, resting.top, "a resting floating sheet padded its content at the top")
        assertEquals(Width - 80f - Resting, resting.left, "a resting floating sheet padded its content on the far side")

        // Expanded, the surface is the window and the content keeps clear of all of it.
        assertEquals(Width - 80f, expanded.right, "an expanded sheet's content is not clear of the 40dp right inset")
        assertEquals(60f, expanded.left, "an expanded sheet's content is not clear of the 30dp left inset")
        assertEquals(48f, expanded.top, "an expanded sheet's content is not clear of the 24dp top inset")
    }

    @Test
    fun aSheetThatIsNotExpandableHasNoGrip() {
        var inner = -1
        Scene(width = Width, height = Height) {
            Harness(presentation = SheetPresentation.Edge, expandable = false)
        }.use { scene ->
            scene.frames(60)
            val edge = Offset((Width - Resting + 10).toFloat(), Height / 2f)
            // Read while still held: letting go over the page is a tap outside,
            // which is a dismissal and a different question.
            scene.drag(from = edge, to = edge - Offset(300f, 0f), steps = 20, release = false)
            inner = scene.frames(20).innerEdge()
            scene.release(edge - Offset(300f, 0f))
        }
        assertEquals(Width - Resting, inner, "a side sheet that is not expandable changed width under a drag")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun theGripExpandsAndCollapsesOnATap() = runDesktopComposeUiTest(width = 1400, height = 900) {
        lateinit var state: SideSheetState
        setContent {
            KontourTheme(reduceMotion = true) {
                state = rememberSideSheetState()
                OverlayHost(Modifier.fillMaxSize()) {
                    SideSheet(
                        visible = true,
                        onDismissRequest = {},
                        width = 300.dp,
                        presentation = SheetPresentation.Floating,
                        expandable = true,
                        state = state,
                    ) {}
                }
            }
        }
        waitForIdle()
        onNodeWithContentDescription("Expand sheet").performClick()
        waitForIdle()
        assertTrue(state.isExpanded, "tapping the grip did not expand the sheet")
        onNodeWithContentDescription("Collapse sheet").performClick()
        waitForIdle()
        assertFalse(state.isExpanded, "tapping the grip again did not collapse the sheet")
    }

    private fun contentBounds(insets: WindowInsets, expanded: Boolean): Rect {
        var bounds = Rect.Zero
        Scene(width = Width, height = Height) {
            Harness(
                presentation = SheetPresentation.Floating,
                expanded = expanded,
                windowInsets = insets,
            ) {
                Box(Modifier.fillMaxSize().onGloballyPositioned { bounds = it.boundsInRoot() })
            }
        }.use { scene -> scene.frames(60) }
        return bounds
    }

    private fun scene(
        presentation: SheetPresentation,
        side: SheetSide = SheetSide.End,
        expanded: Boolean = false,
        edgeMorph: Boolean = true,
    ): BufferedImage = Scene(width = Width, height = Height) {
        Harness(presentation = presentation, side = side, expanded = expanded, edgeMorph = edgeMorph)
    }.use { scene -> scene.frames(60) }

    @Composable
    private fun Harness(
        presentation: SheetPresentation,
        side: SheetSide = SheetSide.End,
        expanded: Boolean = false,
        expandable: Boolean = true,
        edgeMorph: Boolean = true,
        width: Dp = 300.dp,
        windowInsets: WindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        content: @Composable () -> Unit = {},
    ) {
        KontourTheme(reduceMotion = true) {
            val state = rememberSideSheetState(initiallyExpanded = expanded)
            OverlayHost(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().background(Ground))
                SideSheet(
                    visible = true,
                    onDismissRequest = {},
                    side = side,
                    width = width,
                    presentation = presentation,
                    expandable = expandable,
                    state = state,
                    edgeMorph = edgeMorph,
                    containerColour = SheetColour,
                    windowInsets = windowInsets,
                ) { content() }
            }
        }
    }

    private fun BufferedImage.isSheet(x: Int, y: Int): Boolean = (getRGB(x, y) and 0xFFFFFF) == SheetRgb

    /** Columns between the window's right edge and the sheet, across the middle. */
    private fun BufferedImage.gapRight(): Int {
        for (x in width - 1 downTo 0) if (isSheet(x, height / 2)) return width - 1 - x
        return width
    }

    /**
     * The sheet's leftmost column a quarter of the way down: where its inner edge
     * is. Not across the middle, where an expandable sheet's grip is drawn over it.
     */
    private fun BufferedImage.innerEdge(): Int {
        for (x in 0 until width) if (isSheet(x, height / 4)) return x
        return width
    }

    /** Rows between the window's top and the sheet, a little in from its outer edge. */
    private fun BufferedImage.gapTop(): Int {
        val x = width - 100
        for (y in 0 until height) if (isSheet(x, y)) return y
        return height
    }

    private fun BufferedImage.gapBottom(): Int {
        val x = width - 100
        for (y in height - 1 downTo 0) if (isSheet(x, y)) return height - 1 - y
        return height
    }

    private companion object {
        const val Width = 1400
        const val Height = 900

        /** 300dp at a density of 2. */
        const val Resting = 600

        /** `componentDefaults.sheetFloatingInset`, 12dp, at a density of 2. */
        const val Margin = 24

        /** The touch slop a drag spends before anything moves, 18dp at a density of 2. */
        const val Slop = 36f

        val Ground = Color(0xFF3355AA)
        val SheetColour = Color(0xFF11CC55)
        const val SheetRgb = 0x11CC55
    }
}
