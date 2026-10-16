# Charts

Numbers as pictures. In their own module, `io.kontour:ui-charts`, so an app
with no chart carries none of them.

| | For | Instead of |
|---|---|---|
| [`Chart`](chart.md) | Values along two axes: lines, areas, bars, points, ranges and rules, mixed as a chart needs | A `Table`, when a reader needs the exact figures more than the shape |
| [`SectorChart`](chart.md#sectorchart) | The parts of one whole, as a pie or a donut | A `Chart` of bars, for more than six parts or more than one whole |
| [`Sparkline`](chart.md#sparkline) | A trend at the size of a word | A `Stat`'s trend, when the direction is all that matters |

Every chart takes its colours from the theme, writes its numbers and dates
with `Theme.formats`, and says what it shows to a screen reader in a sentence
before anything in it. Where colour is not enough (at the high contrast tier,
or where the reader has asked), each series also has its own dash, symbol and
hatching. See [`Chart`](chart.md#accessibility).
