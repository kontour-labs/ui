package io.kontour.ui.foundation

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import io.kontour.ui.theme.Theme

/**
 * Draws an icon, tinted to the surrounding content colour, at its true
 * proportions.
 *
 * The design system ships **no icon set**. Components take an [ImageVector] or
 * a [Painter], so the choice of icon library stays an application decision and
 * `:ui` does not drag a few hundred kilobytes of glyphs into every consumer.
 *
 * ```
 * Icon(FontAwesome.Solid.Search, contentDescription = "Search")
 * Icon(MyIcons.Bus, contentDescription = null)   // decorative, next to a label
 * ```
 *
 * ### Why this is not just `Image(vector)`
 *
 * Some icon sets — FontAwesome among them — declare every glyph as square
 * (`defaultWidth` and `defaultHeight` both 24dp) while giving them *different*
 * viewports: FontAwesome draws on a 512-unit-tall grid whose width varies with
 * the glyph, so `times` is 352×512 and `star` is 576×512.
 *
 * `VectorPainter` scales each axis independently to fill the size it is given.
 * Drawing a 352×512 viewport into a square box therefore stretches it
 * horizontally by 1.45× — the cross comes out visibly fat, with strokes that
 * are heavier across than down.
 *
 * So [Icon] reads the viewport's real aspect ratio and fits the glyph inside a
 * square layout box at its true proportions. Layout stays square, so a row of
 * icons spaces evenly; the glyph inside is never distorted. The fit normalises
 * on the longer axis, so a wide glyph renders slightly shorter than its
 * neighbours rather than overflowing into them.
 *
 * With Tabler — what this repo actually uses — the correction is a no-op, and
 * `IconMetricsDiagnostic` asserts that it stays one.
 *
 * @param contentDescription What a screen reader announces. **Required, and
 *   nullable on purpose** — passing `null` is how you say "this is decorative",
 *   which is a claim worth making explicitly rather than by omission. Pass
 *   `null` when the icon sits beside a label that already says the same thing;
 *   pass a description when the icon is the only thing conveying meaning.
 * @param tint Defaults to [LocalContentColour], so an icon inside a [Surface]
 *   picks up a legible colour automatically. `Color.Unspecified` disables
 *   tinting entirely — for multi-colour artwork.
 * @param size The size of the square layout box. The glyph is fitted inside it.
 */
@Composable
fun Icon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColour.current,
    size: Dp = Theme.sizing.iconMedium,
) {
    IconBox(
        painter = rememberVectorPainter(imageVector),
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint,
        boxSize = size,
        drawSize = fitToBox(
            box = size,
            aspectRatio = imageVector.viewportWidth / imageVector.viewportHeight,
        ),
    )
}

/**
 * [Painter] overload.
 *
 * A painter only exposes an intrinsic size, not a viewport, so the aspect
 * correction described on the [ImageVector] overload uses that instead. For a
 * painter whose intrinsic size is honest — a bitmap, a correctly-declared
 * vector — the result is identical.
 */
@Composable
fun Icon(
    painter: Painter,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColour.current,
    size: Dp = Theme.sizing.iconMedium,
) {
    val intrinsic = painter.intrinsicSize
    val aspect = if (
        intrinsic.isSpecified() && intrinsic.width > 0f && intrinsic.height > 0f
    ) {
        intrinsic.width / intrinsic.height
    } else {
        1f
    }

    IconBox(
        painter = painter,
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint,
        boxSize = size,
        drawSize = fitToBox(box = size, aspectRatio = aspect),
    )
}

@Composable
private fun IconBox(
    painter: Painter,
    contentDescription: String?,
    modifier: Modifier,
    tint: Color,
    boxSize: Dp,
    drawSize: DpSize,
) {
    val resolvedTint = if (tint == Color.Unspecified) Theme.colours.content else tint
    val tintFilter = if (tint == Color.Unspecified) null else ColorFilter.tint(resolvedTint)

    val semantics = if (contentDescription != null) {
        Modifier.clearAndSetSemantics { this.contentDescription = contentDescription }
    } else {
        // No semantics at all, rather than an empty description: an icon with a
        // blank label is still a node a screen reader stops on.
        Modifier.clearAndSetSemantics {}
    }

    Box(
        modifier = modifier.then(semantics).size(boxSize),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painter,
            contentDescription = null,
            colorFilter = tintFilter,
            // FillBounds, not Fit: the box has already been sized to the glyph's
            // true aspect ratio, so filling it scales both axes by the same
            // factor. Fit would letterbox a second time against the painter's
            // (square, and in FontAwesome's case wrong) intrinsic size.
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.size(drawSize),
        )
    }
}

/**
 * The largest box of [aspectRatio] that fits inside a [box]×[box] square.
 *
 * Internal rather than private so `IconScalingTest` can pin the maths — it is
 * the part of [Icon] most likely to be "simplified" back into a bug.
 */
internal fun fitToBox(box: Dp, aspectRatio: Float): DpSize = when {
    !aspectRatio.isFinite() || aspectRatio <= 0f -> DpSize(box, box)
    aspectRatio >= 1f -> DpSize(box, box / aspectRatio)
    else -> DpSize(box * aspectRatio, box)
}

private fun androidx.compose.ui.geometry.Size.isSpecified(): Boolean =
    this != androidx.compose.ui.geometry.Size.Unspecified
