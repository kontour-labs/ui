# `Chart`

*Also on this page: `SectorChart`, `Sparkline`, `ChartDataTable`, `ChartAxis`, `ChartSeries`, `SeriesStyle`, `ChartColours`, `ChartStrings`, `rememberChartSelectionState`.*

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

`ChartAxis.number()`, `ChartAxis.date()`, `ChartAxis.instant()` and
`ChartAxis.category()`. A number
axis ticks in round steps, 1, 2, 2.5 or 5 of a power of ten. A date axis ticks
in whichever calendar unit fits: days, weeks from the reader's first day of the
week, months from the first, or years from January. A category axis gives each
value a band, in the order the axis's own list gives them or, without one, the
order they first appear in.

An instant axis ticks on a time zone's clock, the device's unless it is given
one: every few seconds, minutes or hours from midnight within a day, and as a
date axis does past one. On the day the clocks change, its ticks are where the
clocks showed them, so midnight and three are two hours apart.

Every label is the reader's own, written by `Theme.formats`. A German reader
sees German digits and month names with nothing said. Numbers are written in full,
"12,000" rather than "12K", on every platform: not every platform can write
the short form in every language, and an axis that reads one way on one phone
and another on the next is worse than a longer label. Pass `label` to write
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
whole, so the axis reads as shares of it. Areas stack the same way, and
`Centred` stacks them about the middle of the plot as a stream does (bars take
it as `Stacked`).

A normalised chart's value axis is shares from 0 to 100%, and everything else
along it is a share too: `rule(y = 0.5)` is the halfway line, and a rule in the
data's own units, which would be off the end, is not drawn. One series on its
own is shared out across its places instead, so each bar reads as its part of
the whole series, as a sector chart's parts do.

## Series

Series take their colours from the theme in the order they first appear, and
keep them by their `key` as the data changes. The third route to appear keeps
the third colour while it is there, wherever it is in the list. The palette has
six colours for each scheme and contrast tier. They start at the brand's
violet and are chosen to stay apart under each kind of colour blindness, and
they hold 3:1 against every ground (4.5:1 at the high tier). An app with a data
palette of its own provides `LocalSeriesColours`.

`SeriesStyle` overrides a series' colour, dash, symbol or fill pattern, on a
`ChartSeries` for everywhere it appears or on a mark for that mark alone.

With patterns on, a series' `pattern` can be any `FillPattern`, as a calendar
event's can: the named ones, lines further apart or heavier, dots in staggered
rows, or a tile of your own.

```kotlin
val hatched = SeriesStyle(pattern = FillPattern.lines(PatternDirection.Rising, spacing = 12.dp, weight = 2.dp))
```

## Hiding a series

Give a chart a `rememberChartSeriesVisibilityState()` and each entry in its
legend becomes a switch. A tap, a click or a screen reader's action hides that
series, and another shows it again. The chart is worked out again from what is
left, the same move as a change of data: the axes close in, a stack closes up,
and in a `SectorChart` the other sectors grow into the hidden one's share. A
hidden series keeps its colour and its pattern, and its legend entry stays, its
swatch an empty outline and its name still readable, so it can be brought back.
On a chart that is not `enabled` the legend hides nothing.

<!--sample:ChartHidingSeries-->
```kotlin
// The legend's entries become switches: a tap hides a route, and the
// chart closes in on the one that is left.
val shown = rememberChartSeriesVisibilityState()
Chart(
    xAxis = ChartAxis.date(),
    yAxis = ChartAxis.number(title = "Riders"),
    modifier = Modifier.fillMaxWidth().height(220.dp),
    seriesVisibility = shown,
    contentDescription = "Riders this week, by route",
) {
    line(week, x = { it.day }, y = { it.riders }, series = { ChartSeries("Route ${it.route}") })
}
```

`seriesVisibility.hidden` is what is hidden, and `hide`, `show` and `showAll`
change it from elsewhere: a "Show all" button, or a filter of your own.

## Regions, bands and rules

What a chart says besides its data: a stretch of time and what it was, how far
a forecast might go, the events that explain a spike.

