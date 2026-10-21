package io.kontour.ui.platform

import io.kontour.ui.motion.ScrollFeel

/** The browser's own fling. */
internal actual val platformScrollFeel: ScrollFeel? = null

/** The browser's own. */
internal actual val platformLosesRestedFlicks: Boolean = false

/** CSS pixels: the DOM wheel event's own deltas. */
internal actual val platformScrollDeltaInPixels: Boolean = true

/** None: a scroll is whole notches, or the browser's pixels. */
internal actual val platformPreciseScrollDp: Float = 0f

internal actual val platformGuardsHomeIndicator: Boolean = false

/** No overscroll of Compose's own. */
internal actual val platformOverscrollMovesContent: Boolean = false
