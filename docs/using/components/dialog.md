# `Dialog`

A decision that must be made before anything else can happen. Centred, over a
dimmed scrim, and it takes the focus with it.

Reach for a [`Popover`](popover.md) when the rest of the screen is still usable —
a dialog says *stop*, and saying it about something optional is how people learn
to dismiss dialogs without reading them. For the ordinary confirm-or-cancel
shape, [`AlertDialog`](alert-dialog.md) already lays the buttons out.

**A dialog scrolls its own content.** It is centred in the window with nowhere
else to go, so content taller than the window has no other way out. Without the
scroller it did not merely spill: the content was measured unbounded and placed
in a box the window had clamped, so it **overlapped itself** — a `DatePicker` in
a dialog on a landscape phone drew the last two weeks of the month in the same
cells, "23" over "30", the rest cut through the middle of the digits.

`windowInsets` defaults to every edge including the keyboard, because a dialog is
centred rather than pinned and a confirmation with a text field in it is common.

See [the overlay guide](../overlays.md#the-stack) for how it stacks with menus
and sheets, and what back does.

---

← [Overlays](overlays.md) · [All components](../components.md)
