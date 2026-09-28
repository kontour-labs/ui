# `Scrim`

Dims what is behind a modal and blocks input to it.

<!--sample:ScrimBasics-->
```kotlin
var open by remember { mutableStateOf(false) }

Box(Modifier.fillMaxSize()) {
    Screen()
    // `fraction` is a lambda rather than a `Float`: a sheet reads its own
    // drag offset through it every frame, so the dimming tracks the gesture
    // without the scrim recomposing.
    Scrim(fraction = { if (open) 1f else 0f }, onDismissRequest = { open = false })
}
```

Rarely called directly — [`Dialog`](dialog.md),
[`ModalBottomSheet`](modal-bottom-sheet.md) and the rest draw their own through
the overlay host, and `ScrimStyle` is how a caller asks for a different one.
`ScrimStyle.Transparent` still blocks input while dimming nothing, which is what
a [`DropdownMenu`](dropdown-menu.md) wants: a tap outside should close it without
the screen going grey first.

---

## Accessibility

`dismissLabel` is required whenever `onDismissRequest` is set, and the KDoc says
so: the scrim carries a real `onClick` action, and "button" is not a useful thing
to hear when a dialog opens.

With `onDismissRequest = null` the scrim still consumes pointer input, so taps
cannot reach content the user can no longer see, and it exposes no action —
correct for a confirmation that must be answered.

`fraction` is a lambda read in the draw phase. That is a performance decision
rather than an accessibility one, but it has an accessibility consequence worth
knowing: the scrim does not recompose as it dims, so nothing is re-announced
while a sheet is dragged.

At high contrast the enhanced scheme's `scrim` is darker than the standard
tier's, in light and in dark, because what it separates is higher contrast too.
Nothing else about it changes.

Nothing about a scrim moves. It is as dark as the thing in front of it has
arrived, so under reduced motion it fades in step with that thing's own fade, and
a change of colour when another overlay opens above it takes `fast` rather than
`default`.
