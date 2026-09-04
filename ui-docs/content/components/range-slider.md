# `RangeSlider`

<!--sample:RangeSliderBasics-->
```kotlin
var window by remember { mutableStateOf(7f..19f) }

RangeSlider(
    value = window,
    onValueChange = { window = it },
    valueRange = 0f..24f,
    steps = 23,
    startContentDescription = "Earliest departure",
    endContentDescription = "Latest departure",
    stateDescription = { "${it.start.roundToInt()}:00 to ${it.endInclusive.roundToInt()}:00" },
)
```

Shares [`Slider`](slider.md)'s drag accumulator and detent feel. The filled band
runs **between** the thumbs rather than from the start of the track, because the
band *is* the value.

**The two thumbs are two things to a screen reader.** One node cannot express
two values — a `ProgressBarRangeInfo` has a single `current` — so each thumb is
its own adjustable node, and each one's range is bounded by the other. "Adjust
to maximum" on the start thumb lands as far as a finger could take it — pushing the other
thumb ahead of it — rather than inverting the range or stopping somewhere a
finger would not have stopped.

### Two states worth knowing about

**A closed range** (`0.5f..0.5f`, both thumbs on the same pixel) is what "no
filter set yet" looks like, and it is the state a range slider gets stuck in.
Distance alone cannot say which thumb you grabbed, and picking the wrong one
opens the range in the direction the user did not ask for. The direction of the
first drag decides instead. There is one in the catalog for this reason.

**Crossing pushes.** Dragging the start thumb past the end shoves the end thumb
along in front of it and keeps going, stopping only at the end of the track. It
used to stop dead against its neighbour, which jams the control at exactly the
moment the user is asking for the narrowest range there is and breaks the one
promise a drag makes — that the thing under your finger goes where your finger
goes. The range still never *swaps*: a range that inverts under the finger is one
the user has to drag twice to fix. The pushed thumb lags a little and stretches
while it lags, so being shoved looks like being shoved.

**A shoved thumb stays shoved.** Reverse the drag and it holds its ground while
the thumb that pushed it comes back — the two separate from the moment the finger
turns round, rather than travelling home together.

**A press picks a thumb; it does not move one.** Tap the track and the nearer
thumb comes to you, the way a single [`Slider`](slider.md)'s does. But a press
that has not travelled yet might be the start of a drag, and with two thumbs a
pixel either side of the midpoint picks a *different* one — so answering on the
press sent whichever thumb you were not aiming at across the track. The move
waits until the gesture has said which it is.

**`minDistance`** is the narrowest the range may be, in the units of
`valueRange`. A departure window of "no less than twenty minutes" is a real
requirement and there was no way to say it. Both thumbs respect it, from a drag
and from assistive tech alike — each thumb's announced range ends where a finger
would have to stop. A minimum wider than the range itself is clamped to the
span, so it asks for the whole track rather than inverting the arithmetic.

```kotlin
RangeSlider(
    value = window,
    onValueChange = { window = it },
    valueRange = 0f..24f,
    minDistance = 1f,
    steps = 23,
)
```

**Reach for two `Slider`s instead** only if the two values are genuinely
independent. If one must not exceed the other, they are a range, and two sliders
cannot enforce that between them.

**`showTicks` marks the steps**, as small dots along the bar. Only on a stepped
range: a continuous one has nothing to mark, and the parameter draws nothing.
Reach for it when the steps are the point — whole hours, a fare band — and leave
it off when they are only there to keep the value tidy.

---

## Accessibility

Two thumbs, two nodes, each with its own `contentDescription` —
`startContentDescription` and `endContentDescription`. Without them a screen
reader announces two identical sliders and the user cannot tell which end they
are moving.

`stateDescription` turns the range into something speakable: "9:00 to 17:00"
rather than "0.375, 0.708". It takes the whole range, so it can describe the pair
as one idea.

Both thumbs are adjustable from a screen reader's increment and decrement
actions, so the range is reachable without a drag.
