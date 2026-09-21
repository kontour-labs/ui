package io.kontour.ui.platform

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import io.kontour.ui.interaction.FeedbackFeel

/**
 * What was asked for, in order, for the one platform whose wiring can be tested.
 *
 * Academic as a *haptic*: desktop has no motor and the platform handler returns
 * immediately whatever it is handed. That is exactly why it records. Android and
 * iOS have no test source set in this repository — `:ui:compileIosMainKotlinMetadata`
 * type-checks the iOS actual and there is no Android SDK here at all — so this is
 * the only place that can see whether an intent reaches the seam as the feel the
 * policy assigned it.
 *
 * The same bargain `reportedAppearances` struck for `platformReportAppearance`, and
 * for the same reason: an `expect` whose only runnable `actual` does nothing is
 * plumbing that can be cut without any check noticing. A test clears it first.
 */
internal val recordedFeels = mutableListOf<FeedbackFeel>()

/**
 * No motor, so the weights are a formality — but they are the *same* formality the
 * other platforms use rather than a third answer invented for a case nobody can
 * feel.
 */
internal actual fun platformHapticFor(feel: FeedbackFeel): HapticFeedbackType {
    recordedFeels += feel
    return when (feel) {
        FeedbackFeel.Light -> HapticFeedbackType.VirtualKey
        FeedbackFeel.Medium -> HapticFeedbackType.VirtualKey
        FeedbackFeel.Heavy -> HapticFeedbackType.LongPress
        FeedbackFeel.Success -> HapticFeedbackType.Confirm
        FeedbackFeel.Danger -> HapticFeedbackType.Reject
    }
}
