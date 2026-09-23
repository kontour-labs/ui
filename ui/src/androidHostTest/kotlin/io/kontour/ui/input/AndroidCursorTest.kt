package io.kontour.ui.input

import androidx.compose.ui.input.pointer.PointerIcon
import kotlin.test.Test
import kotlin.test.assertEquals
import android.view.PointerIcon as AndroidPointerIcon

/**
 * What every [Cursor] draws on Android, entry by entry.
 *
 * The Android mapping was written without an Android SDK to compile it against,
 * and CI only ever built it. This is the table it has to match, run on the host:
 * Compose's `PointerIcon(Int)` is a plain value holding the type, compared by
 * value, so nothing here touches the framework the host does not have.
 */
class AndroidCursorTest {

    private val own = mapOf(
        Cursor.ResizeColumn to PointerIcon(AndroidPointerIcon.TYPE_HORIZONTAL_DOUBLE_ARROW),
        Cursor.ResizeRow to PointerIcon(AndroidPointerIcon.TYPE_VERTICAL_DOUBLE_ARROW),
        Cursor.Grab to PointerIcon(AndroidPointerIcon.TYPE_GRAB),
        Cursor.Grabbing to PointerIcon(AndroidPointerIcon.TYPE_GRABBING),
        Cursor.NotAllowed to PointerIcon(AndroidPointerIcon.TYPE_NO_DROP),
    )

    @Test
    fun androidDrawsItsOwnShapeWhereItHasOne() {
        for ((cursor, icon) in own) {
            assertEquals(icon, pointerIconFor(cursor), "$cursor on Android")
        }
    }

    @Test
    fun progressIsTheArrowNotTheHourglass() {
        // `TYPE_WAIT` says the app has stopped, which a background task has not.
        assertEquals(PointerIcon.Default, pointerIconFor(Cursor.Progress))
    }

    @Test
    fun theFourCommonShapesAreComposesOwn() {
        assertEquals(PointerIcon.Default, pointerIconFor(Cursor.Default))
        assertEquals(PointerIcon.Hand, pointerIconFor(Cursor.Pointer))
        assertEquals(PointerIcon.Text, pointerIconFor(Cursor.Text))
        assertEquals(PointerIcon.Crosshair, pointerIconFor(Cursor.Crosshair))
    }
}
