package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.GripVertical
import io.kontour.ui.components.list.ListItem
import io.kontour.ui.components.list.ReorderableItem
import io.kontour.ui.components.list.rememberReorderableState
import java.awt.image.BufferedImage
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The lifted row's shadow is the row's shape, handle or no handle.
 *
 * Reported as the shadow going "a bit wonky" once drag handles are enabled, with
 * a guess attached — that something still assumes the container is full width.
 * A guess is not a diagnosis and a screenshot golden cannot tell the two
 * pictures apart at the tolerance it compares at, so this measures the drawn
 * shadow directly and reports the number either way.
 *
 * ### What it measures
 *
 * A row is lifted through `ReorderableState.start`, which is public precisely so
 * a lifted row can be photographed without a gesture. The row under it is plain
 * white, so any pixel darker than white on the row's own line is shadow. The
 * measurement is **how far the shadow reaches past the row's left and right
 * edges**, which is the quantity a wrong width would move.
 *
 * ### What it found
 *
 * Nothing, at this density. Both spans came back `26..573` — identical to the
 * pixel — so the box the shadow is cast from is the same either way and the
 * "full width" guess is not the fault. Written down because a measurement that
 * clears a suspect is worth as much as one that convicts, and because the next
 * person to read the report should start somewhere else.
 *
 * ### Where it was, and the instrument was aimed past it — twice
 *
 * The report came back with a picture: a lifted row whose handle sits on a
 * *separate* raised shape, with the card's shadow visible between them. The
 * shadow is cast by the `graphicsLayer`, which is the whole row. The row's
 * *fill* is not: it comes from the content, and with a `handleIcon` the content
 * sits in a `weight(1f)` box beside a grip that paints no background of its own.
 * So the layer cast a shadow under a region nothing filled, and what showed
 * through beside the handle was the shadow itself.
 *
 * [theShadowIsTheSameWidthWithAHandleAsWithout] cannot see that, and the reason
 * is worth keeping: it asks how far the shadow reaches **past** the row's edges,
 * and the fault is entirely **inside** them.
 *
 * [aLiftedRowIsOpaqueAllTheWayAcross] asks the inside question — and its first
 * draft **passed on the defect**, which is the more useful of the two lessons
 * here. It found the row by scanning for the first and last pixel that was not
 * the page, then asked whether any page showed between them. But
 * [io.kontour.ui.components.list.ReorderHandleSide.End] is the default, so the
 * unpainted strip is the row's *trailing* edge: the scan's own right-hand bound
 * landed on the last pixel the content painted and the hole lay beyond it. A
 * measurement that derives the boundary from the pixels cannot report a boundary
 * in the wrong place.
 *
 * So the row's extent comes from **layout** now, through [reportBounds], and the
 * pixels are only ever asked what is inside it.
 *
 * One place this still cannot look. `ReorderGrip` carries `minimumTouchTarget()`,
 * which is 24dp on the JVM and 44 to 48 on a phone: a row whose content is
 * shorter than that is inflated by its own grip there and not here, and a shadow
 * cast from a box taller than the row looks like it reads.
 */
class ReorderShadowTest {

    /**
     * How far the drawn shadow reaches past the **card** at each end.
     *
     * Past the card rather than measured absolutely, which is what makes the two
     * configurations comparable at all: a handle moves the card's trailing edge
     * inwards by the grip's width, so two absolute spans differ for a reason that
     * is not the question. The overshoot is the blur's own reach, and the blur
     * does not know whether there is a handle.
     */
    private fun shadowOvershoot(handle: ImageVector?): Pair<Int, Int> {
        val shot = liftedRow(handle)
        val y = shot.image.height / 2
        fun darkAt(x: Int): Boolean = (shot.image.getRGB(x, y) and 0xFFFFFF) != 0xFFFFFF

        val left = (0 until shot.image.width).firstOrNull { darkAt(it) } ?: shot.image.width
        val right = (shot.image.width - 1 downTo 0).firstOrNull { darkAt(it) } ?: 0

        return (shot.card.left.roundToInt() - left) to (right - shot.card.right.roundToInt())
    }

