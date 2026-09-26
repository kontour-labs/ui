package io.kontour.ui.interaction

import io.kontour.haptics.HapticEffect
import io.kontour.haptics.ImpactStyle
import io.kontour.haptics.NotificationType

/**
 * The haptic the theme's dispatcher plays for this intent. The one table.
 *
 * Effects from the `:haptics` module, which plays each through the platform's own
 * tuned feedback: Android's composition primitives on a phone that has them and
 * its feedback constants on one that does not, UIKit's generators on an iPhone,
 * the Vibration API in a browser, and a Mac's trackpad. What each effect is on
 * each platform is that module's table; which effect each interaction gets is
 * this one.
 *
 * Public for the same reason [feel] is: a dispatcher of your own that wants to
 * change one row should not have to re-derive the other eleven.
 *
 * Every strength here is a starting point, set by feel on phones and to be tuned
 * the same way — the catalog's haptics page plays each one.
 */
val FeedbackIntent.defaultEffect: HapticEffect
    get() = when (this) {
        // The texture of a detent going past: the lightest selection tick, which
        // Android plays as its frequent-segment tick and iOS as its picker's.
        FeedbackIntent.Tick -> HapticEffect.Selection(fine = true)
        // A resting place passed: the coarse selection step, a notch firmer.
        FeedbackIntent.Snap -> HapticEffect.Selection(fine = false)
        // A control answering a press — a light impact, a step above a detent.
        FeedbackIntent.Tap -> HapticEffect.Impact(ImpactStyle.Light)
        // On and off feel different on purpose: a crisp tick on, a low one off.
        FeedbackIntent.ToggleOn -> HapticEffect.Toggle(on = true)
        FeedbackIntent.ToggleOff -> HapticEffect.Toggle(on = false)
        FeedbackIntent.Selection -> HapticEffect.Selection()
        FeedbackIntent.DragThreshold -> HapticEffect.Threshold(activate = true)
        // Backing out of a threshold is the softer half of the pair.
        FeedbackIntent.DragThresholdBack -> HapticEffect.Threshold(activate = false)
        // An end stop: a dull knock, not a click.
        FeedbackIntent.Limit -> HapticEffect.Thud(strength = 0.6f)
        FeedbackIntent.LongPress -> HapticEffect.LongPress()
        FeedbackIntent.Confirm -> HapticEffect.Notification(NotificationType.Success)
        FeedbackIntent.Reject -> HapticEffect.Notification(NotificationType.Error)
        // The platform's own warning, which iOS has and the old mapping — one
        // constant for this and Reject — could not reach.
        FeedbackIntent.Warn -> HapticEffect.Notification(NotificationType.Warning)
        // A hold is a rumble (see [defaultRumble]); this is what a dispatcher
        // that only performs plays at its start.
        FeedbackIntent.Hold -> HapticEffect.Selection(fine = true, strength = 0.4f)
        FeedbackIntent.GestureEnd -> HapticEffect.Impact(ImpactStyle.Soft, strength = 0.6f)
        FeedbackIntent.KeyPress -> HapticEffect.KeyPress()
    }

/** A rumble that builds from [from] to [to] with progress, at [sharpness]. */
internal class RumbleSpec(val from: Float, val to: Float, val sharpness: Float) {
    fun intensityAt(progress: Float): Float = from + (to - from) * progress.coerceIn(0f, 1f)
}

/**
 * The rumble an intent sustains, for the one intent that has one: faint, and
 * building to under half as the hold nears its end, dull rather than buzzy where
 * the platform has a sharpness to say so.
 */
internal val FeedbackIntent.defaultRumble: RumbleSpec?
    get() = if (this == FeedbackIntent.Hold) HoldRumble else null

private val HoldRumble = RumbleSpec(from = 0.15f, to = 0.45f, sharpness = 0.2f)
