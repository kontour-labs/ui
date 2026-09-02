package io.kontour.ui.motion

import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.foundation.Surface
import io.kontour.ui.a11y.highContrast
import io.kontour.ui.platform.platformSupportsBackdropBlur
import io.kontour.ui.theme.LocalBackdropBlur
import io.kontour.ui.theme.Theme

/**
 * A translucent panel, for a bar floating over content.
 *
 * ```kotlin
 * GlassSurface(shape = Theme.shapes.pill) {
 *     NavBar(items, selectedIndex = current)
 * }
 * ```
 *
 * ### About the blur
 *
 * Compose has **no portable backdrop filter**. `Modifier.blur` blurs a layer's
 * own content, not what is behind it, and there is no common equivalent of CSS's
 * `backdrop-filter` or iOS's `UIVisualEffectView`. So this draws a translucent
 * tint and a hairline highlight, which is most of what reads as glass, and
 * offers real frosting only where you can afford it.
 *
 * Pass [backdrop] to get an actual blur: the content is composed **twice**, once
 * blurred and clipped to the panel's shape, once normally behind it. That is
 * genuinely frosted glass and genuinely costs double. Fine for a static image or
 * a gradient; not for a live map, which is exactly where a floating bar sits, so
 * the default leaves it out.
 *
 * The marketing site's header uses `backdrop-filter: blur(14px)`; on the web
 * that is free and here it is not. The translucent form is the honest
 * equivalent.
 *
 * The blur itself is now the library's own — `BlurEffect` through a
 * `graphicsLayer`, the same call [io.kontour.ui.overlay.BackdropStyle] makes —
 * rather than `Modifier.blur`. So it answers to the theme's `backdropBlur`
 * switch and to `platformSupportsBackdropBlur`, and a target that cannot blur
 * falls back to the translucent tint everywhere at once instead of here and
 * there.
 *
 * ### A modal is not this problem
 *
 * All of the above is about a panel that is a sibling *above* live content:
 * there is no single node holding "everything behind the bar", so there is
 * nothing to blur. Behind a modal there is one —
 * [io.kontour.ui.overlay.OverlayHost] composes the whole application as a single
 * full-size sibling of the overlay stack — so every dimmed overlay does blur
 * what is behind it, properly, with nothing composed twice. See
 * [io.kontour.ui.overlay.BackdropStyle]. This is the hard case; that one is
 * not.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = Theme.shapes.large,
    tint: Color = Theme.colours.surfaceRaised,
    /**
     * How much of the tint is laid over what is behind — so how *little* of it
     * shows through.
     *
     * `0f` is a pane of clear glass and `1f` is a painted panel. It used to
     * default to `0.72`, which is nearly opaque: the panel read as a solid bar
     * with a slightly wrong colour rather than as something you could see
     * through, and the frosting behind it was doing almost no work. Now
     * [GlassSurfaceDefaults.Alpha].
     *
     * **Except under high contrast**, where it goes back up to
     * [GlassSurfaceDefaults.OpaqueAlpha]. Glass is a contrast hazard by
     * definition — the text on it is sitting over whatever happens to be
     * underneath — and a user who has asked the system for more contrast has
     * asked for exactly the thing translucency takes away.
     */
    alpha: Float = if (highContrast()) {
        GlassSurfaceDefaults.OpaqueAlpha
    } else {
        GlassSurfaceDefaults.Alpha
    },
    borderAlpha: Float = 0.35f,
    blurRadius: Dp = GlassSurfaceDefaults.BlurRadius,
    backdrop: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    // The same blur the overlay backdrop uses, gated the same way.
    //
    // It was `Modifier.blur`, which is a different thing with the same name: on
    // a target without the render effect it quietly draws nothing at all, and
    // it takes no notice of the theme's own `backdropBlur` switch — so an app
    // that had turned blur off across the whole library went on paying for it
    // here. `BlurEffect` through a `graphicsLayer` is what
    // `io.kontour.ui.overlay.BackdropStyle` applies, and using it means one
    // answer to "does this platform blur" rather than two.
    val blurring = LocalBackdropBlur.current && platformSupportsBackdropBlur
    val radiusPx = with(LocalDensity.current) { blurRadius.toPx() }

    Box(modifier) {
        if (backdrop != null) {
            Box(
                Modifier
                    .matchParentSize()
                    .clip(shape)
                    .graphicsLayer {
                        renderEffect = if (blurring && radiusPx > 0f) {
                            BlurEffect(radiusX = radiusPx, radiusY = radiusPx)
                        } else {
                            null
                        }
                    }
            ) {
                backdrop()
            }
        }

        Surface(
            modifier = Modifier.matchParentSize(),
            shape = shape,
            colour = tint.copy(alpha = alpha),
            // A hairline of the content colour along the edge. On real frosted
            // glass this is the refraction at the bevel; here it is the thing
            // that stops a translucent rectangle reading as a bug.
            border = androidx.compose.foundation.BorderStroke(
                Theme.sizing.borderWidth,
                Theme.colours.content.copy(alpha = borderAlpha * 0.2f),
            ),
            content = {},
        )

        content()
    }
}

object GlassSurfaceDefaults {

    /**
     * How much tint a pane carries by default.
     *
     * Well under half again from the 0.72 it shipped with. The number that
     * reads as glass rather than as a tinted panel is lower than it feels like
     * it should be: at 0.72 the eye sees a solid bar, and the difference
     * between 0.6 and 0.55 is the difference between "the bar is a bit
     * see-through" and "there is something behind the bar".
     */
    const val Alpha: Float = 0.58f

    /** What [Alpha] becomes under [io.kontour.ui.theme.ContrastLevel.High]. */
    const val OpaqueAlpha: Float = 0.94f

