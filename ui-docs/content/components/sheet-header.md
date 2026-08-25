# `SheetHeader`

The title row a sheet needs: a label, optional supporting text, an actions slot,
and the drag handle above them.

<!--sample:SheetHeaderBasics-->
```kotlin
SheetHeader(
    actions = {
        IconButton(
            icon = Tabler.Outline.Star,
            contentDescription = "Add to favourites",
            onClick = { save() },
        )
    },
) {
    +"Perth Underground"
    supporting { +"Platform 2 · Joondalup line" }
}
```

It takes a `ListItemScope`, the same shape [`ListItem`](list-item.md) and
[`TopBar`](top-bar.md) take, so `+"Title"` and `supporting { … }` mean the same
thing in all three. That is the point of the slot vocabulary — see
[`../dsls.md`](../dsls.md).

`Modifier.sheetPeekAnchor()` on the header is what tells a `peek` detent how tall
to be: the sheet measures the anchored node rather than taking a number, so a
two-line title peeks taller than a one-line one without anybody computing it.

---

## Accessibility

The title is marked `heading()`, so a screen reader can jump to it — which is
what makes a long sheet navigable rather than a single run of text.

The close button carries `closeLabel`, and `onClose` defaults to closing the
enclosing sheet, so the affordance is there without the caller wiring it. Set it
to `null` only for a sheet that genuinely cannot be closed from inside.

Actions in the `actions` slot are ordinary buttons and need their own
`contentDescription` when they are icons.

---

← [Sheets](sheets.md) · [All components](../components.md)
