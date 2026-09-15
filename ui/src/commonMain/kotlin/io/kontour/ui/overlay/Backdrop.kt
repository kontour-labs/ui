package io.kontour.ui.overlay

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
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
import androidx.compose.ui.graphics.TileMode
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
     * Pushed back the way a card slides under the one in front, and not blurred.
     *
     * What sheets use. The recede is the part that carries the meaning — see
     * [BlurAndScale], whose KDoc made this argument before there was a style
     * that acted on it — and the blur is a softening laid over the top of it.
     *
     * The softening is not free. Measured by `ThemeSwitchCostDiagnostic`, a
     * blurred backdrop costs **7.3x the whole rest of the frame**, and it is
     * paid on every frame the overlay is open rather than only while it arrives:
     * a full-screen offscreen render, at a radius the same diagnostic shows
     * barely matters to the price. That is a fair trade for a dialog, which is
     * on screen for one decision. It is a poor one for a sheet somebody sits in
     * flipping switches, which is where it was reported from.
     *
     * So a sheet recedes and does not blur. It still dims, it still scales, and
     * the band around it is still filled — everything that says *behind* is
     * intact.
     */
    Scale,

    /**
     * Blurred, and pushed back the way a card slides under the one in front.
     *
     * For sheets, which cover part of the screen rather than floating in the
     * middle of it — the presenting content receding is what says the sheet is
     * *on top of* this screen rather than a new one.
     *
     * No longer what [io.kontour.ui.sheet.ModalBottomSheet] asks for; [Scale]
     * is. Kept because it is still the right answer for a sheet over something
     * visually busy enough that dimming alone leaves it legible and distracting,
     * and because withdrawing a public variant to make a performance point would
     * be charging the wrong people for it.
     */
    BlurAndScale,
    ;

    /** Whether this style softens what is behind it. */
    internal val blurs: Boolean get() = this == Blur || this == BlurAndScale

    /** Whether this style pushes what is behind it away. */
    internal val scales: Boolean get() = this == Scale || this == BlurAndScale
}

/**
 * How far the ground's hole is drawn inside the content it is cut for, in
 * pixels.
 *
 * One device pixel, which is the width of the antialiased boundary the two
 * share. Not a `Dp`: this is not a design measure but the size of a rasteriser's
 * seam, and it is the same one pixel on every density.
 */
private const val SeamOverlap = 1f

/**
 * How far the blur's softened edge reaches inside the content it is applied to,
 * as a multiple of the blur radius.
 *
 * **Measured, not chosen.** At a 48px radius the content's own colour is not
 * reached again until roughly 68px inside its edge, and that ratio is what a
 * Gaussian's tail gives: Compose's `BlurEffect` radius is two standard
 * deviations, and a blur is done at three. 1.5 is that, rounded up, and it is
 * checked by the same measurement that found the halo — a ring cut too shallow
 * puts the leak straight back and `theBlurredEdgeDoesNotShowThePageBehindIt`
 * says so with the number.
 */
private const val HaloReach = 1.5f

/** Numbers behind [BackdropStyle]. */
object BackdropDefaults {

    /**
     * How far the content behind is blurred once an overlay is fully in.
     *
     * Larger than `GlassSurface`'s 14dp, which is tuned for a small panel over a
     * busy background. Across a whole screen a radius that size reads as a
     * smudge on the glass rather than as distance.
     */
    val BlurRadius: Dp
        @Composable @ReadOnlyComposable get() = Theme.componentDefaults.backdropBlurRadius

    /** How far in from the screen's edge the presenting content sits under a sheet. */
    val Inset: Dp
        @Composable @ReadOnlyComposable get() = Theme.componentDefaults.backdropInset
}

