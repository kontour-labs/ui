# `Knob`

<!--sample:KnobBasics-->
```kotlin
var volume by remember { mutableStateOf(0.4f) }

Knob(
    value = volume,
    onValueChange = { volume = it },
    steps = 9,
    contentDescription = "Volume",
    stateDescription = { "${(it * 100).roundToInt()}%" },
) {
    Text("${(volume * 100).roundToInt()}")
}
```

A dial the user turns: a value on an arc, set by turning a raised face with a notch
on it. The input half of [`Gauge`](gauge.md), drawing the same scale. Where a
[`Slider`](slider.md) wants a line, a knob wants a square, and it earns it where
settings sit in a grid — an equaliser, a mixer, a synth's panel — or where the
thing being set is itself a turn.

**Turned or dragged, whichever the finger does.** Go round the knob and it turns
with the finger's angle — a finger that grabbed the notch keeps it under the finger.
Drag it in a line instead and up or right is more, down or left is less, from
anywhere on it, 200dp for the whole range whatever the knob's size.

The two readings agree over the top-left half of the knob — along the top, right is
clockwise; up the left side, up is clockwise — and disagree over the bottom-right,
where down the right-hand side is less as a drag and more as a turn. So the knob
tells them apart by **the shape of the path**, not its direction: a finger going
round curves, its heading turning as fast as it sweeps round the middle, and a
finger going in a line does not curve at all. Each gesture starts undecided,
following the drag where the two agree and holding still where they disagree, and
within the first 12dp or so it decides — then applies what it held the way it
decided, so neither reading goes the wrong way first and nothing is lost. A drag
that carries on into a circle becomes a turn; a turn stays one until the finger
lifts. Near the middle, where an angle is noise, it is always a drag. Asked for as
*"can we somehow combine the circular spinning motion of the knob with the
left/right and up/down motion?"*, after a round of dragging only.

Right is more in both layout directions. The dial does not mirror — it fills
clockwise everywhere, like a gauge — and at the top of it, where the notch starts,
clockwise is to the right.

**Thrown, it spins.** Let go while moving quickly — 400dp a second or more, round
the knob or along the drag, whichever the gesture was — and it carries on, slowing,
through its steps, and stops at an end if it reaches one. The spinning-wheel feel.
Under reduced motion it stops where it was let go.

`steps` works as it does on a slider: how many stops between the ends, each with a
tick mark outside the track. Zero turns smoothly.

**Its fill is coloured like a gauge's**: `KnobDefaults.colours(indicator = …)` takes
a `ScaleColours` — one colour, a gradient along the scale, or bands at values in
the knob's own units, `ScaleColours.bands { band(from = 0f, colour = green);
band(from = 0.9f, colour = red) }`, hard-edged unless given a `smoothing`, with the
theme's accent wherever no band reaches. See
[`Gauge`](gauge.md#colour-bands).

**Stepped, it has the slider's detents.** Dragged between two steps, the notch and
the fill lean from the step they are on toward the finger — 0.45 of the way, the
slider's own `SliderDefaults.DetentPull`, so it reads as held by the step rather
than following freely — and crossing to the next step they carry on from where they
had got to. Let go, spun to a stop or moved with the keyboard, the knob springs onto
its step rather than appearing there. It used to go from step to step in a frame:
*"can we add the detent-like behaviour that the slider has, so it animates in
stepped mode?"*. Under reduced motion it sits on its step.

**Felt as well as seen.** A tick per step passed, turned or spinning, and one
report on running into either end — the same end stop every slider has.

The drag belongs to the round face and track. A finger landing in the square's
corners is the page's, so a knob in a scrolling column does not stop it scrolling.

---

## Accessibility

It is operated like a [`Slider`](slider.md), and announced like one: a progress
reading over `valueRange` with its steps, adjustable by assistive technology through
the same `setProgress` action — withheld while disabled, so an inert-looking knob
cannot be moved from under the user.

**From the keyboard**, once focused: the arrow keys move a step (or a hundredth of
the range without steps), Page Up and Page Down a tenth, Home and End to the ends.
The focus ring is round, like the knob.

Give it a `contentDescription` naming what it sets, and a `stateDescription` for a
value that is not a percentage.
