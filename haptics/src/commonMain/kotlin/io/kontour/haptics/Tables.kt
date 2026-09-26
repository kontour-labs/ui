package io.kontour.haptics

import io.kontour.haptics.HapticEffect.Click
import io.kontour.haptics.HapticEffect.Impact
import io.kontour.haptics.HapticEffect.KeyPress
import io.kontour.haptics.HapticEffect.LongPress
import io.kontour.haptics.HapticEffect.LowTick
import io.kontour.haptics.HapticEffect.Notification
import io.kontour.haptics.HapticEffect.Primitive
import io.kontour.haptics.HapticEffect.QuickFall
import io.kontour.haptics.HapticEffect.QuickRise
import io.kontour.haptics.HapticEffect.Selection
import io.kontour.haptics.HapticEffect.SlowRise
import io.kontour.haptics.HapticEffect.Spin
import io.kontour.haptics.HapticEffect.Thud
import io.kontour.haptics.HapticEffect.Threshold
import io.kontour.haptics.HapticEffect.Tick
import io.kontour.haptics.HapticEffect.Toggle
import kotlin.math.roundToInt

// What each platform plays for each effect, as data.
//
// All of it pure and all of it here, in common code, so the whole of the
// mapping is tested on the JVM: the platform files only turn these answers into
// calls. Every number is a starting point, tuned by feel on a device — the
// haptics page in the catalog is where — and not a measurement.

/**
 * How far apart a warning's two knocks start, everywhere it is two knocks.
 *
 * Far enough that the first has stopped ringing before the second lands — at
 * 115ms, with a faint second, a phone played the pair as one — and still close
 * enough to read as one event rather than two.
 */
internal const val WarningGapMillis = 140

// ---------------------------------------------------------------------------
// Android, tier 1: composition primitives at a scale
// ---------------------------------------------------------------------------

/** One primitive in an Android composition: what, how strong, and when it starts. */
internal data class PlannedPrimitive(val kind: PrimitiveKind, val scale: Float, val startMillis: Int)

/** An effect as Android composition primitives. */
internal fun androidPrimitivePlan(effect: HapticEffect): List<PlannedPrimitive> {
    val s = effect.level
    fun one(kind: PrimitiveKind, scale: Float) = listOf(PlannedPrimitive(kind, unit(scale), 0))
    fun at(kind: PrimitiveKind, scale: Float, start: Int) = PlannedPrimitive(kind, unit(scale), start)
    return when (effect) {
        is Primitive -> one(effect.kind, s)
        is Selection -> one(PrimitiveKind.Tick, if (effect.fine) 0.5f * s else 0.8f * s)
        is Impact -> when (effect.style) {
            ImpactStyle.Light -> one(PrimitiveKind.Click, 0.5f * s)
            ImpactStyle.Medium -> one(PrimitiveKind.Click, 0.8f * s)
            ImpactStyle.Heavy -> one(PrimitiveKind.Thud, s)
            ImpactStyle.Soft -> one(PrimitiveKind.LowTick, s)
            ImpactStyle.Rigid -> one(PrimitiveKind.Tick, s)
        }
        // A rising pair for success, two knocks well apart for a warning, three
        // even knocks for an error — the shapes iOS's own three have.
        is Notification -> when (effect.type) {
            NotificationType.Success -> listOf(
                at(PrimitiveKind.Tick, 0.5f * s, 0),
                at(PrimitiveKind.Click, 0.9f * s, 70),
            )
            // The second is a click too. It was a tick at half, which on a phone
            // is under the first knock's ring-down and was felt as one knock:
            // "the warning needs a more distinct second click".
            NotificationType.Warning -> listOf(
                at(PrimitiveKind.Click, 0.9f * s, 0),
                at(PrimitiveKind.Click, 0.75f * s, WarningGapMillis),
            )
            NotificationType.Error -> listOf(
                at(PrimitiveKind.Click, 0.8f * s, 0),
                at(PrimitiveKind.Click, 0.8f * s, 55),
                at(PrimitiveKind.Click, 0.8f * s, 110),
            )
        }
        is Toggle -> if (effect.on) one(PrimitiveKind.Tick, 0.5f * s) else one(PrimitiveKind.LowTick, 0.3f * s)
        is Threshold -> if (effect.activate) one(PrimitiveKind.Click, 0.7f * s) else one(PrimitiveKind.Tick, 0.4f * s)
        is LongPress -> one(PrimitiveKind.Click, s)
        is HapticEffect.KeyPress -> one(PrimitiveKind.Click, 0.5f * s)
    }
}

