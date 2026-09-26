package io.kontour.haptics

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

/**
 * The haptics of this computer: a Mac's Force Touch trackpad, and nothing
 * anywhere else.
 *
 * The trackpad has three patterns and no strength, and plays only while a finger
 * is on it — which, for feedback on something the pointer is doing, it is. It is
 * reached through Java's foreign-function API, which is Java 22's; on an older
 * JVM, on Windows and on Linux this plays nothing and says why in
 * [HapticCapability.details].
 *
 * Java prints a warning the first time native code is called unless the app is
 * run with `--enable-native-access=ALL-UNNAMED`. A minified build has to keep
 * `io.kontour.haptics.MacTrackpadFfm`, which is loaded by name — the rule ships
 * in this library's jar for ProGuard to find.
 */
fun Haptics(): Haptics {
    val route = desktopRoute(System.getProperty("os.name"), Runtime.version().feature())
    val actuator = if (route == DesktopRoute.MacTrackpad) loadTrackpad() else null
    val reason = when {
        actuator != null -> "Mac trackpad, through Java's foreign-function API"
        route == DesktopRoute.MacTrackpad -> "Mac, but the trackpad could not be reached"
        System.getProperty("os.name").orEmpty().startsWith("Mac", ignoreCase = true) ->
            "Mac on Java ${Runtime.version().feature()}; the trackpad needs Java 22"
        else -> "No haptics on ${System.getProperty("os.name")}"
    }
    return DesktopHaptics(actuator, reason)
}

/** Where a desktop's haptics can come from. */
internal enum class DesktopRoute { MacTrackpad, None }

/** A Mac on Java 22 or later has a trackpad to try; nothing else has anything. */
internal fun desktopRoute(osName: String?, javaFeature: Int): DesktopRoute =
    if (osName.orEmpty().startsWith("Mac", ignoreCase = true) && javaFeature >= 22) DesktopRoute.MacTrackpad else DesktopRoute.None

/** Plays an `NSHapticFeedbackPattern` on a trackpad. */
internal fun interface TrackpadActuator {
    fun perform(pattern: MacPattern)
}

/**
 * The class that talks to AppKit, by name. Loaded only on a Mac with Java 22, so
 * an older JVM never has to resolve the foreign-function types it is written in.
 */
internal const val TrackpadClass = "io.kontour.haptics.MacTrackpadFfm"

private fun loadTrackpad(): TrackpadActuator? = try {
    Class.forName(TrackpadClass).getDeclaredConstructor().newInstance() as TrackpadActuator
} catch (_: Throwable) {
    // A missing class, a missing framework, native access refused: all the same
    // to the person holding the trackpad, which is that it does not tap.
    null
}

internal class DesktopHaptics(private val actuator: TrackpadActuator?, private val reason: String) : Haptics {

    private val scheduler: ScheduledExecutorService? by lazy {
        actuator?.let {
            Executors.newSingleThreadScheduledExecutor { task ->
                Thread(task, "kontour-haptics").apply { isDaemon = true }
            }
        }
    }
    private val pending = mutableSetOf<ScheduledFuture<*>>()
    private val rumbles = mutableSetOf<TrackpadRumble>()
    private var closed = false

    override val capability: HapticCapability
        get() = if (actuator != null) {
            HapticCapability(
                level = HapticCapability.Level.Basic,
                honoursStrength = false,
                rumble = HapticCapability.RumbleSupport.Pulsed,
                details = listOf(reason, "Plays only while a finger is on the trackpad"),
            )
        } else {
            HapticCapability.None.copy(details = listOf(reason))
        }

    override fun play(effect: HapticEffect) {
        if (!closed && effect.audible) schedule(macPlan(effect))
    }

    override fun play(pattern: HapticPattern) {
        if (!closed) schedule(macPlan(pattern))
    }

    override fun startRumble(intensity: Float, sharpness: Float, timeout: Duration): Rumble {
        val scheduler = scheduler
        if (closed || actuator == null || scheduler == null) return StoppedRumble
        return TrackpadRumble(scheduler, actuator, unit(intensity), timeout.inWholeMilliseconds).also {
            synchronized(rumbles) { rumbles += it }
        }
    }

    override fun cancel() {
        synchronized(pending) {
            pending.forEach { it.cancel(false) }
            pending.clear()
        }
        synchronized(rumbles) { rumbles.toList() }.forEach { it.stop() }
    }

    override fun close() {
        if (closed) return
        cancel()
        closed = true
        if (actuator != null) scheduler?.shutdownNow()
    }

    private fun schedule(plan: List<Pair<Int, MacPattern>>) {
        val actuator = actuator ?: return
        plan.forEach { (offset, pattern) ->
            if (offset <= 0) {
                perform(actuator, pattern)
            } else {
                val future = scheduler?.schedule({ perform(actuator, pattern) }, offset.toLong(), TimeUnit.MILLISECONDS)
                // The ones already played are dropped as each new one is added.
                // Nothing else took them out — only `cancel` emptied the set — so
                // every pattern with a second pulse left a finished future behind
                // for the life of the app.
                if (future != null) {
                    synchronized(pending) {
                        pending.removeAll { it.isDone }
                        pending += future
                    }
                }
            }
        }
    }

    private inner class TrackpadRumble(
        scheduler: ScheduledExecutorService,
        private val actuator: TrackpadActuator,
        @Volatile private var intensity: Float,
        private val leaseMillis: Long,
    ) : Rumble {
        @Volatile private var leaseEnds = System.nanoTime() + leaseMillis * 1_000_000
        @Volatile private var nextAt = 0L
        private val tick: ScheduledFuture<*> = scheduler.scheduleAtFixedRate(::step, 0, 10, TimeUnit.MILLISECONDS)

        @Volatile override var isActive: Boolean = true
            private set

        /** Every 10ms: taps when the next one is due, and ends when the lease has run out. */
        private fun step() {
            val t = System.nanoTime()
            if (t >= leaseEnds) {
                stop()
                return
            }
            if (t >= nextAt) {
                perform(actuator, MacPattern.Alignment)
                nextAt = t + macPulseMillis(intensity) * 1_000_000L
            }
        }

        override fun update(intensity: Float, sharpness: Float) {
            this.intensity = unit(intensity)
            leaseEnds = System.nanoTime() + leaseMillis * 1_000_000
        }

        override fun stop() {
            if (!isActive) return
            isActive = false
            tick.cancel(false)
            synchronized(rumbles) { rumbles -= this }
        }
    }
}

private fun perform(actuator: TrackpadActuator, pattern: MacPattern) {
    try {
        actuator.perform(pattern)
    } catch (_: Throwable) {
        // A trackpad that stops answering is not worth an exception in a gesture.
    }
}
