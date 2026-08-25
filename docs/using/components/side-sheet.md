# `SideSheet`

A panel from the leading or trailing edge — filters, detail, anything that
belongs beside the content rather than over it.

`side` is `SheetSide.Start` or `SheetSide.End`, and start/end rather than
left/right because the whole library lays out by direction: in a right-to-left
locale a start sheet comes from the right, which is what a reader of that locale
expects.

For destinations rather than content, `ModalNavDrawer` is the same motion with a
navigation model attached — a sheet full of links is a drawer wearing the wrong
component.

---

← [Sheets](sheets.md) · [All components](../components.md)
