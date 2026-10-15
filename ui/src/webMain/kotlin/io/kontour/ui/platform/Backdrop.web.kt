package io.kontour.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.IRect
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.Matrix33
import org.jetbrains.skia.SamplingMode

internal actual val platformSupportsBackdropBlur: Boolean = true

/** Skia's: see [platformReducedBlur]'s table. */
internal actual val platformBlursReduced: Boolean = true

/**
 * The blur, cropped and clamped to the drawn content, then scaled back up.
 *
 * The scale maps each output pixel's centre between the input's outermost
 * pixel centres, so the bilinear sample never reaches past the crop into the
 * transparent beyond it, and the edge stays as opaque as the content was.
 */
internal actual fun platformReducedBlur(
    sigma: Float,
    cropWidth: Int,
    cropHeight: Int,
    scaleX: Float,
    scaleY: Float,
): RenderEffect? {
    val blur = ImageFilter.makeBlur(sigma, sigma, FilterTileMode.CLAMP, null, IRect.makeWH(cropWidth, cropHeight))
    val up = Matrix33(scaleX, 0f, -0.5f * scaleX, 0f, scaleY, -0.5f * scaleY, 0f, 0f, 1f)
    return ImageFilter.makeMatrixTransform(up, SamplingMode.LINEAR, blur).asComposeRenderEffect()
}

/** Zero: A browser's own chrome is outside the viewport, and a page has no bar of its own to report. */
@Composable
internal actual fun platformOpaqueBottomInset(): Dp = 0.dp
