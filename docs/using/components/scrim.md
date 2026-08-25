# `Scrim`

Dims what is behind a modal and blocks input to it.

Rarely called directly — [`Dialog`](dialog.md),
[`ModalBottomSheet`](modal-bottom-sheet.md) and the rest draw their own through
the overlay host, and `ScrimStyle` is how a caller asks for a different one.
`ScrimStyle.Transparent` still blocks input while dimming nothing, which is what
a [`DropdownMenu`](dropdown-menu.md) wants: a tap outside should close it without
the screen going grey first.

---

← [Foundation](foundation.md) · [All components](../components.md)
