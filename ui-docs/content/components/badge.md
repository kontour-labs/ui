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

A badge has **no description by default**, and that is the right default: the
count is usually already announced by whatever it is attached to, and a badge
that announced itself would double it.

Where it is the only source — a dot on an icon button with nothing else saying
there is anything new — pass `contentDescription`. Write it as a sentence, "12
unread alerts", not as the number: "12" beside "Alerts" is heard as two
unconnected things.

Counts above `max` render as "9+" visually. Give the real number in the
description where it matters.
