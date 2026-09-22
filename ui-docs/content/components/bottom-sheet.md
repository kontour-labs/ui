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
stopped answering. The stretch is drawn and nothing else: no anchor, no detent
and nothing the caller sees knows it happened.

**A sheet whose bottom is held rather than open does the same thing downward.**
That is `dismissible = false`, here as well as on
[`ModalBottomSheet`](modal-bottom-sheet.md): a drag past the lowest resting detent
stretches and springs back instead of putting the sheet away. `Hidden` stays in
the anchors, so the app can still close it with `state.hide()` — what the flag
refuses is the *user* doing it. On a modal sheet it also closes the tap outside
and the back gesture, neither of which a plain sheet has.

A sheet at [`SheetDetent.Full`](sheets.md) is the exception, and only to the
*drawing*: it already fills its container, so moving it up would lift its bottom
edge off the bottom of the screen and show a band of background under it. It
still absorbs the pull, which it did not used to — a full-height sheet that did
nothing with an upward drag left the gesture live, so the couple of dozen pixels
a finger travels back down as it leaves the glass read as a flick and dropped the
sheet a detent.

---

## `part` — content that knows which detents it is for

A sheet that collapses to a bar and opens to half a screen is showing two
different things, and writing that as a `when` over the current detent is two
code paths that have to agree. **A part declares its own detent instead.**

```kotlin
BottomSheet(state) {
    part { StopHeader(stop) }
    part(from = SheetDetent.Half) { Departures() }
}
```

`from = null` is a part that is always there. It is worth writing anyway: it puts
every piece of the sheet in the same shape, and makes the ones that come and go
read as the exceptions.

**A part arrives as the sheet passes its detent**, under the finger, and unrolls
out of the sheet as it comes.

It used to wait for the sheet to settle, and the reason was real:
`SheetDetent.Expanded` is measured from the content's height, so a part that
changed that height rebuilt the sheet's anchors, and moving an anchor under a
finger re-pins a drag already in flight. The cost of waiting was worse than the
beat it produced, though — a sheet whose parts were *all* gated could not be
dragged past its collapsed content at all, because the height `Expanded` was
measured from was the height with everything hidden.

So the sheet keeps two heights: what its column places, and what it would place
with every part out. The second is the one the detents are measured from, and
revealing a part cannot change it — a part gains in the first exactly what it
loses in the second, to the pixel. There is nothing left that needed the finger
to lift.

A collapsed part is collapsed for real: it draws nothing, nothing in it can be
tapped, and a screen reader does not announce it. It *is* composed while it is
hidden, which is the trade — the reveal frame has no composition in it, and that
is the frame with a finger on the glass.

The content lambda's receiver is a `SheetContentScope`, which is a `ColumnScope`
with `part` added — so `weight`, `align` and everything else a column offers are
unchanged, and content written before this existed reads and behaves the same.

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

**The margin is given back on the way out.** Below the lowest detent that is
somewhere to *be*, the sheet is leaving and there is nothing under it but hidden,
so the margin shrinks to zero and the sheet lands on the window's edge as it
goes. It used to keep the full margin at every offset, which meant a closing sheet
had a strip of background under it the whole way down and then vanished with the
strip still there — reported as the sheet staying floating while it closed.
Because the pay-back is linear in the sheet's visible height rather than timed, a
sheet dragged down by hand gives it back at the speed of the hand and one let go
gives it back at the speed of the spring, with no second animation to keep in
step.

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