    /**
     * Tuned for a small panel over a busy background, and smaller than
     * `BackdropDefaults.BlurRadius` for the reason recorded there: across a
     * whole screen a radius this size reads as a smudge rather than as
     * distance.
     */
    val BlurRadius: Dp = 14.dp
}

/**
 * The radial glow both websites use behind a hero.
 *
 * ```kotlin
 * Box(Modifier.fillMaxSize().atmosphere()) {
 *     Hero()
 * }
 * ```
 *
 * **Opt-in, and never baked into `Surface`.** It is a treatment for one or two
 * screens — a hero, an onboarding page — and a background gradient behind every
 * list in the app is a background gradient nobody notices and everybody pays for.
 *
 * Drawn from the accent and brand colours at low alpha, so it follows the theme
 * rather than being a fixed image, and it inverts sensibly in dark mode without
 * a second asset.
 *
 * @param intensity Scales every layer. Past `1f` the glow starts competing with
 *   the content it is behind.
 */
@Composable
fun Modifier.atmosphere(
    intensity: Float = 1f,
    primary: Color = Theme.colours.accent.solid,
    secondary: Color = Theme.colours.brand,
): Modifier = drawWithCache {
    val glow = buildAtmosphere(size, primary, secondary, intensity)
    onDrawBehind { glow.forEach { it.draw(this) } }
}

/** One radial wash in an [atmosphere]. */
@Immutable
private class Glow(
    private val brush: Brush,
    private val topLeft: Offset,
    private val size: Size,
) {
    fun draw(scope: DrawScope) {
        scope.drawRect(brush = brush, topLeft = topLeft, size = size)
    }
}

/**
 * Two offset radial washes, one warm and one cool.
 *
 * Positioned off the corners rather than centred: a glow centred behind content
 * puts its brightest point exactly where the text is, which is the one place it
 * should not be.
 */
private fun buildAtmosphere(
    size: Size,
    primary: Color,
    secondary: Color,
    intensity: Float,
): List<Glow> {
    if (size.minDimension <= 0f) return emptyList()
    // Off the *smaller* dimension. Sizing off the larger one makes each glow
    // bigger than the box on anything wide, and two overlapping washes that
    // both cover everything average out to one flat tint.
    val radius = size.minDimension * 1.1f

    return listOf(
        Glow(
            brush = Brush.radialGradient(
                colors = listOf(
                    primary.copy(alpha = 0.22f * intensity),
                    Color.Transparent,
                ),
                center = Offset(size.width * 0.05f, -size.height * 0.1f),
                radius = radius,
            ),
            topLeft = Offset.Zero,
            size = size,
        ),
        Glow(
            brush = Brush.radialGradient(
                colors = listOf(
                    secondary.copy(alpha = 0.18f * intensity),
                    Color.Transparent,
                ),
                center = Offset(size.width * 0.95f, size.height * 1.1f),
                radius = radius,
            ),
            topLeft = Offset.Zero,
            size = size,
        ),
    )
}

/**
 * Fades the content out against the top and bottom edges of a scrolling page.
 *
 * A port of the site's `EdgeVignette.svelte`, minus its blur — see
 * [GlassSurface] for why. What survives is the part that does the work: a soft
 * falloff at each edge, appearing only when there is content past it.
 *
 * For a scrolling *list*, use
 * [io.kontour.ui.components.list.fadingEdges] instead: it takes a scroll state
 * and knows when each edge has more content, where this takes the fractions
 * directly and is for a page whose scroll you are tracking yourself.
 *
 * @param topFade 0 to 1. The site drives this from `scrollY / 120`.
 */
@Composable
fun Modifier.edgeVignette(
    topFade: Float,
    bottomFade: Float,
    height: Dp = 80.dp,
    colour: Color = Theme.colours.background,
): Modifier {
    val heightPx = with(androidx.compose.ui.platform.LocalDensity.current) { height.toPx() }
    // Over the content, not behind it. `drawBehind` paints before the content
    // and the content then covers the gradient entirely — a vignette that is
    // present in the layer and invisible on the screen.
    return drawWithContent {
        drawContent()
        if (topFade > 0f) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(colour.copy(alpha = topFade), Color.Transparent),
                    startY = 0f,
                    endY = heightPx,
                ),
                size = Size(size.width, heightPx),
            )
        }
        if (bottomFade > 0f) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, colour.copy(alpha = bottomFade)),
                    startY = size.height - heightPx,
                    endY = size.height,
                ),
                topLeft = Offset(0f, size.height - heightPx),
                size = Size(size.width, heightPx),
            )
        }
    }
}

/**
 * A travelling highlight, for a loading placeholder.
 *
 * ```kotlin
 * Box(Modifier.size(120.dp, 16.dp).clip(Theme.shapes.small).shimmer())
 * ```
 *
 * Used by [io.kontour.ui.components.display.Skeleton]; exposed for placeholders
 * the skeleton components do not cover.
 *
 * **Stops under reduced motion**, holding a flat tone instead. A looping
 * animation is the category the preference exists to stop, and a shimmer runs
 * for as long as the load takes.
 */
@Composable
fun Modifier.shimmer(
    colour: Color = Theme.colours.surfaceSunken,
    highlight: Color = Theme.colours.surface,
    durationMillis: Int = 1_400,
): Modifier {
    val motion = Theme.motion
    if (motion.reduceMotion) return drawBehind { drawRect(colour) }

    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerProgress",
    )

    return drawBehind {
        drawRect(colour)
        // Travels one and a half widths, so the highlight is fully off screen
        // between passes rather than wrapping visibly at the edge.
        val travel = size.width * 1.5f
        val start = -size.width * 0.5f + travel * progress
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.Transparent, highlight, Color.Transparent),
                startX = start,
                endX = start + size.width * 0.5f,
            )
        )
    }
}
