package io.kontour.ui.platform

import androidx.compose.ui.hapticfeedback.HapticFeedbackType

/**
 * 20ms of `navigator.vibrate`. The lighter patterns are 12ms and 6ms, and 6ms
 * is below what a motor can spin up to be felt at all — which is the
 * measurement that put the tick here in the first place. Nothing softer than
 * this is *anything* on the web.
 */
internal actual val platformTickHaptic: HapticFeedbackType = HapticFeedbackType.VirtualKey


/**
 * The same 20ms pulse as the tick, because there is nothing between it and
 * nothing. 12ms and 6ms are below the motor's spin-up, so a "lighter" tap on the
 * web is silence rather than a softer tap.
 */
internal actual val platformTapHaptic: HapticFeedbackType = HapticFeedbackType.VirtualKey
