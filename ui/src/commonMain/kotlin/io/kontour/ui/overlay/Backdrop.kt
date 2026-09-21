package io.kontour.ui.overlay

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import io.kontour.ui.adaptive.edges
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.unit.Density
import io.kontour.ui.platform.DeviceCorners
import io.kontour.ui.platform.platformDeviceCorners
import io.kontour.ui.theme.concentricWith
import kotlin.math.roundToInt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.drawWithContent
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
import io.kontour.ui.platform.platformOpaqueBottomInset
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
 * One device pixel, which is the width of the antialiased boundary the band and
 * the content share. Not a `Dp`: this is not a design measure but the size of a
 * rasteriser's seam, and it is the same one pixel on every density.
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
 * How a receding screen is fitted inside its frame: two scales and a lift.
 *
 * ### Equal gaps need two scales, and there is no way round it
 *
 * Scaling a rectangle insets it by a fraction of each *axis*, so **one** number
 * gives two different margins on any screen that is not square. A uniform scale
 * simply cannot put an equal gap on all four edges of a 390x844 phone; the only
 * question is where the difference is spent.
 *
 * It used to be spent at the bottom, on the argument that the bottom is the edge
 * a sheet is covering anyway. Then the bottom acquired a navigation bar and the
 * surplus went with it, so a second constraint was added to keep the inset below
 * the bar — which bought the bottom back and pushed the difference out to the
 * *sides*, at about 16.6dp against a 12dp top. Reported, twice, and the second
 * report was the plain statement that it should be uniform everywhere, always.
 *
 * So the difference is not spent anywhere. [scaleX] and [scaleY] are worked out
 * separately and every gap is exactly the inset.
 *
 * ### What that costs, stated rather than buried
 *
 * It distorts. Mapping 390x844 into a 12dp frame needs 0.938 across and 0.972
 * down, so the receded page is about 3.5% wider in proportion than it is at rest
 * and a circle on it is very slightly an ellipse. This function's own
 * documentation used to give that as the reason *not* to do this, and it was a
 * fair reason against an unreported cost — it stopped being one when the cost it
 * was avoiding turned out to be the thing people could see. A page behind a scrim,
 * for a few hundred milliseconds, at three and a half percent, against a frame
 * that is visibly heavier on one edge.
 *
 * ### An opaque bar is the one thing that moves it
 *
 * A gap below something the system paints over is not a gap. Where the platform
 * reports a bottom inset that is *tappable* — three-button navigation, and
 * nothing else; see [io.kontour.ui.platform.platformOpaqueBottomInset] — the page
 * is fitted into the window **less that bar** and lifted by half of it, so the
 * bottom gap lands on the inset above the bar rather than under it.
 *
 * All four gaps are still the inset. What changes is that the page is shorter,
 * because the room it is being fitted into is. Gesture navigation and iOS report
 * a bottom inset that nothing paints, so they are the plain case and nothing
 * moves there.
 */
@Immutable
internal data class BackdropFit(
    val scaleX: Float,
    val scaleY: Float,
    /** How far to move the content **up**, in pixels. Call sites negate it. */
    val shiftY: Float,
)

/** See [BackdropFit]. */
internal fun backdropFit(
    width: Float,
    height: Float,
    insetPx: Float,
    opaqueBottomPx: Float,
): BackdropFit {
    if (width <= 0f || height <= 0f) return BackdropFit(1f, 1f, 0f)
    // The room the page is actually being fitted into. An opaque bar is not part
    // of it; anything else at the bottom is.
    val room = (height - opaqueBottomPx).coerceAtLeast(0f)
    return BackdropFit(
        scaleX = (1f - 2f * insetPx / width).coerceIn(0f, 1f),
        scaleY = ((room - 2f * insetPx) / height).coerceIn(0f, 1f),
        // Half, not all of it. A scaled layer stays centred on the window, and
        // the page has to end up centred on the *room* instead — which is half a
        // bar higher up.
        shiftY = opaqueBottomPx / 2f,
    )
}

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
 * Whatever the system has parked along the bottom of the window, in pixels.
 *
 * **`edges` rather than `safeDrawing`, and the difference is the keyboard.**
 * `WindowInsets.edges` is deliberately the system bars and the display cutout
 * and *not* the IME — see its own note — so a keyboard opening does not make the
 * screen recede further. `safeDrawing` includes the IME, and a screen that steps
 * back another 300dp when a field is focused inside the sheet in front of it is
 * a worse fault than the one this parameter exists to fix.
 *
 * Zero on a JVM scene, which is why the existing backdrop tests are unaffected:
 * an `ImageComposeScene` has no bars, so the new constraint never binds and the
 * width-derived scale is still the answer. That is the check that this only
 * changes the case it was written for.
 */
