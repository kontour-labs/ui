# `PageTransition`

<!--sample:PageTransitionBasics-->
```kotlin
var route by remember { mutableStateOf<Route>(Route.List) }

PageTransition(target = route, modifier = Modifier.fillMaxSize()) { page ->
    when (page) {
        is Route.List -> StopList(onOpen = { route = Route.Detail(it) })
        is Route.Detail -> StopDetail(page.stop)
    }
}
```

The two pages are ordinary composables. `sharedBounds` reaches the transition
through a composition local, so neither takes a scope, and either renders on its
own — in a test, or in a pane with no transition around it — with the modifier
quietly doing nothing:

```kotlin
@Composable
private fun StopList(onOpen: (Stop) -> Unit) {
    Column {
        for (stop in stops) {
            Card(
                modifier = Modifier.sharedBounds("stop-${stop.name}"),
                onClick = { onOpen(stop) },
            ) {
                Text(stop.name)
            }
        }
    }
}

@Composable
private fun StopDetail(stop: Stop) {
    Column {
        Card(modifier = Modifier.sharedBounds("stop-${stop.name}")) {
            Text(stop.name)
        }
        Text("${stop.routes} routes")
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

### The element does not have to be written inside the lambda

`content` takes the page and nothing else. The two scopes a shared element needs
reach it through a composition local, so `Modifier.sharedElement(key)` works at
any depth — inside `StopList()`, inside the row it renders, inside a component
three modules away that has never heard of this one. Outside a `PageTransition`
both modifiers do nothing, the same bargain `Modifier.selectionIndicatorItem`
makes, so a page is an ordinary component that renders in a test or in a pane
without one.

That is the API rethink. The scopes used to be a **receiver** on `content`,
which meant a shared element could only be written literally inside that lambda.
Factor a page into its own composable — as the example above does, and as every
real app does — and the call stopped compiling; the escape was to thread the
scope down as a parameter through every composable in between, which is worse
than the incantation the receiver was hiding. This page's own example used to
show pages as separate composables *and* `Modifier.sharedBounds` inside them,
which was not a combination that could be written.

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

## Accessibility

Under reduced motion the shared-element morph is skipped and the change is a
crossfade. Nothing about what is announced changes, which is the property that
matters: a transition is a way of showing a change, not the change itself.

A transition does not move focus. After navigating, focus stays where it was
unless the screen moves it — so a screen that replaces its content should send
focus to the new content's heading, or a screen-reader user is left reading a
page that is no longer there.

Give the incoming screen a heading. It is what makes the arrival findable.

---

← [Adaptive layout and motion](adaptive.md) · [All components](../components.md)
