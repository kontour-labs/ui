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

**Reach for `LinearProgress` or `ProgressRing` instead** when the fraction is
known. A spinner says "wait"; a progress bar says how long, and people wait
longer when they can see the end. Both are in
[display.md](progress.md).

---

← [Actions](actions.md) · [All components](../components.md)
