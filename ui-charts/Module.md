# Module kontour-ui-charts

Charts drawn from Kontour UI's theme: one cartesian `Chart` whose content is
marks — lines, areas, bars, points, rectangles and rules, mixed as a chart
needs them — a `SectorChart` for the parts of a whole, and a `Sparkline` for a
trend in a line of text.

A module of its own so that an app without a chart carries none of it. Its
series take their colours from the theme's tier, their patterns and symbols
from the reader's settings, their words from `ChartStrings` and their numbers
and dates from `Theme.formats`, so a chart reads as the rest of the library
does and says what it shows to a screen reader.

**This is the reference, not the documentation.** How to choose a chart, and
why it is built the way it is, is in `ui-docs/content/components/chart.md`.

# Package io.kontour.ui.charts

`Chart` with its `ChartScope` marks, `ChartAxis`, `ChartSeries` and
`SeriesStyle`; `SectorChart`; `Sparkline`; `ChartColours`, `ChartDefaults` and
`ChartStrings`; and `rememberChartSelectionState`.
