# `FloatingActionButton`

<!--sample:FloatingActionButtonBasics-->
```kotlin
FloatingActionButton(icon = Tabler.Outline.Plus, contentDescription = "Add favourite", onClick = { add() })
```

One per screen. A second FAB is two competing "the" actions.

---

## Accessibility

`contentDescription` is required and is the button's name. A FAB is an icon with
no label, so nothing else supplies one.

`ExtendedFloatingActionButton` is named by its `contentDescription` whether it
is open or has collapsed to an icon, so the label can be terse — "Start" — while
the name says the whole thing, "Start trip to Perth Station". The name of a
control must not change because the layout did; until 0.4.2 it did, since the
open button was named by its label and the collapsed one by its description.

A FAB floats over the content, which means it can cover it. `Scaffold` accounts
for that in the padding it hands out; a FAB positioned by hand over a scrolling
list needs the same bottom padding on the list, or the last row is permanently
under it.

At high contrast a disabled FAB, which has no shadow and is a well 1.13:1 from
the page, takes an edge. An enabled one takes its colours from the enhanced
scheme and has no edge unless you pass `border`. The focus ring is drawn
thicker.

Under reduced motion a press keeps its tonal wash and loses the shrink.
`ExtendedFloatingActionButton` takes its collapsed or expanded width at once, and
its label fades.
