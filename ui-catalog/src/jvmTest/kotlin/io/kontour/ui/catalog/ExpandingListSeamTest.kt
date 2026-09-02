package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import io.kontour.ui.components.list.ExpandingListItem
import io.kontour.ui.components.list.ListItemDefaults
import io.kontour.ui.components.list.ListItemPosition
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.KontourTheme
import io.kontour.ui.theme.Theme
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The header's corners follow what is underneath it.
 *
 * This is the entire reason the component exists rather than an
 * `io.kontour.ui.components.display.Accordion` with rows in it. An accordion
 * draws its own frame, so its body is a block *under* a row; this hands its
 * children to the same `ListGroupScope` a `ListGroup` uses, so they are rows in
 * the same run — and a run of rows is held together by its seams.
 *
 * Shut, a lone group is one row and rounds on all four corners. Open, that same
 * header has rows after it, so its bottom corners must square off and the last
 * child's must round instead. Get it wrong and the group reads as a card that
 * appeared under a pill.
 *
 * ### Measured at the corner itself
 *
 * A rounded corner leaves the page showing in the few pixels outside the arc; a
 * square one does not. So the test samples the pixel just inside the row's
 * bottom-left corner and asks whether it is the row's ground or the page behind
 * it. Nothing else about the component distinguishes the two states at that
 * point, which is what makes it the right pixel to look at.
 */
class ExpandingListSeamTest {

    @Test
    fun theHeaderSquaresOffWhenSomethingOpensBelowIt() {
        val shut = corners(expanded = false)
        val open = corners(expanded = true)

        assertTrue(
            shut.headerBottomIsRounded,
            "a shut group is one row on its own and its header is rounded at the " +
                "bottom — this one is square, so it is drawing as though something " +
                "follows it",
        )
        assertTrue(
            !open.headerBottomIsRounded,
            "an open group has rows after its header and the header's bottom " +
                "corners must square off into them. They are rounded, so the " +
                "children read as a separate object below a pill rather than as " +
                "the same run continuing.",
        )
        assertTrue(
            open.lastChildBottomIsRounded,
            "the last child of an open group closes it and must be rounded at the " +
                "bottom — it is square, so the group has no end",
        )
    }

    private class Corners(
        val headerBottomIsRounded: Boolean,
        val lastChildBottomIsRounded: Boolean,
    )

    private fun corners(expanded: Boolean): Corners {
        var header = Rect.Zero
        var last = Rect.Zero
        var page = 0
        var frame: BufferedImage? = null

        Scene(width = 760, height = 800, density = Density.toFloat()) {
            KontourTheme(reduceMotion = true) {
                OverlayHost(Modifier.fillMaxSize()) {
                    Box(
                        Modifier.fillMaxSize().background(Theme.colours.background),
                        Alignment.TopCenter,
                    ) {
                        Column(
                            Modifier.padding(24.dp).width(300.dp),
                            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.Spacing),
                        ) {
                            ExpandingListItem(
                                expanded = expanded,
                                onExpandedChange = {},
                                // On its own, so shut it is `Only` and every
                                // corner is rounded — which is the state the
                                // open one has to differ from.
                                position = ListItemPosition.Only,
                                modifier = Modifier.reportBounds { header = it },
                                header = { +"Perth Underground" },
                            ) {
                                item("Platform 1")
                                item("Platform 2")
                            }
                        }
                    }
                }
            }
        }.use { scene ->
            frame = scene.frames(24)
            page = frame.getRGB(4, 4)
        }

        val image = requireNotNull(frame)
        // `reportBounds` is on the whole component, so its top is the header's
        // top and its bottom is the last child's bottom — which is exactly the
        // two corners under test.
        val headerBottom = header.top + RowHeight
        return Corners(
            headerBottomIsRounded = image.isPage(header.left, headerBottom, page),
            lastChildBottomIsRounded = image.isPage(header.left, header.bottom, page),
        )
    }

    /**
     * Whether the corner pixel is still the page.
     *
     * Sampled one pixel in from the row's edge and one up from its last row: a
     * rounded corner has not reached the corner of its own box there, so the
     * page shows through. A square one has.
     */
    private fun BufferedImage.isPage(left: Float, bottom: Float, page: Int): Boolean {
        val x = (left + Inset).toInt().coerceIn(0, width - 1)
        val y = (bottom - Inset).toInt().coerceIn(0, height - 1)
        return getRGB(x, y) == page
    }

    private companion object {
        const val Density = 2

        /**
         * A single-line row's height at this density.
         *
         * The header is one line with no supporting text, so it is the list's
         * own minimum — and its bottom edge is where the seam under test is.
         */
        const val RowHeight = 96f

        /** Inside the corner's arc, outside its antialiasing. */
        const val Inset = 3
    }
}
