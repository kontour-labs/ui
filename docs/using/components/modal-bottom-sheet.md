# `ModalBottomSheet`

A task that owns the screen until it is finished. Scrim behind, dismissed by a
tap outside, by back, or by dragging it down.

On a phone this is usually the right answer where a desktop would use a
[`Dialog`](dialog.md): the controls arrive under the thumb rather than in the
middle of the screen, and the dismissal gesture is the one people already use.

Takes `visible` and `onDismissRequest` rather than a sheet state, because a modal
sheet has one meaningful position and the state object exists to describe
several. Reach for [`BottomSheet`](bottom-sheet.md) when what is behind the sheet
is still the point.

---

← [Sheets](sheets.md) · [All components](../components.md)
