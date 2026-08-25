# `OverlayHost`

Where everything that draws over the screen actually renders. Installed once, at
the root, inside the theme.

*Also on this page: `ScrimStyle`.*

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

← [Overlays](overlays.md) · [All components](../components.md)
