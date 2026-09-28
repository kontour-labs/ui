package io.kontour.ui.platform

import io.kontour.ui.motion.ScrollFeel

/**
 * The library's fling where this is running, or null for the platform's own: iOS
 * has one, for the accumulation UIKit's scroll views have and Compose's do not.
 * See [ScrollFeel].
 */
internal expect val platformScrollFeel: ScrollFeel?

/**
 * Whether this platform's velocity tracker reports a thrown finger as still:
 * iOS's, whose rule is the one [io.kontour.ui.motion.FingerTrail] explains. Where
 * it is true, the root of a `KontourTheme` reads a release that reports nothing
 * from the finger's own path.
 */
internal expect val platformLosesRestedFlicks: Boolean
