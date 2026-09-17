package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.mutableStateListOf
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

    private fun shadowReach(handle: ImageVector?): Pair<Int, Int> {
        val (shot, _) = liftedRow(handle)
        val y = shot.height / 2
        fun darkAt(x: Int): Boolean = (shot.getRGB(x, y) and 0xFFFFFF) != 0xFFFFFF

        val left = (0 until shot.width).firstOrNull { darkAt(it) } ?: shot.width
        val right = (shot.width - 1 downTo 0).firstOrNull { darkAt(it) } ?: 0

        return left to right
    }

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
    ): Pair<BufferedImage, Rect> {
        val rows = mutableStateListOf("Perth", "Daglish", "Subiaco")
        var image: BufferedImage? = null
        var bounds = Rect.Zero

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
                            ListItem { +name }
                        }
                    }
                }
            }
        }.use { scene ->
            image = scene.frames(30)
        }
        return image!! to bounds
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
    fun aLiftedRowIsOpaqueAllTheWayAcross() {
        for (handle in listOf(null, Tabler.Outline.GripVertical)) {
            val name = if (handle == null) "without a handle" else "with a handle"
            val (shot, row) = liftedRow(handle, page = PageRed)

            assertTrue(
                row.width > MinimumSpan,
                "the lifted row is ${row.width}px wide $name in a ${Width}px " +
                    "scene, which is not a row — this measured nothing",
            )

            val holes = pageInside(shot, row)
            assertTrue(
                holes == 0,
                "$holes pixels inside a lifted row $name are still the page " +
                    "showing through. A row that has been picked up is one card " +
                    "from edge to edge: the shadow is cast by the whole row and " +
                    "the fill used to come only from its content, so beside a " +
                    "handle nothing was painted and the shadow showed through it.",
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

    @Test
    fun theShadowIsTheSameWidthWithAHandleAsWithout() {
        val (bareLeft, bareRight) = shadowReach(handle = null)
        val (handleLeft, handleRight) = shadowReach(handle = Tabler.Outline.GripVertical)

        assertTrue(
            bareRight > bareLeft,
            "nothing was drawn without a handle, so this measured nothing: " +
                "$bareLeft..$bareRight",
        )
        assertTrue(
            kotlin.math.abs(bareLeft - handleLeft) <= Tolerance &&
                kotlin.math.abs(bareRight - handleRight) <= Tolerance,
            "the lifted row's shadow spans $handleLeft..$handleRight with a " +
                "handle and $bareLeft..$bareRight without one. A handle changes " +
                "what is *inside* the row; it must not change the box the shadow " +
                "is cast from.",
        )
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
    }
}
