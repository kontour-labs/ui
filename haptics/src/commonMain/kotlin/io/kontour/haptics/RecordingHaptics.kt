package io.kontour.haptics

import kotlin.time.Duration

/**
 * A [Haptics] that plays nothing and writes down everything it was asked to do,
 * in order, for a test to read back.
 *
 * Rumbles are recorded with their intensity and sharpness clamped to 0..1, as
 * they would play; effects are recorded as they were passed, so a test can
 * compare them with the ones it expected. Rumble leases are not timed — a test
 * that wants a rumble stopped stops it.
 *
 * @property capability What it reports, a rich device with a continuous rumble
 *   unless told otherwise.
 */
class RecordingHaptics(
    override val capability: HapticCapability = HapticCapability(
        level = HapticCapability.Level.Rich,
        honoursStrength = true,
        rumble = HapticCapability.RumbleSupport.Continuous,
        details = listOf("recording"),
    ),
) : Haptics {

    private val recorded = mutableListOf<HapticRecord>()
    private var nextRumble = 0
    private var closed = false

    /** Everything asked of it so far, oldest first. */
    val records: List<HapticRecord> get() = recorded.toList()

    /** Forgets [records]. */
    fun clear() = recorded.clear()

    override fun play(effect: HapticEffect) {
        if (!closed) recorded += HapticRecord.Played(effect)
    }

    override fun play(pattern: HapticPattern) {
        if (!closed) recorded += HapticRecord.PatternPlayed(pattern)
    }

    override fun startRumble(intensity: Float, sharpness: Float, timeout: Duration): Rumble {
        if (closed) return StoppedRumble
        val id = nextRumble++
        recorded += HapticRecord.RumbleStarted(id, unit(intensity), unit(sharpness))
        return object : Rumble {
            override var isActive: Boolean = true
                private set

            override fun update(intensity: Float, sharpness: Float) {
                if (isActive) recorded += HapticRecord.RumbleUpdated(id, unit(intensity), unit(sharpness))
            }

            override fun stop() {
                if (!isActive) return
                isActive = false
                recorded += HapticRecord.RumbleStopped(id)
            }
        }
    }

    override fun cancel() {
        if (!closed) recorded += HapticRecord.Cancelled
    }

    override fun close() {
        closed = true
    }
}

/** One thing a [RecordingHaptics] was asked to do. */
sealed interface HapticRecord {
    /** [Haptics.play] with an effect. */
    data class Played(val effect: HapticEffect) : HapticRecord

    /** [Haptics.play] with a pattern. */
    data class PatternPlayed(val pattern: HapticPattern) : HapticRecord

    /** [Haptics.startRumble]; [id] tells one rumble's records from another's. */
    data class RumbleStarted(val id: Int, val intensity: Float, val sharpness: Float) : HapticRecord

    /** [Rumble.update] on the rumble [id]. */
    data class RumbleUpdated(val id: Int, val intensity: Float, val sharpness: Float) : HapticRecord

    /** [Rumble.stop] on the rumble [id] — once, however many times it was called. */
    data class RumbleStopped(val id: Int) : HapticRecord

    /** [Haptics.cancel]. */
    data object Cancelled : HapticRecord
}
