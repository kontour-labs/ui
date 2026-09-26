package io.kontour.haptics

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import kotlin.math.abs
import kotlin.time.Duration

/**
 * Which way an Android [Haptics] plays one-shots. [Auto] everywhere but a screen
 * for comparing them on a phone.
 */
enum class AndroidHapticsRoute {
    /** The richest this phone allows: [Primitives], then [ViewConstants], then [Predefined]. */
    Auto,

    /** Composition primitives at a scale — Android 11 and later, on an actuator that has them. */
    Primitives,

    /** The system's own `HapticFeedbackConstants`, through the `View` passed in. */
    ViewConstants,

    /** `VibrationEffect.createPredefined`'s four effects, for a vibrator with no `View`. */
    Predefined,
}

/** Which way an Android [Haptics] plays a rumble. [Auto] everywhere but a comparison. */
enum class AndroidRumbleRoute {
    /** The best this phone allows: [Envelope], then [Amplitude], then [Pulsed]. */
    Auto,

    /** A looping envelope with a sharpness — Android 16, on an actuator that has them. */
    Envelope,

    /** A vibration held at an amplitude, on an actuator that can vary one. */
    Amplitude,

    /** A rapid run of the lightest tick. */
    Pulsed,
}

/**
 * The haptics of this phone.
 *
 * Plays through the richest route the phone allows. **Composition primitives**
 * first, from Android 11 on an actuator that reports them: the effects a phone
 * maker tunes for its own hardware, at any strength, played as touch feedback so
 * the system's touch-vibration setting and intensity apply to them. Then the
 * system's own **feedback constants** through [view], which need no permission
 * and are what a phone without primitives is tuned for. Then, with no view,
 * **predefined** effects.
 *
 * The primitives need `android.permission.VIBRATE`, which this library's manifest
 * declares — a normal permission, granted at install, with no prompt. An app that
 * removes it gets the constants instead, and a phone that refuses a vibration
 * for any other reason moves this to them for good.
 *
 * @param context Any context; only its application context is kept.
 * @param view The view the feedback constants are played through. Without one a
 *   phone with no primitives has only the four predefined effects.
 * @param route [AndroidHapticsRoute.Auto] unless comparing routes. One asked for
 *   and not available plays nothing, and [HapticCapability.details] says why.
 * @param rumbleRoute The same, for rumbles.
 */
fun Haptics(
    context: Context,
    view: View? = null,
    route: AndroidHapticsRoute = AndroidHapticsRoute.Auto,
    rumbleRoute: AndroidRumbleRoute = AndroidRumbleRoute.Auto,
): Haptics = AndroidHaptics(context, view, route, rumbleRoute)

