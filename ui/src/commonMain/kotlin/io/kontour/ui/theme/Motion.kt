package io.kontour.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable

/**
 * A spring described by its two parameters, so it can live in a theme.
 *
 * `SpringSpec` is generic over the value being animated, which makes it awkward
 * to store as a token. This holds the tuning and hands out a typed spec on
 * demand via [spec].
 */
@Immutable
data class SpringToken(val dampingRatio: Float, val stiffness: Float) {
    fun <T> spec(): SpringSpec<T> = spring(dampingRatio = dampingRatio, stiffness = stiffness)
}

/**
 * Durations, easings and springs.
 *
 * Read through [Theme.motion], which resolves [reduceMotion] for you from the
 * OS preference. Components should not check the preference themselves — they
 * should ask for a duration and get an honest answer.
 *
 * ### On reduced motion
 *
 * [reduceMotion] does **not** mean "no animation". A cross-fade is not what
 * triggers vestibular discomfort; large-scale translation, parallax and
 * spinning are. So when it is set, durations collapse toward [fast] and
 * transition presets swap movement for opacity — the interface still
 * communicates that something changed, it just stops flying around to say so.
 * Continuous looping motion (marquee, indeterminate spinners) stops entirely.
 *
 * @property reduceMotion Whether the user has asked for reduced motion, at the
 *   OS level or through an in-app setting.
 */
@Immutable
data class Motion(
    val instant: Int,
    val fast: Int,
    val default: Int,
    val slow: Int,
    val deliberate: Int,
    /** The house easing: a strong ease-out. `cubic-bezier(0.16, 1, 0.3, 1)`. */
    val standard: Easing,
    /** For elements entering the screen — decelerates into place. */
    val enter: Easing,
    /** For elements leaving — accelerates away. */
    val exit: Easing,
    /** Overshoots slightly. For elements that should feel physical. */
    val emphasized: Easing,
    /** Snappy, minimal overshoot. Toggles, thumbs, selection indicators. */
    val springSnappy: SpringToken,
    /** The default spring for size and position changes. */
    val springDefault: SpringToken,
    /** Soft and slow. Sheets and large surfaces. */
    val springGentle: SpringToken,
    /**
     * Deliberately under-damped — it overshoots and settles back.
     *
     * The system's one bit of play. Used on the *return* leg of an interaction:
     * a button springing back after a press, a toggle thumb arriving, a chip
     * popping in. Never on the way *down* into a press — overshoot on the
     * outbound leg reads as mushy and slow, while overshoot on the way back
     * reads as alive. That asymmetry is the whole trick.
     *
     * Suppressed entirely under [reduceMotion]: an overshoot is exactly the kind
     * of unrequested movement the preference exists to stop.
     */
    val springBouncy: SpringToken,
    val reduceMotion: Boolean,
) {
    /** A tween at [default], or at [fast] under reduced motion. */
    fun <T> tweenDefault(): FiniteAnimationSpec<T> =
        tween(durationMillis = if (reduceMotion) fast else default, easing = standard)

    /** A tween at [fast]. */
    fun <T> tweenFast(): FiniteAnimationSpec<T> =
        tween(durationMillis = fast, easing = standard)

    /**
     * A tween at [slow], collapsed to [fast] under reduced motion — use for
     * anything that would otherwise travel a long distance.
     */
    fun <T> tweenSlow(): FiniteAnimationSpec<T> =
        tween(durationMillis = if (reduceMotion) fast else slow, easing = standard)

    /**
     * The spring to use for position and size, degraded to a plain tween under
     * reduced motion so nothing overshoots or bounces.
     */
    fun <T> springOrTween(token: SpringToken = springDefault): FiniteAnimationSpec<T> =
        if (reduceMotion) tweenFast() else token.spec()
}

/**
 * The default motion scale.
 *
 * [standard] is the marketing site's easing verbatim, so a transition in the
 * app and the same transition on the web feel like the same product.
 */
fun kontourMotion(reduceMotion: Boolean): Motion = Motion(
    instant = 0,
    fast = 150,
    default = 220,
    slow = 400,
    deliberate = 600,
    standard = CubicBezierEasing(0.16f, 1f, 0.30f, 1f),
    enter = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f),
    exit = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f),
    emphasized = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f),
    springSnappy = SpringToken(dampingRatio = 0.9f, stiffness = 1400f),
    springDefault = SpringToken(dampingRatio = 0.85f, stiffness = 700f),
    springGentle = SpringToken(dampingRatio = 1f, stiffness = 300f),
    // 0.45 overshoots by a few percent and settles in about two oscillations.
    // Enough to feel alive at a glance; not enough to notice as "bouncing".
    springBouncy = SpringToken(dampingRatio = 0.45f, stiffness = 900f),
    reduceMotion = reduceMotion,
)
