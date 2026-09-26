package io.kontour.haptics

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.time.Duration

/**
 * The haptics of this browser: the Vibration API, where there is one.
 *
 * Chrome and Firefox on Android have it; Safari has not, on any device, and
 * nothing plays there. A desktop browser may report it and have no motor to
 * run. It has no strength — only how long — so a softer effect here is a
 * shorter one, and a rumble is a run of short vibrations. A browser also ignores
 * it until the person has touched the page once.
 */
fun Haptics(): Haptics = WebHaptics()

private class WebHaptics : Haptics {
    private val available = canVibrate()
    private var closed = false
    private val rumbles = mutableSetOf<WebRumble>()

    override val capability: HapticCapability
        get() = if (available) {
            HapticCapability(
                richness = HapticCapability.Richness.Basic,
                honoursStrength = false,
                rumble = HapticCapability.RumbleSupport.Pulsed,
                details = listOf("Vibration API: yes", "Strength becomes length; a rumble is a run of short pulses"),
            )
        } else {
            HapticCapability.None.copy(details = listOf("Vibration API: none (Safari has none on any device)"))
        }

    override fun play(effect: HapticEffect) {
        if (closed || !available || !effect.audible) return
        vibrate(webPattern(effect).joinToString(","))
    }

    override fun play(pattern: HapticPattern) {
        if (closed || !available) return
        val entries = webPattern(pattern)
        if (entries.isNotEmpty()) vibrate(entries.joinToString(","))
    }

    override fun startRumble(intensity: Float, sharpness: Float, timeout: Duration): Rumble {
        if (closed || !available) return StoppedRumble
        val rumble = WebRumble(timeout.inWholeMilliseconds.toInt().coerceIn(RumbleStepMillis, LongestRumbleMillis))
        rumbles += rumble
        rumble.issue(unit(intensity), force = true)
        return rumble
    }

    override fun cancel() {
        rumbles.toList().forEach { it.stop() }
        if (available) vibrate("")
    }

    override fun close() {
        if (closed) return
        cancel()
        closed = true
    }

    /**
     * A run of pulses that ends by itself after its lease, with no timer: the
     * browser plays the whole array and stops. An update hands it a fresh one.
     */
    private inner class WebRumble(private val lengthMillis: Int) : Rumble {
        private var issuedAt = Double.NEGATIVE_INFINITY
        private var intensity = 0f

        override val isActive: Boolean
            get() = active && now() - issuedAt < lengthMillis

        private var active = true

        override fun update(intensity: Float, sharpness: Float) {
            if (active) issue(unit(intensity))
        }

        override fun stop() {
            if (!active) return
            active = false
            rumbles -= this
            vibrate("")
        }

        /** At most every [RumbleStepMillis]: each one restarts the vibration. */
        fun issue(intensity: Float, force: Boolean = false) {
            val t = now()
            if (!force && t - issuedAt < RumbleStepMillis) return
            this.intensity = intensity
            issuedAt = t
            vibrate(webRumble(intensity, lengthMillis).joinToString(","))
        }
    }

    private companion object {
        const val RumbleStepMillis = 100

        /** As long as a browser's array allows at the rumble's period. */
        const val LongestRumbleMillis = (WebMostEntries / 2) * 70
    }
}

/** Whether this browser has `navigator.vibrate` at all. */
@OptIn(ExperimentalWasmJsInterop::class)
private fun canVibrate(): Boolean = js(
    "typeof navigator !== 'undefined' && typeof navigator.vibrate === 'function'"
)

/**
 * `navigator.vibrate` with [pattern], comma-separated milliseconds; an empty one
 * stops whatever is playing. A string because it crosses into both the JS and the
 * Wasm world the same way.
 */
@OptIn(ExperimentalWasmJsInterop::class)
private fun vibrate(pattern: String): Boolean = js(
    """(function(p){try{
        return navigator.vibrate(p.length ? p.split(',').map(Number) : 0);
    }catch(e){return false;}})(pattern)"""
)

@OptIn(ExperimentalWasmJsInterop::class)
private fun now(): Double = js("Date.now()")
