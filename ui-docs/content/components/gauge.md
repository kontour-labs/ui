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
    colours = GaugeDefaults.colours(indicator = listOf(Color(0xFFFF5C9E), Color(0xFF7C5CFF))),
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
- **One colour or a gradient** — pass several colours in
  `GaugeDefaults.colours(indicator = …)` and they run along the **scale**, not the
  fill: a colour means a value, so the part of the arc at 80% is the same colour
  whether the fill ends there or carries on. The round cap at the start of the
  scale stays the first colour, with no seam where the gradient comes back round.
- **What marks the value** — `GaugeIndicator.None` (the fill alone), `Needle` (a
  tapered needle on a hub, the speedometer) or `Thumb` (a disc on the arc, the
  thermostat).
- **Ticks** — `majorTicks` counts both ends, so 0, 2K, … 10K is six; `minorTicks`
  sit between each pair; `tickLabel` writes the major ones; `tickPlacement` puts
  them inside the arc or outside it. Outside leaves the middle to the content.
- **The middle** — a slot. Centred, or under the hub when there is a needle.

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
