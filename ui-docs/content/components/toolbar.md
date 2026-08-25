# `Toolbar`

*Also on this page: `ToolbarDivider`.*

![Toolbar](../../../ui-catalog/screenshots/components/toolbar-light.png)

<!--sample:ToolbarBasics-->
```kotlin
Toolbar {
    ButtonGroup {
        item(
            onClick = { zoomOut() },
            contentDescription = "Zoom out",
            icon = Tabler.Outline.Minus,
        )
        item(onClick = { zoomIn() }, contentDescription = "Zoom in", icon = Tabler.Outline.Plus)
    }
    ToolbarDivider()
    IconButton(Tabler.Outline.Stack, "Map layers", onClick = { openLayers() })
}
```

A floating surface holding actions, over content it does not belong to — the
controls over the map.

**Deliberately thin**: a `Surface` and a `Row`. It earns its place the way
[`Card`](card.md) does, by fixing the elevation, shape, padding and
traversal semantics in one place so a second toolbar does not grow a second set
of numbers.

**Its corners are concentric with what it holds**, and now by construction
rather than by arithmetic. `ToolbarDefaults.Shape` is `Theme.shapes.control` —
the same shape as the buttons inside it. Both are capsules, so the outer radius
is half the bar's height and the inner is half a button's, and a button inset by
the content padding top and bottom is shorter by exactly twice it: the two radii
differ by exactly the padding, which is the rule. It holds whatever the padding,
the height or the buttons turn out to be.

It got here the long way. It was a pill; then a `ButtonGroup`'s 8dp corners were
found poking *through* the pill's curve and being sheared flat against it, so it
became one rung up the size scale instead; and now the buttons are capsules too,
so the original answer is right again — for a reason this time.

**It is not a [`TopBar`](top-bar.md).** A top bar is *part of* the
screen — it holds the title and sits at the top. A toolbar floats **over**
content that is not its own, which is why it has a shadow and rounded corners
and a top bar has neither. If it is the screen's chrome, it is a top bar.

For a translucent one over a live map, use `GlassSurface` and read the note in
[adaptive](adaptive.md#there-is-no-portable-backdrop-blur--for-a-floating-bar) — there is no
portable backdrop blur.

---

## Accessibility

An `isTraversalGroup`, and `minTouchTarget` reserved on the row rather than per
button — the same arrangement as [`ButtonGroup`](button-group.md), for the same
reason.

Every item is an icon button and needs its own `contentDescription`.
`ToolbarDivider` is presentational and carries none.

A floating toolbar sits over content. Where it is over something scrollable, that
content needs padding to match, or the rows underneath it can never be read.

---

← [Actions](actions.md) · [All components](../components.md)
