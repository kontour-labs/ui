package io.kontour.haptics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Strength as it is played, and patterns as they are built. */
class HapticEffectTest {

    @Test
    fun strengthIsClampedWhenPlayedNotWhenBuilt() {
        assertEquals(0f, HapticEffect.Tick(Float.NaN).level)
        assertEquals(0f, HapticEffect.Tick(-3f).level)
        assertEquals(1f, HapticEffect.Tick(7f).level)
        assertEquals(0.4f, HapticEffect.Tick(0.4f).level)
        assertEquals(7f, HapticEffect.Tick(7f).strength, "the effect keeps what it was given")
    }

    @Test
    fun aStrengthOfNearlyNothingPlaysNothing() {
        assertFalse(HapticEffect.Click(0f).audible)
        assertFalse(HapticEffect.Click(0.001f).audible)
        assertFalse(HapticEffect.Click(Float.NaN).audible)
        assertTrue(HapticEffect.Click(0.002f).audible)
    }

    /** Scaling changes the strength and nothing else, for every kind of effect. */
    @Test
    fun scalingKeepsTheEffectAndMultipliesItsStrength() {
        val every = listOf(
            HapticEffect.Tick(0.8f), HapticEffect.LowTick(0.8f), HapticEffect.Click(0.8f), HapticEffect.Thud(0.8f),
            HapticEffect.Spin(0.8f), HapticEffect.QuickRise(0.8f), HapticEffect.SlowRise(0.8f), HapticEffect.QuickFall(0.8f),
            HapticEffect.Selection(fine = true, strength = 0.8f), HapticEffect.Impact(ImpactStyle.Soft, 0.8f),
            HapticEffect.Notification(NotificationType.Warning, 0.8f), HapticEffect.Toggle(on = false, strength = 0.8f),
            HapticEffect.Threshold(activate = false, strength = 0.8f), HapticEffect.LongPress(0.8f), HapticEffect.KeyPress(0.8f),
        )
        for (effect in every) {
            val half = effect.scaled(0.5f)
            assertEquals(effect::class, half::class)
            assertEquals(0.4f, half.strength, 1e-6f, "$effect")
        }
        assertEquals(HapticEffect.Selection(fine = true, strength = 0.4f), HapticEffect.Selection(fine = true, strength = 0.8f).scaled(0.5f))
        assertEquals(1f, HapticEffect.Tick(1f).scaled(3f).level, "clamped when played, like any strength")
    }

    @Test
    fun aPatternIsInTheOrderItsEventsStart() {
        val pattern = hapticPattern {
            at(40.milliseconds, HapticEffect.Click())
            at(0.milliseconds, HapticEffect.Tick())
        }
        assertEquals(listOf(0.milliseconds, 40.milliseconds), pattern.events.map { it.at })
    }

    /** `after` is a gap after the previous one *ends*, which is what Android means by a delay. */
    @Test
    fun afterIsAGapFromTheEndOfThePreviousOne() {
        val pattern = hapticPattern {
            after(0.milliseconds, HapticEffect.Tick())
            after(60.milliseconds, HapticEffect.Click())
        }
        assertEquals(0.milliseconds, pattern.events[0].at)
        assertEquals((PrimitiveKind.Tick.nominalMillis + 60).milliseconds, pattern.events[1].at)
        assertEquals((PrimitiveKind.Tick.nominalMillis + 60 + PrimitiveKind.Click.nominalMillis).milliseconds, pattern.duration)
    }

    @Test
    fun aPatternRefusesWhatNoPlatformCouldPlay() {
        assertFailsWith<IllegalArgumentException> { hapticPattern { at((-1).milliseconds, HapticEffect.Tick()) } }
        assertFailsWith<IllegalArgumentException> { hapticPattern { at(6.seconds, HapticEffect.Tick()) } }
        assertFailsWith<IllegalArgumentException> {
            hapticPattern { repeat(HapticPattern.MaxEvents + 1) { at(it.milliseconds, HapticEffect.Tick()) } }
        }
    }

    @Test
    fun patternsCompareByTheirEvents() {
        val a = hapticPattern { at(0.milliseconds, HapticEffect.Tick(0.5f)) }
        val b = hapticPattern { at(0.milliseconds, HapticEffect.Tick(0.5f)) }
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun nothingPlaysNothingAndSaysSo() {
        val none = Haptics.None
        none.play(HapticEffect.Click())
        val rumble = none.startRumble()
        assertFalse(rumble.isActive)
        assertEquals(HapticCapability.Level.None, none.capability.level)
        none.close()
        none.close()
    }

    @Test
    fun aRecorderWritesDownWhatItWasAskedInOrder() {
        val haptics = RecordingHaptics()
        val pattern = hapticPattern { at(0.milliseconds, HapticEffect.Tick()) }
        haptics.play(HapticEffect.Click(0.5f))
        haptics.play(pattern)
        val rumble = haptics.startRumble(intensity = 2f, sharpness = -1f)
        rumble.update(0.4f, 0.2f)
        rumble.stop()
        rumble.stop()
        rumble.update(0.9f, 0.9f)
        haptics.cancel()
        assertEquals(
            listOf(
                HapticRecord.Played(HapticEffect.Click(0.5f)),
                HapticRecord.PatternPlayed(pattern),
                HapticRecord.RumbleStarted(0, 1f, 0f),
                HapticRecord.RumbleUpdated(0, 0.4f, 0.2f),
                HapticRecord.RumbleStopped(0),
                HapticRecord.Cancelled,
            ),
            haptics.records,
        )
        haptics.close()
        haptics.play(HapticEffect.Click())
        assertEquals(6, haptics.records.size, "played after closing")
    }
}
