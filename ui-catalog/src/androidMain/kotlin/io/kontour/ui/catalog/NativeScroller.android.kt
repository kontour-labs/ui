package io.kontour.ui.catalog

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

internal actual val hasNativeScroller: Boolean = false

@Composable
internal actual fun NativeScroller(modifier: Modifier, rows: Int, onThrow: (Throw) -> Unit) = Unit
