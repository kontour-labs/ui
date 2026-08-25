# `Text`

The typographic primitive. Resolves its style and its colour from the theme
rather than taking them, so a paragraph inside a `Card` on a dark scheme needs
no arguments at all.

Two overloads, `String` and `AnnotatedString`. The second is what carries spans —
a route number in the accent colour inside a sentence — without a second
component or a second style.

Colour comes from `LocalContentColor`, which [`Surface`](surface.md) sets. That
chain is the reason a component can be dropped on a dark card and stay legible
without every child being told where it is.

---

← [Foundation](foundation.md) · [All components](../components.md)
