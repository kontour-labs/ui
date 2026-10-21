package io.kontour.ui.platform

import io.kontour.ui.motion.ScrollFeel

/** The desktop's own fling: a wheel's and a trackpad's momentum are the device's. */
internal actual val platformScrollFeel: ScrollFeel? = null

/** A mouse or a trackpad, whose momentum is the device's. */
internal actual val platformLosesRestedFlicks: Boolean = false

/**
 * Notches: AWT's wheel rotation. A Mac's trackpad arrives as wheel events too,
 * not as a pan — AWT has none — with a fraction of a notch in each.
 */
internal actual val platformScrollDeltaInPixels: Boolean = false

/** Ten dp a unit on a Mac, as Compose's own lists scroll there; nothing elsewhere. */
internal actual val platformPreciseScrollDp: Float =
    if (System.getProperty("os.name").orEmpty().startsWith("Mac")) 10f else 0f

internal actual val platformGuardsHomeIndicator: Boolean = false

/** No overscroll at all. */
internal actual val platformOverscrollMovesContent: Boolean = false
