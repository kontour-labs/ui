package io.kontour.ui.foundation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import io.kontour.ui.a11y.contentColourFor
import io.kontour.ui.theme.Shadow
import io.kontour.ui.theme.Theme

/**
 * A ground: background, shape, border, shadow, and the content colour that
 * cascades from it.
 *
 * The substrate nearly every component in the system is built on. A card is a
 * `Surface`; so is a sheet, a menu, a dialog, a chip. What makes it worth having
 * rather than reaching for `Modifier.background()` is the last part — it sets
 * [LocalContentColour], so text and icons inside it are legible without any call
 * site knowing what colour it painted.
 *
 * ```
 * Surface(
 *     shape = Theme.shapes.medium,
 *     containerColour = Theme.colours.surface,
 *     shadow = Theme.elevation.low,
 * ) {
 *     Column(Modifier.padding(Theme.spacing.md)) {
 *         Text("Perth Station")     // inherits a legible colour automatically
 *     }
 * }
 * ```
 *
 * @param contentColour Defaults to whichever of the scheme's light or dark
 *   content colours reads better on [containerColour] — so a surface painted an arbitrary
 *   colour (a route colour out of a transit feed, say) still gets legible
 *   content without the caller working it out.
 * @param contentAlignment Where the content sits when the surface is larger
 *   than it — because a `defaultMinSize` or a fixed height made it so. The
 *   default `TopStart` matches `Box`, but anything with a minimum size almost
 *   certainly wants `Center`; leaving it at the default is what puts an icon in
 *   the corner of a FAB.
 * ### A tap on a surface is the surface's
 *
 * Whatever is drawn beneath a surface does not get the taps that land on it. A
 * card floating over a map, a toolbar over a canvas, a toast or a tooltip over
 * the page: a tap on the part of one with no control in it used to fall straight
 * through to whatever was underneath, and pressed a row the reader could not
 * see. The surface is a target now, within its [shape], so the hit stops there.
 * It does not consume anything: its own controls and its own `modifier`'s
 * gestures see every event as they did, and a clickable container *around* a
 * surface still gets a tap on it, which is what makes a card inside a clickable
 * row press the row. The same rule as Material's surface.
 *
 * @param shadow A [Shadow] from `Theme.elevation`, not a raw dp. Pick the level
 *   that matches where the element sits in the overlay stack.
 */
@Composable
fun Surface(
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    containerColour: Color = Theme.colours.surface,
    contentColour: Color = SurfaceDefaults.contentColour(containerColour),
    border: BorderStroke? = null,
    shadow: Shadow = Shadow.None,
    contentAlignment: Alignment = Alignment.TopStart,
    propagateMinConstraints: Boolean = false,
    content: @Composable () -> Unit,
) = SurfaceBox(
    modifier, shape, containerColour, contentColour, border, shadow,
    contentAlignment, propagateMinConstraints, keepsPresses = false, content,
)

/**
 * A [Surface] for a bar: one that also keeps a press from the container it sits
 * in.
 *
 * A bar is chrome. A tap on the stretch of a top bar between its title and its
 * actions, or between two buttons on a floating toolbar, is a tap on nothing —
 * and it must not become a tap on the thing the bar sits inside either, which a
 * plain [Surface] lets through on purpose. A toolbar over a drawing canvas is
 * the case that matters: the canvas is its container, and a press between two
 * tools put a point on the drawing.
 *
 * So the bar takes the presses and releases that nothing in it took. Only those:
 * a drag that starts on a bar still reaches a scrolling page round it, which is
 * what a bar drawn in a scrolling column — a gallery of them, or a title that
 * scrolls away — needs to keep doing. See [keepsPresses].
 *
 * A press on a bar is therefore not a press on nothing, so it does not put the
 * keyboard away as one on the page does (`OverlayHost(clearFocusOnTap)`): a
 * field keeps its focus when the bar above it is tapped, as under UIKit's and
 * Android's own bars.
 */
@Composable
internal fun BarSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    containerColour: Color = Theme.colours.surface,
    contentColour: Color = SurfaceDefaults.contentColour(containerColour),
    border: BorderStroke? = null,
    shadow: Shadow = Shadow.None,
    contentAlignment: Alignment = Alignment.TopStart,
    propagateMinConstraints: Boolean = false,
    content: @Composable () -> Unit,
) = SurfaceBox(
    modifier, shape, containerColour, contentColour, border, shadow,
    contentAlignment, propagateMinConstraints, keepsPresses = true, content,
)

