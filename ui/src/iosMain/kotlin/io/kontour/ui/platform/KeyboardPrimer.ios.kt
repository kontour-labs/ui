package io.kontour.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** A field's focus opens the keyboard here; there is nothing to prime. */
internal actual fun Modifier.opensKeyboard(enabled: Boolean): Modifier = this

@Composable
internal actual fun InstallKeyboardPrimer() = Unit
