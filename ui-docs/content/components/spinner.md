# `Spinner`

An indeterminate activity indicator. The arc sweeps *and* breathes — its length
grows and shrinks as it rotates, so the tail chases the head. Under reduced
motion the breathing stops and the arc holds a constant length.

<!--sample:SpinnerBasics-->
```kotlin
Spinner(contentDescription = "Loading departures")
```

`contentDescription` defaults to `null`, which is right when the spinner sits
inside something that already announces itself as busy — a loading `Button`
does, so its spinner is silent. Pass one when the spinner is the only thing
saying work is happening.

**Reach for `LinearProgress` or `CircularProgress` instead** when the fraction is
known. A spinner says "wait"; a progress bar says how long, and people wait
longer when they can see the end. Both are in
[display.md](progress.md).

---

## Accessibility

`Spinner` carries `ProgressBarRangeInfo` and takes a `contentDescription` — an
indeterminate indicator with no description is an announced "progress bar" with
no subject.

Give it what is happening, not that something is: "Finding routes", not
"Loading". Where the spinner is inside a [`Button`](button.md) with
`loading = true`, leave it to the button — that clears the spinner's semantics
and puts the state on the button instead, so nothing is announced twice.

Under reduced motion the arc stops breathing and holds a constant length. It
still turns — an indicator that stopped moving is an indicator that looks stuck,
which is a worse answer than a calmer one.

---

← [Actions](actions.md) · [All components](../components.md)
