package io.kontour.ui.platform

import io.kontour.ui.motion.ScrollFeel

/** The desktop's own fling: a wheel's and a trackpad's momentum are the device's. */
internal actual val platformScrollFeel: ScrollFeel? = null

/** A mouse or a trackpad, whose momentum is the device's. */
internal actual val platformLosesRestedFlicks: Boolean = false
