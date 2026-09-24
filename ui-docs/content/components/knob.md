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

**Round, the way a knob turns.** The value follows the angle of the finger about
the centre, and keeps following it however far round the finger goes, up to either
end. Near the centre, where an angle is noise, an up or down drag turns it instead.

**Thrown, it spins.** Let go while turning quickly — 90° a second or more — and it
carries on, slowing, through its steps, and stops at an end if it reaches one. The
spinning-wheel feel. Under reduced motion it stops where it was let go.

`steps` works as it does on a slider: how many stops between the ends, each with a
tick mark outside the track. Zero turns smoothly.

**Stepped, it has the slider's detents.** Turned between two steps, the notch and
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
