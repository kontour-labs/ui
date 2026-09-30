package io.kontour.ui.platform

import io.kontour.ui.motion.BackStyle

/** No back gesture — Escape completes at once — so only push and pop show, as the shared axis. */
internal actual val platformBackStyle: BackStyle = BackStyle.Predictive

/** No swipe: Escape completes at once. */
internal actual val libraryTracksBackSwipe: Boolean = false

internal actual val libraryRelaysSystemBack: Boolean = false
