package io.kontour.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal actual val platformSupportsBackdropBlur: Boolean = true

/** Zero: A desktop window has no system bar inside it; the chrome is the window manager's and sits outside. */
@Composable
internal actual fun platformOpaqueBottomInset(): Dp = 0.dp
