// An `NSError**` is how Core Haptics reports what went wrong, and reading one
// back — or passing `null` to say it will not be read — needs the opt-ins.
@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package io.kontour.haptics

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.cValue
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
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
import platform.Foundation.NSError
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperatingSystemVersion
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSThread
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationDidReceiveMemoryWarningNotification
import platform.UIKit.UIDevice
import platform.UIKit.UIFeedbackGenerator
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackType
import platform.UIKit.UISelectionFeedbackGenerator
import platform.UIKit.UIView
import platform.darwin.DISPATCH_TIME_NOW
import platform.darwin.dispatch_after
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_time
import kotlin.time.Duration

/**
 * How the iOS player plays its one-off effects — for comparing on a phone, not
 * for an app to choose.
 *
 * Kept because haptics were reported to stop for good on iPhones — after a
 * slider ran into its stop, or quick taps through a multi-select — and only a
 * hand on a phone can tell which way of driving the Taptic Engine keeps
 * working. The catalog's haptics page switches between these, with a trace of
 * what each did.
 */
enum class IosHapticsRoute {
    /** [BurstGenerators]: what an app gets. */
    Auto,

    /** UIKit's generators, held for the whole process and prepared after every haptic: the old way. */
    ProcessGenerators,

    /** UIKit's generators, made afresh after a pause and prepared only when made. */
    BurstGenerators,

    /**
     * As [BurstGenerators], but each generator attached to a view, as iOS 17.5
     * added — falling back to [BurstGenerators] before 17.5 or without a view.
     */
    ViewGenerators,

    /** No UIKit generators at all: every one-off as Core Haptics taps, near UIKit's feel. */
    CoreHaptics,
}

/**
 * The haptics of this iPhone.
 *
 * One-off effects are UIKit's feedback generators — selection, impact, and
 * notification — which Apple tuned and which follow the system's haptics
 * switch. Core Haptics plays what UIKit has no word for: the swells, patterns,
 * and rumbles. An iPad has neither, and plays nothing.
 *
 * Generators are kept for a burst of haptics and made afresh after a pause (see
 * [IosHapticsRoute.BurstGenerators]); one haptic engine serves the process,
 * starts the first time something needs it, stops itself when idle, and is made
 * again from scratch if the system resets it or it will not start. What it did
 * is counted in [Haptics.trace].
 *
 * @param route [IosHapticsRoute.Auto] unless comparing routes.
 * @param view The view to attach generators to on [IosHapticsRoute.ViewGenerators].
 */
fun Haptics(route: IosHapticsRoute = IosHapticsRoute.Auto, view: UIView? = null): Haptics = IosHaptics(route, view)

private class IosHaptics(private val route: IosHapticsRoute, view: UIView?) : Haptics {
    private val rumbles = mutableSetOf<IosRumble>()
    private var closed = false
    private val generators: Generators? = when (route) {
        IosHapticsRoute.CoreHaptics -> null
        IosHapticsRoute.ProcessGenerators -> AppleHaptics.processGenerators
        IosHapticsRoute.Auto, IosHapticsRoute.BurstGenerators -> Generators(BurstGapSeconds, view = null, reprepare = false)
        IosHapticsRoute.ViewGenerators -> Generators(BurstGapSeconds, view, reprepare = false)
    }

    init {
        generators?.let { AppleHaptics.live += it }
    }

    override val capability: HapticCapability get() = AppleHaptics.capability

    override val trace: HapticsTrace get() = AppleHaptics.trace

    override fun play(effect: HapticEffect) {
        if (!accepts() || !effect.audible) return
        val plan = applePlan(effect)
        onMain { AppleHaptics.play(plan, generators) }
    }

    override fun play(pattern: HapticPattern) {
        if (!accepts()) return
        val events = appleEvents(pattern)
        onMain { AppleHaptics.playEvents(events) }
    }

    override fun startRumble(intensity: Float, sharpness: Float, timeout: Duration): Rumble {
        if (!accepts()) return StoppedRumble
        val rumble = IosRumble(timeout, onStopped = { rumbles -= it })
        rumbles += rumble
        rumble.start(unit(intensity), unit(sharpness))
        return rumble
    }

    /** Whether a request goes any further, counting the ones that do not. */
    private fun accepts(): Boolean {
        val trace = AppleHaptics.trace
        return when {
            closed -> false.also { trace.count("refused: player closed") }
            !AppleHaptics.supportsHaptics -> false.also { trace.count("refused: no haptics hardware") }
            else -> true
        }
    }

    override fun cancel() {
        rumbles.toList().forEach { it.stop() }
    }

    override fun close() {
        if (closed) return
        cancel()
        closed = true
        generators?.let { if (it !== AppleHaptics.processGenerators) AppleHaptics.live -= it }
        AppleHaptics.trace.note("player closed ($route)", step = "player closed")
    }
}

/** A pause this long ends a burst of haptics; the next one makes new generators. */
private const val BurstGapSeconds = 1.5

private sealed interface GeneratorKey {
    data object Selection : GeneratorKey
    data object Notification : GeneratorKey
    data class Impact(val style: ImpactStyle) : GeneratorKey
}