/**
 * Thirteen clip shapes, one per step of the recede.
 *
 * ### The corner is the snap
 *
 * At `f = 0.01` the content is still full size and suddenly has a 34dp corner cut
 * out of it. That is the reported "it just snaps into place", and it is a corner
 * rather than a scale: the travel is 12dp over 220ms and reads as smooth, while
 * the radius arrives whole on the first frame that clips at all.
 *
 * So the radius ramps too — from the **display's own** at rest, where the clip is
 * invisible because the bezel already draws that curve, to the settled one when
 * the screen is fully back. On a phone that reports a 55dp corner it is a change
 * of about 12dp over 12dp of travel, which is the concentric answer arriving
 * gradually. On a display that says nothing it is a ramp from square, which is
 * still honest: at `f = 0.01` a full-size screen has a 0.3dp corner rather than a
 * 34dp one.
 *
 * ### Why thirteen and not one per frame
 *
 * A shape whose corner depends on `f` is a new shape every frame, and
 * [backdropGround] already records what that costs: `createOutline` misses the
 * squircle path cache on each one *and* evicts the entries every other container
 * on screen is using. Quantising `f` to twelve steps makes it thirteen cache
 * entries for the whole animation instead of sixty a second, and twelve steps
 * over a 12dp change is a dp a step — under the threshold where a reader could
 * see the quantisation even if they were looking for it.
 *
 * ### The floor is the theme's, and only where the platform is silent
 *
 * `atLeast(device - gap)` rather than `atLeast(device).inset(gap)`: both are
 * concentric on a phone that answers, and this one leaves every desktop, every
 * browser and every pre-API-31 Android exactly the corner they have today. A
 * change that only fires where new information arrived is easier to trust than
 * one that quietly restyles the platforms that told us nothing.
 */
@Composable
private fun backdropClipShapes(gap: Dp): List<CornerBasedShape> {
    val device = platformDeviceCorners()
    val direction = LocalLayoutDirection.current
    // **All four corners, and the bezel's own curve where the platform states
    // one.** This read a single radius and applied it four times, which is right
    // on an iPhone and wrong on the phones that declare their top and bottom
    // pairs differently — and wrong on *every* phone once the window is rotated,
    // because the positions rotate with it. The backdrop clips the whole window,
    // so its four corners map one to one onto the four the platform reports.
    val settled = Theme.shapes.extraLarge.concentricWith(device, gap, direction)
    return remember(settled, device, direction) {
        List(RampSteps + 1) { step ->
            settled.rampedFrom(device, direction, step.toFloat() / RampSteps)
        }
    }
}

/** Which of [backdropClipShapes] a fraction lands on. */
private fun rampStep(f: Float): Int = (f * RampSteps).roundToInt().coerceIn(0, RampSteps)

/**
 * How far through the blur a fraction is, quantised.
 *
 * **Two places read this and they have to be the same number.** The radius
 * [overlayBackdrop] blurs by and the depth of the ring [backdropGround] puts
 * behind the result are the same measurement from opposite sides: the ring
 * exists to back the pixels the blur has made see-through, and it is cut exactly
 * as deep as the blur reaches.
 *
 * Quantising one and not the other is what a round of this cost. The radius was
 * put on [rampStep] so a `BlurEffect` is one of thirteen objects rather than a
 * new one every frame, and the ring was left on the raw fraction — and rounding
 * goes *up* as often as down, so for half the steps the blur reached further in
 * than the ring came out. `theBlurredEdgeDoesNotShowThePageBehindIt` found it
 * immediately, at the frames where the step is largest relative to the fraction:
 * just after the animation starts, a blur twice as deep as the backing under it.
 */
private fun blurFraction(f: Float): Float = rampStep(f).toFloat() / RampSteps

/**
 * This shape's corners, interpolated from a flat [start] radius at [fraction] 0.
 *
 * Private to the backdrop rather than a third sibling of `inset` and `outset` in
 * `Shapes`: those two express a relationship between two boxes and are reached
 * for all over the library, and this expresses one frame of one animation.
 */