    /** A lifted row's frame, its own box, and the box its content drew in. */
    private class Lift(val image: BufferedImage, val row: Rect, val card: Rect)

    /**
     * The middle row of three, lifted without a gesture, and where it landed.
     *
     * The [Rect] is the lifted row's own layout bounds in scene pixels. It is
     * reported from inside rather than worked out here because it is the one
     * thing this file got wrong twice: the row's edges are a fact about the
     * layout, and reading them back off the drawing assumes the drawing is
     * right, which is the question.
     *
     * The bounds are the row's *unscaled* box. `ReorderableItem` draws a lifted
     * row at 1.02x about its centre, so the drawing is very slightly larger than
     * this and the rect stays strictly inside it — which is the safe direction
     * for something that is about to be scanned for holes.
     */
    private fun liftedRow(
        handle: ImageVector?,
        page: Color = Color.White,
    ): Lift {
        val rows = mutableStateListOf("Perth", "Daglish", "Subiaco")
        var image: BufferedImage? = null
        var bounds = Rect.Zero
        var card = Rect.Zero

        Scene(width = Width, height = Height) {
            val listState = rememberLazyListState()
            val reorder = rememberReorderableState(listState) { from, to ->
                rows.add(to, rows.removeAt(from))
            }
            // Lift the middle row without a gesture.
            androidx.compose.runtime.LaunchedEffect(Unit) { reorder.start(Lifted) }
            Box(Modifier.fillMaxSize().background(page).padding(Gutter.dp)) {
                LazyColumn(state = listState) {
                    itemsIndexed(rows) { index, name ->
                        ReorderableItem(
                            state = reorder,
                            index = index,
                            itemCount = rows.size,
                            handleIcon = handle,
                            modifier = if (index == Lifted) {
                                Modifier.reportBounds { bounds = it }
                            } else {
                                Modifier
                            },
                        ) {
                            ListItem(
                                modifier = if (index == Lifted) {
                                    Modifier.reportBounds { card = it }
                                } else {
                                    Modifier
                                },
                            ) { +name }
                        }
                    }
                }
            }
        }.use { scene ->
            image = scene.frames(30)
        }
        return Lift(image!!, bounds, card)
    }

    /**
     * Nothing shows through a row that has been picked up, handle or no handle.
     *
     * ### The page is red, and that is the whole instrument
     *
     * The first draft put the row on white and asked whether every pixel across
     * it matched the row's own surface. That fails on a correct row, because the
     * row has a **label** on it: 83 pixels of "Daglish" are not the surface and
     * never were. And it could not have succeeded either way — in a light theme
     * the surface and the page are both white, so "is this the page" and "is
     * this the row" are the same pixel.
     *
     * A red page separates them. Ink is grey, the surface is white, and neither
     * is red-dominant; the shadow *over* red is a darker red and still is — at
     * the alphas a shadow uses, `r` stays more than [RedMargin] clear of the
     * other two. So the question becomes a straight one: is any pixel inside the
     * row's own box still the page?
     *
     * Against the unfixed component it reports 1924 of them with a handle and
     * none without: the grip's 24dp column across the scanned band, less the
     * glyph's own ink.
     */
    @Test
    fun aLiftedCardIsOpaqueAllTheWayAcross() {
        for (handle in listOf(null, Tabler.Outline.GripVertical)) {
            val name = if (handle == null) "without a handle" else "with a handle"
            val shot = liftedRow(handle, page = PageRed)

            assertTrue(
                shot.card.width > MinimumSpan,
                "the lifted card is ${shot.card.width}px wide $name in a " +
                    "${Width}px scene, which is not a card — this measured nothing",
            )

            val holes = pageInside(shot.image, shot.card)
            assertTrue(
                holes == 0,
                "$holes pixels inside a lifted card $name are still the page " +
                    "showing through. Whatever a row's content paints, a card that " +
                    "has been picked up is solid across its own box: the lift's " +
                    "fill sits under the content for exactly the case where the " +
                    "content paints no ground of its own.",
            )
        }
    }

