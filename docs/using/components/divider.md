# `HorizontalDivider` / `VerticalDivider`

Decorative rules. Both are `clearAndSetSemantics {}`, because a line is not
something a screen reader should announce.

A divider is the weakest way to group things and usually the wrong one. Space
separates without drawing anything, and a [`ListSection`](list-section.md) or a
[`Card`](card.md) says *these belong together* rather than *these are apart*.
Reach for a rule where the alternative would be an unreasonable amount of space —
between the halves of a toolbar, under a bar that must read as attached.

---

← [Foundation](foundation.md) · [All components](../components.md)
