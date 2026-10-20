# `LinearProgress` / `CircularProgress` / `StepProgress` / `CircularStepProgress`

<!--sample:ProgressBasics-->
```kotlin
// Determinate where the total is known.
LinearProgress(progress = 0.4f, contentDescription = "Downloading timetables")

// `null` is indeterminate — for work whose length nobody can predict, which
// is honest rather than a bar that sits at 90% for a minute.
LinearProgress(progress = null, contentDescription = "Finding routes")

CircularProgress(progress = 0.4f, contentDescription = "Downloading timetables")

// For a wizard, where the count is the story.
StepProgress(current = 2, total = 4, contentDescription = "Step 2 of 4")

// The same steps round a ring, for a space too small for a row.
CircularStepProgress(current = 2, total = 4, contentDescription = "Step 2 of 4")

// No gap: one continuous bar, filled to the end of the step.
StepProgress(current = 2, total = 4, gap = 0.dp, contentDescription = "Step 2 of 4")

// A step that knows how far through itself it is: the third of four
// photos, 40% uploaded.
StepProgress(current = 3, total = 4, stepProgress = 0.4f, contentDescription = "Uploading photo 3 of 4")
```
| | |
|---|---|
| `LinearProgress` | A bar. Determinate, or indeterminate at `progress = null` |
| `CircularProgress` | A ring, in a space too small for a bar. `progress = null` hands off to `Spinner` — a ring has no indeterminate sweep of its own |
| `StepProgress` | Segmented, for a known number of steps. `current = null` is the sequence with no step reached yet |
| `CircularStepProgress` | `StepProgress` round a ring, one arc a step from twelve o'clock, for a space too small for a row |
| [`Spinner`](spinner.md) | Indeterminate activity, and the library's one loader |

Prefer a determinate one wherever the fraction is known. People wait longer when
they can see the end.

---

## Accessibility

All four report `ProgressBarRangeInfo` and take a `contentDescription`, which
they need: an indicator with no description announces "progress bar" with no
subject.

Describe the work, not the widget — "Downloading timetables", not "Loading".

`progress = null` is indeterminate on both `LinearProgress` and
`CircularProgress`, and honest: a bar that sits at 90% for a minute is worse than
one that admits it does not know. But it also announces no value, so anything
long enough to worry about wants a message beside it. The same goes for
`StepProgress` and `CircularStepProgress` at `current = null`. Both say the
step themselves when given no description, "Step 2 of 4", as a screen reader
would want it.

**Indeterminate is two bars chasing each other**: a long one sweeping out and a
short one after it, each end speeding up and slowing down on its own curve, so the
bars stretch and shrink as they go — Material's timing, over 1.8 seconds. It
replaced a single band at an even speed, reported as wanting to be "more
interesting". Every bar keeps round ends, down to a sliver, which is drawn as a
dot.

`StepProgress` is for a known number of steps, and the count is the story — give
it "Step 2 of 4" rather than a percentage. At `current = null` the row melts into
one bar and the same two bars chase along it, occluded where they cross the gaps
between segments. It used to light one whole segment at a time, which is a
stepped animation for a state whose whole meaning is that there are no steps to
report.

**`working = true` runs the chase inside the current step**: its position is
known and its progress within it is not. When `current` moves on, the step just
finished fills first, and only once it is full do the bars start in the next
step, from its start — asked for from a phone, so the step "fills up before the
indeterminate animation moves to the next step".

**`stepProgress` fills the current step as far as it has got**, from 0 to 1
as `LinearProgress`'s `progress` is, for a step that can say how much of
itself is done: the third of four photos uploading, a stage of an install.
The steps before it are full, and the step itself fills on the same spring as
the rest of the row, in the bar and round the ring alike. It takes the place
of `working`'s bars, which are for a step that cannot say, and a screen
reader hears the step in progress and how far through the steps it is, 2.4
of 4. Left `null`, a step reached is drawn full, as it always has been.

**`CircularStepProgress` is the same steps round a ring**, one arc a step,
clockwise from twelve o'clock and counter-clockwise in a right-to-left
language. It moves through the same animation as the row, so its fill, its
melt, its chase and its still stub under reduced motion are the row's, and the
two read as one thing on one screen. Its gaps are measured along the ring and
leave room for the round ends, so the gap you see is the `gap` you pass.

**`gap = 0.dp` draws the steps as one bar**, or one ring, with no seams, and
its fill stops round at the end of the step it has reached. For where the count
is said elsewhere and the row only has to show how far along it is. Before,
the segments met at their round ends and the bar pinched at every step.

At [high contrast](../accessibility.md#what-high-actually-changes) the track
under the fill becomes `inactive` on all four, 3:1 from the default fill and
4.5:1 on the page. The bar and the ring keep their thickness, and nothing else
changes.

Under reduced motion nothing loops but the spinner. An indeterminate
`LinearProgress` holds a still stretch of fill at its start, `StepProgress` and
`CircularStepProgress` hold a still stub where their bars would run, and `CircularProgress` with
`progress = null`, being a [`Spinner`](spinner.md), keeps turning with its
breathing stilled. A new value still eases to its fill, over 150ms and without
overshoot.
