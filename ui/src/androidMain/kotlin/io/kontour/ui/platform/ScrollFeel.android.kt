package io.kontour.ui.platform

import io.kontour.ui.motion.ScrollFeel

/** Android's own fling. */
internal actual val platformScrollFeel: ScrollFeel? = null

/** Android's tracker reads what the finger did. */
internal actual val platformLosesRestedFlicks: Boolean = false
