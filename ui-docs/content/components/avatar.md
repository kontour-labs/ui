# `Avatar` / `AvatarGroup`

Image, initials or icon.

<!--sample:AvatarBasics-->
```kotlin
// Initials from the name, so a missing photo is still a person rather than
// a grey circle. `image` wins when there is one.
Avatar(name = "Ada Lovelace", size = AvatarSize.Large)

// The overflow count is part of the accessible name, not a decoration —
// "+3" read out of context tells a screen-reader user nothing.
AvatarGroup(names = listOf("Ada Lovelace", "Grace Hopper", "Alan Turing", "Ken Thompson"))
```

**Colours are derived from the name**, so the same person is the same colour
everywhere without anyone storing one. The derivation deliberately avoids
`hashCode()` — Kotlin's String hash is not guaranteed identical across
Kotlin/Native and Kotlin/JS, so the same person could be a different colour on
iOS than on Android.

---

← [Display and content](display.md) · [All components](../components.md)