private fun CornerBasedShape.rampedFrom(
    start: DeviceCorners?,
    direction: LayoutDirection,
    fraction: Float,
): CornerBasedShape {
    // Null is a square display, which is where the ramp started from before any
    // of this: a screen with no corner of its own recedes out of a flat window.
    val ltr = direction == LayoutDirection.Ltr
    val topStartFrom = start?.let { if (ltr) it.topLeft else it.topRight } ?: 0.dp
    val topEndFrom = start?.let { if (ltr) it.topRight else it.topLeft } ?: 0.dp
    val bottomEndFrom = start?.let { if (ltr) it.bottomRight else it.bottomLeft } ?: 0.dp
    val bottomStartFrom = start?.let { if (ltr) it.bottomLeft else it.bottomRight } ?: 0.dp
    return copy(
        topStart = RampCornerSize(topStart, topStartFrom, fraction),
        topEnd = RampCornerSize(topEnd, topEndFrom, fraction),
        bottomEnd = RampCornerSize(bottomEnd, bottomEndFrom, fraction),
        bottomStart = RampCornerSize(bottomStart, bottomStartFrom, fraction),
    )
}

/**
 * A [CornerSize] part of the way from a flat radius to another corner size.
 *
 * Deferred for the reason every corner size here is: the target may be a
 * percentage, and a percentage of what is not known until there is a size and a
 * density to resolve it against.
 */
@Immutable
private data class RampCornerSize(
    val target: CornerSize,
    val start: Dp,
    val fraction: Float,
) : CornerSize {
    override fun toPx(shapeSize: Size, density: Density): Float {
        val from = with(density) { start.toPx() }
        val to = target.toPx(shapeSize, density)
        return from + (to - from) * fraction
    }
}

/**
 * How much of the window's bottom edge something opaque is sitting on.
 *
 * **Not `WindowInsets`.** A bottom inset says the system has reserved the edge;
 * it does not say whether anything is painted there, and the two cases want
 * opposite answers. Gesture navigation reserves a strip for the handle and paints
 * nothing on it, so a gap that runs under it is a gap the reader can see.
 * Three-button navigation paints a bar, and a gap under *that* is no gap at all —
 * which is the report this exists for.
 *
 * `tappableElement` is the distinction the platform actually draws, and
 * [io.kontour.ui.platform.platformOpaqueBottomInset] is where it is read.
 */