/** A pattern as Android composition primitives. */
internal fun androidPrimitivePlan(pattern: HapticPattern): List<PlannedPrimitive> =
    pattern.events.filter { it.primitive.audible }.map {
        PlannedPrimitive(it.primitive.kind, it.primitive.level, it.at.inWholeMilliseconds.toInt())
    }

/**
 * [plan] with what this phone lacks swapped for the nearest it has.
 *
 * `LOW_TICK`, `THUD` and `SPIN` arrived a release after the rest, and any phone
 * may report any primitive unsupported. A tier-1 phone has at least `CLICK` and
 * `TICK` — that is what makes it tier 1 — so everything lands on one of those at
 * worst; anything that still cannot play is dropped rather than guessed at.
 */
internal fun substitute(plan: List<PlannedPrimitive>, supported: Set<PrimitiveKind>): List<PlannedPrimitive> =
    plan.flatMap { p ->
        if (p.kind in supported) {
            listOf(p)
        } else {
            val nearest = when (p.kind) {
                PrimitiveKind.LowTick -> listOf(p.copy(kind = PrimitiveKind.Tick, scale = p.scale * 0.5f))
                PrimitiveKind.Thud -> listOf(p.copy(kind = PrimitiveKind.Click))
                PrimitiveKind.Spin -> listOf(
                    p.copy(kind = PrimitiveKind.QuickRise),
                    p.copy(kind = PrimitiveKind.QuickFall, startMillis = p.startMillis + PrimitiveKind.QuickRise.nominalMillis),
                )
                PrimitiveKind.QuickRise -> listOf(p.copy(kind = PrimitiveKind.Click, scale = p.scale * 0.7f))
                PrimitiveKind.SlowRise -> listOf(p.copy(kind = PrimitiveKind.Click, scale = p.scale * 0.6f))
                PrimitiveKind.QuickFall -> listOf(p.copy(kind = PrimitiveKind.Tick, scale = p.scale * 0.8f))
                PrimitiveKind.Tick, PrimitiveKind.Click -> emptyList()
            }
            substitute(nearest, supported)
        }
    }

/**
 * The `delay` for each primitive of [plan] in one Android composition.
 *
 * From Android 16 a delay can be measured from the previous primitive's *start*
 * ([relativeToStart]), which is exactly a timeline. Before it a delay is a pause
 * after the previous one *ends*, so the previous one's length comes off — its
 * real length from Android 12, which reports one, and the nominal one on
 * Android 11. Where two would overlap, the later one waits for the first rather
 * than cutting it off, and everything after moves with it.
 */
internal fun compositionDelays(
    plan: List<PlannedPrimitive>,
    relativeToStart: Boolean,
    durationOf: (PrimitiveKind) -> Int,
): IntArray {
    val delays = IntArray(plan.size)
    var previousStart = 0
    var previousEnd = 0
    plan.forEachIndexed { i, p ->
        if (i == 0) {
            delays[i] = p.startMillis.coerceAtLeast(0)
            previousStart = delays[i]
        } else if (relativeToStart) {
            delays[i] = (p.startMillis - previousStart).coerceAtLeast(0)
            previousStart += delays[i]
        } else {
            delays[i] = (p.startMillis - previousEnd).coerceAtLeast(0)
            previousStart = previousEnd + delays[i]
        }
        previousEnd = previousStart + durationOf(p.kind)
    }
    return delays
}

// ---------------------------------------------------------------------------
// Android, tier 2: the system's own feedback constants
// ---------------------------------------------------------------------------

/**
 * `HapticFeedbackConstants`, by name, with the release each arrived in. The
 * Android file turns a name into its number; nothing here can name one a phone
 * does not have, which the tests hold it to.
 */
