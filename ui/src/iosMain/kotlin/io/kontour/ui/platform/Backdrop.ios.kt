package io.kontour.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal actual val platformSupportsBackdropBlur: Boolean = true

/** Zero: The home indicator is drawn over the app rather than reserving a strip of it, so there is never an opaque bar inside an iOS window. */
@Composable
internal actual fun platformOpaqueBottomInset(): Dp = 0.dp
