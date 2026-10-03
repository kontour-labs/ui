# `WheelSelect`

**A field that opens into a wheel.** Closed it is a field like a
[`Select`](select.md)'s: the same frame, label, helper and error. Tap it and a
[`WheelPicker`](wheel-picker.md) grows out of it, above and below, with the
value in the middle row where the field's value was. Turn the wheel and the
value changes as each row reaches the middle.

<!--sample:WheelSelectBasics-->
```kotlin
var passengers by remember { mutableStateOf(2) }

// A tap opens the wheel over the field; a drag up or down on the closed
// field turns it straight away, and it folds back once it comes to rest.
WheelSelect(
    value = passengers,
    options = (1..9).toList(),
    onValueChange = { passengers = it },
    label = "Passengers",
)
```

### Two ways in

**A tap opens it, and it stays open.** Turn it, tap a row to bring it to the
middle, and close it by tapping the value in the middle, tapping anywhere else,
or going back.

**A drag turns it straight away.** Put a finger on the closed field and move it
up or down: the wheel opens around the finger and follows it, with the same
tick at each row, the same give at either end and the same fling onto a row as
a finger on the drum itself. Once it is left alone — no finger on it and the
drum at rest — it waits a moment, so the value it landed on can be read among
its neighbours, then folds back into the field. **A finger back on the wheel
within that moment keeps it open** for another flick, as many as a long list
needs, and the wait starts again after the last: *"longer lists might require
multiple flicks"*. A tap that catches the drum still turning stops it on the row
in the middle rather than choosing it. It is the quick way for someone who knows
what they want, and the tap is the way a newcomer finds.

**What the drag costs.** A vertical drag that starts on the field turns the
wheel instead of scrolling the page, the way a slider's sideways drag stays with
the slider inside a pager. In a long scrolling form that can be the wrong trade:
`dragToPick = false` leaves the field opening only on a tap.

### When to use it

Reach for it rather than a `Select` when the options are an ordered run — a
count, a size, a duration — and the usual change is a step or two either way. A
list of unrelated names reads better as a menu, and a long one as a
[`Combobox`](combobox.md).

`infinite = true` wraps the wheel round, for something cyclic such as minutes or
months; see the `WheelPicker` page for why a list of names should not.

The open wheel is as wide as the field and moves only as far as it must to stay
on screen, so near the top or bottom of the window its middle row no longer
lines up with the field.

It shares `SelectState` with `Select`, so an app can open or close it from
elsewhere.

**Each row, and then the rest.** `onValueChange` follows every row the wheel
turns past, which is what the field shows. A save, a request or a toast belongs
in `onValueChangeFinished`, which comes once the wheel has stopped — a flick
through twenty stops is one call, not twenty. A value set by the caller is not
one.

---

## Accessibility

The closed field is a `Role.DropdownList` named by its `label` and announcing
the current value, like a `Select`'s. The open wheel is a `WheelPicker`, and
announces its value the same way. The drag is a shortcut for a finger and adds
nothing a screen reader needs: activating the field opens the wheel, and the
wheel is operated as any other.

At high contrast the field's frame thickens with the tier, as every field's
does. The open wheel's panel takes the overlay border, and its selection band
takes the edge the `WheelPicker` gains at that tier.

Under reduced motion the wheel does not grow out of the field: it appears at
full height and fades in, and folds away by fading out. It still turns under
the finger and settles on a row, with a short settle and no overshoot at the
ends.