/**
 * The transform that insets the content behind a modal by a uniform margin.
 *
 * ### Why a uniform scale could not do it
 *
 * Scaling a rectangle insets it by a fraction of each *axis*, so one number
 * gives two different margins on any screen that is not square: `0.94` on a
 * 390x844 phone left 11.7dp at the sides and 25.3dp at the top. That is what was
 * reported — the receded screen read as having a gap above it rather than a
 * frame around it.
 *
 * ### Why not simply scale each axis separately
 *
 * Because that distorts. Mapping 390x844 into a 12dp frame needs 0.938 across
 * and 0.972 down, and a 3.5% difference between them turns every avatar into a
 * slight ellipse. A placeholder for a screen should not be the wrong shape.
 *
 * ### What this does instead
 *
 * Scales **uniformly, from the width**, so both side margins are exactly the
 * inset, and then moves the content *up* by whatever vertical slack that leaves
 * over. The top margin lands on the inset too, and the surplus collects at the
 * bottom — which is the one edge a bottom sheet is covering anyway. Three edges
 * uniform and the fourth hidden, with no distortion, which is the trade iOS
 * makes.
 *
 * Up, not down, and the first version had it the other way round. A uniform
 * scale leaves the same slack top and bottom; putting the *top* on the inset
 * means closing the difference, which moves the content toward the top of the
 * screen. Translating down instead doubled the top margin — 48px where 24 was
 * wanted, on a 600x900 window — and `BackdropBlurTest` caught it by sampling a
 * row that the content had stopped covering.
 *
 * On a window wider than it is tall there is no surplus to move — the slack from
 * a width-derived scale is smaller than the inset — so the translation clamps at
 * zero and the top settles wherever the uniform scale puts it. A modal over a
 * landscape window is a centred dialog rather than a bottom sheet, so this is
 * the case that matters least.
 */
internal fun backdropScale(width: Float, insetPx: Float): Float =
    if (width <= 0f) 1f else (1f - 2f * insetPx / width).coerceIn(0f, 1f)

/**
 * How far to move the content *up* to put the top margin on the inset too.
 *
 * A magnitude, so both call sites negate it. See [backdropScale], which argues
 * the direction at length — a uniform scale leaves equal slack top and bottom,
 * and closing the top down to the inset moves the content toward the top.
 */
internal fun backdropShift(height: Float, scale: Float, insetPx: Float): Float =
    ((1f - scale) * height / 2f - insetPx).coerceAtLeast(0f)

/**
 * [BackdropDefaults.Inset], or no inset at all when the reader has asked for
 * reduced motion.
 *
 * **`Motion`'s helpers cannot express this.** `tweenDefault` and `tweenSlow`
 * shorten a movement, `springOrTween` stops one overshooting; none of them can
 * make a movement *smaller*. Anything whose objection is "it moves too far"
 * rather than "it takes too long" has to read the preference itself, and until
 * this the whole of `overlay/`, `nav/`, `sheet/` and `adaptive/` read it
 * nowhere.
 *
 * This is the largest instance in the library by a wide margin: opening any
 * sheet scales the **entire viewport**, and during a drag the screen scales
 * continuously under the finger with no spec anywhere in the path to shorten.
 * Reported from a phone.
 *
 * It is also an inconsistency rather than a judgement call, which is what
 * settles it: `Transitions.fadeThrough` already drops its
 * `scaleIn(initialScale = 0.94f)` under the same preference, and `Indication`
 * drops the press-shrink. The same 0.94, gated in one place and not the other.
 *
 * **The blur stays.** A blur does not move, and the reason a sheet blurs what is
 * behind it — separating the panel from the page — survives the preference
 * intact. What goes is the travel.
 */
@Composable
@ReadOnlyComposable
private fun resolvedInset(): Dp =
    if (Theme.motion.reduceMotion) 0.dp else BackdropDefaults.Inset

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
    val blurring = style.blurs && LocalBackdropBlur.current && platformSupportsBackdropBlur
    val clipShape: Shape = Theme.shapes.extraLarge
    // Read here rather than in the lambda below: `graphicsLayer` runs at draw
    // time, and a theme value has to be captured in composition.
    val insetPx = with(LocalDensity.current) { resolvedInset().toPx() }
    val scaling = style.scales && insetPx > 0f
    if (!blurring && !scaling) return this

    return graphicsLayer {
        val f = (state.backdropFraction?.invoke() ?: 0f).coerceIn(0f, 1f)

        renderEffect = if (blurring && f > 0f) {
            // Grown with the fraction rather than switched on, so the screen
            // softens as the panel arrives instead of going out of focus a frame
            // before it appears.
            val radius = radiusPx * f
            // `TileMode.Clamp`, and it is the other half of the reported white
            // flash. A blur samples beyond what it is blurring, and left to
            // itself it treats everything outside as *transparent* — so the
            // layer's own edge fades out over the blur radius, and this layer's
            // edge is the whole screen. Inset by `Inset`, that fade
            // lands exactly where the reporter saw it: a soft halo hugging the
            // receding content, showing whatever the app is sitting on. Clamping
            // extends the edge pixels instead, so the content stays opaque to
            // its own boundary.
            BlurEffect(radiusX = radius, radiusY = radius, edgeTreatment = TileMode.Clamp)
        } else {
            null
        }

        if (scaling) {
            val target = backdropScale(size.width, insetPx)
            val scale = lerp(1f, target, f)
            scaleX = scale
            scaleY = scale
            // Negative: the surplus goes to the *bottom*, where the sheet is,
            // which means the content moves up. See [backdropScale].
            translationY = lerp(0f, -backdropShift(size.height, target, insetPx), f)
            shape = clipShape
            clip = f > 0f
        }
    }
}

