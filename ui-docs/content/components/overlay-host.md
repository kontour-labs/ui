# `OverlayHost`

*Also on this page: `ScrimStyle`.*

Where everything that draws over the screen actually renders. Installed once, at
the root, inside the theme.

<!--sample:OverlayHostBasics-->
```kotlin
// One host at the root of the window. Every dialog, menu, popover and
// tooltip below it draws into this, above everything and clipped by nothing.
OverlayHost(Modifier.fillMaxSize()) {
    Screen()
}
```

Every dialog, menu, popover, tooltip, toast and sheet finds the **nearest** host
through `LocalOverlayHost` and renders into it — which is why a screen with no
host throws rather than quietly drawing nothing, and why a panel that installs
its own host contains its overlays inside that panel's bounds.

That "nearest wins" rule is the one that surprises people. A demo card with its
own host keeps its dialog inside the card; the same dialog without one covers the
whole page, scrim and all. Both are correct and only one is usually wanted.

`ScrimStyle` says what the layer behind an entry does: `None` dims nothing and
lets pointers through, `Transparent` blocks without dimming — which is what a
menu wants, so a tap outside closes it without the screen going grey — and
`Dimmed` does both.

The stack, the queue, back handling and focus order are in
[the overlay guide](../overlays.md).

---

## Accessibility

The host is what makes an overlay reachable rather than merely visible.

Each entry is an `isTraversalGroup` with `traversalIndex = index + 1`, and the
screen underneath is `traversalIndex = 0`. Traversal order therefore follows the
stack: a screen reader reaches the newest overlay first and the page last,
however the composables happen to be nested. Without that, an overlay drawn last
in a `Box` is read *after* the content it covers.

The scrim is not decoration. Where an overlay is dismissible it carries an
`onClick` action with a real label — `Theme.strings.dismiss`, or whatever the
overlay passes — so "tap outside to close" exists for someone who cannot tap
outside. Where it is not, the scrim still consumes input, so taps cannot reach
content the user can no longer see.

---

← [Overlays](overlays.md) · [All components](../components.md)
