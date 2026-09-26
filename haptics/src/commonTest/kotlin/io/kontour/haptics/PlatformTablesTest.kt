package io.kontour.haptics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/**
 * What each platform plays for each effect. The tables are pure, so all of them
 * are held here to what the platforms accept — every Android constant exists on
 * the release it is asked for on, every scale and intensity is within 0..1, every
 * browser array starts with a vibration a hand can feel — and to the few shapes
 * that are decisions rather than numbers.
 */
class PlatformTablesTest {

    /** One of every effect, at the given strength. */
    private fun everyEffect(strength: Float = 1f): List<HapticEffect> = listOf(
        HapticEffect.Tick(strength), HapticEffect.LowTick(strength), HapticEffect.Click(strength),
        HapticEffect.Thud(strength), HapticEffect.Spin(strength), HapticEffect.QuickRise(strength),
        HapticEffect.SlowRise(strength), HapticEffect.QuickFall(strength),
        HapticEffect.Selection(fine = false, strength), HapticEffect.Selection(fine = true, strength),
        HapticEffect.LongPress(strength), HapticEffect.KeyPress(strength),
        HapticEffect.Toggle(on = true, strength), HapticEffect.Toggle(on = false, strength),
        HapticEffect.Threshold(activate = true, strength), HapticEffect.Threshold(activate = false, strength),
    ) + ImpactStyle.entries.map { HapticEffect.Impact(it, strength) } +
        NotificationType.entries.map { HapticEffect.Notification(it, strength) }

    private val strengths = listOf(0.1f, 0.3f, 0.5f, 0.7f, 1f, 5f)

    // --- Android ------------------------------------------------------------

    @Test
    fun everyAndroidScaleIsOneAndroidAccepts() {
        for (s in strengths) for (effect in everyEffect(s)) {
            for (p in androidPrimitivePlan(effect)) {
                assertTrue(p.scale in 0f..1f, "$effect plans ${p.kind} at ${p.scale}")
                assertTrue(p.startMillis >= 0, "$effect starts ${p.kind} at ${p.startMillis}")
            }
        }
    }

    @Test
    fun noConstantIsAskedForOnAReleaseWithoutIt() {
        for (sdk in 29..37) for (s in strengths) for (effect in everyEffect(s)) {
            val constant = androidConstant(effect, sdk)
            assertTrue(constant.sinceApi <= sdk, "$effect on API $sdk asks for $constant, from API ${constant.sinceApi}")
        }
    }

    /** Not `TEXT_HANDLE_MOVE`, which a phone maker can switch off. */
    @Test
    fun theLightestConstantIsOneNobodyCanSwitchOff() {
        assertEquals(AndroidConstant.ClockTick, androidConstant(HapticEffect.Selection(fine = true), 33))
        assertEquals(AndroidConstant.SegmentFrequentTick, androidConstant(HapticEffect.Selection(fine = true), 34))
    }

    @Test
    fun aSofterPhysicalEffectStepsDownTheConstants() {
        assertEquals(AndroidConstant.LongPress, androidConstant(HapticEffect.Thud(1f), 34))
        assertEquals(AndroidConstant.VirtualKey, androidConstant(HapticEffect.Thud(0.5f), 34))
        assertEquals(AndroidConstant.SegmentTick, androidConstant(HapticEffect.Thud(0.2f), 34))
        assertEquals(AndroidConstant.SegmentFrequentTick, androidConstant(HapticEffect.Tick(0.2f), 34))
    }

    @Test
    fun theMeaningfulConstantsIgnoreStrength() {
        for (s in strengths) {
            assertEquals(AndroidConstant.ToggleOn, androidConstant(HapticEffect.Toggle(true, s), 34))
            assertEquals(AndroidConstant.ToggleOff, androidConstant(HapticEffect.Toggle(false, s), 34))
            assertEquals(AndroidConstant.GestureThresholdActivate, androidConstant(HapticEffect.Threshold(true, s), 34))
            assertEquals(AndroidConstant.GestureThresholdDeactivate, androidConstant(HapticEffect.Threshold(false, s), 34))
            assertEquals(AndroidConstant.Confirm, androidConstant(HapticEffect.Notification(NotificationType.Success, s), 30))
            assertEquals(AndroidConstant.Reject, androidConstant(HapticEffect.Notification(NotificationType.Error, s), 30))
            assertEquals(AndroidConstant.KeyboardTap, androidConstant(HapticEffect.KeyPress(s), 29))
        }
    }

