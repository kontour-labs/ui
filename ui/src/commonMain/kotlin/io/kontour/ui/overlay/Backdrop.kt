package io.kontour.ui.overlay

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import io.kontour.ui.platform.platformSupportsBackdropBlur
import io.kontour.ui.theme.LocalBackdropBlur
import io.kontour.ui.theme.Theme

/**
 * What an overlay does to the content behind it, beyond dimming it.
 *
 * A modal that only darkens what is behind it says "ignore that"; one that also
 * takes the detail out of it says "you cannot read that anyway", which is the
 * truer statement and the one that lets the scrim be lighter. The two together
 * separate better than either alone at twice the strength.
 *
 * **Follows the scrim.** The default is [Blur] for anything that dims and
 * [None] for anything that does not, which is why menus, tooltips and toasts get
 * nothing: they sit *over* content the user is still reading, and blurring the
 * page behind a dropdown would be both expensive and a lie about what is
 * dismissable. If it was not worth dimming, it is not worth blurring.
 */
enum class BackdropStyle {
    /** Nothing. The content behind is drawn as it is. */
    None,

    /** The content behind blurs as the overlay arrives. Dialogs, the palette. */
    Blur,

    /**
     * Blurred, and pushed back the way a card slides under the one in front.
     *
     * For sheets, which cover part of the screen rather than floating in the
     * middle of it — the presenting content receding is what says the sheet is
     * *on top of* this screen rather than a new one.
     */
    BlurAndScale,
}

/** Numbers behind [BackdropStyle]. */
object BackdropDefaults {

    /**
     * How far the content behind is blurred once an overlay is fully in.
     *
     * Larger than `GlassSurface`'s 14dp, which is tuned for a small panel over a
     * busy background. Across a whole screen a radius that size reads as a
     * smudge on the glass rather than as distance.
     */
    val BlurRadius: Dp = 24.dp

    /** How far back the presenting content sits under a sheet. */
    const val ScaleBack: Float = 0.94f
}

/**
 * Blurs, and optionally pushes back, everything drawn inside this node while an
 * overlay above it asks for it.
 *
 * ### Why this is a backdrop filter when `GlassSurface`'s is not
 *
 * `Modifier.blur` blurs a layer's own content, not what is behind it, and there
 * is no portable equivalent of `backdrop-filter`. For a bar floating over a live
 * map that is a real wall — the bar is a sibling *above* the map, so blurring it
 * blurs the bar. `GlassSurface` documents the workaround and its cost.
 *
 * A modal is the other case, and it is not the same problem at all. `OverlayHost`
 * composes the app content as a single full-size sibling in the same render tree
 * as the overlay stack, so the thing behind the modal *is* one node — and
 * blurring a node's own content is precisely what `Modifier.blur` does. Nothing
 * is composed twice and nothing is handed in by the caller.
 *
 * ### Read in the layer phase, not in composition
 *
 * The fraction changes every frame. Reading it during composition would
 * recompose the content — the entire application — on each one, which is the
 * single worst thing this modifier could do. So it is a lambda pulled from
 * snapshot state inside the `graphicsLayer` block, the same shape of answer
 * `Scrim` already uses for the dim it is matching.
 */
@Composable
internal fun Modifier.overlayBackdrop(state: OverlayHostState, style: BackdropStyle): Modifier {
    if (style == BackdropStyle.None) return this

    val radiusPx = with(LocalDensity.current) { BackdropDefaults.BlurRadius.toPx() }
    val blurring = LocalBackdropBlur.current && platformSupportsBackdropBlur
    val scaling = style == BackdropStyle.BlurAndScale
    val clipShape: Shape = Theme.shapes.extraLarge
    if (!blurring && !scaling) return this

    return graphicsLayer {
        val f = (state.backdropFraction?.invoke() ?: 0f).coerceIn(0f, 1f)

        renderEffect = if (blurring && f > 0f) {
            // Grown with the fraction rather than switched on, so the screen
            // softens as the panel arrives instead of going out of focus a frame
            // before it appears.
            val radius = radiusPx * f
            BlurEffect(radiusX = radius, radiusY = radius)
        } else {
            null
        }

        if (scaling) {
            val scale = lerp(1f, BackdropDefaults.ScaleBack, f)
            scaleX = scale
            scaleY = scale
            shape = clipShape
            clip = f > 0f
        }
    }
}

