package io.kontour.ui.components.list

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.kontour.ui.theme.lerpCorners
import kotlin.math.roundToInt

/**
 * A morph between two corner sets, quantised so it is a handful of shapes.
 *
 * A shape whose corner depends on an animated fraction is a **new shape every
 * frame**, and the cost of that is not the allocation. `SquirclePaths` is keyed
 * on the corners a shape resolves to, so a fresh instance mid-morph misses on
 * every frame and rebuilds a path — and where the result feeds a
 * `graphicsLayer`'s `shape`, the layer's shadow blur is re-rasterised with it,
 * sixty times a second, under a finger.
 *
 * Twelve steps is one shape per 8% of the morph, on a change that is a few dp of
 * radius: under the threshold where a reader could see the quantisation if they
 * were looking for it, and thirteen cache entries for the whole animation
 * instead of sixty a second.
 *
 * `overlayBackdrop` reached the same arrangement for the same reason and its
 * `backdropClipShapes` records the measurement. This is that, for the two places
 * in `list/` that animate a corner.
 */
@Composable
internal fun rememberMorphedShape(
    from: CornerBasedShape,
    to: CornerBasedShape,
    fraction: Float,
): CornerBasedShape {
    val steps = remember(from, to) {
        List(MorphSteps + 1) { step -> from.lerpCorners(to, step.toFloat() / MorphSteps) }
    }
    return steps[(fraction * MorphSteps).roundToInt().coerceIn(0, MorphSteps)]
}

/** See [rememberMorphedShape]. The same twelve the backdrop's corner ramp uses. */
private const val MorphSteps = 12
