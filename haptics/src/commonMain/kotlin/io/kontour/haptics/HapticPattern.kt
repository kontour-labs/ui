package io.kontour.haptics

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Primitives on a timeline, played as one — see [Haptics.play].
 *
 * Built with [hapticPattern]. Only [HapticEffect.Primitive]s, because they are the
 * only effects every platform can schedule inside a pattern of its own; UIKit's
 * generators and Android's feedback constants are one-shots.
 *
 * @property events In the order they start, which is the order they were added
 *   in once sorted by [Event.at].
 */
class HapticPattern internal constructor(val events: List<Event>) {

    /**
     * One primitive, starting [at] this far into the pattern.
     *
     * @property at When it starts, from the start of the pattern.
     * @property primitive What plays.
     */
    data class Event(val at: Duration, val primitive: HapticEffect.Primitive)

    /** From the start of the first event to the nominal end of the last. */
    val duration: Duration
        get() = events.maxOfOrNull { it.at + it.primitive.nominalDuration } ?: Duration.ZERO

    override fun equals(other: Any?): Boolean = other is HapticPattern && other.events == events
    override fun hashCode(): Int = events.hashCode()
    override fun toString(): String = "HapticPattern($events)"

    companion object {
        /** The most events a pattern holds; the web's vibrate array is the limit that bites first. */
        const val MaxEvents: Int = 64

        /** The latest an event may start. Anything longer is a rumble, and has one. */
        val MaxDuration: Duration = 5.seconds
    }
}

/**
 * Builds a [HapticPattern].
 *
 * ```
 * val success = hapticPattern {
 *     at(0.milliseconds, HapticEffect.Tick(0.5f))
 *     after(60.milliseconds, HapticEffect.Click(0.9f))
 * }
 * ```
 *
 * @throws IllegalArgumentException for a negative time, more than
 *   [HapticPattern.MaxEvents] events, or one starting after
 *   [HapticPattern.MaxDuration].
 */
fun hapticPattern(block: HapticPatternBuilder.() -> Unit): HapticPattern {
    val builder = HapticPatternBuilder()
    builder.block()
    return builder.build()
}

/** What [hapticPattern]'s block is called on. */
class HapticPatternBuilder internal constructor() {
    private val events = mutableListOf<HapticPattern.Event>()

    /** Starts [primitive] [time] after the start of the pattern. */
    fun at(time: Duration, primitive: HapticEffect.Primitive) {
        require(!time.isNegative()) { "An event cannot start before its pattern: $time" }
        events += HapticPattern.Event(time, primitive)
    }

    /**
     * Starts [primitive] [gap] after the previous event ends — by its nominal
     * length, which is what Android's own compositions mean by a delay. The first
     * event starts [gap] after the start.
     */
    fun after(gap: Duration, primitive: HapticEffect.Primitive) {
        require(!gap.isNegative()) { "A gap cannot be negative: $gap" }
        val previous = events.lastOrNull()
        val start = if (previous == null) gap else previous.at + previous.primitive.nominalDuration + gap
        events += HapticPattern.Event(start, primitive)
    }

    internal fun build(): HapticPattern {
        require(events.size <= HapticPattern.MaxEvents) {
            "A pattern holds at most ${HapticPattern.MaxEvents} events, not ${events.size}"
        }
        require(events.all { it.at <= HapticPattern.MaxDuration }) {
            "Every event must start within ${HapticPattern.MaxDuration}; a longer one is a rumble"
        }
        return HapticPattern(events.sortedBy { it.at })
    }
}

/** The platform-neutral name of a primitive, and how long it nominally lasts. */
internal enum class PrimitiveKind(val nominalMillis: Int) {
    Tick(10),
    LowTick(15),
    Click(15),
    Thud(80),
    Spin(150),
    QuickRise(120),
    SlowRise(400),
    QuickFall(80),
}

internal val HapticEffect.Primitive.kind: PrimitiveKind
    get() = when (this) {
        is HapticEffect.Tick -> PrimitiveKind.Tick
        is HapticEffect.LowTick -> PrimitiveKind.LowTick
        is HapticEffect.Click -> PrimitiveKind.Click
        is HapticEffect.Thud -> PrimitiveKind.Thud
        is HapticEffect.Spin -> PrimitiveKind.Spin
        is HapticEffect.QuickRise -> PrimitiveKind.QuickRise
        is HapticEffect.SlowRise -> PrimitiveKind.SlowRise
        is HapticEffect.QuickFall -> PrimitiveKind.QuickFall
    }

/**
 * How long a primitive lasts before the next can follow it without overlapping.
 * Nominal — Android 12 and later reports the device's own, and uses that.
 */
internal val HapticEffect.Primitive.nominalDuration: Duration
    get() = kind.nominalMillis.milliseconds
