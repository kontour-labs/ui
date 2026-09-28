package io.kontour.ui.platform

import io.kontour.ui.motion.ScrollFeel

/**
 * The library's fling where this is running, or null for the platform's own: iOS
 * has one, for the accumulation UIKit's scroll views have and Compose's do not.
 * See [ScrollFeel].
 */
internal expect val platformScrollFeel: ScrollFeel?
