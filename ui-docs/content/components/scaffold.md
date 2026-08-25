# `Scaffold`

The frame a screen sits in: a top bar, a bottom bar, a floating action button,
and content handed the padding rather than squeezed by it.

<!--sample:ScaffoldBasics-->
```kotlin
Scaffold(
    topBar = { TopBar { +"Favourites" } },
    floatingActionButton = {
        FloatingActionButton(
            icon = Tabler.Outline.Star,
            contentDescription = "Add a favourite",
            onClick = { add() },
        )
    },
) { padding ->
    // Handed over rather than applied, so content can scroll *under* the
    // bar instead of starting below it.
    Column(Modifier.fillMaxSize().padding(padding)) {
        Screen()
    }
}
```

Handing out `PaddingValues` instead of insetting the content is the decision
worth knowing about. A list inside a scaffold should scroll *under* the top bar
rather than starting below it, which it can only do if it receives the padding
and applies it to its own content rather than to itself.

`contentWindowInsets` defaults to `safeDrawing`, so the status bar, the gesture
bar and a display cutout are already accounted for.

---

← [Adaptive](adaptive.md) · [All components](../components.md)
