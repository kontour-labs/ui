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

---

← [Actions](actions.md) · [All components](../components.md)
