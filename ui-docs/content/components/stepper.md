# `Stepper`

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

**`animateValue` rolls the digit** rather than replacing it, which is worth
having when the number is the whole content and the user is the one changing it
— the roll says *you did that* where a silent swap looks like the screen
redrawing. It is off by default because a stepper inside a long form is one of
several things moving, and under reduced motion it does nothing at all.

**Reach for a [`Slider`](slider.md) instead** when the number is approximate and
the range is wide. A stepper is for a count someone knows exactly and will change
by one or two; nobody taps `+` thirty times.

---

## Accessibility

`contentDescription` names the whole control and is required, because `+` and `−`
say nothing about what they are adjusting. The current value is the control's
`stateDescription`, so a screen reader says "Adults, 2" rather than reading a
number floating between two buttons.

The **value** is cleared from the tree, not the buttons: the row already
announces it as its state, and a bare "2" read out between two controls is a node
with no meaning of its own. The `−` and `+` remain real, separately reachable
buttons carrying `decrementLabel` and `incrementLabel`.

`format` is what gets announced as well as drawn — use it to say the unit.