/**
 * Fills the band a receding sheet leaves around the content.
 *
 * Content scaled to 94% pulls away from every edge, and what shows through is
 * whatever is under the host — usually the window's own background, usually the
 * same colour the content was, so the recession reads as nothing at all. Black
 * behind it is what makes it read as depth, and it is what iOS puts there.
 *
 * **Only the band.** The obvious implementation — fill the host and let the
 * content draw over it — is wrong, and wrong in a way that is invisible until it
 * is catastrophic: nothing requires the app's content to be opaque, and where it
 * is not, the ground shows through the middle and the screen goes black. So this
 * cuts the content's own shape out of the fill with an even-odd path, the way
 * [io.kontour.ui.overlay.Coachmark]'s spotlight cuts its hole, and covers
 * exactly the pixels the content has vacated.
 *
 * Drawn on the *host*, before its children, rather than under the content layer
 * — anything inside that layer is scaled and blurred along with everything else.
 */
@Composable
internal fun Modifier.backdropGround(state: OverlayHostState, style: BackdropStyle): Modifier {
    if (style != BackdropStyle.BlurAndScale) return this

    val clipShape: Shape = Theme.shapes.extraLarge
    val geometry = remember { GroundGeometry() }

    return drawBehind {
        val f = (state.backdropFraction?.invoke() ?: 0f).coerceIn(0f, 1f)
        if (f <= 0f) return@drawBehind

        // The hole is the content's own outline at **full** size under the same
        // transform the content's layer applies to itself.
        //
        // A `graphicsLayer`'s clip is resolved in the layer's own coordinates and
        // then scaled with everything else in it — measured, not assumed: a 400dp
        // box clipped to a 40dp corner in a layer at half scale comes out 200px
        // wide with a 20px corner. So the content's corner on screen is 32dp times
        // the scale-back, and this used to cut a 32dp one: the hole was fractionally
        // *tighter* than the content in each corner, and the band's black showed
        // through the content's antialiased edge there. Nothing was ever missing —
        // the corners just blended a shade darker than the straight edges.
        //
        // It is also the difference between building a squircle once and building
        // one per frame: the shrunken size changes every frame, so `createOutline`
        // missed the shape's path cache on each one *and* evicted the entries
        // every other container on screen was using.
        if (geometry.size != size) {
            geometry.hole.reset()
            geometry.hole.addOutline(clipShape.createOutline(size, layoutDirection, this))
            geometry.size = size
        }

        val scale = lerp(1f, BackdropDefaults.ScaleBack, f)
        geometry.matrix.reset()
        geometry.matrix.translate(size.width / 2f, size.height / 2f)
        geometry.matrix.scale(scale, scale)
        geometry.matrix.translate(-size.width / 2f, -size.height / 2f)

        // Only the hole scales. The band's outer rect is the host, which does
        // not move, so the transform cannot be applied to the combined path.
        geometry.scaled.reset()
        geometry.scaled.addPath(geometry.hole)
        geometry.scaled.transform(geometry.matrix)

        geometry.band.reset()
        geometry.band.fillType = PathFillType.EvenOdd
        geometry.band.addRect(Rect(Offset.Zero, size))
        geometry.band.addPath(geometry.scaled)

        drawPath(geometry.band, Color.Black, alpha = f)
    }
}

/**
 * The ground's scratch geometry, kept across frames.
 *
 * Scratch held across frames rather than allocated inside one, for a modifier
 * that runs on every frame of every sheet animation. [hole] is the expensive
 * one — a squircle is four corners of trigonometry and twelve cubics — and is
 * rebuilt only when the host resizes; the rest are rewritten in place.
 */
private class GroundGeometry {
    /** The content's outline at full size. Rebuilt only when the host resizes. */
    val hole = Path()

    /** [hole] under this frame's scale. */
    val scaled = Path()

    /** The host, with [scaled] cut out of it. */
    val band = Path()

    val matrix = Matrix()
    var size: Size? = null
}
