# `Modifier.coachmark`

*Also on this page: `CoachmarkTour`, `Modifier.coachmarkStep`, `rememberCoachmarkTour`, `CoachmarkDefaults`.*

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

## `CoachmarkTour`

A walk through several controls, which the user asked for — a "Show me around"
button, or once on a first run. `rememberCoachmarkTour("plan", "saved", "alerts")`
holds only the **order**; each step's words live at the control they describe, in
`Modifier.coachmarkStep(tour, id, title, message)`, which is the only place they
can be kept honest when that control changes. A step whose control is not on
screen shows nothing.

`tour.start()` begins it, and each bubble carries its position — "2 / 3" — with
Back and Next, and Back and Done on the last. The way on, Next or Done, is
filled in the bubble's ink so it reads as a button beside Back. A tour of one
step has only "Got it", since there is nothing to go back to or to be done
with. `tour.index`, `isRunning` and `isFinished` say where it is; `finish()`
ends it early.

While a step shows, everything but the control is dimmed, and a hole is cut
around the control, `CoachmarkDefaults.SpotlightPadding` past its edge with the
theme's container corner. **Everything the dim covers is blocked, the lit control
included**: a tap on the control being explained is the likeliest accident here,
and letting it through would run an action the user was only being shown.

---

## Accessibility

A coach mark is **not** a control's accessible name. The control keeps its own
`contentDescription`, and the tip says what it is *for*.

The bubble's title and text are one node, read as one sentence rather than as two
unrelated ones, and its buttons carry real labels — "Got it", and in a tour
"Back", "Next" and "Done". A tour's position is read as words, from the theme's strings,
so it follows the reader's language and plural rules.

A tour's dim blocks every tap outside the bubble, so its own buttons are the only
way on — Next, Back, or Done to finish.

At high contrast a single tip is the enhanced scheme's inverse surface and ink,
which already stand apart from the page, and its "Got it" is a ghost button that
gains the edge those take at this tier. A tour's bubble is the solid accent with
its own ink, and its position is drawn at full strength rather than faded.

Under reduced motion a coach mark and a tour's bubble fade in where they will sit
rather than scaling into place.