    /** A tier-1 phone has click and tick; whatever else it lacks lands on one of them. */
    @Test
    fun whatAPhoneLacksLandsOnWhatItHas() {
        val bare = setOf(PrimitiveKind.Click, PrimitiveKind.Tick)
        for (kind in PrimitiveKind.entries) {
            val played = substitute(listOf(PlannedPrimitive(kind, 1f, 0)), bare)
            assertTrue(played.isNotEmpty(), "$kind played nothing on a phone with click and tick")
            assertTrue(played.all { it.kind in bare }, "$kind became $played")
        }
        assertEquals(
            listOf(PlannedPrimitive(PrimitiveKind.Tick, 0.4f, 0)),
            substitute(listOf(PlannedPrimitive(PrimitiveKind.LowTick, 0.8f, 0)), bare),
            "a low tick becomes a tick at half the scale",
        )
        val spin = substitute(listOf(PlannedPrimitive(PrimitiveKind.Spin, 1f, 0)), PrimitiveKind.entries.toSet() - PrimitiveKind.Spin)
        assertEquals(listOf(PrimitiveKind.QuickRise, PrimitiveKind.QuickFall), spin.map { it.kind })
    }

    /** From Android 16 a delay is from the previous start; before it, a pause after the previous end. */
    @Test
    fun compositionDelaysAreTheReleasesOwnKind() {
        val plan = listOf(
            PlannedPrimitive(PrimitiveKind.Tick, 1f, 0),
            PlannedPrimitive(PrimitiveKind.Click, 1f, 70),
            PlannedPrimitive(PrimitiveKind.Click, 1f, 80),
        )
        val durations = mapOf(PrimitiveKind.Tick to 10, PrimitiveKind.Click to 20)
        assertEquals(listOf(0, 70, 10), compositionDelays(plan, relativeToStart = true) { durations.getValue(it) }.toList())
        // Before: 70 - (0 + 10) = 60; then the second click would start inside
        // the first, so it waits for it instead of overlapping.
        assertEquals(listOf(0, 60, 0), compositionDelays(plan, relativeToStart = false) { durations.getValue(it) }.toList())
    }

    @Test
    fun theRichestAndroidRouteWins() {
        assertEquals(AndroidTier.Primitives, chooseAndroidTier(31, hasVibrator = true, permitted = true, clickAndTick = true, hasView = true))
        assertEquals(AndroidTier.ViewConstants, chooseAndroidTier(29, hasVibrator = true, permitted = true, clickAndTick = true, hasView = true), "no primitives before Android 11")
        assertEquals(AndroidTier.ViewConstants, chooseAndroidTier(34, hasVibrator = true, permitted = false, clickAndTick = true, hasView = true), "no permission")
        assertEquals(AndroidTier.ViewConstants, chooseAndroidTier(34, hasVibrator = true, permitted = true, clickAndTick = false, hasView = true), "no primitives on this actuator")
        assertEquals(AndroidTier.Predefined, chooseAndroidTier(34, hasVibrator = true, permitted = true, clickAndTick = false, hasView = false))
        assertEquals(AndroidTier.None, chooseAndroidTier(34, hasVibrator = false, permitted = true, clickAndTick = false, hasView = false))
    }

    @Test
    fun aRouteAskedForAndNotThereIsSilentNotSubstituted() {
        assertEquals(AndroidTier.ViewConstants, chooseAndroidTier(34, true, true, true, true, requested = AndroidTier.ViewConstants))
        assertEquals(AndroidTier.None, chooseAndroidTier(29, true, true, true, true, requested = AndroidTier.Primitives))
        assertEquals(AndroidRumble.None, chooseAndroidRumble(34, AndroidTier.Primitives, envelopes = false, amplitude = true, requested = AndroidRumble.Envelope))
    }

    @Test
    fun theRumbleWithASharpnessComesFirst() {
        assertEquals(AndroidRumble.Envelope, chooseAndroidRumble(36, AndroidTier.Primitives, envelopes = true, amplitude = true))
        assertEquals(AndroidRumble.Amplitude, chooseAndroidRumble(35, AndroidTier.Primitives, envelopes = true, amplitude = true), "envelopes are Android 16")
        assertEquals(AndroidRumble.TickPulses, chooseAndroidRumble(34, AndroidTier.Primitives, envelopes = false, amplitude = false))
        assertEquals(AndroidRumble.ConstantPulses, chooseAndroidRumble(34, AndroidTier.ViewConstants, envelopes = true, amplitude = true))
        assertEquals(AndroidRumble.None, chooseAndroidRumble(34, AndroidTier.None, envelopes = true, amplitude = true))
    }

    @Test
    fun rumbleAmplitudeAndSpacingStayInRange() {
        assertEquals(1, androidAmplitude(0f))
        assertEquals(255, androidAmplitude(1f))
        assertEquals(255, androidAmplitude(9f))
        assertTrue(androidPulseMillis(1f, constants = true) < androidPulseMillis(0f, constants = true), "stronger is closer together")
    }

