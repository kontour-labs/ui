# `DragHandle`

The grab bar at the top of a sheet.

<!--sample:DragHandleBasics-->
```kotlin
val sheet = rememberSheetState(
    detents = listOf(SheetDetent.peek(140.dp), SheetDetent.Expanded),
    initialDetent = SheetDetent.peek(140.dp),
)

// Every sheet draws one already. Pass `dragHandle` only to replace it, or
// `null` to take it away — a sheet with `draggable = false` should not
// advertise a gesture it does not have.
BottomSheet(state = sheet, dragHandle = { DragHandle(state = sheet) }) {
    SheetHeader(modifier = Modifier.sheetPeekAnchor()) { +"Perth Underground" }
    Departures()
}
```

**It is drawn, not draggable.** The whole sheet is already draggable, and a
handle that is the *only* draggable part makes a 4dp-tall target the user has to
hit.

It does carry the sheet's accessibility actions, though, since a drag is not a
gesture a screen reader can perform: expand and collapse appear as actions on the
handle, which is why it is not marked decorative. It widens slightly on hover and
under a press — the one hint a pointer user gets that a sheet is draggable at
all.

**Its band is the content's top padding, and all of it.** In a sheet the handle
sits in the middle of a 24dp band, `DragHandleDefaults.Target`, and nothing under
it adds more: a `SheetHeader` or the sheet's `header` starts at the band's foot. The
header used to add its own padding and its close button's empty space under the
band, reported as *"a fair bit of extra space between the top of the sheet header
… and the sheet handle"*. For a while the band was 16dp, the sheet's inset at the
sides, and the handle then sat on the content: *"the top of it looks a bit cramped
now"*. So the top has the handle's room, and the sides and the bottom have
`BottomSheetDefaults.ContentPadding`, 16dp. On a display whose corners are rounder
than the sheet's, the band is taller by what the corner cuts in, with the handle
still in its middle: see [the header's](sheet-header.md#the-space-round-it).

`BottomSheet` and `ModalBottomSheet` draw one by default and take a `dragHandle`
slot to replace or remove it. [Sheets](sheets.md) is where the detents, the
nested scrolling and the rest of the sheet story live.

---

## Accessibility

The handle is the sheet's resize control for anyone not using the gesture. It
carries `expand` and `collapse` as **semantic actions** wired to the sheet state,
so the detents are reachable from a screen reader's actions rotor.

Its `contentDescription` tracks the current detent: `Theme.strings.collapseSheet`
when the sheet is at its last detent, `expandSheet` otherwise. A handle that
always announced "expand" would be lying half the time.

`dragHandle = null` removes it. That is correct with `draggable = false` — a
sheet should not advertise a gesture it does not have — and wrong in every other
case, because it takes the actions away with it.

At high contrast the bar is drawn in the enhanced scheme's colours at the same
size; nothing else about it changes.

Under reduced motion the bar still widens under a pointer or a finger, over a
short tween with no overshoot. The expand and collapse actions put the sheet at
its new detent at once instead of sliding it there.
