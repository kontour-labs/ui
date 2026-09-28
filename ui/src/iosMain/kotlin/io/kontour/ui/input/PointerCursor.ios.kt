package io.kontour.ui.input

import androidx.compose.ui.input.pointer.PointerIcon

/**
 * Nothing. Compose on iOS has no cursor backend, so `pointerHoverIcon` is inert
 * there and a pointer on an iPad keeps the system's own adaptive shape — which is
 * the right shape for that platform in any case.
 */
internal actual fun platformPointerIcon(cursor: Cursor): PointerIcon? = null
