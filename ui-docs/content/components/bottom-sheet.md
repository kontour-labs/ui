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
    // Floating controls that ride up with the sheet rather than being
    // covered by it — the map's "recentre" button is the case this is for.
    actions = {
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

← [Sheets](sheets.md) · [All components](../components.md)