internal enum class AndroidConstant(val sinceApi: Int) {
    LongPress(3),
    VirtualKey(5),
    KeyboardTap(8),
    ClockTick(21),
    ContextClick(23),
    Confirm(30),
    Reject(30),
    GestureStart(30),
    GestureEnd(30),
    SegmentTick(34),
    SegmentFrequentTick(34),
    ToggleOn(34),
    ToggleOff(34),
    GestureThresholdActivate(34),
    GestureThresholdDeactivate(34),
}

/**
 * The physical constants, heaviest first. The lighter two are Android 14's
 * segment ticks where the phone has them and the older constants that play the
 * same effects where it does not.
 *
 * Not `TEXT_HANDLE_MOVE`, which this library used to fall back to: a phone maker
 * can switch it off, and some do.
 */
private fun rungs(sdk: Int): List<AndroidConstant> = listOf(
    AndroidConstant.LongPress,
    AndroidConstant.VirtualKey,
    if (sdk >= 34) AndroidConstant.SegmentTick else AndroidConstant.ContextClick,
    if (sdk >= 34) AndroidConstant.SegmentFrequentTick else AndroidConstant.ClockTick,
)

/**
 * The constant [base] rungs down, and further for a softer [strength]: below two
 * thirds one rung lighter, below a third two. The constants have no strength of
 * their own, so this is as fine as tier 2 gets.
 */
private fun ladder(base: Int, strength: Float, sdk: Int): AndroidConstant {
    val step = when {
        strength < 0.34f -> 2
        strength < 0.67f -> 1
        else -> 0
    }
    return rungs(sdk)[(base + step).coerceAtMost(3)]
}

/**
 * The feedback constant for [effect] on Android [sdk].
 *
 * The semantic ones — toggles, thresholds, confirm, reject, keyboard — are never
 * moved by strength: a phone maker tuned each of them as a meaning, and a
 * lighter toggle is not a lighter version of the same thing.
 */
internal fun androidConstant(effect: HapticEffect, sdk: Int): AndroidConstant {
    val s = effect.level
    return when (effect) {
        is Tick -> ladder(2, s, sdk)
        is LowTick -> ladder(3, s, sdk)
        is Click -> ladder(1, s, sdk)
        is Thud -> ladder(0, s, sdk)
        is Spin, is QuickRise, is SlowRise -> if (sdk >= 30) AndroidConstant.GestureStart else ladder(1, s, sdk)
        is QuickFall -> if (sdk >= 30) AndroidConstant.GestureEnd else ladder(2, s, sdk)
        is Selection -> ladder(if (effect.fine) 3 else 2, s, sdk)
        is Impact -> when (effect.style) {
            ImpactStyle.Light, ImpactStyle.Medium -> ladder(1, s, sdk)
            ImpactStyle.Heavy -> ladder(0, s, sdk)
            ImpactStyle.Soft -> ladder(3, s, sdk)
            ImpactStyle.Rigid -> ladder(2, s, sdk)
        }
        is Notification -> when (effect.type) {
            NotificationType.Success -> if (sdk >= 30) AndroidConstant.Confirm else AndroidConstant.VirtualKey
            NotificationType.Warning -> AndroidConstant.LongPress
            NotificationType.Error -> if (sdk >= 30) AndroidConstant.Reject else AndroidConstant.LongPress
        }
        is Toggle -> when {
            sdk >= 34 -> if (effect.on) AndroidConstant.ToggleOn else AndroidConstant.ToggleOff
            else -> if (effect.on) AndroidConstant.ContextClick else AndroidConstant.ClockTick
        }
        is Threshold -> when {
            sdk >= 34 -> if (effect.activate) AndroidConstant.GestureThresholdActivate else AndroidConstant.GestureThresholdDeactivate
            else -> if (effect.activate) AndroidConstant.VirtualKey else AndroidConstant.ClockTick
        }
        is LongPress -> ladder(0, s, sdk)
        is KeyPress -> AndroidConstant.KeyboardTap
    }
}

/**
 * [effect] as feedback constants on a timeline, in milliseconds from now: one
 * constant, except for a warning. No constant is a warning, and one long press
 * was felt as a single knock, so it is two, [WarningGapMillis] apart — the
 * second a step lighter, as on every other platform.
 */
