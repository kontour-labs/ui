# `Modifier.coachmark`

*Also on this page: `CoachmarkTour`, `Modifier.coachmarkStep`, `CoachmarkStep`, `rememberCoachmarkTour`, `CoachmarkTransition`, `CoachmarkDefaults`.*

A tip that introduces a control the user has not found, or a tour of several.

<!--sample:CoachmarkBasics-->
```kotlin
// One tip, shown when the app decides — through a queue, so six features do
// not all introduce themselves on one launch, and never over a dialog.
CompositionLocalProvider(LocalOverlayQueue provides rememberOverlayQueue(sessionCount = launches)) {
    IconButton(
        icon = Tabler.Outline.Bookmark,
        contentDescription = "Save this trip",
        onClick = { save() },
        modifier = Modifier.coachmark(
            id = "save-trip",
            title = "Save this trip",
            message = "Trips you save show up on the home screen.",
            minSessions = 3,
        ),
    )
}

// A tour the user asked for: the order here, and each step's words at the
// control they explain.
val tour = rememberCoachmarkTour("plan", "saved")
Row {
    IconButton(
        icon = Tabler.Outline.Route,
        contentDescription = "Plan a trip",
        onClick = { plan() },
        modifier = Modifier.coachmarkStep(tour, id = "plan", title = "Plan a trip", message = "From here to anywhere on the network."),
    )
    IconButton(
        icon = Tabler.Outline.Bookmark,
        contentDescription = "Saved trips",
        onClick = { save() },
        modifier = Modifier.coachmarkStep(tour, id = "saved", title = "Saved trips", message = "The ones you keep show up here."),
    )
}
Button(onClick = tour::start, variant = ButtonVariant.Secondary) { +"Show me around" }
```

Two things with similar names and opposite bargains.

| | `Modifier.coachmark` | `CoachmarkTour` |
|---|---|---|
| Who starts it | the app, through a queue | the user |
| How many | one | several, in order |
| The rest of the screen | untouched | dimmed, with a hole around the control |
| Can be ignored | yes | it is modal |

## `Modifier.coachmark`