private class AndroidHaptics(
    context: Context,
    private val view: View?,
    private val route: AndroidHapticsRoute,
    private val rumbleRoute: AndroidRumbleRoute,
) : Haptics {

    private val sdk = Build.VERSION.SDK_INT
    private val resolver: ContentResolver = context.applicationContext.contentResolver
    private val vibrator: Vibrator? = findVibrator(context.applicationContext)
    private val hasVibrator = vibrator?.hasVibrator() == true
    private val permitted =
        context.checkSelfPermission(Manifest.permission.VIBRATE) == PackageManager.PERMISSION_GRANTED
    private val supported: Set<PrimitiveKind> =
        if (sdk >= 30 && vibrator != null && hasVibrator) supportedPrimitives(vibrator, sdk) else emptySet()
    private val durations: Map<PrimitiveKind, Int> =
        if (sdk >= 31 && vibrator != null) primitiveDurations(vibrator, supported) else emptyMap()
    private val envelopes = sdk >= 36 && vibrator != null && hasVibrator && vibrator.areEnvelopeEffectsSupported()
    private val amplitude = vibrator != null && hasVibrator && vibrator.hasAmplitudeControl()

    /** Refused a vibration once: the constants from now on. */
    private var demoted = false

    private var tier = pickTier()
    private var rumbleKind = pickRumble()

    private val handler = Handler(Looper.getMainLooper())

    /** What this instance posted, so [cancel] takes back its own and nobody else's. */
    private val token = Any()
    private val rumbles = mutableSetOf<AndroidRumbleHandle>()
    private var closed = false

    private fun pickTier(): AndroidTier = chooseAndroidTier(
        sdk = sdk,
        hasVibrator = hasVibrator && !demoted,
        permitted = permitted,
        clickAndTick = PrimitiveKind.Click in supported && PrimitiveKind.Tick in supported,
        hasView = view != null,
        requested = when (route) {
            AndroidHapticsRoute.Auto -> null
            AndroidHapticsRoute.Primitives -> AndroidTier.Primitives
            AndroidHapticsRoute.ViewConstants -> AndroidTier.ViewConstants
            AndroidHapticsRoute.Predefined -> AndroidTier.Predefined
        },
    )

    private fun pickRumble(): AndroidRumble = chooseAndroidRumble(
        sdk = sdk,
        tier = tier,
        envelopes = envelopes,
        amplitude = amplitude,
        requested = when (rumbleRoute) {
            AndroidRumbleRoute.Auto -> null
            AndroidRumbleRoute.Envelope -> AndroidRumble.Envelope
            AndroidRumbleRoute.Amplitude -> AndroidRumble.Amplitude
            AndroidRumbleRoute.Pulsed -> if (tier == AndroidTier.ViewConstants) AndroidRumble.ConstantPulses else AndroidRumble.TickPulses
        },
    )

    override val capability: HapticCapability
        get() = HapticCapability(
            level = when (tier) {
                AndroidTier.Primitives -> HapticCapability.Level.Rich
                AndroidTier.ViewConstants, AndroidTier.Predefined -> HapticCapability.Level.Basic
                AndroidTier.None -> HapticCapability.Level.None
            },
            honoursStrength = tier == AndroidTier.Primitives,
            rumble = when (rumbleKind) {
                AndroidRumble.Envelope, AndroidRumble.Amplitude -> HapticCapability.RumbleSupport.Continuous
                AndroidRumble.TickPulses, AndroidRumble.ConstantPulses -> HapticCapability.RumbleSupport.Pulsed
                AndroidRumble.None -> HapticCapability.RumbleSupport.None
            },
            details = listOf(
                "Android API $sdk",
                "Route: $tier" + if (route != AndroidHapticsRoute.Auto) " (asked for $route)" else "",
                "Rumble: $rumbleKind" + if (rumbleRoute != AndroidRumbleRoute.Auto) " (asked for $rumbleRoute)" else "",
                "Vibrator: " + if (hasVibrator) "yes" else "none",
                "VIBRATE permission: " + if (permitted) "granted" else "not granted",
                "Primitives: " + supported.joinToString().ifEmpty { "none" },
                "Amplitude control: " + if (amplitude) "yes" else "no",
                "Envelopes: " + if (envelopes) "yes" else "no",
                "Touch feedback setting: " + if (touchFeedbackOn()) "on" else "off",
            ) + if (demoted) listOf("A vibration was refused; using the system constants") else emptyList(),
        )

    override fun play(effect: HapticEffect) {
        if (closed || !effect.audible) return
        when (tier) {
            AndroidTier.Primitives -> {
                val plan = androidPrimitivePlan(effect)
                vibrateAround(estimateMillis(plan)) { composition(plan)?.let(::vibrate) }
            }
            AndroidTier.ViewConstants -> playConstants(effect) { perform(it) }
            AndroidTier.Predefined -> playConstants(effect) { vibrateAround(PredefinedMillis) { vibrate(predefined(it)) } }
            AndroidTier.None -> Unit
        }
    }

    /** [effect]'s constants: the first now, any after it on the main looper at their time. */
    private inline fun playConstants(effect: HapticEffect, crossinline play: (AndroidConstant) -> Unit) {
        val now = SystemClock.uptimeMillis()
        androidConstantPlan(effect, sdk).forEach { (at, constant) ->
            if (at == 0) play(constant) else handler.postAtTime({ play(constant) }, token, now + at)
        }
    }

    override fun play(pattern: HapticPattern) {
        if (closed) return
        when (tier) {
            AndroidTier.Primitives -> {
                val plan = androidPrimitivePlan(pattern)
                vibrateAround(estimateMillis(plan)) { composition(plan)?.let(::vibrate) }
            }
            // No pattern of their own: each event on the main looper at its time.
            AndroidTier.ViewConstants, AndroidTier.Predefined -> {
                val now = SystemClock.uptimeMillis()
                pattern.events.filter { it.primitive.audible }.forEach { event ->
                    handler.postAtTime({ play(event.primitive) }, token, now + event.at.inWholeMilliseconds)
                }
            }
            AndroidTier.None -> Unit
        }
    }

    override fun startRumble(intensity: Float, sharpness: Float, timeout: Duration): Rumble {
        if (closed || rumbleKind == AndroidRumble.None) return StoppedRumble
        val handle = AndroidRumbleHandle(rumbleKind, timeout.inWholeMilliseconds)
        rumbles += handle
        handle.start(unit(intensity), unit(sharpness))
        return handle
    }

    override fun cancel() {
        handler.removeCallbacksAndMessages(token)
        rumbles.toList().forEach { it.stop() }
        if (tier == AndroidTier.Primitives || tier == AndroidTier.Predefined) cancelVibrator()
    }

    override fun close() {
        if (closed) return
        cancel()
        closed = true
    }

    // --- playing ------------------------------------------------------------

    /** One composition for [plan], with what this phone lacks swapped out. */
    private fun composition(plan: List<PlannedPrimitive>): VibrationEffect? {
        val playable = substitute(plan, supported)
        if (playable.isEmpty()) return null
        val relative = sdk >= 36
        val delays = compositionDelays(playable, relative) { durations[it] ?: it.nominalMillis }
        val composition = VibrationEffect.startComposition()
        playable.forEachIndexed { i, p ->
            if (relative) {
                composition.addPrimitive(primitiveId(p.kind), p.scale, delays[i], VibrationEffect.Composition.DELAY_TYPE_RELATIVE_START_OFFSET)
            } else {
                composition.addPrimitive(primitiveId(p.kind), p.scale, delays[i])
            }
        }
        return composition.compose()
    }

    private fun predefined(constant: AndroidConstant): VibrationEffect = VibrationEffect.createPredefined(
        when (androidPredefined(constant)) {
            AndroidPredefined.Tick -> VibrationEffect.EFFECT_TICK
            AndroidPredefined.Click -> VibrationEffect.EFFECT_CLICK
            AndroidPredefined.HeavyClick -> VibrationEffect.EFFECT_HEAVY_CLICK
            AndroidPredefined.DoubleClick -> VibrationEffect.EFFECT_DOUBLE_CLICK
        },
    )

    /**
     * Plays a one-shot through the vibrator, around any continuous rumble: a
     * vibration that repeats can make the system pass over a one-shot started
     * while it runs, so the rumble steps aside for [millis] and comes back.
     */
    private inline fun vibrateAround(millis: Int, play: () -> Unit) {
        if (!touchFeedbackOn()) return
        val continuous = rumbles.filter { it.continuous && it.isActive }
        continuous.forEach { it.stepAside(millis + StepAsideMargin) }
        play()
    }

    /**
     * Plays [effect] as touch feedback, so the system's touch-vibration setting and
     * intensity reach it. `VibrationAttributes` from Android 13; before it, the
     * audio attributes the system's own feedback used, and our own read of the
     * setting in [touchFeedbackOn].
     */
    private fun vibrate(effect: VibrationEffect) {
        val v = vibrator ?: return
        try {
            if (sdk >= 33) {
                v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
            } else {
                vibrateLegacy(v, effect)
            }
        } catch (_: SecurityException) {
            demote()
        }
    }

    @Suppress("DEPRECATION")
    private fun vibrateLegacy(v: Vibrator, effect: VibrationEffect) = v.vibrate(effect, TouchAudio)

    private fun cancelVibrator() {
        try {
            vibrator?.cancel()
        } catch (_: SecurityException) {
            demote()
        }
    }

    private fun demote() {
        if (demoted) return
        demoted = true
        rumbles.toList().forEach { it.stop() }
        tier = pickTier()
        rumbleKind = pickRumble()
    }

    /**
     * Whether the person has touch vibration on. From Android 13 the touch usage
     * carries that to the system, which drops or scales the vibration itself;
     * before it nothing does, so this reads the setting.
     */
    @Suppress("DEPRECATION")
    private fun touchFeedbackOn(): Boolean =
        sdk >= 33 || Settings.System.getInt(resolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) != 0

    private fun perform(constant: AndroidConstant) {
        val v = view ?: return
        val id = constantId(constant)
        if (Looper.myLooper() == Looper.getMainLooper()) v.performHapticFeedback(id) else v.post { v.performHapticFeedback(id) }
    }

    private fun estimateMillis(plan: List<PlannedPrimitive>): Int =
        plan.maxOfOrNull { it.startMillis + (durations[it.kind] ?: it.kind.nominalMillis) } ?: 0

    // --- rumbles -------------------------------------------------------------

    private inner class AndroidRumbleHandle(private val kind: AndroidRumble, private val leaseMillis: Long) : Rumble {

        val continuous: Boolean get() = kind == AndroidRumble.Envelope || kind == AndroidRumble.Amplitude

        override var isActive: Boolean = false
            private set

        private var intensity = 0f
        private var sharpness = 0f
        private var issuedAmplitude = -1
        private var issuedSharpness = -1f
        private var issuedAt = 0L
        private var aside = false

        private val expire = Runnable { stop() }
        private val reissue = Runnable { issue() }
        private val resume = Runnable {
            aside = false
            issue(force = true)
        }
        private val pulse = object : Runnable {
            override fun run() {
                if (!isActive) return
                if (!aside) pulseOnce()
                handler.postDelayed(this, androidPulseMillis(intensity, kind == AndroidRumble.ConstantPulses).toLong())
            }
        }

        fun start(intensity: Float, sharpness: Float) {
            this.intensity = intensity
            this.sharpness = sharpness
            isActive = true
            renewLease()
            if (continuous) issue(force = true) else pulse.run()
        }

        override fun update(intensity: Float, sharpness: Float) {
            if (!isActive) return
            this.intensity = unit(intensity)
            this.sharpness = unit(sharpness)
            renewLease()
            if (continuous) issue()
        }

        override fun stop() {
            if (!isActive) return
            isActive = false
            handler.removeCallbacks(expire)
            handler.removeCallbacks(reissue)
            handler.removeCallbacks(resume)
            handler.removeCallbacks(pulse)
            rumbles -= this
            if (continuous) cancelVibrator()
        }

        /** Makes way for a one-shot for [millis], then carries on. */
        fun stepAside(millis: Int) {
            aside = true
            handler.removeCallbacks(resume)
            if (continuous) cancelVibrator()
            handler.postDelayed(resume, millis.toLong())
        }

        private fun renewLease() {
            handler.removeCallbacks(expire)
            handler.postDelayed(expire, leaseMillis)
        }

        /**
         * Re-issues a continuous rumble at the current strength — at most every
         * [ReissueMillis], and only when it has changed by enough to feel, because
         * each one restarts the vibration.
         */
        private fun issue(force: Boolean = false) {
            if (!isActive || aside) return
            val amplitude = androidAmplitude(intensity)
            val changed = issuedAmplitude < 0 ||
                abs(amplitude - issuedAmplitude) >= FeltAmplitudeStep ||
                abs(sharpness - issuedSharpness) >= FeltSharpnessStep
            if (!force && !changed) return
            val since = SystemClock.uptimeMillis() - issuedAt
            if (!force && since < ReissueMillis) {
                handler.removeCallbacks(reissue)
                handler.postDelayed(reissue, ReissueMillis - since)
                return
            }
            issuedAmplitude = amplitude
            issuedSharpness = sharpness
            issuedAt = SystemClock.uptimeMillis()
            if (!touchFeedbackOn()) return
            vibrate(
                when (kind) {
                    AndroidRumble.Envelope -> envelope(intensity, sharpness)
                    else -> VibrationEffect.createWaveform(longArrayOf(1000), intArrayOf(androidAmplitude(intensity)), 0)
                },
            )
        }

        private fun pulseOnce() {
            when (kind) {
                AndroidRumble.ConstantPulses -> perform(androidConstant(HapticEffect.Selection(fine = true), sdk))
                AndroidRumble.TickPulses -> if (touchFeedbackOn()) {
                    if (tier == AndroidTier.Primitives) {
                        composition(listOf(PlannedPrimitive(PrimitiveKind.LowTick, intensity, 0)))?.let(::vibrate)
                    } else {
                        vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
                    }
                }
                else -> Unit
            }
        }
    }

    /**
     * A looping envelope: up to [intensity] at [sharpness], held, and down — an
     * envelope has to end at nothing, so each loop dips, once every
     * [EnvelopeHoldMillis], which a rumble held for a moment never reaches.
     */
    private fun envelope(intensity: Float, sharpness: Float): VibrationEffect {
        val loop = VibrationEffect.BasicEnvelopeBuilder()
            .setInitialSharpness(sharpness)
            .addControlPoint(intensity, sharpness, 20)
            .addControlPoint(intensity, sharpness, EnvelopeHoldMillis)
            .addControlPoint(0f, sharpness, 10)
            .build()
        return VibrationEffect.createRepeatingEffect(loop)
    }

    private companion object {
        /** The audio attributes the system's own haptic feedback used before Android 13. */
        val TouchAudio: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        const val ReissueMillis = 50L

        /** The smallest change of amplitude, of 255, worth restarting a vibration for. */
        const val FeltAmplitudeStep = 4

        /** The same, for sharpness. */
        const val FeltSharpnessStep = 0.05f
        const val EnvelopeHoldMillis = 1500L
        const val PredefinedMillis = 30
        const val StepAsideMargin = 20
    }
}

