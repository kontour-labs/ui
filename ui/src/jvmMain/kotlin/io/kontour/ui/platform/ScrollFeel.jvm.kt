package io.kontour.ui.platform

import io.kontour.ui.motion.ScrollFeel

/** The desktop's own fling: a wheel's and a trackpad's momentum are the device's. */
internal actual val platformScrollFeel: ScrollFeel? = null

/** A mouse or a trackpad, whose momentum is the device's. */
internal actual val platformLosesRestedFlicks: Boolean = false

/** Notches: AWT's wheel rotation. A Mac trackpad arrives as a pan instead. */
internal actual val platformScrollDeltaInPixels: Boolean = false

internal actual val platformGuardsHomeIndicator: Boolean = false

/** No overscroll at all. */
internal actual val platformOverscrollMovesContent: Boolean = false
