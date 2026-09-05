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

**Reach for a [`Select`](select.md) instead** inside a form. A drum
is right when the value is one of a long ordered run and the user is adjusting
it; a select is right when they are choosing from a list.

---

## Accessibility

The wheel reports the centred item as its `stateDescription`, so a screen reader
announces the value the picker has settled on rather than the scroll position.

`label` is what gets announced, so return something speakable: `"08"` reads as
"zero eight" and is what a clock wants visually — where the two disagree, the
component drawing the wheel should give it a spoken form.

A wheel is a scroll gesture with a lot of stops. Where the set is long, a
[`Select`](select.md) is reachable in one gesture and a wheel is not.
