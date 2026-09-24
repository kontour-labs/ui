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

## Floating, and expanding to the whole window

`SideSheet` **floats by default**: lifted off its side, the top and the bottom by
`Theme.componentDefaults.sheetFloatingInset`, with every corner rounded — a panel
over the page rather than a drawer out of the window's edge. It is the floating
bottom sheet's presentation on the other axis, with the same margin, unioned with
the window's insets rather than added to them.
`presentation = SheetPresentation.Edge` puts the sheet flush against its side,
with only the corners facing the content rounded — which is where
[`ModalSideSheet`](modal-side-sheet.md) starts, since a modal sheet recedes the
page behind it and a floating panel in front of that is two frames around one
thing.

`expandable = true` puts a **grip on the sheet's inner edge** — the one facing the
page — that drags the sheet out to the far side of the window and back. A tap on
it toggles, and so do a screen reader, a keyboard and a switch, none of which can
drag; `SideSheetState.expand()` and `collapse()` do the same from code.

```kotlin
val filters = rememberSideSheetState()

SideSheet(visible = open, expandable = true, state = filters) { … }
```

**A floating sheet becomes an edge sheet as it expands**, the way a floating
bottom sheet does on its last step. Its margins close up and its corners become
`expandedShape`'s — square by default, since a page filling the window has no
corners of its own — so it arrives flush to every edge. It follows the grip
frame by frame: held halfway, it is halfway there.

| | Resting | Expanded |
|---|---|---|
| Floating, the default | inset on three sides, every corner rounded | the whole window, square |
| Floating, `edgeMorph = false` | inset on three sides | the window less the margin on all four sides, still rounded |
| `Edge` | flush to its side, inner corners rounded | the whole window, square |

**The content keeps clear of whatever the sheet now covers.** While it floats,
its margin clears the window's insets and the content is not padded for them.
As it widens, each side is padded by what the margin no longer clears — and the
far side, which a resting sheet is nowhere near, by however much of that side's
inset the sheet has reached. A cutout on the far side of a landscape phone is
padded for exactly when the sheet arrives at it.

**A window no wider than the resting sheet has nothing to expand across**, so
there the grip is not shown and `expand()` does nothing. A sheet closed while
expanded opens again at its resting width.

Expanding is opt-in, and a navigation drawer is why: it is a side sheet that has
no business filling the window.

---

## Accessibility

**Focus is not trapped.** The sheet is beside the page rather than over it, and a
keyboard or switch user moves between the two as a pointer does — trapping focus
in a rail would lock them out of the list it is filtering. It is a traversal group,
so its controls are read together rather than interleaved with the page's.

Pass **`paneTitle`**, so a screen reader announces the region by name when focus
enters it — the same reasoning as [`BottomSheet`](bottom-sheet.md), and the same
default of none.

The expand grip is a button: labelled "Expand sheet" or "Collapse sheet" by its
state, and activated by a tap, a screen reader or a keyboard, none of which can
drag.
