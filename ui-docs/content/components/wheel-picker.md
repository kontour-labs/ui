# `WheelPicker`

**Grab it with a mouse.** A `LazyColumn` answers touch and the wheel, and on
desktop that is all — which is right for a list and wrong for a drum. Nobody
sets a time by scrolling a picker with a wheel.

<!--sample:WheelPickerBasics-->
```kotlin
val platforms = remember { listOf("Platform 1", "Platform 2", "Platform 3") }
var index by remember { mutableStateOf(1) }

WheelPicker(
    items = platforms,
    selected = index,
    onSelectedChange = { index = it },
    label = { it },
)
```

The scrolling drum, for any list of values. `TimePicker` is three of these.

**The ends give.** A list of hours has a first and a last, and a drum that stops
dead at either one is a boundary the finger cannot feel — the gesture simply
stops answering and the control reads as broken rather than as finished. Pushed
past an end, the drum follows the finger a row or so further, each pixel buying
less than the last, and springs back when the finger lifts. Nothing is selected
by it: the stretch is drawn and no index, no settled value and nothing the caller
sees knows it happened.

Set `infinite = true` and there are no ends to feel. That is a decision about
whether the *values* wrap — hours and months do, a list of countries does not —
rather than about how the ends behave.

**Tapping a row you can see turns the drum to it**, on both kinds. It used to be
a drag or nothing: the rows are boxes with text in them and nothing anywhere
handled a click, so reaching a value two rows up meant dragging the drum by
exactly two rows. On a phone that is a gesture; with a mouse it is a gesture
nobody makes.

It is a pointer surface on the container rather than a click handler per row, and
that is deliberate. Making each row `selectable` would turn a 24-hour drum into
24 stops for a screen reader and a keyboard, where the whole point of the control
is that it is *one* value with a state description. The two input methods that
already had a working way in should not pay for the one that did not.

The turn is silent, which is the harder half. An animated scroll crosses every row
between here and there, and none of those is a detent a finger crossed — a buzz
per row would be four for a gesture in which nothing was felt.

**Reach for a [`Select`](select.md) instead** inside a form. A drum
is right when the value is one of a long ordered run and the user is adjusting
it; a select is right when they are choosing from a list.

---

## What it refuses

`visibleItems` must be odd, so a row can sit in the centre. `itemHeight` must be
greater than zero.

The second one is here because it was silent. Every position on the drum is
derived by dividing by the row height, so `0.dp` produced NaN — no exception, no
drawing, and a picker that could not be scrolled. Its neighbour had been
rejecting even `visibleItems` with a clear message since the day it was written.
One bad argument shouted and the one beside it went quiet, which is the worse of
the two failures.

An empty `items` list is not refused: it draws nothing, which is what a list that
has not loaded should do.

## Accessibility

The wheel reports the centred item as its `stateDescription`, so a screen reader
announces the value the picker has settled on rather than the scroll position.

`label` is what gets announced, so return something speakable: `"08"` reads as
"zero eight" and is what a clock wants visually — where the two disagree, the
component drawing the wheel should give it a spoken form.

A wheel is a scroll gesture with a lot of stops. Where the set is long, a
[`Select`](select.md) is reachable in one gesture and a wheel is not.
