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
list, so an app can add its own — [the sheet guide](sheets.md#the-model)
explains why that matters and what `peek` measures.

**Both ends give.** Pulled above its tallest detent the sheet follows the finger
a little way, each pixel buying less than the last, and springs back when the
finger lifts — a sheet that stops dead at a boundary is a gesture that has
stopped answering. A sheet whose bottom is held rather than open, which is what
[`ModalBottomSheet`](modal-bottom-sheet.md)'s `dismissible = false` produces,
does the same thing downward. The stretch is drawn and nothing else: no anchor,
no detent and nothing the caller sees knows it happened.

A sheet at [`SheetDetent.Full`](sheets.md) is the exception, and only to the
*drawing*: it already fills its container, so moving it up would lift its bottom
edge off the bottom of the screen and show a band of background under it. It
still absorbs the pull, which it did not used to — a full-height sheet that did
nothing with an upward drag left the gesture live, so the couple of dozen pixels
a finger travels back down as it leaves the glass read as a flick and dropped the
sheet a detent.

---

## `presentation` — a drawer out of the screen, or a panel over it

`SheetPresentation.Edge` is the default and is what a sheet has always been:
flush to the bottom and to both sides, top corners rounded, bottom corners square
because there is no bottom edge to round.

`SheetPresentation.Floating` lifts it off all three edges and rounds every
corner. Two things follow, and they are the reasons to reach for it. It reads as
a panel *over* the screen rather than a drawer pulled out of it, which is what a
sheet that is permanently present should look like. And it can shrink to the size
of a control without looking broken — **a bar-height sheet flush to the bottom of
the window reads as a drawer that failed to open**, and the same thing floating
reads as a search field.

```kotlin
val search = rememberSheetState(
    // No `Hidden`, so there is nowhere to be swiped away to.
    detents = listOf(SheetDetent.height("bar", 64.dp), SheetDetent.Half),
    initialDetent = SheetDetent.height("bar", 64.dp),
)

BottomSheet(state = search, presentation = SheetPresentation.Floating) {
    TextField(state = query, placeholder = "Search stops")
}
```

**Collapsing instead of dismissing is a detent question, not a presentation
one.** A sheet's anchors come from its own detent list, so one with no
`SheetDetent.Hidden` in it has no anchor to be dragged away to, and stretches
past its lowest detent exactly as it does above its top. That works at either
presentation; `presentation` only decides what it looks like while it does. Both
halves are checked on every build.

The margin is `Theme.componentDefaults.sheetFloatingInset`, unioned with the window insets
rather than added to them — it is a *minimum* clearance, so on a phone with a
24dp gesture bar the sheet floats 24dp up and not 36. The same line is what lifts
a floating search field above the keyboard, since the sheet's insets carry the
IME.

One cost, and it is structural. An edge sheet's surface is as tall as its
container at every detent and merely translated, so its size never changes and
its shadow is rasterised once; the surplus hangs off the bottom of the screen
where nothing sees it. A floating sheet has a bottom edge that *is* seen, so it
is exactly as tall as it is visible and that changes on every frame of a drag.
Two edges that both move cannot be drawn by a box that never resizes.

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
