package io.kontour.ui.foundation

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas

/*
 * Erasing part of what a node draws: a list's faded edge, a cut title, a wheel's
 * sliver of the next row.
 *
 * `BlendMode.DstOut` erases whatever is under it in the layer it is drawn into.
 * Inside a layer of the node's own that is the node's own pixels, which is the
 * point. Anywhere else it is everything already drawn there too: the surface
 * the node sits on, and through it whatever is under that.
 *
 * These used to isolate themselves with `graphicsLayer { compositingStrategy =
 * Offscreen }`, switched on only while there was something to erase, and erase
 * in a separate draw block. The two are separate pieces of state, kept in step
 * by the frame. Raised 2026-10-28 for 0.5.1: a list's faded edge in a sheet over
 * a map erased the sheet's surface as well as its rows, and the map showed
 * through the top of the sheet, for a stretch of frames at a time. That is the
 * erase drawn without the isolation. No test here could make it happen, and
 * one way it can is a parent's recorded drawing of the child layer outliving a
 * change to the child's compositing, while the child's own drawing is current.
 *
 * So the isolation is now part of the same drawing as the erase: a layer opened
 * round the content and the erase, in the draw block that decides to erase, and
 * not otherwise. There is nothing left to fall out of step.
 */

/**
 * Draws the content and then [erase] into a layer of their own, so the erase
 * takes out the content and nothing under it. Only call it when there is
 * something to erase: the layer is an offscreen pass, and every frame without
 * one should draw the content plainly.
 */
internal inline fun ContentDrawScope.drawContentErasing(
    area: Rect = Rect(Offset.Zero, size),
    erase: DrawScope.() -> Unit,
) {
    drawIntoCanvas { it.saveLayer(area, Paint()) }
    try {
        drawContent()
        erase()
    } finally {
        drawIntoCanvas { it.restore() }
    }
}
