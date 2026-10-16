package io.kontour.ui.catalog

/** Not kept here: the question is the iPhone's. See [WarmUp]. */
internal actual val warmUpStored: Boolean = false

internal actual fun storedWarmUp(): WarmUp = WarmUp.Off

internal actual fun storeWarmUp(mode: WarmUp) = Unit
