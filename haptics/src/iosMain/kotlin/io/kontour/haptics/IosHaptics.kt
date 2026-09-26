// Passing `null` for an `NSError**` is how every Core Haptics call here says it
// will read the result instead of the error, and that needs the opt-in.
@file:OptIn(ExperimentalForeignApi::class)

package io.kontour.haptics

import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreHaptics.CHHapticAdvancedPatternPlayerProtocol
import platform.CoreHaptics.CHHapticDynamicParameter
import platform.CoreHaptics.CHHapticDynamicParameterIDHapticIntensityControl
import platform.CoreHaptics.CHHapticDynamicParameterIDHapticSharpnessControl
import platform.CoreHaptics.CHHapticEngine
import platform.CoreHaptics.CHHapticEvent
import platform.CoreHaptics.CHHapticEventParameter
import platform.CoreHaptics.CHHapticEventParameterIDHapticIntensity
import platform.CoreHaptics.CHHapticEventParameterIDHapticSharpness
import platform.CoreHaptics.CHHapticEventTypeHapticContinuous
import platform.CoreHaptics.CHHapticEventTypeHapticTransient
import platform.CoreHaptics.CHHapticPattern
import platform.CoreHaptics.CHHapticTimeImmediate
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSThread
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIDevice
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackType
import platform.UIKit.UISelectionFeedbackGenerator
import platform.darwin.DISPATCH_TIME_NOW
import platform.darwin.dispatch_after
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_time
import kotlin.time.Duration

/**
 * The haptics of this iPhone.
 *
 * One-off effects are UIKit's feedback generators — selection, impact, and
 * notification — which Apple tuned and which follow the system's haptics
 * switch. Core Haptics plays what UIKit has no word for: the swells, patterns,
 * and rumbles. An iPad has neither, and plays nothing.
 *
 * Every instance shares one set of generators and one haptic engine for the
 * process, so making one per screen costs nothing. The engine starts the first
 * time something needs it, stops itself when idle, and starts again when next
 * asked — including after the app has been in the background, when the system
 * stops it.
 */
fun Haptics(): Haptics = IosHaptics()

private class IosHaptics : Haptics {
    private val rumbles = mutableSetOf<IosRumble>()
    private var closed = false

    override val capability: HapticCapability get() = AppleHaptics.capability

    override fun play(effect: HapticEffect) {
        if (closed || !effect.audible || !AppleHaptics.supportsHaptics) return
        val plan = applePlan(effect)
        onMain { AppleHaptics.play(plan) }
    }

    override fun play(pattern: HapticPattern) {
        if (closed || !AppleHaptics.supportsHaptics) return
        val events = appleEvents(pattern)
        onMain { AppleHaptics.playEvents(events) }
    }

    override fun startRumble(intensity: Float, sharpness: Float, timeout: Duration): Rumble {
        if (closed || !AppleHaptics.supportsHaptics) return StoppedRumble
        val rumble = IosRumble(timeout, onStopped = { rumbles -= it })
        rumbles += rumble
        rumble.start(unit(intensity), unit(sharpness))
        return rumble
    }

    override fun cancel() {
        rumbles.toList().forEach { it.stop() }
    }

    override fun close() {
        if (closed) return
        cancel()
        closed = true
    }
}

/** The generators and the engine, one of each for the process. */
private object AppleHaptics {

    /** Whether this device has a Taptic Engine at all; iPads do not. */
    val supportsHaptics: Boolean by lazy { CHHapticEngine.capabilitiesForHardware().supportsHaptics }

    val capability: HapticCapability by lazy {
        HapticCapability(
            level = if (supportsHaptics) HapticCapability.Level.Rich else HapticCapability.Level.None,
            honoursStrength = supportsHaptics,
            rumble = if (supportsHaptics) HapticCapability.RumbleSupport.Continuous else HapticCapability.RumbleSupport.None,
            details = listOf(
                "iOS ${UIDevice.currentDevice.systemVersion}",
                "Haptics hardware: " + if (supportsHaptics) "yes" else "none",
                "One-offs: UIKit feedback generators; swells and rumbles: Core Haptics",
            ),
        )
    }

    private val selection by lazy { UISelectionFeedbackGenerator().also { it.prepare() } }
    private val notifications by lazy { UINotificationFeedbackGenerator().also { it.prepare() } }
    private val impacts = mutableMapOf<ImpactStyle, UIImpactFeedbackGenerator>()

