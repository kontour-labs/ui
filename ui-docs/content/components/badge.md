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

← [Display and content](display.md) · [All components](../components.md)
