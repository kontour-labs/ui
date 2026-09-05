# `LoadingOverlay`

Blocks the whole screen while something must not be interrupted.

<!--sample:LoadingOverlayBasics-->
```kotlin
var planning by remember { mutableStateOf(false) }

Box(Modifier.fillMaxSize()) {
    Screen()
    LoadingOverlay(visible = planning, label = "Planning your trip")
}
```

**It should be rare.** Blocking everything for something that usually takes 200ms
trades a brief wait for a flash of grey, and the flash is worse. Prefer a
[`Skeleton`](skeleton.md) where the result will fill a known shape, an inline
[`Spinner`](spinner.md) where one region is loading, and a
[`Button`](button.md)'s own `loading` state where the user pressed a button.

`label` is required and is read aloud, because a blocked screen with no
explanation is indistinguishable from a frozen one.

---

## Accessibility

Blocking the whole screen is the accessibility cost, not the visual one: while
it is up, nothing behind it can be reached by any input, including a screen
reader's. `label` is therefore required rather than optional — a blocked screen
with nothing announced is a screen that has stopped responding.

This is the reason the component should be rare. A [`Skeleton`](skeleton.md) or
an inline [`Spinner`](spinner.md) leaves the rest of the page navigable while one
part of it loads.