internal fun androidConstantPlan(effect: HapticEffect, sdk: Int): List<Pair<Int, AndroidConstant>> =
    if (effect is Notification && effect.type == NotificationType.Warning) {
        listOf(0 to AndroidConstant.LongPress, WarningGapMillis to AndroidConstant.VirtualKey)
    } else {
        listOf(0 to androidConstant(effect, sdk))
    }

/** `VibrationEffect.createPredefined`'s four, for a phone with a vibrator and no View to ask. */
internal enum class AndroidPredefined { Tick, Click, HeavyClick, DoubleClick }

/** The predefined effect nearest [constant]. */
internal fun androidPredefined(constant: AndroidConstant): AndroidPredefined = when (constant) {
    AndroidConstant.LongPress -> AndroidPredefined.HeavyClick
    AndroidConstant.Reject -> AndroidPredefined.DoubleClick
    AndroidConstant.VirtualKey,
    AndroidConstant.KeyboardTap,
    AndroidConstant.Confirm,
    AndroidConstant.GestureStart,
    AndroidConstant.GestureThresholdActivate,
    AndroidConstant.ToggleOn -> AndroidPredefined.Click
    else -> AndroidPredefined.Tick
}

// ---------------------------------------------------------------------------
// Android: which route
// ---------------------------------------------------------------------------

/** How an Android [Haptics] plays one-shots. */
internal enum class AndroidTier { Primitives, ViewConstants, Predefined, None }

/**
 * The richest route this phone allows, or the one [requested] if it allows that.
 * One that is asked for and not allowed is [AndroidTier.None], not a quiet
 * fallback: the only caller asking is somebody comparing routes on a phone, who
 * needs to know.
 */
internal fun chooseAndroidTier(
    sdk: Int,
    hasVibrator: Boolean,
    permitted: Boolean,
    clickAndTick: Boolean,
    hasView: Boolean,
    requested: AndroidTier? = null,
): AndroidTier {
    val primitives = sdk >= 30 && hasVibrator && permitted && clickAndTick
    val predefined = hasVibrator && permitted
    val available = buildList {
        if (primitives) add(AndroidTier.Primitives)
        if (hasView) add(AndroidTier.ViewConstants)
        if (predefined) add(AndroidTier.Predefined)
    }
    return when (requested) {
        null -> available.firstOrNull() ?: AndroidTier.None
        in available -> requested
        else -> AndroidTier.None
    }
}

/** How an Android [Haptics] plays a rumble. */
internal enum class AndroidRumble { Envelope, Amplitude, TickPulses, ConstantPulses, None }

/**
 * The best rumble this phone allows, or [requested] if it allows that.
 *
 * An envelope first, from Android 16, because it is the one route with a
 * sharpness — a dull rumble rather than the actuator's own buzz. Then a
 * vibration held at an amplitude, then a run of the lightest tick.
 */
internal fun chooseAndroidRumble(
    sdk: Int,
    tier: AndroidTier,
    envelopes: Boolean,
    amplitude: Boolean,
    requested: AndroidRumble? = null,
): AndroidRumble {
    val vibrator = tier == AndroidTier.Primitives || tier == AndroidTier.Predefined
    val available = buildList {
        if (vibrator && sdk >= 36 && envelopes) add(AndroidRumble.Envelope)
        if (vibrator && amplitude) add(AndroidRumble.Amplitude)
        if (vibrator) add(AndroidRumble.TickPulses)
        if (tier == AndroidTier.ViewConstants) add(AndroidRumble.ConstantPulses)
    }
    return when (requested) {
        null -> available.firstOrNull() ?: AndroidRumble.None
        in available -> requested
        else -> AndroidRumble.None
    }
}

/** `createWaveform`'s amplitude, 1 to 255, for a rumble at [intensity]. */
internal fun androidAmplitude(intensity: Float): Int = (1 + 254 * unit(intensity)).roundToInt().coerceIn(1, 255)

/**
 * How far apart a pulsed rumble's pulses are. A primitive tick is short enough to
 * run every 50ms at any intensity; a feedback constant is longer and is spaced
 * further the fainter the rumble, which is the only way it has of being fainter.
 */