@Composable
private fun SurfaceBox(
    modifier: Modifier,
    shape: Shape,
    containerColour: Color,
    contentColour: Color,
    border: BorderStroke?,
    shadow: Shadow,
    contentAlignment: Alignment,
    propagateMinConstraints: Boolean,
    keepsPresses: Boolean,
    content: @Composable () -> Unit,
) {
    // Remembered, and this is the one line in this file that is about the frame
    // rate rather than about what a surface is.
    //
    // `Modifier.elevation` folds an `androidx.compose.ui.draw.dropShadow` node
    // per layer, each taking a lambda that closes over its `ShadowSpec`.
    // `DropShadowElement` compares that lambda, and two lambda instances are
    // never equal — so every recomposition of every `Surface` used to hand
    // Compose a modifier element it had to treat as new, updating the node and
    // discarding the blur it had already rasterised.
    //
    // `OverlayRecompositionTest` wrote this down before anything acted on it:
    // "a fresh modifier chain every frame means the sheet's two `dropShadow`
    // layers ... are re-rasterised every frame instead of being cached. That is
    // the frame rate."
    //
    // It matters most where it is least obvious. Theme changes provide new
    // colours through `staticCompositionLocalOf`, so a fade recomposes the whole
    // application once per frame — while the elevation scale deliberately
    // *steps* rather than interpolating, so `shadow` is the same instance for
    // almost all of it. There was a cache to hit on nearly every frame and
    // nothing was hitting it.
    //
    // `ElevationCacheTest` is what holds this: it counts how many times an
    // unchanged elevated surface asks its shape for an outline across fourteen
    // recompositions, and the answer has to be none. Inline this call again and
    // it reports 28 — fourteen frames times the two layers of `Elevation.low`.
    val raised = remember(shadow, shape) { Modifier.elevation(shadow, shape) }
    CompositionLocalProvider(
        LocalContentColour provides contentColour,
        LocalContainerColour provides if (containerColour.alpha >= 1f) containerColour else LocalContainerColour.current,
    ) {
        Box(
            modifier = modifier
                .then(raised)
                .clip(shape)
                // After the clip, so it is the shape that stops a tap and not
                // its box: just off a capsule's rounded end is still the page.
                // See "A tap on a surface is the surface's" above.
                .then(if (keepsPresses) Modifier.keepsPresses() else HitTarget)
                .background(color = containerColour, shape = shape)
                .then(if (border != null) Modifier.border(border, shape) else Modifier),
            contentAlignment = contentAlignment,
            propagateMinConstraints = propagateMinConstraints,
            content = { content() },
        )
    }
}

/**
 * A pointer target that does nothing: what makes a surface stop a tap from
 * reaching what is beneath it. Hit testing ends at the first sibling that has a
 * pointer node under the finger, and this is one, everywhere on the surface.
 */
private val HitTarget = Modifier.pointerInput(Unit) {}

/**
 * Takes every press and release that reaches it and that nothing inside took.
 *
 * On the node itself rather than as a layer under the content, and conditional
 * rather than wholesale, and both are what keeps the content's own gestures
 * working. Children see each event first, so a button in the bar has consumed
 * its press by the time this looks, and is left alone. What reaches here
 * unconsumed is a press on nothing, and consuming it is what stops a clickable
 * container round the bar from starting a tap. Moves are never consumed: a
 * press that becomes a drag is the page's to scroll, and consuming moves is
 * also what cancels a child's tap, since a tap gives up when it sees its
 * movement taken.
 */
internal fun Modifier.keepsPresses(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            for (change in awaitPointerEvent().changes) {
                if (change.changedToDown() || change.changedToUp()) change.consume()
            }
        }
    }
}

/**
 * Draws [shadow] beneath this element, in [shape].
 *
 * Each [io.kontour.ui.theme.ShadowSpec] in the shadow becomes its own draw
 * layer, so a two-layer shadow — a tight contact shadow plus a wide ambient one
 * — composites the way it does in CSS. Ordered furthest-first so the tight,
 * darker layer lands on top.
 */
fun Modifier.elevation(shadow: Shadow, shape: Shape): Modifier {
    if (shadow.layers.isEmpty()) return this
    return shadow.layers.fold(this) { acc, spec ->
        acc.dropShadow(shape) {
            // DropShadowScope works in pixels and is itself a Density, so the
            // dp-based tokens convert here rather than at authoring time.
            radius = spec.blurRadius.toPx()
            spread = spec.spread.toPx()
            offset = Offset(spec.offsetX.toPx(), spec.offsetY.toPx())
            color = spec.colour
            alpha = spec.alpha
        }
    }
}

/** What a [Surface] takes by default. */
object SurfaceDefaults {
    /**
     * The content colour to use on [containerColour].
     *
     * Recognises the scheme's own grounds and returns their designed partner; for
     * anything else, picks whichever of the scheme's content colours has better
     * contrast.
     *
     * A **transparent** background is not a ground at all — whatever is behind the
     * surface is still the thing the content sits on — so it inherits rather than
     * deciding. Measuring it would treat it as black, since `contrastRatio` reads
     * the colour channels and a transparent colour's are `(0, 0, 0)`, and every
     * `Surface(containerColour = Color.Transparent)` would get near-white
     * content on a light page. That is the case a bar with no ground of its own
     * is in, and it is why `TabBar` could not go through `Surface` until now.
     */
    @Composable
    @ReadOnlyComposable
    fun contentColour(containerColour: Color): Color {
        val colours = Theme.colours
        if (containerColour.alpha == 0f) return LocalContentColour.current
        return when (containerColour) {
            colours.background,
            colours.surface,
            colours.surfaceSunken,
            colours.surfaceIndicator,
            colours.surfaceRaised,
            -> colours.content
            colours.surfaceInverse -> colours.onSurfaceInverse
            colours.primary -> colours.onPrimary
            colours.accent.solid -> colours.accent.onSolid
            colours.accent.container -> colours.accent.onContainer
            colours.success.solid -> colours.success.onSolid
            colours.success.container -> colours.success.onContainer
            colours.warning.solid -> colours.warning.onSolid
            colours.warning.container -> colours.warning.onContainer
            colours.danger.solid -> colours.danger.onSolid
            colours.danger.container -> colours.danger.onContainer
            colours.info.solid -> colours.info.onSolid
            colours.info.container -> colours.info.onContainer
            else -> contentColourFor(
                background = containerColour,
                light = if (colours.isDark) colours.content else colours.onPrimary,
                dark = if (colours.isDark) colours.onPrimary else colours.content,
            )
        }
    }
}

