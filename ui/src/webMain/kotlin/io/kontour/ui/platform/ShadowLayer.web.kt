package io.kontour.ui.platform

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asSkiaPath
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.skiaCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import io.kontour.ui.theme.ShadowSpec
import org.jetbrains.skia.FilterBlurMode
import org.jetbrains.skia.MaskFilter
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.RRect
import org.jetbrains.skia.Rect

/**
 * **On the web, a shadow is blurred onto the canvas as it is drawn, with no
 * bitmap.** Compose's `dropShadow` rasterises the shape and its blur into a
 * bitmap the size of the element, and makes another whenever the element is
 * composed again or changes size. On the web that bitmap lives in Skia's heap and
 * is freed when the browser's garbage collector finalises its Kotlin object, which
 * Safari does long after Chrome would. An iPhone in November 2026 grew Skia's
 * heap by the size of a side sheet's two shadow layers, about 4 MB at three
 * pixels to the point, every time the sheet opened, from 18 MB to 65 MB in four
 * openings, and from the third Skia began failing to allocate the blur and
 * Compose threw "Can't wrap nullptr" on every frame, with the page left drawn
 * piled into its top left corner. Chrome's heap settled at 26 MB.
 *
 * Drawn here instead with Skia's mask-filter blur, which for a rounded rectangle
 * is worked out on the GPU from the shape itself, and for any other outline is
 * a blurred mask Skia keeps in its own cache. The one blur a layer needs is made
 * when its radius is first known and closed by hand when it changes or the
 * element leaves, so nothing here waits for the garbage collector.
 *
 * The same blur as Compose's for the same radius: Compose's own conversion from
 * a radius to a standard deviation, the spread as the outline grown by it, and
 * the offset as a translation.
 */
internal actual fun Modifier.shadowLayer(shape: Shape, spec: ShadowSpec): Modifier =
    this then CanvasShadowElement(shape, spec)

private class CanvasShadowElement(val shape: Shape, val spec: ShadowSpec) : ModifierNodeElement<CanvasShadowNode>() {
    override fun create() = CanvasShadowNode(shape, spec)

    override fun update(node: CanvasShadowNode) {
        node.shape = shape
        node.spec = spec
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "shadowLayer"
        properties["shape"] = shape
        properties["spec"] = spec
    }

    override fun equals(other: Any?) = other is CanvasShadowElement && other.shape == shape && other.spec == spec

    override fun hashCode() = 31 * shape.hashCode() + spec.hashCode()
}

private class CanvasShadowNode(var shape: Shape, var spec: ShadowSpec) : Modifier.Node(), DrawModifierNode {
    private var fill: Paint? = null
    private var stroke: Paint? = null
    private var blur: MaskFilter? = null
    private var blurRadius = Float.NaN

    override fun ContentDrawScope.draw() {
        val alpha = (spec.colour.alpha * spec.alpha).coerceIn(0f, 1f)
        if (alpha > 0f) drawShadow(alpha)
        drawContent()
    }

    private fun ContentDrawScope.drawShadow(alpha: Float) {
        val radius = spec.blurRadius.toPx()
        val spread = spec.spread.toPx().coerceAtLeast(0f)
        val filter = filterFor(radius)
        val paint = fill ?: Paint().apply { isAntiAlias = true }.also { fill = it }
        paint.color = spec.colour.copy(alpha = alpha).toArgb()
        paint.maskFilter = filter
        val outline = shape.createOutline(size, layoutDirection, this)
        val dx = spec.offsetX.toPx()
        val dy = spec.offsetY.toPx()
        drawIntoCanvas { composeCanvas ->
            val canvas = composeCanvas.skiaCanvas
            canvas.save()
            canvas.translate(dx, dy)
            when (outline) {
                is Outline.Rectangle -> {
                    val r = outline.rect
                    canvas.drawRect(Rect.makeLTRB(r.left - spread, r.top - spread, r.right + spread, r.bottom + spread), paint)
                }
                is Outline.Rounded -> {
                    val r = outline.roundRect
                    fun grow(v: Float) = (v + spread).coerceAtLeast(0f)
                    canvas.drawRRect(
                        RRect.makeComplexLTRB(
                            r.left - spread, r.top - spread, r.right + spread, r.bottom + spread,
                            floatArrayOf(
                                grow(r.topLeftCornerRadius.x), grow(r.topLeftCornerRadius.y),
                                grow(r.topRightCornerRadius.x), grow(r.topRightCornerRadius.y),
                                grow(r.bottomRightCornerRadius.x), grow(r.bottomRightCornerRadius.y),
                                grow(r.bottomLeftCornerRadius.x), grow(r.bottomLeftCornerRadius.y),
                            ),
                        ),
                        paint,
                    )
                }
                is Outline.Generic -> {
                    // A path is grown as Compose grows it: filled, then stroked
                    // twice the spread wide, both through the blur.
                    val path = outline.path.asSkiaPath()
                    canvas.drawPath(path, paint)
                    if (spread > 0f) {
                        val edge = stroke ?: Paint().apply { isAntiAlias = true; mode = PaintMode.STROKE }.also { stroke = it }
                        edge.color = paint.color
                        edge.maskFilter = filter
                        edge.strokeWidth = spread * 2f
                        canvas.drawPath(path, edge)
                    }
                }
            }
            canvas.restore()
        }
    }

    /** The blur for [radius], kept until the radius changes: Compose's conversion to a standard deviation. */
    private fun filterFor(radius: Float): MaskFilter? {
        if (radius == blurRadius) return blur
        blur?.close()
        blur = if (radius > 0f && radius.isFinite()) MaskFilter.makeBlur(FilterBlurMode.NORMAL, BlurSigmaScale * radius + 0.5f) else null
        blurRadius = radius
        return blur
    }

    override fun onDetach() {
        fill?.close()
        stroke?.close()
        blur?.close()
        fill = null
        stroke = null
        blur = null
        blurRadius = Float.NaN
    }
}

/** Compose's `BlurEffect`: a radius to a standard deviation, plus the half pixel it adds. */
private const val BlurSigmaScale = 0.57735f