internal fun androidPulseMillis(intensity: Float, constants: Boolean): Int =
    if (constants) (110 - 40 * unit(intensity)).roundToInt() else 50

// ---------------------------------------------------------------------------
// iOS
// ---------------------------------------------------------------------------

/** What iOS plays for an effect: one of UIKit's generators, or Core Haptics events. */
internal sealed interface ApplePlan {
    /** `UISelectionFeedbackGenerator.selectionChanged()`. */
    data object Selection : ApplePlan

    /** `UIImpactFeedbackGenerator(style).impactOccurred(intensity)`. */
    data class Impact(val style: ImpactStyle, val intensity: Float) : ApplePlan

    /** `UINotificationFeedbackGenerator.notificationOccurred(type)`. */
    data class Notification(val type: NotificationType) : ApplePlan

    /** A Core Haptics pattern. */
    data class Events(val events: List<AppleEvent>) : ApplePlan
}

/** One Core Haptics event: a transient tap, or a continuous stretch. */
internal data class AppleEvent(
    val startSeconds: Double,
    val continuous: Boolean,
    val durationSeconds: Double,
    val intensity: Float,
    val sharpness: Float,
)

/**
 * What iOS plays for [effect]. UIKit's generators for everything they have a
 * word for — Apple tuned those, and they follow the system's haptics switch —
 * and Core Haptics only for the swells, which UIKit cannot say.
 */
internal fun applePlan(effect: HapticEffect): ApplePlan {
    val s = effect.level
    return when (effect) {
        is Tick -> ApplePlan.Impact(ImpactStyle.Rigid, s)
        is LowTick -> ApplePlan.Impact(ImpactStyle.Soft, s)
        is Click -> ApplePlan.Impact(ImpactStyle.Medium, 0.8f * s)
        is Thud -> ApplePlan.Impact(ImpactStyle.Heavy, s)
        is Spin, is QuickRise, is SlowRise, is QuickFall -> ApplePlan.Events(appleEvents(effect.kind, s, 0.0))
        is Selection -> ApplePlan.Selection
        is Impact -> ApplePlan.Impact(effect.style, s)
        is Notification -> ApplePlan.Notification(effect.type)
        is Toggle -> if (effect.on) ApplePlan.Impact(ImpactStyle.Light, 0.8f * s) else ApplePlan.Impact(ImpactStyle.Soft, 0.6f * s)
        is Threshold -> if (effect.activate) ApplePlan.Impact(ImpactStyle.Rigid, 0.8f * s) else ApplePlan.Impact(ImpactStyle.Soft, 0.6f * s)
        is LongPress -> ApplePlan.Impact(ImpactStyle.Medium, s)
        is KeyPress -> ApplePlan.Impact(ImpactStyle.Light, 0.6f * s)
    }
}

/** A pattern as Core Haptics events. */
internal fun appleEvents(pattern: HapticPattern): List<AppleEvent> =
    pattern.events.filter { it.primitive.audible }.flatMap {
        appleEvents(it.primitive.kind, it.primitive.level, it.at.inWholeMilliseconds / 1000.0)
    }

/**
 * One primitive as Core Haptics events: a transient for each tap, and for each
 * swell a run of short continuous steps, which is a curve without the pattern-wide
 * parameter curves that would reach into the events around it.
 */
internal fun appleEvents(kind: PrimitiveKind, strength: Float, start: Double): List<AppleEvent> {
    val s = unit(strength)
    fun transient(intensity: Float, sharpness: Float) = listOf(AppleEvent(start, false, 0.0, unit(intensity), sharpness))
    fun steps(total: Double, sharpness: Float, vararg levels: Float): List<AppleEvent> {
        val each = total / levels.size
        return levels.mapIndexed { i, level -> AppleEvent(start + i * each, true, each, unit(level * s), sharpness) }
    }
    return when (kind) {
        PrimitiveKind.Tick -> transient(0.6f * s, 0.9f)
        PrimitiveKind.LowTick -> transient(0.5f * s, 0.3f)
        PrimitiveKind.Click -> transient(0.8f * s, 0.7f)
        PrimitiveKind.Thud -> transient(s, 0.2f) + AppleEvent(start + 0.005, true, 0.06, unit(0.4f * s), 0.1f)
        PrimitiveKind.Spin -> steps(0.15, 0.4f, 0.3f, 0.8f, 0.7f, 0.2f)
        PrimitiveKind.QuickRise -> steps(0.12, 0.5f, 0.1f, 0.4f, 0.7f, 1f)
        PrimitiveKind.SlowRise -> steps(0.4, 0.3f, 0.05f, 0.25f, 0.45f, 0.7f)
        PrimitiveKind.QuickFall -> steps(0.08, 0.6f, 1f, 0.6f, 0.3f, 0.1f)
    }
}

