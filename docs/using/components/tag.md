# `Tag`

**Takes an arbitrary background and derives its own text colour.** Transit feeds
supply route colours that are not drawn from any palette — a route can be pale
yellow or near-black. Passing `color` resolves the label with
`contentColorFor()`, so it stays legible.

That is the whole reason the component exists rather than callers styling a
`Surface` themselves: the case they would get wrong is the one where the feed
hands them a colour nobody designed for.

**Reach for a [`Chip`](chip.md) instead** when the
thing is pressable. A tag is a label; a chip is a control.

---

← [Display and content](display.md) · [All components](../components.md)
