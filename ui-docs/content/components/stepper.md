# `Stepper`

![Stepper](../../../ui-catalog/screenshots/components/stepper-light.png)

<!--sample:StepperBasics-->
```kotlin
var adults by remember { mutableStateOf(1) }

Stepper(
    value = adults,
    onValueChange = { adults = it },
    contentDescription = "Adults",
    range = 1..9,
)
```

A bounded count with a button at each end. `format` renders the number, for
units — `format = { if (it == 1) "1 bag" else "$it bags" }`.

**The buttons disable individually at each end of `range`**, rather than the
whole control disabling or the value silently refusing to move. A `+` that looks
live and does nothing is the defect this shape exists to avoid, and it is
*announced* as unavailable, not just drawn greyed — which is the half a
screen-reader user gets.

A `step` that would overshoot disables too: with `range = 0..10` and `step = 4`,
the button is dead at 8 rather than clamping to 10, which is a value the step
sequence never contains.

**The number between the buttons is not its own node.** It is the control's
`stateDescription`, so a screen reader says "Adults, 2" rather than offering an
unlabelled "2" between two buttons.

**Reach for a [`Slider`](slider.md) instead** when the number is approximate and
the range is wide. A stepper is for a count someone knows exactly and will change
by one or two; nobody taps `+` thirty times.

---

← [Selection](selection.md) · [All components](../components.md)
