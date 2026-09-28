package io.kontour.haptics

/**
 * What a [Haptics] can play on this device, and how it is playing it.
 *
 * @property richness How rich its haptics are. Not to be confused with a
 *   `HapticsLevel`, which is how much of that richness the user asked for.
 * @property honoursStrength Whether [HapticEffect.strength] changes what is felt
 *   finely. Android's composition primitives, iOS's impacts and Core Haptics do;
 *   Android's feedback constants step between three weights at most, and the
 *   browser and the trackpad not at all.
 * @property rumble What [Haptics.startRumble] plays here.
 * @property details Lines for a person reading them — the platform version, the
 *   route taken and why. Not for parsing.
 */
data class HapticCapability(
    val richness: Richness,
    val honoursStrength: Boolean,
    val rumble: RumbleSupport,
    val details: List<String> = emptyList(),
) {

    /** How rich a device's haptics are. */
    enum class Richness {
        /** Nothing plays: no actuator, no API, or no permission. */
        None,

        /** The system's own feedback, in a handful of fixed kinds. */
        Basic,

        /** Tuned primitives with strength, or Core Haptics. */
        Rich,
    }

    /** What a rumble is on this device. */
    enum class RumbleSupport {
        /** [Haptics.startRumble] plays nothing. */
        None,

        /** A rapid run of the lightest tick, as near to continuous as it gets. */
        Pulsed,

        /** A true continuous vibration. */
        Continuous,
    }

    companion object {
        /** Nothing plays. */
        val None: HapticCapability = HapticCapability(Richness.None, honoursStrength = false, rumble = RumbleSupport.None)
    }
}
