package io.kontour.ui.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import io.kontour.ui.theme.Theme
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * How a surface is filled over its colour, as a second cue beside it: a
 * chart's series, an event on a calendar.
 *
 * Colour alone never carries meaning in this library, and where two things of
 * one kind are told apart by colour — two series, a tentative meeting and a
 * confirmed one — a pattern says it again for a reader who cannot see the
 * difference, or has asked not to rely on it. Drawn in a wash over the colour,
 * so it reads on any colour and in either scheme.
 *
 * The named ones are what a chart's series take, in [FillPatternDefaults.Series]'
 * order. Others are made with [lines], [dots] and, for anything else, [custom]:
 *
 * ```kotlin
 * // Hatching twice as open as the default, in heavier lines.
 * val open = FillPattern.lines(PatternDirection.Rising, spacing = 14.dp, weight = 2.dp)
 * // Dots in staggered rows, like a halftone.
 * val halftone = FillPattern.dots(spacing = 8.dp, staggered = true)
 * // A tile of an app's own, repeated: a little plus.
 * val plus = FillPattern.custom(DpSize(10.dp, 10.dp), key = "plus") { colour ->
 *     drawLine(colour, Offset(size.width / 2, 2f), Offset(size.width / 2, size.height - 2f), strokeWidth = 2f)
 *     drawLine(colour, Offset(2f, size.height / 2), Offset(size.width - 2f, size.height / 2), strokeWidth = 2f)
 * }
 * ```
 *
 * Asked for as *"can we add more patterns for things? Like more space between
 * lines, etc. Maybe even custom patterns?"* It was an enum of seven until then.
 *
 * @property spacing How far the pattern repeats along a side: between two
 *   lines running across it, or two dots. Unspecified for
 *   [FillPatternDefaults.Spacing].
 * @property weight How heavy its lines are, or how wide its dots. Unspecified
 *   for the theme's: [FillPatternDefaults.Weight], and twice that for a dot.
 */
