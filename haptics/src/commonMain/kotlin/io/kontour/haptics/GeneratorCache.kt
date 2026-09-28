package io.kontour.haptics

/**
 * One platform generator per [K], kept for a burst of haptics and made afresh
 * for the next.
 *
 * iOS's feedback generators are objects the app holds, and the iOS player used
 * to hold one of each for the whole process, re-preparing it after every haptic.
 * Reported on iPhones: after a slider ran into its stop, or a run of quick
 * toggles, haptics stopped for good — until the app was reopened. A generator
 * that has gone wrong has no way to say so, so the policy is not to trust one
 * for long: a pause of [burstGapSeconds] ends a burst, and the next haptic
 * makes new ones. Within a burst — a drag, a run of taps — the same generator
 * answers every time, which is what keeps its latency low.
 *
 * `null` for [burstGapSeconds] keeps each one for good: the old policy, kept as
 * a route to compare against on a phone.
 *
 * @param now Seconds on a monotonic clock.
 * @param make A new generator for a key; called at most once per key per burst.
 */
internal class GeneratorCache<K, G : Any>(
    private val burstGapSeconds: Double?,
    private val now: () -> Double,
    private val make: (K) -> G,
) {
    private val held = linkedMapOf<K, G>()
    private var lastUsed = Double.NaN

    /** How many generators are held now. */
    val size: Int get() = held.size

    /** The generator for [key], made now if this is a new burst or it has none yet. */
    operator fun get(key: K): G {
        val at = now()
        val gap = burstGapSeconds
        if (gap != null && !lastUsed.isNaN() && at - lastUsed > gap) held.clear()
        lastUsed = at
        return held.getOrPut(key) { make(key) }
    }

    /** Lets go of every generator: the next haptic starts a burst of new ones. */
    fun clear() {
        held.clear()
        lastUsed = Double.NaN
    }
}
