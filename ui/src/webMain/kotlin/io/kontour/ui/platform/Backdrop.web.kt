package io.kontour.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal actual val platformSupportsBackdropBlur: Boolean = true

/** Zero: A browser's own chrome is outside the viewport, and a page has no bar of its own to report. */
@Composable
internal actual fun platformOpaqueBottomInset(): Dp = 0.dp
