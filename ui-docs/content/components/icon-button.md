# `IconButton`

![IconButton](../../../ui-catalog/screenshots/components/iconbutton-light.png)

<!--sample:IconButtonBasics-->
```kotlin
IconButton(Tabler.Outline.X, contentDescription = "Close", onClick = { dismiss() })
```

`contentDescription` is **required and non-null**. There is no visible text to
fall back on, so an icon button without one is a control a screen-reader user
cannot identify. If the icon is genuinely decorative, it is not a button.

`rotation` animates, which is what makes a disclosure chevron read as the same
arrow turning rather than two different glyphs swapping.

<!--sample:IconButtonRotation-->
```kotlin
var expanded by remember { mutableStateOf(false) }

IconButton(
    icon = Tabler.Outline.ChevronDown,
    contentDescription = if (expanded) "Collapse" else "Expand",
    onClick = { expanded = !expanded },
    rotation = if (expanded) 180f else 0f,
)
```

It defaults to `ButtonVariant.Ghost` and `Theme.shapes.pill`, because an icon
button is nearly always a low-weight action inside something else. Both are
parameters when it is not.

**It is as tall as a `Button` of the same size.** The box is the control height
— 28, 36, 44, 52, 60dp — and the padding round the glyph is whatever is left
over. It used to be `iconSize + iconOnlyPadding * 2`, which is a second way of
saying how tall a control is, and it disagreed with the first at three of the
five sizes. That made every `ButtonGroup` mixing an icon action with a labelled
one ragged, and the trailing half of a [`SplitButton`](split-button.md) 4dp
short of the half beside it. `ControlHeightTest` holds the two together now.

---

## Accessibility

`contentDescription` is the button's **name**, not a tooltip, and there is no
default — an icon button with nothing else in it has no other source of one.

`IconToggleButton` reports `Role.Checkbox` and a `stateDescription`, so what is
announced is "Favourite, on" rather than a button that gives no clue which way it
is set. `stateDescription` is overridable for the cases where on/off is the wrong
vocabulary — "muted"/"unmuted" reads better than "on"/"off".

The touch target is `Theme.sizing.minTouchTarget` regardless of the icon size, so
a small icon button is still a full-size target. That is enforced by a modifier
rather than by remembering, and asserted by the contract suite.

---

← [Actions](actions.md) · [All components](../components.md)
