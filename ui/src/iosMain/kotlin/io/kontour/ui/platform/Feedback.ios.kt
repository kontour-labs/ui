package io.kontour.ui.platform

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import io.kontour.ui.interaction.FeedbackFeel

/**
 * Three generators, three weights, and no motor floor to clear.
 *
 * `UISelectionFeedbackGenerator.selectionChanged()` — the tick under the system's
 * own pickers — is the lightest thing the device has, and `SegmentTick` reaches it.
 * Above it `CupertinoHapticFeedback` routes `GestureThresholdActivate` to a *light*
 * impact and `LongPress` to a *medium* one, so the scale is real rather than
 * asserted.
 *
 * The one behaviour change on this platform: a press was `selectionChanged()` too,
 * because the intent that meant "a control answered" claimed to be the lightest
 * thing available and there was nothing in the vocabulary to say otherwise. A
 * checkbox and a row of a drum felt identical here, which is the same defect as the
 * Android one arriving from the other direction.
 */
internal actual fun platformHapticFor(feel: FeedbackFeel): HapticFeedbackType =
    when (feel) {
        FeedbackFeel.Light -> HapticFeedbackType.SegmentTick
        FeedbackFeel.Medium -> HapticFeedbackType.GestureThresholdActivate
        FeedbackFeel.Heavy -> HapticFeedbackType.LongPress
        FeedbackFeel.Success -> HapticFeedbackType.Confirm
        FeedbackFeel.Danger -> HapticFeedbackType.Reject
    }
