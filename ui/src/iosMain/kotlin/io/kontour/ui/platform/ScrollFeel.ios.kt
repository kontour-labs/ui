package io.kontour.ui.platform

import io.kontour.ui.motion.ScrollFeel

/**
 * UIKit's deceleration, its flicks that add up, and a throw read a fifth faster.
 *
 * The fifth is from a phone, twice: flings on iOS stopped "too soon", and once
 * the rate was UIKit's they still had "a bit of friction". The rate *is* UIKit's,
 * so what is left is the speed the throw is read at, and Compose's tracker is not
 * UIKit's. A fifth is a starting point, not a measurement: tune it live on the
 * catalog's Frames page, against the native scroll view beside it.
 */
internal actual val platformScrollFeel: ScrollFeel? = ScrollFeel(velocityScale = 1.2f)

/** Compose's UIKit tracker: a rest before a flick, or a late frame, reads as no speed. */
internal actual val platformLosesRestedFlicks: Boolean = true
