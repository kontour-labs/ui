package io.kontour.haptics

/**
 * One haptic, named by what it is rather than how a platform makes it.
 *
 * Two families.
 *
 * - **[Primitive]s** — [Tick], [LowTick], [Click], [Thud], [Spin], [QuickRise],
 *   [SlowRise], [QuickFall] — are Android's composition primitives by name, the
 *   building blocks a phone maker tunes for its own actuator. They are the only
 *   effects a [HapticPattern] can hold, because they are the only ones every
 *   platform can put on a timeline.
 * - **System effects** — [Selection], [Impact], [Notification], [Toggle],
 *   [Threshold], [LongPress], [KeyPress] — say what happened and let the
 *   platform play its own tuned version of it: UIKit's feedback generators on
 *   iOS, Android's `HapticFeedbackConstants` where a phone has no primitives.
 *   Where a phone does, they are played as primitives at the strengths in the
 *   table on the haptics guide.
 *
 * **[strength] is 0 to 1**, and clamped when played rather than checked when
 * built: NaN and anything below 0 is 0, anything above 1 is 1, and 0.001 or less
 * plays nothing at all. At 1 a system effect is the platform's own version;
 * below it, softer, as far as the platform can say so — see
 * [HapticCapability.honoursStrength].
 */
sealed interface HapticEffect {

    /** How strongly to play it, 0 to 1. */
    val strength: Float

    /** One of the primitives a [HapticPattern] is built from. */
    sealed interface Primitive : HapticEffect

    /** The shortest, crispest tap there is: a texture, a detent going past. */
    data class Tick(override val strength: Float = 1f) : Primitive

    /** A [Tick] with the treble taken out — deeper and softer. */
    data class LowTick(override val strength: Float = 1f) : Primitive

    /** A firm, short press: a button's click. */
    data class Click(override val strength: Float = 1f) : Primitive

    /** A low, heavy knock, as of something hitting a wall. */
    data class Thud(override val strength: Float = 1f) : Primitive

    /** A short whirl that rises and falls. */
    data class Spin(override val strength: Float = 1f) : Primitive

    /** A quick swell, up to full. */
    data class QuickRise(override val strength: Float = 1f) : Primitive

    /** A slow swell, up to a little under full. */
    data class SlowRise(override val strength: Float = 1f) : Primitive

    /** A quick fade from full to nothing. */
    data class QuickFall(override val strength: Float = 1f) : Primitive

    /**
     * A selection moved: a picker's row, a slider's step, a segment.
     *
     * @property fine `true` for a texture of many small steps — a slider with a
     *   hundred of them, a drum of numbers — and `false` for a few large ones.
     *   Android plays these as `SEGMENT_FREQUENT_TICK` and `SEGMENT_TICK`; iOS
     *   has one selection haptic for both.
     */
    data class Selection(val fine: Boolean = false, override val strength: Float = 1f) : HapticEffect

    /** Something struck: a collision of the weight [style] names. */
    data class Impact(val style: ImpactStyle = ImpactStyle.Medium, override val strength: Float = 1f) : HapticEffect

    /**
     * An outcome: something succeeded, needs care, or failed.
     *
     * iOS plays Apple's own at full strength whatever [strength] says; it is
     * there for the platforms that play these as primitives.
     */
    data class Notification(val type: NotificationType, override val strength: Float = 1f) : HapticEffect

    /** A switch, a checkbox, a filter flipped — [on] or off, which feel different. */
    data class Toggle(val on: Boolean, override val strength: Float = 1f) : HapticEffect

    /**
     * A drag crossed a threshold: going [activate]s the thing letting go will
     * do, coming back out of it does not, and feels softer.
     */
    data class Threshold(val activate: Boolean = true, override val strength: Float = 1f) : HapticEffect

    /** A press held long enough to become something else. */
    data class LongPress(override val strength: Float = 1f) : HapticEffect

    /** A key struck. */
    data class KeyPress(override val strength: Float = 1f) : HapticEffect
}

/**
 * The same effect at [factor] times its strength: a texture that follows how fast
 * a finger moves, say, played as one effect at a strength of the moment.
 *
 * ```kotlin
 * haptics.play(HapticEffect.LowTick(0.6f).scaled(speed))
 * ```
 *
 * Clamped as any strength is, when played — a factor over 1 on a strength of 1
 * is still 1. A [HapticEffect.Notification] on iOS is Apple's own at any
 * strength, so scaling it there changes nothing.
 */
fun HapticEffect.scaled(factor: Float): HapticEffect {
    val s = strength * factor
    return when (this) {
        is HapticEffect.Tick -> copy(strength = s)
        is HapticEffect.LowTick -> copy(strength = s)
        is HapticEffect.Click -> copy(strength = s)
        is HapticEffect.Thud -> copy(strength = s)
        is HapticEffect.Spin -> copy(strength = s)
        is HapticEffect.QuickRise -> copy(strength = s)
        is HapticEffect.SlowRise -> copy(strength = s)
        is HapticEffect.QuickFall -> copy(strength = s)
        is HapticEffect.Selection -> copy(strength = s)
        is HapticEffect.Impact -> copy(strength = s)
        is HapticEffect.Notification -> copy(strength = s)
        is HapticEffect.Toggle -> copy(strength = s)
        is HapticEffect.Threshold -> copy(strength = s)
        is HapticEffect.LongPress -> copy(strength = s)
        is HapticEffect.KeyPress -> copy(strength = s)
    }
}

/** The weight of an [HapticEffect.Impact]. These are UIKit's five, by name. */
enum class ImpactStyle {
    /** A small, light object. */
    Light,

    /** The default. */
    Medium,

    /** A large, heavy object. */
    Heavy,

    /** A collision with something that gives a little. */
    Soft,

    /** A collision with something that does not give at all. */
    Rigid,
}

/** What an [HapticEffect.Notification] reports. */
enum class NotificationType {
    /** It worked. */
    Success,

    /** It needs care — or something with consequences is about to be asked. */
    Warning,

    /** It failed. */
    Error,
}

/**
 * [HapticEffect.strength] as it is played: clamped to 0..1, NaN as 0.
 */
internal val HapticEffect.level: Float
    get() = strength.let { if (it.isNaN()) 0f else it.coerceIn(0f, 1f) }

/** Whether this is strong enough to play at all. */
internal val HapticEffect.audible: Boolean
    get() = level > SilentBelow

/** At or below this, an effect plays nothing — the strength of a slider at rest. */
internal const val SilentBelow = 0.001f

/** A 0..1 value as it is played, NaN as 0. */
internal fun unit(value: Float): Float = if (value.isNaN()) 0f else value.coerceIn(0f, 1f)
