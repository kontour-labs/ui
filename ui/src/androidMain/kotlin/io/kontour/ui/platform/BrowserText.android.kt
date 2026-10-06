package io.kontour.ui.platform

import androidx.compose.ui.Modifier

/** A field's touches are the field's own here: there is no browser to hand them to. */
internal actual val textTouchesGoToTheBrowser: Boolean = false

internal actual fun Modifier.browserText(text: BrowserText): Modifier = this