    private var engine: CHHapticEngine? = null
    private var running = false

    /** Bumped when the engine resets, so a rumble knows its player went with it. */
    var generation = 0
        private set

    /** Every rumble playing, whichever instance started it, to stop in the background. */
    val playing = mutableSetOf<IosRumble>()

    private var observing = false

    fun play(plan: ApplePlan) {
        when (plan) {
            ApplePlan.Selection -> selection.run {
                selectionChanged()
                prepare()
            }
            is ApplePlan.Impact -> impact(plan.style).run {
                impactOccurredWithIntensity(plan.intensity.toDouble())
                prepare()
            }
            is ApplePlan.Notification -> notifications.run {
                notificationOccurred(
                    when (plan.type) {
                        NotificationType.Success -> UINotificationFeedbackType.UINotificationFeedbackTypeSuccess
                        NotificationType.Warning -> UINotificationFeedbackType.UINotificationFeedbackTypeWarning
                        NotificationType.Error -> UINotificationFeedbackType.UINotificationFeedbackTypeError
                    },
                )
                prepare()
            }
            is ApplePlan.Events -> playEvents(plan.events)
        }
    }

    fun playEvents(events: List<AppleEvent>) {
        if (events.isEmpty()) return
        val engine = startedEngine() ?: return
        val pattern = pattern(events) ?: return
        val player = engine.createPlayerWithPattern(pattern, null) ?: return
        player.startAtTime(CHHapticTimeImmediate, null)
    }

    /** The engine, started — made the first time, and started again whenever the system stopped it. */
    fun startedEngine(): CHHapticEngine? {
        observeBackground()
        val engine = engine ?: runCatching { CHHapticEngine(andReturnError = null) }.getOrNull()?.also { made ->
            made.playsHapticsOnly = true
            made.autoShutdownEnabled = true
            made.stoppedHandler = { _ -> onMain { running = false } }
            made.resetHandler = {
                onMain {
                    running = false
                    generation++
                }
            }
            engine = made
        } ?: return null
        if (!running) running = engine.startAndReturnError(null)
        return engine.takeIf { running }
    }

    /**
     * A background app's engine is stopped by the system; a rumble that thought it
     * was playing would come back as nothing, so they all stop on the way out.
     */
    private fun observeBackground() {
        if (observing) return
        observing = true
        NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIApplicationDidEnterBackgroundNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { _ ->
            playing.toList().forEach { it.stop() }
            running = false
        }
    }

    private fun impact(style: ImpactStyle): UIImpactFeedbackGenerator = impacts.getOrPut(style) {
        UIImpactFeedbackGenerator(
            style = when (style) {
                ImpactStyle.Light -> UIImpactFeedbackStyle.UIImpactFeedbackStyleLight
                ImpactStyle.Medium -> UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium
                ImpactStyle.Heavy -> UIImpactFeedbackStyle.UIImpactFeedbackStyleHeavy
                ImpactStyle.Soft -> UIImpactFeedbackStyle.UIImpactFeedbackStyleSoft
                ImpactStyle.Rigid -> UIImpactFeedbackStyle.UIImpactFeedbackStyleRigid
            },
        ).also { it.prepare() }
    }
}

/** A Core Haptics pattern of [events], or null if Core Haptics would not make one. */
private fun pattern(events: List<AppleEvent>): CHHapticPattern? = runCatching {
    // Named arguments: the two constructors with this shape differ only in
    // whether the second list is of parameters or of parameter curves.
    CHHapticPattern(events = events.map(::event), parameters = emptyList<Any>(), error = null)
}.getOrNull()

private fun event(e: AppleEvent): CHHapticEvent {
    val parameters = listOf(
        CHHapticEventParameter(parameterID = CHHapticEventParameterIDHapticIntensity, value = e.intensity),
        CHHapticEventParameter(parameterID = CHHapticEventParameterIDHapticSharpness, value = e.sharpness),
    )
    return if (e.continuous) {
        CHHapticEvent(
            eventType = CHHapticEventTypeHapticContinuous,
            parameters = parameters,
            relativeTime = e.startSeconds,
            duration = e.durationSeconds,
        )
    } else {
        CHHapticEvent(eventType = CHHapticEventTypeHapticTransient, parameters = parameters, relativeTime = e.startSeconds)
    }
}

