# `Popover`

Arbitrary content attached to a control. It points at the thing it is about and
leaves the rest of the screen alone.

**A popover is not a small dialog.** If the content is a decision that must be
made before anything else can happen, it is a [`Dialog`](dialog.md). If it is a
list of actions, it is a [`DropdownMenu`](dropdown-menu.md), which handles
keyboard traversal and the roles a menu owes a screen reader.

`side` and `alignment` are a preference rather than an instruction: the popover
flips to the other side of its anchor when there is not room, which is the
behaviour [anchoring](../overlays.md#anchoring) describes for everything in the
overlay host. `showArrow` draws the tie back to the anchor; turn it off when the
popover is wide enough that the arrow points at nothing in particular.

---

← [Overlays](overlays.md) · [All components](../components.md)
