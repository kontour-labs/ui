# `SideSheet`

A panel from the side that shares the screen with the page beside it — a filter
rail beside a list, an inspector beside a canvas. Nothing behind it is dimmed or
blocked, so a filter changed in the sheet is seen taking effect as it is made.

<!--sample:SideSheetBasics-->
```kotlin
var filtersOpen by remember { mutableStateOf(true) }

Box(Modifier.fillMaxSize()) {
    // The list stays the list: nothing is dimmed or blocked, so a filter
    // changed in the sheet is seen taking effect beside it.
    Departures()

    SideSheet(visible = filtersOpen, paneTitle = "Filters") {
        // No scrim to tap and no back gesture to catch — the app owns
        // `visible`, so the header's close button is how it goes away.
        SheetHeader(onClose = { filtersOpen = false }) { +"Filters" }
        Column(
            modifier = Modifier.padding(horizontal = Theme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
        ) {
            Text("Only show routes that run in the next hour")
        }
    }
}
```

It is to [`ModalSideSheet`](modal-side-sheet.md) what
[`BottomSheet`](bottom-sheet.md) is to [`ModalBottomSheet`](modal-bottom-sheet.md).
For a sheet that owns the screen until it is answered, use the modal one.

**It lives in your layout**, not in the `OverlayHost`: put it in a `Box` over the
content it sits beside, and it fills that box and places itself against its side.
Everywhere it is not, the page underneath gets every touch and every click.

**The app owns `visible`.** There is no scrim to tap and no back gesture to catch,
so nothing here asks to be closed — give the sheet's header an `onClose` that sets
`visible` to false. It slides in and out on its own spring, a tween under reduced
motion, and is not composed at all once it has slid away.

`side` is `SheetSide.Start` or `SheetSide.End`, and start/end rather than
left/right because the whole library lays out by direction: in a right-to-left
locale a start sheet comes from the right, which is what a reader of that locale
expects.

---

## One width, flush to its side

A side sheet is **flush to its side, the top and the bottom**, with only the
corners facing the content rounded, at the width it was given. It could float
clear of its edges and be dragged out to the whole window for a while; both were
taken out again, asked for and tried and not wanted. A panel that needs more room
is a different layout — a pane — rather than a sheet that grows.

---

## Accessibility

**Focus is not trapped.** The sheet is beside the page rather than over it, and a
keyboard or switch user moves between the two as a pointer does — trapping focus
in a rail would lock them out of the list it is filtering. It is a traversal group,
so its controls are read together rather than interleaved with the page's.

Pass **`paneTitle`**, so a screen reader announces the region by name when focus
enters it — the same reasoning as [`BottomSheet`](bottom-sheet.md), and the same
default of none.