/** UIKit's generators for one player, under one policy. */
private class Generators(burstGap: Double?, private val view: UIView?, private val reprepare: Boolean) {
    private val cache = GeneratorCache<GeneratorKey, UIFeedbackGenerator>(burstGap, ::now) { key ->
        make(key).also {
            AppleHaptics.trace.count("generator made")
            it.prepare()
            AppleHaptics.trace.count("prepare")
        }
    }

    fun selection() = cache[GeneratorKey.Selection] as UISelectionFeedbackGenerator
    fun notification() = cache[GeneratorKey.Notification] as UINotificationFeedbackGenerator
    fun impact(style: ImpactStyle) = cache[GeneratorKey.Impact(style)] as UIImpactFeedbackGenerator

    /** After a haptic: the old policy prepared again at once, to keep the engine warm. */
    fun fired(generator: UIFeedbackGenerator) {
        if (!reprepare) return
        generator.prepare()
        AppleHaptics.trace.count("prepare")
    }

    fun clear() = cache.clear()

    private fun make(key: GeneratorKey): UIFeedbackGenerator {
        val attached = view?.takeIf { AppleHaptics.viewGeneratorsAvailable }
        return when (key) {
            GeneratorKey.Selection ->
                if (attached != null) UISelectionFeedbackGenerator.feedbackGeneratorForView(attached) else UISelectionFeedbackGenerator()
            GeneratorKey.Notification ->
                if (attached != null) UINotificationFeedbackGenerator.feedbackGeneratorForView(attached) else UINotificationFeedbackGenerator()
            is GeneratorKey.Impact -> {
                val style = uiStyle(key.style)
                if (attached != null) {
                    UIImpactFeedbackGenerator.feedbackGeneratorWithStyle(style, forView = attached)
                } else {
                    UIImpactFeedbackGenerator(style = style)
                }
            }
        }
    }
}

private fun uiStyle(style: ImpactStyle): UIImpactFeedbackStyle = when (style) {
    ImpactStyle.Light -> UIImpactFeedbackStyle.UIImpactFeedbackStyleLight
    ImpactStyle.Medium -> UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium
    ImpactStyle.Heavy -> UIImpactFeedbackStyle.UIImpactFeedbackStyleHeavy
    ImpactStyle.Soft -> UIImpactFeedbackStyle.UIImpactFeedbackStyleSoft
    ImpactStyle.Rigid -> UIImpactFeedbackStyle.UIImpactFeedbackStyleRigid
}

/** What the whole process shares: the engine, the old route's generators, and the trace. */
private object AppleHaptics {

    /** Whether this device has a Taptic Engine at all; iPads do not. */
    val supportsHaptics: Boolean by lazy { CHHapticEngine.capabilitiesForHardware().supportsHaptics }

    /** Whether UIKit can attach a generator to a view: iOS 17.5 and later. */
    val viewGeneratorsAvailable: Boolean by lazy {
        NSProcessInfo.processInfo.isOperatingSystemAtLeastVersion(
            cValue<NSOperatingSystemVersion> {
                majorVersion = 17
                minorVersion = 5
                patchVersion = 0
            },
        )
    }

    val capability: HapticCapability by lazy {
        HapticCapability(
            richness = if (supportsHaptics) HapticCapability.Richness.Rich else HapticCapability.Richness.None,
            honoursStrength = supportsHaptics,
            rumble = if (supportsHaptics) HapticCapability.RumbleSupport.Continuous else HapticCapability.RumbleSupport.None,
            details = listOf(
                "iOS ${UIDevice.currentDevice.systemVersion}",
                "Haptics hardware: " + if (supportsHaptics) "yes" else "none",
                "One-offs: UIKit feedback generators, fresh each burst; swells and rumbles: Core Haptics",
            ),
        )
    }

    /** Everything the iOS players did, for the process. */
    val trace = HapticsTrace()

    /** The old route's generators, which were the process's. */
    val processGenerators = Generators(burstGap = null, view = null, reprepare = true)

    /** Every player's generators, to let go of together when the app comes back or runs short. */
    val live = mutableSetOf<Generators>()

    private var engine: CHHapticEngine? = null
    private var running = false

    /** Bumped when the engine resets or is made again, so a rumble knows its player went with it. */
    var generation = 0
        private set

    /** Every rumble playing, whichever instance started it, to stop in the background. */
    val playing = mutableSetOf<IosRumble>()

    private var observing = false

    fun play(plan: ApplePlan, generators: Generators?) {
        observe()
        if (generators == null) {
            trace.count("core haptics one-off")
            playEvents(appleTransients(plan))
            return
        }
        when (plan) {
            ApplePlan.Selection -> generators.selection().let {
                it.selectionChanged()
                trace.count("selection")
                generators.fired(it)
            }
            is ApplePlan.Impact -> generators.impact(plan.style).let {
                it.impactOccurredWithIntensity(unit(plan.intensity).toDouble())
                trace.count("impact ${plan.style}")
                generators.fired(it)
            }
            is ApplePlan.Notification -> generators.notification().let {
                it.notificationOccurred(
                    when (plan.type) {
                        NotificationType.Success -> UINotificationFeedbackType.UINotificationFeedbackTypeSuccess
                        NotificationType.Warning -> UINotificationFeedbackType.UINotificationFeedbackTypeWarning
                        NotificationType.Error -> UINotificationFeedbackType.UINotificationFeedbackTypeError
                    },
                )
                trace.count("notification ${plan.type}")
                generators.fired(it)
            }
            is ApplePlan.Events -> playEvents(plan.events)
        }
    }

