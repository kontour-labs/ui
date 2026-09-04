# `SideSheet`

A panel from the leading or trailing edge — filters, detail, anything that
belongs beside the content rather than over it.

<!--sample:SideSheetBasics-->
```kotlin
var open by remember { mutableStateOf(false) }

SideSheet(
    visible = open,
    onDismissRequest = { open = false },
    side = SheetSide.End,
    // Given a back arrow, the sheet becomes a second level rather than a
    // dead end — the filters open, and closing them returns to the list.
    onBack = { open = false },
) {
    SheetHeader { +"Filters" }
    Column(
        modifier = Modifier.padding(horizontal = Theme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(Theme.spacing.xs),
    ) {
        Text("Only show routes that run in the next hour")
    }
}
```

`side` is `SheetSide.Start` or `SheetSide.End`, and start/end rather than
left/right because the whole library lays out by direction: in a right-to-left
locale a start sheet comes from the right, which is what a reader of that locale
expects.

For destinations rather than content, `ModalNavDrawer` is the same motion with a
navigation model attached — a sheet full of links is a drawer wearing the wrong
component.

---

## Accessibility

Pass **`paneTitle`** — the same reasoning as [`BottomSheet`](bottom-sheet.md),
and the same default of none.

`onBack` adds a labelled back button (`backLabel`) rather than relying on the
platform gesture, which is what makes a sheet that is a second *level* legible
as one: filters open, and going back returns to the list rather than closing
everything.

`side` is `SheetSide.Start` / `End`, not left and right, so the sheet follows the
layout direction. A sheet pinned to the left is a sheet that slides in from the
wrong edge in Arabic.
