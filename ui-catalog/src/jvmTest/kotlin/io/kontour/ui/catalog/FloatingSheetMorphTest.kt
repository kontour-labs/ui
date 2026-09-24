package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import io.kontour.ui.overlay.OverlayAlignment
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.sheet.BottomSheet
import io.kontour.ui.sheet.SheetDetent
import io.kontour.ui.sheet.SheetEdgeMorph
import io.kontour.ui.sheet.SheetPresentation
import io.kontour.ui.sheet.rememberSheetState
import io.kontour.ui.theme.KontourTheme
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A floating sheet expanded to its top detent **is** an edge sheet.
 *
 * Asked for as: *"when the user expands it, it should morph into the regular edge
 * sheet, expanding to the appropriate edges of the device"*. The appropriate edges
 * are the ones the same sheet would reach as `SheetPresentation.Edge` — the bottom
 * and both sides on a phone, the bottom alone beside a sheet the width cap has
 * stopped — so every arm here is a picture of an edge sheet it can be compared
 * against, read off the window's own edge pixels.
 *
 * The morph runs across the last step of the sheet's travel, from the resting
 * detent below the top to the top, and follows the sheet's position rather than a
 * clock. Below it the sheet is the floating panel it always was.
 */
class FloatingSheetMorphTest {

    @Test
    fun anExpandedFloatingSheetMeetsTheWindowsEdges() {
        val image = settled(SheetDetent.Expanded)

        assertEquals(0, image.gapUnderTheSheet(), "an expanded floating sheet still floats above the window's bottom")
        assertTrue(
            image.isSheet(1, image.height - 2) && image.isSheet(image.width - 2, image.height - 2),
            "the bottom corners of an expanded floating sheet are not on the window's corners — " +
                "it is still inset from the sides, or still rounded where an edge sheet is square",
        )
    }

    @Test
    fun belowTheTopDetentItIsStillFloating() {
        val image = settled(SheetDetent.Half)

        assertEquals(FloatingMargin, image.gapUnderTheSheet(), "a floating sheet at Half lost its bottom margin")
        assertEquals(FloatingMargin, image.gapBesideTheSheet(), "a floating sheet at Half lost its side margin")
    }

    /**
     * Halfway up the last step, halfway there.
     *
     * The first two arms would pass for a sheet that switched presentation at a
     * threshold, which reads as a jump under the finger. Held by hand partway
     * between `Half` and `Expanded`, the margins are both somewhere strictly
     * between the floating one and none.
     */
    @Test
    fun theMorphFollowsTheSheet() {
        var under = -1
        var beside = -1
        Scene(width = 600, height = 900) {
            Harness(initial = SheetDetent.Half)
        }.use { scene ->
            val settled = scene.frames(40)
            val top = settled.topOfTheSheet()
            val handle = Offset(300f, top + 16f)
            // Half the distance from Half's top edge to Expanded's, plus the slop
            // a touch drag spends before it moves anything.
            scene.drag(from = handle, to = handle - Offset(0f, (top - 24f) / 2f + 36f), steps = 24, release = false)
            val held = scene.frames(10)
            under = held.gapUnderTheSheet()
            beside = held.gapBesideTheSheet()
            scene.release(handle - Offset(0f, (top - 24f) / 2f + 36f))
        }

        assertTrue(
            under in 1 until FloatingMargin && beside in 1 until FloatingMargin,
            "held partway between Half and Expanded, the sheet was ${under}px off the bottom and " +
                "${beside}px off the side, where the floating margin is ${FloatingMargin}px and an " +
                "edge sheet's is 0. The morph is meant to follow the sheet, not switch at a point",
        )
    }

    /**
     * On a window wider than the sheet's cap, the edge sheet it becomes meets the
     * bottom and nothing else: the cap still binds, and the sheet stays where its
     * alignment puts it.
     */
    @Test
    fun onAWideWindowItMeetsOnlyTheBottom() {
        val centred = settled(SheetDetent.Expanded, width = 1400)
        // 640dp at a density of 2, centred in 1400px: 60 to 1340.
        assertEquals(0, centred.gapUnderTheSheet(), "an expanded floating sheet on a wide window is still off the bottom")
        assertTrue(centred.isSheet(62, centred.height - 2), "the capped sheet did not reach its own full width")
        assertTrue(!centred.isSheet(5, centred.height - 2), "the capped sheet spread to the window's side")
        assertTrue(!centred.isSheet(1395, centred.height - 2), "the capped sheet spread to the window's side")

        val start = settled(SheetDetent.Expanded, width = 1400, alignment = OverlayAlignment.Start)
        assertTrue(start.isSheet(1, start.height - 2), "a start-aligned expanded sheet did not meet the window's start edge")
        assertTrue(!start.isSheet(1300, start.height - 2), "a start-aligned expanded sheet is wider than its cap")
    }

    @Test
    fun aNullMorphStaysFloating() {
        val image = settled(SheetDetent.Expanded, morph = null)

        assertEquals(FloatingMargin, image.gapUnderTheSheet(), "`edgeMorph = null` still morphed the sheet")
        assertEquals(FloatingMargin, image.gapBesideTheSheet(), "`edgeMorph = null` still morphed the sheet")
    }

