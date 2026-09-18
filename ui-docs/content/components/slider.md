# `Slider`

<!--sample:SliderBasics-->
```kotlin
var walkSpeed by remember { mutableStateOf(4f) }

Slider(
    value = walkSpeed,
    onValueChange = { walkSpeed = it },
    valueRange = 2f..7f,
    steps = 4,
    showTicks = true,
    // Without this the announcement is a bare percentage, which is not the
    // number the user is choosing.
    stateDescription = { "${it.roundToInt()} km/h" },
)
```

The thumb grows while dragged and settles back with a bounce, and it **stretches
toward whatever is pulling on it** — the finger, while a stepped drag is held
between two notches, and its own destination while it is travelling to one. It
is round again the moment nothing is straining it, and a continuous drag keeps
it round throughout, because a thumb pinned to the finger is not straining
against anything. Each step crossed on a stepped slider fires a tick haptic, so a
user changing a value without looking can feel the detents — which is most of the
point of having steps.

**The marks are opt-in.** `steps` no longer draws them; `showTicks` does. A row
of marks turns a slider into a diagram of its own implementation, and on a short
track with many steps they merge into a dashed line that reads as texture rather
than as information. The detent is still there either way — the thumb still
resists and still ticks. Turn them on where the count is small and *is* the
point: five ratings, four zoom levels.

**They are bars, and they used to be dots.** A dot on a track shares the track's
own axis, so it can only ever differ from it in colour — which made them hard to
pick out at any size worth drawing. A bar *crosses* the track, which is a
difference in shape, and it stays legible when the two colours are close.

`minorTicks` puts marks **between** the steps, drawn at half height:
`steps = 4, minorTicks = 1` is a mark every half step with the halves short.
They are graduations rather than detents — the value still lands on a step — and
they are shorter rather than fainter, so the coarse scale stays readable at a
glance without taking the fine one below the contrast floor. Worth it on a scale
somebody reads a position off; noise on one with many steps already.

**Push past either end and the thumb squashes against it.** A slider that stops
dead at its limit has a boundary the finger cannot feel — the gesture simply
stops answering, which reads as the control having broken rather than as having
finished. The part of the push the clamp refused goes into a rubber band, at
diminishing returns, and comes back as the thumb shortening along the axis of the
push with its leading edge pinned to the wall. It springs back on release, and it
widens into its resting circle on the way rather than out through the capsule it
was let go from.

Squashed it is a **vertical ellipse**, which took two goes. It grew at first, and
that was the wrong shape entirely: the band was summed into the stretch that
makes a thumb reach toward where it is trying to get to, and at an end stop there
is nowhere it is trying to get to — so the thumb elongated backwards, away from
the stop it had just run into. Then it shortened correctly but kept the cap
against the wall and flattened the *other* end, which at 18dp across and 30dp
tall leaves a 3dp radius against a 15dp one: a cut end rather than a squash. An
ellipse has no end to cut, is symmetric, and stays curved where it touches the
track — which is the part that reads as pressing rather than as retreating.
`Switch` gives at its ends the same way and by the same proportion of itself;
`SegmentedControl`'s thumb is a whole segment wide and can never approach a
circle, so it keeps compressing from its trailing side.

**Pass `stateDescription`.** Without it the announcement is a bare percentage,
which is rarely what the number means.

`onValueChangeFinished` fires once on release, for the expensive thing you do
not want to run on every frame of a drag.

**The whole control answers a press**, including the thumb's-radius strip at
either end. The thumb is held back from the ends so it is not clipped there, and
that hold-back used to be layout with the gesture handlers inside it — so the
outer 11dp of every slider was dead to touch, which is precisely where the thumb
sits when the value is at its minimum or maximum. Half the thumb could not be
picked up at either end of the range, on a control that looked entirely
normal.

> A disabled slider still exposed `setProgress` to assistive tech, so it could
> not be dragged but could still be moved. The contract suite found it, and now
> checks that `enabled` is honoured on both paths.

**Reach for a `NumberField` instead** when the exact figure matters more than
the relative position — a slider is for "about this much", and nobody sets a
fare to $4.35 by dragging.

---

**Press anywhere and the thumb comes to the finger**, then follows it. A *tap*
springs the thumb across rather than teleporting it; a drag tracks exactly,
because a thumb that eases toward the finger holding it reads as lag.
`RangeSlider` does all of this and the detent easing too.

---

## What it refuses

`valueRange.start` must be at or below `valueRange.endInclusive`. A range built
from two computed bounds can invert when the data behind them is empty or arrives
out of order, and an inverted one has no reading.

Worth stating because of where it used to fail. The first thing to touch the
range was `setProgress`, the semantics action — so an inverted range drew a
perfectly ordinary slider, passed every screenshot, and crashed the first time
somebody moved it with a screen reader or an automated accessibility check. It
now fails at the call, for everyone, on the first frame.

## Accessibility

`stateDescription` is the parameter to pass. Without it a slider announces a
fraction — "0.62" — and with it, "62 kilometres per hour". The raw value is
almost never what the user is choosing.

`contentDescription` names the control, and it is separate for the reason the two
always are: "Speed" is what it is, "62 km/h" is what it says.

Increment and decrement come from `ProgressBarRangeInfo` and `setProgress`, so
the slider is adjustable without a drag. `steps` makes those increments
meaningful — a continuous slider moves by an arbitrary fraction.
