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

## Floating, and expanding to the whole window

`presentation = SheetPresentation.Floating` lifts the sheet off its side, the top
and the bottom by `Theme.componentDefaults.sheetFloatingInset`, and rounds every
corner: a panel over the page rather than a drawer out of the window's edge. It
is the floating bottom sheet's presentation on the other axis, with the same
margin, unioned with the window's insets rather than added to them.

`expandable = true` puts a **grip on the sheet's inner edge** — the one facing the
page — that drags the sheet out to the far side of the window and back. A tap on
it toggles, and so do a screen reader, a keyboard and a switch, none of which can
drag; `SideSheetState.expand()` and `collapse()` do the same from code.

```kotlin
val filters = rememberSideSheetState()

SideSheet(
    visible = open,
    onDismissRequest = { open = false },
    presentation = SheetPresentation.Floating,
    expandable = true,
    state = filters,
) { … }
```

**A floating sheet becomes an edge sheet as it expands**, the way a floating
bottom sheet does on its last step. Its margins close up and its corners become
`expandedShape`'s — square by default, since a page filling the window has no
corners of its own — so it arrives flush to every edge. It follows the grip
frame by frame: held halfway, it is halfway there.

| | Resting | Expanded |
|---|---|---|
| Edge | flush to its side, inner corners rounded | the whole window, square |
| Floating | inset on three sides, every corner rounded | the whole window, square |
| Floating, `edgeMorph = false` | inset on three sides | the window less the margin on all four sides, still rounded |

**The content keeps clear of whatever the sheet now covers.** While it floats,
its margin clears the window's insets and the content is not padded for them.
As it widens, each side is padded by what the margin no longer clears — and the
far side, which a resting sheet is nowhere near, by however much of that side's
inset the sheet has reached. A cutout on the far side of a landscape phone is
padded for exactly when the sheet arrives at it.

**A window no wider than the resting sheet has nothing to expand across**, so
there the grip is not shown and `expand()` does nothing. A sheet closed while
expanded opens again at its resting width.

Both are opt-in, and a navigation drawer is why: it is a side sheet that has no
business filling the window.

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