<!--sample:ChartAnnotations-->
```kotlin
// The week so far, a forecast's likely range after today, and what else
// happened: a span shaded and named, events pinned above the plot.
Chart(
    xAxis = ChartAxis.date(),
    yAxis = ChartAxis.number(title = "Riders"),
    modifier = Modifier.fillMaxWidth().height(240.dp),
    contentDescription = "Route 950 this week, and the next two days",
) {
    val route = ChartSeries("Route 950")
    region(xStart = LocalDate(2026, 10, 16), label = "Forecast")
    region(xStart = LocalDate(2026, 10, 13), xEnd = LocalDate(2026, 10, 14), label = "School holidays")
    band(ahead, x = { it.day }, yStart = { it.low }, yEnd = { it.high }, series = { route })
    line(week.filter { it.route == "950" }, x = { it.day }, y = { it.riders }, series = { route })
    line(ahead, x = { it.day }, y = { it.likely }, series = { route }, style = SeriesStyle(line = LinePattern.Dashed))
    rule(x = LocalDate(2026, 10, 15), label = "Timetable change")
    rule(y = 800, label = "Capacity")
}
```

- `region` shades a span of the plot and names it in its top corner: from
  `xStart` to `xEnd` across and right up it, or `yStart` to `yEnd` up and right
  across it. An end left out runs to the edge, so `region(xStart = today)` is
  everything after today. It is drawn behind the marks, in the selection's
  colour unless its `style` gives one, and widens the axis to take it in.
- `band` fills between a low and a high value at each place, one band per
  series: a forecast's likely range, a day's least and most. Drawn light and
  with no edge, so a `line` through it in the same series reads as the value
  and the band as its range. A reader hears the range at each place, the
  tooltip shows it, and `ChartDataTable` writes it in one cell.
- `rule` with an `x` is an event: its label sits in a pin above the plot, and
  pins that would touch stand one on another, so a run of events close together
  keeps every label. With a `y` it is a target or an average, its label at its
  end on the page's ground so it reads over the marks it crosses.