/**
 * A rumble's two dynamic parameters on iOS: intensity *multiplies* the event's,
 * which is 1, and sharpness is *added* to the event's, which is ½.
 */
internal fun appleRumbleControls(intensity: Float, sharpness: Float): Pair<Float, Float> =
    unit(intensity) to (unit(sharpness) - 0.5f)

// ---------------------------------------------------------------------------
// The web
// ---------------------------------------------------------------------------

/** The shortest vibration a browser plays that a hand reliably feels. */
internal const val WebShortestMillis = 10

/**
 * The most entries a vibrate array is given. Browsers cap it — Chrome at 99 — and
 * one over the cap is refused whole, not trimmed.
 */
internal const val WebMostEntries = 98

/**
 * [effect] as a `navigator.vibrate` array: on, off, on, … in milliseconds.
 *
 * A browser has no strength, only length, so a softer effect is a shorter one —
 * never under [WebShortestMillis], below which nothing is felt at all.
 */
internal fun webPattern(effect: HapticEffect): IntArray {
    val s = effect.level
    fun on(lo: Int, hi: Int) = intArrayOf(lerp(lo, hi, s))
    fun scaled(vararg entries: Int) = IntArray(entries.size) { i ->
        if (i % 2 == 0) maxOf(WebShortestMillis, (entries[i] * (0.6f + 0.4f * s)).roundToInt()) else entries[i]
    }
    return when (effect) {
        is Tick -> on(12, 20)
        is LowTick -> on(10, 16)
        is Click -> on(18, 26)
        is Thud -> on(24, 36)
        is Spin -> scaled(14, 16, 20, 16, 12)
        is QuickRise -> scaled(10, 12, 16, 10, 22)
        is SlowRise -> scaled(10, 30, 14, 30, 20, 30, 28)
        is QuickFall -> scaled(22, 10, 14, 12, 10)
        is Selection -> if (effect.fine) on(12, 18) else on(14, 20)
        is Impact -> when (effect.style) {
            ImpactStyle.Light -> on(16, 22)
            ImpactStyle.Medium -> on(18, 28)
            ImpactStyle.Heavy -> on(26, 36)
            ImpactStyle.Soft -> on(10, 16)
            ImpactStyle.Rigid -> on(14, 20)
        }
        is Notification -> when (effect.type) {
            NotificationType.Success -> scaled(18, 32, 36)
            // On, off, on: the second starts [WarningGapMillis] after the first.
            NotificationType.Warning -> scaled(30, WarningGapMillis - 30, 26)
            NotificationType.Error -> scaled(18, 28, 18, 28, 18)
        }
        is Toggle -> if (effect.on) on(16, 22) else on(10, 14)
        is Threshold -> if (effect.activate) on(18, 24) else on(10, 14)
        is LongPress -> on(26, 32)
        is KeyPress -> on(10, 14)
    }
}

/** How long a primitive vibrates for on the web, when it is one event of a pattern. */
internal fun webPrimitiveMillis(kind: PrimitiveKind, strength: Float): Int {
    val s = unit(strength)
    return when (kind) {
        PrimitiveKind.Tick -> lerp(12, 20, s)
        PrimitiveKind.LowTick -> lerp(10, 16, s)
        PrimitiveKind.Click -> lerp(18, 26, s)
        PrimitiveKind.Thud -> lerp(24, 36, s)
        PrimitiveKind.Spin -> lerp(30, 40, s)
        PrimitiveKind.QuickRise -> lerp(20, 30, s)
        PrimitiveKind.SlowRise -> lerp(40, 60, s)
        PrimitiveKind.QuickFall -> lerp(16, 24, s)
    }
}

