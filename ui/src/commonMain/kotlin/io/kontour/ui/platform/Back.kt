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
