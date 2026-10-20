package io.kontour.ui.charts

import io.kontour.ui.interaction.rememberToggleFeedback
import io.kontour.ui.input.pointerCursor
import io.kontour.ui.input.focusRing
import io.kontour.ui.a11y.minimumTouchTarget
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.alpha
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import io.kontour.ui.a11y.contrastEdge
import io.kontour.ui.components.datetime.ActivityCalendarDefaults
import io.kontour.ui.foundation.Text
import io.kontour.ui.foundation.fillPattern
import io.kontour.ui.theme.Theme

/**
 * A chart's legend: each named series, with a swatch drawn the way the series
 * is — a dash of its line with its symbol, or a square of its fill — so that a
 * reader matches a series by its pattern as well as its colour.
 *
 * Kept out of what a screen reader hears, which names every series already in
 * the chart's summary; read again, the legend would be the same words twice.
 * Except with a [visibility] to answer to: then each entry is a switch, read as
 * one — "Route 950, checkbox, checked" — and a hidden series' entry stays,
 * faded, to be shown again.
 */
@Composable
internal fun ChartLegend(
    model: ChartModel,
    colours: ChartColours,
    patterns: Boolean,
    stacked: Boolean,
    scale: CellScale? = null,
    visibility: ChartSeriesVisibility? = null,
) {
    val named = model.series.filter { it.name.isNotBlank() }
    val spacing = Theme.spacing
    val toggled = rememberToggleFeedback()
    val focusShape = Theme.shapes.extraSmall
    val item: @Composable (ChartSeries) -> Unit = { series ->
        val shown = visibility?.isShown(series) ?: true
        val interactions = remember { MutableInteractionSource() }
        val toggle = if (visibility == null) {
            Modifier
        } else {
            Modifier
                .minimumTouchTarget()
                .focusRing(interactions, focusShape)
                .pointerCursor()
                .toggleable(
                    value = shown,
                    interactionSource = interactions,
                    indication = null,
                    role = Role.Checkbox,
                    onValueChange = { on ->
                        toggled(on)
                        if (on) visibility.show(series) else visibility.hide(series)
                    },
                )
        }
        Row(
            toggle.then(if (shown) Modifier else Modifier.alpha(HiddenAlpha)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.xxs),
        ) {
            SeriesSwatch(model, series, colours, patterns)
            Text(series.name, style = Theme.typography.labelMedium)
        }
    }
    val hidden = Modifier.clearAndSetSemantics { }
    // Said only where it can be pressed; otherwise the summary has said it.
    val heard = if (visibility != null) Modifier else hidden
    val scaleKey: @Composable () -> Unit = { scale?.let { Box(hidden) { ScaleKey(it, colours.ramp) } } }
    if (stacked) {
        Column(heard, verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
            named.forEach { series -> key(series) { item(series) } }
            scaleKey()
        }
    } else {
        FlowRow(
            modifier = heard,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.xxs),
        ) {
            named.forEach { series -> key(series) { item(series) } }
            scaleKey()
        }
    }
}

/**
 * A heat map's key: its least value, the ramp, and its most, the ramp running
 * the way the line reads, so in a right-to-left language its least is on the
 * right, beside the number that names it.
 */
@Composable
private fun ScaleKey(scale: CellScale, ramp: List<Color>) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val style = Theme.typography.labelMedium
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Theme.spacing.xxs)) {
        Text(scale.write(scale.low), style = style)
        Box(
            Modifier
                .size(width = ScaleWidth, height = SwatchSize)
                .background(Brush.horizontalGradient(if (rtl) ramp.reversed() else ramp), Theme.shapes.extraSmall),
        )
        Text(scale.write(scale.high), style = style)
    }
}

/**
 * A series' key, drawn the way the series is — a dash of its line with its
 * symbol, or a square of its fill — for the legend and a tooltip's rows alike,
 * so that a series is told by the same mark wherever it is named.
 */
@Composable
internal fun SeriesSwatch(model: ChartModel, series: ChartSeries, colours: ChartColours, patterns: Boolean) {
    val mark = model.marks.firstOrNull { mark -> mark.runs.any { it.series == series } }
    val look = lookOf(model.slotOf(series), colours.series, patterns, mark?.style, series.style)
    // Read once: a series with no mark yet has a square swatch, as a bar's.
    val kind = mark?.kind
    if (mark == null || (kind != MarkKind.Line && kind != MarkKind.Point)) {
        FillSwatch(look, colours)
        return
    }
    val markSymbol = mark.symbol
    val symbolShown = kind == MarkKind.Point || mark.showPoints || patterns
    val sizing = Theme.sizing
    Spacer(
        Modifier
            .size(width = LineSwatchWidth, height = SwatchSize)
            .drawBehind {
                if (kind == MarkKind.Line) {
                    val width = sizing.borderWidthStrong.toPx()
                    val path = Path().apply {
                        moveTo(0f, size.height / 2)
                        lineTo(size.width, size.height / 2)
                    }
                    drawPath(path, look.colour, style = Stroke(width, cap = StrokeCap.Round, pathEffect = look.line.effect(width)))
                }
                if (symbolShown) {
                    val symbol = Path()
                    addSymbol(symbol, markSymbol ?: look.symbol, Offset(size.width / 2, size.height / 2), ChartDefaults.PointSize.toPx())
                    drawPath(symbol, look.colour)
                }
            },
    )
}

/**
 * A square of [look]'s fill, its pattern over its colour: a bar's, an area's
 * or a sector's key, hatched by the library's own [fillPattern] as an event on
 * a calendar is.
 *
 * No edge at the high contrast tier: the series' colours hold 4.5:1 against
 * the ground there already, and a 12dp swatch given one was mostly edge, its
 * colour and its pattern a speck inside a grey ring. A square, as the
 * legend's were; the theme's smallest shape made it a circle.
 */
@Composable
internal fun FillSwatch(look: SeriesLook, colours: ChartColours) {
    Box(
        Modifier
            .size(SwatchSize)
            .background(look.colour, SwatchShape)
            .fillPattern(look.fill, colours.patternInk, SwatchShape, scale = SwatchScale),
    )
}

/**
 * A square of one [shade]: a heat map cell's key, in its tooltip. Edged at the
 * high contrast tier, unlike a series' swatch, since the pale end of the ramp
 * does not hold contrast against the ground.
 */
@Composable
internal fun ShadeSwatch(shade: Color) {
    val shape = SwatchShape
    val edge = contrastEdge()
    Box(
        Modifier
            .size(SwatchSize)
            .background(shade, shape)
            .then(if (edge != null) Modifier.border(edge, shape) else Modifier),
    )
}

/** How faint a hidden series' entry is drawn: there, to be shown again, and plainly not on the chart. */
private const val HiddenAlpha = 0.38f

/**
 * A legend's swatch: the size of a small label's capital letters, so it sits
 * in the line of text it introduces. A line's is longer, to have room for its
 * dashes and its symbol together.
 */
private val SwatchSize = 12.dp
private val LineSwatchWidth = 20.dp

/**
 * A swatch's shape: an activity calendar's cell, the library's corner curve at
 * a fifth of its side, so it reads as a square, as a key's should, and a heat
 * map's key is the shape of an activity calendar's.
 */
private val SwatchShape = ActivityCalendarDefaults.CellShape

/** A heat map key's ramp: long enough to see its shades change, short enough to sit in a legend's line. */
private val ScaleWidth = 72.dp

/** A swatch's pattern, finer than the plot's, so that two repeats fit in its 12dp. */
private const val SwatchScale = 6f / 7f
