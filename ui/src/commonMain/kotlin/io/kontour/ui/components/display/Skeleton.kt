package io.kontour.ui.components.display

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.theme.Theme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * A placeholder in the shape of the content that is loading.
 *
 * ```
 * if (departures == null) SkeletonListItem() else DepartureRow(departures)
 * ```
 *
 * Better than a spinner when you know the shape of what is coming, because the
 * layout does not jump when the data lands — the skeleton is already the right
 * size. Worse than a spinner when you do not, because a skeleton that turns out
 * to be the wrong shape is a bigger jolt than a spinner disappearing.
 *
 * Skeletons are hidden from the accessibility tree entirely. There is nothing to
 * announce, and a screen reader walking a dozen unlabelled boxes is noise; the
 * container should carry the loading announcement instead.
 *
 * The shimmer stops under reduced motion, leaving a static block — continuous
 * looping motion is exactly the category that preference exists to stop.
 */
@Composable
fun Skeleton(
    modifier: Modifier = Modifier,
    shape: Shape = Theme.shapes.extraSmall,
    /**
     * Which way the wipe travels, in degrees clockwise from left-to-right.
     *
     * `0` is the default and the one everything shipped with. `90` runs top to
     * bottom, `180` right to left, and the diagonals in between are what a large
     * block wants — a wipe straight across a tall card reads as a horizontal
     * seam rather than as light moving over a surface, and the angle is the only
     * thing that fixes it.
     *
     * The band's width and travel are measured along the angle rather than
     * across the box, so the sweep covers the whole shape at every angle and
     * takes the same time to cross it. At `0` the arithmetic reduces to exactly
     * the horizontal gradient this used to draw.
     */
    angle: Float = 0f,
) {
    val colors = Theme.colors
    val reduceMotion = Theme.motion.reduceMotion

    val base = colors.surfaceSunken
    val highlight = if (colors.isDark) {
        colors.outline
    } else {
        // Lighter than the base rather than darker: a dark band reads as content.
        colors.surface
    }

    val transition = rememberInfiniteTransition(label = "skeleton")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
        ),
        label = "skeletonSweep",
    )

    Box(
        modifier
            .clearAndSetSemantics { }
            .clip(shape)
            .then(
                if (reduceMotion) {
                    Modifier.background(base)
                } else {
                    Modifier.drawWithCache {
                        // The wipe's own axis, and everything measured along it.
                        //
                        // A box has no single "width" from an angle's point of
                        // view: the distance light has to travel to cross it is
                        // the box's *projection* onto the direction of travel,
                        // which is where the two absolutes come from. Get that
                        // wrong and a diagonal wipe either stops short of the
                        // far corner or spends half its time off the edge.
                        val radians = angle * PI.toFloat() / 180f
                        val dx = cos(radians)
                        val dy = sin(radians)
                        val span = abs(size.width * dx) + abs(size.height * dy)
                        val bandWidth = span * BandShare
                        val travel = span + bandWidth
                        val centre = Offset(size.width / 2f, size.height / 2f)

                        onDrawBehind {
                            // Measured from the centre outwards, so the band
                            // enters one edge and leaves the other whatever the
                            // angle. At 0° this is `-bandWidth + travel * sweep`
                            // offset from the left edge, which is what it was.
                            val head = -span / 2f - bandWidth + travel * sweep
                            drawRect(
                                brush = Brush.linearGradient(
                                    colorStops = arrayOf(
                                        0f to base,
                                        0.5f to highlight,
                                        1f to base,
                                    ),
                                    start = centre + Offset(dx * head, dy * head),
                                    end = centre + Offset(
                                        dx * (head + bandWidth),
                                        dy * (head + bandWidth),
                                    ),
                                ),
                            )
                        }
                    }
                }
            )
    )
}

/**
 * Skeleton lines standing in for a paragraph.
 *
 * The last line is shortened, because real text rarely fills its final line and
 * a block of equal-length bars reads as a table rather than as prose.
 */
@Composable
fun SkeletonText(
    modifier: Modifier = Modifier,
    lines: Int = 3,
    lineHeight: Dp = 12.dp,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(8.dp),
    lastLineFraction: Float = 0.6f,
) {
    Column(
        modifier.clearAndSetSemantics { },
        verticalArrangement = verticalArrangement,
    ) {
        repeat(lines) { index ->
            val isLast = index == lines - 1
            Skeleton(
                Modifier
                    .then(
                        if (isLast && lines > 1) {
                            Modifier.fillMaxWidth(lastLineFraction)
                        } else {
                            Modifier.fillMaxWidth()
                        }
                    )
                    .height(lineHeight),
                shape = Theme.shapes.pill,
            )
        }
    }
}

/**
 * A skeleton shaped like a list row — leading circle, two lines of text.
 *
 * Matches the proportions of a typical `ListItem`, so a list of these does not
 * reflow when the real rows arrive.
 */
@Composable
fun SkeletonListItem(
    modifier: Modifier = Modifier,
    showLeading: Boolean = true,
    supportingLine: Boolean = true,
) {
    Row(
        modifier
            .clearAndSetSemantics { }
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Theme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showLeading) {
            Skeleton(Modifier.size(40.dp), shape = Theme.shapes.pill)
        }
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Skeleton(Modifier.fillMaxWidth(0.55f).height(14.dp), shape = Theme.shapes.pill)
            if (supportingLine) {
                Skeleton(Modifier.fillMaxWidth(0.8f).height(12.dp), shape = Theme.shapes.pill)
            }
        }
    }
}

/**
 * How much of the crossing distance the lit band takes up.
 *
 * Three fifths, which is what the horizontal wipe always was. Narrower reads as
 * a glare passing over; this reads as a surface being lit, which is the softer
 * of the two and the right one for something that repeats forever.
 */
private const val BandShare = 0.6f
