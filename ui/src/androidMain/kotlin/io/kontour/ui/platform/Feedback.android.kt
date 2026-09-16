package io.kontour.ui.platform

import android.os.Build
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

/**
 * Not [HapticFeedbackType.SegmentTick], which would be the softer answer and is
 * an API-34 constant: on Android 13 and below it does nothing whatever, and a
 * tick that is silent on most of the installed base is worse than one that is
 * heavier than ideal. `VirtualKey` goes back to API 5.
 */
internal actual val platformTickHaptic: HapticFeedbackType = HapticFeedbackType.VirtualKey


/**
 * The soft constant where it exists, and the audible one where it does not.
 *
 * `SegmentTick` is API 34, so on anything older this falls back to the same
 * `VirtualKey` the detent tick uses rather than to silence — a tap that does
 * nothing on most of the installed base is a worse answer than one that is
 * heavier than ideal.
 */
internal actual val platformTapHaptic: HapticFeedbackType =
    if (Build.VERSION.SDK_INT >= 34) HapticFeedbackType.SegmentTick else HapticFeedbackType.VirtualKey
