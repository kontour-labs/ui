package io.kontour.ui.platform

import io.kontour.ui.motion.BackStyle

/** UIKit's interactive pop: the page follows the finger from the leading edge, over the one beneath. */
internal actual val platformBackStyle: BackStyle = BackStyle.Swipe

/** Compose's UIKit edge pan stops for a frame at the release; the library's own carries on. */
internal actual val libraryTracksBackSwipe: Boolean = true