@Immutable
class FillPattern private constructor(
    private val kind: Kind,
    private val directions: List<PatternDirection> = emptyList(),
    val spacing: Dp = Dp.Unspecified,
    val weight: Dp = Dp.Unspecified,
    private val staggered: Boolean = false,
    private val tile: DpSize = DpSize.Unspecified,
    private val key: Any? = null,
    private val draw: (DrawScope.(colour: Color) -> Unit)? = null,
) {
    private enum class Kind { Solid, Lines, Dots, Custom }

    /** Whether this draws nothing over the colour. */
    val isSolid: Boolean get() = kind == Kind.Solid

    /** Whether this is dots, whose [weight] is how wide each is. */
    internal val isDots: Boolean get() = kind == Kind.Dots

    override fun equals(other: Any?): Boolean =
        other is FillPattern && other.kind == kind && other.directions == directions && other.spacing == spacing &&
            other.weight == weight && other.staggered == staggered && other.tile == tile &&
            // A custom tile without a key is itself, by its drawing.
            (if (key != null || other.key != null) other.key == key else other.draw === draw)

    override fun hashCode(): Int {
        var result = kind.hashCode()
        result = 31 * result + directions.hashCode()
        result = 31 * result + spacing.hashCode()
        result = 31 * result + weight.hashCode()
        result = 31 * result + staggered.hashCode()
        result = 31 * result + tile.hashCode()
        result = 31 * result + (key ?: draw).hashCode()
        return result
    }

    /**
     * One repeat of this pattern in [colour], [spacingPx] by [spacingPx] — or
     * a custom tile's own size, by [scale] — with lines [weightPx] wide where
     * the pattern does not say.
     */
    internal fun tile(colour: Color, density: Density, spacingPx: Int, weightPx: Float, scale: Float): ImageBitmap? {
        return when (kind) {
            Kind.Solid -> null
            Kind.Custom -> {
                val tileWidth = with(density) { tile.width.toPx() }
                val tileHeight = with(density) { tile.height.toPx() }
                val width = (tileWidth * scale).roundToInt()
                val height = (tileHeight * scale).roundToInt()
                if (width <= 0 || height <= 0) return null
                ImageBitmap(width, height).also { image ->
                    // Drawn at the tile's own size and scaled onto the bitmap,
                    // so a tile drawn smaller is the same picture smaller.
                    // Drawn straight onto the smaller bitmap, a tile that put
                    // its mark at its middle in dp had it at the bitmap's
                    // corner, cut off, in a legend's key.
                    CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(image), Size(tileWidth, tileHeight)) {
                        drawContext.transform.scale(scaleX = width / tileWidth, scaleY = height / tileHeight, pivot = Offset.Zero)
                        draw?.invoke(this, colour)
                    }
                }
            }
            else -> ImageBitmap(spacingPx, spacingPx).also { image -> drawRepeat(Canvas(image), colour, spacingPx.toFloat(), weightPx) }
        }
    }

    private fun drawRepeat(canvas: Canvas, colour: Color, s: Float, weight: Float) {
        val paint = Paint().apply {
            color = colour
            strokeWidth = weight
            isAntiAlias = true
            style = PaintingStyle.Stroke
            strokeCap = StrokeCap.Square
        }
        if (kind == Kind.Dots) {
            paint.style = PaintingStyle.Fill
            val radius = weight / 2
            canvas.drawCircle(Offset(s / 2, s / 2), radius, paint)
            // Staggered: a dot at each corner as well, so the next row sits
            // halfway between this one's, and the four quarters join up
            // across the repeat into whole dots.
            if (staggered) for (x in listOf(0f, s)) for (y in listOf(0f, s)) canvas.drawCircle(Offset(x, y), radius, paint)
            return
        }
        for (direction in directions) {
            when (direction) {
                // Each diagonal is drawn three times, a repeat apart, so that
                // the part of a line that leaves one edge comes back in at the
                // opposite one and the repeat has no seam.
                PatternDirection.Rising -> listOf(-s, 0f, s).forEach { o -> canvas.drawLine(Offset(o, s), Offset(o + s, 0f), paint) }
                PatternDirection.Falling -> listOf(-s, 0f, s).forEach { o -> canvas.drawLine(Offset(o, 0f), Offset(o + s, s), paint) }
                PatternDirection.Horizontal -> canvas.drawLine(Offset(0f, s / 2), Offset(s, s / 2), paint)
                PatternDirection.Vertical -> canvas.drawLine(Offset(s / 2, 0f), Offset(s / 2, s), paint)
            }
        }
    }

    companion object {
        /** Nothing over the colour. */
        val Solid: FillPattern = FillPattern(Kind.Solid)

        /** Lines rising from the start, as a `/`. */
        val Diagonal: FillPattern = lines(PatternDirection.Rising)

        /** Lines falling from the start, as a `\`. */
        val ReverseDiagonal: FillPattern = lines(PatternDirection.Falling)

        /** Lines across. */
        val Horizontal: FillPattern = lines(PatternDirection.Horizontal)

        /** Lines up and down. */
        val Vertical: FillPattern = lines(PatternDirection.Vertical)

        /** Both diagonals, crossing. */
        val CrossHatch: FillPattern = lines(PatternDirection.Rising, PatternDirection.Falling)

        /** Lines across and up and down: squares. */
        val Grid: FillPattern = lines(PatternDirection.Horizontal, PatternDirection.Vertical)

        /** A dot in each square of the repeat. */
        val Dots: FillPattern = dots()

        /**
         * Lines running in each of [directions], [spacing] apart along a side,
         * [weight] heavy: two directions cross.
         */
        fun lines(
            vararg directions: PatternDirection,
            spacing: Dp = Dp.Unspecified,
            weight: Dp = Dp.Unspecified,
        ): FillPattern {
            require(directions.isNotEmpty()) { "A pattern of lines runs in at least one direction" }
            return FillPattern(Kind.Lines, directions.distinct(), spacing, weight)
        }

        /**
         * Dots [size] across, [spacing] apart; [staggered], each row sits
         * halfway between the last one's.
         */
        fun dots(spacing: Dp = Dp.Unspecified, size: Dp = Dp.Unspecified, staggered: Boolean = false): FillPattern =
            FillPattern(Kind.Dots, spacing = spacing, weight = size, staggered = staggered)

        /**
         * A tile [size] big, drawn once by [draw] in the colour it is given
         * and repeated. Drawn from its top start and laid in the window's own
         * space, as the others are, so neighbouring surfaces line up. Drawn
         * smaller, as a legend's key is, the whole tile is scaled, so [draw]
         * always draws at [size].
         *
         * @param key What makes two custom patterns the same one, so the tile
         *   is drawn once and kept. Without it the pattern is the same only as
         *   itself, and one made afresh in each composition draws its tile
         *   afresh too: remember it, or give it a key.
         */
        fun custom(size: DpSize, key: Any? = null, draw: DrawScope.(colour: Color) -> Unit): FillPattern =
            FillPattern(Kind.Custom, tile = size, key = key, draw = draw)
    }
}

