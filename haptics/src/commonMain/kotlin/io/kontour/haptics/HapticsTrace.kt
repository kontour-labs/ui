package io.kontour.haptics

import kotlin.time.Duration

/**
 * What a player has done, counted: for a diagnostics page, when a haptic that
 * should have been felt was not.
 *
 * Haptics fail silently by design — no platform reports whether a motor moved —
 * so when they stop, the only question that can be answered is *how far down a
 * request got*. A trace answers it with a counter per step, named by the layer
 * that took it (`play Tick`, `impact Heavy`, `refused: closed`, `engine start
 * failed`), and the last few notable events as lines. Read the same counters
 * before and after the haptics go quiet: the first one that stops rising is
 * where they stopped.
 *
 * Call it from the main thread, as the players are. It costs a map lookup per
 * haptic, which is nothing next to the haptic.
 */
class HapticsTrace {

    private val counts = linkedMapOf<String, Int>()
    private val lines = ArrayDeque<String>()

    /** Every counter so far, in the order each was first counted. */
    val counters: Map<String, Int> get() = LinkedHashMap(counts)

    /** The last [LogLength] notable events, oldest first. */
    val log: List<String> get() = lines.toList()

    /** Counts one [step]. */
    fun count(step: String) {
        counts[step] = (counts[step] ?: 0) + 1
    }

    /** Writes a notable event — an error, a reset — to [log], and counts it under [step] if given. */
    fun note(message: String, step: String? = null) {
        if (step != null) count(step)
        if (lines.size == LogLength) lines.removeFirst()
        lines.addLast(message)
    }

    /** Forgets everything. */
    fun clear() {
        counts.clear()
        lines.clear()
    }

    /** The counters and the log as plain text, to copy into a report. */
    fun describe(): String = buildString {
        counts.forEach { (step, n) -> appendLine("$step: $n") }
        if (lines.isNotEmpty()) {
            appendLine("---")
            lines.forEach { appendLine(it) }
        }
    }

    companion object {
        /** How many lines [log] keeps. */
        const val LogLength: Int = 50
    }
}

/**
 * A [Haptics] that counts every call into [trace] and passes it on to [delegate].
 *
 * The layer between the design system and the platform: wrap the platform's
 * player in one, provide it where the theme will find it, and every haptic the
 * app plays is counted by effect before the platform sees it. The platform's own
 * trace, where it keeps one, is [platformTrace].
 *
 * @property trace This layer's counts: what the app asked the player for. Pass
 *   one to share it between two wrappers — the app's player, and one pinned to a
 *   route for a comparison.
 */
class TracingHaptics(
    private val delegate: Haptics,
    override val trace: HapticsTrace = HapticsTrace(),
) : Haptics {

    /** What the platform's player counted underneath, if it keeps a trace. */
    val platformTrace: HapticsTrace? get() = delegate.trace

    override val capability: HapticCapability get() = delegate.capability

    override fun play(effect: HapticEffect) {
        trace.count("play ${effect.kindName}")
        delegate.play(effect)
    }

    override fun play(pattern: HapticPattern) {
        trace.count("play pattern")
        delegate.play(pattern)
    }

    override fun startRumble(intensity: Float, sharpness: Float, timeout: Duration): Rumble {
        trace.count("rumble start")
        return delegate.startRumble(intensity, sharpness, timeout)
    }

    override fun cancel() {
        trace.count("cancel")
        delegate.cancel()
    }

    override fun close() {
        trace.note("closed", step = "close")
        delegate.close()
    }

    override fun toString(): String = "TracingHaptics($delegate)"
}

/** An effect's name for a trace: its class, which is its kind. */
internal val HapticEffect.kindName: String get() = this::class.simpleName ?: "effect"
