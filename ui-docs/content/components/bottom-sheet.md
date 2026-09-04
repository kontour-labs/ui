# `BottomSheet`

A panel from the bottom edge that shares the screen with whatever is behind it.
The map stays usable; the sheet rests at a detent and is dragged between them.

<!--sample:BottomSheetBasics-->
```kotlin
val sheet = rememberSheetState(
    detents = listOf(
        SheetDetent.Hidden,
        SheetDetent.peek(140.dp),
        SheetDetent.Half,
        SheetDetent.Expanded,
    ),
    initialDetent = SheetDetent.peek(140.dp),
)

BottomSheet(
    state = sheet,
    // Controls that ride up with the sheet rather than being covered by
    // it — the map's "recentre" button is the case this is for.
    floatingControls = {
        IconButton(
            icon = Tabler.Outline.CurrentLocation,
            contentDescription = "Recentre",
            onClick = { recentre() },
            variant = ButtonVariant.Secondary,
        )
    },
) {
    // `sheetPeekAnchor` is what makes `peek` mean "as tall as this", so the
    // peek height follows the header instead of being a number to maintain.
    SheetHeader(modifier = Modifier.sheetPeekAnchor()) {
        +"Perth Underground"
        supporting { +"Platform 2 · Joondalup line" }
    }
    Departures()
}
```

Non-modal, which is the whole difference from
[`ModalBottomSheet`](modal-bottom-sheet.md): there is no scrim, nothing is
blocked, and the sheet is a second surface rather than an interruption. That is
what makes it right for a stop list over a map and wrong for a form.

Its resting positions come from a `rememberSheetState(detents = …)`. `Hidden`,
`Half`, `Expanded`, `Full` and `peek(…)` are values rather than an enum entries
list, so an app can add its own — [the sheet guide](../sheets.md#the-model)
explains why that matters and what `peek` measures.

---

## Accessibility

Pass **`paneTitle`**. The sheet sets it as pane semantics, which is how a screen
reader says what the region that just appeared *is* — without it the user is told
a new area exists and not what it holds. It is optional in the signature because
a sheet whose first child is a titled [`SheetHeader`](sheet-header.md) already
says so; it is not optional in any other case.

A non-modal sheet leaves the content behind it reachable, which is the whole
reason to choose one — so nothing here traps focus, and the reader can move
between the map and the sheet freely.

The detents are reachable without the gesture: [`DragHandle`](drag-handle.md)
exposes `expand` and `collapse` as semantic actions, so the sheet can be resized
from a screen reader's rotor rather than only by dragging.
