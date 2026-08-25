# `PageTransition`

<!--sample:PageTransitionBasics-->
```kotlin
var route by remember { mutableStateOf<Route>(Route.List) }

PageTransition(target = route, modifier = Modifier.fillMaxSize()) { page ->
    when (page) {
        is Route.List -> Column {
            for (stop in stops) {
                Card(
                    modifier = Modifier.sharedBounds("stop-${stop.name}"),
                    onClick = { route = Route.Detail(stop) },
                ) {
                    Text(stop.name)
                }
            }
        }

        is Route.Detail -> Column {
            Card(modifier = Modifier.sharedBounds("stop-${page.stop.name}")) {
                Text(page.stop.name)
            }
            Text("${page.stop.routes} routes")
        }
    }
}
```

The card in the list and the header it becomes carry the same key, so Compose
animates the bounds of one into the other instead of cross-fading two unrelated
rectangles. That is what turns "a new screen appeared" into "the thing I tapped
opened".

`containerTransform` above is the **cheap** version of this, for two elements
that are not literally the same node. This is the real one, and its own KDoc has
said to reach for `SharedTransitionLayout` since it was written.

| | For |
|---|---|
| `Modifier.sharedElement(key)` | The same *kind* of thing — an image, a title |
| `Modifier.sharedBounds(key)` | A **container** whose contents differ between the pages |

Keys are matched across pages, so they identify the subject rather than the
position: `"stop-${stop.id}"`, never `"card"`.

### It takes your state, not a back stack

`target` is any value you already have — a sealed route, an id, a `Boolean` for
"is the detail open". That is why `:ui` still has no navigation dependency, and
it is what lets the same component sit under Navigation 3, an app's own
navigator, or a bare `var route by remember`.

### With Navigation 3

The glue is six lines, and it lives here rather than in the library so it can
name Nav3:

```kotlin
NavDisplay(
    backStack = backStack,
    entryProvider = entryProvider {
        entry<Route.List> { key -> PageContent(key) }
        entry<Route.Detail> { key -> PageContent(key) }
    },
    // One transition for the whole display, driven by the top of the stack.
    transitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
)
```

`NavDisplay` runs its own enter and exit animations, and two animations for one
change is the "background disappears before the sheet does" problem in a
different costume. Switch its own off, wrap the content in a `PageTransition`
keyed on `backStack.last()`, and one thing is in charge.

### Reduced motion

Degrades to a cross-fade with **no bounds morph at all** — `sharedElement` and
`sharedBounds` both become plain modifiers. Unlike the presets above, this is not
a softening: an element flying across the screen is the clearest case that
preference covers, and half a morph is still a thing travelling a long way.

---

← [Adaptive layout and motion](adaptive.md) · [All components](../components.md)
