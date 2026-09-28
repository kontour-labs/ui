package io.kontour.ui.catalog

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Whether [NativeScroller] draws anything here: on iOS, where UIKit's lists are the ones to match. */
internal expect val hasNativeScroller: Boolean

/**
 * The platform's own scrolling list, [rows] rows of [RowHeightDp], reporting
 * each throw as [ScrollFeelCard]'s own list does — how hard, and how far.
 * Nothing where [hasNativeScroller] is false.
 */
@Composable
internal expect fun NativeScroller(modifier: Modifier, rows: Int, onThrow: (Throw) -> Unit)