Regions and rules are references, not data: each is said after the chart's
summary by its label and where it is ("Capacity, 800"; "School holidays, 13 Oct
to 14 Oct"), and none is in the legend. One with no label is decoration, and
is not said at all.

## Ranges

A bar given `yStart` and `yEnd` runs from one to the other rather than from
zero: a day's coldest and warmest, a shift's start and end. It is rounded at
both ends, several series stand side by side, and a reader hears each as its
range, "Mar: 17 to 25".

<!--sample:ChartRangeBars-->
```kotlin
// Each month from its coldest to its warmest, a bar from one to the other.
Chart(
    xAxis = ChartAxis.category<String>(),
    yAxis = ChartAxis.number(title = "°C"),
    modifier = Modifier.fillMaxWidth().height(200.dp),
    contentDescription = "Temperatures in autumn",
) {
    bar(autumn, x = { it.month }, yStart = { it.low }, yEnd = { it.high })
}
```

`stem` draws a thin line between two values. With a `point` at each end it is
a dumbbell; from zero with one at its top, a lollipop; over a bar, its margin
of error. A stem whose ends the points already say is drawn and not said again.

<!--sample:ChartDumbbells-->
```kotlin
// Each route's two fares, joined by a stem: a dumbbell.
val offPeak = ChartSeries("Off-peak")
val peak = ChartSeries("Peak")
// Read out here: a chart's content records marks, and is not composed.
val joining = SeriesStyle(colour = Theme.colours.outlineStrong)
Chart(
    xAxis = ChartAxis.category<String>(),
    yAxis = ChartAxis.number(title = "Fare"),
    modifier = Modifier.fillMaxWidth().height(200.dp),
    contentDescription = "Fares by route",
) {
    stem(fares, x = { it.route }, yStart = { it.offPeak }, yEnd = { it.peak }, style = joining)
    point(fares, x = { it.route }, y = { it.offPeak }, series = { offPeak })
    point(fares, x = { it.route }, y = { it.peak }, series = { peak })
}
```

## Box plots

`box` draws the spread of many values at each place: a box over the middle half
of them with a line at the median, whiskers out to the rest, and anything far
past them as a dot of its own. Give it each place's `samples` and it works the
numbers out as a spreadsheet's `QUARTILE` does, with whiskers out to one and a
half box lengths. Or give it the five numbers and the `outliers` yourself. A
reader hears "median 28, quartiles 27 to 31, range 24 to 33, outliers 52", and
`ChartDataTable` gives each number a column.

<!--sample:ChartBoxPlot-->
```kotlin
// How long a trip takes on each route: the box is the middle half of the
// trips, and the slow one on the 950 is a dot past its whisker.
Chart(
    xAxis = ChartAxis.category<String>(),
    yAxis = ChartAxis.number(title = "Minutes"),
    modifier = Modifier.fillMaxWidth().height(220.dp),
    contentDescription = "Trip times by route",
) {
    box(journeys, x = { it.route }, samples = { it.minutes })
}
```

## Streams

`ChartStacking.Centred` stacks areas about the middle of the plot rather than on
zero. Where a stream sits up the plot measures nothing; only how thick each
series is matters. So its value axis has no ticks or gridlines, and isn't read
out, and each series' values are said as themselves.

<!--sample:ChartStreamgraph-->
```kotlin
// The week's riders by route, stacked about the middle: how thick each
// route is says how many it carried.
Chart(
    xAxis = ChartAxis.date(),
    yAxis = ChartAxis.number(title = "Riders"),
    modifier = Modifier.fillMaxWidth().height(200.dp),
    contentDescription = "Riders this week, by route",
) {
    area(
        week,
        x = { it.day },
        y = { it.riders },
        series = { ChartSeries("Route ${it.route}") },
        stacking = ChartStacking.Centred,
        interpolation = ChartInterpolation.Monotone,
    )
}
```

## Heat maps

`cell` puts a cell at each place of two category axes and shades it by its
`value`: from `ChartColours.ramp`'s pale end at the least of them to its deep
end at the most, with a scale under the plot. With `showPatterns`, the default,
each cell has its number written in it too, so it isn't read by its shade
alone. A reader hears a column at a time, "Mon: 6am, 120, 7am, 340 and 8am,
410", and `ChartDataTable` has a row for each column. The axis across can be a
date or a number axis as well: a calendar of weeks across and the days of the
week up is read a week at a time, each week with its own days.

<!--sample:ChartHeatMap-->
```kotlin
// Riders by day and hour, each cell shaded by how many, with the scale
// under the plot.
Chart(
    xAxis = ChartAxis.category<String>(),
    yAxis = ChartAxis.category<String>(),
    modifier = Modifier.fillMaxWidth().height(200.dp),
    contentDescription = "Morning riders by day and hour",
) {
    cell(mornings, x = { it.day }, y = { it.hour }, value = { it.riders })
}
```

## Bars both ways

Negative values in a stack hang below zero, or to the left of it when the bars
lie down, so a population pyramid is lying bars with one side negative. An axis
`label` that drops the sign writes both sides as the numbers they are, and the
tooltip and a reader use it too.

<!--sample:ChartPopulationPyramid-->
```kotlin
// Men to the left as negatives and women to the right, both written as
// the number of people they are.
val formats = Theme.formats
Chart(
    xAxis = ChartAxis.number(title = "People", label = { formats.decimal(abs(it), 0) }),
    yAxis = ChartAxis.category<String>(),
    modifier = Modifier.fillMaxWidth().height(200.dp),
    contentDescription = "Population by age and sex",
) {
    bar(cohorts, x = { it.people }, y = { it.ages }, series = { ChartSeries(it.sex) })
}
```

## Selection

Give a chart a `rememberChartSelectionState()` and it is interactive. A tap or
a drag selects the nearest place along it, a pointer selects what it is over,
and with focus the arrow keys move between places, Home and End jump to the
ends, and Escape clears. With nothing selected, Left starts from the last
place, and so does Right in a right-to-left language. Everything at the
selected place is selected together, every series' value there, and the chart
shows it in a tooltip. A finger's tooltip goes a few seconds after it lifts,
and a tap on the same place brings it back.
`selection.x` is the selected value.

The tooltip is a card beside the selected place: the place, quietly, over a
row for each series, its swatch and name at the start and its value at the
end, so that the values line up down the card. A stack of bars or areas adds
its total under a rule. The card stands to the end of the place, or to its
start where there is no room for it there, so it covers its neighbours and not
the values it reads out. Among bars the selected place is washed behind them,
its whole width; on a line or an area each value there is ringed.

`tooltip` replaces what the card says, with whatever the selection should
mean on your page:

<!--sample:ChartCustomTooltip-->
```kotlin
// The selected month's tooltip says what was left of a budget, in place
// of each category's figure.
val selection = rememberChartSelectionState<String>()
val formats = Theme.formats
Chart(
    xAxis = ChartAxis.category<String>(),
    yAxis = ChartAxis.number(),
    modifier = Modifier.fillMaxWidth().height(220.dp),
    selection = selection,
    contentDescription = "Spending by month",
    tooltip = { shown ->
        val spent = shown.values.sumOf { it.value }
        Column {
            Text(shown.label, colour = Theme.colours.contentMuted, style = Theme.typography.labelSmall)
            Text(formats.currency(spent, "AUD"), style = Theme.typography.titleSmall)
            Text("${formats.currency(2500.0 - spent, "AUD")} under budget")
        }
    },
) {
    bar(quarter, x = { it.month }, y = { it.amount }, series = { ChartSeries(it.category) })
}
```

`showSelectionTooltip = false` keeps the selection and its highlight with no
card, for a page that says the selected values somewhere of its own.

### Selecting by hovering, or by clicking

A pointer resting over a place selects it, and the selection follows the
pointer across the chart. `hoverSelection = ChartHoverSelection.Never` leaves
it to a click, as a finger leaves it to a tap: moving across the chart then
selects nothing and lets go of nothing, and the selected place, its highlight
and its tooltip stay where the click put them. The keys and a screen reader
choose as they always do. A `SectorChart` takes the same parameter.

<!--sample:ChartSelectOnClick-->
```kotlin
// A pointer selects a day by clicking it, as a finger does by tapping:
// moving across the chart leaves the selected day where it is.
val selection = rememberChartSelectionState<LocalDate>()
Chart(
    xAxis = ChartAxis.date(),
    yAxis = ChartAxis.number(title = "Riders"),
    modifier = Modifier.fillMaxWidth().height(220.dp),
    selection = selection,
    hoverSelection = ChartHoverSelection.Never,
    contentDescription = "Riders this week, by route",
) {
    line(week, x = { it.day }, y = { it.riders }, series = { ChartSeries("Route ${it.route}") })
}
```

On a chart that also marks a range, below, the third way is the question of
what happens while the button is down:

| `hoverSelection` | Resting over the chart | While a range is dragged out |
|---|---|---|
| `ExceptWhileDragging`, the default | Selects the place under it | Selects nothing: the range starting lets go of the place, so its line and tooltip are not over the range being marked, and the pointer selects again from its first move once the button is up |
| `Always` | Selects the place under it | Goes on selecting: the place, its line and its tooltip follow the pointer as the range opens out |
| `Never` | Selects nothing until a click | Selects nothing |

On a chart that marks no ranges, and on a `SectorChart`, there is no drag to
wait for, and the first two are the same.

### Marking a range

Give a chart a `rememberChartRangeSelectionState()` as its `rangeSelection`
and a reader can mark a stretch of it: a pointer by dragging across it, a
finger by holding and then dragging, since a plain drag across moves the
selected place, and the keyboard with Shift and the arrows, stretching a range
a place at a time from the selected one, or from the first. A tap lets go of
it, on the chart or anywhere else, and so does Escape, before it lets go of the
place. A drag marks from exactly where it went down to where it was let go, on
any chart, and a box, where both axes are measures, both ways; on a date axis
`xStart` and `xEnd` are the days its ends fall in. A range set with `select`,
or stretched by the keys, is of whole places along categories and among bars.
It is drawn behind the marks, edged in the selection's colour, and a screen
reader hears it after the selected place: "Selected Feb to Apr".

A finger's hold is felt as it takes, with the library's long-press haptic, and
the range is drawn at once as a line where the finger is, the plot's height,
in its edge's colour; it opens into the band as the finger moves. A box starts
as the two lines through the point, across and up. Until it opens, nothing is
reported, nothing is said and nothing outside it fades: `isEmpty` is still
true, and a finger lifted there lets go of it.

While a range is marked, the marks outside it fade to 60%, so the ones inside
stand out at full strength against the rest, and what is whole is what the
range reports:

| | Inside the range | Along categories, `xStart` and `xEnd` are |
|---|---|---|
| A line, an area, a band | The stretch between its edges, exactly where it is drawn | The first and last places inside it |
| A point, a stem, a dumbbell's end | Where it is, and in a box up as well as across | The first and last places inside it |
| A bar, a box plot, a cell, a rectangle | Wherever the range reaches into its band | The first and last whose bands it reaches into |

So a drag from late in February's band takes February's bar, and on a line it
starts at March's point. A chart with bars and a line together reports by the
bars, and its line is still whole only between the range's edges. A drag on a
chart of lines and points that takes in no place reports nothing: it is drawn
while it goes on, so the reader sees what they are marking, and gone when it
is let go. The fade comes in and goes as the range is marked and let go, and at
once under reduced motion. At the high contrast tier it fades to 80%, where
every series' colour still holds 3:1 against the page. A chart with a range
selection and no `selection` chooses no place, so a plain drag across it is
the page's and scrolls what it is in, and the arrows, Home and End go on to
what is round it.

<!--sample:ChartRangeSelection-->
```kotlin
// Drag across the week to mark some days, and say what they came to.
val range = rememberChartRangeSelectionState<LocalDate, Number>()
val formats = Theme.formats
Chart(
    xAxis = ChartAxis.date(),
    yAxis = ChartAxis.number(title = "Riders"),
    modifier = Modifier.fillMaxWidth().height(220.dp),
    rangeSelection = range,
    contentDescription = "Riders this week, by route",
) {
    bar(week, x = { it.day }, y = { it.riders }, series = { ChartSeries("Route ${it.route}") })
}
val from = range.xStart
val to = range.xEnd
if (from != null && to != null) {
    val riders = week.filter { it.day in from..to }.sumOf { it.riders }
    Text("${formats.decimal(riders.toDouble(), 0)} riders, ${formats.date(from)} to ${formats.date(to)}")
}
```

`ChartRangeMode.Box` marks a box instead, across and up, for a scatter plot's
points in a corner. It needs a measure both ways, numbers or times; where
either axis is a category it marks a range across. The keys stretch a box
across only.

<!--sample:ChartBoxSelection-->
```kotlin
// A box round some of a scatter's points: the hot days that used the most.
val box = rememberChartRangeSelectionState<Number, Number>(ChartRangeMode.Box)
Chart(
    xAxis = ChartAxis.number(title = "Hottest, °C"),
    yAxis = ChartAxis.number(title = "kWh"),
    modifier = Modifier.fillMaxWidth().height(220.dp),
    rangeSelection = box,
    contentDescription = "Each day's heat against its use",
) {
    point(heat, x = { it.first }, y = { it.second })
}
```

The range is of the axes, not of the data: `xStart` and `xEnd`, and a box's
`yStart` and `yEnd`, each in order, and the page reads its data against them.
It is optional and separate from `selection`: a chart can have either, both or
neither.

### A chart with no interaction

A chart without a `selection`, the default, is a picture. Nothing on it
answers a tap, a drag, a pointer or a key, it takes no focus, and it shows no
tooltip, so a page of small charts scrolls and tabs past them as it does past
text. A screen reader still hears all of it: the summary, then each place and
its values. `enabled = false` does the same for a chart that has a selection
it should not change for now, and turns its legend's switches off with it.
What is selected stays drawn, and said, each place marked unavailable. The
legend's switches are a separate choice, made with `seriesVisibility`.

## Two axes up

<!--sample:ChartEndAxis-->
```kotlin
// Two series in different units, each against its own axis: the riders
// up the start edge, and what they paid down the end.
val formats = Theme.formats
Chart(
    xAxis = ChartAxis.date(),
    yAxis = ChartAxis.number(title = "Riders"),
    endYAxis = ChartAxis.number(title = "Takings", label = { formats.currency(it, "AUD") }),
    modifier = Modifier.fillMaxWidth().height(220.dp),
    contentDescription = "Route 950's riders and takings this week",
) {
    bar(takings, x = { it.day }, y = { it.riders }, series = { ChartSeries("Riders") })
    onEndAxis {
        line(takings, x = { it.day }, y = { it.fares }, series = { ChartSeries("Takings") }, showPoints = true)
    }
}
```

For two series in different units, riders and dollars, kilowatt-hours and
degrees: `endYAxis` puts a second axis down the end edge of the plot, and
whatever the content draws inside `onEndAxis { }` is measured against it,
marks, rules and regions alike. Each axis is scaled to its own series, so
neither is pressed flat by the other's numbers. The end axis has as many
ticks as the start axis, at round values of its own, so its labels sit on
the start axis's gridlines rather than on a second grid. Each axis's title
sits over its own edge.

The tooltip, the table and what a screen reader hears write each series in
its own axis's units, and a screen reader hears both axes' ranges. Hide
everything on the end axis from the legend and the axis goes with it, the
plot widening into its gutter. A chart whose axis up is a category axis, a
row of lying bars or a heat map, has no end axis; there the content of
`onEndAxis` is drawn against the start axis.

Two scales on one plot invite reading a crossing as meaningful when it is
only where two scales happen to meet. Use one when the two series are
read together, the bill beside the energy behind it, and not to fit two
unrelated charts into one.

## Motion

A chart rises into place when it first appears: bars and areas from their
baseline, lines from the bottom. It does the same when its first data arrives
in a chart that had none, loading say, and a series added later rises from its
baseline while the rest move. A change of data moves from the old chart to
the new one. A bar for March becomes the new bar for March, and a line of
twelve points becomes one of thirty by being read at the new places, so
nothing jumps. Points and rectangles, which have no baseline, fade in, and a
rectangle moves by both its corners. The plot's edges move with it: when the
axis's labels grow a digit, the plot narrows over the same move rather than
jumping across. `animateData = false` draws each chart at once.

A stack rises as one, from its foot, and a new place's stack does too. Moving
between ways of drawing the same data keeps every mark where it can be
followed. Bars leaving a stack to stand side by side narrow and move apart as
they come down; a stack turned to shares takes its percent axis at once and
each part moves to its share; and a series drawn as bars, then as a line or an
area, fades from one to the other, the line going to the bars' tops and from
them, while a stack holds its place. Along a date or number axis a place that
has gone goes down to its foot as the axis closes in, rather than vanishing,
and every bar is as wide as the chart's closest places allow, whichever series
it is in: a series with a bar a week stacked on daily ones is as wide as they
are.

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
apart by its shape as well as its ink. Its tooltip is just above the pointer or
the finger that chose it, and moves with a pointer round the sector; chosen by
a key or a screen reader, it is over the sector. Sectors start at the top and go clockwise, and
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

It draws itself in from the start when it first appears, and when its values
change, the line moves to its new shape rather than being replaced. Each new
point sets off from where the old line was at the same place along it, so a
live trend that gains a value and drops its oldest slides into shape. Values
that change faster than the move carry on from wherever the line had got to.
`animateData = false` draws it at once, as reduced motion does.

## `ChartDataTable`

<!--sample:ChartDataTableBasics-->
```kotlin
// The same week as a table: a row a day, a column a route.
ChartDataTable(
    xAxis = ChartAxis.date(),
    yAxis = ChartAxis.number(title = "Riders"),
    modifier = Modifier.fillMaxWidth(),
) {
    line(week, x = { it.day }, y = { it.riders }, series = { ChartSeries("Route ${it.route}") })
}
```

The same axes and the same content as a chart, as a table: a row for each
place along it and a column for each series, every value written as the chart
writes it. A chart shows the shape and a table shows the numbers, and some
readers need the second: someone comparing two values exactly, a screen reader
that is better at tables than at pictures, a page being printed. Offer one
beside the chart, behind a "Show as table", from the same content, so the two
cannot drift apart.

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
it. With a selection, a place is chosen from there as it is by touch, and with
a range selection, Shift and the arrow keys mark a range from it, which is
said with the place: "Selected Feb to Apr". There
are no custom actions, so a chart of a year costs no more to navigate than a
chart of a week. A chart with no selection is read the same way and takes no
focus, so a page of them is passed by the keyboard as text is.

**The tooltip says to the eye what the place's node says to the ear.** Each row
of the card is one node, "Rent, 1,450", and a stack's total is "Total, 2,400",
so a reader who reaches the card hears it as rows and not as loose words. The
selected place is washed behind bars and ringed on a line, and at the high
contrast tier the wash is edged in the selection's colour, so it is found by
more than a tint. A pie's card stands just above the finger that chose a
sector, where the finger does not cover it.

The legend is not read: the summary names every series already. With a
`seriesVisibility` its entries are switches, and each is read as one: "Route
950, checkbox, checked". A hidden series is not in the summary.

**Under reduced motion nothing moves.** A chart appearing, or its first data
arriving, fades in, and a change of data is the new chart at once.

**Right to left**, the axis across runs from the right and the axis up stands on
the right, and the text stays the right way round. The tooltip stands to the
left of the place, or to its right where it does not fit, with each value at
the card's left end.

**At 200% text**, tick labels that would touch are thinned out evenly and never
turned on their side, and the legend wraps. A label that is kept says its date,
or a month its year, wherever that has changed since the label kept before it.

The words a chart says on its own are `ChartStrings`, English by default. Build
your own with `ChartStrings.from(lookup)` over the same `StringLookup` you give
`Strings.from`, remembered by locale, and provide it with `LocalChartStrings`;
`python3 docs/export-strings.py` writes their English with the library's for a
translator. Among them is `total`, the word over a stack's total in the tooltip.