/**
 * One continuous event, looping, with its intensity and sharpness steered by the
 * two dynamic parameters — which is how Core Haptics changes a haptic already
 * playing without restarting it.
 */
private class IosRumble(timeout: Duration, private val onStopped: (IosRumble) -> Unit) : Rumble {
    private val leaseSeconds = timeout.inWholeMilliseconds / 1000.0
    private var player: CHHapticAdvancedPatternPlayerProtocol? = null
    private var generation = -1
    private var intensity = 0f
    private var sharpness = 0f
    private var sentAt = 0.0
    private var trailing = false
    private var leaseEnds = 0.0
    private var leaseWatched = false

    override var isActive: Boolean = false
        private set

    fun start(intensity: Float, sharpness: Float) {
        this.intensity = intensity
        this.sharpness = sharpness
        isActive = true
        AppleHaptics.playing += this
        onMain {
            renewLease()
            begin()
        }
    }

    override fun update(intensity: Float, sharpness: Float) {
        if (!isActive) return
        this.intensity = unit(intensity)
        this.sharpness = unit(sharpness)
        onMain {
            renewLease()
            send()
        }
    }

    override fun stop() {
        if (!isActive) return
        isActive = false
        AppleHaptics.playing -= this
        onStopped(this)
        onMain {
            runCatching { player?.stopAtTime(CHHapticTimeImmediate, null) }
            player = null
        }
    }

    private fun begin() {
        if (!isActive) return
        val engine = AppleHaptics.startedEngine() ?: return
        val loop = pattern(listOf(AppleEvent(0.0, continuous = true, durationSeconds = LoopSeconds, intensity = 1f, sharpness = 0.5f)))
            ?: return
        val made = engine.createAdvancedPlayerWithPattern(loop, null) ?: return
        made.loopEnabled = true
        player = made
        generation = AppleHaptics.generation
        sendNow()
        made.startAtTime(CHHapticTimeImmediate, null)
    }

    /** At most about sixty a second: the rest are folded into the next. */
    private fun send() {
        if (!isActive) return
        if (player == null || generation != AppleHaptics.generation) {
            begin()
            return
        }
        val since = now() - sentAt
        if (since >= SendEverySeconds) {
            sendNow()
        } else if (!trailing) {
            trailing = true
            after(SendEverySeconds - since) {
                trailing = false
                if (isActive) sendNow()
            }
        }
    }

    private fun sendNow() {
        val (intensityControl, sharpnessControl) = appleRumbleControls(intensity, sharpness)
        player?.sendParameters(
            parameters = listOf(
                CHHapticDynamicParameter(
                    parameterID = CHHapticDynamicParameterIDHapticIntensityControl,
                    value = intensityControl,
                    relativeTime = 0.0,
                ),
                CHHapticDynamicParameter(
                    parameterID = CHHapticDynamicParameterIDHapticSharpnessControl,
                    value = sharpnessControl,
                    relativeTime = 0.0,
                ),
            ),
            atTime = CHHapticTimeImmediate,
            error = null,
        )
        sentAt = now()
    }

    /**
     * Pushes the end of the lease back, with one check waiting for it at a time.
     *
     * It used to schedule a check per renewal, and a renewal comes with every
     * update — sixty a second through a drag, each leaving a block on the main
     * queue to wake up a timeout later and find the lease already renewed. Now
     * the one check that is waiting, on waking early, waits out the rest of
     * the lease and looks again; the rumble still stops the moment it lapses.
     */
    private fun renewLease() {
        leaseEnds = now() + leaseSeconds
        if (!leaseWatched) watchLease(leaseSeconds)
    }

    private fun watchLease(seconds: Double) {
        leaseWatched = true
        after(seconds) {
            leaseWatched = false
            if (isActive) {
                val left = leaseEnds - now()
                if (left <= 0.0) stop() else watchLease(left)
            }
        }
    }

    private companion object {
        /** A loop long enough that its seam is rare; it repeats for as long as the rumble lasts. */
        const val LoopSeconds = 30.0
        const val SendEverySeconds = 1.0 / 60.0
    }
}

private fun now(): Double = NSProcessInfo.processInfo.systemUptime

private fun onMain(block: () -> Unit) {
    if (NSThread.isMainThread) block() else dispatch_async(dispatch_get_main_queue(), block)
}

private fun after(seconds: Double, block: () -> Unit) {
    dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (seconds * 1_000_000_000).toLong()), dispatch_get_main_queue(), block)
}