private fun findVibrator(context: Context): Vibrator? =
    if (Build.VERSION.SDK_INT >= 31) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        context.getSystemService(Vibrator::class.java)
    }

/** The primitives this actuator reports, asking about the Android 12 ones only from Android 12. */
private fun supportedPrimitives(vibrator: Vibrator, sdk: Int): Set<PrimitiveKind> {
    val kinds = PrimitiveKind.entries.filter { sdk >= 31 || it !in AndroidTwelvePrimitives }
    val answers = vibrator.arePrimitivesSupported(*kinds.map(::primitiveId).toIntArray())
    return kinds.filterIndexed { i, _ -> answers.getOrElse(i) { false } }.toSet()
}

private fun primitiveDurations(vibrator: Vibrator, kinds: Set<PrimitiveKind>): Map<PrimitiveKind, Int> {
    val list = kinds.toList()
    val durations = vibrator.getPrimitiveDurations(*list.map(::primitiveId).toIntArray())
    return list.mapIndexedNotNull { i, kind -> durations.getOrNull(i)?.takeIf { it > 0 }?.let { kind to it } }.toMap()
}

private val AndroidTwelvePrimitives = setOf(PrimitiveKind.LowTick, PrimitiveKind.Thud, PrimitiveKind.Spin)

private fun primitiveId(kind: PrimitiveKind): Int = when (kind) {
    PrimitiveKind.Tick -> VibrationEffect.Composition.PRIMITIVE_TICK
    PrimitiveKind.LowTick -> VibrationEffect.Composition.PRIMITIVE_LOW_TICK
    PrimitiveKind.Click -> VibrationEffect.Composition.PRIMITIVE_CLICK
    PrimitiveKind.Thud -> VibrationEffect.Composition.PRIMITIVE_THUD
    PrimitiveKind.Spin -> VibrationEffect.Composition.PRIMITIVE_SPIN
    PrimitiveKind.QuickRise -> VibrationEffect.Composition.PRIMITIVE_QUICK_RISE
    PrimitiveKind.SlowRise -> VibrationEffect.Composition.PRIMITIVE_SLOW_RISE
    PrimitiveKind.QuickFall -> VibrationEffect.Composition.PRIMITIVE_QUICK_FALL
}

