# `ModalSideSheet`

A panel from the side that owns the screen until it is dealt with — filters, a
detail, a second level of something. Scrim behind, focus held inside, dismissed by
a tap outside, by back, or by its own close button.

<!--sample:ModalSideSheetBasics-->
```kotlin
var open by remember { mutableStateOf(false) }

ModalSideSheet(
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

The wide-screen counterpart of [`ModalBottomSheet`](modal-bottom-sheet.md). On a
phone a bottom sheet is right because the content is near the thumb and the screen
is taller than it is wide; on a tablet or desktop the same sheet becomes a short
letterbox across a very wide window, and a side sheet uses the shape of the screen
instead. For a panel that shares the screen with the page, use
[`SideSheet`](side-sheet.md).

It **floats by default**, and takes `expandable`, `edgeMorph` and
`expandedShape` exactly as [`SideSheet`](side-sheet.md#floating-and-expanding-to-the-whole-window)
does: a grip on its inner edge widens it to the whole window, and a floating
sheet becomes an edge sheet on the way.

`scrim = ScrimStyle.None` lets the page behind take pointer events and stops the
sheet trapping focus, but it is still an overlay in the `OverlayHost`, above
everything in the page. A rail that belongs *in* the layout is a `SideSheet`.

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
