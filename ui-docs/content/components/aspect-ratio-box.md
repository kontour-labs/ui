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
        .background(Theme.colors.surfaceSunken),
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

← [Adaptive](adaptive.md) · [All components](../components.md)
