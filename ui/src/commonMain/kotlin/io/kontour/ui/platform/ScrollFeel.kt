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
 * Whether a finger landing on the home indicator is the system's from the start:
 * iOS, which hands the app a swipe home until it has recognised it. Where it is
 * true, the root of a `KontourTheme` keeps such a swipe from scrolling the app:
 * see [io.kontour.ui.input.guardHomeIndicator].
 */
internal expect val platformGuardsHomeIndicator: Boolean

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

/**
 * How far, in dp, a unit of a *precise* scroll's delta follows the fingers, or
 * 0 where a scroll's delta is only ever whole notches or pixels.
 *
 * A Mac, on the desktop: AWT hands on a trackpad's two-finger scroll as wheel
 * events whose rotation is a fraction, many to a gesture and accelerated, and
 * Compose's own lists on a Mac scroll ten dp for each unit of it (its
 * `MacOSCocoaConfig`). There is no pan on the desktop at all; Compose sends one
 * only where a platform reports one. A mouse's wheel on the same Mac still
 * turns in whole notches. See [io.kontour.ui.components.list.preciseScrollDistance].
 */
internal expect val platformPreciseScrollDp: Float

/**
 * Whether this platform's overscroll moves a scrollable's content bodily past
 * its end: iOS's, a rubber band that slides the whole content and springs it
 * back. Android's stretches the content in place and the desktop has none, so
 * neither carries the content's top anywhere. Where it is true, a collapsing
 * [io.kontour.ui.nav.TopBar] takes a push past the content's end itself, so the
 * content cannot slide up under it: see [io.kontour.ui.nav.TopBarCollapseState].
 */
internal expect val platformOverscrollMovesContent: Boolean