/**
 * Fills the band a receding sheet leaves around the content.
 *
 * Content inset from the screen pulls away from every edge, and what shows through is
 * whatever is under the host — usually the window's own background, usually the
 * same colour the content was, so the recession reads as nothing at all. Black
 * behind it is what makes it read as depth, and it is what iOS puts there.
 *
 * **Only the band.** The obvious implementation — fill the host and let the
 * content draw over it — is wrong, and wrong in a way that is invisible until it
 * is catastrophic: nothing requires the app's content to be opaque, and where it
 * is not, the ground shows through the middle and the screen goes black. So this
 * cuts the content's own shape out of the fill with an even-odd path, the way
 * [coachmarkStep]'s spotlight cuts its hole, and covers exactly the pixels the
 * content has vacated.
 *
 * **And a ring inside it, where the blur has made the content see-through.** The
 * blur softens the content's own edge inward as well as outward, for about
 * [HaloReach] radii, and nothing stops it — so behind those pixels goes
 * [Theme.colours][io.kontour.ui.theme.Theme.colours]`.background`, which is what
 * an app's root is: `Scaffold`, `NavigationSuiteScaffold` and `TopBar` all take
 * it as their container colour's default.
 *
 * That is a guess about the caller, and the one place it is visibly wrong is
 * worth naming: an app whose root paints *nothing* shows this ring against
 * whatever its window is, instead of showing the window through the halo.
 * The catalog's sheet demos frame their stage exactly that way — a
 * `surface`-coloured `Surface` with its own `OverlayHost` in it — and
 * `phone/sheets` photographs the ring because of it. The alternative is to keep letting the page through, which is the
 * defect. Between guessing the colour of a root that paints one and showing a
 * white browser page under a dark app, the guess wins.
 *
 * Drawn on the *host*, before its children, rather than under the content layer
 * — anything inside that layer is scaled and blurred along with everything else.
 */
