package io.kontour.haptics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * A trace counts what a player was asked, passes everything on untouched, and
 * keeps a bounded log — the three things a diagnostics page relies on when a
 * phone's haptics go quiet.
 */
class HapticsTraceTest {

    @Test
    fun eachPlayIsCountedByItsKindAndPassedOn() {
        val recording = RecordingHaptics()
        val traced = TracingHaptics(recording)
        traced.play(HapticEffect.Tick())
        traced.play(HapticEffect.Tick(0.5f))
        traced.play(HapticEffect.Thud())
        traced.play(hapticPattern { at(kotlin.time.Duration.ZERO, HapticEffect.Click()) })
        traced.startRumble().stop()
        traced.cancel()

        assertEquals(
            mapOf("play Tick" to 2, "play Thud" to 1, "play pattern" to 1, "rumble start" to 1, "cancel" to 1),
            traced.trace.counters,
        )
        // Everything reached the player, in order and unchanged.
        assertEquals(
            listOf(HapticEffect.Tick(), HapticEffect.Tick(0.5f), HapticEffect.Thud()),
            recording.records.filterIsInstance<HapticRecord.Played>().map { it.effect },
        )
        assertTrue(recording.records.any { it is HapticRecord.PatternPlayed })
        assertTrue(recording.records.last() == HapticRecord.Cancelled)
    }

    @Test
    fun twoWrappersCanShareOneTrace() {
        val shared = HapticsTrace()
        TracingHaptics(RecordingHaptics(), shared).play(HapticEffect.Click())
        TracingHaptics(RecordingHaptics(), shared).play(HapticEffect.Click())
        assertEquals(2, shared.counters["play Click"])
    }

    @Test
    fun thePlatformsTraceIsTheDelegatesOwn() {
        val platform = HapticsTrace()
        val delegate = object : Haptics by RecordingHaptics() {
            override val trace: HapticsTrace get() = platform
        }
        assertSame(platform, TracingHaptics(delegate).platformTrace)
        assertNull(TracingHaptics(RecordingHaptics()).platformTrace)
    }

    @Test
    fun theLogKeepsItsLastLinesAndCountsNotesByStep() {
        val trace = HapticsTrace()
        repeat(HapticsTrace.LogLength + 5) { trace.note("line $it", step = "noted") }
        assertEquals(HapticsTrace.LogLength, trace.log.size)
        assertEquals("line 5", trace.log.first())
        assertEquals("line ${HapticsTrace.LogLength + 4}", trace.log.last())
        assertEquals(HapticsTrace.LogLength + 5, trace.counters["noted"])
    }

    @Test
    fun describeListsCountersThenTheLog() {
        val trace = HapticsTrace()
        trace.count("impact Heavy")
        trace.note("engine reset", step = "engine reset")
        assertEquals("impact Heavy: 1\nengine reset: 1\n---\nengine reset\n", trace.describe())
        trace.clear()
        assertEquals("", trace.describe())
    }
}
