package io.kontour.ui.catalog

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalView
import kotlin.math.roundToInt

/**
 * The fastest mode the attached display supports, not the mode it is in.
 *
 * `Display.getRefreshRate()` reports the *current* mode, and an Android phone
 * with a variable-rate panel sits at sixty until something asks for more — so
 * reading it would make the budget depend on when the readout happened to be
 * switched on. `supportedModes` is the ceiling, which is what a budget is
 * measured against.
 *
 * `LocalView.current.display` is null before the view is attached to a window,
 * which is a real state during the first composition. [FallbackHz] then, and it
 * corrects itself on the next composition once attached.
 */
@Composable
internal actual fun platformDisplayHz(): Int {
    val display = LocalView.current.display ?: return FallbackHz
    val fastest = display.supportedModes
        ?.maxOfOrNull { it.refreshRate }
        ?: display.refreshRate
    return if (fastest > 0f) fastest.roundToInt() else FallbackHz
}
