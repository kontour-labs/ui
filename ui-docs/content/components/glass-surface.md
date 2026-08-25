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

← [Adaptive](adaptive.md) · [All components](../components.md)
