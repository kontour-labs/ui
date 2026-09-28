package io.kontour.ui.catalog

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
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
     * A last step of a few dp still morphs over a distance, rather than in a frame.
     *
     * "You seem to have broken the expand to edge animation. It just snaps." The
     * catalog's frame grew from 420dp to 520dp, and its sheet's content — about
     * 262dp — went from well above `Half` to a few dp above it. The morph runs
     * across the step from the resting detent below the top to the top, so it ran
     * across those few dp: a frame or two of any travel.
     *
     * Here `Half` is 225dp and the content 230dp. Dragged by hand from a peek to
     * the top, at 15px a frame, the sheet is caught partway morphed on a handful of
     * frames — not the one or none a 5dp morph leaves room for.
     */
    @Test
    fun aShortLastStepStillMorphsOverADistance() {
        val between = mutableListOf<Int>()
        Scene(width = 600, height = 900) {
            Harness(
                initial = SheetDetent.peek(120.dp),
                detents = listOf(SheetDetent.Hidden, SheetDetent.peek(120.dp), SheetDetent.Half, SheetDetent.Expanded),
                content = { Box(Modifier.fillMaxWidth().height(230.dp)) },
            )
        }.use { scene ->
            val settled = scene.frames(40)
            val top = settled.topOfTheSheet()
            val handle = Offset(300f, top + 16f)
            // Up past where the top detent's edge rests, 440px down, and a little
            // beyond: the sheet stops at the top and the finger can carry on.
            val travel = top - 400f
            scene.press(handle)
            scene.frame()
            repeat(40) { step ->
                scene.move(handle - Offset(0f, travel * (step + 1) / 40f))
                val beside = scene.frame().gapBesideTheSheet()
                if (beside in 1 until FloatingMargin) between += beside
            }
            scene.release(handle - Offset(0f, travel))
        }
        assertTrue(
            between.size >= 4,
            "dragged from a peek to the top of a sheet whose last step is 5dp, it was partway " +
                "morphed on ${between.size} frames ($between) — the morph happened in the last step's " +
                "few dp, which is a snap",
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
     * A sheet with one size to be — a `ModalBottomSheet` on its defaults — has no
     * step to morph across, so the morph runs over the last stretch before the top
     * of the window instead. Content tall enough to reach the top arrives as an
     * edge sheet; the arm after this one is the same sheet with a line of text in
     * it, which still floats.
     */
    @Test
    fun aTallSingleSizeSheetIsAnEdgeSheet() {
        val image = modal { Box(Modifier.fillMaxWidth().height(1200.dp)) }

        assertEquals(0, image.gapUnderTheSheet(), "a tall single-size floating sheet still floats above the bottom")
        assertTrue(
            image.isSheet(1, image.height - 2) && image.isSheet(image.width - 2, image.height - 2),
            "a tall single-size floating sheet does not reach the window's bottom corners",
        )
    }

    @Test
    fun aShortSingleSizeSheetStillFloats() {
        val image = modal { Box(Modifier.fillMaxWidth().height(120.dp)) }

        assertEquals(FloatingMargin, image.gapUnderTheSheet(), "a short single-size floating sheet lost its margin")
    }

    private fun modal(content: @androidx.compose.runtime.Composable () -> Unit): BufferedImage =
        Scene(width = 600, height = 900) {
            KontourTheme(reduceMotion = true) {
                OverlayHost(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().background(Ground))
                    io.kontour.ui.sheet.ModalBottomSheet(
                        visible = true,
                        onDismissRequest = {},
                        presentation = SheetPresentation.Floating,
                        containerColour = SheetColour,
                        windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
                        dragHandle = null,
                    ) { content() }
                }
            }
        }.use { scene -> scene.frames(60) }

    /**
     * A tall single-size sheet is an edge sheet from the moment it starts opening.
     *
     * Reported as the floating sheet "snapping" fully open near the top. The rule
     * used to follow the live offset over the last stretch before the top, so a
     * tall sheet opened floating and changed shape in its last few frames — and on
     * a phone with a status bar it measured from the wrong top, and rested half
     * changed. It decides by where the sheet *rests* now, so nothing changes while
     * it moves: every frame of the open that shows the sheet shows it flush.
     */
    @Test
    fun aTallSingleSizeSheetOpensAsAnEdgeSheet() {
        var open by mutableStateOf(false)
        val gaps = mutableListOf<Int>()
        Scene(width = 600, height = 900) {
            TallModal(open = open, rows = 80)
        }.use { scene ->
            scene.frames(4)
            open = true
            repeat(40) {
                val frame = scene.frame()
                // Only once the sheet's top is well clear of the row being read: the
                // first frame to reach it crosses it at the top corner, whose curve
                // is a gap beside an edge sheet too.
                if (frame.topOfTheSheet() < frame.height - 100 - CornerClearance) {
                    gaps += frame.gapBesideTheSheet()
                }
            }
        }
        assertTrue(gaps.isNotEmpty(), "the sheet never reached the row being read")
        assertTrue(
            gaps.all { it == 0 },
            "a tall single-size floating sheet was off the side by $gaps across its opening — " +
                "it floated and then changed shape, where it should arrive as the edge sheet it rests as",
        )
    }

    /**
     * The same sheet, scrolled to the end: the last row comes to rest on the sheet.
     *
     * Reported as not being able to scroll to the bottom of a floating sheet. Resting
     * half changed, its surface stopped a part-margin short of the window while its
     * content was measured to the window's bottom, so the end of the scroller was
     * under the sheet's own edge.
     */
    @Test
    fun aTallFloatingSheetScrollsToItsLastRow() {
        var last = Rect.Zero
        var scroll: ScrollState? = null
        val image = Scene(width = 600, height = 900) {
            TallModal(open = true, rows = 80, onScroll = { scroll = it }, onLastRow = { last = it })
        }.use { scene ->
            scene.frames(60)
            checkNotNull(scroll).dispatchRawDelta(1_000_000f)
            scene.frames(10)
        }
        val surfaceBottom = image.height - 1 - image.gapUnderTheSheet()
        assertTrue(last.height > 0f, "the last row was never positioned")
        assertTrue(
            last.bottom <= surfaceBottom + 1,
            "scrolled to the end, the last row's bottom was at ${last.bottom} and the sheet's own " +
                "bottom edge at $surfaceBottom — the end of the content is under the sheet",
        )
    }

    /**
     * The content stays still while the surface grows around it.
     *
     * It used to reflow at every width the morph passed through, and content whose
     * height depends on its width — a line of text that wraps — changed the sheet's
     * height, and with it the anchors, in the middle of the drag: the other half of
     * the reported snap.
     */
    @Test
    fun theContentKeepsItsWidthThroughTheMorph() {
        fun widthAt(at: SheetDetent, held: Boolean): Float {
            var width = -1f
            Scene(width = 600, height = 900) {
                Harness(initial = at) {
                    Box(Modifier.fillMaxWidth().height(1200.dp).onGloballyPositioned { width = it.size.width.toFloat() })
                }
            }.use { scene ->
                val settled = scene.frames(40)
                if (held) {
                    val top = settled.topOfTheSheet()
                    val handle = Offset(300f, top + 16f)
                    scene.drag(from = handle, to = handle - Offset(0f, (top - 24f) / 2f + 36f), steps = 24, release = false)
                    scene.frames(10)
                }
            }
            return width
        }
        val floating = widthAt(SheetDetent.Half, held = false)
        val halfway = widthAt(SheetDetent.Half, held = true)
        val expanded = widthAt(SheetDetent.Expanded, held = false)
        assertTrue(
            floating == halfway && halfway == expanded,
            "the content was $floating wide floating, $halfway halfway through the morph and $expanded " +
                "expanded — it reflowed as the sheet widened",
        )
    }

    /**
     * A list in a sheet whose tallest detent is short reaches its last row there.
     *
     * The content was measured at nearly the window's height whatever the sheet's
     * tallest detent, so a list in a sheet that stops at `Half` had a viewport
     * running far below the window, and its last rows could never be scrolled into
     * view. Floating sheets are the ones that stop short — a bar and a half — so this
     * is where it was found.
     */
    @Test
    fun aListInAShortFloatingSheetReachesItsEnd() {
        var last = Rect.Zero
        var list: LazyListState? = null
        Scene(width = 600, height = 900) {
            Harness(
                initial = SheetDetent.Half,
                detents = listOf(SheetDetent.Hidden, SheetDetent.height("bar", 64.dp), SheetDetent.Half),
            ) { padding ->
                val state = rememberLazyListState()
                list = state
                LazyColumn(Modifier.fillMaxWidth(), state = state, contentPadding = padding) {
                    items(100) { index ->
                        Box(
                            Modifier.fillMaxWidth().height(48.dp).then(
                                if (index == 99) Modifier.onGloballyPositioned { last = it.unclipped() } else Modifier
                            )
                        )
                    }
                }
            }
        }.use { scene ->
            scene.frames(40)
            checkNotNull(list).dispatchRawDelta(1_000_000f)
            scene.frames(10)
        }
        assertTrue(last.height > 0f, "the last row was never positioned")
        assertTrue(
            last.bottom <= 900f,
            "scrolled to the end at Half, the last row's bottom was at ${last.bottom}, where the " +
                "window ends at 900 — the list's viewport runs off the bottom of the window",
        )
    }

    /** A modal floating sheet with a scroller taller than the window, and a status bar. */
    @androidx.compose.runtime.Composable
    private fun TallModal(
        open: Boolean,
        rows: Int,
        onScroll: (ScrollState) -> Unit = {},
        onLastRow: (Rect) -> Unit = {},
    ) {
        KontourTheme(reduceMotion = true) {
            OverlayHost(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().background(Ground))
                io.kontour.ui.sheet.ModalBottomSheet(
                    visible = open,
                    onDismissRequest = {},
                    presentation = SheetPresentation.Floating,
                    containerColour = SheetColour,
                    windowInsets = WindowInsets(top = 40.dp),
                    dragHandle = null,
                ) { padding ->
                    val scroll = rememberScrollState()
                    onScroll(scroll)
                    androidx.compose.foundation.layout.Column(
                        Modifier.verticalScroll(scroll).padding(bottom = padding.calculateBottomPadding())
                    ) {
                        repeat(rows) { index ->
                            Box(
                                Modifier.fillMaxWidth().height(48.dp).then(
                                    if (index == rows - 1) Modifier.onGloballyPositioned { onLastRow(it.unclipped()) } else Modifier
                                )
                            )
                        }
                    }
                }
            }
        }
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
        alignment: OverlayAlignment = OverlayAlignment.Centre,
    ): BufferedImage = Scene(width = width, height = 900) {
        Harness(initial = at, detents = detents, morph = morph, alignment = alignment)
    }.use { scene -> scene.frames(40) }

    @androidx.compose.runtime.Composable
    private fun Harness(
        initial: SheetDetent,
        detents: List<SheetDetent> = listOf(SheetDetent.Hidden, SheetDetent.Half, SheetDetent.Expanded),
        morph: SheetEdgeMorph? = SheetEdgeMorph(),
        alignment: OverlayAlignment = OverlayAlignment.Centre,
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

    /**
     * Where a node really is, uncut. `boundsInRoot` clips to every parent, so a row
     * under the sheet's edge reads as shorter than it is and one off the window as
     * empty — which is exactly the case these measure.
     */
    private fun androidx.compose.ui.layout.LayoutCoordinates.unclipped(): Rect {
        val at = positionInRoot()
        return Rect(at.x, at.y, at.x + size.width, at.y + size.height)
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

        /** Further than any top corner in the library reaches, at the scene's density. */
        const val CornerClearance = 120
    }
}
