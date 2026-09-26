package io.kontour.ui.interaction

import io.kontour.haptics.HapticEffect
import io.kontour.haptics.ImpactStyle
import io.kontour.haptics.NotificationType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The effect each intent plays: the one table, written out in full, so changing a
 * row is an edit somebody makes on purpose — and the few shapes in it that are
 * decisions rather than strengths.
 */
class FeedbackEffectsTest {

    @Test
    fun theTableIsWrittenDown() {
        assertEquals(
            mapOf(
                FeedbackIntent.Selection to HapticEffect.Selection(fine = false),
                FeedbackIntent.Tap to HapticEffect.Impact(ImpactStyle.Light),
                FeedbackIntent.Tick to HapticEffect.Selection(fine = true),
                FeedbackIntent.Snap to HapticEffect.Selection(fine = false),
                FeedbackIntent.ToggleOn to HapticEffect.Toggle(on = true),
                FeedbackIntent.ToggleOff to HapticEffect.Toggle(on = false),
                FeedbackIntent.Confirm to HapticEffect.Notification(NotificationType.Success),
                FeedbackIntent.Reject to HapticEffect.Notification(NotificationType.Error),
                FeedbackIntent.Warn to HapticEffect.Notification(NotificationType.Warning),
                FeedbackIntent.LongPress to HapticEffect.LongPress(),
                FeedbackIntent.DragThreshold to HapticEffect.Threshold(activate = true),
                FeedbackIntent.DragThresholdBack to HapticEffect.Threshold(activate = false),
                FeedbackIntent.Limit to HapticEffect.Thud(strength = 0.6f),
                FeedbackIntent.Hold to HapticEffect.Selection(fine = true, strength = 0.4f),
                FeedbackIntent.GestureEnd to HapticEffect.Impact(ImpactStyle.Soft, strength = 0.6f),
                FeedbackIntent.KeyPress to HapticEffect.KeyPress(),
            ),
            FeedbackIntent.entries.associateWith { it.defaultEffect },
        )
    }

    /**
     * A warning and a refusal were one constant, and a consumer retuning the
     * mapping could not pull them apart. They are two now, and the platforms that
     * have a warning of their own play it.
     */
    @Test
    fun aWarningIsNotARefusal() {
        assertTrue(FeedbackIntent.Warn.defaultEffect != FeedbackIntent.Reject.defaultEffect)
    }

    /** Only a hold has a rumble; it starts faint, builds, and never gets past a half. */
    @Test
    fun onlyAHoldRumblesAndItStaysFaint() {
        for (intent in FeedbackIntent.entries - FeedbackIntent.Hold) assertNull(intent.defaultRumble, "$intent rumbles")
        val rumble = FeedbackIntent.Hold.defaultRumble!!
        assertTrue(rumble.intensityAt(0f) < rumble.intensityAt(0.5f))
        assertTrue(rumble.intensityAt(0.5f) < rumble.intensityAt(1f))
        assertTrue(rumble.intensityAt(1f) <= 0.5f)
        assertEquals(rumble.intensityAt(1f), rumble.intensityAt(7f), "progress past the end is the end")
    }
}
