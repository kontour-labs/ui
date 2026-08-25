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

---

← [Date and time](date-time.md) · [All components](../components.md)
