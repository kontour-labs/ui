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
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import io.kontour.ui.platform.platformBlursReduced
import io.kontour.ui.platform.platformOpaqueBottomInset
import io.kontour.ui.platform.platformReducedBlur
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
 * How far a corner's piece of the band reaches past the content's rectangle,
 * under the straight sides, in pixels: past the one pixel two antialiased edges
 * share, so the pieces overlap rather than meet. See `drawAround`.
 */
private const val FilletOverlap = 3f

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
     * Larger than a small panel over a busy background would take. Across a
     * whole screen a radius of 14dp or so reads as a smudge on the glass rather
     * than as distance.
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
 * ### Why this can be a backdrop filter when a floating bar cannot
 *
 * `Modifier.blur` blurs a layer's own content, not what is behind it, and there
 * is no portable equivalent of `backdrop-filter`. For a bar floating over a live
 * map that is a real wall — the bar is a sibling *above* the map, so blurring it
 * blurs the bar. The only workaround composes the content twice, which is why
 * the library has no frosted bar.
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
 *
 * ### Paid on every frame that draws, and on Skia drawn smaller
 *
 * Measured, a dialog that sits open costs nothing at all: nothing asks for a
 * frame, so nothing is drawn. What costs is every frame that *is* drawn while
 * it is open — the dialog arriving and leaving, a caret blinking in it twice a
 * second, a spinner, a clock ticking on the page behind — and on Skia each one
 * blurs the whole application again from scratch. Skiko's render node keeps no
 * picture of its filtered layer: a `saveLayer`, the application rasterised into
 * it at full size and the blur run over the result, for a caret's blink exactly
 * as for a page that moved. A blink in a dialog on a phone-sized desktop scene
 * took 272ms to draw against 48 with the blur off.
 *
 * Nothing in Compose can say whether a frame changed the application or only
 * the dialog over it, so the result cannot be kept and reused — not without
 * freezing a live map behind the dialog. What can change is how many pixels
 * the blur is paid in. A blur this wide keeps nothing finer than a few pixels,
 * so on Skia — desktop, iOS and the web — the application is drawn at a quarter
 * of its size, blurred by a quarter of the radius and scaled back up: a
 * sixteenth of the offscreen and of everything drawn into it, and the same
 * picture to within a few levels in 255. See [reducedBlur]. Not under a
 * recede, which is noted where it is decided.
 *
 * On the same software rasteriser that is 168ms for the blink, and a settled
 * frame at 3.7 times an unblurred one against 8.5. Most of what is left there
 * is the bilinear scale back up over every pixel, which a CPU pays in full and
 * a GPU's texture units barely notice; the frame a GPU draws is not measured
 * here, and is the one this is for.
 *
 * Android is left as it was. Its render node is a hardware layer, and the
 * filtered layer is reused until something in it changes — the caching Skia's
 * lacks.
 */
