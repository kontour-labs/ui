package io.kontour.ui.catalog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UIScreen

/**
 * `maximumFramesPerSecond`, which is the ceiling rather than the rate right now.
 *
 * The ceiling is the right one to ask for. A ProMotion display varies its rate
 * with what is on it, so "the rate right now" would move under the readout —
 * and the readout holds it near the ceiling anyway by asking for a frame every
 * frame, which [FrameReadout]'s own docstring is explicit about.
 *
 * This is also the number `CADisableMinimumFrameDurationOnPhone` governs. With
 * that key absent from the app's `Info.plist` iOS caps the app at sixty whatever
 * the panel can do — `showcase/ios/KontourUI/Info.plist` sets it, and
 * `docs/check-xcode-host.py` fails if it stops being set.
 */
@Composable
internal actual fun platformDisplayHz(): Int = remember {
    UIScreen.mainScreen.maximumFramesPerSecond.toInt().takeIf { it > 0 } ?: FallbackHz
}
