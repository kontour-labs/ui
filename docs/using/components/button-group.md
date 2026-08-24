# `ButtonGroup`

![ButtonGroup](../../../ui-catalog/screenshots/components/buttongroup-light.png)

<!--sample:ButtonGroupBasics-->
```kotlin
ButtonGroup {
    item(onClick = { zoomOut() }, contentDescription = "Zoom out", icon = Tabler.Outline.Minus)
    item(
        onClick = { recentre() },
        contentDescription = "Recentre",
        icon = Tabler.Outline.CurrentLocation,
    )
    item(onClick = { zoomIn() }, contentDescription = "Zoom in", icon = Tabler.Outline.Plus)
}
```

The buttons sit flush and only the outside corners round — the same treatment
[`ListItemPosition`](list-item.md) gives a group of rows. That is the
whole visual idea: three separate buttons say "three things", one joined group
says "one thing, three ways".

It is a **builder**, not a row of `Button`s, because each button's shape depends
on how many there are — which is not known until they have all been declared.
Note that the builder collects rather than composes, so a `@Composable` helper
cannot be called inside it; hoist the value first.

### Not a `SegmentedControl`

They look almost identical and mean opposite things:

| | `ButtonGroup` | [`SegmentedControl`](segmented-control.md) |
|---|---|---|
| Each item is | an action | an option |
| Something is selected | no | always exactly one |
| Role | `Button` | `RadioButton` |
| Pressing one | does something | changes a value |

A segmented control with no selection is broken; a button group with a selection
is a segmented control wearing the wrong clothes. **If the row answers a
question, it is a segmented control.**

### Not `TopBar`'s `actions`

That slot holds the *screen's* actions, separated from each other. This is a
cluster that belongs together — zoom in and zoom out — and the joining is what
says so. A [`TopBar`](top-bar.md) can hold one of these in its slot.

Any single action can be disabled without the others, which is what lets a
cluster grey a button rather than hide it. A cluster that changes width as you
use it is worse than one with a greyed button in it.

---

← [Actions](actions.md) · [All components](../components.md)
