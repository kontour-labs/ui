# `AspectRatioBox`

Reserves a box of a known ratio before its content has arrived.

<!--sample:AspectRatioBoxBasics-->
```kotlin
// The space is reserved before the photo arrives, so nothing below it moves
// when it does — which is the whole point.
AspectRatioBox(
    ratio = 16f / 9f,
    modifier = Modifier
        .fillMaxWidth()
        .clip(Theme.shapes.medium)
        .background(Theme.colours.surfaceSunken),
) {
    Screen()
}
```

The reason is layout shift. An image that lands after the text around it has
been placed pushes everything down at the moment somebody started reading, and
the fix is to occupy the space in advance. `Modifier.aspectRatio` does the same
arithmetic; this is the version that reads as an intention at the call site and
pairs with a [`Skeleton`](skeleton.md) while the content loads.

---

## Accessibility

Reserving the space is an accessibility feature and not only a visual one:
content that arrives and pushes everything down moves the thing a user was
reading, or was about to press, out from under them. That is worst for someone
with a motor impairment and worst again for anyone using a screen magnifier.

The box itself adds no semantics. Whatever goes inside it — an image, a map —
carries its own `contentDescription`.

---

← [Adaptive](adaptive.md) · [All components](../components.md)
