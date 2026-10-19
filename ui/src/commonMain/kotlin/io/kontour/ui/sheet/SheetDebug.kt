package io.kontour.ui.sheet

/**
 * **Temporary.** A log of a sheet's drag, for the full-height sheet on Android
 * that follows a finger from under the status bar for a moment and then springs
 * back to the top with the finger still down. It does not reproduce off a
 * phone, so this says on one what the sheet saw: every pointer event the drag
 * and the finger tracker were handed, why the drag ended, what the nested scroll
 * was offered, and every move the state was asked to make.
 *
 * On Android `println` goes to logcat under `System.out`:
 * `adb logcat -s System.out | grep KSHEET`. Remove this file and every
 * `sheetLog` call once the cause is found.
 */
internal var sheetDebug: Boolean = true

internal inline fun sheetLog(message: () -> String) {
    if (sheetDebug) println("KSHEET ${message()}")
}

/** A pixel figure to one decimal place, short enough to read down a column. */
internal fun Float.px(): String = if (isNaN()) "NaN" else ((this * 10f).toInt() / 10f).toString()
