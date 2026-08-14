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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.kontour.ui.theme.Theme

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
                        val bandWidth = size.width * 0.6f
                        val travel = size.width + bandWidth
                        onDrawBehind {
                            val start = -bandWidth + travel * sweep
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    colorStops = arrayOf(
                                        0f to base,
                                        0.5f to highlight,
                                        1f to base,
                                    ),
                                    startX = start,
                                    endX = start + bandWidth,
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
    spacing: Dp = 8.dp,
    lastLineFraction: Float = 0.6f,
) {
    Column(
        modifier.clearAndSetSemantics { },
        verticalArrangement = Arrangement.spacedBy(spacing),
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
