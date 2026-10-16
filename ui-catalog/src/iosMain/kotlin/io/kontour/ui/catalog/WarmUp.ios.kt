package io.kontour.ui.catalog

import platform.Foundation.NSUserDefaults

private const val Key = "io.kontour.catalog.warmUp"

internal actual val warmUpStored: Boolean = true

internal actual fun storedWarmUp(): WarmUp =
    WarmUp.entries.getOrNull(NSUserDefaults.standardUserDefaults.integerForKey(Key).toInt()) ?: WarmUp.Off

internal actual fun storeWarmUp(mode: WarmUp) {
    NSUserDefaults.standardUserDefaults.setInteger(mode.ordinal.toLong(), forKey = Key)
}