/** Which way a [FillPattern]'s lines run. */
enum class PatternDirection {
    /** From the bottom start to the top end, as a `/`. */
    Rising,

    /** From the top start to the bottom end, as a `\`. */
    Falling,

    Horizontal,

    Vertical,
}

/**
 * [pattern] drawn over what is under it, in [colour], cut to [shape]: nothing for
 * [FillPattern.Solid]. After the node's own background, so the pattern lies on
 * its ground and under its content.
 *
 * ```kotlin
 * Box(Modifier.background(tone.container, shape).fillPattern(FillPattern.Diagonal, tone.onContainer.copy(alpha = 0.25f), shape))
 * ```
 *
 * The pattern is laid in the window's own space rather than each node's, so the
 * hatching of two surfaces side by side lines up and reads as one.
 *
 * @param colour The pattern's colour: a wash of the content's colour reads
 *   over the ground without fighting the text on it.
 * @param scale How big the repeat is drawn, as a share of the pattern's own:
 *   a key smaller than what it keys, as a chart's legend draws them.
 */
@Composable
fun Modifier.fillPattern(
    pattern: FillPattern,
    colour: Color,
    shape: Shape = RectangleShape,
    scale: Float = 1f,
): Modifier {
    if (pattern.isSolid) return this
    // One tile for the node, the last it drew: a node draws one pattern in one
    // colour at a time. It kept every tile it had ever drawn, and through a
    // theme's fade, which moves the colour on every frame, each patterned
    // event on a calendar kept a new bitmap a frame for as long as it was on
    // screen. Kept here rather than made afresh in the cache below, which a
    // change of size alone empties, as an event being dragged longer does.
    val brushes = remember { PatternBrushes(capacity = 1) }
    val weight = FillPatternDefaults.Weight
    return drawWithCache {
        val brush = brushes.brush(pattern, colour, this, weight, scale)
        val outline = shape.createOutline(size, layoutDirection, this)
        onDrawWithContent {
            if (brush != null) drawOutline(outline, brush)
            drawContent()
        }
    }
}

/**
 * What a [FillPattern] is drawn with where it does not say, for [fillPattern]
 * and for a canvas drawing its own with [PatternBrushes]: a chart's hatching
 * and a calendar event's are one pattern, and stay so.
 */
object FillPatternDefaults {
    /** How far a pattern repeats: tight enough to read on a sliver of a bar. */
    val Spacing: Dp = PatternSpacing

    /** How heavy a pattern's lines are: half again a border, so it reads as texture rather than as an edge. */
    val Weight: Dp
        @Composable @ReadOnlyComposable get() = Theme.sizing.borderWidth * PatternWeightShare