    @Test
    fun aCustomMorphCompletesWhereItIsTold() {
        val bar = SheetDetent.height("bar", 100.dp)
        val image = settled(
            SheetDetent.Half,
            detents = listOf(SheetDetent.Hidden, bar, SheetDetent.Half, SheetDetent.Expanded),
            morph = SheetEdgeMorph(until = SheetDetent.Half),
        )

        assertEquals(0, image.gapUnderTheSheet(), "a morph told to finish at Half had not finished there")
        assertEquals(0, image.gapBesideTheSheet(), "a morph told to finish at Half had not finished there")
    }

    /**
     * What the margin stops clearing, the content is told about.
     *
     * A floating sheet hands its content no bottom padding and no side padding,
     * because its margin already clears the window's insets. Once it has become an
     * edge sheet the surface is on the window's edges, and the content is handed
     * the insets exactly as an edge sheet's is — so it never moves under a gesture
     * bar or a cutout at any point of the morph.
     */
    @Test
    fun theContentIsHandedTheInsetsTheSheetNoLongerClears() {
        val floating = insetReadings(SheetDetent.Half)
        val expanded = insetReadings(SheetDetent.Expanded)

        assertEquals(0, floating.bottom, "a floating sheet handed its content bottom padding it has already cleared")
        assertEquals(80f, floating.contentLeft, "a floating sheet's content is not clear of a 40dp side inset")
        assertEquals(48, expanded.bottom, "an expanded sheet did not hand its content the 24dp bottom inset")
        assertEquals(80f, expanded.contentLeft, "an expanded sheet's content is not clear of a 40dp side inset")
        assertTrue(expanded.image.isSheet(1, 450), "an expanded sheet's surface did not reach the window's side")
    }

    private class InsetReadings(val bottom: Int, val contentLeft: Float, val image: BufferedImage)

    private fun insetReadings(at: SheetDetent): InsetReadings {
        var bottom = -1
        var left = -1f
        val image = Scene(width = 600, height = 900) {
            Harness(
                initial = at,
                windowInsets = WindowInsets(left = 40.dp, bottom = 24.dp),
            ) { padding ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1200.dp)
                        .onGloballyPositioned { left = it.positionInRoot().x }
                        .layout { measurable, constraints ->
                            bottom = padding.calculateBottomPadding().roundToPx()
                            val placeable = measurable.measure(constraints)
                            layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                        }
                )
            }
        }.use { scene -> scene.frames(40) }
        return InsetReadings(bottom, left, image)
    }

    private fun settled(
        at: SheetDetent,
        width: Int = 600,
        detents: List<SheetDetent> = listOf(SheetDetent.Hidden, SheetDetent.Half, SheetDetent.Expanded),
        morph: SheetEdgeMorph? = SheetEdgeMorph(),
        alignment: OverlayAlignment = OverlayAlignment.Center,
    ): BufferedImage = Scene(width = width, height = 900) {
        Harness(initial = at, detents = detents, morph = morph, alignment = alignment)
    }.use { scene -> scene.frames(40) }

    @androidx.compose.runtime.Composable
    private fun Harness(
        initial: SheetDetent,
        detents: List<SheetDetent> = listOf(SheetDetent.Hidden, SheetDetent.Half, SheetDetent.Expanded),
        morph: SheetEdgeMorph? = SheetEdgeMorph(),
        alignment: OverlayAlignment = OverlayAlignment.Center,
        windowInsets: WindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        content: @androidx.compose.runtime.Composable (PaddingValues) -> Unit = {
            // Taller than the window, so `Expanded` is the top of it.
            Box(Modifier.fillMaxWidth().height(1200.dp))
        },
    ) {
        KontourTheme(reduceMotion = true) {
            val state = rememberSheetState(detents = detents, initialDetent = initial)
            OverlayHost(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().background(Ground))
                BottomSheet(
                    state = state,
                    presentation = SheetPresentation.Floating,
                    edgeMorph = morph,
                    alignment = alignment,
                    containerColour = SheetColour,
                    windowInsets = windowInsets,
                    dragHandle = null,
                ) { padding -> content(padding) }
            }
        }
    }

    private fun BufferedImage.isSheet(x: Int, y: Int): Boolean = (getRGB(x, y) and 0xFFFFFF) == SheetRgb

    /** Rows between the sheet's lowest ink and the bottom of the window, down the middle. */
    private fun BufferedImage.gapUnderTheSheet(): Int {
        for (y in height - 1 downTo 0) {
            if (isSheet(width / 2, y)) return height - 1 - y
        }
        return height
    }

    /** Columns between the window's left edge and the sheet, a hundred rows up from the bottom. */
    private fun BufferedImage.gapBesideTheSheet(): Int {
        val y = height - 100
        for (x in 0 until width) {
            if (isSheet(x, y)) return x
        }
        return width
    }

    /** The first row of the sheet's ink, down the middle. */
    private fun BufferedImage.topOfTheSheet(): Float {
        for (y in 0 until height) {
            if (isSheet(width / 2, y)) return y.toFloat()
        }
        return height.toFloat()
    }

    private companion object {
        val Ground = Color(0xFF3355AA)
        val SheetColour = Color(0xFF11CC55)
        const val SheetRgb = 0x11CC55

        /** `componentDefaults.sheetFloatingInset`, 12dp, at the scene's density of 2. */
        const val FloatingMargin = 24
    }
}
