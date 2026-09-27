package io.kontour.ui.platform

import io.kontour.ui.motion.BackStyle

/**
 * How back looks where this is running, before anything overrides it.
 *
 * Android and iOS each have a way back that their users' hands already know;
 * the desktop and the web have Escape and a browser button, which complete at
 * once, so there only a push or a pop is ever seen — and `Predictive`'s push
 * and pop are the shared axis every page in the library already moves on.
 */
internal expect val platformBackStyle: BackStyle

/**
 * Whether the library tracks the back swipe itself rather than taking the
 * platform's: true on iOS.
 *
 * Compose's own UIKit edge pan reports only that the finger lifted. A
 * `NavDisplay` then pops, and finishes the transition from where the hand left
 * it at the transition's own pace, a frame later: the page stops for a moment
 * as the finger lifts, and a flick leaves no faster than a slow drag. The
 * library's pan keeps going at the finger's speed instead — see
 * `SwipeBackInput`. The outermost `OverlayHost` takes the swipe over: it gives
 * everything under it a back dispatcher of its own, so the platform's edge pan,
 * which arms only while the platform's dispatcher has something to go back to,
 * never does.
 *
 * Android's back is the system's, and the desktop and the web have no swipe.
 */
internal expect val libraryTracksBackSwipe: Boolean
