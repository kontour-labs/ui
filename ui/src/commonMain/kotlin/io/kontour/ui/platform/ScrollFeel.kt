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

/**
 * Whether a scroll event's delta is in pixels rather than in notches of a wheel.
 *
 * The web, and only the web: Compose hands on the DOM wheel event's own `deltaX`
 * and `deltaY`, which are CSS pixels — a trackpad sends a few at a time, a mouse
 * wheel's click about a hundred. Everywhere else a delta is in notches, a click
 * being one. Compose's own scrollables read the DOM event's `deltaMode` to tell
 * them apart, from code the library cannot reach; see
 * [io.kontour.ui.components.list.sideScrollDistance], which is what reads this.
 */
internal expect val platformScrollDeltaInPixels: Boolean