One tip, beside the control it is about, with a title, a line of text and a
"Got it". **Nothing the user does triggers it**: the app decides the moment has
come, and the tip asks an [`OverlayQueue`](../overlays.md#the-queue) for its
turn. The queue is what stops six features each deciding that today is the day to
introduce themselves — it shows one at a time, highest `priority` first — and it
holds every tip back while a dialog, a sheet or a menu is open, because a tip
pointing at a button behind a modal is worse than none.

Provide the queue once, near the root, with `LocalOverlayQueue provides
rememberOverlayQueue(sessionCount)`. Without one the modifier does nothing, since
a coach mark is by definition optional.

- `id` is what "already seen" is keyed on, so keep it stable across releases.
  Once dismissed, a tip does not come back until `queue.reset(id)`.
- `minSessions` waits for the app to have been opened that many times — a tip on
  the first launch is one more thing between a new user and what they came for.
- `prerequisites` names tips that must have been dismissed first, for teaching a
  sequence in order.
- `enabled = false` withdraws it without forgetting it; so does the control
  scrolling out of view, since a tip needs somewhere to point.

The screen stays undimmed and usable: the user is being shown a thing *in* the
screen, and the screen has to stay legible around it.

A tip, and each step of a tour, opens where the whole of it fits, as a tooltip
does: on its `side`, then the other side, then beside the control. It scrolls
only when no side has room for all of it.

## `CoachmarkTour`

A walk through several controls, which the user asked for — a "Show me around"
button, or once on a first run. `rememberCoachmarkTour("plan", "saved", "alerts")`
holds only the **order**; each step's words live at the control they describe, in
`Modifier.coachmarkStep(tour, id, title, message)`, which is the only place they
can be kept honest when that control changes.

`tour.start()` begins it, and each bubble carries its position — "2 / 3" — with
Back and Next, and Back and Done on the last. The way on, Next or Done, is
filled in the bubble's ink so it reads as a button beside Back. A tour of one
step has only "Got it", since there is nothing to go back to or to be done
with. `tour.index`, `isRunning` and `isFinished` say where it is; `finish()`
ends it early.

**A tour is not dismissed by accident.** A tap on the dim, Back and Escape do
nothing by default: the reader asked for the tour, and a stray tap beside the
bubble used to lose the rest of it. Its way out is its own buttons. Pass
`dismissible = true` for a tour that a tap outside, Back or Escape ends.
`showSkip = true` adds **Skip** to every bubble but the last, which goes
straight to the last step (`tour.skip()`), so a skipped tour still ends on
its Done.

While a step shows, everything but the control is dimmed, and a hole is cut
around the control, `CoachmarkDefaults.SpotlightPadding` past its edge with the
theme's container corner. **Everything the dim covers is blocked, the lit control
included**: a tap on the control being explained is the likeliest accident here,
and letting it through would run an action the user was only being shown.

**One dim for the whole tour.** From the first step to the last the page is
dimmed once, and only the hole and the mark change. Each step used to be an
overlay of its own, with its own dim, and the next one arrived while the last
was leaving, so between every pair of steps the page went twice as dark and came
back — *"the background gets twice as dim when animating between two
coachmarks"*. The tour's one overlay is the tour's own now: it stays up across a
change of screen, and comes down when the tour finishes.

### From one step to the next

`rememberCoachmarkTour(…, transition = …)` chooses how the mark goes between
steps — asked for as *"one for just fading one out and fading the new one in,
and another for the mark shrinking into ball, zipping to it's new position, and
expanding back into a new mark"*:

| `CoachmarkTransition` | The mark | The hole |
|---|---|---|
| `Morph`, the default | shrinks into a ball at the side it points from, keeping its own corner until it is round; the ball zips to the next step, taking its colour on the way and carrying a small clearing in the dim with it so it reads in any tone, and grows into the next mark | closes behind the one, opens round the other |
| `Fade` | crossfades: fades out where it is while the next fades in where it will be | its control goes dark as the next one's lights |

Next pressed twice turns the mark round from wherever it has got to rather
than cutting it. Under reduced motion every tour fades.

### A step that points at nothing

`CoachmarkStep(tour, id, title, message)` is a step with no control: a card in
the middle of the dimmed screen, for a welcome, a word between two screens or a
last "that's it" — *"a coachmark that acts as a modal, not pointing to anything
in particular"*. It draws nothing where it is, so put it in the screen it
belongs to, or above the screens for one that belongs to none. A tour of this one
step is a modal of its own, closed with "Got it".

### Across the app

A step shows once its control is on screen, so a tour can go wherever the app
does. Remember it **above the screens it visits**, and give
`rememberCoachmarkTour` a `navigate`: it is told each step's id as the tour
reaches it — at the start, forwards and back — and takes the reader to the screen
that step is on. The dim stays up while the screen changes, and the mark goes to
the step once its control has arrived.

<!--sample:CoachmarkTourAcrossTheApp-->
```kotlin
var tab by remember { mutableStateOf("plan") }

// Above the screens, so it outlives each of them, and told each step as
// it gets there, to take the reader to the screen that step is on.
val tour = rememberCoachmarkTour("welcome", "plan", "saved", transition = CoachmarkTransition.Fade) { step ->
    if (step != "welcome") tab = step
}

// A step that points at nothing: a card in the middle of the dimmed screen.
CoachmarkStep(tour, id = "welcome", title = "Welcome aboard", message = "A minute's tour of the app.")

when (tab) {
    "plan" -> IconButton(
        icon = Tabler.Outline.Route,
        contentDescription = "Plan a trip",
        onClick = { plan() },
        modifier = Modifier.coachmarkStep(tour, id = "plan", title = "Plan a trip", message = "From here to anywhere on the network."),
    )
    else -> IconButton(
        icon = Tabler.Outline.Bookmark,
        contentDescription = "Saved trips",
        onClick = { save() },
        modifier = Modifier.coachmarkStep(
            tour,
            id = "saved",
            title = "Saved trips",
            message = "The ones you keep show up here.",
            tone = Tone.Success,
        ),
    )
}
Button(onClick = tour::start, variant = ButtonVariant.Secondary) { +"Show me around" }
```

A step whose control does not turn up within a second and a half — scrolled out
of a list, or on a screen `navigate` did not go to — is passed over, the way the
reader was going.

### Colour

Every step, and a single tip, takes a `tone`: the accent by default, and any
[`Tone`](../tokens.md) the step is better said in — `Success` for something done,
`Warning` for something to watch, `Neutral` for the inverse surface a toast
uses. The bubble, its arrow and its buttons take the tone's solid colour and its
ink; a morph's ball changes from the one step's colour to the next as it goes.

---

## Accessibility

A coach mark is **not** a control's accessible name. The control keeps its own
`contentDescription`, and the tip says what it is *for*.

The bubble's title and text are one node, read as one sentence rather than as two
unrelated ones, and its buttons carry real labels — "Got it", and in a tour
"Back", "Next" and "Done". A tour's position is read as words, from the theme's strings,
so it follows the reader's language and plural rules.

A tour's dim blocks every tap outside the bubble, so its own buttons are the only
way on — Next, Back, Skip where there is one, or Done to finish. They are
buttons a keyboard and a screen reader reach, so a tour that is not dismissible
is never a trap. Its screen reader dismiss action, and Escape, are there only
when the tour is `dismissible`.

At high contrast a single tip is the enhanced scheme's inverse surface and ink,
which already stand apart from the page, and its "Got it" is a ghost button that
gains the edge those take at this tier. A tour's bubble is the solid accent with
its own ink, in whichever tone it is in, and its position is drawn at full
strength rather than faded.

Under reduced motion a coach mark and a tour's bubble fade in where they will sit
rather than scaling into place, and from one step to the next the marks
crossfade, whichever transition the tour asked for: a ball
travelling across the screen is the movement the preference asks to be spared.

The mark on its way out is taken out of the accessibility tree and takes no
taps, so a screen reader finds only the step arriving.