    // --- iOS ----------------------------------------------------------------

    @Test
    fun everyAppleValueIsInRange() {
        for (s in strengths) for (effect in everyEffect(s)) {
            when (val plan = applePlan(effect)) {
                is ApplePlan.Impact -> assertTrue(plan.intensity in 0f..1f, "$effect at ${plan.intensity}")
                is ApplePlan.Events -> plan.events.forEach {
                    assertTrue(it.intensity in 0f..1f && it.sharpness in 0f..1f, "$effect has $it")
                }
                else -> Unit
            }
        }
    }

    /** UIKit's own for everything it has a word for; Core Haptics only for the swells. */
    @Test
    fun iosPlaysApplesOwnFeedbackWhereThereIsOne() {
        assertEquals(ApplePlan.Selection, applePlan(HapticEffect.Selection(fine = true)))
        assertEquals(ApplePlan.Notification(NotificationType.Warning), applePlan(HapticEffect.Notification(NotificationType.Warning)))
        assertEquals(ApplePlan.Impact(ImpactStyle.Soft, 0.5f), applePlan(HapticEffect.Impact(ImpactStyle.Soft, 0.5f)))
        for (swell in listOf(HapticEffect.Spin(), HapticEffect.QuickRise(), HapticEffect.SlowRise(), HapticEffect.QuickFall())) {
            assertTrue(applePlan(swell) is ApplePlan.Events, "$swell is not Core Haptics")
        }
    }

    @Test
    fun aPatternOnIosKeepsItsTiming() {
        val pattern = hapticPattern {
            at(0.milliseconds, HapticEffect.Tick())
            at(90.milliseconds, HapticEffect.Click())
        }
        assertEquals(listOf(0.0, 0.09), appleEvents(pattern).map { it.startSeconds })
    }

    @Test
    fun theRumblesSharpnessIsAddedToAHalf() {
        val (intensity, sharpness) = appleRumbleControls(0.4f, 0.2f)
        assertEquals(0.4f, intensity)
        assertEquals(-0.3f, sharpness, 0.0001f)
        assertEquals(1f, appleRumbleControls(3f, 0f).first)
    }

    // --- the web --------------------------------------------------------------

    @Test
    fun everyBrowserArrayStartsWithAVibrationAHandFeels() {
        for (s in strengths) for (effect in everyEffect(s)) {
            val array = webPattern(effect)
            assertTrue(array.isNotEmpty() && array.size <= WebMostEntries, "$effect is ${array.toList()}")
            array.forEachIndexed { i, ms ->
                if (i % 2 == 0) assertTrue(ms >= WebShortestMillis, "$effect vibrates for $ms ms at entry $i")
            }
        }
    }

    @Test
    fun aStrongerEffectIsNoShorterInABrowser() {
        for ((weak, strong) in everyEffect(0.2f).zip(everyEffect(1f))) {
            assertTrue(webPattern(strong).sum() >= webPattern(weak).sum(), "$strong is shorter than $weak")
        }
    }

    @Test
    fun aBrowserPatternWaitsForItsFirstEventAndIsCutAtTheNext() {
        val pattern = hapticPattern {
            at(30.milliseconds, HapticEffect.Thud())
            at(40.milliseconds, HapticEffect.Tick())
        }
        val array = webPattern(pattern).toList()
        assertEquals(listOf(0, 30), array.take(2), "a pattern that does not start at once starts with the wait")
        assertEquals(10, array[2], "the thud is cut where the tick starts")
        assertEquals(0, array[3])
    }

    @Test
    fun aBrowserRumbleIsAFaintDutyCycle() {
        for (i in listOf(0f, 0.5f, 1f)) {
            val array = webRumble(i, 3000)
            assertTrue(array.size <= WebMostEntries && array.size % 2 == 0)
            assertTrue(array[0] >= WebShortestMillis && array[0] < 70, "on for ${array[0]} of 70 ms")
            assertEquals(70, array[0] + array[1])
        }
        assertTrue(webRumble(1f, 3000)[0] > webRumble(0f, 3000)[0])
    }

    // --- a Mac's trackpad ------------------------------------------------------

    @Test
    fun everyEffectTapsTheTrackpad() {
        for (effect in everyEffect()) assertTrue(macPlan(effect).isNotEmpty(), "$effect is silent on a trackpad")
        assertEquals(2, macPlan(HapticEffect.Notification(NotificationType.Success)).size, "success is two taps")
        assertTrue(macPulseMillis(1f) < macPulseMillis(0f))
    }
}
