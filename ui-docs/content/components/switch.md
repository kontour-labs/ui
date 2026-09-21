# `Switch`

<!--sample:SwitchBasics-->
```kotlin
var liveAlerts by remember { mutableStateOf(true) }

Switch(checked = liveAlerts, onCheckedChange = { liveAlerts = it })
```

**Use a switch for a setting that takes effect immediately, and a `Checkbox` for
one that is part of a form and takes effect on submit.** A user who flips a
switch expects the thing to have happened; a user who ticks a box expects to
press Save.

The thumb stretches as it travels — wider mid-flight, round at rest — and keeps
its 2dp of clearance on both sides the whole way, growing into whichever side has
the room. At either end that is all behind it, so the stretch trails the way give
should.

Pushed past the end it squashes, and it squashes **on one side**. The end against
the wall keeps the circle the thumb rests as — so it stays exactly concentric with
the track's end arc, 2dp the whole way round, which is what the padding is for —
and the free side eases in to a shallower curve. A ball pressed into a wall does
not go pointy where it is touching.

The join between the two is the hard part, and it is why the free side is not
simply a shallower ellipse butted onto the cap: two arcs like that share a tangent
but not a curvature, and the step is visible as a kink at the widest row. The free
side leaves the join on the cap's own radius and tightens from there.

Squashed, the thumb is inside the circle it rests as at every point, so it never
comes closer to the track than a resting thumb does.

**The stretch is the travel, not a second animation about it.** It is taken from
how fast the thumb is going, so it grows as the thumb sets off, is widest where
the thumb is quickest, and is gone by the time it arrives — one movement. Driven
instead by a flag saying *the position is animating*, it could only ever start
after the thumb had and finish after it stopped, which is a flip that expands,
then moves, then contracts.

**The track is filled in both states and the thumb never changes colour.** Only
the track behind it does, because a switch has one moving part and one thing that
changes behind it; recolouring the thumb as well makes the flip read as two
events. The off track used to be an unfilled, stroked capsule, on the reasoning
that a grey track sits too close in tone to the surfaces it is toggled on top of
to read as a distinct control. The reasoning was right and the conclusion was
not — the answer is not *no* fill but a fill dark enough. It is
`outlineStrong`, the token that exists to bound an interactive control at the 3:1
WCAG asks for, and it is checked against every ground a switch can land on.

**The track's colour is the thumb's position, not a tween about it.** It used to
be a 150ms crossfade fired by the commit, which on a tap roughly coincided with
the travel and on a *drag* could not: the thumb follows your finger one to one,
so a slow drag holds it anywhere it likes, and the track behind it stayed one
colour and then repainted itself the instant the midpoint went by, whatever the
finger was doing. Interpolated from the position instead, a drag half way across
is a track half way across, and a tap crossfades on the position spring's own
timing — the same correction the thumb's stretch already had. The blend is
through Oklab, so the middle of grey to a saturated primary is a colour rather
than a muddy step. Enabled to disabled is still a tween: that is the one change
the thumb's position cannot express.

**The track is a squircle, like every other pill in the library.** It was the one
that was not — it named `Theme.shapes.control`, the same token a `Button` takes,
and then drew plain circular corners into a canvas, which is what "switches don't
have that same smoothing factor as things like buttons" turned out to mean. It
draws the shape's own outline now. The thumb stays a true circle, and that is the
shape scale's answer rather than an exception to it: a square at capsule radius
saturates on both edges, so `SquircleShape` returns a circle for it — the same as
an `IconButton`, an `Avatar` or a radio ring.

**And the track names `Theme.shapes.pill` rather than `Theme.shapes.control`,
because the thumb cannot be capped.** The thumb is drawn at half its own height —
12dp on a 24dp thumb, a number rather than a token, because a draw call has no
shape to consult. (A rounded rect while it is at least as wide as it is tall, and
an egg once a squash takes it narrower; the cap's radius is the same either way.)
The track was reading the capped rule, so a theme
setting `capsuleCap = 10.dp` brought the 28dp track down to 10 and left the thumb
at 12, where concentricity wants the track to be the thumb *plus* the 2dp of
padding around it — 14. Reported as switches no longer being concentric in a
theme that squares its controls off, and that was exactly it. `pill` is the same
rule uncapped, so the two now track each other at whatever cap a theme picks. In
the default theme nothing moves: the cap is 18dp and half of 28 is 14, so it was
never reached here anyway.

---

**Drag the thumb, and it goes over at the midpoint.** A switch is the most
draggable-looking control there is, and the crossing is the commit: the state
changes under your finger rather than when you lift it. Drag back across and it
changes back. A drag that stops short springs home and reports nothing. Wherever
the finger lets go, that is where the spring starts from — there is one position
for the thumb, not a drag position and a separate resting animation that have to
agree. It works inside a `SelectionRow` too, where the row still owns the tap —
the row publishes its own toggle for the switch to drag against.

**The thumb tracks your finger one to one.** It briefly did not — it leaned
against you, using the ticked slider's `SliderDefaults.DetentPull` — and that is
right on a slider and wrong here. A slider's thumb crosses most of a screen, so
travelling 45% of the way reads as strain. This track is **20dp**: the same
ratio moves the thumb 9dp while your finger moves 20 and then jumps it at the
midpoint, which reads as a control that is not keeping up, and then a glitch.

It is also the one place in the library a switch buzzes. Crossing the midpoint
fires `FeedbackIntent.DragThreshold` — what letting go will do has just changed,
and nothing on screen said so first. **Tapping** a switch is still silent, and
[the haptics policy](../theming.md#what-the-library-buzzes-for) has the split.

---

## Accessibility

`Role.Switch` with a `toggleableState`, which is what makes a screen reader say
"on" and "off" rather than "checked" and "unchecked". The difference is not
pedantry: a switch takes effect immediately, and a checkbox is a value that is
submitted later.

Put it in a [`SelectionRow`](selection-row.md) with `onCheckedChange = null` so
the row is the target — a bare switch has no name.

The thumb stretches while it moves and does not under reduced motion. Nothing
about the announcement changes.