    /**
     * How many pixels inside [row] are still the page.
     *
     * A band across the middle rather than the whole box, because a row's
     * corners are rounded and the page legitimately shows in them. [CornerClear]
     * is a quarter of the row's height off each end, which on any plausible
     * corner radius is a long way clear of the arc.
     */
    private fun pageInside(shot: BufferedImage, row: Rect): Int {
        fun isPage(x: Int, y: Int): Boolean {
            val rgb = shot.getRGB(x, y)
            val r = rgb shr 16 and 0xFF
            val g = rgb shr 8 and 0xFF
            val b = rgb and 0xFF
            return r > g + RedMargin && r > b + RedMargin
        }

        val left = row.left.roundToInt() + EdgeSlack
        val right = row.right.roundToInt() - EdgeSlack
        val top = (row.top + row.height * CornerClear).roundToInt()
        val bottom = (row.bottom - row.height * CornerClear).roundToInt()

        var holes = 0
        for (y in top.coerceAtLeast(0) until bottom.coerceAtMost(shot.height)) {
            for (x in left.coerceAtLeast(0) until right.coerceAtMost(shot.width)) {
                if (isPage(x, y)) holes++
            }
        }
        return holes
    }

    /**
     * The shadow is cast by the card, so a handle moves it in with the card.
     *
     * **This inverts the claim this test used to make**, and the inversion is the
     * fix. It asserted that the shadow was the same width with a handle as
     * without — which was true, and was the defect: the layer casting it was the
     * whole row, the card beside a grip is 24dp narrower than the row, and the
     * result is a shadow tracing a rectangle wider than anything on screen.
     * Reported twice, the second time with a picture of a card whose shadow runs
     * on past its trailing edge.
     *
     * Measured as the overshoot **past the card** rather than as an absolute
     * span, because that is the quantity a blur decides and a handle does not: the
     * two configurations put the card's trailing edge in different places on
     * purpose, so comparing the spans compares the layouts. Against the unfixed
     * component the trailing overshoot came out about a grip wider than the
     * leading one — the handle's whole column — where now the two agree.
     */
    @Test
    fun theShadowHugsTheCardRatherThanTheRow() {
        val (bareLeft, bareRight) = shadowOvershoot(handle = null)
        val (handleLeft, handleRight) = shadowOvershoot(handle = Tabler.Outline.GripVertical)

        assertTrue(
            bareLeft > 0 && bareRight > 0,
            "the shadow did not reach past the card at all without a handle " +
                "($bareLeft, $bareRight), so this measured nothing",
        )
        assertTrue(
            kotlin.math.abs(bareLeft - handleLeft) <= Tolerance &&
                kotlin.math.abs(bareRight - handleRight) <= Tolerance,
            "the lifted card's shadow reaches ${handleLeft}px past its leading " +
                "edge and ${handleRight}px past its trailing one with a handle, " +
                "against $bareLeft and $bareRight without one. The shadow belongs " +
                "to the card, so a grip beside the card must not stretch it: an " +
                "overshoot that is larger on the handle's side by about the grip's " +
                "width is a shadow still being cast by the whole row.",
        )
    }