    fun playEvents(events: List<AppleEvent>) {
        if (events.isEmpty()) return
        val engine = startedEngine() ?: return
        val pattern = pattern(events) ?: return trace.count("pattern refused")
        val (player, made) = withError { engine.createPlayerWithPattern(pattern, it) }
        if (player == null) return trace.note("player not made: ${made.describe()}", step = "pattern player failed")
        val (started, error) = withError { player.startAtTime(CHHapticTimeImmediate, it) }
        if (started) trace.count("pattern played") else trace.note("pattern not started: ${error.describe()}", step = "pattern start failed")
    }

    /**
     * The engine, started: made the first time, started again whenever the system
     * stopped it, and made again from scratch after a reset or a start that failed.
     */
    fun startedEngine(): CHHapticEngine? {
        observe()
        val engine = engine ?: makeEngine() ?: return null
        if (!running) {
            val (started, error) = withError { engine.startAndReturnError(it) }
            if (!started) {
                trace.note("engine did not start: ${error.describe()}", step = "engine start failed")
                // Whatever state it is in, it is not one to try again from.
                this.engine = null
                generation++
                return null
            }
            running = true
            trace.count("engine started")
        }
        return engine
    }

    private fun makeEngine(): CHHapticEngine? {
        val (made, error) = withError { e -> runCatching { CHHapticEngine(andReturnError = e) }.getOrNull() }
        if (made == null) {
            trace.note("engine not made: ${error.describe()}", step = "engine make failed")
            return null
        }
        trace.count("engine made")
        made.playsHapticsOnly = true
        made.autoShutdownEnabled = true
        made.stoppedHandler = { reason ->
            onMain {
                running = false
                trace.note("engine stopped (reason $reason)", step = "engine stopped")
            }
        }
        made.resetHandler = {
            onMain {
                // Apple's advice is to start it again and remake its players; making the
                // engine itself again is the same with less to get wrong.
                running = false
                if (engine === made) engine = null
                generation++
                dropGenerators("engine reset")
                trace.note("engine reset", step = "engine reset")
            }
        }
        engine = made
        generation++
        return made
    }

    fun dropGenerators(why: String) {
        live.forEach { it.clear() }
        processGenerators.clear()
        trace.note("generators dropped: $why", step = "generators dropped")
    }

    /**
     * A background app's engine is stopped by the system; a rumble that thought it
     * was playing would come back as nothing, so they all stop on the way out. On
     * the way back in, and when memory runs short, every generator is let go.
     */
    private fun observe() {
        if (observing) return
        observing = true
        val centre = NSNotificationCenter.defaultCenter
        centre.addObserverForName(UIApplicationDidEnterBackgroundNotification, `object` = null, queue = NSOperationQueue.mainQueue) { _ ->
            playing.toList().forEach { it.stop() }
            running = false
            trace.count("app to background")
        }
        centre.addObserverForName(UIApplicationDidBecomeActiveNotification, `object` = null, queue = NSOperationQueue.mainQueue) { _ ->
            dropGenerators("app became active")
        }
        centre.addObserverForName(UIApplicationDidReceiveMemoryWarningNotification, `object` = null, queue = NSOperationQueue.mainQueue) { _ ->
            dropGenerators("memory warning")
        }
    }
}

/** [block]'s result, and the error it wrote, if it wrote one. */
private inline fun <T> withError(block: (CPointer<ObjCObjectVar<NSError?>>) -> T): Pair<T, NSError?> = memScoped {
    val error = alloc<ObjCObjectVar<NSError?>>()
    val result = block(error.ptr)
    result to error.value
}

private fun NSError?.describe(): String = this?.let { "${it.domain} ${it.code}: ${it.localizedDescription}" } ?: "no error given"

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
        val trace = AppleHaptics.trace
        val engine = AppleHaptics.startedEngine() ?: return
        val loop = pattern(listOf(AppleEvent(0.0, continuous = true, durationSeconds = LoopSeconds, intensity = 1f, sharpness = 0.5f)))
            ?: return trace.count("rumble pattern refused")
        val (made, error) = withError { engine.createAdvancedPlayerWithPattern(loop, it) }
        if (made == null) return trace.note("rumble player not made: ${error.describe()}", step = "rumble player failed")
        made.loopEnabled = true
        player = made
        generation = AppleHaptics.generation
        sendNow()
        val (started, startError) = withError { made.startAtTime(CHHapticTimeImmediate, it) }
        if (started) trace.count("rumble started") else trace.note("rumble not started: ${startError.describe()}", step = "rumble start failed")
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
