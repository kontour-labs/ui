# `Toolbar`

*Also on this page: `VerticalToolbar`, `ToolbarDivider`.*

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

**Its corners are concentric with what it holds**, and now by derivation rather
than by coincidence. `ToolbarDefaults.Shape` is
`Theme.shapes.control.outset(ContentPadding)` — the buttons' own shape resolved
against the box they are drawn on, with the ring of space added back. Concentric
means the outer radius is the inner one plus the gap between them, and that is
what `outset` says.

Sharing the bare `control` token used to be enough, because two uncapped capsules
gave the outer radius as half the bar's height and the inner as half a button's,
and a button inset by the content padding top and bottom is shorter by exactly
twice it. Capping the capsule ends that, and the failure is invisible in the
token: a 56dp bar and a 44dp button both stop at 18, so the ring stays 6dp along
every straight edge and **closes to nothing at the corners**. Derived, the bar
comes out at 24 where its buttons are at 18, whatever the padding, the height or
the buttons turn out to be.

It got here the long way — a pill, then one rung up the size scale when a
`ButtonGroup`'s 8dp corners were found poking *through* the pill's curve, then
the bare token again once the children became capsules. This is the first version
that says what it means.

**It is not a [`TopBar`](top-bar.md).** A top bar is *part of* the
screen — it holds the title and sits at the top. A toolbar floats **over**
content that is not its own, which is why it has a shadow and rounded corners
and a top bar has neither. If it is the screen's chrome, it is a top bar.

For a translucent one over a live map, use `GlassSurface` and read the note in
[adaptive](adaptive.md#there-is-no-portable-backdrop-blur--for-a-floating-bar) — there is no
portable backdrop blur.

---

## `VerticalToolbar` — the same bar, down the side

<!--sample:VerticalToolbarBasics-->
```kotlin
VerticalToolbar {
    IconButton(Tabler.Outline.Plus, "Zoom in", onClick = { zoomIn() })
    IconButton(Tabler.Outline.Minus, "Zoom out", onClick = { zoomOut() })
    ToolbarDivider()
    IconButton(Tabler.Outline.Stack, "Map layers", onClick = { openLayers() })
}
```

For a strip that belongs *beside* the thing it acts on rather than under it — map
zoom controls, a canvas's tools. Everything is shared with the horizontal bar:
the shape, the elevation, the padding, the traversal group, and the bar owning
its children's touch targets.

**A separate composable rather than an `orientation` parameter**, because the
axis changes the slot's type: a bar laid out in a column hands its content a
`ColumnScope`, and one function cannot offer both without handing out a scope
that lies about one of them. `HorizontalDivider` and `VerticalDivider` are the
same call made for the same reason.

`arrangement` is the one parameter that differs — an `Arrangement.Vertical`,
defaulting to the same `xxs` gap the horizontal bar uses. Pass
`Arrangement.spacedBy(…, Alignment.Bottom)` for a bar that hangs from the top of
a taller space.

**`ToolbarDivider` follows the axis by itself**, drawing a vertical rule in a
horizontal bar and a horizontal one in a vertical bar. It reads the bar it is
inside, so there is nothing to pass and nothing to get wrong. Its size token is
`ToolbarDefaults.DividerLength` — a length rather than a height, because it is
the divider's height in one bar and its width in the other.

---

## Accessibility

An `isTraversalGroup`, and `minTouchTarget` reserved on the row rather than per
button — the same arrangement as [`ButtonGroup`](button-group.md), for the same
reason.

Every item is an icon button and needs its own `contentDescription`.
`ToolbarDivider` is presentational and carries none.

A floating toolbar sits over content. Where it is over something scrollable, that
content needs padding to match, or the rows underneath it can never be read.
