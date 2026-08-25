# `Card`

<!--sample:CardBasics-->
```kotlin
Card(variant = CardVariant.Outlined, onClick = { openStop("Perth Underground") }) {
    Text("Perth Underground", style = Theme.typography.titleSmall)
    Text(
        "Platform 2 · Joondalup line",
        style = Theme.typography.bodySmall,
        color = Theme.colors.contentMuted,
    )
}
```
`Elevated`, `Outlined` or `Filled`, optionally clickable as a whole.

`Elevated` and `Filled` take a `contrastEdge()` at the high-contrast tier. An
elevated card is white on white with a shadow for an edge, and a shadow does not
change between tiers — `surfaceSunken` on `background` measures **1.14:1** at
the high-contrast light tier, against the 3:1 WCAG 1.4.11 asks of a control's
boundary.

---

## Accessibility

A card with `onClick` reports `Role.Button` and merges its content, so it is one
target announcing everything inside it. A card without one is a container and
announces nothing of its own.

The consequence is the rule: **do not put buttons inside a clickable card.** A
target inside a target is ambiguous to touch and unreachable to a screen reader
that has merged the two. Either the card is the action, or the card is a
container with its own buttons — not both.

Elevation and border are `CardVariant`, which is visual. Nothing about the
variant changes what is announced.

---

← [Display and content](display.md) · [All components](../components.md)
