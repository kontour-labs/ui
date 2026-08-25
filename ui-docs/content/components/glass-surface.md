# `GlassSurface`

A translucent panel over content — a search bar floating on a map, a toolbar
over a photograph.

<!--sample:GlassSurfaceBasics-->
```kotlin
Box(Modifier.fillMaxSize().atmosphere()) {
    Screen()
    GlassSurface(
        modifier = Modifier.align(Alignment.BottomCenter).padding(Theme.spacing.md),
        shape = Theme.shapes.pill,
    ) {
        Text("Live departures", modifier = Modifier.padding(Theme.spacing.md))
    }
}
```

**Translucent, not blurred.** There is no portable backdrop blur in Compose
Multiplatform: Android has `RenderEffect` from API 31, Skia can do it on desktop,
and the web target has neither in a way that survives a canvas. A component that
blurred on two platforms and did nothing on the third would be a component whose
appearance is a per-platform surprise, so this one is honest everywhere —
see [the adaptive page](adaptive.md#there-is-no-portable-backdrop-blur).

---

## Accessibility

Translucency is a contrast risk, and this component does **not** manage it for
you: `alpha` is 0.72 over `Theme.colors.surfaceRaised` and nothing branches on
the contrast tier. The token pairs are contrast-asserted against the *opaque*
surface colour, so a translucent one is no longer the colour those assertions
were about.

What follows is a rule about where to use it. Decoration, a header over a
photograph, a floating pill over a map — fine. Anything a user has to read
carefully, or any control they have to find, wants an opaque
[`Surface`](surface.md); and where you keep the glass, raise `alpha` until the
worst pixel of the backdrop still leaves the text legible, not the average one.

`backdrop` composes the content twice — once blurred, once not — because Compose
has no portable backdrop filter. That is a cost worth knowing before reaching for
it on a scrolling surface.

---

← [Adaptive](adaptive.md) · [All components](../components.md)
