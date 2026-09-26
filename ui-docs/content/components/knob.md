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

**Turned or dragged, the way GarageBand's knobs are.** Go round the knob and it
turns with the finger's angle — a finger that grabbed the notch keeps it under the
finger. Drag it in a line instead and it becomes a slider along that line's axis,
up and down or across, 200dp for the whole range whatever the knob's size.

**Which way a drag turns it depends on the notch.** A drag pulls the notch the way
it moves it round the arc: at the end of the scale, low on the right, dragging up
pulls it back towards zero; high on the right, dragging down pulls it on round. That
is GarageBand's rule — its knobs turn clockwise to a drag down their right-hand side
— read at the notch rather than under the finger, because the notch is where the
knob is and a finger covers most of a small one. Where a drag runs straight across
the arc at the notch, as up and down do with the notch at the top, it pulls neither
way and the ordinary rule decides: up or right is more. So does a pull into the end
the value is already at, so a drag at either end always moves it.

**The direction holds for the gesture.** Carried on, a drag keeps turning the knob
the way it started, past the top and round — *"if it starts as dragging up, then we
probably want to pull the knob up, but then keep pulling it in the same
direction"*. Brought back, it turns back.

**A circle and a line are told apart by their shape.** A finger going round curves,
its heading turning as fast as it sweeps round the middle, and a finger going in a
line does not curve at all. Each gesture starts undecided, following the two readings
where they agree and holding still where they disagree, and within the first 12dp or
so it decides — then makes up what it held, so it never goes the wrong way first and
nothing is lost. A drag that carries on into a circle becomes a turn; a turn stays
one until the finger lifts. Near the middle, where an angle is noise, it is always a
drag.

None of it mirrors in a right-to-left layout. The dial fills clockwise everywhere,
like a gauge, so the way a drag pulls the notch is the same in both directions, and
so is the ordinary rule's right.

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
theme's `primary` wherever no band reaches. See
[`Gauge`](gauge.md#colour-bands).

**Stepped, it has the slider's detents.** Dragged between two steps, the notch and
the fill lean from the step they are on toward the finger — 0.45 of the way, the
slider's own `SliderDefaults.DetentPull`, so it reads as held by the step rather
than following freely — and crossing to the next step they carry on from where they
had got to. Let go, spun to a stop or moved with the keyboard, the knob springs onto
its step rather than appearing there. It used to go from step to step in a frame:
*"can we add the detent-like behaviour that the slider has, so it animates in
stepped mode?"*. Under reduced motion it sits on its step.

**Felt as well as seen.** A tick per step passed, turned or spinning — or,
without steps, a slider's texture, firmer the faster it turns and fading as a spin
slows — and one report on running into either end, the same end stop every slider
has.

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
