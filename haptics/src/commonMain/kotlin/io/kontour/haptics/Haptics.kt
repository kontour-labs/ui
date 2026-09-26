package io.kontour.haptics

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Plays haptics on this device.
 *
 * Made per platform, because what it needs differs: Android's takes a `Context`
 * (and a `View`, for the system's own feedback constants), and iOS, the JVM and
 * the web take nothing. Each of those is a function called `Haptics`, next to
 * this interface. [None] plays nothing anywhere, and [RecordingHaptics] plays
 * nothing and writes down what it was asked, for tests.
 *
 * **Call it from the main thread.** On iOS the feedback generators are
 * main-thread objects and a call from elsewhere is moved there; on Android the
 * system's feedback constants go through a `View` and are posted to it. Nothing
 * here waits.
 *
 * **Nothing here limits the rate.** A haptic a frame is a buzz on every platform —
 * each new one cuts off the last — and deciding which of a stream to drop is a
 * question of what the stream *is*, which only the caller knows. `:ui`'s
 * detents drop all but one every 80ms.
 */
interface Haptics : AutoCloseable {

    /** What this device can play, and how. */
    val capability: HapticCapability

    /** Plays one effect, now. */
    fun play(effect: HapticEffect)

    /**
     * Plays a timeline of primitives as **one** pattern where the platform has
     * such a thing — an Android composition, a Core Haptics pattern, a single
     * `navigator.vibrate` array — so its timing is the platform's rather than a
     * timer's.
     */
    fun play(pattern: HapticPattern)

    /**
     * Starts a continuous rumble, and returns the handle that changes and stops it.
     *
     * [intensity] and [sharpness] are 0 to 1. Sharpness is how buzzy rather than
     * dull it is, where a platform can say so: Core Haptics, and Android's
     * envelopes from Android 16. Elsewhere a rumble is a rapid run of the
     * lightest tick, which is as close as the hardware comes.
     *
     * **[timeout] is a lease.** Every [Rumble.update] renews it, and one that
     * runs out stops the rumble, so a caller that forgets it — an exception
     * between start and stop, a screen torn down mid-hold — leaves a phone
     * buzzing for at most that long rather than until the battery goes.
     */
    fun startRumble(
        intensity: Float = 0.3f,
        sharpness: Float = 0.3f,
        timeout: Duration = 10.seconds,
    ): Rumble

    /** Stops anything this is playing: rumbles, and the rest of a pattern. */
    fun cancel()

    /**
     * Stops everything and lets go of the platform's resources. Closing twice is
     * fine, and playing after closing does nothing.
     */
    override fun close()

    companion object {
        /** Plays nothing, reports [HapticCapability.None], and never fails. */
        val None: Haptics = NoHaptics
    }
}

/**
 * A continuous haptic, started by [Haptics.startRumble].
 *
 * Closing it stops it, so `use { }` works for a rumble that lives exactly as long
 * as a block.
 */
interface Rumble : AutoCloseable {

    /** Whether it is still playing — false once stopped, or once its lease ran out. */
    val isActive: Boolean

    /**
     * Changes how strong and how sharp it is, 0 to 1 each, and renews its lease.
     * Calling this every frame is fine; the platforms that have to re-issue a
     * vibration to change it do so at most a few times a second.
     */
    fun update(intensity: Float, sharpness: Float)

    /** Stops it. Stopping twice is fine. */
    fun stop()

    override fun close() = stop()
}

private object NoHaptics : Haptics {
    override val capability: HapticCapability get() = HapticCapability.None
    override fun play(effect: HapticEffect) = Unit
    override fun play(pattern: HapticPattern) = Unit
    override fun startRumble(intensity: Float, sharpness: Float, timeout: Duration): Rumble = StoppedRumble
    override fun cancel() = Unit
    override fun close() = Unit
    override fun toString(): String = "Haptics.None"
}

/** A rumble that was never going to play: what a device without one hands back. */
internal object StoppedRumble : Rumble {
    override val isActive: Boolean get() = false
    override fun update(intensity: Float, sharpness: Float) = Unit
    override fun stop() = Unit
}
