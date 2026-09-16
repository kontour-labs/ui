package io.kontour.ui.platform

import androidx.compose.ui.hapticfeedback.HapticFeedbackType

/**
 * `UISelectionFeedbackGenerator.selectionChanged()` — the tick under the
 * system's own pickers, and the lightest feedback the device has. There is no
 * motor spin-up to clear here, so the web's floor does not apply, and an
 * *impact* per detent on a wheel spun with a thumb is the punchiness this
 * answers.
 */
internal actual val platformTickHaptic: HapticFeedbackType = HapticFeedbackType.SegmentTick


/**
 * The same generator the detent tick uses, and for the same reason: there is
 * nothing lighter on the device than `selectionChanged()`, and a tap is exactly
 * the case it was built for.
 */
internal actual val platformTapHaptic: HapticFeedbackType = HapticFeedbackType.SegmentTick
