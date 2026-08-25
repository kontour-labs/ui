# `IconToggleButton`

![IconToggleButton, unchecked](../../../ui-catalog/screenshots/components/icontogglebutton-light.png)
![IconToggleButton, checked](../../../ui-catalog/screenshots/components/icontogglebutton-checked-light.png)

The first of those is pixel-for-pixel an [`IconButton`](icon-button.md), and that is
not a mistake in the picture: unchecked, it *is* one. The accent ground in the
second is the entire visible difference between the two components.

<!--sample:IconToggleButtonBasics-->
```kotlin
var favourite by remember { mutableStateOf(false) }

IconToggleButton(
    icon = Tabler.Outline.Star,
    contentDescription = "Favourite",
    checked = favourite,
    onCheckedChange = { favourite = it },
)
```

Announces `Role.Checkbox`, not `Role.Switch`. It reads as one — a star that is
on or off — but a switch describes the sliding control, and a screen reader that
says "switch" for a star describes a widget that is not on screen. The contract
suite found this component announcing `Role.Switch` from a wrapper `Box` around
an `IconButton` that announced `Role.Button`: a switch containing a button, and
the wrong role either way.

**Reach for `SelectionRow` with a `Switch` instead** when the thing being toggled
deserves a written label. An icon toggle is for a dense row of actions where the
glyph is the whole affordance.

---

← [Actions](actions.md) · [All components](../components.md)