@Composable
internal fun Modifier.overlayBackdrop(
    state: OverlayHostState,
    receding: List<Any>,
    blurring: List<Any>,
): Modifier {
    if (receding.isEmpty() && blurring.isEmpty()) return this

    val blurs = blurring.isNotEmpty() && blurAvailable()
    // Read here rather than in the lambda below: `graphicsLayer` runs at draw
    // time, and a theme value has to be captured in composition.
    val insetDp = resolvedInset()
    val insetPx = with(LocalDensity.current) { insetDp.toPx() }
    val opaqueBottomPx = backdropOpaqueBottom()
    val scaling = receding.isNotEmpty() && insetPx > 0f
    if (!blurs && !scaling) return this

    // Every blur the ramp can reach, built once. See [rememberBlurRamp].
    val ramp = rememberBlurRamp()
    // **Not under a recede.** Scaled, the full-size blur lets the content's
    // own edge go see-through, and the ring [backdropGround] puts behind it
    // shows there as a pale rim; a reduced blur is clamped to the content's
    // pixels exactly and keeps the edge solid. Better, arguably, and visibly
    // not the same picture. Only [BackdropStyle.BlurAndScale] blurs and
    // recedes, and nothing in the library asks for it.
    val reduced = blurs && !scaling && platformBlursReduced

    return graphicsLayer {
        // Two numbers now, and each from every entry that asks for its effect:
        // a dialog over a sheet blurs the page by its own arrival while the
        // sheet goes on holding it back. See `OverlayHost`.
        val f = state.furthestIn(receding)
        val b = state.furthestIn(blurring)

        // **The step, not `b`.** A step that has rounded down to zero must
        // leave the layer with *no render effect at all*, rather than one of
        // radius zero — the edge fade this backdrop's ring exists to cover is a
        // property of the offscreen an effect forces, not of how wide the blur
        // is, so a zero-radius `BlurEffect` produces the identical see-through
        // rim with nothing behind it. Which is what it did, on exactly the
        // frames where the round goes to zero.
        //
        // Where the blur is drawn smaller, it is [reducedBlur]'s instead, and
        // this layer only moves the page.
        renderEffect = if (blurs && !reduced) ramp[rampStep(b)] else null

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
    }.then(if (reduced) Modifier.reducedBlur(rememberBlurStep(state, blurring)) else Modifier)
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
internal fun Modifier.backdropGround(
    state: OverlayHostState,
    receding: List<Any>,
    blurring: List<Any>,
): Modifier {
    // Before the way out below, so it is the host's rather than one sheet's: the
    // corners it has cut are as good for the next sheet as for this one. See
    // [GroundGeometry].
    val geometry = remember { GroundGeometry() }
    if (receding.isEmpty()) return this

    // `insetDp` rather than `inset`: there is a second `inset` further down in
    // this function, the halo's scale delta, and it is a fraction rather than a
    // distance. Two locals a few lines apart with one name and two units is the
    // kind of thing that reads fine and resolves wrong.
    val insetDp = resolvedInset()
    val clipShapes = backdropClipShapes(insetDp)

    // No blur, no halo, and therefore no ring: the content's edge is hard and
    // the band alone covers everything it has vacated.
    val haloPx = if (blurring.isNotEmpty() && blurAvailable()) {
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
        // How far back the page is, and — separately — how far blurred, which
        // is what the ring is as deep as. One entry can ask for both; a dialog
        // over a sheet is two entries asking for one each.
        val f = state.furthestIn(receding)
        val b = state.furthestIn(blurring)
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
        // is the reason the ramp is quantised at all: thirteen shapes across the
        // whole animation is a cost this cache absorbs, and one per frame is the
        // cost it was written to avoid. Each is cut once and kept, so a sheet
        // going back down, or dragged across the steps, or the next sheet, cuts
        // none: see [GroundGeometry].
        val fillets = geometry.filletsAt(rampStep(f), clipShapes, this) { state.groundCuts++ }

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

        // The same shift the content's layer applies, or the hole and the
        // content stop being the same rectangle and the band shows down one
        // side of it. Applied outside the scale, because `translationY` on a
        // `graphicsLayer` is in the *parent's* coordinates and is not scaled.
        // See [drawAround] for how the band is drawn from it.
        val bandScaleX = scaleX - overlapX
        val bandScaleY = scaleY - overlapY

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
            // [blurFraction] of the blur's own fraction: as deep as the blur
            // actually is this frame rather than as deep as the animation has
            // got, and the blur's rather than the recede's. See its KDoc.
            val reach = 2f * haloPx * blurFraction(b)
            drawAround(
                fillets,
                scaleX = (scaleX - reach / size.width).coerceAtLeast(0f),
                scaleY = (scaleY - reach / size.height).coerceAtLeast(0f),
                shift = shift,
                colour = backing,
            )
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
        drawAround(fillets, bandScaleX, bandScaleY, shift, Color.Black)
    }
}

/**
 * Fills everything in the host outside its content's outline scaled by
 * [scaleX] and [scaleY] about the middle and lifted by [shift]: the band round a
 * receding page, or the ring under its blurred edge.
 *
 * **In pieces whose shape never changes from frame to frame**, and that is the
 * whole of the cost. This was one path: the host with the scaled outline cut out
 * of it, even-odd, rebuilt on every frame the page moved — a screen-sized,
 * non-convex path with new geometry each time, which a GPU renderer tessellates
 * or rasterises afresh on every one of them. On Android and iOS that was paid on
 * every frame a sheet arrived, left or was dragged, on top of the page's own
 * drawing. Reported over a map and a video, where the page's own drawing already
 * filled the frame: *"it tends to drop quite a few frames and lag"*.
 *
 * Now the four straight sides are rectangles, the cheapest thing there is to
 * draw, and the four corners are [fillets], from [GroundGeometry]: each the corner's box
 * less the outline, cut once per size and per step of the corner's ramp and drawn
 * under this frame's scale rather than rebuilt for it. A path drawn under a
 * changing transform is the same path, which a renderer can keep; a path
 * rebuilt every frame is a new one.
 *
 * The fillets reach a little past the content's rectangle, under the sides: two
 * opaque fills of one colour that share an antialiased edge would leave the
 * hairline [SeamOverlap] exists to close, and overlapping by more than a pixel
 * leaves no edge between them at all.
 */
private fun DrawScope.drawAround(
    fillets: List<Path>,
    scaleX: Float,
    scaleY: Float,
    shift: Float,
    colour: Color,
) {
    val width = size.width
    val height = size.height
    val left = width / 2f * (1f - scaleX)
    val right = width - left
    val top = height / 2f * (1f - scaleY) - shift
    val bottom = height / 2f * (1f + scaleY) - shift
    fun strip(l: Float, t: Float, r: Float, b: Float) {
        if (r > l && b > t) drawRect(colour, Offset(l, t), Size(r - l, b - t))
    }
    strip(0f, 0f, width, top)
    strip(0f, bottom, width, height)
    strip(0f, top, left, bottom)
    strip(right, top, width, bottom)
    withTransform({
        translate(top = -shift)
        scale(scaleX, scaleY, pivot = Offset(width / 2f, height / 2f))
    }) {
        for (fillet in fillets) drawPath(fillet, colour)
    }
}

/** Whether a backdrop blur can be drawn here at all: the theme allows it, and the platform can. */
@Composable
@ReadOnlyComposable
private fun blurAvailable(): Boolean = LocalBackdropBlur.current && platformSupportsBackdropBlur

/**
 * Every blur the ramp can reach, built once — step 0 none at all.
 *
 * Grown with the fraction rather than switched on, so the screen softens as the
 * panel arrives instead of going out of focus a frame before it appears.
 *
 * Quantised to the same [RampSteps] the corner ramp uses, and for the same
 * reason: a `BlurEffect` is an object, and a radius read straight off `f` is a
 * new one every frame — which is a new render effect on the layer every frame,
 * which is the layer's cached rasterisation thrown away every frame. Twelve
 * steps over a blur radius is under a pixel a step.
 *
 * Step for step what [blurFraction] measures, which `backdropGround`'s ring
 * reads too. They are the same measurement and getting them out of step leaks
 * the page behind straight through the content's edge.
 *
 * `TileMode.Clamp`, and it is the other half of the reported white flash. A blur
 * samples beyond what it is blurring, and left to itself it treats everything
 * outside as *transparent* — so the layer's own edge fades out over the blur
 * radius, and the page's layer edge is the whole screen. Inset by `Inset`, that
 * fade lands exactly where the reporter saw it: a soft halo hugging the receding
 * content, showing whatever the app is sitting on. Clamping extends the edge
 * pixels instead, so the content stays opaque to its own boundary.
 */
@Composable
private fun rememberBlurRamp(): Array<BlurEffect?> {
    val radiusPx = with(LocalDensity.current) { BackdropDefaults.BlurRadius.toPx() }
    return remember(radiusPx) {
        Array(RampSteps + 1) { step ->
            if (step == 0) {
                null
            } else {
                val radius = radiusPx * (step.toFloat() / RampSteps)
                BlurEffect(radiusX = radius, radiusY = radius, edgeTreatment = TileMode.Clamp)
            }
        }
    }
}

/**
 * An overlay under one that blurs what is behind it, blurred with the page.
 *
 * A dialog over a sheet is over the sheet as well as the page, and blurring
 * only the page — the strip of it the sheet leaves showing — put a sharp sheet
 * beside a soft screen: two things at the same depth drawn at two depths. Each
 * entry under a blurring one is softened by the same ramp as the page, to the
 * same step, so everything behind the dialog reads as one surface.
 *
 * The cost is the page's again for as long as it lasts: an offscreen render of
 * the entry's layer on each frame. It lasts while a blurring overlay is open
 * over another — a dialog is up for one decision — and costs nothing
 * otherwise: [blurring] is empty and this is never applied.
 *
 * No halo to cover. The page's needed a ring behind it because it is scaled as
 * well as blurred, and a blur under a scale comes out see-through at its edge;
 * an entry is not scaled, and its edges are its own transparent surround.
 */
@Composable
internal fun Modifier.blurredBehind(state: OverlayHostState, blurring: List<Any>): Modifier {
    if (blurring.isEmpty() || !blurAvailable()) return this
    if (platformBlursReduced) return reducedBlur(rememberBlurStep(state, blurring))
    val ramp = rememberBlurRamp()
    return graphicsLayer {
        renderEffect = ramp[rampStep(state.furthestIn(blurring))]
    }
}

/**
 * Which step of the ramp [blurring] has the blur at, as state that changes only
 * when the step does.
 *
 * Read in a draw rather than a layer block, so it has to be this: the fraction
 * itself changes on every frame of the animation, and a draw that read it
 * would run again on every one rather than on the twelve the step changes on.
 */
@Composable
private fun rememberBlurStep(state: OverlayHostState, blurring: List<Any>): State<Int> =
    remember(state, blurring) { derivedStateOf { rampStep(state.furthestIn(blurring)) } }

/**
 * Blurs what this wraps by [step] of the ramp, drawn smaller and scaled back
 * up where the platform can: Skia's, so the desktop, iOS and the web.
 *
 * ### How much smaller
 *
 * By four once the blur's standard deviation reaches 16px, by two from 8px,
 * and not at all below that: whichever leaves at least [LeastReducedSigma]
 * for the blur where it is done. That is about where Skia's GPU blur stops
 * scaling down of its own accord — it halves the picture until the deviation
 * is 4 or less — so on a GPU the blur itself still runs at the size it always
 * did. What goes is the full-size offscreen it was scaled down *from*, and the
 * application drawn into it at full size: a quarter is a sixteenth of the
 * pixels.
 *
 * At [BackdropDefaults.BlurRadius] on a 3x phone that is a quarter from the
 * fifth of the ramp's twelve steps to the end, a half on the two before, and
 * the full size on the first two.
 *
 * ### Why the effect is set in a draw, and the content has a layer of its own
 *
 * The content is drawn smaller by the canvas, not by a layer's scale. A layer's
 * scale moves the content's coordinates — its hit testing, its position in the
 * window, anything anchored to it — where a canvas transform moves only pixels.
 * So the scale is a draw, and the effect that scales back up has to change in
 * the same frame as it, or the page shows at a quarter or four times its size
 * for that frame. One draw block sets both.
 *
 * The content gets a layer of its own underneath, so that a new step runs this
 * block again and not the application's drawing: the content is drawn here as
 * a layer already recorded, and recorded again only when something in it
 * changes.
 *
 * Clipped to its bounds, because scaling back up from half a pixel in reaches
 * two pixels past each edge. In a full-window host that is off screen; in a
 * smaller one it would be drawn over the host's neighbours. A rectangle is the
 * clip with no offscreen of its own, and it is the canvas's: the layer's own
 * `clip`, with no outline set, drew nothing at all on Skiko.
 */
@Composable
private fun Modifier.reducedBlur(step: State<Int>): Modifier {
    val radiusPx = with(LocalDensity.current) { BackdropDefaults.BlurRadius.toPx() }
    val ramp = remember(radiusPx) { ReducedBlurRamp(radiusPx) }
    val layer = rememberGraphicsLayer()
    return drawWithContent {
        val blur = ramp.at(step.value, size.width.roundToInt(), size.height.roundToInt())
        if (blur == null) {
            // Step 0 is no effect at all rather than one of radius zero: see
            // [overlayBackdrop] for what a zero-radius effect still does.
            drawContent()
            return@drawWithContent
        }
        layer.renderEffect = blur.effect
        layer.record {
            scale(blur.drawScaleX, blur.drawScaleY, pivot = Offset.Zero) {
                this@drawWithContent.drawContent()
            }
        }
        clipRect { drawLayer(layer) }
    }.graphicsLayer()
}

/** One step of the ramp as [reducedBlur] draws it: the effect, and how much smaller the content is drawn for it. */
private class RampBlur(val effect: RenderEffect, val drawScaleX: Float, val drawScaleY: Float)

/**
 * Every blur the ramp can reach at one size, each built when first asked for
 * and all of them again when the size changes. A reduced blur is cut to the
 * content's own pixels, so unlike [rememberBlurRamp]'s these depend on it.
 */
private class ReducedBlurRamp(private val radiusPx: Float) {
    private var width = 0
    private var height = 0
    private val steps = arrayOfNulls<RampBlur>(RampSteps + 1)

    fun at(step: Int, width: Int, height: Int): RampBlur? {
        if (step == 0 || width < 2 || height < 2) return null
        if (width != this.width || height != this.height) {
            steps.fill(null)
            this.width = width
            this.height = height
        }
        return steps[step] ?: build(step).also { steps[step] = it }
    }

    private fun build(step: Int): RampBlur {
        val radius = radiusPx * (step.toFloat() / RampSteps)
        val full = RampBlur(BlurEffect(radius, radius, edgeTreatment = TileMode.Clamp), 1f, 1f)
        // The standard deviation Compose's own `BlurEffect` makes of a radius.
        val sigma = BlurSigmaScale * radius + 0.5f
        val factor = when {
            sigma / 4f >= LeastReducedSigma -> 4
            sigma / 2f >= LeastReducedSigma -> 2
            else -> return full
        }
        // Whole pixels, so the edge the blur clamps to is a pixel's edge rather
        // than one the content only partly covers.
        val cropWidth = (width / factor.toFloat()).roundToInt().coerceAtLeast(2)
        val cropHeight = (height / factor.toFloat()).roundToInt().coerceAtLeast(2)
        val effect = platformReducedBlur(
            sigma = sigma / factor,
            cropWidth = cropWidth,
            cropHeight = cropHeight,
            scaleX = width / (cropWidth - 1f),
            scaleY = height / (cropHeight - 1f),
        ) ?: return full
        return RampBlur(effect, cropWidth / width.toFloat(), cropHeight / height.toFloat())
    }
}

/** Compose's `BlurEffect`: standard deviations to a radius, plus the half pixel it adds. */
private const val BlurSigmaScale = 0.57735f

/**
 * The least standard deviation a reduced blur is left with, in its own pixels:
 * where Skia's GPU blur stops scaling down by itself. See [reducedBlur].
 */
private const val LeastReducedSigma = 4f

/**
 * The ground's corners, cut once for each step of the ramp and kept.
 *
 * Held across frames rather than built inside one, for a modifier that runs on
 * every frame of every sheet animation. Cutting a step's corners is the
 * expensive part — the content's outline, a squircle of four corners of
 * trigonometry and twelve cubics, and then four path differences taken from it
 * — and nothing here changes on a frame that only moves the page.
 *
 * ### Every step kept, not the last one
 *
 * This held one step's corners, the one the last frame drew, and cut again
 * whenever the ramp took a step: thirteen cuts as a sheet came up, thirteen more
 * as it went down, and one for every step a drag crossed, each about 90µs of path
 * operations on a desktop and several times that on a phone, on frames that are
 * already the busiest a sheet has. And all of it again for the next sheet, since
 * this was remembered past the point where a host with nothing receding leaves.
 * Kept for every step, and by the host rather than the sheet, the first sheet
 * cuts thirteen and nothing after it cuts any: counted by
 * [OverlayHostState.groundCuts] in `BackdropGroundCutsTest`.
 *
 * The same paths whichever way round, so the picture is too. Cut again only
 * when what they are cut from changes: the host's size, its density, or the
 * shapes, which carry the theme's corner, the device's and the layout direction.
 */
private class GroundGeometry {
    /** Scratch: the content's outline at full size, for the step being cut. */
    private val hole = Path()

    /** What [filletsAt] has cut, by step. */
    private val cut = arrayOfNulls<List<Path>>(RampSteps + 1)

    private var size: Size? = null
    private var density = Float.NaN
    private var direction: LayoutDirection? = null
    private var shapes: List<CornerBasedShape>? = null

    /**
     * What lies outside the content's outline in each of its corners, at full
     * size, for [step] of [shapes]: the corner's box less the outline, the box
     * reaching [FilletOverlap] past the host's edges. Drawn under each frame's
     * scale by `drawAround` rather than rebuilt for it. [onCut] hears of each one
     * that was not already kept.
     */
    fun filletsAt(step: Int, shapes: List<CornerBasedShape>, scope: DrawScope, onCut: () -> Unit): List<Path> {
        val size = scope.size
        // Compared whole only when it is a new list: the host remembers its
        // shapes for as long as a sheet is up, so this is once a sheet, and the
        // next sheet's are a new list of the same shapes.
        val newShapes = shapes !== this.shapes && shapes != this.shapes
        // And the layout direction, which the outline is made in: the host
        // keeps these from one sheet to the next, and a page switched to right
        // to left in between would otherwise be cut by the left-to-right
        // corners of an asymmetric shape.
        if (size != this.size || scope.density != density || scope.layoutDirection != direction || newShapes) {
            cut.fill(null)
            this.size = size
            density = scope.density
            direction = scope.layoutDirection
        }
        this.shapes = shapes
        cut[step]?.let { return it }
        val shape = shapes[step]
        hole.reset()
        hole.addOutline(shape.createOutline(size, scope.layoutDirection, scope))
        val radius = maxOf(
            maxOf(shape.topStart.toPx(size, scope), shape.topEnd.toPx(size, scope)),
            maxOf(shape.bottomEnd.toPx(size, scope), shape.bottomStart.toPx(size, scope)),
        )
        // A smoothed corner leaves the straight edge before its radius does:
        // at most twice the radius along each side, at full smoothing.
        val reach = (radius * 2f + FilletOverlap).coerceAtMost(minOf(size.width, size.height) / 2f)
        val out = FilletOverlap
        onCut()
        return listOf(
            Rect(-out, -out, reach, reach),
            Rect(size.width - reach, -out, size.width + out, reach),
            Rect(size.width - reach, size.height - reach, size.width + out, size.height + out),
            Rect(-out, size.height - reach, reach, size.height + out),
        ).map { box ->
            Path.combine(PathOperation.Difference, Path().apply { addRect(box) }, hole)
        }.also { cut[step] = it }
    }
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