    /**
     * Turning handles on narrows the card over several frames rather than one.
     *
     * The other half of the same report: *"can we make it so when drag handles are
     * enabled/disabled, then animate in and out from their respective side? that
     * would also mean animating the width of the item"*. It was an `if` — the
     * content went from filling the row to being a `weight(1f)` sibling of a grip
     * between two frames, and 24dp of text reflowed with no warning.
     *
     * **The card's width is what is measured, not the grip's.** The grip is what
     * animates, but the thing a reader notices is the row's text moving, and that
     * is the card — so the assertion is on the quantity that was reported rather
     * than on the mechanism that fixes it. Any mechanism that animates the card's
     * width passes this; the `if` fails it on the first frame.
     *
     * Three claims: it ends up narrower by about a grip, it visits at least one
     * width in between, and it stops. The last one matters because
     * `IdleAnimationTest` polices the "and then asks for no more frames" half
     * globally and this is where a spring that never settles would be born.
     */
    @Test
    fun turningTheHandleOnAnimatesTheCardsWidth() {
        val rows = mutableStateListOf("Perth", "Daglish", "Subiaco")
        var handle by mutableStateOf<ImageVector?>(null)
        var card = Rect.Zero
        val widths = mutableListOf<Int>()

        Scene(width = Width, height = Height) {
            val listState = rememberLazyListState()
            val reorder = rememberReorderableState(listState) { from, to ->
                rows.add(to, rows.removeAt(from))
            }
            Box(Modifier.fillMaxSize().background(Color.White).padding(Gutter.dp)) {
                LazyColumn(state = listState) {
                    itemsIndexed(rows) { index, name ->
                        ReorderableItem(
                            state = reorder,
                            index = index,
                            itemCount = rows.size,
                            handleIcon = handle,
                        ) {
                            ListItem(
                                modifier = if (index == Lifted) {
                                    Modifier.reportBounds { card = it }
                                } else {
                                    Modifier
                                },
                            ) { +name }
                        }
                    }
                }
            }
        }.use { scene ->
            scene.frames(3)
            val before = card.width.roundToInt()
            handle = Tabler.Outline.GripVertical
            repeat(HandleFrames) {
                scene.frame()
                widths += card.width.roundToInt()
            }
            val after = widths.last()

            assertTrue(
                before - after > MinimumGrip,
                "the card was ${before}px wide with no handle and ${after}px with " +
                    "one, a difference of ${before - after}px. A grip is at least " +
                    "${MinimumGrip}px of row at this density, so this says the " +
                    "handle took no width from the content at all",
            )
            val between = widths.count { it in (after + Tolerance)..(before - Tolerance) }
            assertTrue(
                between > 0,
                "the card went from ${before}px to ${after}px without being " +
                    "measured at any width in between. The widths, frame by frame: " +
                    "$widths. A handle appearing used to be an `if`, which is a " +
                    "row of text reflowing between two frames; it animates from " +
                    "its own side now, and the card's width animates with it.",
            )
            assertTrue(
                widths.last() == widths[widths.size - 2],
                "the card was still moving on the last of $HandleFrames frames: " +
                    "$widths",
            )
        }
    }

    private companion object {
        const val Width = 600
        const val Height = 400
        const val Gutter = 24

        /** Which of the three rows gets picked up. */
        const val Lifted = 1

        /** A pixel either side, for antialiasing on the shadow's own fade. */
        const val Tolerance = 2

        /** A page nothing in the row is: ink is grey and the surface is white. */
        val PageRed = Color(0xFFCC2020)

        /** How much redder than its other channels a pixel has to be to be page. */
        const val RedMargin = 40

        /** Narrower than this is not a row in a 600px scene. */
        const val MinimumSpan = 200

        /** One pixel off each side, for the fill's own antialiased edge. */
        const val EdgeSlack = 2

        /** How much of the row's height to skip at each end, to clear its corners. */
        const val CornerClear = 0.25f

        /** Long enough for a `springDefault` width to arrive and stop. */
        const val HandleFrames = 40

        /**
         * The least a grip can cost the content, in scene pixels.
         *
         * `minimumTouchTarget` is 24dp on the JVM and the scene is 2x, so a grip
         * is 48px of row there. Half of that is a floor no correct layout is
         * under and no broken one reaches.
         */
        const val MinimumGrip = 24
    }
}
