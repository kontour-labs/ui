package io.kontour.ui.adaptive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import io.kontour.ui.input.InputModality
import io.kontour.ui.input.LocalInputModality

/**
 * Everything a layout needs to decide its shape.
 *
 * Bundled because the decisions are rarely about one of them. "Show two panes"
 * is a question about width *and* whether the user has a pointer — a 900dp
 * touchscreen in someone's hands is not a 900dp desktop window, and a layout
 * that consults only the size class treats them the same.
 */
@Immutable
data class WindowAdaptiveInfo(
    val size: WindowSizeClass,
    val modality: InputModality,
) {
    /** Room beside the content for a rail or drawer. */
    val hasRoomBeside: Boolean get() = size.width.hasRoomBeside

    /** Room for two panes of content at once. */
    val hasRoomForTwoPanes: Boolean get() = size.width.hasRoomForTwoPanes

    /**
     * True where a layout can assume precise pointing.
     *
     * Governs the things that are cheap for a mouse and awkward for a thumb: a
     * resize handle, a hover-revealed control, a dense table row.
     */
    val isPrecise: Boolean get() = modality.supportsHover
}

/** The current window's adaptive info. */
val windowAdaptiveInfo: WindowAdaptiveInfo
    @Composable @ReadOnlyComposable
    get() = WindowAdaptiveInfo(
        size = LocalWindowSizeClass.current,
        modality = LocalInputModality.current,
    )
