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

`ExtendedFloatingActionButton` announces its visible label while expanded and
falls back to `contentDescription` once it has collapsed to an icon. Write the
two to say the same thing: otherwise the control is called one name at the top of
a list and another after the user has scrolled, and the name of a control must
not change because the layout did.

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
