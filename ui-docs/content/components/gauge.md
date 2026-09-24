# `Gauge`

<!--sample:GaugeBasics-->
```kotlin
// An engine's speed: a needle over a gradient, labelled ticks inside the arc.
Gauge(
    value = 8_500f,
    valueRange = 0f..10_000f,
    indicator = GaugeIndicator.Needle,
    majorTicks = 6,
    minorTicks = 1,
    tickLabel = { "${(it / 1000).roundToInt()}K" },
    colours = GaugeDefaults.colours(
        indicator = ScaleColours.gradient(listOf(Color(0xFFFF5C9E), Color(0xFF7C5CFF))),
    ),
    contentDescription = "Engine speed",
    stateDescription = { "${it.roundToInt()} revolutions a minute" },
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("8.5k", style = Theme.typography.titleLarge)
        Text("RPM", style = Theme.typography.labelSmall)
    }
}

// A thermostat: a thumb on a thicker arc, the reading in the middle.
Gauge(
    value = 40f,
    valueRange = 0f..100f,
    thickness = 20.dp,
    indicator = GaugeIndicator.Thumb,
    majorTicks = 11,
    tickPlacement = GaugeTickPlacement.Outside,
    contentDescription = "Humidity",
) {
    Text("40", style = Theme.typography.displaySmall)
}
```

A reading on a dial: a value on an arc, open at the bottom. **Display only** — a
gauge says what something *is*. For a dial the user turns, use
[`Knob`](knob.md), which draws the same scale.

Everything about the picture is a parameter, because a gauge is where an app's
personality shows in a number:

- **How far round** — `sweepAngle`, 270° by default, centred on the gap at the
  bottom. 180 is a half-dial; 360 is a ring.
- **How thick, and how its ends are cut** — `thickness` and `cap`.
- **One colour, a gradient, or bands** — `GaugeDefaults.colours(indicator = …)`
  takes a `ScaleColours`: `solid(colour)`, `gradient(listOf(…))`, or
  `bands { band(from = …, colour = …) }`. Whichever it is, the colours run
  along the **scale**, not the fill: a colour means a value, so the part of the arc
  at 80% is the same colour whether the fill ends there or carries on. The round
  cap at the start of the scale stays the first colour, with no seam where a
  gradient comes back round.
- **What marks the value** — `GaugeIndicator.None` (the fill alone), `Needle` (a
  tapered needle on a hub, the speedometer), `Thumb` (a disc on the arc, the
  thermostat), or `NeedleAndThumb`, the needle pointing at a thumb: the value
  marked where it is read and pointed at from the middle, for a dial read from
  across a room.
- **The needle's length and colour** — `needleLength` is a share of the room
  between the hub and the innermost thing on the scale: the labels, the ticks, or
  the arc when neither is inside it. 0.8, the default, stops clear of them; 1
  reaches them; more crosses them, as far as the arc's outer edge. Its colour is
  `GaugeDefaults.colours(needle = …)`, or, with `needleMatchesFill`, the fill's
  colour at the reading — the band it points into, changing as it crosses one.
- **Ticks** — `majorTicks` counts both ends, so 0, 2K, … 10K is six; `minorTicks`
  sit between each pair; `tickLabel` writes the major ones; `tickPlacement` puts
  them inside the arc or outside it. Outside leaves the middle to the content.
- **The middle** — a slot. Centred, or under the hub when there is a needle.

## Colour bands

<!--sample:GaugeColourBands-->
```kotlin
// Zones on a tachometer, in revolutions — the gauge's own units, so the scale
// is only said once. Green to 6,000, amber to 8,000, red to the end, and the
// needle in the colour of the zone it points at.
Gauge(
    value = 7_200f,
    valueRange = 0f..10_000f,
    indicator = GaugeIndicator.Needle,
    needleLength = 1f,
    needleMatchesFill = true,
    colours = GaugeDefaults.colours(
        indicator = ScaleColours.bands {
            band(from = 0f, colour = Theme.colours.success.solid)
            band(from = 6_000f, colour = Theme.colours.warning.solid)
            band(from = 8_000f, colour = Theme.colours.danger.solid)
        },
    ),
    contentDescription = "Engine speed",
)

// The same zones blended at their edges.
Gauge(
    value = 7_200f,
    valueRange = 0f..10_000f,
    colours = GaugeDefaults.colours(
        indicator = ScaleColours.bands(smoothing = 0.6f) {
            band(from = 0f, colour = Theme.colours.success.solid)
            band(from = 6_000f, colour = Theme.colours.warning.solid)
            band(from = 8_000f, colour = Theme.colours.danger.solid)
        },
    ),
    contentDescription = "Engine speed",
)

// A comfort band that stops short: 20 to 24 degrees is green, and the scale
// either side of it is the gauge's own colour.
Gauge(
    value = 22f,
    valueRange = 10f..30f,
    colours = GaugeDefaults.colours(
        indicator = ScaleColours.bands {
            band(from = 20f, until = 24f, colour = Theme.colours.success.solid)
        },
    ),
    contentDescription = "Room temperature",
)
```

Zones on the scale — a tachometer's green, amber and red, a thermometer's comfort
band — with `ScaleColours.bands`. Each `band(from = …, colour = …)` runs from its
value until the next band starts, and the last to the end of the scale. **The values
are in the gauge's own units** — revolutions, degrees — and the gauge places them on
its `valueRange` when it draws, so the scale is said once, on the gauge, and the
bands never repeat it. They can be given in any order.

**Whatever no band covers is the gauge's own colour** — the theme's accent, as an
unbanded gauge is. That is the scale before the first band starts, if it starts
after the scale does, and the gap after a band given an `until`: `band(from = 20f,
until = 24f, colour = green)` is a comfort zone with the plain scale either side of
it. A band's `until` never reaches past the next band's start; the later start wins.

`smoothing` blends each edge: 0, the default, is a hard edge, one colour and then
the next; 1 leaves each band its own colour only at its middle and blends the rest
of the way to its neighbours. A blend never reaches past halfway into either side,
so a narrow band keeps its colour at its middle however smooth the rest.

`needleMatchesFill` paints the needle, and its hub, in the fill's colour at the
reading, so it says which band the value is in as well as where: it changes as the
needle crosses a hard edge and blends through a smoothed one.

The same `ScaleColours` colours a [`Knob`](knob.md)'s fill, in the knob's units.

## The fill's origin

The fill runs from `origin`, which is the start of the range unless it is moved: a
reading that goes either side of a centre — a balance, a trim — fills outward from
wherever `origin` is put.

A new `value` travels there on the theme's gentle spring, or is drawn there with
`animated = false` and under reduced motion. The value is read in the draw pass, so
a gauge that is not moving does no work and a moving one only redraws.

**It does not mirror.** A dial reads clockwise everywhere, the way a clock does; a
bar mirrors, and a dial is not a bar bent round.

---

## Accessibility

One node, merged, reported as a progress reading over `valueRange` — so a screen
reader says where in the range it is. Give it a `contentDescription` naming what is
measured, and a `stateDescription` that says the value the way a person would:
"8,500 revolutions a minute" rather than "85 percent".

The ticks and their labels are drawn, not text nodes, so they are not read one by
one — the reading is the value, not the scale.

A gauge is not a control. If it opens something, put it in a
[`Card`](card.md) with `onClick`.
