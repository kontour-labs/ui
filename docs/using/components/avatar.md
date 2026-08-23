# `Avatar` / `AvatarGroup`

Image, initials or icon.

**Colours are derived from the name**, so the same person is the same colour
everywhere without anyone storing one. The derivation deliberately avoids
`hashCode()` — Kotlin's String hash is not guaranteed identical across
Kotlin/Native and Kotlin/JS, so the same person could be a different colour on
iOS than on Android.

---

← [Display and content](display.md) · [All components](../components.md)