@Composable
internal fun Modifier.backdropGround(state: OverlayHostState, style: BackdropStyle): Modifier {
    if (!style.scales) return this

    val clipShape: Shape = Theme.shapes.extraLarge
    val geometry = remember { GroundGeometry() }

    // No blur, no halo, and therefore no ring: the content's edge is hard and
    // the band alone covers everything it has vacated.
    val haloPx = if (style.blurs && LocalBackdropBlur.current && platformSupportsBackdropBlur) {
        with(LocalDensity.current) { BackdropDefaults.BlurRadius.toPx() } * HaloReach
    } else {
        0f
    }
    val backing = Theme.colours.background
    val insetPx = with(LocalDensity.current) { resolvedInset().toPx() }

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

        // A pixel tighter than the content, and that pixel is the third of the
        // three ways the page behind the host was reaching the screen.
        //
        // The hole's edge and the content layer's clip land on the same line,
        // and both are antialiased. Two edges that each cover about 85% of the
        // boundary pixel do not add up to one covered pixel — roughly 30% of it
        // is neither, and through that runs a hairline of whatever is behind.
        // Invisible on a light page under a light app; a bright thread around
        // the content in dark mode, which is what was reported.
        //
        // So they overlap instead of meeting. The cost is the one the corner
        // note above describes — the band's black sitting under the content's
        // antialiased edge reads as that edge being a shade darker — and it is
        // taken deliberately here rather than by accident. The difference is
        // that it is a single pixel and it is the same pixel all the way round,
        // where that bug was a whole corner radius and only in the corners.
        val target = backdropScale(size.width, insetPx)
        val scale = lerp(1f, target, f)
        val shift = lerp(0f, backdropShift(size.height, target, insetPx), f)
        val overlap = 2f * SeamOverlap / minOf(size.width, size.height)
        geometry.matrix.reset()
        // The same shift the content's layer applies, or the hole and the
        // content stop being the same rectangle and the band shows down one
        // side of it. Applied outside the scale, because `translationY` on a
        // `graphicsLayer` is in the *parent's* coordinates and is not scaled.
        geometry.matrix.translate(0f, -shift)
        geometry.matrix.translate(size.width / 2f, size.height / 2f)
        geometry.matrix.scale(scale - overlap, scale - overlap)
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

        // And the app's own background under the content's blurred edge, which
        // is the fourth way the page behind the host was reaching the screen and
        // the one the reporter kept seeing: a glow around the shrinking screen
        // that flickers frame to frame.
        //
        // A blurred layer under an ancestor `scale` comes out **partially
        // transparent** for the blur's whole reach inside its own edge, and
        // `TileMode.Clamp` cannot stop it. Measured six ways — blur alone, blur
        // with the scale on the same layer, the scale on an outer layer with the
        // blur on an inner one, the blur forced offscreen, with a rectangle clip
        // and with the squircle — and every arrangement that has a scale
        // anywhere above the blur produces the identical fade, byte for byte,
        // while every arrangement without one produces none at all. So this is
        // not something the composition can be rearranged out of; the only
        // answer is to put something opaque behind it.
        //
        // The band's black is the wrong thing to put there — it would read as a
        // dark vignette on a light theme, which is the same defect in the other
        // direction. What belongs behind the app's own content, where that
        // content has gone see-through, is the colour the app's content is.
        //
        // **A ring, not a fill.** The middle of the host is deliberately left
        // alone, for the reason the note above gives: nothing requires an app's
        // content to be opaque, and painting the whole host would decide that
        // for it. The ring is as deep as the halo and no deeper, and its outer
        // edge is the host itself rather than the hole — two opaque fills that
        // shared the hole's antialiased boundary would leave the same hairline
        // `SeamOverlap` exists to close. The black band is drawn over it.
        if (haloPx > 0f) {
            val inset = 2f * haloPx * f / minOf(size.width, size.height)
            geometry.matrix.reset()
            geometry.matrix.translate(size.width / 2f, size.height / 2f)
            geometry.matrix.scale((scale - inset).coerceAtLeast(0f), (scale - inset).coerceAtLeast(0f))
            geometry.matrix.translate(-size.width / 2f, -size.height / 2f)

            geometry.inner.reset()
            geometry.inner.addPath(geometry.hole)
            geometry.inner.transform(geometry.matrix)

            geometry.ring.reset()
            geometry.ring.fillType = PathFillType.EvenOdd
            geometry.ring.addRect(Rect(Offset.Zero, size))
            geometry.ring.addPath(geometry.inner)
            drawPath(geometry.ring, backing)
        }

        // Opaque, and that is the fix for the reported white flash. This used to
        // be `alpha = f`, which made the first frames of every sheet a nearly
        // transparent band — and what showed through was whatever the app is
        // sitting on, which the library does not know and cannot paint. A docs
        // site whose page follows the OS theme while the app follows its own
        // setting is a white page behind a dark app, and the band was a window
        // onto it.
        //
        // The ramp bought no motion in the first place: the band's *area* is
        // `(1 - scale) / 2` of the host, which is already zero at zero and grows
        // with the same fraction. It appears by getting wider, which is what a
        // gap opening up does.
        drawPath(geometry.band, Color.Black)
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

    /** [hole] a halo's depth further in, which is the ring's inner edge. */
    val inner = Path()

    /** The host, with [scaled] cut out of it. */
    val band = Path()

    /** The host, with [inner] cut out of it. */
    val ring = Path()

    val matrix = Matrix()
    var size: Size? = null
}
