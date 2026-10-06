# `Badge` / `BadgedBox`

A count or a dot, positioned over what it annotates. `BadgedBox` places it;
`Badge` is the mark itself.

<!--sample:BadgeBasics-->
```kotlin
// A dot, for "something changed" with no number worth reading.
BadgedBox(badge = { Badge() }) {
    IconButton(icon = Tabler.Outline.Bell, contentDescription = "Alerts", onClick = { nearby() })
}

// A count, which caps at `max` and announces itself.
BadgedBox(badge = { Badge(count = 12, contentDescription = "12 unread alerts") }) {
    IconButton(icon = Tabler.Outline.Bell, contentDescription = "Alerts", onClick = { nearby() })
}
```

The navigation surfaces do **not** use `BadgedBox` — they place the badge
against the glyph rather than the touch target, because a 48dp circle around a
24dp icon would otherwise leave the dot floating in empty space.

---

## Accessibility

A badge says what it means as a sentence — "New" for a dot, "3 new" for a
count — rather than as the number: "12" beside "Alerts" is heard as two
unconnected things. Pass `contentDescription` for something more specific, "12
unread alerts".

**Inside a row it is part of the row's text.** In a `ListItem`, a clickable
`Card`, a `SelectionRow` or a button's label, a badge says itself as text, read
in its place among the rest, rather than as a description — which on a merged
row is read instead of the row's text, so a chat row with a badge was announced
as "3 unread" and nothing else. On an icon button it describes itself, as
before, since the button is named by its description.

Counts above `max` render as "9+" visually. Give the real number in the
description where it matters.

At high contrast a badge gains a ring of the page's colour, at `borderWidth`, so
a red dot on a black icon reads as a dot on the icon rather than part of it.

Nothing about a badge moves, so reduced motion changes nothing: a new count
replaces the old one without a transition.
