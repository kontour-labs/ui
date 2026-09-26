# `Meter`

<!--sample:MeterBasics-->
```kotlin
// A battery across the page: bands along the scale, a thumb at the reading,
// and the label above it.
Meter(
    value = 62f,
    valueRange = 0f..100f,
    modifier = Modifier.fillMaxWidth(),
    indicator = GaugeIndicator.Thumb,
    majorTicks = 5,
    tickLabel = { "${it.roundToInt()}%" },
    colours = MeterDefaults.colours(
        indicator = ScaleColours.bands {
            band(from = 0f, colour = Theme.colours.danger.solid)
            band(from = 20f, colour = Theme.colours.warning.solid)
            band(from = 40f, colour = Theme.colours.success.solid)
        },
    ),
    contentDescription = "Battery",
    stateDescription = { "${it.roundToInt()} percent charged" },
) {
    Row(Modifier.fillMaxWidth()) {
        Text("Battery", modifier = Modifier.weight(1f))
        Text("62%")
    }
}

// A tank up the page, a needle pointing at the level and the reading beside it.
Meter(
    value = 340f,
    valueRange = 0f..500f,
    orientation = MeterOrientation.Vertical,
    thickness = 16.dp,
    indicator = GaugeIndicator.Needle,
    majorTicks = 6,
    minorTicks = 1,
    tickLabel = { "${it.roundToInt()}" },
    contentDescription = "Water tank",
    stateDescription = { "${it.roundToInt()} litres" },
) {
    Text("340 L", style = Theme.typography.titleLarge)
}
```

A reading on a straight scale: the [`Gauge`](gauge.md), laid flat. **Display
only** — a meter says what something *is*, and a screen reader hears it as a
progress reading. For a value the user sets, use a [`Slider`](slider.md).

It does everything the gauge does, and the parameters mean the same things
wherever they can:

- **The fill runs from `origin` to the reading**, coloured along the *scale* by a
  `ScaleColours` — `solid`, `gradient` or `bands` — so the part of the track at 80%
  is the same colour whether the fill stops there or carries on.
- **What marks the value** — `GaugeIndicator.None`, `Needle`, `Thumb` (a ringed
  disc on the track), or `NeedleAndThumb`. The needle is a small triangle beside
  the track pointing at the reading, like a caret on a ruler — from the side the
  ticks are not on, so it never sits among their labels; with a thumb, it points
  at the thumb. `needleLength` is how tall the triangle stands off the track, in
  track thicknesses: 0.6 by default, and never less than 5dp, so it reads as a
  caret rather than a flag. `needleMatchesFill` paints it in the colour
  of the band the reading is in.
- **Ticks** — `majorTicks` counts both ends, `minorTicks` sit between each pair,
  `tickLabel` writes the major ones. `tickPlacement` says which side of the track
  they go on: `Inside` is the content's side, between it and the track;
  `Outside`, the default for a meter, is the far side, so the label sits against
  the bar and the scale reads underneath it.
- **`thickness` and `cap`**, as on the gauge. The thumb and the needle scale with
  the thickness.
- **`animated`** — a new reading travels there on the theme's gentle spring, or is
  drawn there when it is off and under reduced motion. The reading is read in the
  draw and placement passes, so a meter that is not moving does no work.

## Orientation

`MeterOrientation.Horizontal`, the default, runs across the page and **fills the
width it is given**. `Vertical` runs up the page and fills from the bottom — a
thermometer, a tank — and is `MeterDefaults.Length` tall, 160dp, unless its
modifier says otherwise.

**A horizontal meter mirrors.** It fills from the start of the line, so right to
left it fills from the right: a bar is read the way the text runs. That is the one
thing a dial does not do. A vertical meter fills upward either way, and its content
and its ticks swap sides with the text.

## Content at the reading

<!--sample:MeterAtTheReading-->
```kotlin
// The label rides along with the reading, on a capsule of its own.
Meter(
    value = 0.35f,
    modifier = Modifier.fillMaxWidth(),
    contentPlacement = MeterContentPlacement.AtValue,
    contentBackground = true,
    contentDescription = "Download",
) {
    Text("35%", style = Theme.typography.labelMedium)
}

// A balance either side of zero: the fill runs from the middle out.
Meter(
    value = -12f,
    valueRange = -50f..50f,
    origin = 0f,
    modifier = Modifier.fillMaxWidth(),
    indicator = GaugeIndicator.Needle,
    majorTicks = 3,
    tickLabel = { if (it == 0f) "0" else "${it.roundToInt()}" },
    contentPlacement = MeterContentPlacement.AtValue,
    contentDescription = "Balance",
) {
    Text("−12", style = Theme.typography.labelMedium)
}
```

The content slot sits in one place by default, `MeterContentPlacement.Fixed`:
above a horizontal meter, the full width of it, and beside a vertical one on the
end side, centred along it. With `AtValue` it **rides along with the reading**,
centred on it and kept inside the meter's ends, so a label near either end stops
at the edge rather than hanging off it. It moves with the animated reading, in the
placement pass, so it never lags the fill.

`contentBackground = true` sits the content on a translucent capsule of the
theme's surface, as it does on the gauge, which turns a label at the reading into a
tag. `MeterDefaults.colours(contentBackground = …)` changes its colour.

## Colour bands

Zones on the scale, with `ScaleColours.bands`, exactly as on the
[gauge](gauge.md#colour-bands): each `band(from = …, colour = …)` runs until the
next starts, the values are in the meter's own units, and whatever no band covers
is the meter's own colour. `smoothing` blends the edges. A battery:

```kotlin
colours = MeterDefaults.colours(
    indicator = ScaleColours.bands {
        band(from = 0f, colour = Theme.colours.danger.solid)
        band(from = 20f, colour = Theme.colours.warning.solid)
        band(from = 40f, colour = Theme.colours.success.solid)
    },
)
```

## The fill's origin

The fill runs from `origin`, the start of the range unless it is moved. A reading
that goes either side of a centre — a balance, a trim, a difference from a target —
fills outward from wherever `origin` is put, in whichever direction the reading is.

---

## Accessibility

One node, merged, reported as a progress reading over `valueRange` — so a screen
reader says where in the range it is. Give it a `contentDescription` naming what is
measured and a `stateDescription` that says the value the way a person would: "62
percent charged", "340 litres".

The ticks and their labels are drawn, not text nodes, so they are not read one by
one. The content is merged into the one node, so a label in it is read with the
reading rather than as a separate stop.

A meter is not a control. If it opens something, put it in a [`Card`](card.md) with
`onClick`.