@Composable
private fun backdropOpaqueBottom(): Float =
    with(LocalDensity.current) { platformOpaqueBottomInset().toPx() }

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
    // Read here rather than in the lambda below: `graphicsLayer` runs at draw
    // time, and a theme value has to be captured in composition.
    val insetDp = resolvedInset()
    val insetPx = with(LocalDensity.current) { insetDp.toPx() }
    val opaqueBottomPx = backdropOpaqueBottom()
    val scaling = style.scales && insetPx > 0f
    if (!blurring && !scaling) return this

    return graphicsLayer {
        val f = (state.backdropFraction?.invoke() ?: 0f).coerceIn(0f, 1f)

        // **[blurFraction], not `f`.** A step that has rounded down to zero must
        // leave the layer with *no render effect at all*, rather than one of
        // radius zero — the edge fade this backdrop's ring exists to cover is a
        // property of the offscreen an effect forces, not of how wide the blur
        // is, so a zero-radius `BlurEffect` produces the identical see-through
        // rim with nothing behind it. Which is what it did, on exactly the
        // frames where the round goes to zero.
        renderEffect = if (blurring && blurFraction(f) > 0f) {
            // Grown with the fraction rather than switched on, so the screen
            // softens as the panel arrives instead of going out of focus a frame
            // before it appears.
            //
            // Quantised to the same [RampSteps] the corner ramp uses, and for
            // the same reason: a `BlurEffect` is an object, and a radius read
            // straight off `f` is a new one every frame — which is a new render
            // effect on the layer every frame, which is the layer's cached
            // rasterisation thrown away every frame. Twelve steps over a blur
            // radius is under a pixel a step.
            //
            // Through [blurFraction], which `backdropGround`'s ring reads too.
            // They are the same measurement and getting them out of step leaks
            // the page behind straight through the content's edge.
            val radius = radiusPx * blurFraction(f)
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
            val fit = backdropFit(size.width, size.height, insetPx, opaqueBottomPx)
            scaleX = lerp(1f, fit.scaleX, f)
            scaleY = lerp(1f, fit.scaleY, f)
            // Negative: [BackdropFit.shiftY] is a magnitude and the content moves
            // *up* by it, off an opaque bar.
            translationY = lerp(0f, -fit.shiftY, f)

            // **No clip, and that is the whole of a 58ms frame on a phone.**
            //
            // This used to set `shape` and `clip = f > 0f` so the receding page
            // had rounded corners. A squircle is an `Outline.Generic`, so Skia
            // cannot take its rounded-rectangle fast path: a non-rectangular
            // clip on a layer is a `saveLayer`, an offscreen surface the size of
            // the layer, and a masked composite back. The layer here is the
            // *whole application* — `OverlayHost` composes the app as one
            // full-size sibling — so on a phone that is three million pixels of
            // offscreen allocation and blit for every frame a sheet is open, on
            // a tiled GPU where `saveLayer` is a cliff rather than a slope. It
            // fires under [BackdropStyle.Scale], which is what every sheet uses.
            //
            // The corners are drawn *over* the content instead, by
            // [backdropGround], which was already drawing the band round them in
            // the same colour from the same path. So the picture is the one it
            // always was and the layer is transform-only — for a sheet, no
            // offscreen at all.
            //
            // Nothing measured it for three rounds because nothing could:
            // `BackdropCostDiagnostic` does exercise this path, on a software
            // rasteriser, where a mask is more pixel work and costs about what
            // it looks like it should. This cost is architectural and a CPU
            // rasteriser does not have it.
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
 * whatever its window is, instead of showing the window through the halo. The
 * catalog's sheet demos frame their stage exactly that way — a `surface`-coloured
 * `Surface` with its own `OverlayHost` in it.
 *
 * **No golden photographs it**, which this note used to claim one did. The phone
 * screenshots all render with `reduceMotion = true`, so the inset is zero, the
 * content never recedes and there is no band for a ring to sit in. Worth stating
 * plainly rather than leaving a reader to go looking for the picture.
 *
 * The alternative is to keep letting the page through, which is the defect.
 * Between guessing the colour of a root that paints one and showing a white
 * browser page under a dark app, the guess wins.
 *
 * Wraps the content's own layer from outside it: anything drawn *inside* that
 * layer is scaled and blurred along with everything else, and the band and the
 * ring are the frame rather than part of the picture.
 *
 * **The band goes over the content and the ring goes under it**, which is why
 * this is a `drawWithContent` rather than the `drawBehind` it used to be. The
 * band is what rounds the content's corners now that the content's own layer has
 * stopped clipping itself — see the note in [overlayBackdrop], which is where
 * the cost of that clip is written down. Drawing the band behind would leave the
 * content's square corners over the top of it.
 *
 * The ring stays underneath because of what it is for: backing the pixels the
 * blur has made see-through. Over the top it would cover them.
 *
 * **On the content's box, not on the host.** It was on the host for one round,
 * and that is a different `drawContent`: the host's children are the app *and*
 * the overlay stack, so a band drawn after them went over the sheet. The
 * distinction did not exist while this drew behind everything, which is exactly
 * the kind of thing that changes underneath a modifier when its phase does.
 */
@Composable
internal fun Modifier.backdropGround(state: OverlayHostState, style: BackdropStyle): Modifier {
    if (!style.scales) return this

    // `insetDp` rather than `inset`: there is a second `inset` further down in
    // this function, the halo's scale delta, and it is a fraction rather than a
    // distance. Two locals a few lines apart with one name and two units is the
    // kind of thing that reads fine and resolves wrong.
    val insetDp = resolvedInset()
    val clipShapes = backdropClipShapes(insetDp)
    val geometry = remember { GroundGeometry() }

    // No blur, no halo, and therefore no ring: the content's edge is hard and
    // the band alone covers everything it has vacated.
    val haloPx = if (style.blurs && LocalBackdropBlur.current && platformSupportsBackdropBlur) {
        with(LocalDensity.current) { BackdropDefaults.BlurRadius.toPx() } * HaloReach
    } else {
        0f
    }
    val backing = Theme.colours.background
    val insetPx = with(LocalDensity.current) { insetDp.toPx() }
    // The same number the content's layer uses. Both have to agree or the hole
    // and the content stop being the same rectangle — which is the fault the
    // shift below already documents, in a second dimension.
    val opaqueBottomPx = backdropOpaqueBottom()

    return drawWithContent {
        val f = (state.backdropFraction?.invoke() ?: 0f).coerceIn(0f, 1f)
        if (f <= 0f) {
            drawContent()
            return@drawWithContent
        }

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
        //
        // The step joins the size as a cache key now that the corner ramps. It
        // is the reason the ramp is quantised at all: thirteen rebuilds across
        // the whole animation is a cost this cache absorbs, and one per frame is
        // the cost it was written to avoid.
        val step = rampStep(f)
        if (geometry.size != size || geometry.step != step) {
            geometry.hole.reset()
            geometry.hole.addOutline(
                clipShapes[step].createOutline(size, layoutDirection, this)
            )
            geometry.size = size
            geometry.step = step
        }

        // A pixel tighter than the content, which is the third of the three
        // ways the page behind the host reached the screen.
        //
        // Two antialiased edges land on the same line — this hole's, and the
        // *scaled content rectangle's* — and two edges each covering about 85%
        // of the boundary pixel do not add up to one covered pixel. Roughly 30%
        // of it is neither, and through that runs a hairline of whatever is
        // behind: invisible on a light page under a light app, a bright thread
        // around the content in dark mode, which is what was reported.
        //
        // **The overlap survived the clip coming off, and the reasoning
        // inverted.** It used to widen the band so it underlapped a clipped
        // content's soft edge from behind. The band is drawn over the content
        // now — see the header — so the same tighter hole reaches a pixel
        // *inward* instead and covers that edge from the front. It was removed
        // on the argument that with no clip there is only one edge to worry
        // about, which is wrong: the layer stopped clipping itself but it is
        // still scaled, so its own rectangle still lands on a fractional pixel.
        // `theBlurredEdgeDoesNotShowThePageBehindIt` put the hairline straight
        // back on screen and named it in one run.
        val fit = backdropFit(size.width, size.height, insetPx, opaqueBottomPx)
        val scaleX = lerp(1f, fit.scaleX, f)
        val scaleY = lerp(1f, fit.scaleY, f)
        val shift = lerp(0f, fit.shiftY, f)
        val overlapX = 2f * SeamOverlap / size.width
        val overlapY = 2f * SeamOverlap / size.height

        // Nothing has moved, so there is nothing to frame.
        //
        // A reader who has asked for reduced motion gets an inset of zero — see
        // [resolvedInset] — so the scales stay at 1 while the fraction still
        // animates, and the content vacates no pixels at all. The band's area is
        // zero there by construction *except* for the overlap, which would draw
        // a one-pixel black frame over the content's own edge now that this
        // paints on top of it rather than behind. Invisible for as long as it
        // was underneath, which is why it needed saying only now.
        if (scaleX >= 1f && scaleY >= 1f) {
            drawContent()
            return@drawWithContent
        }

        geometry.matrix.reset()
        // The same shift the content's layer applies, or the hole and the
        // content stop being the same rectangle and the band shows down one
        // side of it. Applied outside the scale, because `translationY` on a
        // `graphicsLayer` is in the *parent's* coordinates and is not scaled.
        geometry.matrix.translate(0f, -shift)
        geometry.matrix.translate(size.width / 2f, size.height / 2f)
        geometry.matrix.scale(scaleX - overlapX, scaleY - overlapY)
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
            // [blurFraction], not `f`: as deep as the blur actually is this
            // frame rather than as deep as the animation has got. See its KDoc.
            val reach = 2f * haloPx * blurFraction(f)
            geometry.matrix.reset()
            geometry.matrix.translate(0f, -shift)
            geometry.matrix.translate(size.width / 2f, size.height / 2f)
            geometry.matrix.scale(
                (scaleX - reach / size.width).coerceAtLeast(0f),
                (scaleY - reach / size.height).coerceAtLeast(0f),
            )
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
        //
        // **After the content, not before it.** The band is the content's
        // corners as well as the gap around them now — see this function's
        // header and [overlayBackdrop] — and a corner cannot be rounded from
        // underneath.
        drawContent()
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

    /** Which of the ramp's thirteen shapes [hole] was built from. */
    var step: Int = -1
}

/**
 * How many steps the corner's ramp is quantised to.
 *
 * Twelve, for thirteen shapes counting both ends. The change being quantised is
 * about twelve dp on a phone that reports its corner, so this is a dp a step —
 * below what a reader can see in a 220ms animation, and far below what a path
 * cache can absorb at sixty rebuilds a second.
 */
private const val RampSteps = 12
