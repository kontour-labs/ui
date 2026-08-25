# `LinearProgress` / `ProgressRing` / `StepProgress`

<!--sample:ProgressBasics-->
```kotlin
// Determinate where the total is known.
LinearProgress(progress = 0.4f, contentDescription = "Downloading timetables")

// `null` is indeterminate — for work whose length nobody can predict, which
// is honest rather than a bar that sits at 90% for a minute.
LinearProgress(progress = null, contentDescription = "Finding routes")

ProgressRing(progress = 0.4f, contentDescription = "Downloading timetables")

// For a wizard, where the count is the story.
StepProgress(current = 2, total = 4, contentDescription = "Step 2 of 4")
```
| | |
|---|---|
| `LinearProgress` | Determinate or indeterminate |
| `ProgressRing` | Circular, determinate |
| `StepProgress` | Segmented, for a known number of steps |
| [`Spinner`](spinner.md) | Indeterminate activity |

Prefer a determinate one wherever the fraction is known. People wait longer when
they can see the end.

---

← [Display and content](display.md) · [All components](../components.md)