/**
 * A pattern as one vibrate array. A pattern that does not start at once starts
 * with a zero-length vibration and the wait; one that would run into the next is
 * cut short at it; and one too long for a browser loses its end.
 */
internal fun webPattern(pattern: HapticPattern): IntArray {
    val events = pattern.events.filter { it.primitive.audible }
    val entries = mutableListOf<Int>()
    var cursor = 0
    events.forEachIndexed { i, event ->
        val start = event.at.inWholeMilliseconds.toInt()
        val gap = start - cursor
        if (entries.isEmpty()) {
            if (gap > 0) {
                entries += 0
                entries += gap
            }
        } else {
            entries += gap.coerceAtLeast(0)
        }
        val next = events.getOrNull(i + 1)?.at?.inWholeMilliseconds?.toInt()
        val length = webPrimitiveMillis(event.primitive.kind, event.primitive.level)
            .let { if (next != null) it.coerceAtMost((next - start).coerceAtLeast(1)) else it }
        entries += length
        cursor = maxOf(cursor, start) + length
    }
    val capped = if (entries.size > WebMostEntries) entries.subList(0, WebMostEntries - 1) else entries
    return capped.toIntArray()
}

/**
 * A rumble on the web: a vibration a few milliseconds long every 70, longer the
 * stronger the rumble — about as close as an on-and-off motor comes to a hum —
 * repeated for up to [lengthMillis], within what a browser accepts.
 */
internal fun webRumble(intensity: Float, lengthMillis: Int): IntArray {
    val period = 70
    val on = (WebShortestMillis + 14 * unit(intensity)).roundToInt()
    val pairs = (lengthMillis / period).coerceIn(1, WebMostEntries / 2)
    return IntArray(pairs * 2) { if (it % 2 == 0) on else period - on }
}

// ---------------------------------------------------------------------------
// A Mac's trackpad
// ---------------------------------------------------------------------------

/** `NSHapticFeedbackPattern`, with AppKit's numbers. */
internal enum class MacPattern(val code: Long) {
    Generic(0),
    Alignment(1),
    LevelChange(2),
}

/**
 * What a Force Touch trackpad plays for [effect], and when, in milliseconds from
 * now. It has three patterns and no strength; alignment is the lightest.
 */
internal fun macPlan(effect: HapticEffect): List<Pair<Int, MacPattern>> {
    fun now(pattern: MacPattern) = listOf(0 to pattern)
    return when (effect) {
        is Tick, is LowTick, is Selection, is KeyPress -> now(MacPattern.Alignment)
        is Click, is Thud, is Spin, is QuickRise, is SlowRise, is QuickFall, is LongPress -> now(MacPattern.Generic)
        is Impact -> now(if (effect.style == ImpactStyle.Soft) MacPattern.Alignment else MacPattern.Generic)
        is Notification -> when (effect.type) {
            NotificationType.Warning -> listOf(0 to MacPattern.Generic, WarningGapMillis to MacPattern.Generic)
            NotificationType.Success, NotificationType.Error -> listOf(0 to MacPattern.Generic, 120 to MacPattern.Generic)
        }
        is Toggle -> now(if (effect.on) MacPattern.Generic else MacPattern.Alignment)
        is Threshold -> now(MacPattern.LevelChange)
    }
}

/** A pattern on a trackpad: each event's pattern at its time. */
internal fun macPlan(pattern: HapticPattern): List<Pair<Int, MacPattern>> =
    pattern.events.filter { it.primitive.audible }.flatMap { event ->
        macPlan(event.primitive).map { (offset, p) -> (event.at.inWholeMilliseconds.toInt() + offset) to p }
    }

/** How far apart a trackpad rumble's taps are: closer the stronger. */
internal fun macPulseMillis(intensity: Float): Int = (140 - 60 * unit(intensity)).roundToInt()

private fun lerp(lo: Int, hi: Int, t: Float): Int = (lo + (hi - lo) * unit(t)).roundToInt()
