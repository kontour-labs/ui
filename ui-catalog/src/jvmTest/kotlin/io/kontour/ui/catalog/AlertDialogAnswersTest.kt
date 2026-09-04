package io.kontour.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import io.kontour.ui.overlay.AlertDialog
import io.kontour.ui.overlay.OverlayHost
import io.kontour.ui.theme.Theme
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Which of an alert's three buttons gets a line of its own.
 *
 * Three equal thirds put three verbs in a third of a dialog each, which is where
 * a button starts ellipsising itself — so one of the three has to take a full
 * line. It used to be the confirm, leaving the two ghosts sharing the row above
 * it. That is backwards: "Save" and "Don't save" are the question, and "Cancel"
 * is declining to answer it. Beside each other, three buttons read as a
 * three-way choice; the answers together with the way out beneath them read as
 * what it is.
 *
 * ### Found by its fill, and by the *size* of it
 *
 * The confirm is the only filled button on the dialog. Matching `colours.primary`
 * alone is not enough to find it, though — in this theme that token is very
 * nearly black, so the first version of this matched every glyph on the dialog
 * and reported a button the size of the whole thing. What identifies a filled
 * button is not the colour but the *run*: a hundred consecutive pixels of one
 * colour is a button, and no letterform is.
 *
 * The colour still comes out of the theme rather than being written down here,
 * so a token change cannot quietly turn this into a test that measures nothing.
 */
class AlertDialogAnswersTest {

    @Test
    fun theConfirmSharesTheTopRowAndCancelTakesItsOwnLine() {
        var primary = 0
        var image: BufferedImage? = null

        Scene(width = 900, height = 700) {
            primary = Theme.colours.primary.toArgb()
            Box(Modifier.fillMaxSize().background(Color.White)) {
                OverlayHost(Modifier.fillMaxSize()) {
                    AlertDialog(
                        visible = true,
                        onDismissRequest = {},
                        confirmLabel = "Discard",
                        onConfirm = {},
                        neutralLabel = "Keep editing",
                        onNeutral = {},
                        cancelLabel = "Cancel",
                    ) {
                        +"Leave without saving?"
                        supporting { +"This journey has unsaved changes." }
                    }
                }
            }
        }.use { scene -> image = scene.frames(20) }

        val frame = requireNotNull(image)
        val fill = requireNotNull(frame.filledButton(primary)) {
            "no filled confirm button was drawn at all"
        }

        // How much of the dialog's own width the confirm is taking. Measured
        // against the surface rather than the scene, because the dialog is
        // narrower than the window.
        val dialog = frame.surfaceSpanAt(fill.top + (fill.bottom - fill.top) / 2)
        val fillWidth = fill.right - fill.left + 1

        assertTrue(
            fillWidth < dialog * 0.7,
            "the confirm is ${fillWidth}px wide in a ${dialog}px dialog — it is " +
                "taking a full line of its own, which is the line cancel should " +
                "have",
        )
        assertTrue(
            frame.hasInkBelow(fill.bottom),
            "nothing is drawn below the confirm — cancel is not on its own line " +
                "beneath the two answers",
        )
    }

    @Test
    fun twoAnswersStayOnOneRow() {
        var primary = 0
        var image: BufferedImage? = null

        Scene(width = 900, height = 700) {
            primary = Theme.colours.primary.toArgb()
            Box(Modifier.fillMaxSize().background(Color.White)) {
                OverlayHost(Modifier.fillMaxSize()) {
                    AlertDialog(
                        visible = true,
                        onDismissRequest = {},
                        confirmLabel = "Remove",
                        onConfirm = {},
                        cancelLabel = "Cancel",
                    ) {
                        +"Remove this stop?"
                    }
                }
            }
        }.use { scene -> image = scene.frames(20) }

        val frame = requireNotNull(image)
        val fill = requireNotNull(frame.filledButton(primary)) {
            "no filled confirm button was drawn at all"
        }
        assertTrue(
            !frame.hasInkBelow(fill.bottom),
            "something is drawn below the confirm — two answers should share one " +
                "row, and nothing here asked for a second",
        )
    }

    /**
     * Where the one filled button is.
     *
     * Rows carrying a run of [SolidRun] pixels of [argb] and nothing else. A
     * glyph in the same colour never manages a run that long, so this finds the
     * button and skips the text.
     */
    private fun BufferedImage.filledButton(argb: Int): Bounds? {
        var left = width
        var top = height
        var right = -1
        var bottom = -1
        for (y in 0 until height) {
            var run = 0
            for (x in 0 until width) {
                if (getRGB(x, y) == argb) {
                    run++
                    if (run < SolidRun) continue
                    if (x - run + 1 < left) left = x - run + 1
                    if (y < top) top = y
                    if (x > right) right = x
                    if (y > bottom) bottom = y
                } else {
                    run = 0
                }
            }
        }
        return if (right < 0) null else Bounds(left, top, right, bottom)
    }

    /** How wide the dialog's white surface is on one row. */
    private fun BufferedImage.surfaceSpanAt(y: Int): Int {
        var left = width
        var right = -1
        for (x in 0 until width) {
            if (!isSurface(getRGB(x, y))) continue
            if (x < left) left = x
            if (x > right) right = x
        }
        return if (right < 0) 0 else right - left + 1
    }

    /**
     * Whether anything is drawn on the dialog below a given row.
     *
     * Only where the dialog's surface still is, so the scrim below it and the
     * shadow around it are never mistaken for a second row of buttons.
     */
    private fun BufferedImage.hasInkBelow(y: Int): Boolean {
        for (row in (y + Gap) until height) {
            var onSurface = false
            var ink = false
            for (x in 0 until width) {
                val rgb = getRGB(x, row)
                if (isSurface(rgb)) {
                    onSurface = true
                } else if (onSurface) {
                    // Past the surface's leading edge and not the surface, so it
                    // is either something drawn on it or its trailing edge. The
                    // edge is one antialiased pixel; ink is many.
                    ink = true
                }
            }
            // A row with no surface at all is below the dialog entirely.
            if (!onSurface) return false
            if (ink && countOffSurface(row) > EdgePixels) return true
        }
        return false
    }

    /** How many pixels of a row are inside the dialog but not its surface. */
    private fun BufferedImage.countOffSurface(row: Int): Int {
        var first = -1
        var last = -1
        for (x in 0 until width) {
            if (!isSurface(getRGB(x, row))) continue
            if (first < 0) first = x
            last = x
        }
        if (first < 0) return 0
        var off = 0
        for (x in first until last) if (!isSurface(getRGB(x, row))) off++
        return off
    }

    private fun isSurface(rgb: Int): Boolean = (rgb and 0xFFFFFF) == 0xFFFFFF

    private data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int)

    private companion object {
        /** Long enough to be a button and too long to be a letterform. */
        const val SolidRun = 100

        /** Clear of the confirm's own antialiased bottom edge. */
        const val Gap = 6

        /** More off-surface pixels than a rounded corner can account for. */
        const val EdgePixels = 12
    }
}
