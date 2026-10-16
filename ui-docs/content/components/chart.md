# `Chart`

*Also on this page: `SectorChart`, `Sparkline`, `ChartAxis`, `ChartSeries`, `SeriesStyle`, `ChartColours`, `ChartStrings`, `rememberChartSelectionState`.*

<!--sample:ChartBasics-->
```kotlin
// A line per route, across the days of a week, with a reference line.
val selection = rememberChartSelectionState<LocalDate>()
Chart(
    xAxis = ChartAxis.date(),
    yAxis = ChartAxis.number(title = "Riders"),
    modifier = Modifier.fillMaxWidth().height(220.dp),
    selection = selection,
    contentDescription = "Riders this week, by route",
) {
    line(
        week,
        x = { it.day },
        y = { it.riders },
        series = { ChartSeries("Route ${it.route}") },
        interpolation = ChartInterpolation.Monotone,
        showPoints = true,
    )
    rule(y = 800, label = "Capacity")
}
```

Numbers drawn along two axes. A chart is made of **marks**, as many and as mixed
as it needs: a `line` through the days and a `rule` at capacity, bars with a line
of the average over them, points on an area. Each mark takes the items it draws
and says how to read a value across (`x`) and a value up (`y`) from each, and
which series each item is in.

It lives in its own module, so an app with no chart carries none of it:

```toml
# gradle/libs.versions.toml
kontour-ui-charts = { module = "io.kontour:ui-charts", version.ref = "kontour" }
```

## Axes

`ChartAxis.number()`, `ChartAxis.date()` and `ChartAxis.category()`. A number
axis ticks in round steps, 1, 2, 2.5 or 5 of a power of ten. A date axis ticks
in whichever calendar unit fits: days, weeks from the reader's first day of the
week, months from the first, or years from January. A category axis gives each
value a band, in the order the axis's own list gives them or, without one, the
order they first appear in.

Every label is the reader's own, written by `Theme.formats`. A German reader
sees German digits and month names with nothing said. Pass `label` to write
them yourself, and `title` to name what the axis measures.

**Bars stand or lie down by the axes.** With a category axis across and a number
up, they stand; the other way round, they lie on their side, which is the better
chart for long category names.

<!--sample:ChartStackedBars-->
```kotlin
// Bars of categories, stacked into each month's whole.
Chart(
    xAxis = ChartAxis.category<String>(),
    yAxis = ChartAxis.number(),
    modifier = Modifier.fillMaxWidth().height(220.dp),
    contentDescription = "Spending by month",
) {
    bar(
        quarter,
        x = { it.month },
        y = { it.amount },
        series = { ChartSeries(it.category) },
        stacking = ChartStacking.Stacked,
    )
}
```

`stacking` sets how the series at one place share it. `Stacked`, the default for
bars, piles them into one, with negative values hanging below zero.
`Grouped` puts them side by side. `Normalised` scales each place to the same
whole, so the axis reads as shares of it. Areas stack the same way.

## Series

Series take their colours from the theme in the order they first appear, and
keep them by their `key` as the data changes. The third route to appear keeps
the third colour while it is there, wherever it is in the list. The palette has
six colours for each scheme and contrast tier. They start at the brand's
violet and are chosen to stay apart under each kind of colour blindness, and
they hold 3:1 against every ground (4.5:1 at the high tier). An app with a data
palette of its own provides `LocalSeriesColours`.

`SeriesStyle` overrides a series' colour, dash, symbol or fill, on a
`ChartSeries` for everywhere it appears or on a mark for that mark alone.

## Selection

Give a chart a `rememberChartSelectionState()` and it is interactive. A tap or
a drag selects the nearest place along it, a pointer selects what it is over,
and with focus the arrow keys move between places, Home and End jump to the
ends, and Escape clears. Everything at the selected place is selected together,
every series' value there, and the chart shows it in a tooltip.
`selection.x` is the selected value; `tooltip` replaces what the tooltip shows.

## Motion

A chart rises into place when it first appears: bars and areas from their
baseline, lines from the bottom. A change of data moves from the old chart to
the new one. A bar for March becomes the new bar for March, and a line of
twelve points becomes one of thirty by being read at the new places, so
nothing jumps. `animateData = false` draws each chart at once.

## `SectorChart`

<!--sample:SectorChartBasics-->
```kotlin
// September's spending as a donut, with the total in its hole.
SectorChart(
    modifier = Modifier.fillMaxWidth().height(200.dp),
    innerRadius = 0.6f,
    contentDescription = "Spending in September",
    centre = { Text(Theme.formats.currency(2025.0, "AUD")) },
) {
    sectors(quarter.filter { it.month == "Sep" }, value = { it.amount }, series = { ChartSeries(it.category) })
}
```

The parts of a whole, as a pie or, with an `innerRadius`, a donut with its
`centre` free for the total. Give it a `rememberChartSelectionState<ChartSeries>()`
and a sector is chosen as a chart's place is: a tap, a pointer, the arrow keys
round the circle the way it is drawn, or a screen reader on the sector's own
node. The chosen one stands out from the centre and is outlined, so it is told
apart by its shape as well as its ink. Sectors start at the top and go clockwise, and
anticlockwise in a right-to-left language. Use one for up to five or six parts
of one whole. For more parts, or to compare parts across wholes, use a `Chart`
of bars.

## `Sparkline`

<!--sample:SparklineBasics-->
```kotlin
// A week's trend, at the size of a word, beside the number that matters.
Row(verticalAlignment = Alignment.CenterVertically) {
    Text("905 riders")
    Sparkline(
        values = week.filter { it.route == "950" }.map { it.riders },
        modifier = Modifier.size(width = 64.dp, height = 20.dp),
        contentDescription = "Route 950 this week",
    )
}
```

A trend at the size of a word, with no axes: for a table cell, a `Stat`, the
end of a sentence. It shows the shape and nothing else. To read values off it,
use a `Chart`.

## Accessibility

**Colour is never the only difference.** With `showPatterns` on, each series
also has its own line dash, point symbol and fill hatching, and the legend shows
them. It is on by default at the high contrast tier and wherever the reader has
asked for colour not to be relied on: iOS's *Differentiate Without Colour*, and
Android's colour correction. A chart with more series than the palette has
colours turns patterns on too, so the seventh series is not the first one
again.

**A screen reader hears a summary first**: the kind of chart and how many
series ("Line chart. 2 series."), what each axis spans ("Riders, 300 to
1,000."), and each series' least, most and direction ("Route 950, 640 to 905,
rising."). The `contentDescription` goes before it. Then come the places
along the chart, one node each, with every series' value there: "13 Oct 2026: Route 950, 720 and
Route 951, 380". Past sixty places, which nobody swipes through one at a time,
each node covers a run of them, with the least and the most of each series in
it. With a selection, a place is chosen from there as it is by touch. There
are no custom actions, so a chart of a year costs no more to navigate than a
chart of a week.

The legend is not read: the summary names every series already.

**Under reduced motion nothing moves.** A chart appearing fades in, and a change
of data is the new chart at once.

**Right to left**, the axis across runs from the right and the axis up stands on
the right, and the text stays the right way round.

**At 200% text**, tick labels that would touch are thinned out evenly and never
turned on their side, and the legend wraps.

The words a chart says on its own are `ChartStrings`, English by default. Build
your own with `ChartStrings.from(lookup)` over the same `StringLookup` you give
`Strings.from`, and provide it with `LocalChartStrings`.
