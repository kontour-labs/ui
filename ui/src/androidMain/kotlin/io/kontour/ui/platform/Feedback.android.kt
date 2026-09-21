package io.kontour.ui.platform

import android.os.Build
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import io.kontour.ui.interaction.FeedbackFeel

/**
 * The lightest thing this device has, which is not the constant it used to be.
 *
 * `SegmentTick` is the right name for a detent and is an API-34 constant, so on
 * Android 13 and below it does nothing whatever — the reason both of this file's
 * previous values fell back to `VirtualKey`, and the reason a soft tier appeared
 * not to exist here.
 *
 * It does. `TEXT_HANDLE_MOVE` has existed since API 27, one release **below** this
 * library's `minSdk` of 29, so every device it runs on has it; it resolves to a
 * tick rather than to `EFFECT_CLICK`; and Compose exposes it. Nothing needed
 * `Vibrator`, `VibrationEffect` or the `VIBRATE` manifest permission — which would
 * also have bypassed the reader's own touch-feedback setting, and a UI library must
 * not do that for a tick.
 *
 * Read once rather than per call, which is what the two constants this replaces
 * were for.
 */
private val LightHaptic: HapticFeedbackType =
    if (Build.VERSION.SDK_INT >= 34) {
        HapticFeedbackType.SegmentTick
    } else {
        HapticFeedbackType.TextHandleMove
    }

/**
 * `Confirm` and `Reject` arrived in API 30 and `minSdk` here is 29, so exactly one
 * supported release lacks them. It falls back rather than going silent, at the
 * nearest weight each rhythm is standing in for.
 */
private val Api30 = Build.VERSION.SDK_INT >= 30

/**
 * `VirtualKey` is the **middle** tier, and that is the whole correction.
 *
 * It was never the wrong constant. On Android it is `EFFECT_CLICK` — a full key
 * click, exactly the weight a button press wants — and the measurement that chose
 * it was a web measurement about whether 6ms is felt at all. Putting a stream of
 * detents on it meant a key click per row of a drum, which is the report.
 */
internal actual fun platformHapticFor(feel: FeedbackFeel): HapticFeedbackType =
    when (feel) {
        FeedbackFeel.Light -> LightHaptic
        FeedbackFeel.Medium -> HapticFeedbackType.VirtualKey
        FeedbackFeel.Heavy -> HapticFeedbackType.LongPress
        FeedbackFeel.Success ->
            if (Api30) HapticFeedbackType.Confirm else HapticFeedbackType.VirtualKey
        FeedbackFeel.Danger ->
            if (Api30) HapticFeedbackType.Reject else HapticFeedbackType.LongPress
    }
