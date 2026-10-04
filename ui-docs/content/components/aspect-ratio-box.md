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

### Images from the network

The library loads nothing over the network. Its image slots take a `Painter`
or an `ImageVector`, and a URL needs a client, a decoder and a disk cache —
three dependencies that every app using the library would inherit whether it
showed a remote image or not. An image loader brings all three and is one
line in the build; [Coil 3](https://coil-kt.github.io/coil/) is Compose
Multiplatform's, and it fits inside this box:

```kotlin
// Coil 3: implementation("io.coil-kt.coil3:coil-compose:<version>"), and a
// network module — coil-network-ktor3 with a Ktor engine per platform.
AspectRatioBox(
    ratio = 16f / 9f,
    modifier = Modifier.fillMaxWidth().clip(Theme.shapes.medium),
) {
    SubcomposeAsyncImage(
        model = story.coverUrl,
        contentDescription = story.coverDescription,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
        // The box holds the space; these fill it while there is nothing yet.
        loading = { Skeleton(Modifier.fillMaxSize(), shape = RectangleShape) },
        error = {
            Box(Modifier.fillMaxSize().background(Theme.colours.surfaceSunken), Alignment.Center) {
                Icon(Tabler.Outline.PhotoOff, contentDescription = null, tint = Theme.colours.contentSubtle)
            }
        },
    )
}
```

The box is the reason the page does not jump when the cover lands, the
[`Skeleton`](skeleton.md) says something is coming, and the failed state is
the same sunken well with the picture's absence drawn in it rather than a
broken-image glyph. A slot that takes a `Painter` —
[`Avatar`](avatar.md)'s `image`, a list row's leading picture — takes Coil's
`rememberAsyncImagePainter(url)` as it is.

---

## Accessibility

Reserving the space is an accessibility feature and not only a visual one:
content that arrives and pushes everything down moves the thing a user was
reading, or was about to press, out from under them. That is worst for someone
with a motor impairment and worst again for anyone using a screen magnifier.

The box itself adds no semantics. Whatever goes inside it — an image, a map —
carries its own `contentDescription`.

At high contrast nothing changes: the box draws nothing of its own, so there is
no colour or line for the tier to strengthen.

Under reduced motion nothing changes either, because nothing moves. Whatever
goes inside the box answers to both settings on its own.
