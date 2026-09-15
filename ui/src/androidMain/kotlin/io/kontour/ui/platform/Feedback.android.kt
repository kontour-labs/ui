package io.kontour.ui.platform

import androidx.compose.ui.hapticfeedback.HapticFeedbackType

/**
 * Not [HapticFeedbackType.SegmentTick], which would be the softer answer and is
 * an API-34 constant: on Android 13 and below it does nothing whatever, and a
 * tick that is silent on most of the installed base is worse than one that is
 * heavier than ideal. `VirtualKey` goes back to API 5.
 */
internal actual val platformTickHaptic: HapticFeedbackType = HapticFeedbackType.VirtualKey
