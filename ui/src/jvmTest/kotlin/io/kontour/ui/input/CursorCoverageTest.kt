package io.kontour.ui.input

import androidx.compose.ui.input.pointer.PointerIcon
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What every [Cursor] draws on the desktop, entry by entry.
 *
 * The cursor set used to stop at Compose's common four on the strength of a note
 * saying no other shape was reachable. Nobody had checked, and it was wrong; this
 * is the check. A new [Cursor] cannot be added without somebody deciding what the
 * desktop draws for it — the actuals' `when`s are exhaustive, so the compiler asks
 * the other three platforms the same question — and a mapping that quietly fell
 * back to the hand everywhere would fail here rather than ship looking fine.
 *
 * On the desktop only, because it is the one target whose icons a JVM test can
 * construct and compare. The web and Android mappings are checked to compile.
 */
class CursorCoverageTest {

    private val common = setOf(PointerIcon.Default, PointerIcon.Hand, PointerIcon.Text, PointerIcon.Crosshair)

    /** The three AWT has a real shape for. */
    private val own = setOf(Cursor.ResizeColumn, Cursor.ResizeRow, Cursor.Grabbing)

    @Test
    fun theDesktopDrawsItsOwnShapeWhereItHasOne() {
        val borrowed = own.filter { pointerIconFor(it) in common }
        assertTrue(
            borrowed.isEmpty(),
            "${borrowed.joinToString()} drew one of Compose's four common shapes on the " +
                "desktop, which has a shape of its own for each — the pane splitter shows " +
                "a hand again, which is the thing this set exists to stop",
        )
    }

    @Test
    fun everyOtherCursorDrawsTheNearestCommonShape() {
        for (cursor in Cursor.entries - own) {
            assertEquals(
                cursor.nearest,
                pointerIconFor(cursor),
                "$cursor should draw the nearest common shape on the desktop, where AWT " +
                    "has nothing that means the same thing",
            )
        }
    }

    /**
     * A platform that cannot draw a drag shape falls back to the hand, never the
     * arrow. On the web, where Compose keeps the browser's cursors to itself, this
     * is what a pane splitter shows — and the arrow there would take away the one
     * hint it had before it had a shape of its own.
     */
    @Test
    fun aDraggedThingNeverFallsBackToTheArrow() {
        val dragged = listOf(Cursor.ResizeColumn, Cursor.ResizeRow, Cursor.Grab, Cursor.Grabbing)
        for (cursor in dragged) {
            assertEquals(
                PointerIcon.Hand,
                cursor.nearest,
                "$cursor falls back to ${cursor.nearest} where a platform has no shape for it",
            )
        }
    }
}
