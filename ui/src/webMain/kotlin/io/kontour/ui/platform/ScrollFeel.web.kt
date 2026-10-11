package io.kontour.ui.platform

import io.kontour.ui.motion.ScrollFeel

/** The browser's own fling. */
internal actual val platformScrollFeel: ScrollFeel? = null

/** The browser's own. */
internal actual val platformLosesRestedFlicks: Boolean = false

/** CSS pixels: the DOM wheel event's own deltas. */
internal actual val platformScrollDeltaInPixels: Boolean = true

internal actual val platformGuardsHomeIndicator: Boolean = false