private fun constantId(constant: AndroidConstant): Int = when (constant) {
    AndroidConstant.LongPress -> HapticFeedbackConstants.LONG_PRESS
    AndroidConstant.VirtualKey -> HapticFeedbackConstants.VIRTUAL_KEY
    AndroidConstant.KeyboardTap -> HapticFeedbackConstants.KEYBOARD_TAP
    AndroidConstant.ClockTick -> HapticFeedbackConstants.CLOCK_TICK
    AndroidConstant.ContextClick -> HapticFeedbackConstants.CONTEXT_CLICK
    AndroidConstant.Confirm -> HapticFeedbackConstants.CONFIRM
    AndroidConstant.Reject -> HapticFeedbackConstants.REJECT
    AndroidConstant.GestureStart -> HapticFeedbackConstants.GESTURE_START
    AndroidConstant.GestureEnd -> HapticFeedbackConstants.GESTURE_END
    AndroidConstant.SegmentTick -> HapticFeedbackConstants.SEGMENT_TICK
    AndroidConstant.SegmentFrequentTick -> HapticFeedbackConstants.SEGMENT_FREQUENT_TICK
    AndroidConstant.ToggleOn -> HapticFeedbackConstants.TOGGLE_ON
    AndroidConstant.ToggleOff -> HapticFeedbackConstants.TOGGLE_OFF
    AndroidConstant.GestureThresholdActivate -> HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE
    AndroidConstant.GestureThresholdDeactivate -> HapticFeedbackConstants.GESTURE_THRESHOLD_DEACTIVATE
}
