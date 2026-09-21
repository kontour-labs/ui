package io.kontour.ui.platform

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import io.kontour.ui.interaction.FeedbackFeel

/**
 * Two felt weights, and saying so is the honest answer.
 *
 * `navigator.vibrate` gives single pulses of 6ms, 12ms, 20ms and 30ms. 6ms is below
 * what a motor can spin up to be felt at all — the measurement that moved the
 * detent tick onto 20ms in the first place — and **12ms has never been measured
 * either way**. So [FeedbackFeel.Light] and [FeedbackFeel.Medium] are both the 20ms
 * pulse rather than one of them being a 12ms guess that might reintroduce the bug
 * on the component that fires most often.
 *
 * `docs/measure-web.mjs --vibration` is what would settle whether 12ms is felt. If
 * it is, this is a one-line change and the web gets the third tier the other two
 * platforms already have.
 */
internal actual fun platformHapticFor(feel: FeedbackFeel): HapticFeedbackType =
    when (feel) {
        FeedbackFeel.Light -> HapticFeedbackType.VirtualKey
        FeedbackFeel.Medium -> HapticFeedbackType.VirtualKey
        FeedbackFeel.Heavy -> HapticFeedbackType.LongPress
        FeedbackFeel.Success -> HapticFeedbackType.Confirm
        FeedbackFeel.Danger -> HapticFeedbackType.Reject
    }
