package io.kontour.ui.motion

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import io.kontour.ui.platform.platformScrollFeel
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sign
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * How a scrollable thrown by a finger carries on — the library's own fling, where
 * one is wanted: iOS, by default.
 *
 * Asked for from a phone: on iOS, flings "stop too soon". Compose's own iOS fling
 * already slows at UIKit's rate, so the rate was not the difference. What UIKit
 * has and it does not is **accumulation**: a flick while a list is still moving
 * adds to the speed it already had, so a run of flicks goes faster and further
 * the way a long list on an iPhone does, where each one used to start again from
 * rest. And a **velocity scale**, for a throw that Compose reads slower than UIKit
 * does: 1.2 on iOS, since a phone said flings there still had "a bit of friction",
 * and tunable live on the catalog's diagnostics page.
 *
 * A throw Compose's iOS tracker reads as no speed at all — a flick after a rest —
 * is a separate problem with a separate answer; see `catchRestedFlicks`.
 *
 * @property decelerationRate How much of its speed a fling keeps each
 *   millisecond: UIKit's `UIScrollView.DecelerationRate.normal`, 0.998, by default.
 * @property velocityScale What a release's velocity is multiplied by.
 * @property accumulates Whether a flick while still flinging the same way adds
 *   to the speed left over.
 */
@Immutable
data class ScrollFeel(
    val decelerationRate: Float = UIKitDecelerationRate,
    val velocityScale: Float = 1f,
    val accumulates: Boolean = true,
) {
    companion object {
        /** `UIScrollView.DecelerationRate.normal`: 0.998 of the speed kept each millisecond. */
        const val UIKitDecelerationRate: Float = 0.998f
    }
}

/**
 * The fling the library's scrollables use, or null for the platform's own.
 *
 * iOS's by default, and null everywhere else, where the platform's fling is what
 * its users' hands know. Provide one to tune it — the catalog's diagnostics page
 * does, live — or to give another platform the same.
 */
val LocalScrollFeel = staticCompositionLocalOf<ScrollFeel?> { platformScrollFeel }

/** How a scroller in the library moves when it is let go. */
object ScrollDefaults {
    /**
     * The fling for a scrollable: [LocalScrollFeel]'s where there is one, the
     * platform's own where not.
     *
     * Every scroller in the library that does not snap uses it, and so should an
     * app's own lists, for the same feel: Compose has no way to change the fling
     * of every list at once.
     *
     * ```kotlin
     * LazyColumn(flingBehavior = ScrollDefaults.flingBehavior()) { … }
     * ```
     */
    @Composable
    fun flingBehavior(): FlingBehavior {
        val feel = LocalScrollFeel.current ?: return ScrollableDefaults.flingBehavior()
        val density = LocalDensity.current.density
        return remember(feel, density) { AccumulatingFling(feel, density) }
    }
}

/**
 * A fling that slows at [ScrollFeel.decelerationRate], and adds a new throw to
 * one it cut short.
 *
 * A finger landing on a moving list cancels its fling; the speed it had then is
 * kept, and if the release that follows throws the same way, soon enough and
 * hard enough to be another flick rather than a stop, the two add.
 */
internal class AccumulatingFling(
    private val feel: ScrollFeel,
    private val density: Float,
    private val clock: TimeSource = TimeSource.Monotonic,
) : FlingBehavior {
    private val decay = exponentialDecay<Float>(
        frictionMultiplier = (-ln(feel.decelerationRate.coerceIn(0.9f, 0.9999f)) * 1000f) / ExponentialDecayFriction,
        absVelocityThreshold = StopBelow,
    )

    private var carried = 0f
    private var carriedAt: TimeMark? = null

    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        var velocity = initialVelocity * feel.velocityScale
        val left = carried
        val since = carriedAt?.elapsedNow()
        carried = 0f
        carriedAt = null
        if (feel.accumulates && left != 0f && since != null && since.inWholeMilliseconds < AccumulateWithinMillis &&
            sign(left) == sign(velocity) && abs(velocity) >= AccumulateFloorDp * density
        ) {
            velocity += left
        }
        // Too slow to be a throw: handed straight back, as Compose's own iOS
        // fling does below the same speed. See [FlingFloor].
        if (abs(velocity) < FlingFloor) return velocity

        var last = 0f
        var remaining = velocity
        try {
            AnimationState(initialValue = 0f, initialVelocity = velocity).animateDecay(decay) {
                val delta = value - last
                val consumed = scrollBy(delta)
                last = value
                remaining = this.velocity
                // At an end: what is left goes to whatever is outside, as a
                // platform fling hands it on.
                if (abs(delta - consumed) > 0.5f) cancelAnimation()
            }
        } catch (cancelled: CancellationException) {
            // A finger landed on it. Kept, for a flick that follows to add to.
            carried = remaining
            carriedAt = clock.markNow()
            throw cancelled
        }
        return remaining
    }

    private companion object {
        /** Compose's exponential decay, `exp(-4.2 × multiplier × t)`. */
        const val ExponentialDecayFriction = 4.2f

        /**
         * Pixels a second under which a fling has stopped: a quarter of a pixel
         * a frame at 120Hz, where nothing is seen to move.
         *
         * It was 1, which is where UIKit's own deceleration is reckoned to end —
         * half a pixel short of where it would rest — and which at this rate is
         * two seconds of sub-pixel creep after a list looks still. That cost
         * more here than in UIKit, because a fling holds more here. The list's
         * overscroll can relax only once its fling returns, and a fling on a
         * list at its end goes on moving the page round it through nested
         * scrolling: an inner list pulled at its top, on the gallery's Frames
         * page, stayed stretched for 4.2s while the page coasted to a stop,
         * against 1.3s on Compose's fling, and a touch in that time cancelled
         * the fling before the stretch could go back at all. What stopping here
         * gives up is 15px of a throw's travel.
         */
        const val StopBelow = 30f

        /**
         * Pixels a second under which a release is not a fling at all: Compose's
         * `CupertinoFlingBehavior`'s own threshold, which this replaces on iOS.
         *
         * Without it every release flung, however slow. A finger that pulled an
         * inner list at its top and drifted off it before letting go flung it at
         * a crawl, and the fling, driving the page round it by nested scrolling,
         * held the list's stretch for as long as it ran — reported as the
         * stretch left pulled. Compose's fling returned at once and the stretch
         * went back.
         */
        const val FlingFloor = 500f

        /** How long after a finger stops a fling a flick may still add to it. */
        const val AccumulateWithinMillis = 600L

        /** A release slower than this is a finger stopping the list, not another flick. */
        const val AccumulateFloorDp = 300f
    }
}
