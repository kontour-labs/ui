# `Slider`

![Slider](../../../ui-catalog/screenshots/components/slider-light.png)

<!--sample:SliderBasics-->
```kotlin
var walkSpeed by remember { mutableStateOf(4f) }

Slider(
    value = walkSpeed,
    onValueChange = { walkSpeed = it },
    valueRange = 2f..7f,
    steps = 4,
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

← [Selection](selection.md) · [All components](../components.md)
