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
import androidx.compose.runtime.ReadOnlyComposable
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
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.a11y.contrastEdge
import io.kontour.ui.foundation.Surface
import io.kontour.ui.a11y.highContrast
import io.kontour.ui.platform.platformSupportsBackdropBlur
import io.kontour.ui.theme.LocalBackdropBlur
import io.kontour.ui.theme.Theme
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

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
 * A port of the site's `EdgeVignette.svelte`, minus its blur: Compose has no
 * portable backdrop filter to blur what is behind it. What survives is the part that does the work: a soft
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
    duration: Duration = 1_400.milliseconds,
): Modifier {
    val motion = Theme.motion
    if (motion.reduceMotion) return drawBehind { drawRect(colour) }

    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(duration.inWholeMilliseconds.toInt()),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerProgress",
    )

    return drawWithCache {
        // Travels one and a half widths, so the highlight is fully off screen
        // between passes rather than wrapping visibly at the edge.
        val travel = size.width * 1.5f
        val start = -size.width * 0.5f
        // Built once, where the pass starts, and moved each frame rather than
        // rebuilt there — see `Skeleton`, which does the same.
        val band = Brush.horizontalGradient(
            colors = listOf(Color.Transparent, highlight, Color.Transparent),
            startX = start,
            endX = start + size.width * 0.5f,
        )
        onDrawBehind {
            drawRect(colour)
            val along = travel * progress
            translate(along, 0f) {
                drawRect(brush = band, topLeft = Offset(-along, 0f), size = size)
            }
        }
    }
}
