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

---

## Accessibility

A callout is prose. It has no role, no live region and no dismiss — it is
announced as the text it contains, in reading order, like the paragraphs around
it.

That is what makes it wrong for a status. Anything the user needs to be told
about *now* is a [`Banner`](banner.md); anything about something they just did is
a [`Toast`](toast.md). A callout is for a note in the middle of a page that is
worth setting apart visually and no more urgent than its neighbours.

`accent` and `container` change colour only. Do not use colour alone to carry
severity — WCAG 1.4.1 — so if the distinction matters, say it in the words.
