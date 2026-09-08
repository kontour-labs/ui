# `Callout`

The markdown blockquote treatment, for prose. Not dismissible and not a status —
if it can go away, it is a `Banner`.

<!--sample:CalloutBasics-->
```kotlin
// The markdown blockquote treatment, for an aside inside prose. Not a
// status and not dismissible — if it can go away, it is a `Banner`.
Callout {
    Text("Melbourne, Sydney and Canberra do not currently support journey planning.")
}
```

**It is a [`Banner`](banner.md) that cannot go away.** Same `BannerTone`, decided
by the same table, so a warning callout and a warning banner are the same ground,
the same border and the same ink. Two things that both mean "pay attention to
this" should not look like they came from two different libraries.

**The `tone` also picks the icon**, which is the one place a callout has to differ
from a banner. A banner takes its icon from the call site, because the thing it is
a message *about* is the caller's to name. A callout often has no caller worth
asking — a documentation site turns every markdown blockquote into one — so the
tone supplies a default and a caller with an opinion still passes its own. Pass
`icon = null` for tint alone.

**There is no accent rule, after three attempts at one.** It was a 3dp band down
the leading edge, and every version of it lost to the container's own corner:
flush inside the clip it tapered away at both ends, stroked around the whole
outline it read as a "C" bracketing the text, and indented to dodge both it
looked like a tally mark left in the box. A tint and a glyph — which the
component beside it was already using — say the same thing and survive a corner.

---

## Accessibility

A callout is prose. It has no role, no live region and no dismiss — it is
announced as the text it contains, in reading order, like the paragraphs around
it.

That is what makes it wrong for a status. Anything the user needs to be told
about *now* is a [`Banner`](banner.md); anything about something they just did is
a [`Toast`](toast.md). A callout is for a note in the middle of a page that is
worth setting apart visually and no more urgent than its neighbours.

**The icon is why the tone is not colour alone.** WCAG 1.4.1 rules out carrying
information in colour by itself, and a tint is exactly that — so the mark is the
second channel, and it is on by default for that reason rather than for
decoration. It is `contentDescription = null`, deliberately: the tone belongs in
the words, and a reader is not helped by hearing "warning" read out before a
sentence that already says so. If the severity matters, write it.