    /**
     * The fills a chart's series take with patterns on, in order: plain first,
     * so the first series is as it would be without them, then the seven that
     * stay apart at a glance, then three more, opened out, for a chart with
     * more series than that.
     */
    val Series: List<FillPattern> = listOf(
        FillPattern.Solid,
        FillPattern.Diagonal,
        FillPattern.Dots,
        FillPattern.CrossHatch,
        FillPattern.Horizontal,
        FillPattern.ReverseDiagonal,
        FillPattern.Vertical,
        FillPattern.Grid,
        OpenDots,
        OpenDiagonal,
    )
}

private val PatternSpacing = 7.dp
private const val PatternWeightShare = 1.5f

/** Staggered dots half as far apart again: the ninth series'. */
private val OpenDots = FillPattern.dots(spacing = PatternSpacing * 1.5f, staggered = true)

/** Hatching twice as open: the tenth series'. */
private val OpenDiagonal = FillPattern.lines(PatternDirection.Rising, spacing = PatternSpacing * 2)

/**
 * The tiles [FillPattern]s are drawn from, one brush for each pattern, colour
 * and size, made once and kept. For a canvas of an app's own, as a chart's.
 *
 * A shader rather than a clip and a loop of lines: a large area costs one fill
 * however many lines would cross it, and because the shader is laid in the
 * canvas's own space, the hatching of neighbouring shapes lines up, so a stack
 * reads as one surface with lines across it rather than as bricks.
 *
 * It keeps the last few dozen it made, the oldest going first, rather than
 * every one: room for each series of a chart to keep its own, and a bound for
 * a colour that moves, as one does on every frame of a theme's fade, where
 * each frame asks for tiles that nothing will ask for again.
 */
class PatternBrushes() {
    private var capacity = PatternTileCapacity

    /** One that keeps only the last [capacity] tiles: one, for a node that draws one pattern. */
    internal constructor(capacity: Int) : this() {
        this.capacity = capacity
    }

    // In the order they were made, so the oldest is the first to go.
    private val tiles = LinkedHashMap<TileKey, Brush>()

    /** How many tiles it holds now. */
    internal val size: Int get() = tiles.size

    /**
     * [pattern] in [colour] at [density], its lines [weight] heavy where it
     * does not say; `null` for [FillPattern.Solid].
     */
    fun brush(pattern: FillPattern, colour: Color, density: Density, weight: Dp): Brush? =
        brush(pattern, colour, density, weight, 1f)

    internal fun brush(pattern: FillPattern, colour: Color, density: Density, weight: Dp, scale: Float): Brush? {
        if (pattern.isSolid) return null
        val spacing = with(density) {
            max(MinRepeat, ((if (pattern.spacing.isSpecified) pattern.spacing else FillPatternDefaults.Spacing).toPx() * scale).roundToInt())
        }
        val lineWeight = with(density) {
            val own = pattern.weight.takeIf { it.isSpecified }
            // A dot is twice a line's weight across, unless it says.
            own?.toPx() ?: (weight.toPx() * if (pattern.isDots) DotShare else 1f)
        }
        val key = TileKey(pattern, colour, spacing, lineWeight, density.density, scale)
        tiles[key]?.let { return it }
        val image = pattern.tile(colour, density, spacing, lineWeight, scale) ?: return null
        while (tiles.size >= capacity) tiles.remove(tiles.keys.first())
        return ShaderBrush(ImageShader(image, TileMode.Repeated, TileMode.Repeated)).also { tiles[key] = it }
    }

    private data class TileKey(
        val pattern: FillPattern,
        val colour: Color,
        val spacing: Int,
        val weight: Float,
        val density: Float,
        val scale: Float,
    )
}

/** How many tiles a [PatternBrushes] keeps: room for a chart's ten series' three times over. */
internal const val PatternTileCapacity = 32

/** The fewest pixels a repeat is drawn in: below this a pattern is a smear. */
private const val MinRepeat = 3

/** A dot's width, as a share of a line's weight. */
private const val DotShare = 2.2f
