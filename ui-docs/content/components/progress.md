# `LinearProgress` / `CircularProgress` / `StepProgress`

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
```
| | |
|---|---|
| `LinearProgress` | A bar. Determinate, or indeterminate at `progress = null` |
| `CircularProgress` | A ring, in a space too small for a bar. `progress = null` hands off to `Spinner` — a ring has no indeterminate sweep of its own |
| `StepProgress` | Segmented, for a known number of steps. `current = null` is the sequence with no step reached yet |
| [`Spinner`](spinner.md) | Indeterminate activity, and the library's one loader |

Prefer a determinate one wherever the fraction is known. People wait longer when
they can see the end.

---

## Accessibility

All three report `ProgressBarRangeInfo` and take a `contentDescription`, which
they need: an indicator with no description announces "progress bar" with no
subject.

Describe the work, not the widget — "Downloading timetables", not "Loading".

`progress = null` is indeterminate on both `LinearProgress` and
`CircularProgress`, and honest: a bar that sits at 90% for a minute is worse than
one that admits it does not know. But it also announces no value, so anything
long enough to worry about wants a message beside it. The same goes for
`StepProgress` at `current = null`.

`StepProgress` is for a known number of steps, and the count is the story — give
it "Step 2 of 4" rather than a percentage.
